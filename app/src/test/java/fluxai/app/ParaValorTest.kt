package fluxai.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParaValorTest {
    @Test
    fun formatoBrasileiroComMilhar() {
        assertEquals(1500.0, "1.500,00".paraValor()!!, 0.001)
        assertEquals(1234567.89, "1.234.567,89".paraValor()!!, 0.001)
    }

    @Test
    fun virgulaComoDecimal() {
        assertEquals(12.5, "12,5".paraValor()!!, 0.001)
        assertEquals(1500.0, "1500,00".paraValor()!!, 0.001)
    }

    @Test
    fun pontoComoDecimal() {
        assertEquals(1500.5, "1500.50".paraValor()!!, 0.001)
        assertEquals(12.99, "12.99".paraValor()!!, 0.001)
    }

    @Test
    fun pontoComoMilhar() {
        assertEquals(1500.0, "1.500".paraValor()!!, 0.001)
        assertEquals(15000.0, "15.000".paraValor()!!, 0.001)
        assertEquals(2000000.0, "2.000.000".paraValor()!!, 0.001)
    }

    @Test
    fun comSimboloEEspacos() {
        assertEquals(1500.0, "R$ 1.500,00".paraValor()!!, 0.001)
        assertEquals(80.0, " 80 ".paraValor()!!, 0.001)
    }

    @Test
    fun invalidos() {
        assertNull("".paraValor())
        assertNull("abc".paraValor())
        assertNull("R$".paraValor())
    }
}
