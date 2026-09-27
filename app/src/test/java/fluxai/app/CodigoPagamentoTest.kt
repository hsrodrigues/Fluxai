package fluxai.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodigoPagamentoTest {
    private val hoje = DataSimples(27, 9, 2026)
    private val barras = "34191" + "1000" + "0000012345" + "1234567890123456789012345"

    // Monta a linha digitável a partir do código de barras (dígitos verificadores dos campos não importam aqui)
    private fun linhaDe(b: String) = b.substring(0, 4) + b.substring(19, 24) + "0" + b.substring(24, 34) + "0" + b.substring(34, 44) + "0" + b[4] + b.substring(5, 19)

    private fun tlv(id: String, v: String) = id + "%02d".format(v.length) + v

    @Test
    fun leCodigoDeBarrasDoBoleto() {
        val dados = interpretarCodigoPagamento(barras, hoje)!!
        assertEquals("Boleto", dados.tipo)
        assertEquals(123.45, dados.valor!!, 0.001)
        assertEquals(DataSimples(22, 2, 2025), dados.vencimento)
    }

    @Test
    fun leLinhaDigitavelComPontosEEspacos() {
        val linha = linhaDe(barras)
        assertEquals(47, linha.length)
        assertEquals(barras, linhaBancariaParaBarras(linha))
        val formatada = linha.chunked(5).joinToString(".").chunked(12).joinToString(" ")
        assertEquals(123.45, interpretarCodigoPagamento(formatada, hoje)!!.valor!!, 0.001)
    }

    @Test
    fun fatorDeVencimentoAntesEDepoisDaVirada() {
        assertEquals(DataSimples(21, 2, 2025), vencimentoPorFator(9999, DataSimples(10, 1, 2025)))
        assertEquals(DataSimples(22, 2, 2025), vencimentoPorFator(1000, hoje))
        assertEquals(DataSimples(1, 3, 2025), vencimentoPorFator(1007, hoje))
        assertNull(vencimentoPorFator(0, hoje))
    }

    @Test
    fun leContaDeConsumo() {
        val b = "8" + "3" + "6" + "7" + "00000012345" + "12345678901234567890123456789"
        assertEquals(44, b.length)
        val linha = b.chunked(11).joinToString("") { it + "0" }
        val dados = interpretarCodigoPagamento(linha, hoje)!!
        assertEquals("Conta de consumo", dados.tipo)
        assertEquals(123.45, dados.valor!!, 0.001)
        assertNull(dados.vencimento)
    }

    @Test
    fun lePixCopiaECola() {
        val pix = tlv("00", "01") + tlv("26", tlv("00", "br.gov.bcb.pix") + tlv("01", "chave@exemplo.com")) +
            tlv("52", "0000") + tlv("53", "986") + tlv("54", "45.90") + tlv("58", "BR") +
            tlv("59", "PADARIA PAO BOM") + tlv("60", "SAO PAULO") + tlv("63", "ABCD")
        val dados = interpretarCodigoPagamento(pix, hoje)!!
        assertEquals("Pix", dados.tipo)
        assertEquals(45.90, dados.valor!!, 0.001)
        assertEquals("Padaria Pao Bom", dados.beneficiario)
    }

    @Test
    fun recusaTextoQualquer() {
        assertNull(interpretarCodigoPagamento("Compra no mercado 12345", hoje))
        assertNull(interpretarCodigoPagamento("123", hoje))
    }
}
