package fluxai.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeitorNotificacoesTest {
    @Test
    fun compraNoCartaoNubank() {
        val c = interpretarNotificacaoBanco("com.nu.production", "Compra aprovada", "Compra de R$ 45,90 APROVADA em IFOOD *RESTAURANTE para o cartão com final 1234.")!!
        assertEquals("Ifood *Restaurante", c.descricao)
        assertEquals(45.90, c.valor, 0.001)
        assertEquals("Nubank", c.banco)
        assertTrue(c.noCartao)
    }

    @Test
    fun compraItauComLimiteNoFim() {
        val c = interpretarNotificacaoBanco("com.itau", "Itaú", "Compra aprovada no cartão final 1234: R$ 1.234,56 em MERCADO LIVRE em 12/03 às 10:00. Limite disponível R$ 1.000,00")!!
        assertEquals("Mercado Livre", c.descricao)
        assertEquals(1234.56, c.valor, 0.001)
    }

    @Test
    fun pixEnviado() {
        val c = interpretarNotificacaoBanco("br.com.intermedium", "Pix enviado", "Você enviou um Pix de R$ 120,00 para MARIA SOUZA.")!!
        assertEquals("Maria Souza", c.descricao)
        assertFalse(c.noCartao)
    }

    @Test
    fun semEstabelecimentoUsaNomeDoBanco() {
        val c = interpretarNotificacaoBanco("com.nu.production", "Pagamento realizado", "Pagamento de R$ 99,90 realizado com sucesso.")!!
        assertEquals("Compra Nubank", c.descricao)
    }

    @Test
    fun ignoraEntradasEAppsDesconhecidos() {
        assertNull(interpretarNotificacaoBanco("com.nu.production", "Pix recebido", "Você recebeu um Pix de R$ 50,00 de João."))
        assertNull(interpretarNotificacaoBanco("com.nu.production", "Compra negada", "Compra de R$ 10,00 em LOJA foi negada."))
        assertNull(interpretarNotificacaoBanco("com.whatsapp", "Compra", "Compra de R$ 10,00 em LOJA"))
        assertNull(interpretarNotificacaoBanco("com.nu.production", "Sua fatura fechou", "Fatura fechou em R$ 800,00"))
    }
}
