package fluxai.app

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import fluxai.app.ui.theme.LocalAccentColor
import fluxai.app.ui.theme.LocalDarkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(onRegisterSuccess: () -> Unit, onBackToLogin: () -> Unit) {
    var nome by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var senhaVisivel by remember { mutableStateOf(false) }
    var carregando by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Mesmo visual do login: topo com o fundo da marca e formulário numa folha arredondada
    val isDark = LocalDarkTheme.current
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
    val senhaOk = senha.length >= 6

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(FundoMarca, FundoMarcaRoxo)))
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // TOPO: voltar + logo
        Box(Modifier.fillMaxWidth().height(170.dp)) {
            IconButton(onClick = onBackToLogin, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar para o login", tint = Color.White)
            }
            Box(
                Modifier
                    .size(180.dp)
                    .align(Alignment.Center)
                    .background(Brush.radialGradient(listOf(RoxoMarca.copy(alpha = 0.35f), Color.Transparent)), CircleShape)
            )
            Image(
                painter = painterResource(id = R.drawable.logo_app),
                contentDescription = "Logo FluxAí",
                modifier = Modifier.size(180.dp).align(Alignment.Center),
                contentScale = ContentScale.Fit
            )
        }

        // FORMULÁRIO
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
                Text("Criar sua conta", fontSize = 24.sp, fontWeight = FontWeight.Black, color = colorTextPrimary)
                Text("Leva menos de um minuto para começar", fontSize = 14.sp, color = colorTextSecondary)
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = nome, onValueChange = { nome = it }, label = { Text("Nome completo", color = colorTextSecondary) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    colors = coresCampo, singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = email, onValueChange = { email = it }, label = { Text("E-mail", color = colorTextSecondary) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    leadingIcon = { Icon(Icons.Default.Email, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = coresCampo, singleLine = true
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
                    colors = coresCampo, singleLine = true
                )

                // Dica do tamanho mínimo da senha, que fica verde quando atendida
                Row(Modifier.padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = if (senhaOk) Color(0xFF43A047) else colorDivider, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pelo menos 6 caracteres", fontSize = 12.sp, color = if (senhaOk) Color(0xFF43A047) else colorTextSecondary)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (nome.isNotBlank() && email.isNotBlank() && senha.length >= 6) {
                            carregando = true
                            Firebase.auth.createUserWithEmailAndPassword(email, senha)
                                .addOnSuccessListener { result ->
                                    val update = userProfileChangeRequest { displayName = nome }
                                    result.user?.updateProfile(update)?.addOnCompleteListener {
                                        carregando = false
                                        onRegisterSuccess()
                                    }
                                }
                                .addOnFailureListener {
                                    carregando = false
                                    Toast.makeText(context, "Erro: ${it.message}", Toast.LENGTH_LONG).show()
                                }
                        } else {
                            Toast.makeText(context, "Preencha todos os campos (Senha min. 6 caracteres)", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colorAccent),
                    enabled = !carregando
                ) {
                    if (carregando) CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
                    else Text("Criar conta", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text("Já tem uma conta?", color = colorTextSecondary, fontSize = 14.sp)
                    TextButton(onClick = onBackToLogin, contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Text("Entrar", color = colorAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.weight(1f).heightIn(min = 16.dp))
                Text(
                    text = "Ao criar a conta, você concorda com nossos Termos de Uso e Política de Privacidade.",
                    fontSize = 11.sp, color = colorTextSecondary, textAlign = TextAlign.Center, lineHeight = 16.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)
                )
            }
        }
    }
}
