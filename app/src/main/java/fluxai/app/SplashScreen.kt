package fluxai.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Cores da marca: mesmo azul-marinho do fundo do ícone do app e o roxo da logo
val FundoMarca = Color(0xFF0F172A)
val FundoMarcaRoxo = Color(0xFF1E1B4B)
val RoxoMarca = Color(0xFF8B5CF6)

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    val escalaLogo = remember { Animatable(0.82f) }
    val alphaLogo = remember { Animatable(0f) }
    val alphaTexto = remember { Animatable(0f) }

    // Tempo total igual ao anterior (~2s): entrada da logo, frase e pausa curta
    LaunchedEffect(key1 = true) {
        launch { alphaLogo.animateTo(1f, tween(durationMillis = 700)) }
        escalaLogo.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
        alphaTexto.animateTo(1f, tween(durationMillis = 400))
        delay(700)
        onTimeout()
    }

    // Brilho atrás da logo "respirando" devagar
    val pulso = rememberInfiniteTransition(label = "pulso")
    val intensidadeBrilho by pulso.animateFloat(
        initialValue = 0.28f, targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "brilho"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(FundoMarca, FundoMarca, FundoMarcaRoxo))),
        contentAlignment = Alignment.Center
    ) {
        // Halo roxo suave
        Box(
            Modifier
                .size(340.dp)
                .alpha(alphaLogo.value)
                .background(Brush.radialGradient(listOf(RoxoMarca.copy(alpha = intensidadeBrilho), Color.Transparent)), CircleShape)
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // A logo tem bastante margem transparente; o tamanho grande compensa
            Image(
                painter = painterResource(id = R.drawable.logo_app),
                contentDescription = "Logo FluxAí",
                modifier = Modifier
                    .size(300.dp)
                    .scale(escalaLogo.value)
                    .alpha(alphaLogo.value)
            )
            Text(
                text = "GESTÃO FINANCEIRA INTELIGENTE",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 3.sp,
                modifier = Modifier.alpha(alphaTexto.value).padding(top = 0.dp)
            )
        }

        // Indicador de carregamento discreto no rodapé
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 40.dp)
                .alpha(alphaTexto.value),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PontosCarregando()
            Spacer(Modifier.height(14.dp))
            Text("v${BuildConfig.VERSION_NAME}", color = Color.White.copy(alpha = 0.35f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun PontosCarregando() {
    val transicao = rememberInfiniteTransition(label = "pontos")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { i ->
            val a by transicao.animateFloat(
                initialValue = 0.25f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(500, delayMillis = i * 160), RepeatMode.Reverse), label = "ponto$i"
            )
            Box(Modifier.size(7.dp).alpha(a).background(RoxoMarca, CircleShape))
        }
    }
}
