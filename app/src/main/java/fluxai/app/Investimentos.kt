package fluxai.app

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

// =========================================================================
// INVESTIMENTOS
// Carteira manual: renda fixa (CDI, Selic, prefixado, IPCA+, poupança) e renda variável
// (ações, FIIs, ETFs, cripto). Cada investimento guarda seus movimentos (aportes e resgates);
// o valor atual é calculado com os índices do Banco Central e as cotações do dia.
// Aporte pode virar saída do mês (e descontar de uma conta); resgate vira entrada.
// =========================================================================

data class TipoInvestimento(val nome: String, val icone: ImageVector, val cor: Color, val variavel: Boolean)

val TiposInvestimento = listOf(
    TipoInvestimento("Renda fixa", Icons.Default.AccountBalanceWallet, Color(0xFF1E88E5), false),
    TipoInvestimento("Tesouro Direto", Icons.Default.AccountBalance, Color(0xFF3949AB), false),
    TipoInvestimento("Poupança", Icons.Default.Savings, Color(0xFF43A047), false),
    TipoInvestimento("Ações", Icons.AutoMirrored.Filled.ShowChart, Color(0xFF7E57C2), true),
    TipoInvestimento("FIIs", Icons.Default.Apartment, Color(0xFFFB8C00), true),
    TipoInvestimento("ETFs", Icons.Default.PieChart, Color(0xFF00ACC1), true),
    TipoInvestimento("Cripto", Icons.Default.CurrencyBitcoin, Color(0xFFF9A825), true)
)

fun tipoInvestimento(nome: String) = TiposInvestimento.firstOrNull { it.nome == nome } ?: TiposInvestimento.first()

val IndexadoresRendaFixa = listOf(
    INDEXADOR_CDI to "% do CDI",
    INDEXADOR_SELIC to "Selic",
    INDEXADOR_PREFIXADO to "Prefixado",
    INDEXADOR_IPCA to "IPCA +"
)

// Renda fixa: valor em R$ (+ aporte, - resgate recebido) e custo (quanto do aplicado saiu, sem o rendimento).
// Variável: quantidade (+ compra, - venda) e preço unitário.
data class MovimentoInvestimento(
    val id: String, val data: String, val valor: Double = 0.0, val quantidade: Double = 0.0, val preco: Double = 0.0,
    val custo: Double? = null,
    val bruto: Map<String, Any?> = emptyMap()   // mapa original do Firestore (para remover com arrayRemove)
)

data class Investimento(
    val id: String = "",
    val nome: String = "",
    val tipo: String = "Renda fixa",
    val instituicao: String = "",
    val indexador: String = INDEXADOR_CDI,
    val taxa: Double = 100.0,          // % do CDI/Selic ou % ao ano (prefixado, IPCA +)
    val vencimento: String = "",       // aaaa-mm-dd
    val isento: Boolean = false,       // LCI, LCA, CRI, CRA, debêntures incentivadas
    val ticker: String = "",
    val caixinhaId: String = "",       // meta do Cofre que este investimento ajuda a alcançar
    val movimentos: List<MovimentoInvestimento> = emptyList()
) {
    val variavel get() = tipoInvestimento(tipo).variavel
    val indexadorEfetivo get() = if (tipo == "Poupança") INDEXADOR_POUPANCA else indexador
}

data class InvestimentoCalculado(
    val inv: Investimento,
    val investido: Double,       // custo do que ainda está aplicado
    val valorAtual: Double,
    val rendeuNoMes: Double,
    val liquidoEstimado: Double, // valor atual menos o IR estimado (renda fixa tributada)
    val quantidade: Double = 0.0,
    val precoMedio: Double = 0.0,
    val cotacao: Cotacao? = null,
    val semCotacao: Boolean = false
) {
    val rendimento get() = valorAtual - investido
    val rendimentoPct get() = if (investido > 0) rendimento / investido * 100 else 0.0
}

// Um ponto do gráfico: patrimônio e valor aplicado no fim de um mês. O primeiro ponto pode ser o dia
// da primeira aplicação (rotuloFixo "dd/mm") e o último é hoje.
data class PontoEvolucao(val mes: String, val patrimonio: Double, val aplicado: Double, val rotuloFixo: String? = null) {
    val hoje get() = mes.endsWith("*")
    val rotulo get() = rotuloFixo ?: if (hoje) "hoje" else rotuloMes(mes)
}

private val MesesCurtos = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")
fun rotuloMes(mes: String) = "${MesesCurtos[mes.substring(5, 7).toInt() - 1]}/${mes.substring(2, 4)}"

data class EstadoInvestimentos(
    val carregando: Boolean = true,
    val itens: List<InvestimentoCalculado> = emptyList(),
    val evolucao: List<PontoEvolucao> = emptyList(),
    val focus: List<ExpectativaFocus> = emptyList(),
    val cdiAnual: Double? = null,
    val erroMercado: Boolean = false
) {
    val total get() = itens.sumOf { it.valorAtual }
    val investido get() = itens.sumOf { it.investido }
    val rendimento get() = total - investido
    val rendimentoPct get() = if (investido > 0) rendimento / investido * 100 else 0.0
    val rendeuNoMes get() = itens.sumOf { it.rendeuNoMes }
    val liquido get() = itens.sumOf { it.liquidoEstimado }
    // Valor atual investido para cada meta do Cofre
    val porMeta get() = itens.filter { it.inv.caixinhaId.isNotBlank() }.groupBy { it.inv.caixinhaId }.mapValues { e -> e.value.sumOf { it.valorAtual } }
}

private fun diaAnterior(iso: String): String {
    val c = Calendar.getInstance().apply { clear(); set(iso.substring(0, 4).toInt(), iso.substring(5, 7).toInt() - 1, iso.substring(8, 10).toInt()); add(Calendar.DAY_OF_MONTH, -1) }
    return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}

// Quantidade atual e preço médio: compras entram na média ponderada; vendas só reduzem a quantidade
fun posicaoVariavel(movimentos: List<MovimentoInvestimento>): Pair<Double, Double> {
    var qtd = 0.0; var pm = 0.0
    movimentos.sortedBy { it.data }.forEach { m ->
        if (m.quantidade > 0) { pm = (qtd * pm + m.quantidade * m.preco) / (qtd + m.quantidade); qtd += m.quantidade }
        else qtd = (qtd + m.quantidade).coerceAtLeast(0.0)
        if (qtd == 0.0) pm = 0.0
    }
    return qtd to pm
}

