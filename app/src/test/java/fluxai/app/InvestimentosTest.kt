package fluxai.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.pow

class InvestimentosTest {

    @Test
    fun datasNoPadraoBrasileiro() {
        assertEquals("2026-09-28", brParaIso("28/09/2026"))
        assertEquals("28/09/2026", isoParaBr("2026-09-28"))
        assertNull(brParaIso("31/13/2026"))
        assertNull(brParaIso("2026-09-28"))
        assertNull(brParaIso("28/09"))
        assertEquals(365L, diaSequencial("2027-01-01") - diaSequencial("2026-01-01"))
        assertEquals(29L, diaSequencial("2024-03-01") - diaSequencial("2024-02-01"))
    }

    @Test
    fun aliquotaDoImpostoRegressivo() {
        assertEquals(0.225, aliquotaIR(180), 0.0)
        assertEquals(0.20, aliquotaIR(181), 0.0)
        assertEquals(0.175, aliquotaIR(720), 0.0)
        assertEquals(0.15, aliquotaIR(721), 0.0)
    }

    private fun mov(data: String, q: Double, p: Double) = MovimentoInvestimento(data, data, quantidade = q, preco = p)

    @Test
    fun precoMedioComComprasEVendas() {
        // 10 a R$ 20 + 10 a R$ 30 -> PM 25; vende 5 -> PM continua 25
        val (qtd, pm) = posicaoVariavel(listOf(mov("2026-01-10", 10.0, 20.0), mov("2026-02-10", 10.0, 30.0), mov("2026-03-10", -5.0, 40.0)))
        assertEquals(15.0, qtd, 1e-9)
        assertEquals(25.0, pm, 1e-9)
        // Vende tudo e recompra: o PM recomeça
        val (q2, pm2) = posicaoVariavel(listOf(mov("2026-01-10", 10.0, 20.0), mov("2026-02-10", -10.0, 30.0), mov("2026-03-10", 4.0, 50.0)))
        assertEquals(4.0, q2, 1e-9)
        assertEquals(50.0, pm2, 1e-9)
        // Fora de ordem: ordena pela data
        val (q3, pm3) = posicaoVariavel(listOf(mov("2026-02-10", 10.0, 30.0), mov("2026-01-10", 10.0, 20.0)))
        assertEquals(20.0, q3, 1e-9); assertEquals(25.0, pm3, 1e-9)
    }

    private fun serieMensalBcb(serie: Int, de: String, ate: String): List<Double> {
        val arr = Mercado.lerJsonBcb("https://api.bcb.gov.br/dados/serie/bcdata.sgs.$serie/dados?formato=json&dataInicial=$de&dataFinal=$ate")
        return (0 until arr.length()).map { arr.getJSONObject(it).getString("valor").toDouble() }
    }

    // Compara com o CDI acumulado de cada mês publicado pelo Banco Central (série 4391)
    @Test
    fun cdiAcumuladoBateComOBancoCentral() = runBlocking {
        val mensal = serieMensalBcb(4391, "01/01/2026", "31/08/2026")
        val esperado = mensal.fold(1.0) { f, m -> f * (1 + m / 100) }
        val calculado = Mercado.fatorRendaFixa(INDEXADOR_CDI, 100.0, "2025-12-31", "2026-08-31")
        assertEquals(esperado, calculado, 0.0005)
        // 110% do CDI rende mais que 100%
        val cdi110 = Mercado.fatorRendaFixa(INDEXADOR_CDI, 110.0, "2025-12-31", "2026-08-31")
        assert(cdi110 > calculado)
    }

    @Test
    fun ipcaDeMesesInteirosBateComOBancoCentral() = runBlocking {
        val mensal = serieMensalBcb(433, "01/01/2026", "31/08/2026")
        val esperado = mensal.fold(1.0) { f, m -> f * (1 + m / 100) }
        // IPCA + 0%: só a inflação de janeiro a agosto
        val calculado = Mercado.fatorRendaFixa(INDEXADOR_IPCA, 0.0, "2026-01-01", "2026-09-01")
        assertEquals(esperado, calculado, 0.0001)
    }

    @Test
    fun prefixadoPorDiasUteis() = runBlocking {
        val f = Mercado.fatorRendaFixa(INDEXADOR_PREFIXADO, 12.0, "2025-12-31", "2026-08-31")
        val du = serieMensalBcb(12, "01/01/2026", "31/08/2026").size
        assertEquals(1.12.pow(du / 252.0), f, 1e-9)
    }

    @Test
    fun poupancaRendeSoNoAniversario() = runBlocking {
        // Selic acima de 8,5%: 0,5% ao mês. De 10/01 a 09/09 são 7 aniversários completos.
        assertEquals(1.005.pow(7), Mercado.fatorRendaFixa(INDEXADOR_POUPANCA, 0.0, "2026-01-10", "2026-09-09"), 1e-9)
        assertEquals(1.005.pow(8), Mercado.fatorRendaFixa(INDEXADOR_POUPANCA, 0.0, "2026-01-10", "2026-09-10"), 1e-9)
    }

    @Test
    fun evolucaoMesAMesDaRendaFixa() = runBlocking {
        val inv = Investimento(id = "x", nome = "CDB", tipo = "Renda fixa", indexador = INDEXADOR_CDI, taxa = 100.0,
            movimentos = listOf(MovimentoInvestimento("a", "2026-01-02", valor = 1000.0)))
        val atual = EstadoInvestimentos(carregando = false, itens = calcularCarteira(listOf(inv)).first)
        val pontos = evolucaoCarteira(listOf(inv), atual)
        // Começa no mês do primeiro aporte e termina em "hoje"
        assertEquals("2026-01", pontos.first().mes)
        assert(pontos.last().hoje)
        assertEquals("jan/26", pontos.first().rotulo)
        // Aplicado constante; patrimônio só cresce e bate com o fator do CDI no fim de cada mês
        pontos.forEach { assertEquals(1000.0, it.aplicado, 1e-9) }
        pontos.zipWithNext().forEach { (a, b) -> assert(b.patrimonio >= a.patrimonio) }
        val fimJunho = 1000 * Mercado.fatorRendaFixa(INDEXADOR_CDI, 100.0, "2026-01-02", "2026-06-30")
        assertEquals(fimJunho, pontos.first { it.mes == "2026-06" }.patrimonio, 1e-6)
        assertEquals(atual.total, pontos.last().patrimonio, 1e-9)
    }

    @Test
    fun carteiraDeRendaFixaComResgate() = runBlocking {
        // Aplica 1000, resgata 500 depois: o valor atual cai e o aplicado cai só pelo custo proporcional
        val inv = Investimento(id = "x", nome = "CDB", tipo = "Renda fixa", indexador = INDEXADOR_CDI, taxa = 100.0, movimentos = listOf(
            MovimentoInvestimento("a", "2026-01-02", valor = 1000.0),
            MovimentoInvestimento("b", "2026-06-01", valor = -500.0, custo = -470.0)
        ))
        val (itens, falhou) = calcularCarteira(listOf(inv))
        assert(!falhou)
        val c = itens.single()
        assertEquals(530.0, c.investido, 1e-9)
        val esperado = 1000 * Mercado.fatorRendaFixa(INDEXADOR_CDI, 100.0, "2026-01-02", hojeIso()) - 500 * Mercado.fatorRendaFixa(INDEXADOR_CDI, 100.0, "2026-06-01", hojeIso())
        assertEquals(esperado, c.valorAtual, 1e-6)
        assert(c.liquidoEstimado < c.valorAtual) // tem IR sobre o lucro
    }
}
