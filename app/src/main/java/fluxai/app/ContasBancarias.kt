package fluxai.app

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.NumberFormat
import java.util.Locale

// =========================================================================
// CONTAS BANCÁRIAS
// Cada conta tem um saldo real, que muda quando um lançamento ligado a ela é pago
// (ou deixa de ser), por entradas e por transferências. Tudo com incrementos no
// servidor, para dois aparelhos da conta conjunta não sobrescreverem um ao outro.
// =========================================================================
data class ContaBancaria(val id: String = "", val nome: String = "", val tipo: String = "Corrente", val saldo: Double = 0.0)

val TiposConta: List<Pair<String, ImageVector>> = listOf(
    "Corrente" to Icons.Default.AccountBalance,
    "Poupança" to Icons.Default.Savings,
    "Carteira digital" to Icons.Default.PhoneAndroid,
    "Dinheiro" to Icons.Default.Payments
)

fun iconeTipoConta(tipo: String): ImageVector = TiposConta.firstOrNull { it.first == tipo }?.second ?: Icons.Default.AccountBalance

fun refConta(workspaceUid: String, contaId: String): DocumentReference =
    Firebase.firestore.collection("usuarios").document(workspaceUid).collection("contas").document(contaId)

// Efeito de um lançamento no saldo da conta: pago tira o valor, não pago não mexe.
// Chamado ao mudar status, valor ou conta de um lançamento, sempre com o estado antes e depois.
fun ajustarSaldoContas(lote: WriteBatch, workspaceUid: String, antes: Despesa?, depois: Despesa?) {
    fun efeito(d: Despesa?): Pair<String, Double>? =
        d?.contaId?.takeIf { it.isNotBlank() && d.status == "Pago" && d.cartaoId.isNullOrBlank() }?.let { it to -d.valor }
    val a = efeito(antes)
    val b = efeito(depois)
    if (a == b) return
    a?.let { (id, v) -> lote.update(refConta(workspaceUid, id), "saldo", FieldValue.increment(-v)) }
    b?.let { (id, v) -> lote.update(refConta(workspaceUid, id), "saldo", FieldValue.increment(v)) }
}

// Muda o status de um lançamento já corrigindo o saldo da conta e registrando quem pagou
fun mudarStatusDespesa(workspaceUid: String, despesa: Despesa, novoStatus: String, onErro: (String) -> Unit = {}) {
    val usuario = Firebase.auth.currentUser
    val lote = Firebase.firestore.batch()
    val ref = Firebase.firestore.collection("usuarios").document(workspaceUid).collection("despesas").document(despesa.id)
    val campos = mutableMapOf<String, Any?>("status" to novoStatus)
    if (novoStatus == "Pago") {
        campos["pagoPor"] = usuario?.uid
        campos["pagoPorNome"] = usuario?.displayName?.split(" ")?.firstOrNull() ?: usuario?.email
    } else {
        campos["pagoPor"] = FieldValue.delete()
        campos["pagoPorNome"] = FieldValue.delete()
    }
    lote.update(ref, campos)
    ajustarSaldoContas(lote, workspaceUid, despesa, despesa.copy(status = novoStatus))
    lote.commit().addOnFailureListener { onErro(it.message ?: "Falha ao salvar") }
}

class ContasViewModel : ViewModel() {
    private val banco = Firebase.firestore
    private var ouvinte: ListenerRegistration? = null
    private var workspaceUid = ""

    private val _contas = MutableStateFlow<List<ContaBancaria>>(emptyList())
    val contas: StateFlow<List<ContaBancaria>> = _contas.asStateFlow()

    private fun colecao() = banco.collection("usuarios").document(workspaceUid).collection("contas")

    fun observar(workspace: String) {
        if (workspace == workspaceUid || workspace.isBlank()) return
        workspaceUid = workspace
        ouvinte?.remove()
        ouvinte = colecao().addSnapshotListener { snap, _ ->
            _contas.value = snap?.documents?.mapNotNull { d ->
                runCatching { ContaBancaria(d.id, d.getString("nome") ?: "", d.getString("tipo") ?: "Corrente", d.getDouble("saldo") ?: 0.0) }.getOrNull()
            }?.sortedBy { it.nome.lowercase() } ?: emptyList()
        }
    }

