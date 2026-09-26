package fluxai.app

import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fluxai.app.ui.theme.LocalDarkTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SobreScreen(
    onLogout: () -> Unit,
    onAbrirDashboard: () -> Unit,
    onAbrirLancamento: () -> Unit,
    onAbrirAnalytics: () -> Unit,
    onAbrirSettings: () -> Unit,
    onAbrirSobre: () -> Unit,
    onAbrirManutencao: () -> Unit,
    onAbrirCartoes: () -> Unit,
    onAbrirCaixinhas: () -> Unit,
    onAbrirAssinaturas: () -> Unit,
    onAbrirCelular: () -> Unit
) {
    val isDark = LocalDarkTheme.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // >>> BUSCA A VERSÃO DINÂMICA DO APP <<<
    val appVersion = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "Desconhecida"
        }
    }

    // Cores
    val colorBg = if (isDark) Color(0xFF121212) else Color(0xFFF8F9FA)
    val colorSurface = if (isDark) Color(0xFF1E1E1E) else Color.White
    val colorTextPrimary = if (isDark) Color(0xFFF9FAFB) else Color(0xFF1E1E1E)
    val colorTextSecondary = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val colorAccent = Color(0xFF04081F)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MenuLateral(
                drawerState = drawerState,
                coroutineScope = coroutineScope,
                rotaAtual = "sobre", // Pinta o botão "Sobre" de roxo no menu
                onAbrirDashboard = onAbrirDashboard,
                onAbrirLancamento = onAbrirLancamento,
                onAbrirAnalytics = onAbrirAnalytics,
                onAbrirSettings = onAbrirSettings,
                onAbrirSobre = { }, // Já estamos nela
                onLogout = onLogout,
                onAbrirManutencao = onAbrirManutencao,
                onAbrirCartoes = onAbrirCartoes,
                onAbrirCaixinhas = onAbrirCaixinhas,
                onAbrirAssinaturas = onAbrirAssinaturas,
                onAbrirCelular = onAbrirCelular
            )
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Sobre", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colorTextPrimary) },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu Lateral", tint = colorTextPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colorBg)
                )
            },
            containerColor = colorBg
        ) { padding ->
            val colorDivider = if (isDark) Color(0xFF2C2C2C) else Color(0xFFE5E7EB)
            val roxo = Color(0xFF7E57C2)
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(colorAccent), contentAlignment = Alignment.Center) {
                    Image(painter = painterResource(id = R.drawable.logo_app), contentDescription = "Logo FluxAí", modifier = Modifier.size(96.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text("FluxAí", fontSize = 24.sp, fontWeight = FontWeight.Black, color = colorTextPrimary)
                Text("Seu assistente financeiro inteligente", fontSize = 14.sp, color = colorTextSecondary)
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(50), color = roxo.copy(alpha = 0.12f)) {
                    Text("Versão $appVersion", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = roxo, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                }

                // O que o app faz
                TituloSobre("O que o FluxAí faz", colorTextSecondary)
                GrupoSobre(colorSurface, colorDivider) {
                    ItemSobre(Icons.Default.DocumentScanner, Color(0xFF7E57C2), "Leitura de notas e boletos", "Fotografe e a IA preenche o lançamento", colorTextPrimary, colorTextSecondary)
                    ItemSobre(Icons.Default.AutoAwesome, Color(0xFFE91E63), "Consultor com IA", "Análise do mês e conversa sobre suas finanças", colorTextPrimary, colorTextSecondary)
                    ItemSobre(Icons.Default.QueryStats, Color(0xFF1E88E5), "Análise preditiva", "Quanto sobra no fim do mês e quanto pode gastar por dia", colorTextPrimary, colorTextSecondary)
                    ItemSobre(Icons.Default.Groups, Color(0xFF26A69A), "Conta conjunta", "Divida o controle com quem mora com você", colorTextPrimary, colorTextSecondary)
                    ItemSobre(Icons.Default.Savings, Color(0xFF43A047), "Metas e cofre", "Guarde dinheiro para seus objetivos", colorTextPrimary, colorTextSecondary)
                    ItemSobre(Icons.Default.Widgets, Color(0xFFFB8C00), "Widget na tela inicial", "Resumo do mês sem abrir o app", colorTextPrimary, colorTextSecondary)
                }

                // Privacidade
                TituloSobre("Privacidade e segurança", colorTextSecondary)
                GrupoSobre(colorSurface, colorDivider) {
                    ItemSobre(Icons.Default.Lock, Color(0xFF5C6BC0), "Seus dados são só seus", "Só você e quem você convidar acessam sua conta", colorTextPrimary, colorTextSecondary)
                    ItemSobre(Icons.Default.Fingerprint, Color(0xFF5C6BC0), "Biometria ao abrir", "Digital, rosto ou senha do celular", colorTextPrimary, colorTextSecondary)
                    ItemSobre(Icons.Default.DeleteForever, Color(0xFFE53935), "Exclusão total (LGPD)", "Em Configurações, apague a conta e todos os dados", colorTextPrimary, colorTextSecondary)
                }

                // Créditos
                Spacer(Modifier.height(24.dp))
                Text("Desenvolvido por", fontSize = 12.sp, color = colorTextSecondary)
                Text("Rodritech", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colorTextPrimary)
                Text("Desenvolvimento de Soluções em Informática", fontSize = 12.sp, color = colorTextSecondary, textAlign = TextAlign.Center)
                Text("Hudson Rodrigues", fontSize = 12.sp, color = colorTextSecondary, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(8.dp))
                Text("© ${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)} Todos os direitos reservados.", fontSize = 11.sp, color = colorTextSecondary.copy(alpha = 0.7f))
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun TituloSobre(texto: String, cor: Color) {
    Text(texto.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cor, letterSpacing = 1.sp, modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 24.dp, bottom = 8.dp))
}

@Composable
private fun GrupoSobre(fundo: Color, borda: Color, conteudo: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = fundo, border = androidx.compose.foundation.BorderStroke(1.dp, borda), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 4.dp), content = conteudo)
    }
}

@Composable
private fun ItemSobre(icone: androidx.compose.ui.graphics.vector.ImageVector, cor: Color, titulo: String, descricao: String, corTitulo: Color, corDescricao: Color) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(cor.copy(alpha = 0.14f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(icone, null, tint = cor, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(titulo, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = corTitulo)
            Text(descricao, fontSize = 12.sp, color = corDescricao)
        }
    }
}