// Calcula valor atual, rendimento e rendimento do mês de cada investimento
suspend fun calcularCarteira(lista: List<Investimento>): Pair<List<InvestimentoCalculado>, Boolean> {
    val hoje = hojeIso()
    val inicioMes = inicioDoMesIso()
    val fimMesAnterior = diaAnterior(inicioMes)
    var falhou = false
    val cotacoes = lista.filter { it.variavel && it.ticker.isNotBlank() }
        .map { it.ticker to (it.tipo == "Cripto") }
        .takeIf { it.isNotEmpty() }?.let { Mercado.cotacoes(it) } ?: emptyMap()

    val itens = lista.map { inv ->
        val movs = inv.movimentos.sortedBy { it.data }
        if (inv.variavel) {
            val (qtd, pm) = posicaoVariavel(movs)
            val cot = cotacoes[inv.ticker]
            val preco = cot?.preco ?: pm
            val qtdInicioMes = movs.filter { it.data < inicioMes }.sumOf { it.quantidade }.coerceAtLeast(0.0)
            val fluxoMes = movs.filter { it.data >= inicioMes }.sumOf { it.quantidade * it.preco }
            val valorAtual = qtd * preco
            val rendeuMes = if (cot != null) valorAtual - qtdInicioMes * cot.precoInicioMes - fluxoMes else 0.0
            InvestimentoCalculado(inv, qtd * pm, valorAtual, rendeuMes, valorAtual, qtd, pm, cot, semCotacao = cot == null && qtd > 0)
        } else {
            var valorAtual = 0.0; var valorInicioMes = 0.0; var aportesMes = 0.0
            try {
                movs.forEach { m ->
                    valorAtual += m.valor * Mercado.fatorRendaFixa(inv.indexadorEfetivo, inv.taxa, m.data, hoje)
                    if (m.data < inicioMes) valorInicioMes += m.valor * Mercado.fatorRendaFixa(inv.indexadorEfetivo, inv.taxa, m.data, fimMesAnterior)
                    else aportesMes += m.valor
                }
            } catch (e: Exception) {
                falhou = true
                valorAtual = movs.sumOf { it.custo ?: it.valor }; valorInicioMes = valorAtual; aportesMes = 0.0
            }
            val investido = movs.sumOf { it.custo ?: it.valor }
            val lucro = valorAtual - investido
            val isento = inv.isento || inv.tipo == "Poupança"
            val dias = movs.firstOrNull()?.let { diaSequencial(hoje) - diaSequencial(it.data) } ?: 0L
            val ir = if (!isento && lucro > 0) lucro * aliquotaIR(dias) else 0.0
            InvestimentoCalculado(inv, investido, valorAtual, valorAtual - valorInicioMes - aportesMes, valorAtual - ir)
        }
    }.filter { it.valorAtual > 0.005 || it.investido > 0.005 || it.inv.movimentos.isEmpty() }
    return itens to falhou
}

// Patrimônio no fim de cada um dos últimos meses, desde o primeiro movimento (máx. 12 meses + hoje).
// Renda fixa pelos índices até aquela data; renda variável pelo fechamento do mês (sem histórico, usa o preço médio).
suspend fun evolucaoCarteira(lista: List<Investimento>, atual: EstadoInvestimentos): List<PontoEvolucao> {
    val primeiro = lista.flatMap { it.movimentos }.minOfOrNull { it.data } ?: return emptyList()
    val hoje = hojeIso()
    val fins = mutableListOf<String>()
    val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.DAY_OF_MONTH, -1) }
    repeat(12) {
        val fim = "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        if (fim.substring(0, 7) >= primeiro.substring(0, 7)) fins += fim
        cal.set(Calendar.DAY_OF_MONTH, 1); cal.add(Calendar.DAY_OF_MONTH, -1)
    }
    val historico = lista.filter { it.variavel && it.ticker.isNotBlank() }.map { it.ticker to (it.tipo == "Cripto") }
        .takeIf { it.isNotEmpty() }?.let { Mercado.historicoPrecos(it) } ?: emptyMap()
    // Patrimônio e aplicado numa data (no dia da primeira aplicação, ações pelo preço pago)
    suspend fun valoresEm(data: String, usarFechamentoDoMes: Boolean): Pair<Double, Double> {
        var patrimonio = 0.0; var aplicado = 0.0
        lista.forEach { inv ->
            val movs = inv.movimentos.filter { it.data <= data }
            if (movs.isEmpty()) return@forEach
            if (inv.variavel) {
                val (qtd, pm) = posicaoVariavel(movs)
                val preco = if (usarFechamentoDoMes) historico[inv.ticker]?.get(data.substring(0, 7)) else null
                patrimonio += qtd * (preco ?: pm)
                aplicado += qtd * pm
            } else {
                movs.forEach { m -> patrimonio += m.valor * runCatching { Mercado.fatorRendaFixa(inv.indexadorEfetivo, inv.taxa, m.data, data) }.getOrDefault(1.0) }
                aplicado += movs.sumOf { it.custo ?: it.valor }
            }
        }
        return patrimonio.coerceAtLeast(0.0) to aplicado.coerceAtLeast(0.0)
    }
    val pontos = fins.reversed().map { fim ->
        val (patrimonio, aplicado) = valoresEm(fim, usarFechamentoDoMes = true)
        PontoEvolucao(fim.substring(0, 7), patrimonio, aplicado)
    }
    // Começa no dia da primeira aplicação quando ele não coincide com um fim de mês já no gráfico
    val inicio = if (primeiro < hoje && pontos.none { it.mes == primeiro.substring(0, 7) && fins.contains(primeiro) }) {
        val (patrimonio, aplicado) = valoresEm(primeiro, usarFechamentoDoMes = false)
        listOf(PontoEvolucao(primeiro.substring(0, 7) + "i", patrimonio, aplicado, rotuloFixo = isoParaBr(primeiro).substring(0, 5)))
    } else emptyList()
    return inicio + pontos + PontoEvolucao(hoje.substring(0, 7) + "*", atual.total, atual.investido)
}

fun lerInvestimento(d: com.google.firebase.firestore.DocumentSnapshot): Investimento? = runCatching {
    @Suppress("UNCHECKED_CAST")
    val movs = (d.get("movimentos") as? List<Map<String, Any?>>).orEmpty().mapNotNull { m ->
        MovimentoInvestimento(
            m["id"]?.toString() ?: return@mapNotNull null, m["data"]?.toString() ?: return@mapNotNull null,
            (m["valor"] as? Number)?.toDouble() ?: 0.0, (m["quantidade"] as? Number)?.toDouble() ?: 0.0,
            (m["preco"] as? Number)?.toDouble() ?: 0.0, (m["custo"] as? Number)?.toDouble(), m
        )
    }
    Investimento(
        d.id, d.getString("nome") ?: "", d.getString("tipo") ?: "Renda fixa", d.getString("instituicao") ?: "",
        d.getString("indexador") ?: INDEXADOR_CDI, d.getDouble("taxa") ?: 100.0, d.getString("vencimento") ?: "",
        d.getBoolean("isento") ?: false, d.getString("ticker") ?: "", d.getString("caixinhaId") ?: "", movs
    )
}.getOrNull()

class InvestimentosViewModel : ViewModel() {
    private val banco = Firebase.firestore
    private var ouvinte: ListenerRegistration? = null
    private var workspaceUid = ""
    private var brutos: List<Investimento> = emptyList()
    private var calculo: Job? = null

    private val _estado = MutableStateFlow(EstadoInvestimentos())
    val estado: StateFlow<EstadoInvestimentos> = _estado.asStateFlow()

    private fun colecao() = banco.collection("usuarios").document(workspaceUid).collection("investimentos")
    private fun usuarioDoc() = banco.collection("usuarios").document(workspaceUid)