    fun salvar(id: String?, nome: String, tipo: String, saldo: Double, aoTerminar: (Boolean) -> Unit) {
        val dados = mapOf("nome" to nome.trim(), "tipo" to tipo, "saldo" to saldo)
        val tarefa = if (id == null) colecao().add(dados) else colecao().document(id).update(dados)
        tarefa.addOnSuccessListener { aoTerminar(true) }.addOnFailureListener { aoTerminar(false) }
    }

    fun excluir(conta: ContaBancaria) { colecao().document(conta.id).delete() }

    fun registrarEntrada(conta: ContaBancaria, valor: Double, aoTerminar: (Boolean) -> Unit) {
        colecao().document(conta.id).update("saldo", FieldValue.increment(valor))
            .addOnSuccessListener { aoTerminar(true) }.addOnFailureListener { aoTerminar(false) }
    }

    fun transferir(origem: ContaBancaria, destino: ContaBancaria, valor: Double, aoTerminar: (Boolean) -> Unit) {
        val lote = banco.batch()
        lote.update(colecao().document(origem.id), "saldo", FieldValue.increment(-valor))
        lote.update(colecao().document(destino.id), "saldo", FieldValue.increment(valor))
        lote.commit().addOnSuccessListener { aoTerminar(true) }.addOnFailureListener { aoTerminar(false) }
    }

    override fun onCleared() { ouvinte?.remove() }
}

private enum class AcaoConta { NOVA, EDITAR, ENTRADA, TRANSFERIR }

