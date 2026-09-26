package fluxai.app

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

@Composable
fun TelaBloqueioBiometria(
    // O conteúdo do seu app (Dashboard, Home, etc) entra aqui depois de desbloquear
    conteudoDoApp: @Composable () -> Unit
) {
    val context = LocalContext.current
    var autenticado by remember { mutableStateOf(false) }
    var erroMensagem by remember { mutableStateOf("") }

    // Tenta autenticar logo que a tela abre
    LaunchedEffect(Unit) {
        solicitarBiometria(
            activity = context as FragmentActivity,
            onSuccess = { autenticado = true },
            onError = { erro -> erroMensagem = erro }
        )
    }

    if (autenticado) {
        // Se reconheceu o rosto/dedo, mostra o aplicativo!
        conteudoDoApp()
    } else {
        // Tela de bloqueio enquanto não autentica
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFF121212)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(80.dp).background(Color(0xFF7E57C2).copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Lock, contentDescription = "Bloqueado", tint = Color(0xFF7E57C2), modifier = Modifier.size(40.dp))
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text("FluxAí Bloqueado", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Use a biometria para acessar suas finanças.", color = Color.Gray, fontSize = 14.sp)

                if (erroMensagem.isNotBlank()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(erroMensagem, color = Color(0xFFF44336), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            solicitarBiometria(context as FragmentActivity, { autenticado = true }, { erroMensagem = it })
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7E57C2))
                    ) {
                        Text("Tentar Novamente", color = Color.White)
                    }
                }
            }
        }
    }
}

// Função que chama o sistema do Android (Rosto, Digital ou PIN)
private fun solicitarBiometria(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val executor = ContextCompat.getMainExecutor(activity)

    val biometricPrompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errString.toString())
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Acontece quando lê o rosto/dedo mas não reconhece
            }
        }
    )

    // Configura a janelinha do Android
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Acesso Seguro FluxAí")
        .setSubtitle("Confirme sua identidade")
        // Permite Face ID (Weak/Strong), Touch ID e a Senha de números do celular caso a biometria falhe
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        .build()

    biometricPrompt.authenticate(promptInfo)
}