    fun observar(workspace: String) {
        if (workspace == workspaceUid || workspace.isBlank()) return
        workspaceUid = workspace
        ouvinte?.remove()
        ouvinte = colecao().addSnapshotListener { snap, _ ->
            brutos = snap?.documents?.mapNotNull { lerInvestimento(it) } ?: emptyList()
            recalcular()
        }
        viewModelScope.launch { _estado.value = _estado.value.copy(cdiAnual = Mercado.cdiAnualAtual()) }
        viewModelScope.launch { BancoCentral.expectativas()?.let { _estado.value = _estado.value.copy(focus = it) } }
    }

    fun recalcular() {
        calculo?.cancel()
        val lista = brutos
        calculo = viewModelScope.launch {
            val (itens, falhou) = calcularCarteira(lista)
            _estado.value = _estado.value.copy(carregando = false, itens = itens.sortedByDescending { it.valorAtual }, erroMercado = falhou)
            _estado.value = _estado.value.copy(evolucao = runCatching { evolucaoCarteira(lista, _estado.value) }.getOrDefault(emptyList()))
        }
    }

    private fun movimentoParaMapa(m: MovimentoInvestimento) =
        mapOf("id" to m.id, "data" to m.data, "valor" to m.valor, "quantidade" to m.quantidade, "preco" to m.preco) +
            (m.custo?.let { mapOf("custo" to it) } ?: emptyMap())

    private fun camposInvestimento(inv: Investimento) = mapOf(
        "nome" to inv.nome.trim(), "tipo" to inv.tipo, "instituicao" to inv.instituicao.trim(),
        "indexador" to inv.indexador, "taxa" to inv.taxa, "vencimento" to inv.vencimento, "isento" to inv.isento,
        "ticker" to inv.ticker.trim().uppercase(), "caixinhaId" to inv.caixinhaId
    )

    // Aporte: despesa paga no mês do movimento (desconta da conta, se escolhida). Resgate: renda extra + entrada na conta.
    private fun lancarNoMes(lote: com.google.firebase.firestore.WriteBatch, inv: Investimento, mov: MovimentoInvestimento, contaId: String?) {
        val valor = if (inv.variavel) kotlin.math.abs(mov.quantidade * mov.preco) else kotlin.math.abs(mov.valor)
        if (valor <= 0) return
        val (a, m, d) = mov.data.split("-")
        val mesAno = "$m/$a"
        val aporte = if (inv.variavel) mov.quantidade > 0 else mov.valor > 0
        if (aporte) {
            val ref = usuarioDoc().collection("despesas").document()
            val despesa = Despesa(ref.id, "Aporte: ${inv.nome}", valor, "Variável", "Investimentos", "Pago", "Investimento", d.toInt(), "Única", mesAno, contaId = contaId)
            val usuario = Firebase.auth.currentUser
            lote.set(ref, hashMapOf(
                "descricao" to despesa.descricao, "valor" to valor, "diaVencimento" to despesa.diaVencimento, "tipo" to despesa.tipo,
                "categoria" to despesa.categoria, "status" to "Pago", "mesAno" to mesAno, "observacao" to despesa.observacao,
                "frequencia" to "Única", "contaId" to contaId, "investimentoId" to inv.id, "caixinhaId" to inv.caixinhaId.ifBlank { null },
                "pagoPor" to usuario?.uid, "pagoPorNome" to (usuario?.displayName?.split(" ")?.firstOrNull() ?: usuario?.email)
            ))
            ajustarSaldoContas(lote, workspaceUid, null, despesa)
        } else {
            lote.set(usuarioDoc().collection("saldos").document("$m-$a"), mapOf("extra" to FieldValue.increment(valor), "valor" to FieldValue.increment(valor)), SetOptions.merge())
            contaId?.let { lote.update(refConta(workspaceUid, it), "saldo", FieldValue.increment(valor)) }
        }
    }

    fun criar(inv: Investimento, primeiro: MovimentoInvestimento?, lancar: Boolean, contaId: String?, aoTerminar: (Boolean) -> Unit) {
        val ref = colecao().document()
        val lote = banco.batch()
        lote.set(ref, camposInvestimento(inv) + ("movimentos" to listOfNotNull(primeiro?.let { movimentoParaMapa(it) })) + ("criadoEm" to FieldValue.serverTimestamp()))
        if (primeiro != null && lancar) lancarNoMes(lote, inv.copy(id = ref.id), primeiro, contaId)
        lote.commit().addOnSuccessListener { aoTerminar(true) }.addOnFailureListener { aoTerminar(false) }
    }

    fun editar(inv: Investimento, aoTerminar: (Boolean) -> Unit) {
        colecao().document(inv.id).update(camposInvestimento(inv))
            .addOnSuccessListener { aoTerminar(true) }.addOnFailureListener { aoTerminar(false) }
    }

    fun movimentar(inv: Investimento, mov: MovimentoInvestimento, lancar: Boolean, contaId: String?, aoTerminar: (Boolean) -> Unit) {
        val lote = banco.batch()
        lote.update(colecao().document(inv.id), "movimentos", FieldValue.arrayUnion(movimentoParaMapa(mov)))
        if (lancar) lancarNoMes(lote, inv, mov, contaId)
        lote.commit().addOnSuccessListener { aoTerminar(true) }.addOnFailureListener { aoTerminar(false) }
    }

    fun removerMovimento(inv: Investimento, mov: MovimentoInvestimento) {
        colecao().document(inv.id).update("movimentos", FieldValue.arrayRemove(mov.bruto))
    }

    fun excluir(inv: Investimento) { colecao().document(inv.id).delete() }

    override fun onCleared() { ouvinte?.remove() }
}

// Resumo da carteira para o Consultor IA (sem recomendar ativos)
fun resumoCarteiraParaIA(estado: EstadoInvestimentos, rs: (Double) -> String): String {
    if (estado.itens.isEmpty()) return "[INVESTIMENTOS]\n- Nenhum investimento cadastrado"
    val porTipo = estado.itens.groupBy { it.inv.tipo }.mapValues { e -> e.value.sumOf { it.valorAtual } }
        .entries.sortedByDescending { it.value }
        .joinToString("\n") { (t, v) -> "- $t: ${rs(v)} (${if (estado.total > 0) (v / estado.total * 100).toInt() else 0}%)" }
    return buildString {
        appendLine("[INVESTIMENTOS]")
        appendLine("- Patrimônio investido: ${rs(estado.total)} (aplicado: ${rs(estado.investido)}, rendimento: ${rs(estado.rendimento)})")
        appendLine("- Rendeu neste mês: ${rs(estado.rendeuNoMes)}")
        append(porTipo)
        if (estado.focus.isNotEmpty()) {
            appendLine(); append("- Expectativa do mercado (Focus): " + estado.focus.joinToString("; ") { "${it.indicador} ${it.ano} ${String.format(Locale("pt", "BR"), "%.2f", it.mediana)}%" })
        }
    }
}

// =========================================================================
// TELA
// =========================================================================
private val moedaBR: NumberFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
private fun pct(v: Double) = (if (v >= 0) "+" else "") + String.format(Locale("pt", "BR"), "%.2f%%", v)
private fun numeroBR(v: Double, casas: Int = 2) = String.format(Locale("pt", "BR"), "%,.${casas}f", v)
private val Verde = Color(0xFF43A047)
private val Vermelho = Color(0xFFE53935)

