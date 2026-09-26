package fluxai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// O IMPORT SALVADOR DA PÁTRIA:
import coil.compose.AsyncImage

@Composable
fun SmartAvatar(
    photoUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    backgroundColor: Color = Color(0xFF7E57C2)
) {
    if (photoUrl.isNullOrBlank()) {
        // Fallback: Se não tiver foto, mostra o ícone de Pessoa
        Box(
            modifier = modifier
                .size(size)
                .background(backgroundColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Avatar padrão",
                tint = Color.White,
                modifier = Modifier.size(size * 0.6f)
            )
        }
    } else {
        // Carrega a imagem da internet com Coil
        AsyncImage(
            model = photoUrl,
            contentDescription = "Foto de Perfil",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(Color.LightGray)
        )
    }
}