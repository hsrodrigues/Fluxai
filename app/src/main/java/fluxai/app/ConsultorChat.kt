package fluxai.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// =========================================================================
// CONSULTOR IA: análise do mês + conversa de acompanhamento
// A análise inicial vira a primeira mensagem; as perguntas seguintes usam os mesmos dados do mês.
// Só as últimas mensagens vão para a IA, para economizar a cota gratuita.
// =========================================================================
private const val SISTEMA_CHAT = """Você é o Consultor FluxAí, consultor financeiro pessoal num app brasileiro de controle de gastos.
Responda em português do Brasil, em até 90 palavras, tom direto e acolhedor, falando com o usuário por "você".
Use apenas os dados do mês informados abaixo; se faltar informação, diga o que falta em vez de inventar.
Cite valores em R$ quando ajudar. Texto puro, sem markdown (listas com "• ").
Se a pergunta não for sobre as finanças pessoais do usuário, diga gentilmente que só ajuda com isso."""

private const val HISTORICO_MAXIMO = 6

private fun limparMarkdown(texto: String) =
    texto.replace("**", "").replace(Regex("(?m)^#+\\s*"), "").replace(Regex("(?m)^[-*]\\s+"), "• ").trim()

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PainelConsultorIA(
    analise: String,
    carregandoAnalise: Boolean,
    contextoDoMes: String,
    colorAccent: Color,
    colorSurface: Color,
    colorTextPrimary: Color,
    colorTextSecondary: Color,
    onFechar: () -> Unit
) {
    val escopo = rememberCoroutineScope()
    // Mensagens da conversa: (papel, texto). Recomeça a cada nova análise.
    val conversa = remember(analise) { mutableStateListOf<Pair<String, String>>().apply { if (analise.isNotBlank()) add("assistant" to analise) } }
    var pergunta by remember { mutableStateOf("") }
    var respondendo by remember { mutableStateOf(false) }
    val lista = rememberLazyListState()

    fun perguntar(texto: String) {
        val q = texto.trim()
        if (q.isEmpty() || respondendo) return
        conversa.add("user" to q)
        pergunta = ""
        respondendo = true
        escopo.launch {
            val historico = conversa.takeLast(HISTORICO_MAXIMO)
            val resposta = try {
                limparMarkdown(chamarIAConversa("chat", listOf("system" to "$SISTEMA_CHAT\n\nDADOS DO MÊS:\n$contextoDoMes") + historico))
            } catch (e: FalhaIA) { e.message ?: "Falha na IA." }
            conversa.add("assistant" to resposta)
            respondendo = false
        }
    }

    LaunchedEffect(conversa.size, respondendo) { if (conversa.isNotEmpty()) lista.animateScrollToItem(conversa.size) }

    ModalBottomSheet(onDismissRequest = onFechar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colorSurface) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f).imePadding().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).background(colorAccent.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AutoAwesome, null, tint = colorAccent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Consultor FluxAí", fontWeight = FontWeight.Bold, color = colorTextPrimary)
                    Text("Análise do mês e perguntas", fontSize = 12.sp, color = colorTextSecondary)
                }
            }
            Spacer(Modifier.height(12.dp))

            LazyColumn(state = lista, modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (carregandoAnalise) {
                    item { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(color = colorAccent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("Analisando seu mês...", color = colorTextSecondary) } }
                }
                itemsIndexed(conversa) { _, (papel, texto) ->
                    val minha = papel == "user"
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (minha) Arrangement.End else Arrangement.Start) {
                        Surface(
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (minha) 16.dp else 4.dp, bottomEnd = if (minha) 4.dp else 16.dp),
                            color = if (minha) colorAccent else colorAccent.copy(alpha = 0.08f),
                            modifier = Modifier.widthIn(max = 300.dp)
                        ) {
                            Text(texto, color = if (minha) Color.White else colorTextPrimary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                        }
                    }
                }
                if (respondendo) {
                    item { Text("Consultor digitando...", fontSize = 12.sp, color = colorTextSecondary, modifier = Modifier.padding(start = 4.dp)) }
                }
            }

            // Perguntas prontas enquanto a conversa está no começo
            if (!carregandoAnalise && conversa.size <= 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    listOf("Onde posso economizar?", "Consigo guardar R$ 500 este mês?", "Qual gasto mais pesa?", "Como montar uma reserva?").forEach { s ->
                        AssistChip(onClick = { perguntar(s) }, label = { Text(s, fontSize = 12.sp) }, shape = RoundedCornerShape(50))
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = pergunta, onValueChange = { pergunta = it },
                    placeholder = { Text("Pergunte sobre suas finanças", color = colorTextSecondary) },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp), maxLines = 3,
                    enabled = !carregandoAnalise,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { perguntar(pergunta) }),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colorAccent, focusedTextColor = colorTextPrimary, unfocusedTextColor = colorTextPrimary)
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = { perguntar(pergunta) }, enabled = pergunta.isNotBlank() && !respondendo && !carregandoAnalise,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = colorAccent)
                ) { Icon(Icons.AutoMirrored.Filled.Send, "Enviar") }
            }
        }
    }
}
