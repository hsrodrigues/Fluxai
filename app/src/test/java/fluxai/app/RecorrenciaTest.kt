package fluxai.app

import org.junit.Assert.assertEquals
import org.junit.Test

class RecorrenciaTest {
    private fun d(desc: String, tipo: String = "Fixa", status: String = "Pago", freq: String = "Mensal", projeto: String? = null) =
        Despesa(id = desc, descricao = desc, valor = 10.0, tipo = tipo, status = status, frequencia = freq, projetoId = projeto)

    @Test
    fun mesAnteriorViraOAno() {
        assertEquals("12/2025", mesAnterior("01/2026"))
        assertEquals("08/2026", mesAnterior("09/2026"))
    }

    @Test
    fun sugereFixasEAdiadas() {
        val anteriores = listOf(
            d("Aluguel"),
            d("Mercado", tipo = "Variável"),
            d("Consulta", tipo = "Variável", status = "Próximo Mês"),
            d("TV (3/10)"),
            d("Viagem hotel", projeto = "p1"),
            d("IPVA", freq = "Única")
        )
        val sugeridas = candidatasRecorrencia(anteriores, emptyList()).map { it.descricao }
        assertEquals(listOf("Aluguel", "Consulta"), sugeridas)
    }

    @Test
    fun naoRepeteOQueJaExisteNoMes() {
        val anteriores = listOf(d("Aluguel"), d("Internet"))
        val atuais = listOf(d("aluguel "))
        assertEquals(listOf("Internet"), candidatasRecorrencia(anteriores, atuais).map { it.descricao })
    }
}
