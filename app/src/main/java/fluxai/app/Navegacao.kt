package fluxai.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import fluxai.app.ui.theme.LocalAccentColor
import fluxai.app.ui.theme.LocalDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// =========================================================================
// NAVEGAÇÃO E ESTRUTURA COMUM DAS TELAS NOVAS
// LocalNavegar leva a navegação a qualquer tela sem repassar um callback por rota.
// =========================================================================
val LocalNavegar = staticCompositionLocalOf<(String) -> Unit> { {} }

data class CoresTela(
    val fundo: Color,
    val superficie: Color,
    val texto: Color,
    val textoFraco: Color,
    val divisor: Color,
    val destaque: Color
)

@Composable
fun coresTela(): CoresTela {
    val escuro = LocalDarkTheme.current
    return CoresTela(
        fundo = if (escuro) Color(0xFF121212) else Color(0xFFF8F9FA),
        superficie = if (escuro) Color(0xFF1E1E1E) else Color.White,
        texto = if (escuro) Color(0xFFF9FAFB) else Color(0xFF1E1E1E),
        textoFraco = if (escuro) Color(0xFF9CA3AF) else Color(0xFF6B7280),
        divisor = if (escuro) Color(0xFF374151) else Color(0xFFE5E7EB),
        destaque = LocalAccentColor.current
    )
}

@Composable
fun coresCampoTela(c: CoresTela = coresTela()) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = c.destaque, unfocusedBorderColor = c.divisor, focusedTextColor = c.texto, unfocusedTextColor = c.texto, cursorColor = c.destaque
)

// Estrutura padrão: menu lateral, barra superior, aviso de conexão e conteúdo
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaComMenu(
    titulo: String,
    rota: String,
    onLogout: () -> Unit,
    acoes: @Composable RowScope.() -> Unit = {},
    botaoFlutuante: @Composable () -> Unit = {},
    conteudo: @Composable (PaddingValues) -> Unit
) {
    val c = coresTela()
    val escopo = rememberCoroutineScope()
    val gaveta = rememberDrawerState(DrawerValue.Closed)
    ModalNavigationDrawer(
        drawerState = gaveta,
        drawerContent = { MenuLateral(drawerState = gaveta, coroutineScope = escopo, rotaAtual = rota, onLogout = onLogout) }
    ) {
        Scaffold(
            topBar = {
                Column {
                    CenterAlignedTopAppBar(
                        title = { Text(titulo, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.texto) },
                        navigationIcon = { IconButton(onClick = { escopo.launch { gaveta.open() } }) { Icon(Icons.Default.Menu, "Abrir menu", tint = c.texto) } },
                        actions = acoes,
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = c.fundo)
                    )
                    AvisoConexao()
                }
            },
            floatingActionButton = botaoFlutuante,
            containerColor = c.fundo,
            content = conteudo
        )
    }
}

// =========================================================================
// CONEXÃO: o Firestore guarda as alterações no aparelho sem internet e envia depois.
// O aviso deixa isso claro e mostra quando a sincronização terminou.
// =========================================================================
@Composable
fun rememberConectado(): State<Boolean> {
    val context = LocalContext.current
    val gerenciador = remember { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    fun temInternet() = gerenciador.getNetworkCapabilities(gerenciador.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    val conectado = remember { mutableStateOf(temInternet()) }
    DisposableEffect(Unit) {
        val retorno = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { conectado.value = true }
            override fun onLost(network: Network) { conectado.value = temInternet() }
        }
        runCatching { gerenciador.registerDefaultNetworkCallback(retorno) }
        onDispose { runCatching { gerenciador.unregisterNetworkCallback(retorno) } }
    }
    return conectado
}

@Composable
fun AvisoConexao() {
    val conectado by rememberConectado()
    var estavaOffline by remember { mutableStateOf(false) }
    var sincronizando by remember { mutableStateOf(false) }
    var sincronizado by remember { mutableStateOf(false) }

    LaunchedEffect(conectado) {
        if (!conectado) { estavaOffline = true; sincronizado = false; return@LaunchedEffect }
        if (!estavaOffline) return@LaunchedEffect
        // Voltou a conexão: espera o Firestore enviar o que ficou pendente
        sincronizando = true
        runCatching { Firebase.firestore.waitForPendingWrites().await() }
        sincronizando = false
        sincronizado = true
        estavaOffline = false
        delay(2500)
        sincronizado = false
    }

    val (cor, icone, texto) = when {
        !conectado -> Triple(Color(0xFF757575), Icons.Default.CloudOff, "Sem internet. O que você registrar fica salvo no aparelho e sincroniza sozinho.")
        sincronizando -> Triple(Color(0xFF1E88E5), Icons.Default.CloudDone, "Conexão de volta. Sincronizando...")
        else -> Triple(Color(0xFF43A047), Icons.Default.CloudDone, "Tudo sincronizado.")
    }
    AnimatedVisibility(visible = !conectado || sincronizando || sincronizado) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                .background(cor.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (sincronizando) CircularProgressIndicator(Modifier.size(16.dp), color = cor, strokeWidth = 2.dp)
            else Icon(icone, null, tint = cor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(texto, fontSize = 12.sp, color = cor, lineHeight = 16.sp)
        }
    }
}