private sealed interface AcaoInvestimento {
    data object Novo : AcaoInvestimento
    data class Editar(val inv: Investimento) : AcaoInvestimento
    data class Movimentar(val calc: InvestimentoCalculado, val entrada: Boolean) : AcaoInvestimento
    data class Historico(val inv: Investimento) : AcaoInvestimento
    data class Excluir(val inv: Investimento) : AcaoInvestimento
}

@Composable
fun InvestimentosScreen(onLogout: () -> Unit) {
    val context = LocalContext.current
    val usuario = Firebase.auth.currentUser ?: return
    val workspaceUid = remember { workspaceAtual(context, usuario.uid) }
    val c = coresTela()
    val vm: InvestimentosViewModel = viewModel()
    val vmContas: ContasViewModel = viewModel()
    val vmMetas: CaixinhasViewModel = viewModel()
    LaunchedEffect(workspaceUid) { vm.observar(workspaceUid); vmContas.observar(workspaceUid); vmMetas.observar(workspaceUid) }
    val estado by vm.estado.collectAsStateWithLifecycle()
    val contas by vmContas.contas.collectAsStateWithLifecycle()
    val metas by vmMetas.caixinhas.collectAsStateWithLifecycle()
    var acao by remember { mutableStateOf<AcaoInvestimento?>(null) }

    TelaComMenu(
        titulo = "Investimentos", rota = "investimentos", onLogout = onLogout,
        acoes = { IconButton(onClick = { vm.recalcular() }) { Icon(Icons.Default.Refresh, "Atualizar cotações", tint = c.texto) } },
        botaoFlutuante = {
            ExtendedFloatingActionButton(
                onClick = { acao = AcaoInvestimento.Novo }, containerColor = c.destaque, contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, null) }, text = { Text("Novo investimento") }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { ResumoCarteira(estado, c) }

            if (estado.evolucao.size >= 2) item { CardEvolucaoPatrimonio(estado.evolucao, c) }
            else if (!estado.carregando && estado.itens.isNotEmpty()) item {
                Text(
                    "O gráfico da evolução do patrimônio aparece a partir de amanhã, quando houver mais de um dia de histórico.",
                    fontSize = 12.sp, color = c.textoFraco, modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            if (estado.focus.isNotEmpty()) item { CardExpectativasFocus(estado.focus, c) }

            if (estado.itens.size > 1) item { DistribuicaoCarteira(estado, c) }

            if (!estado.carregando && estado.itens.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(88.dp).background(c.destaque.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.TrendingUp, null, Modifier.size(44.dp), tint = c.destaque)
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Nenhum investimento cadastrado", fontWeight = FontWeight.Bold, color = c.texto)
                        Text(
                            "Cadastre seus CDBs, Tesouro, poupança, ações, FIIs ou cripto. O app calcula quanto rendeu com o CDI, a inflação e as cotações do dia.",
                            fontSize = 13.sp, color = c.textoFraco, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            estado.itens.groupBy { it.inv.tipo }.toList()
                .sortedByDescending { (_, l) -> l.sumOf { it.valorAtual } }
                .forEach { (tipo, lista) ->
                    item(key = "cab_$tipo") {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(tipo.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = c.textoFraco, modifier = Modifier.weight(1f))
                            Text(moedaBR.format(lista.sumOf { it.valorAtual }), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.textoFraco)
                        }
                    }
                    items(lista, key = { it.inv.id }) { calc ->
                        CardInvestimento(calc, metas.firstOrNull { it.id == calc.inv.caixinhaId }?.nome, c,
                            onAporte = { acao = AcaoInvestimento.Movimentar(calc, true) },
                            onResgate = { acao = AcaoInvestimento.Movimentar(calc, false) },
                            onHistorico = { acao = AcaoInvestimento.Historico(calc.inv) },
                            onEditar = { acao = AcaoInvestimento.Editar(calc.inv) },
                            onExcluir = { acao = AcaoInvestimento.Excluir(calc.inv) })
                    }
                }

            if (estado.itens.isNotEmpty()) item {
                Text(
                    "Valores brutos, calculados com os índices do Banco Central e as cotações do dia (podem ter atraso). " +
                        "Renda fixa \"na curva\": é o valor se levar até o vencimento; o de mercado pode ser diferente. Não é recomendação de investimento.",
                    fontSize = 11.sp, color = c.textoFraco, lineHeight = 15.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )
            }
        }
    }

    val aviso = { ok: Boolean, msgOk: String -> Toast.makeText(context, if (ok) msgOk else "Não foi possível salvar. Tente de novo.", Toast.LENGTH_SHORT).show() }
    when (val a = acao) {
        AcaoInvestimento.Novo -> DialogoInvestimento(null, metas, contas, c, onFechar = { acao = null }) { inv, primeiro, lancar, contaId ->
            vm.criar(inv, primeiro, lancar, contaId) { ok -> aviso(ok, "Investimento cadastrado."); if (ok) acao = null }
        }
        is AcaoInvestimento.Editar -> DialogoInvestimento(a.inv, metas, contas, c, onFechar = { acao = null }) { inv, _, _, _ ->
            vm.editar(inv.copy(id = a.inv.id)) { ok -> aviso(ok, "Investimento atualizado."); if (ok) acao = null }
        }
        is AcaoInvestimento.Movimentar -> DialogoMovimento(a.calc, a.entrada, contas, c, onFechar = { acao = null }) { mov, lancar, contaId ->
            vm.movimentar(a.calc.inv, mov, lancar, contaId) { ok -> aviso(ok, if (a.entrada) "Aporte registrado." else "Resgate registrado."); if (ok) acao = null }
        }
        is AcaoInvestimento.Historico -> DialogoHistorico(a.inv, c, onFechar = { acao = null }) { mov -> vm.removerMovimento(a.inv, mov) }
        is AcaoInvestimento.Excluir -> AlertDialog(
            onDismissRequest = { acao = null }, containerColor = c.superficie,
            icon = { Icon(Icons.Default.DeleteForever, null, tint = Vermelho) },
            title = { Text("Excluir \"${a.inv.nome}\"?", color = c.texto) },
            text = { Text("O investimento e o histórico dele saem da carteira. Os lançamentos de aporte já feitos no mês continuam.", color = c.textoFraco) },
            confirmButton = { Button(onClick = { vm.excluir(a.inv); acao = null }, colors = ButtonDefaults.buttonColors(containerColor = Vermelho)) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { acao = null }) { Text("Cancelar", color = c.textoFraco) } }
        )
        null -> Unit
    }
}

@Composable
private fun ResumoCarteira(estado: EstadoInvestimentos, c: CoresTela) {
    Surface(shape = RoundedCornerShape(20.dp), color = c.superficie, border = BorderStroke(1.dp, c.divisor), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text("Patrimônio investido", fontSize = 12.sp, color = c.textoFraco)
            if (estado.carregando) {
                Spacer(Modifier.height(8.dp)); CircularProgressIndicator(Modifier.size(24.dp), color = c.destaque, strokeWidth = 2.dp)
            } else {
                Text(moedaBR.format(estado.total), fontSize = 30.sp, fontWeight = FontWeight.Black, color = c.texto)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    InfoResumo("Aplicado", moedaBR.format(estado.investido), c.texto, c, Modifier.weight(1f))
                    InfoResumo("Rendimento", "${moedaBR.format(estado.rendimento)}\n${pct(estado.rendimentoPct)}", if (estado.rendimento >= 0) Verde else Vermelho, c, Modifier.weight(1f))
                    InfoResumo("No mês", moedaBR.format(estado.rendeuNoMes), if (estado.rendeuNoMes >= 0) Verde else Vermelho, c, Modifier.weight(1f))
                }
                if (estado.liquido < estado.total - 0.01) {
                    Spacer(Modifier.height(8.dp))
                    Text("Líquido estimado (descontando IR da renda fixa): ${moedaBR.format(estado.liquido)}", fontSize = 12.sp, color = c.textoFraco)
                }
            }
            estado.cdiAnual?.let { Text("CDI hoje: ${String.format(Locale("pt", "BR"), "%.2f", it)}% ao ano", fontSize = 12.sp, color = c.textoFraco, modifier = Modifier.padding(top = 4.dp)) }
            if (estado.erroMercado) Text("Sem conexão com o Banco Central: a renda fixa aparece pelo valor aplicado.", fontSize = 12.sp, color = Color(0xFFFB8C00), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun InfoResumo(titulo: String, valor: String, cor: Color, c: CoresTela, modifier: Modifier) {
    Column(modifier) {
        Text(titulo, fontSize = 11.sp, color = c.textoFraco)
        Text(valor, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = cor, lineHeight = 16.sp)
    }
}

@Composable
private fun DistribuicaoCarteira(estado: EstadoInvestimentos, c: CoresTela) {
    val partes = estado.itens.groupBy { it.inv.tipo }.mapValues { e -> e.value.sumOf { it.valorAtual } }
        .filter { it.value > 0 }.entries.sortedByDescending { it.value }
    val total = partes.sumOf { it.value }.takeIf { it > 0 } ?: return
    Surface(shape = RoundedCornerShape(20.dp), color = c.superficie, border = BorderStroke(1.dp, c.divisor), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text("Distribuição", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.texto)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape)) {
                partes.forEach { (tipo, v) -> Box(Modifier.weight((v / total).toFloat().coerceAtLeast(0.01f)).fillMaxHeight().background(tipoInvestimento(tipo).cor)) }
            }
            Spacer(Modifier.height(10.dp))
            partes.forEach { (tipo, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(tipoInvestimento(tipo).cor, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(tipo, fontSize = 13.sp, color = c.texto, modifier = Modifier.weight(1f))
                    Text("${(v / total * 100).let { String.format(Locale("pt", "BR"), "%.1f", it) }}%", fontSize = 13.sp, color = c.textoFraco)
                    Spacer(Modifier.width(12.dp))
                    Text(moedaBR.format(v), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.texto)
                }
            }
        }
    }
}

// Linha de detalhe do investimento: taxa e vencimento (renda fixa) ou cotas e cotação (variável)
private fun detalheInvestimento(calc: InvestimentoCalculado): String {
    val inv = calc.inv
    return if (inv.variavel) {
        val casas = if (inv.tipo == "Cripto") 6 else 0
        val qtd = numeroBR(calc.quantidade, if (calc.quantidade % 1.0 == 0.0) 0 else casas.coerceAtLeast(2))
        val cot = calc.cotacao?.let { " · hoje ${moedaBR.format(it.preco)} (${pct(it.variacaoDia)})" } ?: " · sem cotação"
        "$qtd ${if (inv.tipo == "Cripto") "un." else "cotas"} · PM ${moedaBR.format(calc.precoMedio)}$cot"
    } else {
        val taxa = numeroBR(inv.taxa, if (inv.taxa % 1.0 == 0.0) 0 else 2)
        val rent = when (inv.indexadorEfetivo) {
            INDEXADOR_CDI -> "$taxa% do CDI"
            INDEXADOR_SELIC -> "$taxa% da Selic"
            INDEXADOR_PREFIXADO -> "Prefixado $taxa% a.a."
            INDEXADOR_IPCA -> "IPCA + $taxa% a.a."
            else -> "Poupança"
        }
        listOfNotNull(rent, inv.vencimento.takeIf { it.isNotBlank() }?.let { "vence ${isoParaBr(it)}" }, "isento de IR".takeIf { inv.isento }).joinToString(" · ")
    }
}

@Composable
private fun CardInvestimento(
    calc: InvestimentoCalculado, nomeMeta: String?, c: CoresTela,
    onAporte: () -> Unit, onResgate: () -> Unit, onHistorico: () -> Unit, onEditar: () -> Unit, onExcluir: () -> Unit
) {
    val inv = calc.inv
    val tipo = tipoInvestimento(inv.tipo)
    var menu by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(18.dp), color = c.superficie, border = BorderStroke(1.dp, c.divisor), modifier = Modifier.fillMaxWidth().clickable { menu = true }) {
        Row(Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(tipo.cor.copy(alpha = 0.14f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(tipo.icone, null, tint = tipo.cor)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (inv.variavel && inv.ticker.isNotBlank()) "${inv.ticker} · ${inv.nome}" else inv.nome, fontWeight = FontWeight.Bold, color = c.texto, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detalheInvestimento(calc), fontSize = 12.sp, color = c.textoFraco, maxLines = 2, lineHeight = 16.sp)
                listOfNotNull(inv.instituicao.takeIf { it.isNotBlank() }, nomeMeta?.let { "meta: $it" }).takeIf { it.isNotEmpty() }?.let {
                    Text(it.joinToString(" · "), fontSize = 11.sp, color = c.textoFraco)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(moedaBR.format(calc.valorAtual), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.texto)
                Text(pct(calc.rendimentoPct), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (calc.rendimento >= 0) Verde else Vermelho)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Opções de ${inv.nome}", tint = c.textoFraco) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(if (inv.variavel) "Registrar compra" else "Aportar") }, leadingIcon = { Icon(Icons.Default.AddCircle, null, tint = Verde) }, onClick = { menu = false; onAporte() })
                    DropdownMenuItem(text = { Text(if (inv.variavel) "Registrar venda" else "Resgatar") }, leadingIcon = { Icon(Icons.Default.RemoveCircle, null, tint = Color(0xFFFB8C00)) }, onClick = { menu = false; onResgate() })
                    DropdownMenuItem(text = { Text("Histórico") }, leadingIcon = { Icon(Icons.Default.History, null, tint = c.destaque) }, onClick = { menu = false; onHistorico() })
                    DropdownMenuItem(text = { Text("Editar") }, leadingIcon = { Icon(Icons.Default.Edit, null, tint = c.destaque) }, onClick = { menu = false; onEditar() })
                    DropdownMenuItem(text = { Text("Excluir", color = Vermelho) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = Vermelho) }, onClick = { menu = false; onExcluir() })
                }
            }
        }
    }
}

