package fluxai.app

import androidx.navigation.compose.currentBackStackEntryAsState
import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import fluxai.app.ui.theme.LocalDarkTheme
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

// Import do seu Tema e da Cor Personalizada Global
import fluxai.app.ui.theme.FluxaiTheme
import fluxai.app.ui.theme.LocalAccentColor

class MainActivity : FragmentActivity() {
    companion object {
        // Pedido vindo do widget para abrir uma tela específica
        const val EXTRA_ABRIR = "abrir"
        const val ABRIR_LANCAMENTO = "lancamento"
    }

    private var destinoPendente by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        destinoPendente = intent.getStringExtra(EXTRA_ABRIR)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        destinoPendente = intent?.getStringExtra(EXTRA_ABRIR)

        // 1. PEDIR PERMISSÃO (Obrigatório para Android 13, 14 e 15)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        // 2. AGENDAMENTO NORMAL (A cada 24h)
        agendarAlertasVencimento()
        agendarRoboManutencao()

        setContent {
            val context = LocalContext.current
            val sharedPref = remember {
                context.getSharedPreferences(
                    "AppPrefs",
                    android.content.Context.MODE_PRIVATE
                )
            }
            val isSystemDark = isSystemInDarkTheme()

            var isDarkState by remember {
                mutableStateOf(sharedPref.getBoolean("is_dark_mode", isSystemDark))
            }

            // A GRANDE SACADA: Se não tem usuário logado, já libera a interface para a tela de Login!
            val usuarioAtual = Firebase.auth.currentUser
            var estaAutenticado by remember { mutableStateOf(usuarioAtual == null) }

            // CHAMA A BIOMETRIA LOGO QUE O APP ABRE (Somente se já estiver logado)
            LaunchedEffect(Unit) {
                if (usuarioAtual != null) {
                    val biometricManager = BiometricManager.from(this@MainActivity)

                    // Permite Digital/Facial (STRONG) OU o PIN/Senha da tela de bloqueio do celular
                    val autenticadores = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

                    when (biometricManager.canAuthenticate(autenticadores)) {
                        BiometricManager.BIOMETRIC_SUCCESS -> {
                            verificarBiometria { sucesso -> estaAutenticado = sucesso }
                        }
                        else -> {
                            // Se não houver sensor ou senha configurados no aparelho, libera o app
                            estaAutenticado = true
                        }
                    }
                }
            }

            DisposableEffect(isDarkState) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDarkState) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (isDarkState) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                )
                onDispose { }
            }

            FluxaiTheme(
                darkTheme = isDarkState,
                onThemeToggle = {
                    val novoEstado = !isDarkState
                    isDarkState = novoEstado
                    sharedPref.edit { putBoolean("is_dark_mode", novoEstado) }
                }
            ) {
                val accentColor = LocalAccentColor.current

                // TRAVA DE SEGURANÇA
                if (estaAutenticado) {
                    FluxaiApp()
                } else {
                    // Tela de bloqueio é toda escura
                    val view = LocalView.current
                    SideEffect { ajustarIconesBarras(view, topoEscuro = true, rodapeEscuro = true) }
                    TelaBloqueio(
                        nomeUsuario = Firebase.auth.currentUser?.displayName?.split(" ")?.firstOrNull(),
                        corDestaque = accentColor,
                        onDesbloquear = { verificarBiometria { sucesso -> estaAutenticado = sucesso } }
                    )
                }
            }
        }
    }

    // Ícones da barra de status e de navegação: claros quando o fundo embaixo deles é escuro
    private fun ajustarIconesBarras(view: android.view.View, topoEscuro: Boolean, rodapeEscuro: Boolean) {
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !topoEscuro
            isAppearanceLightNavigationBars = !rodapeEscuro
        }
    }

    // FUNÇÃO QUE PROCESSA A BIOMETRIA NATIVA
    private fun verificarBiometria(onResult: (Boolean) -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onResult(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onResult(false)
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("FluxAí Seguro")
            .setSubtitle("Acesse seus dados com segurança")
            // Usando DEVICE_CREDENTIAL, o Android automaticamente injeta o botão de usar o PIN/Padrão
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .setConfirmationRequired(false) // Entra direto se reconhecer o rosto
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun agendarAlertasVencimento() {
        val context = applicationContext

        val agora = java.util.Calendar.getInstance()
        val oitoDaManha = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 8)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
        }

        if (oitoDaManha.before(agora)) {
            oitoDaManha.add(java.util.Calendar.DAY_OF_MONTH, 1)
        }

        val atrasoInicial = oitoDaManha.timeInMillis - agora.timeInMillis

        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()

        val tarefaPeriodica = androidx.work.PeriodicWorkRequestBuilder<AlertaVencimentoWorker>(
            24, java.util.concurrent.TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInitialDelay(
                atrasoInicial,
                java.util.concurrent.TimeUnit.MILLISECONDS
            )
            .addTag("fluxai_alerta_tag")
            .build()

        androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "AlertaVencimentoWork",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            tarefaPeriodica
        )
    }

    private fun agendarRoboManutencao() {
        val context = applicationContext

        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()

        // O robô vai checar silenciosamente a cada 24 horas
        val tarefa = androidx.work.PeriodicWorkRequestBuilder<ManutencaoWorker>(
            24, java.util.concurrent.TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "RoboManutencao",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            tarefa
        )
    }

    @Composable
    fun FluxaiApp() {
        val navController = rememberNavController()

        val sairDoApp = {
            Firebase.auth.signOut()
            // Esquece a conta conjunta em uso: senão a próxima conta logada neste aparelho tentaria abrir os dados da anterior
            getSharedPreferences("AppPrefs", MODE_PRIVATE).edit { remove("workspace_uid"); remove("workspace_nome") }
            navController.navigate("login") {
                popUpTo(0) { inclusive = true }
            }
        }

        val navegarPara = { rota: String ->
            navController.navigate(rota) {
                launchSingleTop = true
                restoreState = true
            }
        }

        // Abre o Novo Lançamento quando o pedido vem do widget (só depois de sair do splash/login)
        val rotaAtual = navController.currentBackStackEntryAsState().value?.destination?.route

        // Ícones das barras do sistema contrastando com a tela: brancos sobre fundo escuro, pretos sobre fundo claro.
        // Splash, login e cadastro têm o topo escuro da marca mesmo no tema claro (rota nula = splash abrindo).
        val temaEscuro = LocalDarkTheme.current
        val view = LocalView.current
        val topoEscuro = temaEscuro || rotaAtual == null || rotaAtual in setOf("splash", "login", "register")
        val rodapeEscuro = temaEscuro || rotaAtual == null || rotaAtual == "splash"
        SideEffect { ajustarIconesBarras(view, topoEscuro, rodapeEscuro) }
        LaunchedEffect(destinoPendente, rotaAtual) {
            if (destinoPendente == ABRIR_LANCAMENTO && rotaAtual != null && rotaAtual !in setOf("splash", "login", "register")) {
                destinoPendente = null
                navegarPara("home")
            }
        }

        NavHost(navController = navController, startDestination = "splash") {

            composable("splash") {
                SplashScreen(
                    onTimeout = {
                        val destino =
                            if (Firebase.auth.currentUser == null) "login" else "dashboard"
                        navController.navigate(destino) { popUpTo("splash") { inclusive = true } }
                    }
                )
            }

            composable("login") {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate("dashboard") {
                            popUpTo("login") {
                                inclusive = true
                            }
                        }
                    },
                    onNavigateToRegister = { navController.navigate("register") }
                )
            }

            composable("register") {
                RegisterScreen(
                    onRegisterSuccess = {
                        navController.navigate("dashboard") {
                            popUpTo("login") { inclusive = true }
                        }
                    },
                    onBackToLogin = { navController.popBackStack() }
                )
            }

            composable("dashboard") {
                DashboardScreen(
                    onLogout = sairDoApp,
                    onAbrirDashboard = { navegarPara("dashboard") },
                    onAbrirLancamento = { navegarPara("home") },
                    onAbrirAnalytics = { navegarPara("analytics") },
                    onAbrirSettings = { navegarPara("settings") },
                    onAbrirSobre = { navegarPara("sobre") },
                    onAbrirManutencao = { navegarPara("manutencao") },
                    onAbrirCartoes = { navegarPara("cartoes") },
                    onAbrirCaixinhas = { navegarPara("caixinhas") },
                    onAbrirAssinaturas = { navegarPara("assinaturas") },
                    onAbrirCelular = { navegarPara("celular") }
                )
            }

            composable("home") {
                HomeScreen(
                    onLogout = sairDoApp,
                    onAbrirDashboard = { navegarPara("dashboard") },
                    onAbrirLancamento = { navegarPara("home") },
                    onAbrirAnalytics = { navegarPara("analytics") },
                    onAbrirSettings = { navegarPara("settings") },
                    onAbrirSobre = { navegarPara("sobre") },
                    onAbrirManutencao = { navegarPara("manutencao") },
                    onAbrirCartoes = { navegarPara("cartoes") },
                    onAbrirCaixinhas = { navegarPara("caixinhas") },
                    onAbrirAssinaturas = { navegarPara("assinaturas") },
                    onAbrirCelular = { navegarPara("celular") }
                )
            }

            composable("analytics") {
                AnalyticsScreen(
                    onLogout = sairDoApp,
                    onAbrirDashboard = { navegarPara("dashboard") },
                    onAbrirLancamento = { navegarPara("home") },
                    onAbrirAnalytics = { navegarPara("analytics") },
                    onAbrirSettings = { navegarPara("settings") },
                    onAbrirSobre = { navegarPara("sobre") },
                    onAbrirManutencao = { navegarPara("manutencao") },
                    onAbrirCartoes = { navegarPara("cartoes") },
                    onAbrirCaixinhas = { navegarPara("caixinhas") },
                    onAbrirAssinaturas = { navegarPara("assinaturas") },
                    onAbrirCelular = { navegarPara("celular") }
                )
            }

            composable("settings") {
                SettingsScreen(
                    onLogout = sairDoApp,
                    onAccountDeleted = sairDoApp,
                    onAbrirDashboard = { navegarPara("dashboard") },
                    onAbrirLancamento = { navegarPara("home") },
                    onAbrirAnalytics = { navegarPara("analytics") },
                    onAbrirSettings = { navegarPara("settings") },
                    onAbrirSobre = { navegarPara("sobre") },
                    onAbrirManutencao = { navegarPara("manutencao") },
                    onAbrirCartoes = { navegarPara("cartoes") },
                    onAbrirCaixinhas = { navegarPara("caixinhas") },
                    onAbrirAssinaturas = { navegarPara("assinaturas") },
                    onAbrirCelular = { navegarPara("celular") }
                )
            }

            composable("sobre") {
                SobreScreen(
                    onLogout = sairDoApp,
                    onAbrirDashboard = { navegarPara("dashboard") },
                    onAbrirLancamento = { navegarPara("home") },
                    onAbrirAnalytics = { navegarPara("analytics") },
                    onAbrirSettings = { navegarPara("settings") },
                    onAbrirSobre = { navegarPara("sobre") },
                    onAbrirManutencao = { navegarPara("manutencao") },
                    onAbrirCartoes = { navegarPara("cartoes") },
                    onAbrirCaixinhas = { navegarPara("caixinhas") },
                    onAbrirAssinaturas = { navegarPara("assinaturas") },
                    onAbrirCelular = { navegarPara("celular") }
                )
            }

            composable("manutencao") {
                AtivosScreen(
                    onLogout = sairDoApp,
                    onAbrirDashboard = { navegarPara("dashboard") },
                    onAbrirLancamento = { navegarPara("home") },
                    onAbrirAnalytics = { navegarPara("analytics") },
                    onAbrirSettings = { navegarPara("settings") },
                    onAbrirSobre = { navegarPara("sobre") },
                    onAbrirManutencao = { navegarPara("manutencao") },
                    onAbrirCartoes = { navegarPara("cartoes") },
                    onAbrirCaixinhas = { navegarPara("caixinhas") },
                    onAbrirAssinaturas = { navegarPara("assinaturas") },
                    onAbrirCelular = { navegarPara("celular") }
                )
            }

            composable("cartoes") {
                CartoesScreen(
                    onLogout = sairDoApp,
                    onAbrirDashboard = { navegarPara("dashboard") },
                    onAbrirLancamento = { navegarPara("home") },
                    onAbrirAnalytics = { navegarPara("analytics") },
                    onAbrirSettings = { navegarPara("settings") },
                    onAbrirSobre = { navegarPara("sobre") },
                    onAbrirManutencao = { navegarPara("manutencao") },
                    onAbrirCartoes = { navegarPara("cartoes") },
                    onAbrirCaixinhas = { navegarPara("caixinhas") },
                    onAbrirAssinaturas = { navegarPara("assinaturas") },
                    onAbrirCelular = { navegarPara("celular") }
                )
            }

            composable("caixinhas") {
                CaixinhasScreen(
                    onLogout = sairDoApp,
                    onAbrirDashboard = { navegarPara("dashboard") },
                    onAbrirLancamento = { navegarPara("home") },
                    onAbrirAnalytics = { navegarPara("analytics") },
                    onAbrirSettings = { navegarPara("settings") },
                    onAbrirSobre = { navegarPara("sobre") },
                    onAbrirManutencao = { navegarPara("manutencao") },
                    onAbrirCartoes = { navegarPara("cartoes") },
                    onAbrirCaixinhas = { navegarPara("caixinhas") },
                    onAbrirAssinaturas = { navegarPara("assinaturas") },
                    onAbrirCelular = { navegarPara("celular") }
                )
            }

            composable("assinaturas") {
                AssinaturasScreen(
                    onLogout = sairDoApp,
                    onAbrirDashboard = { navegarPara("dashboard") },
                    onAbrirLancamento = { navegarPara("home") },
                    onAbrirAnalytics = { navegarPara("analytics") },
                    onAbrirSettings = { navegarPara("settings") },
                    onAbrirSobre = { navegarPara("sobre") },
                    onAbrirManutencao = { navegarPara("manutencao") },
                    onAbrirCartoes = { navegarPara("cartoes") },
                    onAbrirCaixinhas = { navegarPara("caixinhas") },
                    onAbrirAssinaturas = { navegarPara("assinaturas") },
                    onAbrirCelular = { navegarPara("celular") }
                )
            }

            composable("celular") {
                ContasCelularScreen(
                    onLogout = sairDoApp,
                    onAbrirDashboard = { navegarPara("dashboard") },
                    onAbrirLancamento = { navegarPara("home") },
                    onAbrirAnalytics = { navegarPara("analytics") },
                    onAbrirSettings = { navegarPara("settings") },
                    onAbrirSobre = { navegarPara("sobre") },
                    onAbrirManutencao = { navegarPara("manutencao") },
                    onAbrirCartoes = { navegarPara("cartoes") },
                    onAbrirCaixinhas = { navegarPara("caixinhas") },
                    onAbrirAssinaturas = { navegarPara("assinaturas") },
                    onAbrirCelular = { navegarPara("celular") }
                )
            }
        }
    }
}