package fluxai.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarcasTest {
    private fun icone(desc: String) = marcaPorNome(desc)?.icone

    @Test
    fun reconheceServicos() {
        assertEquals(R.drawable.ic_marca_netflix, icone("Netflix Premium"))
        assertEquals(R.drawable.ic_marca_spotify, icone("spotify família"))
        assertEquals(R.drawable.ic_marca_applemusic, icone("Apple Music"))
        assertEquals(R.drawable.ic_marca_apple, icone("Apple One"))
        assertEquals(R.drawable.ic_marca_max, icone("Max"))
        assertEquals(R.drawable.ic_operadora_vivo, icone("Conta Vivo Controle"))
        assertEquals(R.drawable.ic_operadora_oi, icone("Oi Fibra"))
        assertEquals(R.drawable.ic_operadora_tim, icone("TIM Black"))
    }

    @Test
    fun naoConfundePalavrasParecidas() {
        assertNull(icone("Maxxi atacado"))
        assertNull(icone("Mercado Poison"))
        assertNull(icone("Timbó materiais"))
        assertNull(icone("Aluguel"))
    }
}