// Campo de data no padrão brasileiro: digita só os números e as barras entram sozinhas
@Composable
fun CampoDataBR(valor: String, onValor: (String) -> Unit, rotulo: String, c: CoresTela, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = valor,
        onValueChange = { novo ->
            val d = novo.filter { it.isDigit() }.take(8)
            onValor(buildString { d.forEachIndexed { i, ch -> if (i == 2 || i == 4) append('/'); append(ch) } })
        },
        label = { Text(rotulo) }, placeholder = { Text("dd/mm/aaaa") }, singleLine = true,
        isError = valor.length == 10 && brParaIso(valor) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth(), shape = FormatoCampo, colors = coresCampoTela(c)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(opcoes: List<Pair<String, String>>, selecionado: String, c: CoresTela, onEscolher: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        opcoes.forEach { (chave, rotulo) ->
            FilterChip(
                selected = selecionado == chave, onClick = { onEscolher(chave) }, label = { Text(rotulo) }, shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.destaque.copy(alpha = 0.14f), selectedLabelColor = c.destaque)
            )
        }
    }
}

// Onde o dinheiro sai/entra: lançar no mês e, opcionalmente, em qual conta
@Composable
private fun OpcoesLancamento(entrada: Boolean, lancar: Boolean, onLancar: (Boolean) -> Unit, contas: List<ContaBancaria>, contaId: String, onConta: (String) -> Unit, c: CoresTela) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onLancar(!lancar) }) {
        Column(Modifier.weight(1f)) {
            Text(if (entrada) "Lançar como saída do mês" else "Lançar como entrada do mês", fontSize = 14.sp, color = c.texto)
            Text(if (entrada) "Entra nas despesas do mês, na categoria Investimentos" else "Soma na renda extra do mês", fontSize = 11.sp, color = c.textoFraco)
        }
        Switch(checked = lancar, onCheckedChange = onLancar, colors = SwitchDefaults.colors(checkedTrackColor = c.destaque))
    }
    if (lancar && contas.isNotEmpty()) {
        Text(if (entrada) "Saiu de qual conta? (opcional)" else "Entrou em qual conta? (opcional)", fontSize = 12.sp, color = c.textoFraco)
        Chips(listOf("" to "Nenhuma") + contas.map { it.id to it.nome }, contaId, c, onConta)
    }
}

