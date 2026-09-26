package fluxai.app

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.text.style.TextAlign
import com.google.firebase.firestore.ListenerRegistration
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import fluxai.app.ui.theme.LocalAccentColor
import fluxai.app.ui.theme.LocalDarkTheme
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
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
    val view = LocalView.current
    val usuario = Firebase.auth.currentUser
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val isDark = LocalDarkTheme.current

    LaunchedEffect(isDark) {
        val window = (context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
    }

    // =========================================================================
    // === INJETADO: CHAVE MESTRA DA SINCRONIZAÇÃO FAMILIAR ===
    // =========================================================================
    val sharedPref = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
    val workspaceUid = sharedPref.getString("workspace_uid", usuario?.uid ?: "") ?: usuario?.uid ?: ""
    // =========================================================================

    var descricao by remember { mutableStateOf("") }
    var valor by remember { mutableStateOf("") }
    var diaVencimento by remember { mutableStateOf("") }
    var observacao by remember { mutableStateOf("") }
    var tipoDespesa by remember { mutableStateOf("Variável") }
    var frequenciaDespesa by remember { mutableStateOf("Mensal") }
    var categoriaDespesa by remember { mutableStateOf("Outros") }

    // --- VARIÁVEIS DO PARCELAMENTO ---
    var isParcelado by remember { mutableStateOf(false) }
    var qtdParcelas by remember { mutableStateOf("") }

    var calendar by remember { mutableStateOf(Calendar.getInstance()) }
    var mesAnoSelecionado by remember { mutableStateOf(SimpleDateFormat("MM/yyyy", Locale("pt", "BR")).format(calendar.time)) }

    var expandidoCategoria by remember { mutableStateOf(false) }
    var mostrarDialogCalendario by remember { mutableStateOf(false) }
    var salvando by remember { mutableStateOf(false) }

    var processandoIA by remember { mutableStateOf(false) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }

    // =========================================================================
    // === Variáveis de estado do Cartão ===
    // =========================================================================
    var cartoesList by remember { mutableStateOf<List<Cartao>>(emptyList()) }
    var cartoesNumerosMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var cartaoSelecionadoId by remember { mutableStateOf("Saldo Conta") }
    var cartaoSelecionadoNome by remember { mutableStateOf("Saldo Conta / Pix") }
    var expandidoCartao by remember { mutableStateOf(false) }
    // =========================================================================

    val colorAccent = LocalAccentColor.current
    val colorBg = if (isDark) Color(0xFF121212) else Color(0xFFF8F9FA)
    val colorSurface = if (isDark) Color(0xFF1E1E1E) else Color.White
    val colorTextPrimary = if (isDark) Color(0xFFF9FAFB) else Color(0xFF1E1E1E)
    val colorTextSecondary = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val colorDivider = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB)

    // =========================================================================
    // === INJETADO: Controle de Categorias Customizadas ===
    // =========================================================================
    val categoriasPadrao = listOf("Moradia", "Alimentação", "Transporte", "Saúde", "Educação", "Lazer", "Empréstimo", "Cartão de Crédito", "Outros")
    var categoriasCustomList by remember { mutableStateOf<List<String>>(emptyList()) }

    var projetosList by remember { mutableStateOf<List<Projeto>>(emptyList()) }
    var projetoSelecionadoId by remember { mutableStateOf<String?>(null) }
    var projetoSelecionadoNome by remember { mutableStateOf("Nenhum (Gasto Normal)") }
    var expandidoProjeto by remember { mutableStateOf(false) }

    // =========================================================================
    // === Buscar cartões, projetos e CATEGORIAS no Firebase em tempo real ===
    // =========================================================================
    DisposableEffect(workspaceUid) {
        val ouvintes = mutableListOf<ListenerRegistration>()
        if (usuario != null && workspaceUid.isNotEmpty()) {
            // Busca Cartões
            ouvintes += Firebase.firestore.collection("usuarios").document(workspaceUid).collection("cartoes")
                .addSnapshotListener { snap, _ ->
                    if (snap != null) {
                        snap.documents.forEach { limparNumeroCartao(it) }
                        val numMap = mutableMapOf<String, String>()
                        cartoesList = snap.documents.mapNotNull { d ->
                            try {
                                numMap[d.id] = d.getString("numero") ?: ""
                                Cartao(d.id, d.getString("nome") ?: "", d.getString("bandeira") ?: "Mastercard", d.getDouble("limite") ?: 0.0, d.getDouble("faturaAtual") ?: 0.0)
                            } catch (e: Exception) { null }
                        }
                        cartoesNumerosMap = numMap
                    }
                }

            // Busca Projetos/Viagens
            ouvintes += Firebase.firestore.collection("usuarios").document(workspaceUid).collection("projetos")
                .addSnapshotListener { snap, _ ->
                    if (snap != null) {
                        projetosList = snap.documents.mapNotNull { d ->
                            try { Projeto(id = d.id, nome = d.getString("nome") ?: "", orcamento = d.getDouble("orcamento") ?: 0.0) } catch (e: Exception) { null }
                        }
                    }
                }

            // Busca Categorias Customizadas
            ouvintes += Firebase.firestore.collection("usuarios").document(workspaceUid).collection("categorias_custom")
                .addSnapshotListener { snap, _ ->
                    if (snap != null) {
                        categoriasCustomList = snap.documents.mapNotNull { d ->
                            try { d.getString("nome") } catch (e: Exception) { null }
                        }
                        atualizarIconesCustom(snap)
                    }
                }
        }
        // Desliga os ouvintes ao sair da tela ou trocar a chave (ex.: mês), senão eles se acumulam
        onDispose { ouvintes.forEach { it.remove() } }
    }
    // =========================================================================

    // LISTA FINAL DE CATEGORIAS (Junta o padrão com as que o usuário criou)
    val categoriasFinais = (categoriasPadrao + categoriasCustomList).distinct()

    // LAUNCHER DA CÂMERA INTEGRADO COM ML KIT OCR + GROQ OPENAI GPT-OSS-20B
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { sucesso ->
        if (sucesso && imageUri != null) {
            coroutineScope.launch {
                processandoIA = true
                try {
                    val inputStream = context.contentResolver.openInputStream(imageUri!!)
                    val bitmap = BitmapFactory.decodeStream(inputStream)

                    if (bitmap != null) {
                        // 1. OCR LOCAL COM ML KIT (Extrai todo o texto da foto no aparelho)
                        val inputImage = InputImage.fromBitmap(bitmap, 0)
                        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                        recognizer.process(inputImage)
                            .addOnSuccessListener { visionText ->
                                val textoExtraido = visionText.text

                                if (textoExtraido.isNotBlank()) {
                                    coroutineScope.launch {
                                        // 2. ENVIA O TEXTO PARA O MODELO openai/gpt-oss-20b DA GROQ
                                        var erroDetalhado: String? = null

                                        val promptSystem = """
                                            Analise o seguinte texto extraído de uma Nota Fiscal, Cupom ou Boleto.
                                            Retorne APENAS um JSON puro, sem markdown, exatamente neste formato:
                                            {"l":"NOME_LOCAL","v":0.00,"c":"CATEGORIA","d":0,"m":0,"a":0,"manutencao":true/false}
                                            
                                            REGRAS DE OURO:
                                            - 'l' (Local/Empresa): Encontre o NOME EXATO da empresa ou loja. Se for um BOLETO, procure por "Beneficiário", "Cedente" ou "Recebedor". NUNCA retorne nomes genéricos como "Boleto", "Nota Fiscal" ou "Mercado".
                                            - EXCEÇÃO DE CONSUMO: Contas de Luz/Energia, Água, Gás, Internet e Celular DEVEM ter 'c' como "Moradia" e "manutencao": false.
                                            - Use "manutencao": true e 'c' como "Manutenção" EXCLUSIVAMENTE para veículos ou equipamentos físicos (oficina, mecânica, peças, óleo, pneus).
                                            - 'd', 'm', 'a' (dia, mês, ano) DEVEM ser inteiros. Priorize a data de VENCIMENTO. Se não tiver, use a de EMISSÃO. Se não achar, retorne 0.
                                            - 'c' (Categoria) deve ser estritamente: Moradia, Alimentação, Transporte, Saúde, Educação, Lazer, Manutenção ou Outros.
                                            
                                            TEXTO EXTRAÍDO DA IMAGEM:
                                            $textoExtraido
                                        """.trimIndent()

                                        // Chama a IA pela Cloud Function (a chave da Groq não fica mais no app)
                                        val jsonResponse = try { chamarIA("ocr", null, promptSystem) } catch (e: FalhaIA) { erroDetalhado = e.message; "" }

                                        // 3. ATUALIZA A TELA COM OS DADOS PROCESSADOS PELO OPENAI/GPT-OSS-20B
                                        if (jsonResponse.isNotBlank()) {
                                            val jsonMatch = Regex("\\{.*\\}", RegexOption.DOT_MATCHES_ALL).find(jsonResponse)?.value

                                            if (jsonMatch != null) {
                                                try {
                                                    val jsonObject = JSONObject(jsonMatch)
                                                    descricao = jsonObject.optString("l", "Nota Fiscal")
                                                    valor = jsonObject.optDouble("v", 0.0).paraCampo()

                                                    val diaIA = jsonObject.optInt("d", 0)
                                                    val mesIA = jsonObject.optInt("m", 0)
                                                    val anoIA = jsonObject.optInt("a", 0)

                                                    if (diaIA in 1..31) diaVencimento = diaIA.toString()
                                                    if (mesIA in 1..12 && anoIA >= 2020) mesAnoSelecionado = "%02d/%d".format(mesIA, anoIA)

                                                    val catSugerida = jsonObject.optString("c", "Outros")
                                                    if (catSugerida in categoriasFinais) categoriaDespesa = catSugerida

                                                    val isManutencao = jsonObject.optBoolean("manutencao", false)
                                                    if (isManutencao) {
                                                        observacao = "🚨 GASTO DE MANUTENÇÃO IDENTIFICADO PELA IA. Vincular ao TCO?"
                                                        categoriaDespesa = "Manutenção"
                                                        Toast.makeText(context, "IA detectou gasto de manutenção!", Toast.LENGTH_LONG).show()
                                                    }

                                                    Toast.makeText(context, "Dados extraidos com sucesso!", Toast.LENGTH_SHORT).show()
                                                } catch (e: Exception) {
                                                    Log.e("FluxAi_Data", "Erro ao processar JSON: ${e.message}")
                                                    Toast.makeText(context, "Erro na formatação dos dados.", Toast.LENGTH_LONG).show()
                                                }
                                            } else {
                                                Toast.makeText(context, "IA não conseguiu gerar dados válidos.", Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            val mensagemErro = erroDetalhado ?: "Falha na comunicação com o servidor Groq."
                                            Toast.makeText(context, mensagemErro, Toast.LENGTH_LONG).show()
                                        }
                                        processandoIA = false
                                    }
                                } else {
                                    Toast.makeText(context, "Nenhum texto identificado na foto.", Toast.LENGTH_LONG).show()
                                    processandoIA = false
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("FluxAi_OCR", "Erro ao processar imagem localmente: ${e.message}")
                                Toast.makeText(context, "Erro no reconhecedor de texto local.", Toast.LENGTH_SHORT).show()
                                processandoIA = false
                            }
                    } else {
                        processandoIA = false
                    }
                } catch (e: Exception) {
                    Log.e("FluxAi_Câmera", "Erro geral: ${e.message}")
                    Toast.makeText(context, "Erro no processamento da imagem.", Toast.LENGTH_SHORT).show()
                    processandoIA = false
                }
            }
        }
    }

    val permissaoCameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
        if (concedida) {
            try {
                val file = File(context.cacheDir, "temp_nota.jpg")
                if (!file.exists()) file.createNewFile()
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                imageUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "Erro FileProvider: Verifique file_paths.xml", Toast.LENGTH_LONG).show()
                Log.e("FluxAi_Provider", "Erro: ${e.message}")
            }
        } else {
            Toast.makeText(context, "A câmera é necessária para ler a nota fiscal.", Toast.LENGTH_LONG).show()
        }
    }

    // Grava o lançamento (usada pelo botão fixo do rodapé)
    fun salvarLancamento() {
        val vFinal = valor.paraValor() ?: 0.0
        val dFinal = diaVencimento.toIntOrNull() ?: 0
        val numParcelas = if (isParcelado) qtdParcelas.toIntOrNull() ?: 0 else 1
        val erro = when {
            usuario == null -> "Sessão expirada. Entre novamente."
            descricao.isBlank() -> "Informe a descrição."
            vFinal <= 0.0 -> "Informe um valor válido."
            dFinal !in 1..31 -> "O dia de vencimento vai de 1 a 31."
            numParcelas !in 1..72 -> "Parcelas: de 1 a 72."
            else -> null
        }
        if (erro == null) {
            salvando = true

            val partesData = mesAnoSelecionado.split("/")
            var mesAtualLoop = partesData.getOrNull(0)?.toIntOrNull() ?: 1
            var anoAtualLoop = partesData.getOrNull(1)?.toIntOrNull() ?: 2026

            val dbRef = Firebase.firestore.collection("usuarios").document(workspaceUid).collection("despesas")
            // Tudo num lote só: ou grava todas as parcelas, ou nenhuma
            val lote = Firebase.firestore.batch()

            for (i in 1..numParcelas) {
                val baseDesc = if (cartaoSelecionadoId != "Saldo Conta") "[Cartão] $descricao" else descricao
                val descricaoFinal = if (isParcelado) "$baseDesc ($i/$numParcelas)" else baseDesc
                val mesAnoFormatado = String.format(Locale("pt", "BR"), "%02d/%04d", mesAtualLoop, anoAtualLoop)

                val despesaMap = hashMapOf(
                    "descricao" to descricaoFinal,
                    "valor" to vFinal,
                    "diaVencimento" to dFinal,
                    "tipo" to tipoDespesa,
                    "categoria" to categoriaDespesa,
                    "status" to "A pagar",
                    "mesAno" to mesAnoFormatado,
                    "observacao" to observacao,
                    "frequencia" to frequenciaDespesa,
                    "cartaoId" to if (cartaoSelecionadoId == "Saldo Conta") null else cartaoSelecionadoId,
                    "projetoId" to projetoSelecionadoId
                )

                lote.set(dbRef.document(), despesaMap)

                mesAtualLoop++
                if (mesAtualLoop > 12) {
                    mesAtualLoop = 1
                    anoAtualLoop++
                }
            }

            if (cartaoSelecionadoId != "Saldo Conta" && cartoesList.any { it.id == cartaoSelecionadoId }) {
                // Incremento no servidor: dois lançamentos ao mesmo tempo não se sobrescrevem
                lote.update(
                    Firebase.firestore.collection("usuarios").document(workspaceUid).collection("cartoes").document(cartaoSelecionadoId),
                    "faturaAtual", com.google.firebase.firestore.FieldValue.increment(vFinal * numParcelas)
                )
            }

            // Com o cache offline do Firestore o lote fica salvo no aparelho e sobe quando houver internet
            lote.commit().addOnFailureListener { e ->
                Toast.makeText(context, "Falha ao salvar: ${e.message}", Toast.LENGTH_LONG).show()
            }
            salvando = false
            Toast.makeText(context, if (numParcelas > 1) "$numParcelas parcelas registradas!" else "Despesa registrada!", Toast.LENGTH_SHORT).show()
            onAbrirDashboard()

        } else { Toast.makeText(context, erro, Toast.LENGTH_SHORT).show() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MenuLateral(
                drawerState = drawerState,
                coroutineScope = coroutineScope,
                rotaAtual = "home",
                onAbrirDashboard = onAbrirDashboard,
                onAbrirLancamento = { },
                onAbrirAnalytics = onAbrirAnalytics,
                onAbrirSettings = onAbrirSettings,
                onAbrirSobre = onAbrirSobre,
                onLogout = onLogout,
                onAbrirManutencao = onAbrirManutencao,
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
                    title = { Text("Novo Lançamento", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colorTextPrimary) },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, null, tint = colorTextPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colorBg)
                )
            },
            bottomBar = {
                Surface(color = colorBg) {
                    Button(
                        onClick = { salvarLancamento() },
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp).height(54.dp),
                        shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = colorAccent), enabled = !salvando
                    ) {
                        if (salvando) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        else {
                            val n = if (isParcelado) qtdParcelas.toIntOrNull() ?: 0 else 1
                            Text(if (n > 1) "Registrar $n parcelas" else "Registrar lançamento", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            },
            containerColor = colorBg
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val coresCampo = OutlinedTextFieldDefaults.colors(focusedBorderColor = colorAccent, unfocusedBorderColor = colorDivider, focusedTextColor = colorTextPrimary, unfocusedTextColor = colorTextPrimary)
                val abrirCamera: () -> Unit = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        try {
                            val file = File.createTempFile("nota_fluxai_", ".jpg", context.cacheDir)
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            imageUri = uri
                            cameraLauncher.launch(uri)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Não foi possível abrir a câmera.", Toast.LENGTH_LONG).show()
                            Log.e("FluxAi_Provider", "Erro: ${e.message}")
                        }
                    } else permissaoCameraLauncher.launch(Manifest.permission.CAMERA)
                }

                // ===== VALOR EM DESTAQUE =====
                Surface(shape = RoundedCornerShape(24.dp), color = colorSurface, border = BorderStroke(1.dp, colorDivider), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (isParcelado) "Valor de cada parcela" else "Quanto foi?", fontSize = 13.sp, color = colorTextSecondary)
                        TextField(
                            value = valor,
                            onValueChange = { novo ->
                                // Padrão brasileiro: ponto digitado vira vírgula decimal, e só uma vírgula é aceita
                                val txt = novo.replace('.', ',').filter { it.isDigit() || it == ',' }
                                if (txt.count { it == ',' } <= 1) valor = txt
                            },
                            placeholder = { Text("0,00", fontSize = 36.sp, fontWeight = FontWeight.Black, color = colorTextSecondary.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                            prefix = { Text("R$ ", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colorTextSecondary) },
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Black, color = colorTextPrimary, textAlign = TextAlign.Center),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = colorAccent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (processandoIA) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(CircleShape), color = colorAccent)
                            Text("Lendo a nota com IA...", fontSize = 12.sp, color = colorAccent)
                        } else {
                            AssistChip(
                                onClick = abrirCamera,
                                label = { Text("Ler nota ou boleto com a câmera") },
                                leadingIcon = { Icon(Icons.Default.DocumentScanner, null, Modifier.size(18.dp), tint = colorAccent) },
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, colorAccent.copy(alpha = 0.4f)),
                                colors = AssistChipDefaults.assistChipColors(labelColor = colorAccent)
                            )
                        }
                    }
                }

                // ===== DETALHES =====
                TituloSecaoLancamento("Detalhes", colorTextSecondary)
                OutlinedTextField(
                    value = descricao, onValueChange = { descricao = it }, label = { Text("Descrição", color = colorTextSecondary) },
                    leadingIcon = { Icon(Icons.Default.Description, null, tint = colorTextSecondary) }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), singleLine = true, shape = FormatoCampo, colors = coresCampo
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = diaVencimento, onValueChange = { if (it.length <= 2) diaVencimento = it.filter { c -> c.isDigit() } },
                        label = { Text("Vence dia", color = colorTextSecondary, maxLines = 1) },
                        leadingIcon = { Icon(Icons.Default.Event, null, tint = colorTextSecondary) },
                        trailingIcon = {
                            TextButton(onClick = { diaVencimento = Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toString() }) { Text("Hoje", color = colorAccent, fontSize = 12.sp) }
                        },
                        modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, shape = FormatoCampo, colors = coresCampo
                    )
                    Box(Modifier.weight(0.8f)) {
                        OutlinedTextField(
                            value = mesAnoSelecionado, onValueChange = { }, readOnly = true, label = { Text("Mês", color = colorTextSecondary, maxLines = 1) },
                            leadingIcon = { Icon(Icons.Default.CalendarMonth, null, tint = colorAccent) },
                            modifier = Modifier.fillMaxWidth(), shape = FormatoCampo, colors = coresCampo
                        )
                        Box(modifier = Modifier.matchParentSize().clickable { mostrarDialogCalendario = true })
                    }
                }

                // ===== CATEGORIA (grade de ícones) =====
                TituloSecaoLancamento("Categoria", colorTextSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categoriasFinais.forEach { cat ->
                        val sel = categoriaDespesa == cat
                        FilterChip(
                            selected = sel, onClick = { categoriaDespesa = cat },
                            label = { Text(cat) },
                            leadingIcon = { Icon(iconeCategoria(cat), null, Modifier.size(18.dp), tint = if (sel) Color.White else corCategoria(cat)) },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = corCategoria(cat), selectedLabelColor = Color.White, labelColor = colorTextPrimary),
                            border = FilterChipDefaults.filterChipBorder(enabled = true, selected = sel, borderColor = colorDivider)
                        )
                    }
                }

                // ===== PAGAMENTO =====
                TituloSecaoLancamento("Pagamento", colorTextSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val opcoes = listOf(Triple("Saldo Conta", "Conta / Pix", Icons.Default.AccountBalance)) +
                        cartoesList.map { c ->
                            val final4 = (cartoesNumerosMap[c.id] ?: "").filter { it.isDigit() }.takeLast(4)
                            Triple(c.id, if (final4.isNotEmpty()) "${c.nome} •$final4" else c.nome, Icons.Default.CreditCard)
                        }
                    opcoes.forEach { (id, rotulo, icone) ->
                        val sel = cartaoSelecionadoId == id
                        FilterChip(
                            selected = sel, onClick = { cartaoSelecionadoId = id; cartaoSelecionadoNome = rotulo },
                            label = { Text(rotulo) },
                            leadingIcon = { Icon(icone, null, Modifier.size(18.dp)) },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colorAccent.copy(alpha = 0.14f), selectedLabelColor = colorAccent, selectedLeadingIconColor = colorAccent, labelColor = colorTextPrimary, iconColor = colorTextSecondary),
                            border = FilterChipDefaults.filterChipBorder(enabled = true, selected = sel, borderColor = colorDivider, selectedBorderColor = colorAccent)
                        )
                    }
                }

                // Parcelamento
                Surface(shape = RoundedCornerShape(16.dp), color = colorSurface, border = BorderStroke(1.dp, colorDivider), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarViewMonth, null, tint = colorAccent)
                            Spacer(Modifier.width(12.dp))
                            Text("Compra parcelada", fontWeight = FontWeight.Medium, color = colorTextPrimary, modifier = Modifier.weight(1f))
                            Switch(checked = isParcelado, onCheckedChange = { isParcelado = it; if (it && qtdParcelas.isBlank()) qtdParcelas = "2" }, colors = SwitchDefaults.colors(checkedTrackColor = colorAccent))
                        }
                        if (isParcelado) {
                            val n = qtdParcelas.toIntOrNull() ?: 0
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                                FilledTonalIconButton(onClick = { qtdParcelas = (n - 1).coerceAtLeast(2).toString() }) { Icon(Icons.Default.Remove, "Menos parcelas") }
                                Text("${n}x", fontSize = 22.sp, fontWeight = FontWeight.Black, color = colorTextPrimary, textAlign = TextAlign.Center, modifier = Modifier.width(64.dp))
                                FilledTonalIconButton(onClick = { qtdParcelas = (n + 1).coerceAtMost(72).toString() }) { Icon(Icons.Default.Add, "Mais parcelas") }
                                Spacer(Modifier.width(12.dp))
                                val v = valor.paraValor() ?: 0.0
                                if (v > 0 && n > 0) {
                                    val fmt = java.text.NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
                                    Column {
                                        Text("${n}x de ${fmt.format(v)}", fontSize = 13.sp, color = colorTextSecondary)
                                        Text("Total ${fmt.format(v * n)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colorTextPrimary)
                                    }
                                }
                            }
                        }
                    }
                }

                // ===== CLASSIFICAÇÃO =====
                TituloSecaoLancamento("Classificação", colorTextSecondary)
                SeletorSegmentadoLancamento(listOf("Fixa", "Variável"), tipoDespesa, colorAccent) { tipoDespesa = it }
                SeletorSegmentadoLancamento(listOf("Mensal", "Quinzenal"), frequenciaDespesa, colorAccent) { frequenciaDespesa = it }

                if (projetosList.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = projetoSelecionadoNome, onValueChange = { }, readOnly = true, label = { Text("Projeto ou viagem", color = colorTextSecondary) },
                            modifier = Modifier.fillMaxWidth(), shape = FormatoCampo, colors = coresCampo,
                            leadingIcon = { Icon(Icons.Default.Work, null, tint = Color(0xFF03A9F4)) },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, tint = colorTextSecondary) }
                        )
                        Box(modifier = Modifier.matchParentSize().clickable { expandidoProjeto = !expandidoProjeto })
                        DropdownMenu(expanded = expandidoProjeto, onDismissRequest = { expandidoProjeto = false }, modifier = Modifier.fillMaxWidth(0.8f).background(colorSurface)) {
                            DropdownMenuItem(text = { Text("Nenhum (gasto normal)", color = colorTextPrimary) }, onClick = { projetoSelecionadoId = null; projetoSelecionadoNome = "Nenhum (Gasto Normal)"; expandidoProjeto = false })
                            projetosList.forEach { p ->
                                DropdownMenuItem(text = { Text(p.nome, color = colorTextPrimary) }, onClick = { projetoSelecionadoId = p.id; projetoSelecionadoNome = p.nome; expandidoProjeto = false })
                            }
                        }
                    }
                }

                // Observação recolhida até ser pedida
                var mostrarObs by remember { mutableStateOf(observacao.isNotBlank()) }
                if (mostrarObs) {
                    OutlinedTextField(
                        value = observacao, onValueChange = { observacao = it }, label = { Text("Observação", color = colorTextSecondary) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp), shape = FormatoCampo, colors = coresCampo
                    )
                } else {
                    TextButton(onClick = { mostrarObs = true }) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp), tint = colorAccent); Spacer(Modifier.width(4.dp)); Text("Adicionar observação", color = colorAccent)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (mostrarDialogCalendario) {
        var anoTemp by remember { mutableIntStateOf(calendar.get(Calendar.YEAR)) }
        val mesesAbrev = listOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")

        AlertDialog(
            onDismissRequest = { mostrarDialogCalendario = false }, containerColor = colorSurface, titleContentColor = colorTextPrimary,
            title = { Text("Mês de Referência", fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { anoTemp-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = colorTextPrimary) }
                        Text(anoTemp.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colorAccent)
                        IconButton(onClick = { anoTemp++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colorTextPrimary) }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    val rows = mesesAbrev.chunked(4)
                    Column {
                        rows.forEachIndexed { rowIndex, rowMonths ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                rowMonths.forEachIndexed { colIndex, mesStr ->
                                    val mesIndex = rowIndex * 4 + colIndex
                                    val isSelecionado = calendar.get(Calendar.MONTH) == mesIndex && calendar.get(Calendar.YEAR) == anoTemp
                                    TextButton(onClick = {
                                        calendar.set(Calendar.YEAR, anoTemp); calendar.set(Calendar.MONTH, mesIndex)
                                        mesAnoSelecionado = SimpleDateFormat("MM/yyyy", Locale("pt", "BR")).format(calendar.time)
                                        mostrarDialogCalendario = false
                                    }) { Text(mesStr, color = if (isSelecionado) colorAccent else colorTextSecondary, fontWeight = if (isSelecionado) FontWeight.Bold else FontWeight.Normal) }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { mostrarDialogCalendario = false }) { Text("Cancelar", color = colorTextSecondary) } }
        )
    }
}

@Composable
private fun TituloSecaoLancamento(titulo: String, cor: Color) {
    Text(titulo.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cor, letterSpacing = 1.sp, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SeletorSegmentadoLancamento(opcoes: List<String>, selecionada: String, cor: Color, onSelecionar: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        opcoes.forEachIndexed { i, opcao ->
            SegmentedButton(
                selected = selecionada == opcao, onClick = { onSelecionar(opcao) },
                shape = SegmentedButtonDefaults.itemShape(i, opcoes.size),
                colors = SegmentedButtonDefaults.colors(activeContainerColor = cor.copy(alpha = 0.14f), activeContentColor = cor)
            ) { Text(opcao) }
        }
    }
}
