package fluxai.app

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar

// =========================================================================
// APRESENTAÇÃO (primeira vez na conta)
// Renda, primeira conta/cartão e automações. Tudo opcional: dá para pular qualquer passo.
// Quem já tem dados (conta antiga ou conta conjunta) passa direto.
// =========================================================================
fun onboardingConcluido(context: Context, uid: String) =
    context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).getBoolean("onboarding_ok_$uid", false)

fun marcarOnboardingConcluido(context: Context, uid: String) =
    context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).edit { putBoolean("onboarding_ok_$uid", true) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(onConcluir: () -> Unit) {
    val context = LocalContext.current
    val usuario = Firebase.auth.currentUser ?: run { LaunchedEffect(Unit) { onConcluir() }; return }
    val workspaceUid = remember { workspaceAtual(context, usuario.uid) }
    val c = coresTela()
    val escopo = rememberCoroutineScope()
    val usuarioDoc = remember { Firebase.firestore.collection("usuarios").document(workspaceUid) }

    var verificando by remember { mutableStateOf(true) }
    var passo by remember { mutableIntStateOf(0) }
    var trabalhando by remember { mutableStateOf(false) }

    fun concluir() {
        marcarOnboardingConcluido(context, usuario.uid)
        onConcluir()
    }

    // Conta que já tem lançamentos não precisa da apresentação
    LaunchedEffect(Unit) {
        val jaUsa = runCatching { !usuarioDoc.collection("despesas").limit(1).get().await().isEmpty }.getOrDefault(false)
        if (jaUsa) concluir() else verificando = false
    }
    BackHandler(enabled = passo > 0) { passo-- }

    if (verificando) {
        Box(Modifier.fillMaxSize().background(c.fundo), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.destaque) }
        return
    }

    var adiantamento by remember { mutableStateOf("") }
    var salario by remember { mutableStateOf("") }
    var nomeConta by remember { mutableStateOf("") }
    var saldoConta by remember { mutableStateOf("") }
    var nomeCartao by remember { mutableStateOf("") }
    var limiteCartao by remember { mutableStateOf("") }
    var vencimentoCartao by remember { mutableStateOf("") }
    var lembrete by remember { mutableStateOf(true) }
    val totalPassos = 4

    Scaffold(
        containerColor = c.fundo,
        bottomBar = {
            Surface(color = c.fundo) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (passo in 1 until totalPassos) TextButton(onClick = { passo++ }) { Text("Pular", color = c.textoFraco) }
                    Spacer(Modifier.weight(1f))
                    // Indicador de progresso em pontos
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.semantics(mergeDescendants = true) {}) {
                        repeat(totalPassos + 1) { i -> Box(Modifier.size(if (i == passo) 10.dp else 7.dp).background(if (i == passo) c.destaque else c.divisor, CircleShape)) }
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        enabled = !trabalhando,
                        onClick = {
                            trabalhando = true
                            escopo.launch {
                                runCatching {
                                    when (passo) {
                                        1 -> {
                                            val ad = adiantamento.paraValor() ?: 0.0
                                            val pg = salario.paraValor() ?: 0.0
                                            if (ad + pg > 0) {
                                                val hoje = Calendar.getInstance()
                                                val doc = "%02d-%04d".format(hoje.get(Calendar.MONTH) + 1, hoje.get(Calendar.YEAR))
                                                usuarioDoc.set(mapOf("rendaPadrao" to mapOf("adiantamento" to ad, "pagamento" to pg)), SetOptions.merge())
                                                usuarioDoc.collection("saldos").document(doc).set(mapOf("adiantamento" to ad, "pagamento" to pg, "extra" to 0.0, "valor" to ad + pg), SetOptions.merge())
                                            }
                                        }
                                        2 -> {
                                            if (nomeConta.isNotBlank()) usuarioDoc.collection("contas").add(mapOf("nome" to nomeConta.trim(), "tipo" to "Corrente", "saldo" to (saldoConta.paraValor() ?: 0.0)))
                                            if (nomeCartao.isNotBlank()) {
                                                val dia = (vencimentoCartao.toIntOrNull() ?: 10).coerceIn(1, 31)
                                                usuarioDoc.collection("cartoes").add(mapOf(
                                                    "nome" to nomeCartao.trim(), "bandeira" to "Mastercard", "numero" to "", "validade" to "",
                                                    "limite" to (limiteCartao.paraValor() ?: 0.0), "faturaAtual" to 0.0,
                                                    "diaFechamento" to ((dia - 7 + 30) % 30).coerceAtLeast(1), "diaVencimento" to dia
                                                ))
                                            }
                                        }
                                        3 -> configurarLembreteDiario(context, lembrete, horaLembreteDiario(context))
                                    }
                                }
                                trabalhando = false
                                if (passo == totalPassos) concluir() else passo++
                            }
                        },
                        shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = c.destaque)
                    ) { Text(when (passo) { 0 -> "Começar"; totalPassos -> "Ir para o app"; else -> "Continuar" }, fontWeight = FontWeight.Bold) }
                }
            }
        }
    ) { padding ->
        AnimatedContent(targetState = passo, label = "passo") { atual ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val campos = coresCampoTela(c)
                when (atual) {
                    0 -> {
                        CabecalhoPasso(Icons.Default.WavingHand, "Bem-vindo ao FluxAí", "Em 1 minuto deixamos o app pronto para mostrar como seu mês vai terminar.", c)
                        ItemVantagem(Icons.Default.QueryStats, "Previsão do mês", "Quanto sobra no fim do mês e quanto dá para gastar por dia.", c)
                        ItemVantagem(Icons.Default.NotificationsActive, "Avisos na hora certa", "Contas vencendo, limite de categoria chegando e lembrete para registrar gastos.", c)
                        ItemVantagem(Icons.Default.AutoAwesome, "Consultor com IA", "Diagnóstico do mês com ações concretas em reais.", c)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                trabalhando = true
                                escopo.launch {
                                    runCatching { carregarDadosExemplo(workspaceUid) }
                                        .onSuccess { Toast.makeText(context, "Dados de exemplo carregados. Remova quando quiser em Configurações.", Toast.LENGTH_LONG).show(); concluir() }
                                        .onFailure { Toast.makeText(context, "Não foi possível carregar o exemplo.", Toast.LENGTH_SHORT).show() }
                                    trabalhando = false
                                }
                            },
                            enabled = !trabalhando, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                        ) { Icon(Icons.Default.Science, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Explorar com dados de exemplo") }
                    }
                    1 -> {
                        CabecalhoPasso(Icons.Default.Payments, "Sua renda", "O app separa o mês em duas quinzenas: o vale ou adiantamento paga as contas da 1ª, o salário as da 2ª. Esses valores viram o padrão dos próximos meses.", c)
                        OutlinedTextField(adiantamento, { adiantamento = it.replace('.', ',') }, label = { Text("Vale / adiantamento (opcional)") }, prefix = { Text("R$ ") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(), shape = FormatoCampo, colors = campos)
                        OutlinedTextField(salario, { salario = it.replace('.', ',') }, label = { Text("Salário") }, prefix = { Text("R$ ") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(), shape = FormatoCampo, colors = campos)
                    }
                    2 -> {
                        CabecalhoPasso(Icons.Default.AccountBalance, "Conta e cartão", "Cadastre sua conta principal e seu cartão de crédito. Dá para adicionar outros depois.", c)
                        OutlinedTextField(nomeConta, { nomeConta = it }, label = { Text("Conta (ex.: Nubank)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), modifier = Modifier.fillMaxWidth(), shape = FormatoCampo, colors = campos)
                        OutlinedTextField(saldoConta, { saldoConta = it.replace('.', ',') }, label = { Text("Saldo atual da conta") }, prefix = { Text("R$ ") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(), shape = FormatoCampo, colors = campos)
                        HorizontalDivider(color = c.divisor, modifier = Modifier.padding(vertical = 4.dp))
                        OutlinedTextField(nomeCartao, { nomeCartao = it }, label = { Text("Cartão de crédito (opcional)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), modifier = Modifier.fillMaxWidth(), shape = FormatoCampo, colors = campos)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(limiteCartao, { limiteCartao = it.replace('.', ',') }, label = { Text("Limite") }, prefix = { Text("R$ ") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f), shape = FormatoCampo, colors = campos)
                            OutlinedTextField(vencimentoCartao, { if (it.length <= 2) vencimentoCartao = it.filter(Char::isDigit) }, label = { Text("Vence dia") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(0.7f), shape = FormatoCampo, colors = campos)
                        }
                    }
                    3 -> {
                        CabecalhoPasso(Icons.Default.NotificationsActive, "Automação", "Deixe o app trabalhar por você. Você pode mudar isso em Configurações.", c)
                        Surface(shape = RoundedCornerShape(16.dp), color = c.superficie, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Lembrete diário", fontWeight = FontWeight.SemiBold, color = c.texto)
                                    Text("Às ${horaLembreteDiario(context)}h, só se você não registrou nada no dia.", fontSize = 12.sp, color = c.textoFraco)
                                }
                                Switch(checked = lembrete, onCheckedChange = { lembrete = it }, colors = SwitchDefaults.colors(checkedTrackColor = c.destaque))
                            }
                        }
                        Surface(shape = RoundedCornerShape(16.dp), color = c.superficie, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Compras pelas notificações do banco", fontWeight = FontWeight.SemiBold, color = c.texto)
                                Text(
                                    "O app lê só os avisos de apps de banco (Nubank, Inter, Itaú...), sugere o lançamento e você confirma. O texto das notificações não sai do aparelho.",
                                    fontSize = 12.sp, color = c.textoFraco, lineHeight = 16.sp
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(onClick = { abrirPermissaoNotificacoes(context) }, shape = RoundedCornerShape(12.dp)) {
                                    Text(if (leituraNotificacoesAtiva(context)) "Ativado" else "Ativar leitura")
                                }
                            }
                        }
                    }
                    else -> {
                        CabecalhoPasso(Icons.Default.CheckCircle, "Tudo pronto!", "Registre seu primeiro gasto pelo botão Novo Lançamento, pela câmera (nota ou boleto) ou pelo atalho na tela inicial.", c)
                        ItemVantagem(Icons.Default.UploadFile, "Tem um extrato?", "Importe o OFX ou CSV do banco pelo menu para começar com o mês completo.", c)
                    }
                }
            }
        }
    }
}

@Composable
private fun CabecalhoPasso(icone: ImageVector, titulo: String, texto: String, c: CoresTela) {
    Box(Modifier.size(72.dp).background(c.destaque.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(icone, null, tint = c.destaque, modifier = Modifier.size(36.dp))
    }
    Text(titulo, fontSize = 24.sp, fontWeight = FontWeight.Black, color = c.texto, modifier = Modifier.semantics { heading() })
    Text(texto, fontSize = 14.sp, color = c.textoFraco, lineHeight = 20.sp)
}

@Composable
private fun ItemVantagem(icone: ImageVector, titulo: String, texto: String, c: CoresTela) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Icon(icone, null, tint = c.destaque, modifier = Modifier.padding(top = 2.dp).size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(titulo, fontWeight = FontWeight.SemiBold, color = c.texto)
            Text(texto, fontSize = 13.sp, color = c.textoFraco, lineHeight = 18.sp, textAlign = TextAlign.Start)
        }
    }
}
