package fluxai.app

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Estado de carregamento para a verificação inicial da sessão
    var isVerifying by remember { mutableStateOf(true) }

    // =========================================
    // PALETA DE CORES DEFINITIVA (Tema Clean)
    // =========================================
    val colorBg = Color(0xFFF8F9FA) // Off-white de fundo (igual ao resto do app)
    val colorAccent = Color(0xFF7E57C2) // Roxo tema (igual aos botões principais)
    val colorDarkBg = Color(0xFF1E1E1E) // Escuro para o fundo da logo (igual ao menu)
    val colorTextDark = Color(0xFF111827) // Quase preto para títulos
    val colorTextLight = Color(0xFF6B7280) // Cinza para textos secundários

    // =========================================
    // LÓGICA: VERIFICAÇÃO DE SESSÃO ATIVA (Evita re-login)
    // =========================================
    LaunchedEffect(Unit) {
        // Se o Firebase já tiver um usuário logado no aparelho...
        if (Firebase.auth.currentUser != null) {
            // ...pula para o Dashboard imediatamente.
            onLoginSuccess()
        } else {
            // ...senão, encerra o carregamento e mostra a tela de login.
            isVerifying = false
        }
    }

    if (isVerifying) {
        // Tela de loading rápido para evitar "pulo" visual
        Box(modifier = Modifier.fillMaxSize().background(colorBg), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colorAccent)
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorBg)
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // =========================================
                // LOGO DO APP AMPLIADA E DESTACADA
                // =========================================
                // Mantivemos o fundo escuro redondo para destacar a letra branca da logo
                Box(
                    modifier = Modifier
                        .size(150.dp) // LOGO MUITO MAIOR
                        .background(colorDarkBg, CircleShape)
                        .shadow(12.dp, CircleShape, spotColor = colorAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logo_app),
                        contentDescription = "Logo FluxAí",
                        // Tamanho interno da imagem ajustado
                        modifier = Modifier.size(100.dp).clip(CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(48.dp)) // Espaço maior sem o título

                // TEXTO DE BOAS-VINDAS (Removido o título FLUXAÍ)
                Text(
                    text = "O controle financeiro inteligente e seguro para o seu dia a dia.",
                    fontSize = 17.sp,
                    color = colorTextDark, // Texto principal mais escuro
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp),
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(56.dp))

                // =========================================
                // CARTÃO DE LOGIN CLEAN (UAU EFFECT)
                // =========================================
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    // Sombra sutil roxa para alinhar ao tema
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Acesse sua conta",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorTextDark
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        // =========================================
                        // BOTÃO DE LOGIN GOOGLE NO TEMA (ROXO)
                        // =========================================
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    try {
                                        val credentialManager = CredentialManager.create(context)
                                        val googleIdOption = GetGoogleIdOption.Builder()
                                            .setFilterByAuthorizedAccounts(false)
                                            .setServerClientId("179005399064-v55jir7iced2e7p3509i339q9fb4mss8.apps.googleusercontent.com")
                                            .setAutoSelectEnabled(true)
                                            .build()

                                        val request = GetCredentialRequest.Builder()
                                            .addCredentialOption(googleIdOption)
                                            .build()

                                        val result = credentialManager.getCredential(context, request)
                                        val credential = result.credential

                                        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                                            val firebaseAuthCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)

                                            Firebase.auth.signInWithCredential(firebaseAuthCredential)
                                                .addOnSuccessListener { onLoginSuccess() }
                                                .addOnFailureListener { e -> Toast.makeText(context, "Erro de acesso: ${e.message}", Toast.LENGTH_LONG).show() }
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Autenticação cancelada.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp) // Botão premium alto
                                .shadow(6.dp, RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorAccent, // FUNDO ROXO TEMA
                                contentColor = Color.White // Texto Branco
                            ),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center // Centraliza tudo
                            ) {
                                // CÍRCULO BRANCO PARA O G DO GOOGLE (Garante conformidade de marca)
                                Surface(
                                    modifier = Modifier.size(42.dp).clip(CircleShape),
                                    color = Color.White
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Image(
                                            painter = painterResource(id = R.drawable.logo_google_color),
                                            contentDescription = "Google",
                                            modifier = Modifier.size(20.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Text(
                                    text = "Continuar com o Google",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White // Texto Branco
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Ao continuar, você concorda com nossos Termos de Uso e Política de Privacidade.",
                            fontSize = 11.sp,
                            color = colorTextLight,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }
}