@Composable
private fun DialogoInvestimento(
    inv: Investimento?, metas: List<Caixinha>, contas: List<ContaBancaria>, c: CoresTela,
    onFechar: () -> Unit, onSalvar: (Investimento, MovimentoInvestimento?, Boolean, String?) -> Unit
) {
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    var tipo by remember { mutableStateOf(inv?.tipo ?: "Renda fixa") }
    var nome by remember { mutableStateOf(inv?.nome ?: "") }
    var ticker by remember { mutableStateOf(inv?.ticker ?: "") }
    var instituicao by remember { mutableStateOf(inv?.instituicao ?: "") }
    var indexador by remember { mutableStateOf(inv?.indexador ?: INDEXADOR_CDI) }
    var taxa by remember { mutableStateOf(inv?.taxa?.paraCampo()?.removeSuffix(",00") ?: "100") }
    var vencimento by remember { mutableStateOf(inv?.vencimento?.takeIf { it.isNotBlank() }?.let { isoParaBr(it) } ?: "") }
    var isento by remember { mutableStateOf(inv?.isento ?: false) }
    var metaId by remember { mutableStateOf(inv?.caixinhaId ?: "") }
    // Primeiro aporte (só no cadastro)
    var valor by remember { mutableStateOf("") }
    var quantidade by remember { mutableStateOf("") }
    var preco by remember { mutableStateOf("") }
    var data by remember { mutableStateOf(isoParaBr(hojeIso())) }
    var lancar by remember { mutableStateOf(true) }
    var contaId by remember { mutableStateOf("") }
    var cotacaoInfo by remember { mutableStateOf<String?>(null) }
    val variavel = tipoInvestimento(tipo).variavel

    // Confere o ticker e já traz o nome e o preço do dia
    fun buscarTicker() {
        val t = ticker.trim().uppercase()
        if (t.length < 2) return
        cotacaoInfo = "Buscando $t..."
        escopo.launch {
            val cot = Mercado.cotacoes(listOf(t to (tipo == "Cripto")))[t]
            cotacaoInfo = if (cot == null) "Não encontrei \"$t\". Confira o código (ex.: PETR4, HGLG11, BOVA11, BTC)."
            else {
                if (nome.isBlank()) nome = cot.nome.take(40)
                if (preco.isBlank()) preco = cot.preco.paraCampo()
                "${cot.nome}: ${moedaBR.format(cot.preco)} hoje"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onFechar, containerColor = c.superficie, shape = RoundedCornerShape(28.dp),
        title = { Text(if (inv == null) "Novo investimento" else "Editar investimento", fontWeight = FontWeight.Bold, color = c.texto) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (inv == null) Chips(TiposInvestimento.map { it.nome to it.nome }, tipo, c) { tipo = it; cotacaoInfo = null }
                if (variavel) {
                    OutlinedTextField(
                        value = ticker, onValueChange = { ticker = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(12) },
                        label = { Text(if (tipo == "Cripto") "Moeda (ex.: BTC, ETH, SOL)" else "Código (ex.: PETR4, HGLG11)") }, singleLine = true,
                        trailingIcon = { IconButton(onClick = { buscarTicker() }) { Icon(Icons.Default.Search, "Buscar cotação", tint = c.destaque) } },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth(), shape = FormatoCampo, colors = coresCampoTela(c)
                    )
                    cotacaoInfo?.let { Text(it, fontSize = 12.sp, color = c.textoFraco) }
                }
                OutlinedTextField(
                    value = nome, onValueChange = { nome = it }, singleLine = true,
                    label = { Text(if (variavel) "Nome" else "Nome (ex.: CDB Banco Inter, Tesouro IPCA 2035)") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(), shape = FormatoCampo, colors = coresCampoTela(c)
                )
                OutlinedTextField(
                    value = instituicao, onValueChange = { instituicao = it }, singleLine = true, label = { Text("Banco ou corretora (opcional)") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(), shape = FormatoCampo, colors = coresCampoTela(c)
                )
                if (tipo == "Renda fixa" || tipo == "Tesouro Direto") {
                    Text("Rentabilidade", fontSize = 12.sp, color = c.textoFraco)
                    Chips(IndexadoresRendaFixa, indexador, c) { indexador = it; if (it == INDEXADOR_CDI || it == INDEXADOR_SELIC) { if ((taxa.paraValor() ?: 0.0) < 50) taxa = "100" } else if ((taxa.paraValor() ?: 0.0) > 50) taxa = "" }
                    OutlinedTextField(
                        value = taxa, onValueChange = { taxa = it.replace('.', ',') }, singleLine = true,
                        label = { Text(when (indexador) { INDEXADOR_CDI -> "% do CDI (ex.: 110)"; INDEXADOR_SELIC -> "% da Selic (ex.: 100)"; INDEXADOR_PREFIXADO -> "Taxa ao ano (ex.: 12,5)"; else -> "Taxa acima do IPCA ao ano (ex.: 6)" }) },
                        suffix = { Text("%") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(), shape = FormatoCampo, colors = coresCampoTela(c)
                    )
                    CampoDataBR(vencimento, { vencimento = it }, "Vencimento (opcional)", c)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { isento = !isento }) {
                        Column(Modifier.weight(1f)) {
                            Text("Isento de IR", fontSize = 14.sp, color = c.texto)
                            Text("LCI, LCA, CRI, CRA e debêntures incentivadas", fontSize = 11.sp, color = c.textoFraco)
                        }
                        Switch(checked = isento, onCheckedChange = { isento = it }, colors = SwitchDefaults.colors(checkedTrackColor = c.destaque))
                    }
                }
                if (metas.isNotEmpty()) {
                    Text("Ajuda a alcançar uma meta do Cofre? (opcional)", fontSize = 12.sp, color = c.textoFraco)
                    Chips(listOf("" to "Nenhuma") + metas.map { it.id to it.nome }, metaId, c) { metaId = it }
                }
                if (inv == null) {
                    HorizontalDivider(color = c.divisor, modifier = Modifier.padding(vertical = 4.dp))
                    Text(if (variavel) "Primeira compra" else "Valor aplicado", fontWeight = FontWeight.Bold, color = c.texto)
                    if (variavel) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = quantidade, onValueChange = { quantidade = it.replace('.', ',') }, label = { Text("Quantidade") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), shape = FormatoCampo, colors = coresCampoTela(c))
                            OutlinedTextField(value = preco, onValueChange = { preco = it.replace('.', ',') }, label = { Text("Preço pago") }, prefix = { Text("R$ ") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), shape = FormatoCampo, colors = coresCampoTela(c))
                        }
                        val total = (quantidade.paraValor() ?: 0.0) * (preco.paraValor() ?: 0.0)
                        if (total > 0) Text("Total: ${moedaBR.format(total)}", fontSize = 12.sp, color = c.textoFraco)
                    } else {
                        OutlinedTextField(value = valor, onValueChange = { valor = it.replace('.', ',') }, label = { Text("Valor") }, prefix = { Text("R$ ") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), shape = FormatoCampo, colors = coresCampoTela(c))
                    }
                    CampoDataBR(data, { data = it }, "Data da aplicação", c)
                    OpcoesLancamento(true, lancar, { lancar = it }, contas, contaId, { contaId = it }, c)
                    if (brParaIso(data)?.let { it < inicioDoMesIso() } == true && lancar)
                        Text("Aplicação de mês anterior: o lançamento vai para aquele mês.", fontSize = 11.sp, color = Color(0xFFFB8C00))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val iso = brParaIso(data)
                val vencIso = if (vencimento.isBlank()) "" else brParaIso(vencimento)
                val taxaNum = taxa.paraValor() ?: 0.0
                val erro = when {
                    variavel && ticker.length < 2 -> "Informe o código do ativo."
                    nome.isBlank() && !variavel -> "Dê um nome para o investimento."
                    (tipo == "Renda fixa" || tipo == "Tesouro Direto") && taxaNum <= 0 -> "Informe a rentabilidade."
                    vencIso == null -> "Vencimento inválido. Use dd/mm/aaaa."
                    inv == null && (iso == null || iso > hojeIso()) -> "Data da aplicação inválida. Use dd/mm/aaaa, até hoje."
                    inv == null && variavel && ((quantidade.paraValor() ?: 0.0) <= 0 || (preco.paraValor() ?: 0.0) <= 0) -> "Informe a quantidade e o preço."
                    inv == null && !variavel && (valor.paraValor() ?: 0.0) <= 0 -> "Informe o valor aplicado."
                    else -> null
                }
                if (erro != null) { Toast.makeText(context, erro, Toast.LENGTH_SHORT).show(); return@Button }
                val novo = Investimento(
                    nome = nome.ifBlank { ticker }, tipo = tipo, instituicao = instituicao,
                    indexador = if (tipo == "Poupança") INDEXADOR_POUPANCA else indexador,
                    taxa = if (variavel || tipo == "Poupança") 0.0 else taxaNum, vencimento = vencIso ?: "", isento = isento && !variavel,
                    ticker = if (variavel) ticker else "", caixinhaId = metaId
                )
                val primeiro = if (inv != null) null else MovimentoInvestimento(
                    UUID.randomUUID().toString(), iso!!,
                    valor = if (variavel) 0.0 else valor.paraValor()!!,
                    quantidade = if (variavel) quantidade.paraValor()!! else 0.0,
                    preco = if (variavel) preco.paraValor()!! else 0.0
                )
                onSalvar(novo, primeiro, lancar, contaId.ifBlank { null })
            }, colors = ButtonDefaults.buttonColors(containerColor = c.destaque)) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onFechar) { Text("Cancelar", color = c.textoFraco) } }
    )
}

