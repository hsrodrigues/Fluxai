package fluxai.app.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val LocalDarkTheme = compositionLocalOf { false }
val LocalThemeToggle = compositionLocalOf<() -> Unit> { {} }
val LocalAccentColor = compositionLocalOf { Color(0xFF7E57C2) } // Cor de destaque padrão (Roxo)

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun FluxaiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Ajustado para false para garantir a prevalência da cor personalizada
    onThemeToggle: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE) }

    // Estado reativo da cor hexadecimal do tema
    var accentHex by remember {
        mutableLongStateOf(sharedPref.getLong("theme_color_hex", 0xFF7E57C2L))
    }

    // Listener que atualiza a interface instantaneamente ao trocar a cor nas Configurações
    DisposableEffect(Unit) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "theme_color_hex") {
                accentHex = sharedPref.getLong("theme_color_hex", 0xFF7E57C2L)
            }
        }
        sharedPref.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPref.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val customAccentColor = Color(accentHex)

    val baseColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Aplica a cor de destaque selecionada às cores primária e secundária do MaterialTheme
    val colorScheme = baseColorScheme.copy(
        primary = customAccentColor,
        secondary = customAccentColor
    )

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalThemeToggle provides onThemeToggle,
        LocalAccentColor provides customAccentColor
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}