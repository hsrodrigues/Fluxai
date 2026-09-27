package fluxai.app

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.nio.charset.Charset
import java.text.NumberFormat
import java.util.Locale

// =========================================================================
// IMPORTAR EXTRATO
// O usuário escolhe o OFX/CSV exportado pelo banco, revisa e confirma.
// Saídas viram lançamentos; entradas podem somar na renda extra do mês.
// =========================================================================
private data class LinhaImportacao(val item: ItemExtrato, val categoria: String, val marcada: Boolean, val duplicada: Boolean)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportarExtratoScreen(onLogout: () -> Unit) {
    val context = LocalContext.current
    val usuario = Firebase.auth.currentUser ?: return
    val workspaceUid = remember { workspaceAtual(context, usuario.uid) }
    val usuarioDoc = remember { Firebase.firestore.collection("usuarios").document(workspaceUid) }
    val c = coresTela()
    val escopo = rememberCoroutineScope()
    val navegar = LocalNavegar.current
    val moeda = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }

    var fatura by remember { mutableStateOf(false) }
    var nomeArquivo by remember { mutableStateOf<String?>(null) }
    var linhas by remember { mutableStateOf<List<LinhaImportacao>>(emptyList()) }
    var carregando by remember { mutableStateOf(false) }
    var categorizando by remember { mutableStateOf(false) }
    var importando by remember { mutableStateOf(false) }
    var somarEntradas by remember { mutableStateOf(false) }
    var textoBruto by remember { mutableStateOf<String?>(null) }

    var cartoes by remember { mutableStateOf<List<Cartao>>(emptyList()) }
    var contas by remember { mutableStateOf<List<ContaBancaria>>(emptyList()) }
    var cartaoId by remember { mutableStateOf<String?>(null) }
    var contaId by remember { mutableStateOf<String?>(null) }
    var categoriasCustom by remember { mutableStateOf<List<String>>(emptyList()) }
    var linhaEditandoCategoria by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(workspaceUid) {
        runCatching {
            cartoes = usuarioDoc.collection("cartoes").get().await().documents.map { d ->
                Cartao(d.id, d.getString("nome") ?: "", d.getString("bandeira") ?: "", diaVencimento = d.getLong("diaVencimento")?.toInt() ?: 10)
            }
            contas = usuarioDoc.collection("contas").get().await().documents.map { d -> ContaBancaria(d.id, d.getString("nome") ?: "", d.getString("tipo") ?: "") }
            categoriasCustom = usuarioDoc.collection("categorias_custom").get().await().documents.mapNotNull { it.getString("nome") }
        }
    }
    val categorias = (CategoriasPadrao + categoriasCustom).distinct()

    // Lê o arquivo, separa as linhas e marca o que já parece lançado no app
    fun processarItens(obterItens: suspend () -> List<ItemExtrato>) {
        carregando = true
        escopo.launch {
            val itens = try { obterItens() } catch (e: Exception) {
                carregando = false
                Toast.makeText(context, when (e) {
                    is PdfProtegido -> "Este PDF tem senha. Baixe a fatura sem senha no app do banco, ou use o arquivo OFX/CSV."
                    is FalhaIA -> e.message ?: "Falha ao ler o PDF."
                    else -> "Não foi possível ler o arquivo."
                }, Toast.LENGTH_LONG).show()
                return@launch
            }
            val meses = itens.map { it.data.mesAno }.distinct()
            val existentes = runCatching {
                meses.chunked(30).flatMap { parte -> usuarioDoc.collection("despesas").whereIn("mesAno", parte).get().await().documents.map { lerDespesa(it) } }
            }.getOrDefault(emptyList())
            fun chave(desc: String, valor: Double, mes: String) = desc.lowercase().replace("[cartão] ", "").trim() + "|" + "%.2f".format(valor) + "|" + mes
            val jaLancadas = existentes.map { chave(it.descricao, it.valor, it.mesAno) }.toSet()
            linhas = itens.map { item ->
                val dup = chave(item.descricao, item.valor, item.data.mesAno) in jaLancadas
                LinhaImportacao(item, if (item.entrada) "Renda" else sugerirCategoria(item.descricao), marcada = !item.entrada && !dup, duplicada = dup)
            }
            carregando = false
            if (itens.isEmpty()) Toast.makeText(context, "Não encontrei lançamentos nesse arquivo. Use o PDF, OFX ou CSV exportado pelo banco.", Toast.LENGTH_LONG).show()
        }
    }

    fun processar(texto: String, nome: String) = processarItens { withContext(Dispatchers.Default) { lerExtrato(nome, texto, fatura) } }

    val seletor = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val nome = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cur ->
            if (cur.moveToFirst()) cur.getString(0) else null
        } ?: "extrato"
        // PDF: texto lido por OCR e lançamentos separados pela IA (não depende do sinal dos valores)
        if (context.contentResolver.getType(uri) == "application/pdf" || nome.lowercase().endsWith(".pdf")) {
            nomeArquivo = nome
            textoBruto = null
            processarItens { lerPdfExtrato(context, uri) }
            return@rememberLauncherForActivityResult
        }
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        if (bytes == null) { Toast.makeText(context, "Não foi possível ler o arquivo.", Toast.LENGTH_SHORT).show(); return@rememberLauncherForActivityResult }
        // Bancos brasileiros ainda exportam em Latin-1; se o UTF-8 der caracteres inválidos, tenta de novo
        val utf8 = String(bytes, Charsets.UTF_8)
        val texto = if ('�' in utf8) String(bytes, Charset.forName("ISO-8859-1")) else utf8
        nomeArquivo = nome
        textoBruto = texto
        processar(texto, nome)
    }

    // Reprocessa se o usuário trocar entre extrato de conta e fatura de cartão (o sinal dos valores muda)
    LaunchedEffect(fatura) { val t = textoBruto; val n = nomeArquivo; if (t != null && n != null) processar(t, n) }

    fun categorizarComIA() {
        val saidas = linhas.withIndex().filter { !it.value.item.entrada }
        if (saidas.isEmpty()) return
        categorizando = true
        escopo.launch {
            runCatching {
                saidas.chunked(120).forEach { parte ->
                    val lista = parte.joinToString("\n") { (i, l) -> "$i|${l.item.descricao}" }
                    val sistema = "Classifique cada gasto de extrato bancário brasileiro em UMA categoria desta lista: ${categorias.joinToString(", ")}. " +
                        "Responda APENAS um JSON puro {\"indice\":\"Categoria\"}, sem markdown."
                    val resposta = chamarIA("ocr", sistema, lista)
                    val json = Regex("\\{.*\\}", RegexOption.DOT_MATCHES_ALL).find(resposta)?.value ?: return@forEach
                    val obj = JSONObject(json)
                    linhas = linhas.mapIndexed { i, l -> obj.optString(i.toString()).takeIf { it in categorias }?.let { l.copy(categoria = it) } ?: l }
                }
            }.onFailure { Toast.makeText(context, (it as? FalhaIA)?.message ?: "Não foi possível categorizar agora.", Toast.LENGTH_LONG).show() }
            categorizando = false
        }
    }

    fun importar() {
        val escolhidas = linhas.filter { it.marcada && !it.item.entrada }
        val entradas = if (somarEntradas && !fatura) linhas.filter { it.marcada && it.item.entrada } else emptyList()
        if (escolhidas.isEmpty() && entradas.isEmpty()) return
        if (fatura && cartaoId == null) { Toast.makeText(context, "Escolha o cartão desta fatura.", Toast.LENGTH_SHORT).show(); return }
        importando = true
        escopo.launch {
            runCatching {
                val cartao = cartoes.firstOrNull { it.id == cartaoId }
                escolhidas.chunked(400).forEach { parte ->
                    val lote = Firebase.firestore.batch()
                    parte.forEach { l ->
                        lote.set(usuarioDoc.collection("despesas").document(), hashMapOf(
                            "descricao" to if (fatura) "[Cartão] ${l.item.descricao}" else l.item.descricao,
                            "valor" to l.item.valor,
                            "diaVencimento" to if (fatura) (cartao?.diaVencimento ?: l.item.data.dia) else l.item.data.dia,
                            "tipo" to "Variável",
                            "categoria" to l.categoria,
                            // Extrato de conta: já saiu do banco. Fatura: fica a pagar até a fatura ser paga.
                            "status" to if (fatura) "A pagar" else "Pago",
                            "mesAno" to l.item.data.mesAno,
                            "observacao" to "Importado de ${nomeArquivo ?: "extrato"}",
                            "frequencia" to "Mensal",
                            "cartaoId" to if (fatura) cartaoId else null,
                            "contaId" to if (fatura) null else contaId,
                            "projetoId" to null,
                            "pagoPor" to if (fatura) null else usuario.uid,
                            "pagoPorNome" to if (fatura) null else usuario.displayName?.split(" ")?.firstOrNull(),
                            "criadoEm" to FieldValue.serverTimestamp()
                        ))
                    }
                    if (fatura && cartaoId != null) {
                        lote.update(usuarioDoc.collection("cartoes").document(cartaoId!!), "faturaAtual", FieldValue.increment(parte.sumOf { it.item.valor }))
                    }
                    lote.commit().await()
                }
                // Entradas somam na renda extra de cada mês
                entradas.groupBy { it.item.data.mesAno }.forEach { (mes, itens) ->
                    val total = itens.sumOf { it.item.valor }
                    usuarioDoc.collection("saldos").document(mes.replace("/", "-"))
                        .set(mapOf("extra" to FieldValue.increment(total), "valor" to FieldValue.increment(total)), SetOptions.merge()).await()
                }
            }.onSuccess {
                Toast.makeText(context, "${escolhidas.size} lançamentos importados.", Toast.LENGTH_LONG).show()
                registrarAuditoria("Importou ${escolhidas.size} lançamentos de extrato")
                navegar("dashboard")
            }.onFailure { Toast.makeText(context, "Falha ao importar: ${it.message}", Toast.LENGTH_LONG).show() }
            importando = false
        }
    }

    val marcadasSaida = linhas.filter { it.marcada && !it.item.entrada }
    TelaComMenu(titulo = "Importar extrato", rota = "importar", onLogout = onLogout) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Surface(shape = RoundedCornerShape(20.dp), color = c.superficie, border = BorderStroke(1.dp, c.divisor), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Traga o mês inteiro de uma vez", fontWeight = FontWeight.Bold, color = c.texto)
                            Text("Escolha a fatura ou o extrato em PDF, OFX ou CSV (baixe no app do banco). Você revisa tudo antes de importar.",
                                fontSize = 13.sp, color = c.textoFraco, lineHeight = 18.sp)
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                listOf(false to "Extrato da conta", true to "Fatura do cartão").forEachIndexed { i, (valor, rotulo) ->
                                    SegmentedButton(
                                        selected = fatura == valor, onClick = { fatura = valor }, shape = SegmentedButtonDefaults.itemShape(i, 2),
                                        colors = SegmentedButtonDefaults.colors(activeContainerColor = c.destaque.copy(alpha = 0.14f), activeContentColor = c.destaque)
                                    ) { Text(rotulo, fontSize = 13.sp) }
                                }
                            }
                            val opcoes = if (fatura) cartoes.map { it.id to it.nome } else contas.map { it.id to it.nome }
                            if (opcoes.isNotEmpty()) {
                                Text(if (fatura) "Cartão desta fatura" else "Conta do extrato (opcional)", fontSize = 12.sp, color = c.textoFraco)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    opcoes.forEach { (id, nome) ->
                                        val sel = if (fatura) cartaoId == id else contaId == id
                                        FilterChip(
                                            selected = sel,
                                            onClick = { if (fatura) cartaoId = id.takeUnless { sel } else contaId = id.takeUnless { sel } },
                                            label = { Text(nome) }, shape = RoundedCornerShape(12.dp),
                                            leadingIcon = { Icon(if (fatura) Icons.Default.CreditCard else Icons.Default.AccountBalance, null, Modifier.size(16.dp)) },
                                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.destaque.copy(alpha = 0.14f), selectedLabelColor = c.destaque, selectedLeadingIconColor = c.destaque)
                                        )
                                    }
                                }
                            } else if (fatura) {
                                Text("Cadastre o cartão em Cartões antes de importar a fatura.", fontSize = 12.sp, color = Color(0xFFE53935))
                            }
                            if (!fatura) Text("O saldo da conta no app não muda: o extrato já está refletido no saldo do banco.", fontSize = 11.sp, color = c.textoFraco)
                            Button(
                                onClick = { seletor.launch(arrayOf("application/pdf", "text/*", "application/x-ofx", "application/ofx", "application/vnd.ms-excel", "application/octet-stream")) },
                                enabled = !carregando && !importando, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = c.destaque)
                            ) {
                                Icon(Icons.Default.UploadFile, null); Spacer(Modifier.width(8.dp))
                                Text(if (nomeArquivo == null) "Escolher arquivo" else "Trocar arquivo")
                            }
                            nomeArquivo?.let { Text(it, fontSize = 12.sp, color = c.textoFraco, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }

                if (carregando) item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.destaque) } }

                if (linhas.isNotEmpty() && !carregando) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${linhas.size} lançamentos no arquivo", fontWeight = FontWeight.Bold, color = c.texto, modifier = Modifier.weight(1f))
                            TextButton(onClick = { categorizarComIA() }, enabled = !categorizando) {
                                if (categorizando) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = c.destaque)
                                else Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp), tint = c.destaque)
                                Spacer(Modifier.width(6.dp)); Text("Categorizar com IA", color = c.destaque)
                            }
                        }
                        val duplicadas = linhas.count { it.duplicada }
                        if (duplicadas > 0) Text("$duplicadas já parecem estar no app e vieram desmarcadas.", fontSize = 12.sp, color = Color(0xFFFB8C00))
                        if (!fatura && linhas.any { it.item.entrada }) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { somarEntradas = !somarEntradas }) {
                                Checkbox(checked = somarEntradas, onCheckedChange = { somarEntradas = it }, colors = CheckboxDefaults.colors(checkedColor = c.destaque))
                                Text("Somar as entradas marcadas na renda extra do mês", fontSize = 13.sp, color = c.texto)
                            }
                        }
                    }
                    itemsIndexed(linhas) { i, l ->
                        val corValor = if (l.item.entrada) Color(0xFF43A047) else c.texto
                        Surface(
                            shape = RoundedCornerShape(14.dp), color = c.superficie,
                            border = BorderStroke(1.dp, if (l.marcada) c.destaque.copy(alpha = 0.4f) else c.divisor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = l.marcada,
                                    onCheckedChange = { m -> linhas = linhas.toMutableList().also { it[i] = l.copy(marcada = m) } },
                                    colors = CheckboxDefaults.colors(checkedColor = c.destaque),
                                    modifier = Modifier.semantics { stateDescription = if (l.marcada) "Será importado" else "Não será importado" }
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(l.item.descricao, color = c.texto, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("%02d/%02d".format(l.item.data.dia, l.item.data.mes), fontSize = 11.sp, color = c.textoFraco)
                                        if (!l.item.entrada) {
                                            Spacer(Modifier.width(8.dp))
                                            Box {
                                                Row(
                                                    Modifier.clip(RoundedCornerShape(8.dp)).clickable { linhaEditandoCategoria = i }.padding(horizontal = 4.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(iconeCategoria(l.categoria), null, Modifier.size(14.dp), tint = corCategoria(l.categoria))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(l.categoria, fontSize = 11.sp, color = corCategoria(l.categoria), fontWeight = FontWeight.SemiBold)
                                                    Icon(Icons.Default.ArrowDropDown, "Trocar categoria", Modifier.size(14.dp), tint = c.textoFraco)
                                                }
                                                DropdownMenu(expanded = linhaEditandoCategoria == i, onDismissRequest = { linhaEditandoCategoria = null }) {
                                                    categorias.forEach { cat ->
                                                        DropdownMenuItem(
                                                            text = { Text(cat) },
                                                            leadingIcon = { Icon(iconeCategoria(cat), null, tint = corCategoria(cat)) },
                                                            onClick = { linhas = linhas.toMutableList().also { it[i] = l.copy(categoria = cat) }; linhaEditandoCategoria = null }
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            Spacer(Modifier.width(8.dp)); Text("Entrada", fontSize = 11.sp, color = Color(0xFF43A047), fontWeight = FontWeight.SemiBold)
                                        }
                                        if (l.duplicada) { Spacer(Modifier.width(8.dp)); Text("Já lançado?", fontSize = 11.sp, color = Color(0xFFFB8C00)) }
                                    }
                                }
                                Text((if (l.item.entrada) "+ " else "") + moeda.format(l.item.valor), fontWeight = FontWeight.Bold, color = corValor, fontSize = 14.sp)
                            }
                        }
                    }
                }

                if (linhas.isEmpty() && !carregando && nomeArquivo == null) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(80.dp).background(c.destaque.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Description, null, Modifier.size(40.dp), tint = c.destaque)
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("Formatos aceitos: PDF da fatura ou extrato (lido com IA), OFX (todos os bancos) e CSV (Nubank, Inter, C6 e planilhas com data, descrição e valor).",
                                fontSize = 12.sp, color = c.textoFraco, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                        }
                    }
                }
            }

            if (linhas.isNotEmpty()) {
                Surface(color = c.fundo) {
                    Button(
                        onClick = { importar() },
                        enabled = !importando && (marcadasSaida.isNotEmpty() || (somarEntradas && linhas.any { it.marcada && it.item.entrada })),
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 52.dp),
                        shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = c.destaque)
                    ) {
                        if (importando) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("Importar ${marcadasSaida.size} · ${moeda.format(marcadasSaida.sumOf { it.item.valor })}", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