@Composable
private fun DialogoMovimento(
    calc: InvestimentoCalculado, entrada: Boolean, contas: List<ContaBancaria>, c: CoresTela,
    onFechar: () -> Unit, onConfirmar: (MovimentoInvestimento, Boolean, String?) -> Unit
) {
    val context = LocalContext.current
    val inv = calc.inv
    var valor by remember { mutableStateOf("") }
    var quantidade by remember { mutableStateOf("") }
    var preco by remember { mutableStateOf(calc.cotacao?.preco?.paraCampo() ?: "") }
    var data by remember { mutableStateOf(isoParaBr(hojeIso())) }
    var lancar by remember { mutableStateOf(true) }
    var contaId by remember { mutableStateOf("") }
    val titulo = when {
        inv.variavel && entrada -> "Comprar ${inv.ticker}"
        inv.variavel -> "Vender ${inv.ticker}"
        entrada -> "Aportar em ${inv.nome}"
        else -> "Resgatar de ${inv.nome}"
    }
    AlertDialog(
        onDismissRequest = onFechar, containerColor = c.superficie, shape = RoundedCornerShape(28.dp),
        title = { Text(titulo, fontWeight = FontWeight.Bold, color = c.texto, maxLines = 2) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!entrada) Text(
                    if (inv.variavel) "Você tem ${numeroBR(calc.quantidade, if (calc.quantidade % 1.0 == 0.0) 0 else 6)} ${if (inv.tipo == "Cripto") "un." else "cotas"}."
                    else "Valor atual: ${moedaBR.format(calc.valorAtual)}.", fontSize = 13.sp, color = c.textoFraco
                )
                if (inv.variavel) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = quantidade, onValueChange = { quantidade = it.replace('.', ',') }, label = { Text("Quantidade") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), shape = FormatoCampo, colors = coresCampoTela(c))
                        OutlinedTextField(value = preco, onValueChange = { preco = it.replace('.', ',') }, label = { Text("Preço") }, prefix = { Text("R$ ") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), shape = FormatoCampo, colors = coresCampoTela(c))
                    }
                    val total = (quantidade.paraValor() ?: 0.0) * (preco.paraValor() ?: 0.0)
                    if (total > 0) Text("Total: ${moedaBR.format(total)}", fontSize = 12.sp, color = c.textoFraco)
                } else {
                    OutlinedTextField(value = valor, onValueChange = { valor = it.replace('.', ',') }, label = { Text("Valor") }, prefix = { Text("R$ ") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), shape = FormatoCampo, colors = coresCampoTela(c))
                }
                CampoDataBR(data, { data = it }, "Data", c)
                OpcoesLancamento(entrada, lancar, { lancar = it }, contas, contaId, { contaId = it }, c)
            }
        },
        confirmButton = {
            Button(onClick = {
                val iso = brParaIso(data)
                val q = quantidade.paraValor() ?: 0.0
                val p = preco.paraValor() ?: 0.0
                val v = valor.paraValor() ?: 0.0
                val erro = when {
                    iso == null || iso > hojeIso() -> "Data inválida. Use dd/mm/aaaa, até hoje."
                    inv.variavel && (q <= 0 || p <= 0) -> "Informe a quantidade e o preço."
                    inv.variavel && !entrada && q > calc.quantidade + 1e-9 -> "Você só tem ${numeroBR(calc.quantidade, 6)} para vender."
                    !inv.variavel && v <= 0 -> "Informe o valor."
                    !inv.variavel && !entrada && v > calc.valorAtual + 0.01 -> "O resgate passa do valor atual (${moedaBR.format(calc.valorAtual)})."
                    else -> null
                }
                if (erro != null) { Toast.makeText(context, erro, Toast.LENGTH_SHORT).show(); return@Button }
                // Resgate de renda fixa: sai o valor recebido; do aplicado, sai só a parte proporcional (sem o rendimento)
                val custoResgate = if (!inv.variavel && !entrada)
                    -(v * calc.investido / calc.valorAtual.coerceAtLeast(0.01)).coerceAtMost(calc.investido) else null
                val mov = MovimentoInvestimento(
                    UUID.randomUUID().toString(), iso!!,
                    valor = if (inv.variavel) 0.0 else if (entrada) v else -v,
                    quantidade = if (inv.variavel) (if (entrada) q else -q) else 0.0,
                    preco = if (inv.variavel) p else 0.0, custo = custoResgate
                )
                onConfirmar(mov, lancar, contaId.ifBlank { null })
            }, colors = ButtonDefaults.buttonColors(containerColor = c.destaque)) { Text("Confirmar") }
        },
        dismissButton = { TextButton(onClick = onFechar) { Text("Cancelar", color = c.textoFraco) } }
    )
}

