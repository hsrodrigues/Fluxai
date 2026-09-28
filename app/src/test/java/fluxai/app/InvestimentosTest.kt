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
    fun poupancaUsaARentabilidadeOficialDeCadaAniversario() = runBlocking {
        // Depósito em 10/01: multiplica os índices oficiais (série 195) de 10/01, 10/02, ... até o último aniversário
        val serie = Mercado.lerJsonBcb("https://api.bcb.gov.br/dados/serie/bcdata.sgs.195/dados?formato=json&dataInicial=10/01/2026&dataFinal=10/08/2026")
        val porData = (0 until serie.length()).map { serie.getJSONObject(it) }.associate { brParaIso(it.getString("data")) to it.getString("valor").toDouble() }
        val esperado = (1..7).fold(1.0) { f, m -> f * (1 + porData.getValue("2026-%02d-10".format(m)) / 100) }
        assertEquals(esperado, Mercado.fatorRendaFixa(INDEXADOR_POUPANCA, 0.0, "2026-01-10", "2026-08-10"), 1e-9)
        // Um dia antes do aniversário ainda não rendeu o 7º mês
        val seis = (1..6).fold(1.0) { f, m -> f * (1 + porData.getValue("2026-%02d-10".format(m)) / 100) }
        assertEquals(seis, Mercado.fatorRendaFixa(INDEXADOR_POUPANCA, 0.0, "2026-01-10", "2026-08-09"), 1e-9)
    }

    @Test
    fun jurosDeEmprestimoEConversaoDeTaxas() {
        // R$ 1.000 em 12 parcelas de R$ 100 -> cerca de 2,92% ao mês
        assertEquals(2.9229, taxaMensalEmprestimo(1000.0, 100.0, 12)!!, 0.001)
        assertNull(taxaMensalEmprestimo(1000.0, 80.0, 12)) // parcelas somam menos que o recebido
        assertEquals(12.6825, mensalParaAnual(1.0), 0.001)
        assertEquals(1.0, anualParaMensal(mensalParaAnual(1.0)), 1e-9)
        assertEquals("Santander (Brasil)", nomeBanco("BCO SANTANDER (BRASIL) S.A."))
        assertEquals("Caixa Economica Federal", nomeBanco("CAIXA ECONOMICA FEDERAL"))
    }

    @Test
    fun dadosAbertosDoBancoCentralRespondem() = runBlocking {
        val focus = BancoCentral.expectativas()!!
        assert(focus.any { it.indicador == "IPCA" } && focus.any { it.indicador == "Selic" })
        val rotativo = BancoCentral.jurosMedio(modalidadeCredito("Cartão rotativo"))!!
        assert(rotativo.second > 50)
        val ranking = BancoCentral.ranking(modalidadeCredito("Crédito pessoal"))!!
        assert(ranking.bancos.size > 10)
        assertEquals(1, ranking.bancos.first().posicao)
        assert(ranking.bancos.zipWithNext().all { (a, b) -> a.taxaMes <= b.taxaMes + 1e-9 })
        val ptax = BancoCentral.dolarPtax()!!
        assert(ptax.venda in 3.0..10.0)
    }

    @Test
    fun evolucaoComecaNaDataDaPrimeiraAplicacao() = runBlocking {
        // Aplicação feita neste mês: o gráfico começa no dia da aplicação e termina hoje
        val dia = inicioDoMesIso()
        val inv = Investimento(id = "y", nome = "CDB", tipo = "Renda fixa", indexador = INDEXADOR_CDI, taxa = 100.0,
            movimentos = listOf(MovimentoInvestimento("a", dia, valor = 500.0)))
        val atual = EstadoInvestimentos(carregando = false, itens = calcularCarteira(listOf(inv)).first)
        val pontos = evolucaoCarteira(listOf(inv), atual)
        if (dia < hojeIso()) {
            assertEquals(2, pontos.size)
            assertEquals(isoParaBr(dia).substring(0, 5), pontos.first().rotulo)
            assertEquals(500.0, pontos.first().patrimonio, 1e-9)
        }
    }

    @Test
    fun evolucaoMesAMesDaRendaFixa() = runBlocking {
        val inv = Investimento(id = "x", nome = "CDB", tipo = "Renda fixa", indexador = INDEXADOR_CDI, taxa = 100.0,
            movimentos = listOf(MovimentoInvestimento("a", "2026-01-02", valor = 1000.0)))
        val atual = EstadoInvestimentos(carregando = false, itens = calcularCarteira(listOf(inv)).first)
        val pontos = evolucaoCarteira(listOf(inv), atual)
        // Começa no dia da primeira aplicação, segue pelos fins de mês e termina em "hoje"
        assertEquals("02/01", pontos.first().rotulo)
        assertEquals(1000.0, pontos.first().patrimonio, 1e-9)
        assertEquals("jan/26", pontos[1].rotulo)
        assert(pontos.last().hoje)
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
