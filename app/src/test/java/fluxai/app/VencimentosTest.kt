package fluxai.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class VencimentosTest {
    private val hoje = Calendar.getInstance().apply { clear(); set(2026, Calendar.SEPTEMBER, 29, 8, 0) }
    private fun d(desc: String, dia: Int, mes: String = "09/2026", status: String = "A pagar", valor: Double = 10.0) =
        Despesa(id = desc, descricao = desc, valor = valor, diaVencimento = dia, mesAno = mes, status = status)

    @Test
    fun separaVencidasHojeEProximosDias() {
        val contas = contasAVencer(
            listOf(d("Luz", 29), d("Água", 27), d("Internet", 30), d("Aluguel", 1, "10/2026"), d("Escola", 5, "10/2026"), d("Paga", 28, status = "Pago")),
            hoje
        )
        assertEquals(listOf("Água" to -2, "Luz" to 0, "Internet" to 1, "Aluguel" to 2), contas.map { it.despesa.descricao to it.dias })
    }

    @Test
    fun dia31EmMesCurtoViraUltimoDia() {
        val contas = contasAVencer(listOf(d("Cartão", 31)), hoje)
        assertEquals(1, contas.single().dias) // setembro tem 30 dias
    }

    @Test
    fun textoDoPrazo() {
        assertEquals("vence hoje", quandoVence(0))
        assertEquals("vence amanhã", quandoVence(1))
        assertEquals("venceu há 3 dias", quandoVence(-3))
    }
}