@Composable
private fun DialogoHistorico(inv: Investimento, c: CoresTela, onFechar: () -> Unit, onRemover: (MovimentoInvestimento) -> Unit) {
    var paraRemover by remember { mutableStateOf<MovimentoInvestimento?>(null) }
    AlertDialog(
        onDismissRequest = onFechar, containerColor = c.superficie, shape = RoundedCornerShape(28.dp),
        title = { Text("Histórico de ${inv.nome}", fontWeight = FontWeight.Bold, color = c.texto, maxLines = 2) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (inv.movimentos.isEmpty()) Text("Nenhum movimento.", color = c.textoFraco)
                inv.movimentos.sortedByDescending { it.data }.forEach { m ->
                    val entrada = if (inv.variavel) m.quantidade > 0 else m.valor > 0
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (inv.variavel) "${if (entrada) "Compra" else "Venda"} · ${numeroBR(kotlin.math.abs(m.quantidade), if (m.quantidade % 1.0 == 0.0) 0 else 6)} × ${moedaBR.format(m.preco)}"
                                else if (entrada) "Aporte" else "Resgate",
                                fontSize = 14.sp, color = c.texto
                            )
                            Text(isoParaBr(m.data), fontSize = 12.sp, color = c.textoFraco)
                        }
                        Text(
                            moedaBR.format(if (inv.variavel) kotlin.math.abs(m.quantidade * m.preco) else kotlin.math.abs(m.valor)),
                            fontWeight = FontWeight.SemiBold, color = if (entrada) Verde else Color(0xFFFB8C00)
                        )
                        IconButton(onClick = { paraRemover = m }) { Icon(Icons.Default.Close, "Remover movimento", tint = c.textoFraco, modifier = Modifier.size(18.dp)) }
                    }
                    HorizontalDivider(color = c.divisor)
                }
            }
        },
        confirmButton = { TextButton(onClick = onFechar) { Text("Fechar", color = c.destaque) } }
    )
    paraRemover?.let { m ->
        AlertDialog(
            onDismissRequest = { paraRemover = null }, containerColor = c.superficie,
            title = { Text("Remover este movimento?", color = c.texto) },
            text = { Text("Use para corrigir um registro errado. O lançamento do mês feito junto não é apagado; se precisar, apague-o no Dashboard.", color = c.textoFraco) },
            confirmButton = { Button(onClick = { onRemover(m); paraRemover = null; onFechar() }, colors = ButtonDefaults.buttonColors(containerColor = Vermelho)) { Text("Remover") } },
            dismissButton = { TextButton(onClick = { paraRemover = null }) { Text("Cancelar", color = c.textoFraco) } }
        )
    }
}

// O que o mercado espera (Boletim Focus do Banco Central): IPCA e Selic de fim de ano
@Composable
private fun CardExpectativasFocus(focus: List<ExpectativaFocus>, c: CoresTela) {
    Surface(shape = RoundedCornerShape(20.dp), color = c.superficie, border = BorderStroke(1.dp, c.divisor), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text("O que o mercado espera", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.texto)
            Spacer(Modifier.height(10.dp))
            listOf("IPCA" to "Inflação (IPCA) no ano", "Selic" to "Selic no fim do ano").forEach { (indicador, titulo) ->
                val valores = focus.filter { it.indicador == indicador }
                if (valores.isEmpty()) return@forEach
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(titulo, fontSize = 13.sp, color = c.texto, modifier = Modifier.weight(1f))
                    valores.forEach { v ->
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 16.dp)) {
                            Text("${v.ano}", fontSize = 11.sp, color = c.textoFraco)
                            Text(String.format(Locale("pt", "BR"), "%.2f%%", v.mediana), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.texto)
                        }
                    }
                }
            }
            Text("Boletim Focus de ${isoParaBr(focus.first().dataPesquisa)} · mediana das projeções do mercado (Banco Central)", fontSize = 11.sp, color = c.textoFraco, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

// Card do Dashboard: patrimônio e quanto rendeu no mês (só aparece com investimentos cadastrados)
@Composable
fun CardPatrimonioDashboard(estado: EstadoInvestimentos, corSuperficie: Color, corDivisor: Color, corTexto: Color, corTextoFraco: Color, corDestaque: Color, onAbrir: () -> Unit) {
    if (estado.itens.isEmpty()) return
    Surface(
        shape = RoundedCornerShape(20.dp), color = corSuperficie, border = BorderStroke(1.dp, corDivisor),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable(onClick = onAbrir)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(corDestaque.copy(alpha = 0.12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = corDestaque)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Patrimônio investido", fontSize = 12.sp, color = corTextoFraco)
                Text(moedaBR.format(estado.total), fontSize = 20.sp, fontWeight = FontWeight.Black, color = corTexto)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("No mês", fontSize = 11.sp, color = corTextoFraco)
                Text(
                    (if (estado.rendeuNoMes >= 0) "+" else "") + moedaBR.format(estado.rendeuNoMes),
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (estado.rendeuNoMes >= 0) Verde else Vermelho
                )
                Text("${pct(estado.rendimentoPct)} no total", fontSize = 11.sp, color = corTextoFraco)
            }
        }
    }
}
