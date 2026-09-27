package fluxai.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanejamentoTest {
    private fun d(desc: String, valor: Double, cat: String = "Outros", status: String = "Pago", pagoPor: String? = null, nome: String? = null, projeto: String? = null) =
        Despesa(id = desc, descricao = desc, valor = valor, categoria = cat, status = status, pagoPor = pagoPor, pagoPorNome = nome, projetoId = projeto)

    @Test
    fun alertaQuandoPassaDe80PorCento() {
        val despesas = listOf(d("Mercado", 850.0, "Alimentação"), d("Uber", 100.0, "Transporte"), d("Viagem", 999.0, "Lazer", projeto = "p1"))
        val alertas = alertasOrcamento(despesas, mapOf("Alimentação" to 1000.0, "Transporte" to 500.0, "Lazer" to 100.0))
        assertEquals(listOf("Alimentação"), alertas.map { it.categoria })
        assertEquals(0.85, alertas[0].uso, 0.001)
    }

    @Test
    fun transferenciaParaMetaNaoContaNoOrcamento() {
        val despesas = listOf(d("Apontamento: Viagem", 900.0, "Outros"))
        assertEquals(emptyList<AlertaOrcamento>(), alertasOrcamento(despesas, mapOf("Outros" to 100.0)))
    }

    @Test
    fun mesesAtePrazoContaOMesAtual() {
        assertEquals(1, mesesAtePrazo("09/2026", "09/2026"))
        assertEquals(4, mesesAtePrazo("12/2026", "09/2026"))
        assertEquals(5, mesesAtePrazo("01/2027", "09/2026"))
        assertEquals(0, mesesAtePrazo("08/2026", "09/2026"))
    }

    @Test
    fun aporteDivideOQueFaltaPelosMeses() {
        val cx = Caixinha(nome = "Viagem", meta = 5000.0, saldo = 1000.0, prazo = "12/2026")
        assertEquals(1000.0, aporteDoMes(cx, "09/2026"), 0.001)
        // Guardar 400 no mês não muda o plano do mês
        assertEquals(1000.0, aporteDoMes(cx.copy(saldo = 1400.0), "09/2026", guardadoNoMes = 400.0), 0.001)
        // Prazo vencido: falta tudo agora
        assertEquals(4000.0, aporteDoMes(cx, "01/2027"), 0.001)
        assertEquals(0.0, aporteDoMes(cx.copy(prazo = ""), "09/2026"), 0.001)
    }

    @Test
    fun reservaPendenteDescontaOQueJaFoiGuardado() {
        val cx = Caixinha(nome = "Viagem", meta = 5000.0, saldo = 1400.0, prazo = "12/2026")
        val doMes = listOf(d("Apontamento: Viagem", 400.0))
        assertEquals(600.0, reservaPendenteMetas(listOf(cx), doMes, "09/2026"), 0.001)
    }

    @Test
    fun acertoDivideIgualmente() {
        val despesas = listOf(
            d("Aluguel", 1500.0, pagoPor = "a", nome = "Ana"),
            d("Mercado", 500.0, pagoPor = "b", nome = "Bruno"),
            d("Luz", 200.0, status = "A pagar", pagoPor = "b", nome = "Bruno")
        )
        val acerto = calcularAcerto(despesas, "b", "Bruno")!!
        assertEquals(2000.0, acerto.total, 0.001)
        assertEquals(1000.0, acerto.porPessoa, 0.001)
        assertEquals(listOf(Transferencia("Bruno", "Ana", 500.0)), acerto.transferencias)
    }

    @Test
    fun semAcertoQuandoSoUmaPessoaPagou() {
        assertNull(calcularAcerto(listOf(d("Aluguel", 1500.0, pagoPor = "a", nome = "Ana")), "a", "Ana"))
        assertNull(calcularAcerto(listOf(d("Aluguel", 1500.0)), "a", "Ana"))
    }
}
