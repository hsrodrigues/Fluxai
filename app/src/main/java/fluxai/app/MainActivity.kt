package fluxai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()

            // A startDestination agora é a "splash"!
            NavHost(navController = navController, startDestination = "splash") {

                // 1. TELA DE SPLASH (NOVA)
                composable("splash") {
                    SplashScreen(
                        onTimeout = {
                            // Após os 2 segundos, vai para o login e apaga a splash do histórico
                            navController.navigate("login") {
                                popUpTo("splash") { inclusive = true }
                            }
                        }
                    )
                }

                // 2. TELA DE LOGIN
                composable("login") {
                    LoginScreen(onLoginSuccess = {
                        navController.navigate("dashboard") {
                            popUpTo("login") { inclusive = true }
                        }
                    })
                }

                // 3. TELA PRINCIPAL (DASHBOARD)
                composable("dashboard") {
                    DashboardScreen(
                        onLogout = {
                            navController.navigate("login") {
                                popUpTo(0)
                            }
                        },
                        onAbrirAnalytics = { navController.navigate("analytics") },
                        onAbrirLancamento = { navController.navigate("home") }
                    )
                }

                // 4. TELA DE NOVO LANÇAMENTO (HOME)
                composable("home") {
                    HomeScreen(
                        onAbrirDashboard = { navController.popBackStack() },
                        onAbrirAnalytics = { navController.navigate("analytics") },
                        onLogout = {
                            navController.navigate("login") { popUpTo(0) }
                        }
                    )
                }

                // 5. TELA DE GRÁFICOS (ANALYTICS)
                composable("analytics") {
                    AnalyticsScreen(
                        onAbrirDashboard = { navController.popBackStack() },
                        onAbrirLancamento = { navController.navigate("home") },
                        onLogout = {
                            navController.navigate("login") { popUpTo(0) }
                        }
                    )
                }
            }
        }
    }
}