@Composable
fun ContasBancariasScreen(onLogout: () -> Unit) {
    val context = LocalContext.current
    val usuario = Firebase.auth.currentUser ?: return
    val workspaceUid = remember { workspaceAtual(context, usuario.uid) }
    val c = coresTela()
    val moeda = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    val vm: ContasViewModel = viewModel()
    LaunchedEffect(workspaceUid) { vm.observar(workspaceUid) }
    val contas by vm.contas.collectAsStateWithLifecycle()

    var acao by remember { mutableStateOf<AcaoConta?>(null) }
    var contaSelecionada by remember { mutableStateOf<ContaBancaria?>(null) }
    var contaParaExcluir by remember { mutableStateOf<ContaBancaria?>(null) }

    TelaComMenu(
        titulo = "Contas bancárias", rota = "contas", onLogout = onLogout,
        botaoFlutuante = {
            ExtendedFloatingActionButton(
                onClick = { contaSelecionada = null; acao = AcaoConta.NOVA },
                containerColor = c.destaque, contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, null) }, text = { Text("Nova conta") }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                val total = contas.sumOf { it.saldo }
                Surface(shape = RoundedCornerShape(20.dp), color = c.superficie, border = BorderStroke(1.dp, c.divisor), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Saldo em todas as contas", fontSize = 12.sp, color = c.textoFraco)
                        Text(moeda.format(total), fontSize = 30.sp, fontWeight = FontWeight.Black, color = if (total < 0) Color(0xFFE53935) else c.texto)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Lançamentos pagos por uma conta descontam do saldo dela. Cartões de crédito ficam de fora até a fatura ser paga.",
                            fontSize = 12.sp, color = c.textoFraco, lineHeight = 16.sp
                        )
                        if (contas.size >= 2) {
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = { contaSelecionada = contas.first(); acao = AcaoConta.TRANSFERIR }, shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Default.SwapHoriz, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Transferir entre contas")
                            }
                        }
                    }
                }
            }

            if (contas.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(88.dp).background(c.destaque.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AccountBalance, null, Modifier.size(44.dp), tint = c.destaque)
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Nenhuma conta cadastrada", fontWeight = FontWeight.Bold, color = c.texto)
                        Text(
                            "Cadastre sua conta corrente, poupança ou o dinheiro da carteira para acompanhar o saldo real de cada uma.",
                            fontSize = 13.sp, color = c.textoFraco, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            items(contas, key = { it.id }) { conta ->
                var menu by remember { mutableStateOf(false) }
                Surface(shape = RoundedCornerShape(18.dp), color = c.superficie, border = BorderStroke(1.dp, c.divisor), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).background(c.destaque.copy(alpha = 0.12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            Icon(iconeTipoConta(conta.tipo), null, tint = c.destaque)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                            Text(conta.nome, fontWeight = FontWeight.Bold, color = c.texto, fontSize = 15.sp)
                            Text(conta.tipo, fontSize = 12.sp, color = c.textoFraco)
                        }
                        Text(moeda.format(conta.saldo), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (conta.saldo < 0) Color(0xFFE53935) else c.texto)
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Opções de ${conta.nome}", tint = c.textoFraco) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Registrar entrada") }, leadingIcon = { Icon(Icons.Default.AddCard, null, tint = Color(0xFF43A047)) },
                                    onClick = { menu = false; contaSelecionada = conta; acao = AcaoConta.ENTRADA })
                                if (contas.size >= 2) DropdownMenuItem(text = { Text("Transferir") }, leadingIcon = { Icon(Icons.Default.SwapHoriz, null, tint = c.destaque) },
                                    onClick = { menu = false; contaSelecionada = conta; acao = AcaoConta.TRANSFERIR })
                                DropdownMenuItem(text = { Text("Editar ou ajustar saldo") }, leadingIcon = { Icon(Icons.Default.Edit, null, tint = c.destaque) },
                                    onClick = { menu = false; contaSelecionada = conta; acao = AcaoConta.EDITAR })
                                DropdownMenuItem(text = { Text("Excluir", color = Color(0xFFE53935)) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFE53935)) },
                                    onClick = { menu = false; contaParaExcluir = conta })
                            }
                        }
                    }
                }
            }
        }
    }

    when (acao) {
        AcaoConta.NOVA, AcaoConta.EDITAR -> DialogoConta(contaSelecionada, c, onFechar = { acao = null }) { nome, tipo, saldo ->
            vm.salvar(contaSelecionada?.id, nome, tipo, saldo) { ok ->
                if (ok) acao = null else Toast.makeText(context, "Não foi possível salvar a conta.", Toast.LENGTH_SHORT).show()
            }
        }
        AcaoConta.ENTRADA -> contaSelecionada?.let { conta ->
            DialogoValor("Entrada em ${conta.nome}", "Salário, Pix recebido, rendimento... O valor soma no saldo desta conta.", c, onFechar = { acao = null }) { v ->
                vm.registrarEntrada(conta, v) { ok -> if (ok) acao = null }
            }
        }
        AcaoConta.TRANSFERIR -> DialogoTransferencia(contas, contaSelecionada, c, onFechar = { acao = null }) { origem, destino, v ->
            vm.transferir(origem, destino, v) { ok ->
                if (ok) { acao = null; Toast.makeText(context, "Transferência registrada.", Toast.LENGTH_SHORT).show() }
                else Toast.makeText(context, "Não foi possível transferir.", Toast.LENGTH_SHORT).show()
            }
        }
        null -> Unit
    }
    contaParaExcluir?.let { conta ->
        AlertDialog(
            onDismissRequest = { contaParaExcluir = null },
            containerColor = c.superficie,
            icon = { Icon(Icons.Default.DeleteForever, null, tint = Color(0xFFE53935)) },
            title = { Text("Excluir \"${conta.nome}\"?", color = c.texto) },
            text = { Text("Os lançamentos continuam, mas deixam de mexer no saldo de uma conta.", color = c.textoFraco) },
            confirmButton = { Button(onClick = { vm.excluir(conta); contaParaExcluir = null }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { contaParaExcluir = null }) { Text("Cancelar", color = c.textoFraco) } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DialogoConta(conta: ContaBancaria?, c: CoresTela, onFechar: () -> Unit, onSalvar: (String, String, Double) -> Unit) {
    var nome by remember { mutableStateOf(conta?.nome ?: "") }
    var tipo by remember { mutableStateOf(conta?.tipo ?: "Corrente") }
    var saldo by remember { mutableStateOf(conta?.saldo?.paraCampo() ?: "") }
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onFechar,
        containerColor = c.superficie,
        shape = RoundedCornerShape(28.dp),
        title = { Text(if (conta == null) "Nova conta" else "Editar conta", fontWeight = FontWeight.Bold, color = c.texto) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nome, onValueChange = { nome = it }, label = { Text("Nome (ex.: Nubank, Carteira)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(), shape = FormatoCampo, colors = coresCampoTela(c)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TiposConta.forEach { (t, icone) ->
                        FilterChip(
                            selected = tipo == t, onClick = { tipo = t }, label = { Text(t) },
                            leadingIcon = { Icon(icone, null, Modifier.size(16.dp)) }, shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.destaque.copy(alpha = 0.14f), selectedLabelColor = c.destaque, selectedLeadingIconColor = c.destaque)
                        )
                    }
                }
                OutlinedTextField(
                    value = saldo, onValueChange = { saldo = it.replace('.', ',') },
                    label = { Text(if (conta == null) "Saldo atual" else "Saldo (ajuste se estiver diferente do banco)") },
                    prefix = { Text("R$ ") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), shape = FormatoCampo, colors = coresCampoTela(c)
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (nome.isBlank()) Toast.makeText(context, "Dê um nome para a conta.", Toast.LENGTH_SHORT).show()
                else onSalvar(nome, tipo, saldo.paraValor() ?: 0.0)
            }, colors = ButtonDefaults.buttonColors(containerColor = c.destaque)) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onFechar) { Text("Cancelar", color = c.textoFraco) } }
    )
}

