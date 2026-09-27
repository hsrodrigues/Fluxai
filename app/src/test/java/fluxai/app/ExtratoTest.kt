package fluxai.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtratoTest {
    @Test
    fun leOfxSemTagsDeFechamento() {
        val ofx = """
            OFXHEADER:100
            <OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKTRANLIST>
            <STMTTRN>
            <TRNTYPE>DEBIT
            <DTPOSTED>20260312100000[-3:BRT]
            <TRNAMT>-45.90
            <MEMO>SUPERMERCADO BOM PRECO
            </STMTTRN>
            <STMTTRN>
            <TRNTYPE>CREDIT
            <DTPOSTED>20260305
            <TRNAMT>3000.00
            <MEMO>SALARIO
            </STMTTRN>
            </BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>
        """.trimIndent()
        val itens = lerExtrato("extrato.ofx", ofx, fatura = false)
        assertEquals(2, itens.size)
        assertEquals(ItemExtrato(DataSimples(12, 3, 2026), "Supermercado Bom Preco", 45.90, entrada = false), itens[0])
        assertTrue(itens[1].entrada)
    }

    @Test
    fun leCsvDaContaNubank() {
        val csv = "Data,Valor,Identificador,Descrição\n" +
            "12/03/2026,-45.90,abc-123,Compra no débito - Padaria\n" +
            "13/03/2026,200.00,def-456,\"Transferência recebida, Maria\"\n"
        val itens = lerCSVExtrato(csv, fatura = false)
        assertEquals(2, itens.size)
        assertEquals("Compra no débito - Padaria", itens[0].descricao)
        assertFalse(itens[0].entrada)
        assertEquals("Transferência recebida, Maria", itens[1].descricao)
        assertTrue(itens[1].entrada)
    }

    @Test
    fun leCsvDeFaturaComComprasPositivas() {
        val csv = "date,title,amount\n2026-03-10,Uber *Trip,23.50\n2026-03-11,Pagamento recebido,-500.00\n"
        val itens = lerCSVExtrato(csv, fatura = true)
        assertEquals(DataSimples(10, 3, 2026), itens[0].data)
        assertFalse(itens[0].entrada)
        assertEquals(23.50, itens[0].valor, 0.001)
        assertTrue(itens[1].entrada)
    }

    @Test
    fun leCsvComPontoEVirgulaEValorBrasileiro() {
        val csv = "Data;Histórico;Valor\n12/03/26;CONTA DE LUZ;-1.234,56\n"
        val item = lerCSVExtrato(csv, fatura = false).single()
        assertEquals(1234.56, item.valor, 0.001)
        assertEquals("Conta De Luz", item.descricao)
        assertEquals(DataSimples(12, 3, 2026), item.data)
    }

    @Test
    fun csvSemCabecalho() {
        val csv = "12/03/2026;Farmácia Central;-30,00\n13/03/2026;Posto Shell;-150,00\n"
        val itens = lerCSVExtrato(csv, fatura = false)
        assertEquals(2, itens.size)
        assertEquals("Posto Shell", itens[1].descricao)
    }

    @Test
    fun sugereCategoriaPelaDescricao() {
        assertEquals("Alimentação", sugerirCategoria("IFOOD *RESTAURANTE"))
        assertEquals("Saúde", sugerirCategoria("Drogasil 123"))
        assertEquals("Transporte", sugerirCategoria("Posto Shell"))
        assertEquals("Moradia", sugerirCategoria("Conta de Energia Enel"))
        assertEquals("Outros", sugerirCategoria("Loja XYZ"))
    }
}

class ExtratoPdfTest {
    @Test
    fun leLinhasDaIA() {
        val resposta = """
            05/09|IFOOD *RESTAURANTE|45.90|S
            12/08|LOJA X 03/10|120,00|S
            20/09|Pagamento recebido|800.00|E
            Total da fatura|999
            ```
        """.trimIndent()
        val itens = lerLinhasFaturaIA(resposta, DataSimples(27, 9, 2026))
        assertEquals(3, itens.size)
        assertEquals(ItemExtrato(DataSimples(5, 9, 2026), "Ifood *Restaurante", 45.90, entrada = false), itens[0])
        assertEquals(120.0, itens[1].valor, 0.001)
        assertTrue(itens[2].entrada)
    }

    @Test
    fun mesDepoisDoAtualEhDoAnoPassado() {
        val item = lerLinhasFaturaIA("15/12|Presente|50.00|S", DataSimples(10, 1, 2027)).single()
        assertEquals(DataSimples(15, 12, 2026), item.data)
    }
}
