package fluxai.app

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import fluxai.app.ui.theme.LocalAccentColor
import kotlinx.coroutines.launch

// Importando o controle global de tema
import fluxai.app.ui.theme.LocalDarkTheme

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Lendo a cor atual do app
    val isDark = LocalDarkTheme.current

    var isVerifying by remember { mutableStateOf(true) }

    // ESTADOS DE EMAIL/SENHA
    var email by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var senhaVisivel by remember { mutableStateOf(false) }
    var carregandoEmail by remember { mutableStateOf(false) }

    // ESTADOS DO POP-UP DE RECUPERAÇÃO
    var mostrarDialogRecuperacao by remember { mutableStateOf(false) }
    var emailRecuperacao by remember { mutableStateOf("") }
    var enviandoEmail by remember { mutableStateOf(false) }

    // =========================================
    // TEMA DINÂMICO
    // =========================================
    val colorAccent = LocalAccentColor.current
    val colorSurface = if (isDark) Color(0xFF121212) else Color.White
    val colorCampo = if (isDark) Color(0xFF1A1A1A) else Color(0xFFF4F7FA)
    val colorTextPrimary = if (isDark) Color(0xFFF9FAFB) else Color(0xFF1A1A1A)
    val colorTextSecondary = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val colorDivider = if (isDark) Color(0xFF2C2C2C) else Color(0xFFE5E7EB)
    val coresCampo = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colorAccent, unfocusedBorderColor = colorDivider,
        focusedTextColor = colorTextPrimary, unfocusedTextColor = colorTextPrimary,
        focusedContainerColor = colorCampo, unfocusedContainerColor = colorCampo,
        focusedLeadingIconColor = colorAccent, cursorColor = colorAccent
    )

    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser != null) {
            onLoginSuccess()
        } else {
            isVerifying = false
        }
    }

    if (isVerifying) {
        Box(modifier = Modifier.fillMaxSize().background(FundoMarca), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = RoxoMarca)
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(FundoMarca, FundoMarcaRoxo)))
                .windowInsetsPadding(WindowInsets.statusBars),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ÁREA SUPERIOR (logo com halo, igual à splash)
            Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(240.dp)
                        .background(Brush.radialGradient(listOf(RoxoMarca.copy(alpha = 0.35f), Color.Transparent)), CircleShape)
                )
                // A logo tem margem transparente; o tamanho compensa
                Image(
                    painter = painterResource(id = R.drawable.logo_app),
                    contentDescription = "Logo FluxAí",
                    modifier = Modifier.size(230.dp),
                    contentScale = ContentScale.Fit
                )
            }

            // ÁREA INFERIOR (Formulário)
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = colorSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp)
                ) {
                    Spacer(modifier = Modifier.height(28.dp))
                    Text("Bem-vindo de volta", fontSize = 24.sp, fontWeight = FontWeight.Black, color = colorTextPrimary)
                    Text("Entre para acompanhar suas finanças", fontSize = 14.sp, color = colorTextSecondary)
                    Spacer(modifier = Modifier.height(24.dp))

                    // CAMPOS DE LOGIN
                    OutlinedTextField(
                        value = email, onValueChange = { email = it }, label = { Text("E-mail", color = colorTextSecondary) },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                        leadingIcon = { Icon(Icons.Default.Email, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = coresCampo,
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = senha, onValueChange = { senha = it }, label = { Text("Senha", color = colorTextSecondary) },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        visualTransformation = if (senhaVisivel) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { senhaVisivel = !senhaVisivel }) {
                                Icon(if (senhaVisivel) Icons.Default.Visibility else Icons.Default.VisibilityOff, if (senhaVisivel) "Ocultar senha" else "Mostrar senha", tint = colorTextSecondary)
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = coresCampo,
                        singleLine = true
                    )

                    // ABRIR POP-UP DE RECUPERAÇÃO
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        TextButton(onClick = { mostrarDialogRecuperacao = true }) {
                            Text("Esqueci a senha", color = colorAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // BOTÃO ENTRAR
                    Button(
                        onClick = {
                            if (email.isNotBlank() && senha.isNotBlank()) {
                                carregandoEmail = true
                                Firebase.auth.signInWithEmailAndPassword(email.trim(), senha.trim())
                                    .addOnSuccessListener {
                                        carregandoEmail = false
                                        onLoginSuccess()
                                    }
                                    .addOnFailureListener {
                                        carregandoEmail = false
                                        Toast.makeText(context, "Login falhou. Verifique e-mail e senha.", Toast.LENGTH_SHORT).show()
                                    }
                            } else {
                                Toast.makeText(context, "Preencha e-mail e senha.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colorAccent), enabled = !carregandoEmail
                    ) {
                        if (carregandoEmail) CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
                        else Text("Entrar", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = colorDivider)
                        Text("  ou  ", color = colorTextSecondary, fontSize = 12.sp)
                        HorizontalDivider(modifier = Modifier.weight(1f), color = colorDivider)
                    }
                    Spacer(modifier = Modifier.height(24.dp))

                    // BOTÃO GOOGLE
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    val credentialManager = CredentialManager.create(context)
                                    val googleIdOption = GetGoogleIdOption.Builder()
                                        .setFilterByAuthorizedAccounts(false)
                                        .setServerClientId("179005399064-v55jir7iced2e7p3509i339q9fb4mss8.apps.googleusercontent.com")
                                        .setAutoSelectEnabled(true)
                                        .build()

                                    val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
                                    val result = credentialManager.getCredential(context, request)
                                    val credential = result.credential

                                    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                                        val firebaseAuthCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                                        Firebase.auth.signInWithCredential(firebaseAuthCredential)
                                            .addOnSuccessListener { onLoginSuccess() }
                                            .addOnFailureListener { e -> Toast.makeText(context, "Erro de acesso: ${e.message}", Toast.LENGTH_LONG).show() }
                                    }
                                } catch (e: NoCredentialException) {
                                    Toast.makeText(context, "Nenhuma conta Google encontrada neste aparelho.", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Autenticação cancelada.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = colorSurface, contentColor = colorTextPrimary),
                        border = BorderStroke(1.dp, colorDivider)
                    ) {
                        Image(painter = painterResource(id = R.drawable.logo_google_color), contentDescription = null, modifier = Modifier.size(20.dp), contentScale = ContentScale.Fit)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = "Continuar com Google", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colorTextPrimary)
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text("Não tem uma conta?", color = colorTextSecondary, fontSize = 14.sp)
                        TextButton(onClick = onNavigateToRegister, contentPadding = PaddingValues(horizontal = 6.dp)) {
                            Text("Criar conta", color = colorAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f).heightIn(min = 16.dp))
                    Text(
                        text = "Ao continuar, você concorda com nossos Termos de Uso e Política de Privacidade.",
                        fontSize = 11.sp, color = colorTextSecondary, textAlign = TextAlign.Center, lineHeight = 16.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)
                    )
                }
            }
        }

        // ====================================================
        // POP-UP DE RECUPERAÇÃO DE SENHA (MODAL)
        // ====================================================
        if (mostrarDialogRecuperacao) {
            AlertDialog(
                onDismissRequest = { mostrarDialogRecuperacao = false },
                containerColor = colorSurface,
                titleContentColor = colorTextPrimary,
                textContentColor = colorTextSecondary,
                shape = RoundedCornerShape(28.dp),
                icon = { Icon(Icons.Default.LockReset, null, tint = colorAccent) },
                title = { Text("Recuperar senha", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Enviaremos um link para você redefinir sua senha. Digite o e-mail cadastrado:", fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = emailRecuperacao,
                            onValueChange = { emailRecuperacao = it },
                            label = { Text("E-mail", color = colorTextSecondary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(),
                            colors = coresCampo,
                            singleLine = true,
                            shape = FormatoCampo
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (emailRecuperacao.isNotBlank()) {
                                enviandoEmail = true
                                Firebase.auth.sendPasswordResetEmail(emailRecuperacao.trim())
                                    .addOnSuccessListener {
                                        enviandoEmail = false
                                        mostrarDialogRecuperacao = false
                                        Toast.makeText(context, "E-mail de recuperação enviado!", Toast.LENGTH_LONG).show()
                                    }
                                    .addOnFailureListener {
                                        enviandoEmail = false
                                        Toast.makeText(context, "Erro ao enviar. Verifique o e-mail digitado.", Toast.LENGTH_LONG).show()
                                    }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colorAccent)
                    ) {
                        if (enviandoEmail) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                        else Text("Enviar", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { mostrarDialogRecuperacao = false }) {
                        Text("Cancelar", color = colorTextSecondary)
                    }
                }
            )
        }
    }
}