// Diálogo simples para digitar um valor em reais
@Composable
fun DialogoValor(titulo: String, explicacao: String, c: CoresTela, onFechar: () -> Unit, onConfirmar: (Double) -> Unit) {
    var valor by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onFechar,
        containerColor = c.superficie,
        shape = RoundedCornerShape(28.dp),
        title = { Text(titulo, fontWeight = FontWeight.Bold, color = c.texto) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(explicacao, fontSize = 13.sp, color = c.textoFraco, lineHeight = 18.sp)
                OutlinedTextField(
                    value = valor, onValueChange = { valor = it.replace('.', ',') }, label = { Text("Valor") }, prefix = { Text("R$ ") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), shape = FormatoCampo, colors = coresCampoTela(c)
                )
            }
        },
        confirmButton = {
            Button(onClick = { valor.paraValor()?.takeIf { it > 0 }?.let(onConfirmar) }, enabled = (valor.paraValor() ?: 0.0) > 0,
                colors = ButtonDefaults.buttonColors(containerColor = c.destaque)) { Text("Confirmar") }
        },
        dismissButton = { TextButton(onClick = onFechar) { Text("Cancelar", color = c.textoFraco) } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DialogoTransferencia(contas: List<ContaBancaria>, origemInicial: ContaBancaria?, c: CoresTela, onFechar: () -> Unit, onConfirmar: (ContaBancaria, ContaBancaria, Double) -> Unit) {
    var origem by remember { mutableStateOf(origemInicial ?: contas.first()) }
    var destino by remember { mutableStateOf(contas.firstOrNull { it.id != origem.id }) }
    var valor by remember { mutableStateOf("") }
    val v = valor.paraValor() ?: 0.0

    @Composable
    fun Seletor(titulo: String, selecionada: ContaBancaria?, bloqueada: ContaBancaria?, onEscolher: (ContaBancaria) -> Unit) {
        Text(titulo, fontSize = 12.sp, color = c.textoFraco)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            contas.filter { it.id != bloqueada?.id }.forEach { conta ->
                FilterChip(
                    selected = selecionada?.id == conta.id, onClick = { onEscolher(conta) }, label = { Text(conta.nome) }, shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.destaque.copy(alpha = 0.14f), selectedLabelColor = c.destaque)
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = onFechar,
        containerColor = c.superficie,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Transferir entre contas", fontWeight = FontWeight.Bold, color = c.texto) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Seletor("De", origem, null) { origem = it; if (destino?.id == it.id) destino = contas.firstOrNull { d -> d.id != it.id } }
                Seletor("Para", destino, origem) { destino = it }
                OutlinedTextField(
                    value = valor, onValueChange = { valor = it.replace('.', ',') }, label = { Text("Valor") }, prefix = { Text("R$ ") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp), shape = FormatoCampo, colors = coresCampoTela(c)
                )
                Text("Transferências não contam como despesa do mês.", fontSize = 11.sp, color = c.textoFraco)
            }
        },
        confirmButton = {
            Button(onClick = { destino?.let { onConfirmar(origem, it, v) } }, enabled = v > 0 && destino != null,
                colors = ButtonDefaults.buttonColors(containerColor = c.destaque)) { Text("Transferir") }
        },
        dismissButton = { TextButton(onClick = onFechar) { Text("Cancelar", color = c.textoFraco) } }
    )
}
