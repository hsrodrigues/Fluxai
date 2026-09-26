package fluxai.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Tela exibida enquanto a biometria não é confirmada (escura em qualquer tema, mesma identidade do widget)
@Composable
fun TelaBloqueio(nomeUsuario: String?, corDestaque: Color, onDesbloquear: () -> Unit) {
    val pulso = rememberInfiniteTransition(label = "pulso")
    val escala by pulso.animateFloat(1f, 1.12f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "escala")
    val brilho by pulso.animateFloat(0.25f, 0.05f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "brilho")

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2A1558), Color(0xFF1C1236), Color(0xFF0E0A1A)))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Image(painterResource(R.drawable.widget_logo), contentDescription = "FluxAí", modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(16.dp))
            Text("FluxAí", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text(
                if (!nomeUsuario.isNullOrBlank()) "Olá, $nomeUsuario" else "Bem-vindo de volta",
                color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp
            )

            Spacer(Modifier.height(64.dp))

            // Botão de digital pulsando
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(120.dp).scale(escala).background(corDestaque.copy(alpha = brilho), CircleShape))
                Box(
                    Modifier.size(88.dp).background(corDestaque.copy(alpha = 0.25f), CircleShape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDesbloquear),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Fingerprint, "Desbloquear", tint = Color.White, modifier = Modifier.size(52.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Toque para desbloquear", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text("Digital, rosto ou senha do celular", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }

        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Lock, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Seus dados financeiros estão protegidos", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
        }
    }
}
