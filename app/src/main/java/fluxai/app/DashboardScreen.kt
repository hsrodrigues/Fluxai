package fluxai.app

import com.google.firebase.firestore.ListenerRegistration
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import fluxai.app.ui.theme.LocalAccentColor
import fluxai.app.ui.theme.LocalDarkTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.net.toUri

@SuppressLint("AutoboxingStateCreation")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onLogout: () -> Unit,
    onAbrirDashboard: () -> Unit,
    onAbrirLancamento: () -> Unit,
    onAbrirAnalytics: () -> Unit,
    onAbrirSettings: () -> Unit,
    onAbrirSobre: () -> Unit,
    onAbrirManutencao: () -> Unit,
    onAbrirCartoes: () -> Unit,
    onAbrirCaixinhas: () -> Unit,
    onAbrirAssinaturas: () -> Unit,
    onAbrirCelular: () -> Unit
) {
    val context = LocalContext.current
    val user = Firebase.auth.currentUser
    val isDark = LocalDarkTheme.current
    val colorBg = if (isDark) Color(0xFF0F0F0F) else Color(0xFFF4F7FA)
    val colorAccent = LocalAccentColor.current

    if (user == null) {
        Box(modifier = Modifier.fillMaxSize().background(colorBg), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colorAccent)
        }
        LaunchedEffect(Unit) { onLogout() }
        return
    }

    val sharedPref = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
    val workspaceUid = sharedPref.getString("workspace_uid", user.uid) ?: user.uid

    val banco = Firebase.firestore
    // Ajusta a fatura do cartão no servidor quando um lançamento de cartão ainda não pago muda de valor ou é excluído
    fun ajustarFatura(despesa: Despesa, delta: Double) {
        val cartaoId = despesa.cartaoId
        if (cartaoId.isNullOrBlank() || cartaoId == "Saldo Conta" || despesa.status == "Pago" || delta == 0.0) return
        banco.collection("usuarios").document(workspaceUid).collection("cartoes").document(cartaoId)
            .update("faturaAtual", com.google.firebase.firestore.FieldValue.increment(delta))
    }
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val localeBR = remember { Locale("pt", "BR") }

    val colorSurface = if (isDark) Color(0xFF1A1A1A) else Color.White
    val colorTextPrimary = if (isDark) Color.White else Color(0xFF1A1A1A)
    val colorTextSecondary = Color(0xFF9CA3AF)
    val colorDivider = if (isDark) Color(0xFF2C2C2C) else Color(0xFFE5E7EB)
    val moeda = remember { java.text.NumberFormat.getCurrencyInstance(localeBR) }
    val coresCampo = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colorAccent, unfocusedBorderColor = colorDivider, focusedTextColor = colorTextPrimary, unfocusedTextColor = colorTextPrimary,
        focusedContainerColor = colorSurface, unfocusedContainerColor = colorSurface, cursorColor = colorAccent
    )

    var calendar by remember { mutableStateOf(Calendar.getInstance()) }
    var mesAnoSelecionado by remember { mutableStateOf(SimpleDateFormat("MM/yyyy", localeBR).format(calendar.time)) }
    var mesNome by remember { mutableStateOf(SimpleDateFormat("MMMM yyyy", localeBR).format(calendar.time).replaceFirstChar { it.uppercase() }) }
    var categoriaSelecionada by remember { mutableStateOf("Todas") }

    // Estado da barra de pesquisa
    var textoPesquisa by remember { mutableStateOf("") }

    var criarRendaDoMes by remember { mutableStateOf(false) }
    var adiantamentoString by remember { mutableStateOf("") }
    var pagamentoString by remember { mutableStateOf("") }
    var extraString by remember { mutableStateOf("") }
    var editandoSaldo by remember { mutableStateOf(false) }

    var despesasRaw by remember { mutableStateOf<List<Despesa>>(emptyList()) }

    var vServidorState by remember { mutableLongStateOf(0L) }
    var updateUrl by remember { mutableStateOf("") }
    var mostrarUpdateDialog by remember { mutableStateOf(false) }
    var mostrarDialogCalendario by remember { mutableStateOf(false) }
    var mostrarDialogEmprestimo by remember { mutableStateOf(false) }

    var despesaParaExcluir by remember { mutableStateOf<Despesa?>(null) }
    var despesaParaEditar by remember { mutableStateOf<Despesa?>(null) }
    var despesaParaPagarCartao by remember { mutableStateOf<Despesa?>(null) }

    var mostrarDialogIA by remember { mutableStateOf(false) }
    var respostaIA by remember { mutableStateOf("") }
    var carregandoIA by remember { mutableStateOf(false) }
    var contextoIA by remember { mutableStateOf("") } // dados do mês usados também no chat

    var categoriesCustomList by remember { mutableStateOf<List<String>>(emptyList()) }
    var categoriasCustomList by remember { mutableStateOf<List<String>>(emptyList()) }
    var cartoesVisuais by remember { mutableStateOf<Map<String, Pair<String, String>>>(emptyMap()) } // id -> (nome, bandeira)
    var contasBancarias by remember { mutableStateOf<List<ContaBancaria>>(emptyList()) }
    var comprasDetectadas by remember { mutableStateOf<List<CompraPendente>>(emptyList()) }
    var limitesCategoria by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var caixinhasMetas by remember { mutableStateOf<List<Caixinha>>(emptyList()) }
    var rendaPadrao by remember { mutableStateOf<Pair<Double, Double>?>(null) } // (adiantamento, salário)
    var mostrarCompras by remember { mutableStateOf(false) }
    var despesaParaComprovante by remember { mutableStateOf<Despesa?>(null) }
    var despesaVerComprovante by remember { mutableStateOf<Despesa?>(null) }
    var fotoComprovante by remember { mutableStateOf<android.net.Uri?>(null) }

    DisposableEffect(workspaceUid) {
        val ouvintes = mutableListOf<ListenerRegistration>()
        if (workspaceUid.isNotEmpty()) {
            ouvintes += banco.collection("usuarios").document(workspaceUid).collection("categorias_custom")
                .addSnapshotListener { snap, _ ->
                    if (snap != null) {
                        categoriasCustomList = snap.documents.mapNotNull { d ->
                            try { d.getString("nome") } catch (e: Exception) { null }
                        }
                        atualizarIconesCustom(snap)
                    }
                }
            // Só para exibição: nome e bandeira de cada cartão, usados no ícone dos lançamentos
            ouvintes += banco.collection("usuarios").document(workspaceUid).collection("cartoes")
                .addSnapshotListener { snap, _ ->
                    if (snap != null) {
                        cartoesVisuais = snap.documents.associate { d -> d.id to ((d.getString("nome") ?: "") to (d.getString("bandeira") ?: "")) }
                    }
                }
            ouvintes += banco.collection("usuarios").document(workspaceUid).collection("contas")
                .addSnapshotListener { snap, _ ->
                    if (snap != null) contasBancarias = snap.documents.map { d -> ContaBancaria(d.id, d.getString("nome") ?: "", d.getString("tipo") ?: "", d.getDouble("saldo") ?: 0.0) }
                }
            // Compras lidas das notificações do banco, esperando confirmação
            ouvintes += banco.collection("usuarios").document(workspaceUid).collection("compras_detectadas")
                .addSnapshotListener { snap, _ ->
                    if (snap != null) comprasDetectadas = snap.documents.map { lerCompraPendente(it) }.sortedByDescending { it.detectadaEm }
                }
            // Limites por categoria (definidos na Análise BI)
            ouvintes += banco.collection("usuarios").document(workspaceUid).collection("configuracoes").document("limites")
                .addSnapshotListener { snap, _ ->
                    limitesCategoria = (snap?.data ?: emptyMap()).mapValues { (it.value as? Number)?.toDouble() ?: 0.0 }
                }
            ouvintes += banco.collection("usuarios").document(workspaceUid).collection("caixinhas")
                .addSnapshotListener { snap, _ ->
                    if (snap != null) caixinhasMetas = snap.documents.map { d ->
                        Caixinha(d.id, d.getString("nome") ?: "", d.getDouble("meta") ?: 0.0, d.getDouble("saldo") ?: 0.0, d.getString("icone") ?: "", d.getString("prazo") ?: "")
                    }
                }
            ouvintes += banco.collection("usuarios").document(workspaceUid)
                .addSnapshotListener { doc, _ ->
                    val r = doc?.get("rendaPadrao") as? Map<*, *>
                    rendaPadrao = r?.let { ((it["adiantamento"] as? Number)?.toDouble() ?: 0.0) to ((it["pagamento"] as? Number)?.toDouble() ?: 0.0) }
                }
        }
        // Desliga os ouvintes ao sair da tela ou trocar a chave (ex.: mês), senão eles se acumulam
        onDispose { ouvintes.forEach { it.remove() } }
    }

    val formatador = remember {
        java.text.NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }

    fun atualizarData() {
        mesAnoSelecionado = SimpleDateFormat("MM/yyyy", localeBR).format(calendar.time)
        mesNome = SimpleDateFormat("MMMM yyyy", localeBR).format(calendar.time).replaceFirstChar { it.uppercase() }
    }

    // Nova versão: consulta a última versão publicada ao abrir e sempre que o app volta para a tela,
    // no máximo a cada 2 min (o Android mantém o app vivo em segundo plano, então "reabrir" nem sempre recria a tela)
    val ciclo = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var retomadas by remember { mutableIntStateOf(0) }
    DisposableEffect(ciclo) {
        val observador = androidx.lifecycle.LifecycleEventObserver { _, evento ->
            if (evento == androidx.lifecycle.Lifecycle.Event.ON_RESUME) retomadas++
        }
        ciclo.lifecycle.addObserver(observador)
        onDispose { ciclo.lifecycle.removeObserver(observador) }
    }
    LaunchedEffect(retomadas) {
        if (System.currentTimeMillis() - atualizacaoVerificadaEm < INTERVALO_VERIFICAR_ATUALIZACAO_MS) return@LaunchedEffect
        // Ao abrir, o ON_RESUME logo em seguida reinicia este efeito e cancela a consulta em andamento:
        // por isso o horário só é marcado depois da resposta (senão a verificação nunca terminava)
        val remota = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { buscarUltimaVersao() }
            ?: return@LaunchedEffect // falhou a rede: tenta de novo na próxima volta
        atualizacaoVerificadaEm = System.currentTimeMillis()
        if (remota.versao > BuildConfig.VERSION_CODE) {
            vServidorState = remota.versao
            updateUrl = remota.urlApk
            mostrarUpdateDialog = true
        }
    }

    DisposableEffect(mesAnoSelecionado, workspaceUid) {
        val ouvintes = mutableListOf<ListenerRegistration>()
        ouvintes += banco.collection("usuarios").document(workspaceUid).collection("saldos").document(mesAnoSelecionado.replace("/", "-")).addSnapshotListener { doc, _ ->
            if (doc != null && doc.exists()) {
                val ad = doc.getDouble("adiantamento") ?: 0.0
                val pg = doc.getDouble("pagamento") ?: doc.getDouble("valor") ?: 0.0
                val ex = doc.getDouble("extra") ?: 0.0

                adiantamentoString = formatador.format(ad)
                pagamentoString = formatador.format(pg)
                extraString = formatador.format(ex)
                editandoSaldo = false
            } else {
                adiantamentoString = "0,00"
                pagamentoString = "0,00"
                extraString = "0,00"
                editandoSaldo = true
            }
            criarRendaDoMes = doc != null && !doc.exists() && !doc.metadata.isFromCache
        }

        ouvintes += banco.collection("usuarios").document(workspaceUid).collection("despesas").whereEqualTo("mesAno", mesAnoSelecionado).addSnapshotListener { snap, _ ->
            if (snap != null) {
                despesasRaw = snap.documents.sortedBy { it.getLong("ordem") ?: 9999L }.mapNotNull { d ->
                    try { lerDespesa(d) } catch (e: Exception) { null }
                }
            }
        }
        // Desliga os ouvintes ao sair da tela ou trocar a chave (ex.: mês), senão eles se acumulam
        onDispose { ouvintes.forEach { it.remove() } }
    }

    // Mês sem renda cadastrada: usa a renda padrão (definida na apresentação ou ao salvar o mês atual).
    // Só do mês corrente em diante, para não inventar renda em meses que já passaram.
    LaunchedEffect(criarRendaDoMes, rendaPadrao, mesAnoSelecionado) {
        val padrao = rendaPadrao ?: return@LaunchedEffect
        if (!criarRendaDoMes) return@LaunchedEffect
        val (m, a) = mesAnoSelecionado.split("/").map { it.toInt() }
        val hoje = Calendar.getInstance()
        if (a * 12 + m < hoje.get(Calendar.YEAR) * 12 + hoje.get(Calendar.MONTH) + 1) return@LaunchedEffect
        criarRendaDoMes = false
        banco.collection("usuarios").document(workspaceUid).collection("saldos").document(mesAnoSelecionado.replace("/", "-"))
            .set(mapOf("adiantamento" to padrao.first, "pagamento" to padrao.second, "extra" to 0.0, "valor" to padrao.first + padrao.second), com.google.firebase.firestore.SetOptions.merge())
    }

    // Variáveis restauradas corretamente
    val valAdiantamento = adiantamentoString.paraValor() ?: 0.0
    val valPagamento = pagamentoString.paraValor() ?: 0.0
    val valExtra = extraString.paraValor() ?: 0.0

    val totalRenda = valAdiantamento + valPagamento + valExtra

    // Totais só são recalculados quando a lista de despesas muda (não a cada redesenho da tela)
    val (totalDespesasQ1, totalDespesasQ2, totalAPagar, totalPago) = remember(despesasRaw) {
        val doMes = despesasRaw.filter { it.projetoId == null && it.status != "Próximo Mês" }
        val (q1, q2) = doMes.partition { it.frequencia.equals("Quinzenal", ignoreCase = true) }
        listOf(
            q1.sumOf { it.valor },
            q2.sumOf { it.valor },
            despesasRaw.filter { it.status == "A pagar" && it.projetoId == null }.sumOf { it.valor },
            despesasRaw.filter { it.status == "Pago" && it.projetoId == null }.sumOf { it.valor }
        )
    }
    val totalDespesasGeral = totalDespesasQ1 + totalDespesasQ2

    // CORREÇÃO LOGÍSTICA: Sobra da quinzena não acumula no orçamento mensal final para evitar somas incorretas
    val sobraQ1 = valAdiantamento + valExtra - totalDespesasQ1
    val sobraFinal = valPagamento - totalDespesasQ2

    // Filtro Restaurado com a Busca
    // Só refiltra quando a lista, a categoria ou a busca mudam (não a cada redesenho)
    val ordenadas = remember(despesasRaw, categoriaSelecionada, textoPesquisa) {
        despesasRaw.filter {
            (categoriaSelecionada == "Todas" || it.categoria == categoriaSelecionada) &&
                    (it.descricao.contains(textoPesquisa, ignoreCase = true))
        }
    }

    val despesasMutaveis = remember { androidx.compose.runtime.mutableStateListOf<Despesa>() }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(ordenadas) {
        if (draggedId == null) {
            despesasMutaveis.clear()
            despesasMutaveis.addAll(ordenadas)
        }
    }

    val mesAtualStr = remember { SimpleDateFormat("MM/yyyy", localeBR).format(Date()) }

    // Contas recorrentes: despesas do mês anterior que ainda não estão no mês aberto
    var despesasMesAnterior by remember { mutableStateOf<List<Despesa>>(emptyList()) }
    var mostrarRecorrencia by remember { mutableStateOf(false) }
    var recorrenciaDispensada by remember(mesAnoSelecionado) { mutableStateOf(sharedPref.getBoolean("recorrencia_dispensada_$mesAnoSelecionado", false)) }
    LaunchedEffect(mesAnoSelecionado, workspaceUid) {
        despesasMesAnterior = emptyList()
        banco.collection("usuarios").document(workspaceUid).collection("despesas")
            .whereEqualTo("mesAno", mesAnterior(mesAnoSelecionado)).get()
            .addOnSuccessListener { snap -> despesasMesAnterior = snap.documents.map { lerDespesa(it) } }
    }
    val candidatasRecorrentes = remember(despesasMesAnterior, despesasRaw) { candidatasRecorrencia(despesasMesAnterior, despesasRaw) }

    // A projeção só faz sentido no mês corrente
    val mostrarPrevisao = mesAnoSelecionado == mesAtualStr
    // Metas com prazo: o aporte do mês que ainda falta sai da sobra disponível
    val reservaMetas = remember(caixinhasMetas, despesasRaw, mesAnoSelecionado) { reservaPendenteMetas(caixinhasMetas, despesasRaw, mesAnoSelecionado) }
    val previsao = remember(despesasRaw, sobraFinal, totalRenda, mostrarPrevisao, reservaMetas) {
        if (mostrarPrevisao) calcularPrevisao(despesasRaw, sobraFinal, totalRenda, reservaMetas = reservaMetas) else null
    }
    val alertasCategoria = remember(despesasRaw, limitesCategoria) { alertasOrcamento(despesasRaw, limitesCategoria) }

    // Widget: atualiza só com o mês corrente aberto (navegar por meses antigos não deve sobrescrever)
    LaunchedEffect(previsao, totalPago, totalDespesasGeral) {
        val p = previsao ?: return@LaunchedEffect
        // Contas pendentes: vencidas primeiro, depois as próximas a vencer
        val contasWidget = despesasRaw
            .filter { it.status == "A pagar" && it.projetoId == null && it.diaVencimento > 0 }
            .sortedWith(compareBy({ it.diaVencimento >= p.diaHoje }, { it.diaVencimento }))
            .take(3)
            .map { ContaWidget(it.descricao, it.valor, it.diaVencimento, vencida = it.diaVencimento < p.diaHoje) }
        salvarDadosWidget(
            context, mes = mesNome.substringBefore(" "), sobra = sobraFinal, aPagar = totalAPagar, pago = totalPago,
            renda = totalRenda, despesas = totalDespesasGeral, limiteDiario = p.limiteDiario, nivel = p.nivel, contas = contasWidget
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MenuLateral(
                drawerState = drawerState,
                coroutineScope = coroutineScope,
                rotaAtual = "dashboard",
                onAbrirDashboard = { coroutineScope.launch { drawerState.close() } },
                onAbrirLancamento = onAbrirLancamento,
                onAbrirAnalytics = onAbrirAnalytics,
                onAbrirSettings = onAbrirSettings,
                onAbrirSobre = onAbrirSobre,
                onAbrirManutencao = onAbrirManutencao,
                onLogout = onLogout,
                onAbrirCartoes = onAbrirCartoes,
                onAbrirCaixinhas = onAbrirCaixinhas,
                onAbrirAssinaturas = onAbrirAssinaturas,
                onAbrirCelular = onAbrirCelular
            )
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Surface(onClick = { mostrarDialogCalendario = true }, shape = RoundedCornerShape(50), color = colorAccent.copy(alpha = 0.1f)) {
                            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(mesNome, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colorAccent)
                                Icon(Icons.Default.ArrowDropDown, null, tint = colorAccent)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, "Abrir menu", tint = colorTextPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { exportarDespesasParaCSV(context, despesasRaw, mesAnoSelecionado) }) {
                            Icon(Icons.Default.FileDownload, "Exportar o mês em CSV", tint = colorAccent)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colorBg)
                )
                AvisoConexao()
            },
            containerColor = colorBg
        ) { padding ->
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)) {

                item {
                    Surface(shape = RoundedCornerShape(20.dp), color = colorSurface, border = BorderStroke(1.dp, colorDivider), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Renda projetada", fontSize = 12.sp, color = colorTextSecondary)
                                    Text(moeda.format(totalRenda), fontSize = 30.sp, fontWeight = FontWeight.Black, color = colorTextPrimary, maxLines = 1)
                                }

                                FilledTonalIconButton(
                                    onClick = { mostrarDialogEmprestimo = true },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color(0xFF43A047).copy(alpha = 0.12f), contentColor = Color(0xFF43A047))
                                ) { Icon(Icons.Default.AccountBalance, "Pegar empréstimo") }

                                FilledTonalIconButton(
                                    onClick = {
                                        if (editandoSaldo) {
                                            val ad = adiantamentoString.paraValor() ?: 0.0
                                            val pg = pagamentoString.paraValor() ?: 0.0
                                            val ex = extraString.paraValor() ?: 0.0
                                            banco.collection("usuarios").document(workspaceUid).collection("saldos").document(mesAnoSelecionado.replace("/", "-"))
                                                .set(mapOf("adiantamento" to ad, "pagamento" to pg, "extra" to ex, "valor" to (ad + pg + ex))).addOnSuccessListener { editandoSaldo = false }
                                            // A renda fixa do mês corrente vira o padrão dos próximos meses (a renda extra não)
                                            if (mesAnoSelecionado == mesAtualStr && ad + pg > 0) {
                                                banco.collection("usuarios").document(workspaceUid)
                                                    .set(mapOf("rendaPadrao" to mapOf("adiantamento" to ad, "pagamento" to pg)), com.google.firebase.firestore.SetOptions.merge())
                                            }
                                        } else { editandoSaldo = true }
                                    },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = colorAccent.copy(alpha = 0.12f), contentColor = colorAccent)
                                ) { Icon(if (editandoSaldo) Icons.Default.Save else Icons.Default.Edit, if (editandoSaldo) "Salvar renda" else "Editar renda") }
                            }

                            if (editandoSaldo) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = adiantamentoString, onValueChange = { if (it.all { char -> char.isDigit() || char == ',' || char == '.' }) adiantamentoString = it },
                                        label = { Text("Vale / adiantamento (até dia 15)") }, prefix = { Text("R$ ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth(), colors = coresCampo, shape = FormatoCampo, singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = pagamentoString, onValueChange = { if (it.all { char -> char.isDigit() || char == ',' || char == '.' }) pagamentoString = it },
                                        label = { Text("Salário (a partir do dia 16)") }, prefix = { Text("R$ ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth(), colors = coresCampo, shape = FormatoCampo, singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = extraString, onValueChange = { if (it.all { char -> char.isDigit() || char == ',' || char == '.' }) extraString = it },
                                        label = { Text("Renda extra / empréstimos") }, prefix = { Text("R$ ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth(), colors = coresCampo, shape = FormatoCampo, singleLine = true
                                    )
                                }
                            }

                            // Resumo do mês
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                EstatisticaResumo(moeda.format(totalAPagar), "a pagar", Color(0xFFFB8C00), Modifier.weight(1f))
                                EstatisticaResumo(moeda.format(totalPago), "pago", Color(0xFF43A047), Modifier.weight(1f))
                                EstatisticaResumo(moeda.format(sobraFinal), "sobra final", if (sobraFinal >= 0) Color(0xFF1E88E5) else Color(0xFFE53935), Modifier.weight(1f))
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            BlocoQuinzena(
                                titulo = "1ª quinzena", subtitulo = "despesas quinzenais",
                                rotuloEntradas = "Vale + extra", entradas = valAdiantamento + valExtra, gastos = totalDespesasQ1,
                                rotuloSobra = "Sobra", sobra = sobraQ1, corSobra = if (sobraQ1 >= 0) Color(0xFF43A047) else Color(0xFFE53935),
                                moeda = moeda, corFundo = colorBg, corTexto = colorTextPrimary, corTextoFraco = colorTextSecondary, corAcento = colorAccent
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            BlocoQuinzena(
                                titulo = "2ª quinzena", subtitulo = "demais despesas",
                                rotuloEntradas = "Salário", entradas = valPagamento, gastos = totalDespesasQ2,
                                rotuloSobra = "Sobra final", sobra = sobraFinal, corSobra = if (sobraFinal >= 0) Color(0xFF1E88E5) else Color(0xFFE53935),
                                moeda = moeda, corFundo = colorBg, corTexto = colorTextPrimary, corTextoFraco = colorTextSecondary, corAcento = colorAccent
                            )
                        }
                    }
                }

                if (comprasDetectadas.isNotEmpty()) {
                    item {
                        AvisoComprasDetectadas(comprasDetectadas, colorAccent, colorSurface, colorTextPrimary, colorTextSecondary) { mostrarCompras = true }
                    }
                }

                if (previsao != null) {
                    item { CardAnalisePreditiva(previsao, isDark) }
                }

                if (alertasCategoria.isNotEmpty()) {
                    item { CardAlertasOrcamento(alertasCategoria, colorSurface, colorDivider, colorTextPrimary, colorTextSecondary, onAbrir = onAbrirAnalytics) }
                }

                if (candidatasRecorrentes.isNotEmpty() && !recorrenciaDispensada) {
                    item {
                        val nomeMesAnterior = remember(mesAnoSelecionado) {
                            val (m, a) = mesAnterior(mesAnoSelecionado).split("/").map { it.toInt() }
                            SimpleDateFormat("MMMM", localeBR).format(Calendar.getInstance().apply { set(a, m - 1, 1) }.time)
                        }
                        AvisoRecorrencia(
                            qtd = candidatasRecorrentes.size, total = candidatasRecorrentes.sumOf { it.valor }, mesOrigem = nomeMesAnterior,
                            cor = colorAccent, corSuperficie = colorSurface, corTexto = colorTextPrimary, corTextoFraco = colorTextSecondary,
                            onAbrir = { mostrarRecorrencia = true },
                            onDispensar = {
                                recorrenciaDispensada = true
                                sharedPref.edit().putBoolean("recorrencia_dispensada_$mesAnoSelecionado", true).apply()
                            }
                        )
                    }
                }

                // === INJETADO: Cotações e Mercado (AwesomeAPI) ===
                item {
                    CotacoesWidget()
                }

                // === INJETADO: Alerta de Feriados Bancários (Brasil API) ===
                item {
                    AlertaFeriadosWidget(despesas = despesasMutaveis, mesAnoSelecionado = mesAnoSelecionado)
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "LANÇAMENTOS · ${despesasRaw.size}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colorTextSecondary,
                            letterSpacing = 1.sp, modifier = Modifier.weight(1f).padding(start = 4.dp)
                        )

                        // Botão do consultor de IA
                        FilledTonalButton(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(34.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = colorAccent.copy(alpha = 0.12f), contentColor = colorAccent),
                            onClick = {
                                mostrarDialogIA = true
                                carregandoIA = true
                                coroutineScope.launch {
                                    try {
                                        val nomeUsuario = user.displayName?.split(" ")?.firstOrNull() ?: "cliente"
                                        fun rs(v: Double) = "R$ " + formatador.format(v)

                                        // Mesmo recorte do resumo: ignora projetos e o que foi adiado para o próximo mês
                                        val despesasMes = despesasRaw.filter { it.projetoId == null && it.status != "Próximo Mês" }
                                        val percentualGasto = if (totalRenda > 0) ((totalDespesasGeral / totalRenda) * 100).toInt() else 0
                                        val gastosFixos = despesasMes.filter { it.tipo == "Fixa" }.sumOf { it.valor }
                                        val gastosVariaveis = despesasMes.filter { it.tipo == "Variável" }.sumOf { it.valor }

                                        val topCategorias = despesasMes.groupBy { it.categoria }
                                            .mapValues { it.value.sumOf { d -> d.valor } }
                                            .entries.sortedByDescending { it.value }.take(4)
                                            .joinToString("\n") { (cat, v) -> "- $cat: ${rs(v)} (${if (totalDespesasGeral > 0) (v / totalDespesasGeral * 100).toInt() else 0}% das despesas)" }
                                        val maioresGastos = despesasMes.sortedByDescending { it.valor }.take(5)
                                            .joinToString("\n") { "- ${it.descricao} (${it.categoria}, ${it.tipo}): ${rs(it.valor)}" }

                                        val blocoProjecao = previsao?.let { p ->
                                            buildString {
                                                appendLine("[PROJEÇÃO DO MÊS CORRENTE]")
                                                appendLine("- Hoje é dia ${p.diaHoje} de ${p.diasNoMes}")
                                                appendLine("- Ritmo de gasto variável: ${rs(p.ritmoDiario)}/dia")
                                                appendLine("- Sobra projetada no fim do mês: ${rs(p.sobraProjetada)}")
                                                appendLine("- Limite diário para não zerar a sobra: ${rs(p.limiteDiario)}")
                                                p.diaZera?.let { appendLine("- No ritmo atual, a sobra zera no dia $it") }
                                                if (p.contasVencidas.isNotEmpty()) appendLine("- Contas vencidas sem pagamento: ${p.contasVencidas.size} (${rs(p.contasVencidas.sumOf { it.valor })})")
                                                if (!p.projecaoConfiavel) appendLine("- Início do mês: projeção ainda instável")
                                            }.trim()
                                        } ?: "[MÊS FECHADO OU FUTURO: sem projeção diária]"

                                        val promptSistema = listOf(
                                            "Você é o Consultor FluxAí, consultor financeiro pessoal dentro de um app brasileiro de controle de gastos.",
                                            "Escreva em português do Brasil, tom direto e acolhedor, falando com o usuário por \"você\".",
                                            "",
                                            "Regras:",
                                            "- Use apenas os números fornecidos. Não invente gastos, rendas, juros ou produtos financeiros.",
                                            "- Cite valores concretos em R$ nas recomendações (ex.: \"cortar R$ 150 em Lazer\").",
                                            "- As ações devem ser possíveis ainda neste mês e ligadas às categorias e lançamentos informados.",
                                            "- Se a renda for zero, diga que ela não foi cadastrada e peça para registrá-la antes de uma análise completa.",
                                            "- Se estiver tudo saudável, reconheça e sugira o próximo passo (reserva de emergência, investir a sobra).",
                                            "- Texto puro: NÃO use markdown (sem **, #, tabelas). Para listas, comece a linha com \"• \".",
                                            "- Máximo de 130 palavras. Sem saudação e sem despedida.",
                                            "",
                                            "Formato exato:",
                                            "Diagnóstico: <1 ou 2 frases sobre a situação do mês>",
                                            "",
                                            "Ponto de atenção: <o maior gargalo, com o valor>",
                                            "",
                                            "O que fazer:",
                                            "• <ação 1 com valor>",
                                            "• <ação 2 com valor>",
                                            "• <ação 3, opcional>"
                                        ).joinToString("\n")

                                        val prompt = listOf(
                                            "Usuário: $nomeUsuario",
                                            "Mês analisado: $mesAnoSelecionado",
                                            "",
                                            "[RENDA]",
                                            "- Total: ${rs(totalRenda)} (1ª quinzena: ${rs(valAdiantamento + valExtra)}, 2ª quinzena: ${rs(valPagamento)})",
                                            "",
                                            "[DESPESAS]",
                                            "- Total: ${rs(totalDespesasGeral)}, comprometendo $percentualGasto% da renda",
                                            "- Fixas: ${rs(gastosFixos)} | Variáveis: ${rs(gastosVariaveis)}",
                                            "- Já pago: ${rs(totalPago)} | Ainda a pagar: ${rs(totalAPagar)}",
                                            "- Sobra da 1ª quinzena: ${rs(sobraQ1)} | Sobra final: ${rs(sobraFinal)}",
                                            "",
                                            "[MAIORES CATEGORIAS]",
                                            topCategorias.ifEmpty { "(nenhuma despesa lançada)" },
                                            "",
                                            "[MAIORES LANÇAMENTOS]",
                                            maioresGastos.ifEmpty { "(nenhum)" },
                                            "",
                                            blocoProjecao
                                        ).joinToString("\n")

                                        contextoIA = prompt
                                        respostaIA = try {
                                            chamarIA("consultor", promptSistema, prompt)
                                                // Garantia caso o modelo ainda mande markdown: o dialog exibe texto puro
                                                .replace("**", "").replace(Regex("(?m)^#+\\s*"), "").replace(Regex("(?m)^[-*]\\s+"), "• ").trim()
                                        } catch (e: FalhaIA) { e.message ?: "Falha na IA." }
                                    } catch(e: Exception) { respostaIA = "Falha interna ao gerar análise." } finally { carregandoIA = false }
                                }
                            }
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Consultor IA", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Barra de busca
                    OutlinedTextField(
                        value = textoPesquisa,
                        onValueChange = { textoPesquisa = it },
                        placeholder = { Text("Pesquisar lançamentos", color = colorTextSecondary) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = colorTextSecondary) },
                        trailingIcon = { if(textoPesquisa.isNotEmpty()) IconButton(onClick = { textoPesquisa = "" }) { Icon(Icons.Default.Close, "Limpar busca", tint = colorTextSecondary) } },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        shape = FormatoCampo,
                        singleLine = true,
                        colors = coresCampo
                    )
                    val listaFiltroFinais = (listOf("Moradia", "Alimentação", "Transporte", "Saúde", "Educação", "Lazer", "Empréstimo", "Cartão de Crédito", "Outros") + categoriasCustomList).distinct()
                    // Quantidade e total por categoria no mês: as que têm lançamentos aparecem primeiro
                    val resumoCategorias = remember(despesasRaw, listaFiltroFinais) {
                        val porCat = despesasRaw.groupBy { it.categoria }
                        listaFiltroFinais.map { cat -> Triple(cat, porCat[cat]?.size ?: 0, porCat[cat]?.sumOf { it.valor } ?: 0.0) }
                            .sortedWith(compareByDescending<Triple<String, Int, Double>> { it.second > 0 }.thenByDescending { it.third })
                    }
                    var filtroAberto by remember { mutableStateOf(false) }
                    val filtrando = categoriaSelecionada != "Todas"

                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { filtroAberto = true },
                            shape = FormatoCampo,
                            color = if (filtrando) colorAccent.copy(alpha = 0.10f) else colorSurface,
                            border = BorderStroke(1.dp, if (filtrando) colorAccent else colorDivider)
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (filtrando) iconeCategoria(categoriaSelecionada) else Icons.Default.FilterList, null,
                                    tint = if (filtrando) corCategoria(categoriaSelecionada) else colorTextSecondary, modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Categoria", fontSize = 11.sp, color = colorTextSecondary)
                                    Text(if (filtrando) categoriaSelecionada else "Todas as categorias", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colorTextPrimary)
                                }
                                if (filtrando) {
                                    IconButton(onClick = { categoriaSelecionada = "Todas" }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Close, "Limpar filtro", tint = colorTextSecondary, modifier = Modifier.size(18.dp))
                                    }
                                }
                                Icon(Icons.Default.ArrowDropDown, null, tint = colorTextSecondary)
                            }
                        }

                        DropdownMenu(
                            expanded = filtroAberto,
                            onDismissRequest = { filtroAberto = false },
                            modifier = Modifier.fillMaxWidth(0.88f).heightIn(max = 420.dp).background(colorSurface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Todas as categorias", fontWeight = FontWeight.SemiBold, color = colorTextPrimary) },
                                leadingIcon = { Icon(Icons.Default.FilterList, null, tint = colorAccent) },
                                trailingIcon = { if (!filtrando) Icon(Icons.Default.Check, null, tint = colorAccent) },
                                onClick = { categoriaSelecionada = "Todas"; filtroAberto = false }
                            )
                            HorizontalDivider(color = colorDivider)
                            resumoCategorias.forEach { (cat, qtd, total) ->
                                val vazia = qtd == 0
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(cat, color = colorTextPrimary.copy(alpha = if (vazia) 0.45f else 1f), fontWeight = if (cat == categoriaSelecionada) FontWeight.Bold else FontWeight.Normal)
                                            if (!vazia) Text("$qtd ${if (qtd == 1) "lançamento" else "lançamentos"} · R$ ${formatador.format(total)}", fontSize = 11.sp, color = colorTextSecondary)
                                        }
                                    },
                                    leadingIcon = {
                                        Box(modifier = Modifier.size(32.dp).background(corCategoria(cat).copy(alpha = if (vazia) 0.06f else 0.15f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                            Icon(iconeCategoria(cat), null, tint = corCategoria(cat).copy(alpha = if (vazia) 0.4f else 1f), modifier = Modifier.size(18.dp))
                                        }
                                    },
                                    trailingIcon = { if (cat == categoriaSelecionada) Icon(Icons.Default.Check, null, tint = colorAccent) },
                                    onClick = { categoriaSelecionada = cat; filtroAberto = false }
                                )
                            }
                        }
                    }
                }

                if (despesasMutaveis.isEmpty()) {
                    item {
                        val buscando = textoPesquisa.isNotBlank() || categoriaSelecionada != "Todas"
                        Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(80.dp).background(colorAccent.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(if (buscando) Icons.Default.SearchOff else Icons.Default.ReceiptLong, null, Modifier.size(40.dp), tint = colorAccent)
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(if (buscando) "Nenhum lançamento encontrado" else "Nenhum lançamento neste mês", fontWeight = FontWeight.Bold, color = colorTextPrimary)
                            Text(
                                if (buscando) "Tente outro termo ou limpe o filtro de categoria." else "Os gastos que você registrar aparecem aqui.",
                                fontSize = 13.sp, color = colorTextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                itemsIndexed(despesasMutaveis, key = { _, d -> d.id }) { _, despesa ->
                    val isDragging = despesa.id == draggedId
                    val offsetY by animateFloatAsState(targetValue = if (isDragging) dragOffset else 0f, label = "")
                    var itemHeightPx by remember { mutableStateOf(1f) }

                    Box(
                        modifier = Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .onSizeChanged { itemHeightPx = it.height.toFloat() }
                            .graphicsLayer { translationY = offsetY }
                            .pointerInput(despesa.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { draggedId = despesa.id },
                                    onDragEnd = {
                                        draggedId = null
                                        dragOffset = 0f

                                        coroutineScope.launch {
                                            try {
                                                val batch = banco.batch()
                                                val novaListaGlobal = despesasRaw.toMutableList()
                                                val visiveisIds = despesasMutaveis.map { it.id }

                                                val idxStart = novaListaGlobal.indexOfFirst { it.id in visiveisIds }.takeIf { it >= 0 } ?: 0
                                                novaListaGlobal.removeAll { it.id in visiveisIds }
                                                novaListaGlobal.addAll(idxStart, despesasMutaveis)

                                                novaListaGlobal.forEachIndexed { index, desp ->
                                                    val ref = banco.collection("usuarios").document(workspaceUid).collection("despesas").document(desp.id)
                                                    batch.update(ref, "ordem", index)
                                                }
                                                batch.commit()
                                            } catch (e: Exception) {
                                                android.util.Log.e("FluxAi_Drag", "Erro ao salvar ordem no banco", e)
                                            }
                                        }
                                    },
                                    onDragCancel = { draggedId = null; dragOffset = 0f },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset += dragAmount.y

                                        val currIdx = despesasMutaveis.indexOfFirst { it.id == despesa.id }
                                        if (currIdx != -1) {
                                            if (dragOffset > itemHeightPx && currIdx < despesasMutaveis.size - 1) {
                                                val temp = despesasMutaveis[currIdx]
                                                despesasMutaveis[currIdx] = despesasMutaveis[currIdx + 1]
                                                despesasMutaveis[currIdx + 1] = temp
                                                dragOffset -= itemHeightPx
                                            } else if (dragOffset < -itemHeightPx && currIdx > 0) {
                                                val temp = despesasMutaveis[currIdx]
                                                despesasMutaveis[currIdx] = despesasMutaveis[currIdx - 1]
                                                despesasMutaveis[currIdx - 1] = temp
                                                dragOffset += itemHeightPx
                                            }
                                        }
                                    }
                                )
                            }
                    ) {
                        Column {
                            DashDespesaCard(
                                despesa = despesa,
                                isDark = isDark,
                                colorAccent = colorAccent,
                                onStatusChange = { n -> mudarStatusDespesa(workspaceUid, despesa, n) { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } },
                                onPagamentoCartao = { despesaParaPagarCartao = despesa },
                                onEditClick = { despesaParaEditar = despesa },
                                onDeleteClick = { despesaParaExcluir = despesa },
                                cartao = despesa.cartaoId?.let { cartoesVisuais[it] },
                                onComprovante = { if (despesa.comprovante.isNullOrBlank()) despesaParaComprovante = despesa else despesaVerComprovante = despesa },
                                pagoPorOutro = despesa.status == "Pago" && despesa.pagoPor != null && despesa.pagoPor != user.uid
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }

        if (despesaParaEditar != null) {
            var eDesc by remember { mutableStateOf(despesaParaEditar!!.descricao) }
            var eVal by remember { mutableStateOf("%.2f".format(despesaParaEditar!!.valor).replace(".", ",")) }
            var eDia by remember { mutableStateOf(despesaParaEditar!!.diaVencimento.toString()) }
            var eMesAno by remember { mutableStateOf(despesaParaEditar!!.mesAno) }
            var eTipo by remember { mutableStateOf(despesaParaEditar!!.tipo) }
            var eFreq by remember { mutableStateOf(despesaParaEditar!!.frequencia) }
            var eCat by remember { mutableStateOf(despesaParaEditar!!.categoria) }
            var expandidoEditCategoria by remember { mutableStateOf(false) }
            val cats = (listOf("Moradia", "Alimentação", "Transporte", "Saúde", "Educação", "Lazer", "Empréstimo", "Cartão de Crédito", "Outros") + categoriasCustomList).distinct()
            var eStatus by remember { mutableStateOf(despesaParaEditar!!.status) }

            AlertDialog(
                onDismissRequest = { despesaParaEditar = null },
                containerColor = colorSurface,
                shape = RoundedCornerShape(28.dp),
                title = { Text("Editar lançamento", fontWeight = FontWeight.Bold, color = colorTextPrimary) },
                text = {
                    // Chips no mesmo estilo da tela de lançamento
                    val coresChip = FilterChipDefaults.filterChipColors(selectedContainerColor = colorAccent.copy(alpha = 0.14f), selectedLabelColor = colorAccent, labelColor = colorTextPrimary)
                    @Composable
                    fun Chip(texto: String, selecionado: Boolean, onClick: () -> Unit) = FilterChip(
                        selected = selecionado, onClick = onClick, label = { Text(texto) }, shape = RoundedCornerShape(12.dp), colors = coresChip,
                        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selecionado, borderColor = colorDivider, selectedBorderColor = colorAccent)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                        OutlinedTextField(
                            value = eVal, onValueChange = { eVal = it }, label = { Text("Valor", color = colorTextSecondary) }, prefix = { Text("R$ ", color = colorTextPrimary) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                            colors = coresCampo, shape = FormatoCampo, singleLine = true
                        )
                        OutlinedTextField(
                            value = eDesc, onValueChange = { eDesc = it }, label = { Text("Descrição", color = colorTextSecondary) }, modifier = Modifier.fillMaxWidth(),
                            colors = coresCampo, shape = FormatoCampo, singleLine = true
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = eDia, onValueChange = { if(it.length <= 2) eDia = it }, label = { Text("Dia", color = colorTextSecondary) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(0.8f),
                                colors = coresCampo, shape = FormatoCampo, singleLine = true
                            )
                            OutlinedTextField(
                                value = eMesAno, onValueChange = { if(it.length <= 7) eMesAno = it }, label = { Text("Mês/ano", color = colorTextSecondary) }, modifier = Modifier.weight(1.2f),
                                colors = coresCampo, shape = FormatoCampo, singleLine = true
                            )
                        }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = eCat, onValueChange = { }, readOnly = true, label = { Text("Categoria", color = colorTextSecondary) }, modifier = Modifier.fillMaxWidth(),
                                colors = coresCampo,
                                leadingIcon = { Icon(iconeCategoria(eCat), null, tint = corCategoria(eCat)) },
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, tint = colorTextSecondary) },
                                shape = FormatoCampo
                            )
                            Box(modifier = Modifier.matchParentSize().clickable { expandidoEditCategoria = !expandidoEditCategoria })
                            DropdownMenu(expanded = expandidoEditCategoria, onDismissRequest = { expandidoEditCategoria = false }, modifier = Modifier.fillMaxWidth(0.8f).background(colorSurface)) {
                                cats.forEach { c ->
                                    DropdownMenuItem(
                                        text = { Text(c, color = colorTextPrimary) },
                                        leadingIcon = { Icon(iconeCategoria(c), null, tint = corCategoria(c), modifier = Modifier.size(20.dp)) },
                                        onClick = { eCat = c; expandidoEditCategoria = false }
                                    )
                                }
                            }
                        }
                        TituloSecaoDashboard("Frequência", colorTextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip("Mensal", eFreq == "Mensal") { eFreq = "Mensal" }
                            Chip("Quinzenal", eFreq == "Quinzenal") { eFreq = "Quinzenal" }
                        }
                        TituloSecaoDashboard("Tipo", colorTextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip("Fixa", eTipo == "Fixa") { eTipo = "Fixa" }
                            Chip("Variável", eTipo == "Variável") { eTipo = "Variável" }
                        }
                        TituloSecaoDashboard("Status", colorTextSecondary)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf("Pago", "A pagar", "Próximo Mês", "Renegociar")) { s ->
                                Chip(s, eStatus == s) { eStatus = s }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val v = eVal.paraValor() ?: 0.0
                            val original = despesaParaEditar!!
                            ajustarFatura(original, v - original.valor)
                            // Saldo da conta bancária acompanha mudanças de valor ou de status
                            banco.batch().also { ajustarSaldoContas(it, workspaceUid, original, original.copy(valor = v, status = eStatus)) }.commit()
                            val quemPagou: Map<String, Any?> = when {
                                eStatus == original.status -> emptyMap()
                                eStatus == "Pago" -> mapOf("pagoPor" to user.uid, "pagoPorNome" to (user.displayName?.split(" ")?.firstOrNull() ?: user.email))
                                else -> mapOf("pagoPor" to null, "pagoPorNome" to null)
                            }
                            val isParcelaOriginal = original.descricao.matches(Regex(".*\\(\\d+/\\d+\\)$"))

                            if (isParcelaOriginal) {
                                val baseDescOriginal = original.descricao.substringBeforeLast(" (")
                                // Busca só as parcelas da mesma compra (prefixo da descrição), não a coleção inteira
                                banco.collection("usuarios").document(workspaceUid).collection("despesas")
                                    .whereGreaterThanOrEqualTo("descricao", "$baseDescOriginal (")
                                    .whereLessThan("descricao", "$baseDescOriginal (\uf8ff")
                                    .get().addOnSuccessListener { query ->
                                    val batch = banco.batch()
                                    query.documents.forEach { doc ->
                                        val descBanco = doc.getString("descricao") ?: ""
                                        if (descBanco.startsWith("$baseDescOriginal (") && descBanco.matches(Regex(".*\\(\\d+/\\d+\\)$"))) {
                                            val ref = banco.collection("usuarios").document(workspaceUid).collection("despesas").document(doc.id)
                                            if (doc.id == original.id) {
                                                batch.update(ref, mapOf("descricao" to eDesc, "valor" to v, "diaVencimento" to (eDia.toIntOrNull() ?: 1), "mesAno" to eMesAno, "categoria" to eCat, "tipo" to eTipo, "frequencia" to eFreq, "status" to eStatus) + quemPagou)
                                            } else {
                                                batch.update(ref, mapOf("categoria" to eCat))
                                            }
                                        }
                                    }
                                    batch.commit().addOnSuccessListener { despesaParaEditar = null }
                                }
                            } else {
                                banco.collection("usuarios").document(workspaceUid).collection("despesas").document(despesaParaEditar!!.id).update(mapOf("descricao" to eDesc, "valor" to v, "diaVencimento" to (eDia.toIntOrNull() ?: 1), "mesAno" to eMesAno, "categoria" to eCat, "tipo" to eTipo, "frequencia" to eFreq, "status" to eStatus) + quemPagou).addOnSuccessListener { despesaParaEditar = null }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colorAccent)
                    ) { Text("Salvar", color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = { despesaParaEditar = null }) { Text("Cancelar", color = colorTextSecondary) }
                }
            )
        }

        if (mostrarDialogCalendario) {
            var anoTemp by remember { mutableIntStateOf(calendar.get(Calendar.YEAR)) }
            val mesesAbrev = listOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")

            AlertDialog(
                onDismissRequest = { mostrarDialogCalendario = false }, containerColor = colorSurface, titleContentColor = colorTextPrimary,
                shape = RoundedCornerShape(28.dp),
                title = { Text("Mês de referência", fontWeight = FontWeight.Bold) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { anoTemp-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Ano anterior", tint = colorTextPrimary) }
                            Text(anoTemp.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colorAccent)
                            IconButton(onClick = { anoTemp++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Próximo ano", tint = colorTextPrimary) }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        val rows = mesesAbrev.chunked(4)
                        Column {
                            rows.forEachIndexed { rowIndex, rowMonths ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    rowMonths.forEachIndexed { colIndex, mesStr ->
                                        val mesIndex = rowIndex * 4 + colIndex
                                        val isSelecionado = calendar.get(Calendar.MONTH) == mesIndex && calendar.get(Calendar.YEAR) == anoTemp
                                        TextButton(
                                            onClick = {
                                                calendar.set(Calendar.YEAR, anoTemp); calendar.set(Calendar.MONTH, mesIndex)
                                                atualizarData() // Título também atualizado ao selecionar
                                                mostrarDialogCalendario = false
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.textButtonColors(containerColor = if (isSelecionado) colorAccent.copy(alpha = 0.14f) else Color.Transparent)
                                        ) { Text(mesStr, color = if (isSelecionado) colorAccent else colorTextPrimary, fontWeight = if (isSelecionado) FontWeight.Bold else FontWeight.Normal) }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { mostrarDialogCalendario = false }) { Text("Cancelar", color = colorTextSecondary) } }
            )
        }

        if (mostrarDialogEmprestimo) {
            var eBanco by remember { mutableStateOf("") }
            var eValorRecebido by remember { mutableStateOf("") }
            var eQtdParcelas by remember { mutableStateOf("") }
            var eValorParcela by remember { mutableStateOf("") }
            var eDiaVencimento by remember { mutableStateOf("") }
            var salvandoEmprestimo by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { mostrarDialogEmprestimo = false },
                containerColor = colorSurface,
                shape = RoundedCornerShape(28.dp),
                icon = {
                    Box(Modifier.size(48.dp).background(Color(0xFF43A047).copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AccountBalance, null, tint = Color(0xFF43A047))
                    }
                },
                title = { Text("Pegar empréstimo", fontWeight = FontWeight.Bold, color = colorTextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text("O valor recebido entra no saldo deste mês, e as parcelas são geradas automaticamente.", fontSize = 13.sp, color = colorTextSecondary, lineHeight = 18.sp)

                        TituloSecaoDashboard("Recebimento", colorTextSecondary)
                        OutlinedTextField(value = eBanco, onValueChange = { eBanco = it }, label = { Text("Banco ou financeira (ex.: Nubank)") }, modifier = Modifier.fillMaxWidth(), shape = FormatoCampo, colors = coresCampo, singleLine = true)
                        OutlinedTextField(value = eValorRecebido, onValueChange = { eValorRecebido = it }, label = { Text("Valor recebido agora") }, prefix = { Text("R$ ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), colors = coresCampo, shape = FormatoCampo, singleLine = true)

                        TituloSecaoDashboard("Parcelas", colorTextSecondary)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = eQtdParcelas, onValueChange = { eQtdParcelas = it }, label = { Text("Quantidade") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), shape = FormatoCampo, colors = coresCampo, singleLine = true)
                            OutlinedTextField(value = eDiaVencimento, onValueChange = { if(it.length <= 2) eDiaVencimento = it }, label = { Text("Vence dia") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), shape = FormatoCampo, colors = coresCampo, singleLine = true)
                        }
                        OutlinedTextField(value = eValorParcela, onValueChange = { eValorParcela = it }, label = { Text("Valor de cada parcela") }, prefix = { Text("R$ ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), colors = coresCampo, shape = FormatoCampo, singleLine = true)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val vRecebido = eValorRecebido.paraValor() ?: 0.0
                            val vParcela = eValorParcela.paraValor() ?: 0.0
                            val qtd = eQtdParcelas.toIntOrNull() ?: 0
                            val dia = eDiaVencimento.toIntOrNull() ?: 0

                            if (eBanco.isNotBlank() && vRecebido > 0 && vParcela > 0 && qtd > 0 && dia > 0) {
                                salvandoEmprestimo = true
                                coroutineScope.launch {
                                    try {
                                        val ad = adiantamentoString.paraValor() ?: 0.0
                                        val pg = pagamentoString.paraValor() ?: 0.0
                                        val exAntigo = extraString.paraValor() ?: 0.0
                                        val exNovo = exAntigo + vRecebido

                                        banco.collection("usuarios").document(workspaceUid).collection("saldos").document(mesAnoSelecionado.replace("/", "-"))
                                            .set(mapOf("adiantamento" to ad, "pagamento" to pg, "extra" to exNovo, "valor" to (ad + pg + exNovo)))

                                        val batch = banco.batch()
                                        val dbRef = banco.collection("usuarios").document(workspaceUid).collection("despesas")
                                        val partesData = mesAnoSelecionado.split("/")
                                        var mesLoop = partesData.getOrNull(0)?.toIntOrNull() ?: 1
                                        var anoLoop = partesData.getOrNull(1)?.toIntOrNull() ?: 2026

                                        for (i in 1..qtd) {
                                            val mesAnoFormato = String.format(Locale("pt", "BR"), "%02d/%04d", mesLoop, anoLoop)
                                            val docRef = dbRef.document()
                                            batch.set(docRef, hashMapOf(
                                                "descricao" to "Empréstimo $eBanco ($i/$qtd)",
                                                "valor" to vParcela,
                                                "diaVencimento" to dia,
                                                "tipo" to "Fixa",
                                                "categoria" to "Empréstimo",
                                                "status" to "A pagar",
                                                "mesAno" to mesAnoFormato,
                                                "frequencia" to "Mensal",
                                                "observacao" to "Gerado via Assistente",
                                                "ordem" to 0
                                            ))
                                            mesLoop++
                                            if (mesLoop > 12) { mesLoop = 1; anoLoop++ }
                                        }
                                        batch.commit().addOnSuccessListener {
                                            mostrarDialogEmprestimo = false
                                            Toast.makeText(context, "Empréstimo Registrado com Sucesso!", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Erro ao salvar", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        salvandoEmprestimo = false
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Preencha todos os campos corretamente", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43A047)),
                        enabled = !salvandoEmprestimo
                    ) {
                        if(salvandoEmprestimo) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        else Text("Confirmar")
                    }
                },
                dismissButton = { TextButton(onClick = { mostrarDialogEmprestimo = false }) { Text("Cancelar", color = colorTextSecondary) } }
            )
        }

        if (mostrarRecorrencia) {
            DialogoRecorrencia(
                candidatas = candidatasRecorrentes, cor = colorAccent, corSuperficie = colorSurface, corTexto = colorTextPrimary, corTextoFraco = colorTextSecondary,
                onConfirmar = { escolhidas ->
                    mostrarRecorrencia = false
                    trazerRecorrentes(workspaceUid, mesAnoSelecionado, escolhidas) { ok ->
                        Toast.makeText(context, if (ok) "${escolhidas.size} contas trazidas para este mês" else "Não foi possível trazer as contas.", Toast.LENGTH_SHORT).show()
                    }
                },
                onFechar = { mostrarRecorrencia = false }
            )
        }

        if (mostrarDialogIA) {
            PainelConsultorIA(
                analise = respostaIA, carregandoAnalise = carregandoIA, contextoDoMes = contextoIA,
                colorAccent = colorAccent, colorSurface = colorSurface, colorTextPrimary = colorTextPrimary, colorTextSecondary = colorTextSecondary,
                onFechar = { mostrarDialogIA = false }
            )
        }

        if (despesaParaExcluir != null) {
            AlertDialog(
                onDismissRequest = { despesaParaExcluir = null },
                containerColor = colorSurface,
                shape = RoundedCornerShape(28.dp),
                icon = { Icon(Icons.Default.DeleteOutline, null, tint = Color(0xFFE53935)) },
                title = { Text("Excluir lançamento?", fontWeight = FontWeight.Bold, color = colorTextPrimary) },
                text = { Text("'${despesaParaExcluir!!.descricao}' será removido deste mês. Essa ação não pode ser desfeita.", color = colorTextSecondary) },
                confirmButton = {
                    Button(
                        onClick = {
                            val despesa = despesaParaExcluir!!
                            ajustarFatura(despesa, -despesa.valor)
                            // Se estava paga por uma conta, o valor volta para ela; a foto do comprovante vai junto
                            banco.batch().also { ajustarSaldoContas(it, workspaceUid, despesa, null) }.commit()
                            apagarArquivoComprovante(despesa)
                            if (despesa.descricao.startsWith("Apontamento:", ignoreCase = true)) {
                                val nomeCaixinha = despesa.descricao.substringAfter("Apontamento:").trim()
                                val valorEstorno = despesa.valor
                                banco.collection("usuarios").document(workspaceUid).collection("caixinhas")
                                    .whereEqualTo("nome", nomeCaixinha).get()
                                    .addOnSuccessListener { query ->
                                        if (!query.isEmpty) {
                                            val docCx = query.documents.first()
                                            val saldoAtual = docCx.getDouble("saldo") ?: 0.0
                                            val novoSaldo = (saldoAtual - valorEstorno).coerceAtLeast(0.0)
                                            banco.collection("usuarios").document(workspaceUid).collection("caixinhas").document(docCx.id).update("saldo", novoSaldo)
                                            banco.collection("usuarios").document(workspaceUid).collection("despesas").document(despesa.id).delete()
                                            Toast.makeText(context, "Estornado da caixinha: $nomeCaixinha", Toast.LENGTH_SHORT).show()
                                        } else {
                                            banco.collection("usuarios").document(workspaceUid).collection("despesas").document(despesa.id).delete()
                                        }
                                    }
                            } else {
                                banco.collection("usuarios").document(workspaceUid).collection("despesas").document(despesa.id).delete()
                            }
                            despesaParaExcluir = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                    ) { Text("Excluir", color = Color.White) }
                },
                dismissButton = { TextButton(onClick = { despesaParaExcluir = null }) { Text("Cancelar", color = colorTextSecondary) } }
            )
        }

        if (despesaParaPagarCartao != null) {
            var valorPagoStr by remember { mutableStateOf("%.2f".format(despesaParaPagarCartao!!.valor).replace(".", ",")) }
            var processandoPagamento by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { despesaParaPagarCartao = null },
                containerColor = colorSurface,
                shape = RoundedCornerShape(28.dp),
                icon = { Icon(Icons.Default.CreditCard, null, tint = colorAccent) },
                title = { Text("Pagamento de cartão", fontWeight = FontWeight.Bold, color = colorTextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Esta despesa está vinculada a um cartão. Ao confirmar, o limite do cartão será liberado.", color = colorTextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                        Surface(shape = FormatoCampo, color = colorAccent.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Valor original", fontSize = 13.sp, color = colorTextSecondary, modifier = Modifier.weight(1f))
                                Text(moeda.format(despesaParaPagarCartao!!.valor), fontWeight = FontWeight.Bold, color = colorTextPrimary)
                            }
                        }

                        OutlinedTextField(
                            value = valorPagoStr,
                            onValueChange = { valorPagoStr = it },
                            label = { Text("Valor pago") },
                            prefix = { Text("R$ ") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            shape = FormatoCampo,
                            colors = coresCampo
                        )
                        Text("Se você pagar um valor menor, o restante será lançado como uma nova despesa pendente no mês seguinte.", fontSize = 12.sp, color = colorTextSecondary, lineHeight = 16.sp)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val valorPago = valorPagoStr.paraValor() ?: 0.0
                            if (valorPago > 0) {
                                processandoPagamento = true
                                coroutineScope.launch {
                                    try {
                                        val despesaRef = banco.collection("usuarios").document(workspaceUid).collection("despesas").document(despesaParaPagarCartao!!.id)
                                        val cartaoRef = banco.collection("usuarios").document(workspaceUid).collection("cartoes").document(despesaParaPagarCartao!!.cartaoId!!)

                                        banco.runTransaction { transaction ->
                                            val cartaoSnap = transaction.get(cartaoRef)
                                            if (cartaoSnap.exists()) {
                                                val faturaAtual = cartaoSnap.getDouble("faturaAtual") ?: 0.0
                                                val novaFatura = (faturaAtual - valorPago).coerceAtLeast(0.0)
                                                transaction.update(cartaoRef, "faturaAtual", novaFatura)
                                            }

                                            val pagador = user.displayName?.split(" ")?.firstOrNull() ?: user.email
                                            if (valorPago >= despesaParaPagarCartao!!.valor) {
                                                transaction.update(despesaRef, "status", "Pago", "pagoPor", user.uid, "pagoPorNome", pagador)
                                            } else {
                                                transaction.update(despesaRef, "status", "Pago", "valor", valorPago, "observacao", "Pagamento parcial", "pagoPor", user.uid, "pagoPorNome", pagador)

                                                val restante = despesaParaPagarCartao!!.valor - valorPago

                                                val partesData = despesaParaPagarCartao!!.mesAno.split("/")
                                                var mesProx = partesData.getOrNull(0)?.toIntOrNull() ?: 1
                                                var anoProx = partesData.getOrNull(1)?.toIntOrNull() ?: 2026
                                                mesProx++
                                                if (mesProx > 12) { mesProx = 1; anoProx++ }
                                                val proximoMesFormato = String.format(java.util.Locale("pt", "BR"), "%02d/%04d", mesProx, anoProx)

                                                val novaRef = banco.collection("usuarios").document(workspaceUid).collection("despesas").document()
                                                val novaDespesa = hashMapOf(
                                                    "descricao" to "Restante: ${despesaParaPagarCartao!!.descricao}",
                                                    "valor" to restante,
                                                    "diaVencimento" to despesaParaPagarCartao!!.diaVencimento,
                                                    "tipo" to despesaParaPagarCartao!!.tipo,
                                                    "categoria" to despesaParaPagarCartao!!.categoria,
                                                    "status" to "A pagar",
                                                    "mesAno" to proximoMesFormato,
                                                    "cartaoId" to despesaParaPagarCartao!!.cartaoId,
                                                    "frequencia" to "Única",
                                                    "ordem" to 0
                                                )
                                                transaction.set(novaRef, novaDespesa)
                                            }
                                        }.addOnSuccessListener {
                                            Toast.makeText(context, "Pagamento registrado e limite liberado!", Toast.LENGTH_SHORT).show()
                                            despesaParaPagarCartao = null
                                        }.addOnFailureListener {
                                            Toast.makeText(context, "Erro ao atualizar fatura.", Toast.LENGTH_SHORT).show()
                                        }
                                    } finally {
                                        processandoPagamento = false
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Insira um valor válido.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colorAccent),
                        enabled = !processandoPagamento
                    ) {
                        if (processandoPagamento) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        else Text("Confirmar pagamento", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { despesaParaPagarCartao = null }) { Text("Cancelar", color = colorTextSecondary) }
                }
            )
        }

        if (mostrarCompras) {
            DialogoComprasDetectadas(
                compras = comprasDetectadas, workspaceUid = workspaceUid,
                cartoes = cartoesVisuais, contas = contasBancarias,
                cor = colorAccent, corSuperficie = colorSurface, corTexto = colorTextPrimary, corTextoFraco = colorTextSecondary,
                onFechar = { mostrarCompras = false }
            )
        }

        // Anexar comprovante a um lançamento já existente: foto ou galeria
        val cameraComprovante = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { ok ->
            val d = despesaParaComprovante; val uri = fotoComprovante
            if (ok && d != null && uri != null) enviarComprovanteComAviso(context, workspaceUid, d, uri, coroutineScope)
            despesaParaComprovante = null
        }
        val galeriaComprovante = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
            val d = despesaParaComprovante
            if (d != null && uri != null) enviarComprovanteComAviso(context, workspaceUid, d, uri, coroutineScope)
            despesaParaComprovante = null
        }
        val pdfComprovante = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
            val d = despesaParaComprovante
            if (d != null && uri != null) enviarComprovanteComAviso(context, workspaceUid, d, uri, coroutineScope)
            despesaParaComprovante = null
        }
        val permissaoCamera = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) {
                val arquivo = java.io.File.createTempFile("comprovante_", ".jpg", context.cacheDir)
                fotoComprovante = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", arquivo)
                cameraComprovante.launch(fotoComprovante!!)
            } else despesaParaComprovante = null
        }
        despesaParaComprovante?.let { d ->
            AlertDialog(
                onDismissRequest = { despesaParaComprovante = null },
                containerColor = colorSurface,
                shape = RoundedCornerShape(28.dp),
                icon = { Icon(Icons.Default.Receipt, null, tint = colorAccent) },
                title = { Text("Anexar comprovante", fontWeight = FontWeight.Bold, color = colorTextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Foto ou PDF do recibo de \"${d.descricao}\". Fica guardado com o lançamento e ajuda na declaração do IR.", color = colorTextSecondary)
                        Spacer(Modifier.height(4.dp))
                        listOf(
                            Triple("Tirar foto", Icons.Default.PhotoCamera) { permissaoCamera.launch(android.Manifest.permission.CAMERA) },
                            Triple("Escolher da galeria", Icons.Default.PhotoLibrary) { galeriaComprovante.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            Triple("Escolher PDF", Icons.Default.PictureAsPdf) { pdfComprovante.launch(arrayOf("application/pdf")) }
                        ).forEach { (rotulo, icone, acao) ->
                            OutlinedButton(onClick = acao, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                Icon(icone, null, Modifier.size(18.dp), tint = colorAccent); Spacer(Modifier.width(8.dp)); Text(rotulo, color = colorAccent, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { despesaParaComprovante = null }) { Text("Cancelar", color = colorTextSecondary) } }
            )
        }
        despesaVerComprovante?.let { d ->
            DialogoComprovante(d, workspaceUid, coresTela(), onFechar = { despesaVerComprovante = null })
        }

        if (mostrarUpdateDialog) {
            // Visual da marca (mesmo da splash): faixa escura com a logo e o salto de versão em destaque
            androidx.compose.ui.window.Dialog(onDismissRequest = { }) {
                Surface(shape = RoundedCornerShape(28.dp), color = colorSurface, modifier = Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.fillMaxWidth().height(150.dp).background(Brush.verticalGradient(listOf(FundoMarca, FundoMarcaRoxo))),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(Modifier.size(170.dp).background(Brush.radialGradient(listOf(RoxoMarca.copy(alpha = 0.35f), Color.Transparent)), CircleShape))
                            Image(painterResource(R.drawable.logo_app), "Logo FluxAí", modifier = Modifier.size(170.dp), contentScale = ContentScale.Fit)
                        }

                        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(shape = RoundedCornerShape(50), color = colorAccent.copy(alpha = 0.12f)) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.SystemUpdate, null, tint = colorAccent, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("NOVA VERSÃO", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = colorAccent)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("Tem novidade no FluxAí", fontWeight = FontWeight.Black, fontSize = 21.sp, color = colorTextPrimary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Preparamos melhorias e novas funções para deixar sua gestão financeira ainda mais inteligente.",
                                fontSize = 14.sp, color = colorTextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 20.sp
                            )

                            // Versão instalada -> versão nova
                            Spacer(Modifier.height(16.dp))
                            Surface(shape = RoundedCornerShape(14.dp), color = colorBg, modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Instalada", fontSize = 11.sp, color = colorTextSecondary)
                                        Text("v${BuildConfig.VERSION_NAME}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colorTextSecondary)
                                    }
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colorTextSecondary, modifier = Modifier.padding(horizontal = 16.dp))
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Nova", fontSize = 11.sp, color = colorAccent)
                                        Text("v1.0.$vServidorState", fontSize = 15.sp, fontWeight = FontWeight.Black, color = colorAccent)
                                    }
                                }
                            }

                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = { baixarEInstalarApk(context, updateUrl, vServidorState); mostrarUpdateDialog = false },
                                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colorAccent)
                            ) {
                                Icon(Icons.Default.Download, null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Atualizar agora", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            TextButton(onClick = { mostrarUpdateDialog = false }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                Text("Instalar depois", color = colorTextSecondary, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Título de seção em caixa alta, igual ao das outras telas
@Composable
private fun TituloSecaoDashboard(titulo: String, cor: Color) {
    Text(titulo.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cor, letterSpacing = 1.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
}

// Número em destaque com rótulo pequeno embaixo (mesmo padrão do resumo de Assinaturas)
@Composable
private fun EstatisticaResumo(valor: String, rotulo: String, cor: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(valor, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = cor, maxLines = 1)
        Text(rotulo, fontSize = 11.sp, color = Color(0xFF9CA3AF))
    }
}

// Bloco de cada quinzena: entradas, gastos e sobra, com barra de quanto das entradas já foi comprometido
@Composable
private fun BlocoQuinzena(
    titulo: String, subtitulo: String, rotuloEntradas: String, entradas: Double, gastos: Double, rotuloSobra: String, sobra: Double,
    corSobra: Color, moeda: java.text.NumberFormat, corFundo: Color, corTexto: Color, corTextoFraco: Color, corAcento: Color
) {
    Surface(shape = RoundedCornerShape(14.dp), color = corFundo, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(titulo, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = corTexto)
                Text(" · $subtitulo", fontSize = 12.sp, color = corTextoFraco)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (entradas > 0) (gastos / entradas).toFloat().coerceIn(0f, 1f) else if (gastos > 0) 1f else 0f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = if (sobra >= 0) corAcento else Color(0xFFE53935), trackColor = corAcento.copy(alpha = 0.12f),
                drawStopIndicator = {}
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                EstatisticaResumo(moeda.format(entradas), rotuloEntradas, corTexto, Modifier.weight(1f))
                EstatisticaResumo(moeda.format(gastos), "Gastos", corTexto, Modifier.weight(1f))
                EstatisticaResumo(moeda.format(sobra), rotuloSobra, corSobra, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun DashDespesaCard(
    despesa: Despesa,
    isDark: Boolean,
    colorAccent: Color,
    onStatusChange: (String) -> Unit,
    onPagamentoCartao: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    cartao: Pair<String, String>? = null, // (nome, bandeira) do cartão usado, se houver
    onComprovante: (() -> Unit)? = null,
    pagoPorOutro: Boolean = false         // conta conjunta: mostra quem pagou quando não foi o usuário atual
) {
    var menuOpen by remember { mutableStateOf(false) }
    val moeda = remember { java.text.NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }

    val pago = despesa.status == "Pago"
    val ehProjeto = despesa.projetoId != null
    val azulProjeto = Color(0xFF03A9F4)
    val corTexto = if (isDark) Color.White else Color(0xFF1A1A1A)
    val corFraca = Color(0xFF9CA3AF)
    val corStatus = when (despesa.status) {
        "Pago" -> Color(0xFF43A047)
        "A pagar" -> Color(0xFFFB8C00)
        "Próximo Mês" -> Color(0xFF1E88E5)
        "Renegociar" -> Color(0xFFE53935)
        else -> corFraca
    }
    val corBorda = if (ehProjeto) azulProjeto.copy(alpha = 0.45f) else if (isDark) Color(0xFF2C2C2C) else Color(0xFFE5E7EB)

    // Linha secundária: vencimento (ou projeto) · tipo · frequência
    val detalhes = listOfNotNull(
        if (ehProjeto) "Projeto" else despesa.diaVencimento.takeIf { it > 0 }?.let { "Dia $it" },
        cartao?.first?.takeIf { it.isNotBlank() },
        if (pagoPorOutro) despesa.pagoPorNome?.let { "pago por $it" } else null,
        despesa.tipo,
        despesa.frequencia
    ).joinToString(" · ")

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isDark) Color(0xFF1A1A1A) else Color.White,
        border = BorderStroke(1.dp, corBorda),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(start = 4.dp, end = 2.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {

            // Baixa Rápida com 1 Clique
            IconButton(
                onClick = { if (despesa.status != "Pago") { if (despesa.cartaoId != null) onPagamentoCartao() else onStatusChange("Pago") } else onStatusChange("A pagar") }
            ) {
                Icon(
                    if (pago) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    if (pago) "Marcar como a pagar" else "Marcar como paga",
                    tint = if (pago) Color(0xFF43A047) else corFraca
                )
            }

            if (ehProjeto) {
                Box(Modifier.size(42.dp).background(azulProjeto.copy(alpha = 0.14f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Work, null, tint = azulProjeto, modifier = Modifier.size(22.dp))
                }
            } else {
                val logoBandeira = cartao?.second?.let { logoBandeiraCartao(it) }
                val bordaSelo = if (isDark) Color(0xFF2C2C2C) else Color(0xFFE5E7EB)
                when {
                    // Compra no cartão sem marca reconhecida: a bandeira vira o ícone
                    logoBandeira != null && marcaPorNome(despesa.descricao) == null -> Box(
                        Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, bordaSelo, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(painterResource(logoBandeira), cartao.second, contentScale = ContentScale.Fit, modifier = Modifier.padding(horizontal = 5.dp).fillMaxWidth())
                    }
                    // Marca reconhecida (ex.: Netflix) no cartão: logo da marca com selo da bandeira no canto
                    logoBandeira != null -> Box(Modifier.size(46.dp)) {
                        Box(Modifier.align(Alignment.TopStart)) { IconeLancamento(despesa.descricao, iconeCategoria(despesa.categoria), corCategoria(despesa.categoria)) }
                        Box(
                            Modifier.align(Alignment.BottomEnd).size(width = 24.dp, height = 16.dp).clip(RoundedCornerShape(4.dp))
                                .background(Color.White).border(1.dp, bordaSelo, RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(painterResource(logoBandeira), cartao.second, contentScale = ContentScale.Fit, modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp))
                        }
                    }
                    else -> IconeLancamento(despesa.descricao, iconeCategoria(despesa.categoria), corCategoria(despesa.categoria))
                }
            }
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                // Descrição completa, quebrando linha quando for longa (sem cortar com "...")
                Text(
                    despesa.descricao, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 19.sp,
                    color = if (pago) corTexto.copy(alpha = 0.6f) else corTexto
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!despesa.comprovante.isNullOrBlank()) {
                        Icon(Icons.Default.AttachFile, "Tem comprovante", tint = corFraca, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(2.dp))
                    }
                    Text(detalhes, fontSize = 12.sp, color = corFraca, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }

            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(moeda.format(despesa.valor), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = corTexto)
                Text(despesa.status, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = corStatus)
            }

            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Mais opções", tint = corFraca) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, modifier = Modifier.background(if (isDark) Color(0xFF1A1A1A) else Color.White)) {
                    listOf(
                        "Pago" to Icons.Default.CheckCircle,
                        "A pagar" to Icons.Default.Schedule,
                        "Próximo Mês" to Icons.Default.EventRepeat,
                        "Renegociar" to Icons.Default.Handshake
                    ).forEach { (s, icone) ->
                        DropdownMenuItem(
                            text = { Text(s, color = corTexto, fontWeight = if (s == despesa.status) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = { Icon(icone, null, tint = if (s == despesa.status) colorAccent else corFraca) },
                            trailingIcon = { if (s == despesa.status) Icon(Icons.Default.Check, null, tint = colorAccent) },
                            onClick = {
                                if (s == "Pago" && despesa.cartaoId != null) {
                                    onPagamentoCartao()
                                } else {
                                    onStatusChange(s)
                                }
                                menuOpen = false
                            }
                        )
                    }
                    HorizontalDivider(color = if (isDark) Color(0xFF2C2C2C) else Color(0xFFE5E7EB))
                    DropdownMenuItem(text = { Text("Editar", color = corTexto) }, leadingIcon = { Icon(Icons.Default.Edit, null, tint = colorAccent) }, onClick = { onEditClick(); menuOpen = false })
                    if (onComprovante != null) DropdownMenuItem(
                        text = { Text(if (despesa.comprovante.isNullOrBlank()) "Anexar comprovante" else "Ver comprovante", color = corTexto) },
                        leadingIcon = { Icon(Icons.Default.Receipt, null, tint = colorAccent) },
                        onClick = { onComprovante(); menuOpen = false }
                    )
                    DropdownMenuItem(text = { Text("Excluir", color = Color(0xFFE53935)) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFE53935)) }, onClick = { onDeleteClick(); menuOpen = false })
                }
            }
        }
    }
}

// Evita consultar o GitHub de novo a cada volta ao dashboard
private var atualizacaoVerificadaEm = 0L
private const val INTERVALO_VERIFICAR_ATUALIZACAO_MS = 2 * 60 * 1000L // o GitHub aceita 60 consultas/hora sem login

// === INJETADO COM RELEVO E ORGANIZAÇÃO PREMIUM: Câmbio e Mercado (AwesomeAPI) ===
// Cache em memória: o card sai e volta da tela ao rolar a lista; sem isso ele sumia, buscava de novo e a lista "pulava"
private var cacheCotacoes: List<Triple<String, String, Color>> = emptyList()
private var cacheCotacoesEm = 0L
private const val VALIDADE_COTACOES_MS = 10 * 60 * 1000L

@Composable
fun CotacoesWidget() {
    var cotacoes by remember { mutableStateOf(cacheCotacoes) }
    var carregando by remember { mutableStateOf(cacheCotacoes.isEmpty()) }
    val isDark = LocalDarkTheme.current
    val colorSurface = if (isDark) Color(0xFF1A1A1A) else Color.White
    val colorTextPrimary = if (isDark) Color.White else Color(0xFF1A1A1A)
    val colorAccent = LocalAccentColor.current

    LaunchedEffect(Unit) {
        if (cacheCotacoes.isNotEmpty() && System.currentTimeMillis() - cacheCotacoesEm < VALIDADE_COTACOES_MS) return@LaunchedEffect
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val url = java.net.URL("https://economia.awesomeapi.com.br/last/USD-BRL,EUR-BRL,BTC-BRL")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                // FIX: Adicionado cabeçalho User-Agent para contornar o bloqueio 403 da AwesomeAPI
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                conn.connectTimeout = 8000

                if (conn.responseCode == 200) {
                    val resposta = conn.inputStream.bufferedReader().readText()
                    val json = org.json.JSONObject(resposta)

                    val usd = json.getJSONObject("USDBRL").getString("bid").toDoubleOrNull() ?: 0.0
                    val eur = json.getJSONObject("EURBRL").getString("bid").toDoubleOrNull() ?: 0.0
                    val btcRaw = json.getJSONObject("BTCBRL").getString("bid").toDoubleOrNull() ?: 0.0

                    cotacoes = listOf(
                        Triple("Dólar", "R$ %.2f".format(usd), Color(0xFFE8F5E8)),
                        Triple("Euro", "R$ %.2f".format(eur), Color(0xFFE3F2FD)),
                        Triple("Bitcoin", "R$ %.2f".format(btcRaw / 1000) + "k", Color(0xFFFFF3E0))
                    )
                    cacheCotacoes = cotacoes
                    cacheCotacoesEm = System.currentTimeMillis()
                }
            } catch (e: Exception) {
                android.util.Log.e("FLUXAI_COTACOES", "Erro: ${e.message}")
            } finally {
                carregando = false
            }
        }
    }

    if (!carregando && cotacoes.isNotEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = colorSurface,
            border = BorderStroke(1.dp, if (isDark) Color(0xFF2C2C2C) else Color(0xFFE5E7EB))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Ícone global do Card unificado e limpo
                    Icon(Icons.Default.Language, null, tint = colorAccent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Câmbio e mercado", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colorTextPrimary)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cotacoes.forEach { (moeda, valor, corFundo) ->
                        val colTexto = when(moeda) {
                            "Dólar" -> Color(0xFF2E7D32)
                            "Euro" -> Color(0xFF1565C0)
                            else -> Color(0xFFE65100)
                        }
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isDark) corFundo.copy(alpha = 0.08f) else corFundo),
                            border = BorderStroke(1.dp, colTexto.copy(alpha = 0.15f))
                        ) {
                            Column(modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(colTexto.copy(alpha = 0.12f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // CORREÇÃO: Ícones exclusivos e perfeitamente mapeados para cada ativo de mercado
                                    Icon(
                                        imageVector = when(moeda) {
                                            "Dólar" -> Icons.Default.AttachMoney
                                            "Euro" -> Icons.Default.Payments
                                            else -> Icons.Default.Toll
                                        },
                                        contentDescription = null,
                                        tint = colTexto,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(moeda, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colTexto.copy(alpha = 0.8f))
                                Spacer(Modifier.height(2.dp))
                                Text(valor, fontSize = 14.sp, fontWeight = FontWeight.Black, color = colTexto)
                            }
                        }
                    }
                }
            }
        }
    }
}

// === INJETADO COM RELEVO E ORGANIZAÇÃO PREMIUM: Alerta de Feriados Bancários (Brasil API) ===
private val cacheFeriados = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>() // ano -> ("MM-dia" -> feriado)
@Composable
fun AlertaFeriadosWidget(despesas: List<Despesa>, mesAnoSelecionado: String) {
    val isDark = LocalDarkTheme.current
    val partes = mesAnoSelecionado.split("/")
    val mes = partes.getOrNull(0)
    val ano = partes.getOrNull(1)

    // Feriados do ano: vêm do cache em memória ou são buscados uma única vez por ano
    var feriadosAno by remember(ano) { mutableStateOf(ano?.let { cacheFeriados[it] }) }
    LaunchedEffect(ano) {
        if (ano == null || feriadosAno != null) return@LaunchedEffect
        feriadosAno = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val url = java.net.URL("https://brasilapi.com.br/api/feriados/v1/$ano")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"

                if (conn.responseCode == 200) {
                    val resposta = conn.inputStream.bufferedReader().readText()
                    val jsonArray = org.json.JSONArray(resposta)

                    // Chave "MM-dia" -> nome do feriado
                    val feriados = mutableMapOf<String, String>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val partesData = obj.getString("date").split("-")
                        feriados["${partesData[1]}-${partesData[2].toInt()}"] = obj.getString("name")
                    }
                    cacheFeriados[ano] = feriados
                    feriados
                } else null
            } catch (e: Exception) {
                android.util.Log.e("FLUXAI_FERIADOS", "Erro: ${e.message}")
                null
            }
        }
    }

    // Contas a pagar que vencem em feriado; recalculado direto da lista, sem nova busca na rede
    val alertasFeriado = feriadosAno?.let { feriados ->
        despesas.filter { it.status == "A pagar" && it.projetoId == null }
            .mapNotNull { despesa -> feriados["$mes-${despesa.diaVencimento}"]?.let { despesa to it } }
    } ?: emptyList()

    if (alertasFeriado.isNotEmpty()) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            alertasFeriado.forEach { (despesa, feriado) ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = if (isDark) Color(0xFF2C2414) else Color(0xFFFFFDE7),
                    border = BorderStroke(1.dp, Color(0xFFF57F17).copy(alpha = 0.3f))
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFFF57F17).copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Event, contentDescription = "Feriado", tint = Color(0xFFF57F17), modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "Oportunidade de rendimento", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "A conta '${despesa.descricao}' vence no feriado de $feriado. Você pode manter este dinheiro rendendo e pagar no próximo dia útil sem multas.",
                                fontWeight = FontWeight.Medium, fontSize = 13.sp, color = if (isDark) Color(0xFFE0E0E0) else Color(0xFF5D4037), lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
// =========================================================================
// ANÁLISE PREDITIVA: projeção do mês corrente a partir do ritmo de gastos
// =========================================================================
enum class NivelPrevisao { OK, ATENCAO, RISCO, INFO }

data class PrevisaoFinanceira(
    val nivel: NivelPrevisao,
    val titulo: String,
    val mensagem: String,
    val diaHoje: Int,
    val diasNoMes: Int,
    val ritmoDiario: Double,        // média de gasto variável por dia até hoje
    val sobraProjetada: Double,     // sobra final estimada no último dia do mês
    val limiteDiario: Double,       // quanto dá para gastar por dia sem zerar a sobra
    val diaZera: Int?,              // dia em que a sobra zera no ritmo atual (null = não zera)
    val contasVencidas: List<Despesa>,
    val contasProximas: List<Despesa>, // vencem nos próximos 3 dias
    val projecaoConfiavel: Boolean, // no começo do mês a média ainda oscila muito
    val reservaMetas: Double = 0.0  // já descontado da sobra e do limite diário
)

private fun brl(v: Double) = "R$ %,.2f".format(Locale("pt", "BR"), v)

// reservaMetas: quanto ainda falta guardar neste mês para as metas com prazo; sai da sobra disponível
fun calcularPrevisao(despesas: List<Despesa>, sobraTotal: Double, totalRenda: Double, hoje: Calendar = Calendar.getInstance(), reservaMetas: Double = 0.0): PrevisaoFinanceira {
    val sobraFinal = sobraTotal - reservaMetas
    val diaHoje = hoje.get(Calendar.DAY_OF_MONTH)
    val diasNoMes = hoje.getActualMaximum(Calendar.DAY_OF_MONTH)
    val diasRestantes = diasNoMes - diaHoje

    val doMes = despesas.filter { it.projetoId == null && it.status != "Próximo Mês" }
    val totalVariavel = doMes.filter { it.tipo == "Variável" }.sumOf { it.valor }
    val ritmoDiario = if (totalVariavel > 0) totalVariavel / diaHoje else 0.0

    // A sobra final já desconta tudo que foi lançado; projeta só o gasto variável que ainda virá
    val sobraProjetada = sobraFinal - ritmoDiario * diasRestantes
    val limiteDiario = if (diasRestantes > 0 && sobraFinal > 0) sobraFinal / diasRestantes else 0.0
    val diaZera = if (sobraFinal > 0 && ritmoDiario > 0) {
        val dias = (sobraFinal / ritmoDiario).toInt()
        if (dias < diasRestantes) diaHoje + dias else null
    } else null

    val aPagar = doMes.filter { it.status == "A pagar" && it.diaVencimento in 1..diasNoMes }
    val vencidas = aPagar.filter { it.diaVencimento < diaHoje }.sortedBy { it.diaVencimento }
    val proximas = aPagar.filter { it.diaVencimento in diaHoje..(diaHoje + 3) }.sortedBy { it.diaVencimento }
    val margem = if (totalRenda > 0) sobraProjetada / totalRenda else 0.0

    val (nivel, titulo, mensagem) = when {
        sobraFinal <= 0 -> Triple(NivelPrevisao.RISCO, "Orçamento estourado",
            "Suas despesas já superam a renda do mês em ${brl(-sobraFinal)}. Segure os gastos variáveis e veja o que pode ficar para o próximo mês.")
        ritmoDiario == 0.0 -> Triple(NivelPrevisao.INFO, "Sem gastos variáveis ainda",
            "Você pode gastar até ${brl(limiteDiario)} por dia até o fim do mês sem zerar a sobra.")
        diaZera != null -> Triple(NivelPrevisao.RISCO, "A sobra zera no dia $diaZera",
            "No ritmo atual de ${brl(ritmoDiario)}/dia, faltariam ${brl(-sobraProjetada)} no fim do mês. Para fechar no azul, limite-se a ${brl(limiteDiario)}/dia.")
        margem < 0.10 -> Triple(NivelPrevisao.ATENCAO, "Margem apertada",
            "No ritmo atual de ${brl(ritmoDiario)}/dia, você fecha o mês com só ${brl(sobraProjetada)} de sobra. Qualquer imprevisto pesa.")
        else -> Triple(NivelPrevisao.OK, "Ritmo saudável",
            "No ritmo atual de ${brl(ritmoDiario)}/dia, você fecha o mês com cerca de ${brl(sobraProjetada)} de sobra.")
    }

    return PrevisaoFinanceira(nivel, titulo, mensagem, diaHoje, diasNoMes, ritmoDiario, sobraProjetada, limiteDiario, diaZera, vencidas, proximas, projecaoConfiavel = diaHoje >= 5, reservaMetas = reservaMetas)
}

@Composable
fun CardAnalisePreditiva(previsao: PrevisaoFinanceira, isDark: Boolean) {
    val (cor, icone) = when (previsao.nivel) {
        NivelPrevisao.OK -> Color(0xFF2E7D32) to Icons.AutoMirrored.Filled.TrendingUp
        NivelPrevisao.ATENCAO -> Color(0xFFEF6C00) to Icons.Default.Warning
        NivelPrevisao.RISCO -> Color(0xFFD32F2F) to Icons.Default.Error
        NivelPrevisao.INFO -> Color(0xFF1565C0) to Icons.Default.QueryStats
    }
    // No tema escuro usa um tom mais claro da cor e fundo translúcido, no lugar dos pastéis fixos
    val corTexto = if (isDark) androidx.compose.ui.graphics.lerp(cor, Color.White, 0.35f) else cor
    val corFundo = cor.copy(alpha = if (isDark) 0.18f else 0.10f)

    Surface(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), shape = RoundedCornerShape(16.dp), color = corFundo, border = BorderStroke(1.dp, cor.copy(alpha = 0.25f))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icone, contentDescription = null, tint = corTexto, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("ANÁLISE PREDITIVA", fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.sp, color = corTexto.copy(alpha = 0.8f))
                    Text(previsao.titulo, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = corTexto)
                }
                Text("Dia ${previsao.diaHoje}/${previsao.diasNoMes}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = corTexto.copy(alpha = 0.8f))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(previsao.mensagem, fontSize = 13.sp, lineHeight = 18.sp, color = corTexto.copy(alpha = 0.95f))

            // Quanto do mês já passou
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { previsao.diaHoje.toFloat() / previsao.diasNoMes },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = corTexto, trackColor = cor.copy(alpha = 0.15f)
            )

            if (previsao.ritmoDiario > 0 || previsao.limiteDiario > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sobra no fim do mês", fontSize = 11.sp, color = corTexto.copy(alpha = 0.75f))
                        Text(brl(previsao.sobraProjetada), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = corTexto)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pode gastar por dia", fontSize = 11.sp, color = corTexto.copy(alpha = 0.75f))
                        Text(brl(previsao.limiteDiario), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = corTexto)
                    }
                }
            }

            if (previsao.contasVencidas.isNotEmpty()) {
                val qtd = previsao.contasVencidas.size
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "$qtd ${if (qtd == 1) "conta vencida" else "contas vencidas"} sem pagamento: ${brl(previsao.contasVencidas.sumOf { it.valor })}",
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (isDark) Color(0xFFEF9A9A) else Color(0xFFC62828)
                )
            }
            if (previsao.contasProximas.isNotEmpty()) {
                val nomes = previsao.contasProximas.take(3).joinToString(", ") { "${it.descricao} (dia ${it.diaVencimento})" }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Vencem nos próximos 3 dias: $nomes", fontSize = 12.sp, color = corTexto.copy(alpha = 0.85f))
            }

            if (previsao.reservaMetas > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text("Já considera ${brl(previsao.reservaMetas)} para guardar nas metas este mês.", fontSize = 12.sp, color = corTexto.copy(alpha = 0.85f))
            }

            if (!previsao.projecaoConfiavel && previsao.ritmoDiario > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text("Início do mês: a projeção ainda pode variar bastante.", fontSize = 11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = corTexto.copy(alpha = 0.7f))
            }
        }
    }
}
