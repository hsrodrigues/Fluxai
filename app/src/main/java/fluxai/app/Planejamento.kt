package fluxai.app

import kotlin.math.min

// =========================================================================
// CÁLCULOS DE PLANEJAMENTO
// Alertas de orçamento por categoria, aporte mensal das metas e acerto de contas
// da conta conjunta. Funções puras, sem Firebase, para poderem ser testadas.
// =========================================================================

// ---------- Orçamento por categoria (limites definidos na Análise BI) ----------
data class AlertaOrcamento(val categoria: String, val gasto: Double, val limite: Double) {
    val uso: Double get() = if (limite > 0) gasto / limite else 0.0
    val estourou: Boolean get() = gasto >= limite
}

// Mesmo recorte da Análise BI: custo de vida, sem projetos e sem transferências para metas
fun gastoPorCategoria(despesas: List<Despesa>): Map<String, Double> =
    despesas.filter { it.projetoId == null && !it.descricao.startsWith("Apontamento:") }
        .groupBy { it.categoria }.mapValues { (_, l) -> l.sumOf { it.valor } }

fun alertasOrcamento(despesas: List<Despesa>, limites: Map<String, Double>, limiar: Double = 0.8): List<AlertaOrcamento> {
    val gastos = gastoPorCategoria(despesas)
    return limites.filter { it.value > 0 }
        .map { (cat, limite) -> AlertaOrcamento(cat, gastos[cat] ?: 0.0, limite) }
        .filter { it.uso >= limiar }
        .sortedByDescending { it.uso }
}

// ---------- Metas com prazo ----------
// Meses que ainda restam até o prazo, contando o mês atual (prazo no mês atual = 1)
fun mesesAtePrazo(prazo: String, mesAtual: String): Int {
    val (mp, ap) = prazo.split("/").map { it.toIntOrNull() ?: return 0 }
    val (ma, aa) = mesAtual.split("/").map { it.toIntOrNull() ?: return 0 }
    return (ap * 12 + mp) - (aa * 12 + ma) + 1
}

// Quanto guardar neste mês para chegar à meta no prazo.
// guardadoNoMes já está dentro do saldo; o plano do mês é calculado como se fosse o início do mês.
fun aporteDoMes(cx: Caixinha, mesAtual: String, guardadoNoMes: Double = 0.0): Double {
    if (cx.prazo.isBlank() || cx.meta <= 0) return 0.0
    val faltaNoInicio = cx.meta - (cx.saldo - guardadoNoMes)
    if (faltaNoInicio <= 0) return 0.0
    val meses = mesesAtePrazo(cx.prazo, mesAtual)
    return if (meses <= 1) faltaNoInicio else faltaNoInicio / meses
}

// Quanto ainda falta reservar neste mês para as metas com prazo.
// Conta como guardado o que foi para a meta e os aportes em investimentos ligados a ela.
fun reservaPendenteMetas(caixinhas: List<Caixinha>, despesasDoMes: List<Despesa>, mesAtual: String): Double =
    caixinhas.sumOf { cx ->
        val guardado = despesasDoMes.filter { it.descricao == "Apontamento: ${cx.nome}" || it.caixinhaId == cx.id }.sumOf { it.valor }
        (aporteDoMes(cx, mesAtual, guardado) - guardado).coerceAtLeast(0.0)
    }

// ---------- Acerto de contas da conta conjunta ----------
data class Participante(val uid: String, val nome: String, val pagou: Double)
data class Transferencia(val de: String, val para: String, val valor: Double)
data class AcertoContas(val participantes: List<Participante>, val total: Double, val porPessoa: Double, val transferencias: List<Transferencia>)

// Divide igualmente o que foi pago no mês entre quem pagou alguma conta (e o usuário atual).
// Devolve null quando só uma pessoa pagou: não há o que acertar.
fun calcularAcerto(despesas: List<Despesa>, uidAtual: String, nomeAtual: String): AcertoContas? {
    val pagas = despesas.filter { it.status == "Pago" && it.projetoId == null && !it.pagoPor.isNullOrBlank() }
    if (pagas.isEmpty()) return null
    val nomes = pagas.associate { it.pagoPor!! to (it.pagoPorNome?.ifBlank { null } ?: "Parceiro(a)") } + (uidAtual to nomeAtual)
    val porUid = pagas.groupBy { it.pagoPor!! }.mapValues { (_, l) -> l.sumOf { it.valor } }
    val participantes = nomes.map { (uid, nome) -> Participante(uid, nome, porUid[uid] ?: 0.0) }.sortedByDescending { it.pagou }
    if (participantes.size < 2) return null

    val total = participantes.sumOf { it.pagou }
    val cota = total / participantes.size
    // Quem pagou menos que a cota transfere para quem pagou mais (maiores saldos primeiro)
    val devedores = participantes.map { it.nome to cota - it.pagou }.filter { it.second > 0.005 }.sortedByDescending { it.second }.toMutableList()
    val credores = participantes.map { it.nome to it.pagou - cota }.filter { it.second > 0.005 }.sortedByDescending { it.second }.toMutableList()
    val transferencias = mutableListOf<Transferencia>()
    var i = 0; var j = 0
    val restDev = devedores.map { it.second }.toMutableList()
    val restCred = credores.map { it.second }.toMutableList()
    while (i < devedores.size && j < credores.size) {
        val v = min(restDev[i], restCred[j])
        transferencias += Transferencia(devedores[i].first, credores[j].first, Math.round(v * 100) / 100.0)
        restDev[i] -= v; restCred[j] -= v
        if (restDev[i] < 0.005) i++
        if (restCred[j] < 0.005) j++
    }
    return AcertoContas(participantes, total, cota, transferencias)
}
