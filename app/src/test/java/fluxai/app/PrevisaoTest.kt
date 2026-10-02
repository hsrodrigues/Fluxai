package fluxai.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

class PrevisaoTest {
    // Outubro de 2026 tem 31 dias
    private fun dia(d: Int): Calendar = GregorianCalendar(2026, Calendar.OCTOBER, d)

    private fun variavel(valor: Double, status: String = "Pago", projeto: String? = null) =
        Despesa(descricao = "Gasto", valor = valor, tipo = "Variável", status = status, projetoId = projeto)

    private fun conta(desc: String, valor: Double, venc: Int, status: String = "A pagar") =
        Despesa(descricao = desc, valor = valor, tipo = "Fixa", status = status, diaVencimento = venc)

    @Test
    fun noDia2NaoExtrapolaOGastoParaOMesInteiro() {
        // Bug real: R$ 1.640,74 em 2 dias virava R$ 820/dia x 29 dias = -R$ 24 mil
        val p = calcularPrevisao(listOf(variavel(1640.74)), sobraTotal = -720.78, totalRenda = 1889.42, hoje = dia(2))
        assertEquals(-720.78, p.sobraProjetada, 0.001)
        assertEquals(0.0, p.ritmoDiario, 0.001)
        assertFalse(p.projecaoConfiavel)
        assertEquals(NivelPrevisao.RISCO, p.nivel)
        assertEquals("Orçamento estourado", p.titulo)
    }

    @Test
    fun noInicioDoMesComSobraMostraSoOLimiteDiario() {
        val p = calcularPrevisao(listOf(variavel(900.0)), sobraTotal = 3100.0, totalRenda = 5000.0, hoje = dia(2))
        assertEquals(NivelPrevisao.INFO, p.nivel)
        assertEquals("Início do mês", p.titulo)
        assertEquals(3100.0, p.sobraProjetada, 0.001)
        assertEquals(3100.0 / 29, p.limiteDiario, 0.001)
        assertNull(p.diaZera)
    }

    @Test
    fun aPartirDoDia5AProjecaoValeEOMesProjetaORitmo() {
        val p = calcularPrevisao(listOf(variavel(500.0)), sobraTotal = 5000.0, totalRenda = 6000.0, hoje = dia(5))
        assertTrue(p.projecaoConfiavel)
        assertEquals(100.0, p.ritmoDiario, 0.001)
        assertEquals(5000.0 - 100.0 * 26, p.sobraProjetada, 0.001)
    }

    @Test
    fun ritmoSaudavelNoMeioDoMes() {
        val p = calcularPrevisao(listOf(variavel(1000.0)), sobraTotal = 5000.0, totalRenda = 6000.0, hoje = dia(10))
        assertEquals(100.0, p.ritmoDiario, 0.001)
        assertEquals(2900.0, p.sobraProjetada, 0.001)
        assertEquals(NivelPrevisao.OK, p.nivel)
        assertNull(p.diaZera)
    }

    @Test
    fun avisaQualDiaASobraZera() {
        val p = calcularPrevisao(listOf(variavel(1000.0)), sobraTotal = 1000.0, totalRenda = 6000.0, hoje = dia(10))
        assertEquals(NivelPrevisao.RISCO, p.nivel)
        assertEquals(20, p.diaZera)
        assertEquals(-1100.0, p.sobraProjetada, 0.001)
        assertEquals("A sobra zera no dia 20", p.titulo)
    }

    @Test
    fun margemApertadaQuandoSobraProjetadaEMenorQue10PorCentoDaRenda() {
        val p = calcularPrevisao(listOf(variavel(1000.0)), sobraTotal = 2200.0, totalRenda = 6000.0, hoje = dia(10))
        assertEquals(100.0, p.sobraProjetada, 0.001)
        assertEquals(NivelPrevisao.ATENCAO, p.nivel)
    }

    @Test
    fun semGastosVariaveisInformaOLimiteDiario() {
        val p = calcularPrevisao(listOf(conta("Aluguel", 1000.0, 5)), sobraTotal = 2100.0, totalRenda = 6000.0, hoje = dia(10))
        assertEquals(NivelPrevisao.INFO, p.nivel)
        assertEquals("Sem gastos variáveis ainda", p.titulo)
        assertEquals(100.0, p.limiteDiario, 0.001)
    }

    @Test
    fun orcamentoEstouradoNaoMostraLimiteDiario() {
        val p = calcularPrevisao(listOf(variavel(500.0)), sobraTotal = -50.0, totalRenda = 1000.0, hoje = dia(10))
        assertEquals(NivelPrevisao.RISCO, p.nivel)
        assertEquals(0.0, p.limiteDiario, 0.001)
        assertNull(p.diaZera)
    }

    @Test
    fun ignoraProjetosEContasAdiadasNoRitmo() {
        val despesas = listOf(variavel(1000.0), variavel(5000.0, projeto = "viagem"), variavel(3000.0, status = "Próximo Mês"))
        val p = calcularPrevisao(despesas, sobraTotal = 5000.0, totalRenda = 6000.0, hoje = dia(10))
        assertEquals(100.0, p.ritmoDiario, 0.001)
    }

    @Test
    fun reservaDasMetasSaiDaSobra() {
        val p = calcularPrevisao(listOf(variavel(1000.0)), sobraTotal = 5000.0, totalRenda = 6000.0, hoje = dia(10), reservaMetas = 1000.0)
        assertEquals(4000.0 - 2100.0, p.sobraProjetada, 0.001)
        assertEquals(4000.0 / 21, p.limiteDiario, 0.001)
        assertEquals(1000.0, p.reservaMetas, 0.001)
    }

    @Test
    fun noUltimoDiaNaoDividePorZero() {
        val p = calcularPrevisao(listOf(variavel(3100.0)), sobraTotal = 500.0, totalRenda = 6000.0, hoje = dia(31))
        assertEquals(0.0, p.limiteDiario, 0.001)
        assertEquals(500.0, p.sobraProjetada, 0.001)
        assertNull(p.diaZera)
    }

    @Test
    fun separaContasVencidasEAsDosProximos3Dias() {
        val despesas = listOf(
            conta("Luz", 100.0, 8),
            conta("Internet", 120.0, 10),
            conta("Água", 80.0, 13),
            conta("Aluguel", 1000.0, 20),
            conta("Escola", 500.0, 3, status = "Pago")
        )
        val p = calcularPrevisao(despesas, sobraTotal = 3000.0, totalRenda = 6000.0, hoje = dia(10))
        assertEquals(listOf("Luz"), p.contasVencidas.map { it.descricao })
        assertEquals(listOf("Internet", "Água"), p.contasProximas.map { it.descricao })
    }
}
