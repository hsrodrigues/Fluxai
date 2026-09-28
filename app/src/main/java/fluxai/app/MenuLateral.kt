package fluxai.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import fluxai.app.ui.components.SmartAvatar
import fluxai.app.ui.theme.LocalAccentColor
import fluxai.app.ui.theme.LocalDarkTheme
import fluxai.app.ui.theme.LocalThemeToggle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun MenuLateral(
    drawerState: DrawerState,
    coroutineScope: CoroutineScope,
    rotaAtual: String,
    onAbrirDashboard: (() -> Unit)? = null,
    onAbrirLancamento: (() -> Unit)? = null,
    onAbrirAnalytics: (() -> Unit)? = null,
    onAbrirSettings: (() -> Unit)? = null,
    onAbrirSobre: (() -> Unit)? = null,
    onAbrirManutencao: (() -> Unit)? = null,
    onLogout: () -> Unit = {},
    onAbrirCartoes: (() -> Unit)? = null,
    onAbrirCaixinhas: (() -> Unit)? = null,
    onAbrirAssinaturas: (() -> Unit)? = null,
    onAbrirCelular: (() -> Unit)? = null
) {
    // Sem callback próprio, o item navega pela rota (telas novas usam só a rota)
    val navegar = LocalNavegar.current
    fun abrir(callback: (() -> Unit)?, rota: String) {
        coroutineScope.launch { drawerState.close() }
        (callback ?: { navegar(rota) })()
    }
    val usuario = Firebase.auth.currentUser
    val isDark = LocalDarkTheme.current
    val toggleTheme = LocalThemeToggle.current
    val scrollState = rememberScrollState()

    val colorAccent = LocalAccentColor.current
    val colorBgHeader = if (isDark) Color(0xFF121212) else Color(0xFFF8F9FA)
    val colorSurface = if (isDark) Color(0xFF1E1E1E) else Color.White
    val colorTextPrimary = if (isDark) Color.White else Color(0xFF1E1E1E)
    val colorTextSecondary = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF6B7280)
    val colorDivider = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB)

    val hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    // Depois da meia-noite já é "Bom dia"
    val saudacao = when (hora) {
        in 0..11 -> "Bom dia"
        in 12..17 -> "Boa tarde"
        else -> "Boa noite"
    }
    val nomeDisplay = usuario?.displayName?.split(" ")?.firstOrNull() ?: "Usuário"
    val context = LocalContext.current

    // Informações do perfil e resumo do mês (lidos localmente, sem consultas extras ao Firebase)
    val resumo = remember(drawerState.isOpen) { lerResumoSalvo(context) }
    val loginGoogle = usuario?.providerData?.any { it.providerId == "google.com" } == true
    val contaCompartilhada = remember(drawerState.isOpen) {
        val ws = context.getSharedPreferences("AppPrefs", android.content.Context.MODE_PRIVATE).getString("workspace_uid", null)
        ws != null && ws != usuario?.uid
    }
    val membroDesde = usuario?.metadata?.creationTimestamp?.let {
        java.text.SimpleDateFormat("MMM yyyy", java.util.Locale("pt", "BR")).format(java.util.Date(it)).replaceFirstChar { c -> c.uppercase() }
    }

    ModalDrawerSheet(modifier = Modifier.width(300.dp), drawerContainerColor = colorSurface) {
        Column(modifier = Modifier.fillMaxWidth().background(colorBgHeader).padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar com anel na cor de destaque
                Box(
                    modifier = Modifier.size(64.dp).background(Brush.linearGradient(listOf(colorAccent, colorAccent.copy(alpha = 0.4f))), CircleShape).padding(3.dp),
                    contentAlignment = Alignment.Center
                ) { SmartAvatar(photoUrl = usuario?.photoUrl?.toString(), size = 58.dp) }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("$saudacao, $nomeDisplay!", color = colorTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    usuario?.displayName?.takeIf { it.contains(" ") }?.let {
                        Text(it, color = colorTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(usuario?.email ?: "", color = colorTextSecondary.copy(alpha = 0.75f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            // Selos do perfil
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SeloPerfil(if (loginGoogle) "Google" else "E-mail", if (loginGoogle) Icons.Default.VerifiedUser else Icons.Default.Email, colorAccent)
                if (contaCompartilhada) SeloPerfil("Família", Icons.Default.Groups, Color(0xFF26A69A))
                membroDesde?.let { SeloPerfil("Desde $it", Icons.Default.CalendarMonth, colorTextSecondary) }
            }

            // Resumo do mês (mesmos dados do widget)
            resumo?.let { r ->
                val corStatus = when (r.nivel) {
                    NivelPrevisao.OK -> Color(0xFF43A047)
                    NivelPrevisao.ATENCAO -> Color(0xFFFB8C00)
                    NivelPrevisao.RISCO -> Color(0xFFE53935)
                    NivelPrevisao.INFO -> Color(0xFF1E88E5)
                }
                val fmt = remember { java.text.NumberFormat.getCurrencyInstance(java.util.Locale("pt", "BR")) }
                val vencidas = r.contas.count { it.vencida }

                Spacer(Modifier.height(12.dp))
                // Resumo minimalista: uma linha com a sobra e uma linha de apoio
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { abrir(onAbrirDashboard, "dashboard") }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(8.dp).background(corStatus, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Sobra de ${r.mes.lowercase()}", fontSize = 11.sp, color = colorTextSecondary)
                        Text(
                            buildString {
                                append("A pagar ${fmt.format(r.aPagar)}")
                                if (vencidas > 0) append(" · $vencidas vencida${if (vencidas > 1) "s" else ""}")
                            },
                            fontSize = 11.sp, color = if (vencidas > 0) Color(0xFFE53935) else colorTextSecondary.copy(alpha = 0.8f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(fmt.format(r.sobra), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (r.sobra < 0) Color(0xFFE53935) else colorTextPrimary)
                }
            }
        }

        HorizontalDivider(color = colorDivider)

        Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(vertical = 12.dp)) {
            MenuSectionTitle("Operações")
            MenuItem("Dashboard", Icons.Default.Dashboard, rotaAtual == "dashboard", colorAccent, colorTextSecondary) { abrir(onAbrirDashboard, "dashboard") }
            MenuItem("Novo Lançamento", Icons.Default.AddCircle, rotaAtual == "home", colorAccent, colorTextSecondary) { abrir(onAbrirLancamento, "home") }
            MenuItem("Importar extrato", Icons.Default.UploadFile, rotaAtual == "importar", colorAccent, colorTextSecondary) { abrir(null, "importar") }
            MenuItem("Análise BI", Icons.Default.PieChart, rotaAtual == "analytics", colorAccent, colorTextSecondary) { abrir(onAbrirAnalytics, "analytics") }
            MenuItem("Relatório anual", Icons.Default.Assessment, rotaAtual == "relatorio", colorAccent, colorTextSecondary) { abrir(null, "relatorio") }

            Spacer(modifier = Modifier.height(16.dp))

            MenuSectionTitle("Gestão")
            MenuItem("Contas bancárias", Icons.Default.AccountBalance, rotaAtual == "contas", colorAccent, colorTextSecondary) { abrir(null, "contas") }
            MenuItem("Cartões", Icons.Default.CreditCard, rotaAtual == "cartoes", colorAccent, colorTextSecondary) { abrir(onAbrirCartoes, "cartoes") }
            MenuItem("Cofre / Metas", Icons.Default.Savings, rotaAtual == "caixinhas", colorAccent, colorTextSecondary) { abrir(onAbrirCaixinhas, "caixinhas") }
            MenuItem("Investimentos", Icons.AutoMirrored.Filled.TrendingUp, rotaAtual == "investimentos", colorAccent, colorTextSecondary) { abrir(null, "investimentos") }
            MenuItem("Hub de Assinaturas", Icons.Default.Autorenew, rotaAtual == "assinaturas", colorAccent, colorTextSecondary) { abrir(onAbrirAssinaturas, "assinaturas") }
            MenuItem("Planos e Celular", Icons.Default.SettingsCell, rotaAtual == "celular", colorAccent, colorTextSecondary) { abrir(onAbrirCelular, "celular") }
            MenuItem("Ativos & TCO", Icons.Default.Build, rotaAtual == "manutencao", colorAccent, colorTextSecondary) { abrir(onAbrirManutencao, "manutencao") }

            Spacer(modifier = Modifier.height(16.dp))

            MenuSectionTitle("Preferências")
            MenuItem("Configurações", Icons.Default.AccountCircle, rotaAtual == "settings", colorAccent, colorTextSecondary) { abrir(onAbrirSettings, "settings") }
            MenuItem(if (isDark) "Modo Claro" else "Modo Escuro", if (isDark) Icons.Default.WbSunny else Icons.Default.DarkMode, false, colorAccent, colorTextSecondary) { toggleTheme() }
            MenuItem("Sobre o App", Icons.Default.Info, rotaAtual == "sobre", colorAccent, colorTextSecondary) { abrir(onAbrirSobre, "sobre") }
            // Só o administrador vê: ativar e desativar contas
            if (ehAdmin()) MenuItem("Usuários", Icons.Default.AdminPanelSettings, rotaAtual == "admin_usuarios", colorAccent, colorTextSecondary) { abrir(null, "admin_usuarios") }

            Spacer(modifier = Modifier.height(32.dp))

            NavigationDrawerItem(
                label = { Text("Encerrar Sessão", fontWeight = FontWeight.Bold) },
                selected = false,
                icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) },
                colors = NavigationDrawerItemDefaults.colors(unselectedIconColor = Color(0xFFF44336), unselectedTextColor = Color(0xFFF44336)),
                onClick = { coroutineScope.launch { drawerState.close() }; Firebase.auth.signOut(); onLogout() },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun MenuSectionTitle(title: String) {
    Text(text = title, modifier = Modifier.padding(start = 24.dp, bottom = 8.dp), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Gray.copy(alpha = 0.6f), letterSpacing = 1.sp)
}

@Composable
fun MenuItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, accentColor: Color, unselectedColor: Color, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label, fontWeight = if(selected) FontWeight.Bold else FontWeight.Medium) },
        selected = selected,
        icon = { Icon(icon, null, tint = if (selected) accentColor else unselectedColor) },
        colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = accentColor.copy(alpha = 0.1f), selectedIconColor = accentColor, selectedTextColor = accentColor, unselectedIconColor = unselectedColor, unselectedTextColor = unselectedColor),
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
    )
}

@Composable
private fun SeloPerfil(texto: String, icone: androidx.compose.ui.graphics.vector.ImageVector, cor: Color) {
    Row(
        modifier = Modifier.background(cor.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icone, null, tint = cor, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(texto, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = cor, maxLines = 1)
    }
}
