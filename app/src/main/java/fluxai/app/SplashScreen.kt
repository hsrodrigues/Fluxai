package fluxai.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    // Animação de opacidade (do invisível para o visível)
    val alpha = remember { Animatable(0f) }

    // Cores do seu tema
    val colorBg = Color(0xFFF8F9FA)
    val colorAccent = Color(0xFF7E57C2)
    val colorDarkBg = Color(0xFF1E1E1E)

    // Lógica de tempo e animação
    LaunchedEffect(key1 = true) {
        // Anima a opacidade para 100% em 1 segundo
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000)
        )
        // Segura a tela por mais 1 segundo (tempo total = 2s)
        delay(1000)
        // Navega para a próxima tela
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(alpha.value) // Aplica a animação aqui
        ) {
            // Sua logo destacada
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(colorDarkBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_app),
                    contentDescription = "Logo FluxAí",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "FLUXAÍ",
                color = colorAccent,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Gestão Inteligente",
                color = Color.Gray,
                fontSize = 14.sp,
                letterSpacing = 1.sp
            )
        }
    }
}