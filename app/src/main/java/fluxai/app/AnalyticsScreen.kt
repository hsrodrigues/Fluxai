package fluxai.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onAbrirDashboard: () -> Unit,
    onAbrirLancamento: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val usuario = Firebase.auth.currentUser
    val banco = Firebase.firestore
    val coroutineScope = rememberCoroutineScope()

    // Controle do Menu Lateral
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var calendar by remember { mutableStateOf(Calendar.getInstance()) }
    var mesAnoSelecionado by remember { mutableStateOf(SimpleDateFormat("MM/yyyy", Locale("pt", "BR")).format(calendar.time)) }
    var mesNome by remember { mutableStateOf(SimpleDateFormat("MMMM yyyy", Locale("pt", "BR")).format(calendar.time).replaceFirstChar { it.uppercase() }) }

    var despesas by remember { mutableStateOf<List<Despesa>>(emptyList()) }
    var carregando by remember { mutableStateOf(true) }

    // Cores do Tema Clean
    val colorAccent = Color(0xFF7E57C2)
    val colorBg = Color(0xFFF8F9FA)
    val colorSurface = Color.White
    val colorTextPrimary = Color(0xFF1E1E1E)
    val colorTextSecondary = Color(0xFF6B7280)
    val colorDarkBg = Color(0xFF1E1E1E)

    LaunchedEffect(mesAnoSelecionado) {
        carregando = true
        if (usuario != null) {
            banco.collection("usuarios").document(usuario.uid).collection("despesas")
                .whereEqualTo("mesAno", mesAnoSelecionado).get()
                .addOnSuccessListener { snapshot ->
                    despesas = snapshot.documents.map { doc ->
                        Despesa(
                            descricao = doc.getString("descricao") ?: "",
                            valor = doc.getDouble("valor") ?: 0.0,
                            tipo = doc.getString("tipo") ?: "Variável",
                            categoria = doc.getString("categoria") ?: "Outros",
                            status = doc.getString("status") ?: "A pagar"
                        )
                    }
                    carregando = false
                }
        }
    }

    // PROCESSAMENTO DE DADOS PARA OS GRÁFICOS (BI)
    val totalGeral = despesas.sumOf { it.valor }

    // 1. Categorias (Donut)
    val totaisPorCategoria = despesas.groupBy { it.categoria }.mapValues { entry -> entry.value.sumOf { it.valor } }.toList().sortedByDescending { it.second }
    val coresCategorias = listOf(Color(0xFF7E57C2), Color(0xFF26A69A), Color(0xFFFF7043), Color(0xFF42A5F5), Color(0xFFFFEE58), Color(0xFFEC407A), Color(0xFF9E9E9E))

    // 2. Fixa vs Variável
    val totalFixa = despesas.filter { it.tipo == "Fixa" }.sumOf { it.valor }
    val totalVariavel = despesas.filter { it.tipo == "Variável" }.sumOf { it.valor }
    val percFixa = if (totalGeral > 0) (totalFixa / totalGeral).toFloat() else 0f

    // 3. Status Pagamento
    val totalPago = despesas.filter { it.status == "Pago" }.sumOf { it.valor }
    val totalPendente = totalGeral - totalPago
    val percPago = if (totalGeral > 0) (totalPago / totalGeral).toFloat() else 0f

    // 4. Top 3 Gastos
    val top3Despesas = despesas.sortedByDescending { it.valor }.take(3)
    val maxGasto = top3Despesas.firstOrNull()?.valor ?: 1.0

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(280.dp), drawerContainerColor = colorSurface) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp).background(colorBg), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.size(90.dp).background(colorDarkBg, CircleShape), contentAlignment = Alignment.Center) {
                            Image(painter = painterResource(id = R.drawable.logo_app), contentDescription = "Logo FluxAí", modifier = Modifier.size(60.dp).clip(CircleShape))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("FLUXAÍ", color = colorAccent, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                }

                HorizontalDivider(color = Color(0xFFE5E7EB))
                Spacer(modifier = Modifier.height(12.dp))

                NavigationDrawerItem(label = { Text("Meu Dashboard", fontWeight = FontWeight.Medium) }, selected = false, icon = { Icon(Icons.Default.Dashboard, null, tint = colorTextSecondary) }, onClick = { coroutineScope.launch { drawerState.close() }; onAbrirDashboard() }, modifier = Modifier.padding(horizontal = 12.dp))
                Spacer(modifier = Modifier.height(8.dp))
                NavigationDrawerItem(label = { Text("Novo Lançamento", fontWeight = FontWeight.Medium) }, selected = false, icon = { Icon(Icons.Default.AddCircle, null, tint = colorTextSecondary) }, onClick = { coroutineScope.launch { drawerState.close() }; onAbrirLancamento() }, modifier = Modifier.padding(horizontal = 12.dp))
                Spacer(modifier = Modifier.height(8.dp))
                NavigationDrawerItem(label = { Text("Análise BI", fontWeight = FontWeight.Medium) }, selected = true, icon = { Icon(Icons.Default.PieChart, null) }, colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = colorAccent.copy(alpha = 0.1f), selectedIconColor = colorAccent, selectedTextColor = colorAccent), onClick = { coroutineScope.launch { drawerState.close() } }, modifier = Modifier.padding(horizontal = 12.dp))

                Spacer(modifier = Modifier.weight(1f))

                NavigationDrawerItem(label = { Text("Sair da Conta", fontWeight = FontWeight.Medium) }, selected = false, icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) }, colors = NavigationDrawerItemDefaults.colors(unselectedIconColor = Color(0xFFEF5350), unselectedTextColor = Color(0xFFEF5350)), onClick = { coroutineScope.launch { drawerState.close(); Firebase.auth.signOut(); onLogout() } }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp))
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Business Intelligence", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colorTextPrimary) },
                    // Alterado de Seta Voltar para Menu Sanduíche
                    navigationIcon = { IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, "Menu Lateral", tint = colorTextPrimary) } },
                    actions = { IconButton(onClick = { exportarDespesasParaCSV(context, despesas, mesAnoSelecionado) }) { Icon(Icons.Default.Download, "Exportar CSV", tint = colorAccent) } },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colorBg)
                )
            },
            containerColor = colorBg
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {

                // NAVEGADOR DE MÊS
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = colorSurface), elevation = CardDefaults.cardElevation(0.dp)) {
                    Row(modifier = Modifier.padding(8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        IconButton(onClick = { calendar.add(Calendar.MONTH, -1); mesAnoSelecionado = SimpleDateFormat("MM/yyyy", Locale("pt", "BR")).format(calendar.time); mesNome = SimpleDateFormat("MMMM yyyy", Locale("pt", "BR")).format(calendar.time).replaceFirstChar { it.uppercase() } }) { Icon(Icons.Default.KeyboardArrowLeft, null, tint = colorTextSecondary) }
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CalendarMonth, null, tint = colorAccent, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.width(8.dp)); Text(mesNome, fontWeight = FontWeight.Bold, color = colorTextPrimary) }
                        IconButton(onClick = { calendar.add(Calendar.MONTH, 1); mesAnoSelecionado = SimpleDateFormat("MM/yyyy", Locale("pt", "BR")).format(calendar.time); mesNome = SimpleDateFormat("MMMM yyyy", Locale("pt", "BR")).format(calendar.time).replaceFirstChar { it.uppercase() } }) { Icon(Icons.Default.KeyboardArrowRight, null, tint = colorTextSecondary) }
                    }
                }

                if (carregando) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = colorAccent) }
                } else if (despesas.isEmpty()) {
                    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.PieChartOutline, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Sem dados analíticos para $mesAnoSelecionado", color = colorTextSecondary, fontSize = 16.sp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                        // 1. GRÁFICO PRINCIPAL: COMPOSIÇÃO DE GASTOS
                        item {
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = colorSurface), elevation = CardDefaults.cardElevation(0.dp)) {
                                Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Composição de Gastos", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colorTextPrimary)
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
                                        Canvas(modifier = Modifier.size(180.dp)) {
                                            var startAngle = -90f
                                            totaisPorCategoria.forEachIndexed { index, pair ->
                                                val sweepAngle = if (totalGeral > 0) (pair.second.toFloat() / totalGeral.toFloat()) * 360f else 0f
                                                drawArc(color = coresCategorias.getOrElse(index) { Color.LightGray }, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = 25.dp.toPx(), cap = StrokeCap.Round))
                                                startAngle += sweepAngle
                                            }
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Total", fontSize = 12.sp, color = colorTextSecondary); Text("R$ %.2f".format(totalGeral), fontSize = 18.sp, fontWeight = FontWeight.Black, color = colorTextPrimary) }
                                    }
                                }
                            }
                        }

                        // 2. INDICADOR: FIXA VS VARIÁVEL
                        item {
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = colorSurface), elevation = CardDefaults.cardElevation(0.dp)) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text("Natureza das Despesas", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colorTextPrimary)
                                    Spacer(modifier = Modifier.height(16.dp))

                                    LinearProgressIndicator(
                                        progress = { percFixa },
                                        modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                                        color = colorAccent, // Fixas
                                        trackColor = Color(0xFFFFB74D) // Variáveis
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column { Text("Fixas (${"%.0f".format(percFixa * 100)}%)", fontSize = 12.sp, color = colorTextSecondary); Text("R$ %.2f".format(totalFixa), fontWeight = FontWeight.Bold, color = colorAccent) }
                                        Column(horizontalAlignment = Alignment.End) { Text("Variáveis (${"%.0f".format((1f-percFixa) * 100)}%)", fontSize = 12.sp, color = colorTextSecondary); Text("R$ %.2f".format(totalVariavel), fontWeight = FontWeight.Bold, color = Color(0xFFF57C00)) }
                                    }
                                }
                            }
                        }

                        // 3. INDICADOR: STATUS DE PAGAMENTO
                        item {
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = colorSurface), elevation = CardDefaults.cardElevation(0.dp)) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text("Progresso de Pagamentos", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colorTextPrimary)
                                    Spacer(modifier = Modifier.height(16.dp))

                                    LinearProgressIndicator(
                                        progress = { percPago },
                                        modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                                        color = Color(0xFF4CAF50), // Pago
                                        trackColor = Color(0xFFEEEEEE) // Pendente
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column { Text("Pago (${"%.0f".format(percPago * 100)}%)", fontSize = 12.sp, color = colorTextSecondary); Text("R$ %.2f".format(totalPago), fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32)) }
                                        Column(horizontalAlignment = Alignment.End) { Text("Pendente", fontSize = 12.sp, color = colorTextSecondary); Text("R$ %.2f".format(totalPendente), fontWeight = FontWeight.Bold, color = colorTextPrimary) }
                                    }
                                }
                            }
                        }

                        // 4. RANKING: TOP 3 GASTOS
                        item {
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = colorSurface), elevation = CardDefaults.cardElevation(0.dp)) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("Top 3 Maiores Gastos", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colorTextPrimary)
                                        Icon(Icons.Default.TrendingUp, null, tint = Color(0xFFEF5350))
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))

                                    top3Despesas.forEachIndexed { index, despesa ->
                                        val relProgress = if (maxGasto > 0) (despesa.valor / maxGasto).toFloat() else 0f
                                        Column(modifier = Modifier.padding(bottom = 12.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("${index + 1}. ${despesa.descricao}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colorTextPrimary, maxLines = 1)
                                                Text("R$ %.2f".format(despesa.valor), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colorTextPrimary)
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            LinearProgressIndicator(
                                                progress = { relProgress },
                                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                                color = Color(0xFFEF5350).copy(alpha = 1f - (index * 0.2f)), // Vai clareando
                                                trackColor = Color(0xFFF3F4F6)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 5. DETALHAMENTO DE CATEGORIAS
                        item { Text("Detalhamento por Categoria", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colorTextSecondary, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) }

                        items(totaisPorCategoria) { item ->
                            val index = totaisPorCategoria.indexOf(item)
                            val percentual = if (totalGeral > 0) (item.second / totalGeral) * 100 else 0.0
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = colorSurface), elevation = CardDefaults.cardElevation(0.dp)) {
                                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(12.dp).background(coresCategorias.getOrElse(index) { Color.Gray }, CircleShape))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column { Text(item.first, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colorTextPrimary); Text("%.1f%% do mês".format(percentual), fontSize = 12.sp, color = colorTextSecondary) }
                                    }
                                    Text(text = "R$ %.2f".format(item.second), fontWeight = FontWeight.ExtraBold, color = colorTextPrimary, fontSize = 14.sp)
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}