package fluxai.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// =========================================================================
// LOGOS DE SERVIÇOS CONHECIDOS (Netflix, Spotify, operadoras...)
// Logos de marcas vêm do Simple Icons (CC0), em branco sobre a cor oficial da marca.
// Operadoras usam os logos coloridos já existentes, sobre fundo branco.
// =========================================================================
data class Marca(val icone: Int, val cor: Color, val logoColorido: Boolean = false)

// Ordem importa: termos mais específicos antes dos genéricos ("apple music" antes de "apple")
private val marcas: List<Pair<List<String>, Marca>> = listOf(
    listOf("netflix") to Marca(R.drawable.ic_marca_netflix, Color(0xFFE50914)),
    listOf("spotify") to Marca(R.drawable.ic_marca_spotify, Color(0xFF1ED760)),
    listOf("youtube") to Marca(R.drawable.ic_marca_youtube, Color(0xFFFF0000)),
    listOf("hbo") to Marca(R.drawable.ic_marca_hbomax, Color(0xFF000000)),
    listOf("max") to Marca(R.drawable.ic_marca_max, Color(0xFF002BE7)),
    listOf("deezer") to Marca(R.drawable.ic_marca_deezer, Color(0xFFA238FF)),
    listOf("apple music") to Marca(R.drawable.ic_marca_applemusic, Color(0xFFFA243C)),
    listOf("apple tv") to Marca(R.drawable.ic_marca_appletv, Color(0xFF000000)),
    listOf("icloud") to Marca(R.drawable.ic_marca_icloud, Color(0xFF3693F3)),
    listOf("google drive", "google one") to Marca(R.drawable.ic_marca_googledrive, Color(0xFF4285F4)),
    listOf("google play") to Marca(R.drawable.ic_marca_googleplay, Color(0xFF414141)),
    listOf("playstation", "psn", "ps plus") to Marca(R.drawable.ic_marca_playstation, Color(0xFF0070D1)),
    listOf("crunchyroll") to Marca(R.drawable.ic_marca_crunchyroll, Color(0xFFFF5E00)),
    listOf("paramount") to Marca(R.drawable.ic_marca_paramountplus, Color(0xFF0064FF)),
    listOf("twitch") to Marca(R.drawable.ic_marca_twitch, Color(0xFF9146FF)),
    listOf("uber") to Marca(R.drawable.ic_marca_uber, Color(0xFF000000)),
    listOf("ifood") to Marca(R.drawable.ic_marca_ifood, Color(0xFFEA1D2C)),
    listOf("duolingo") to Marca(R.drawable.ic_marca_duolingo, Color(0xFF58CC02)),
    listOf("dropbox") to Marca(R.drawable.ic_marca_dropbox, Color(0xFF0061FF)),
    listOf("tidal") to Marca(R.drawable.ic_marca_tidal, Color(0xFF000000)),
    listOf("audible") to Marca(R.drawable.ic_marca_audible, Color(0xFFF8991C)),
    listOf("nubank", "nu ") to Marca(R.drawable.ic_marca_nubank, Color(0xFF820AD1)),
    listOf("picpay") to Marca(R.drawable.ic_marca_picpay, Color(0xFF21C25E)),
    listOf("telegram") to Marca(R.drawable.ic_marca_telegram, Color(0xFF26A5E4)),
    listOf("discord") to Marca(R.drawable.ic_marca_discord, Color(0xFF5865F2)),
    listOf("steam") to Marca(R.drawable.ic_marca_steam, Color(0xFF171A21)),
    listOf("notion") to Marca(R.drawable.ic_marca_notion, Color(0xFF000000)),
    listOf("apple") to Marca(R.drawable.ic_marca_apple, Color(0xFF000000)),
    listOf("google") to Marca(R.drawable.ic_marca_google, Color(0xFF4285F4)),
    listOf("claro") to Marca(R.drawable.ic_operadora_claro, Color.White, logoColorido = true),
    listOf("vivo") to Marca(R.drawable.ic_operadora_vivo, Color.White, logoColorido = true),
    listOf("tim") to Marca(R.drawable.ic_operadora_tim, Color.White, logoColorido = true),
    listOf("oi") to Marca(R.drawable.ic_operadora_oi, Color.White, logoColorido = true)
)

// Expressões compiladas uma única vez (antes eram recriadas a cada card desenhado, o que travava a rolagem)
private val regexLimpeza = Regex("[^a-z0-9à-ú+ ]")
private val marcasCompiladas: List<Pair<List<Regex>, Marca>> by lazy {
    marcas.map { (termos, marca) -> termos.map { t -> Regex("(?<![a-z0-9])" + Regex.escape(t.trim()) + "(?![a-z0-9])") } to marca }
}

// Resultado guardado por descrição: a mesma conta aparece em vários meses e redesenhos
private val cacheMarcas = java.util.concurrent.ConcurrentHashMap<String, java.util.Optional<Marca>>()

// Procura a marca como palavra inteira na descrição ("Max" não casa com "Maxxi")
fun marcaPorNome(descricao: String): Marca? {
    cacheMarcas[descricao]?.let { return it.orElse(null) }
    val texto = " " + descricao.lowercase().replace(regexLimpeza, " ") + " "
    val marca = marcasCompiladas.firstOrNull { (regexes, _) -> regexes.any { it.containsMatchIn(texto) } }?.second
    if (cacheMarcas.size > 500) cacheMarcas.clear()
    cacheMarcas[descricao] = java.util.Optional.ofNullable(marca)
    return marca
}

// Ícone quadrado do lançamento: logo da marca, se reconhecida; senão o ícone genérico informado
@Composable
fun IconeLancamento(descricao: String, iconeGenerico: ImageVector, corGenerica: Color, tamanho: Dp = 42.dp) {
    val marca = marcaPorNome(descricao)
    if (marca != null) {
        Box(
            Modifier.size(tamanho).clip(RoundedCornerShape(tamanho * 0.28f)).background(marca.cor),
            contentAlignment = Alignment.Center
        ) {
            if (marca.logoColorido) {
                androidx.compose.foundation.Image(painterResource(marca.icone), null, modifier = Modifier.size(tamanho).padding(tamanho * 0.14f))
            } else {
                Icon(painterResource(marca.icone), null, tint = Color.White, modifier = Modifier.size(tamanho * 0.52f))
            }
        }
    } else {
        Box(Modifier.size(tamanho).clip(RoundedCornerShape(tamanho * 0.28f)).background(corGenerica.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(iconeGenerico, null, tint = corGenerica, modifier = Modifier.size(tamanho * 0.52f))
        }
    }
}
