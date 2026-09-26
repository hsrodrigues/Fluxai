package fluxai.app

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import fluxai.app.ui.theme.LocalAccentColor
import fluxai.app.ui.theme.LocalDarkTheme
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AnalyticsScreen(
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
    val context = LocalContext.current
    val usuario = Firebase.auth.currentUser
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // =========================================================================
    // === INJETADO: CHAVE MESTRA DA SINCRONIZAÇÃO FAMILIAR ===
    // =========================================================================
    val sharedPref = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
    val workspaceUid = sharedPref.getString("workspace_uid", usuario?.uid ?: "") ?: usuario?.uid ?: ""
    // =========================================================================

    var calendar by remember { mutableStateOf(Calendar.getInstance()) }
    var mesAnoSelecionado by remember { mutableStateOf(SimpleDateFormat("MM/yyyy", Locale.forLanguageTag("pt-BR")).format(calendar.time)) }
    var mesNome by remember { mutableStateOf(SimpleDateFormat("MMMM yyyy", Locale.forLanguageTag("pt-BR")).format(calendar.time).replaceFirstChar { it.uppercase() }) }


    // ESTADOS DE LIMITES E PROJETOS
    var mostrarDialogLimite by remember { mutableStateOf(false) }
    var categoriaParaLimite by remember { mutableStateOf("") }
    var valorNovoLimite by remember { mutableStateOf("") }

    // =========================================================================
    // === INJETADO: Lista de Projetos para Analytics Isolado ===
    // =========================================================================

    // =========================================================================
    // === INJETADO: Variável para puxar a Renda Total do Mês ===
    // =========================================================================

    // CORES E TEMA
    val colorAccent = LocalAccentColor.current
    val isDark = LocalDarkTheme.current
    val colorBg = if (isDark) Color(0xFF121212) else Color(0xFFF8F9FA)
    val colorSurface = if (isDark) Color(0xFF1E1E1E) else Color.White
    val colorTextPrimary = if (isDark) Color.White else Color(0xFF1E1E1E)
    val colorTextSecondary = Color.Gray
    val progressTrackBg = if (isDark) Color(0xFF333333) else Color(0xFFF3F4F6)
    val colorDivider = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB)

    fun atualizarData() {
        mesAnoSelecionado = SimpleDateFormat("MM/yyyy", Locale.forLanguageTag("pt-BR")).format(calendar.time)
        mesNome = SimpleDateFormat("MMMM yyyy", Locale.forLanguageTag("pt-BR")).format(calendar.time).replaceFirstChar { it.uppercase() }
    }

    // Dados vêm do ViewModel (sobrevive a rotação; ouvintes desligados em onCleared)
    val vm: AnaliseViewModel = viewModel()
    LaunchedEffect(mesAnoSelecionado, workspaceUid) { vm.observar(workspaceUid, mesAnoSelecionado, calendar) }
    val despesas by vm.despesas.collectAsStateWithLifecycle()
    val carregando by vm.carregando.collectAsStateWithLifecycle()
    val rendaTotalMes by vm.renda.collectAsStateWithLifecycle()
    val limites by vm.limites.collectAsStateWithLifecycle()
    val projetosList by vm.projetos.collectAsStateWithLifecycle()
    val historico by vm.historico.collectAsStateWithLifecycle()

    // =========================================================================
    // CÁLCULOS (só refeitos quando os dados mudam). Projetos ficam fora do custo de vida.
    // =========================================================================
    var filtros by remember { mutableStateOf(FiltrosBI()) }
    var mostrarFiltros by remember { mutableStateOf(false) }
    val despesasFiltradas = remember(despesas, filtros) { despesas.filter { filtros.aceita(it) } }
    val bi = remember(despesasFiltradas, rendaTotalMes) { calcularBI(despesasFiltradas, rendaTotalMes) }
    val mesAnterior = historico.getOrNull(historico.size - 2)
    val variacaoDespesas = mesAnterior?.takeIf { it.despesas > 0 }?.let { (bi.totalGeral - it.despesas) / it.despesas * 100 }
    val moeda = remember { java.text.NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    fun brl(v: Double) = moeda.format(v)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MenuLateral(
                drawerState = drawerState,
                coroutineScope = coroutineScope,
                rotaAtual = "analytics",
                onAbrirDashboard = onAbrirDashboard,
                onAbrirLancamento = onAbrirLancamento,
                onAbrirAnalytics = { },
                onAbrirSettings = onAbrirSettings,
                onAbrirSobre = onAbrirSobre,
                onLogout = onLogout,
                onAbrirManutencao = onAbrirManutencao,
                onAbrirCaixinhas = onAbrirCaixinhas,
                onAbrirCartoes = onAbrirCartoes,
                onAbrirAssinaturas = onAbrirAssinaturas,
                onAbrirCelular = onAbrirCelular
            )
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Análise BI", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colorTextPrimary) },
                    navigationIcon = { IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, null, tint = colorTextPrimary) } },
                    actions = {
                        IconButton(onClick = { mostrarFiltros = true }) {
                            BadgedBox(badge = { if (filtros.quantidade > 0) Badge(containerColor = colorAccent) { Text("${filtros.quantidade}", color = Color.White) } }) {
                                Icon(Icons.Default.Tune, "Filtros", tint = if (filtros.quantidade > 0) colorAccent else colorTextPrimary)
                            }
                        }
                        IconButton(onClick = { exportarDespesasParaCSV(context, despesasFiltradas, mesAnoSelecionado) }) {
                            Icon(Icons.Default.IosShare, "Exportar CSV", tint = colorAccent)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colorBg)
                )
            },
            containerColor = colorBg
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {

                // SELETOR DE MÊS (compacto)
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), Arrangement.Center, Alignment.CenterVertically) {
                    IconButton(onClick = { calendar.add(Calendar.MONTH, -1); atualizarData() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mês anterior", tint = colorTextPrimary) }
                    Surface(shape = RoundedCornerShape(50), color = colorSurface, border = BorderStroke(1.dp, colorDivider)) {
                        Text(mesNome, fontWeight = FontWeight.SemiBold, color = colorTextPrimary, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                    }
                    IconButton(onClick = { calendar.add(Calendar.MONTH, 1); atualizarData() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Próximo mês", tint = colorTextPrimary) }
                }

                if (filtros.quantidade > 0) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        filtros.etiquetas().forEach { (texto, limpar) ->
                            InputChip(
                                selected = true, onClick = { filtros = limpar(filtros) },
                                label = { Text(texto, fontSize = 12.sp) },
                                trailingIcon = { Icon(Icons.Default.Close, "Remover filtro", Modifier.size(14.dp)) },
                                colors = InputChipDefaults.inputChipColors(selectedContainerColor = colorAccent.copy(alpha = 0.12f), selectedLabelColor = colorAccent, selectedTrailingIconColor = colorAccent),
                                border = null
                            )
                        }
                        TextButton(onClick = { filtros = FiltrosBI() }) { Text("Limpar", color = colorTextSecondary, fontSize = 12.sp) }
                    }
                }

                if (carregando) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = colorAccent) }
                } else if (despesas.isNotEmpty() && despesasFiltradas.isEmpty()) {
                    Column(Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.FilterAltOff, null, modifier = Modifier.size(48.dp), tint = colorTextSecondary)
                        Spacer(Modifier.height(12.dp))
                        Text("Nenhum lançamento com esses filtros", fontWeight = FontWeight.Bold, color = colorTextPrimary)
                        TextButton(onClick = { filtros = FiltrosBI() }) { Text("Limpar filtros", color = colorAccent) }
                    }
                } else if (despesas.isEmpty()) {
                    Column(Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Box(Modifier.size(88.dp).background(colorAccent.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Insights, null, modifier = Modifier.size(44.dp), tint = colorAccent)
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Nada para analisar em ${mesNome.lowercase()}", fontWeight = FontWeight.Bold, color = colorTextPrimary, fontSize = 16.sp)
                        Text("Quando houver lançamentos neste mês, os gráficos aparecem aqui.", color = colorTextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onAbrirLancamento, colors = ButtonDefaults.buttonColors(containerColor = colorAccent), shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Novo lançamento")
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

                        // ===== DESTAQUE DO MÊS =====
                        item {
                            CartaoBI(colorSurface, colorDivider) {
                                if (filtros.quantidade > 0) {
                                    Text("Total filtrado", fontSize = 12.sp, color = colorTextSecondary)
                                    Text(brl(bi.totalGeral), fontSize = 30.sp, fontWeight = FontWeight.Black, color = colorTextPrimary)
                                    Text("${despesasFiltradas.size} de ${despesas.size} lançamentos", fontSize = 12.sp, color = colorTextSecondary)
                                } else {
                                    Text("Sobra do mês", fontSize = 12.sp, color = colorTextSecondary)
                                    Text(brl(bi.sobra), fontSize = 30.sp, fontWeight = FontWeight.Black, color = if (bi.sobra >= 0) colorTextPrimary else Color(0xFFE53935))
                                }
                                if (filtros.quantidade == 0) variacaoDespesas?.let { v ->
                                    val subiu = v > 0
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(if (subiu) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown, null, tint = if (subiu) Color(0xFFE53935) else Color(0xFF43A047), modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Despesas ${if (subiu) "+" else ""}${"%.0f".format(v)}% vs. ${mesAnterior.rotulo}", fontSize = 12.sp, color = if (subiu) Color(0xFFE53935) else Color(0xFF43A047))
                                    }
                                }
                                Spacer(Modifier.height(14.dp))
                                Row(Modifier.fillMaxWidth()) {
                                    MiniKpi("Renda", brl(rendaTotalMes), Color(0xFF43A047), colorTextSecondary, Modifier.weight(1f))
                                    MiniKpi("Despesas", brl(bi.totalGeral), Color(0xFFE53935), colorTextSecondary, Modifier.weight(1f))
                                    MiniKpi(
                                        "Poupança", if (rendaTotalMes > 0) "%.0f%%".format(bi.taxaPoupanca) else "—",
                                        when { bi.taxaPoupanca >= 20 -> Color(0xFF43A047); bi.taxaPoupanca > 0 -> Color(0xFFFB8C00); else -> Color(0xFFE53935) },
                                        colorTextSecondary, Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // ===== ÚLTIMOS 6 MESES =====
                        if (historico.size >= 2) {
                            item {
                                CartaoBI(colorSurface, colorDivider) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Últimos 6 meses", fontWeight = FontWeight.Bold, color = colorTextPrimary, modifier = Modifier.weight(1f))
                                        Legenda(Color(0xFF43A047), "Renda", colorTextSecondary)
                                        Spacer(Modifier.width(10.dp))
                                        Legenda(colorAccent, "Despesas", colorTextSecondary)
                                    }
                                    Spacer(Modifier.height(14.dp))
                                    GraficoHistorico(historico, colorAccent, colorTextSecondary, colorTextPrimary)
                                    val comDados = historico.filter { it.despesas > 0 }
                                    if (comDados.isNotEmpty()) {
                                        Spacer(Modifier.height(8.dp))
                                        Text("Média de despesas: ${brl(comDados.sumOf { it.despesas } / comDados.size)}", fontSize = 12.sp, color = colorTextSecondary)
                                    }
                                }
                            }
                        }

                        // ===== COMPOSIÇÃO POR CATEGORIA =====
                        item {
                            CartaoBI(colorSurface, colorDivider) {
                                Text("Para onde foi o dinheiro", fontWeight = FontWeight.Bold, color = colorTextPrimary)
                                Spacer(Modifier.height(16.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Canvas(Modifier.size(128.dp)) {
                                            var inicio = -90f
                                            val espaco = if (bi.porCategoria.size > 1) 2f else 0f
                                            bi.porCategoria.forEach { (cat, valor) ->
                                                val varredura = (valor / bi.totalGeral * 360).toFloat()
                                                drawArc(corCategoria(cat), inicio + espaco / 2, (varredura - espaco).coerceAtLeast(0.5f), false, style = Stroke(18.dp.toPx(), cap = StrokeCap.Butt))
                                                inicio += varredura
                                            }
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("${bi.porCategoria.size}", fontSize = 22.sp, fontWeight = FontWeight.Black, color = colorTextPrimary)
                                            Text(if (bi.porCategoria.size == 1) "categoria" else "categorias", fontSize = 10.sp, color = colorTextSecondary)
                                        }
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        bi.porCategoria.take(4).forEach { (cat, valor) ->
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(Modifier.size(8.dp).background(corCategoria(cat), CircleShape))
                                                Spacer(Modifier.width(6.dp))
                                                Text(cat, fontSize = 12.sp, color = colorTextPrimary, maxLines = 1, modifier = Modifier.weight(1f))
                                                Text("${(valor / bi.totalGeral * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colorTextPrimary)
                                            }
                                        }
                                        if (bi.porCategoria.size > 4) Text("+${bi.porCategoria.size - 4} outras", fontSize = 11.sp, color = colorTextSecondary)
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                HorizontalDivider(color = colorDivider)
                                Spacer(Modifier.height(8.dp))
                                // Lista completa com ícone, valor e barra relativa
                                bi.porCategoria.forEach { (cat, valor) ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(34.dp).background(corCategoria(cat).copy(alpha = 0.14f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                            Icon(iconeCategoria(cat), null, tint = corCategoria(cat), modifier = Modifier.size(18.dp))
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Row {
                                                Text(cat, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colorTextPrimary, modifier = Modifier.weight(1f))
                                                Text(brl(valor), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colorTextPrimary)
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            LinearProgressIndicator(
                                                progress = { (valor / bi.porCategoria.first().second).toFloat() },
                                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                                                color = corCategoria(cat), trackColor = progressTrackBg, strokeCap = StrokeCap.Round
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ===== PERFIL DOS GASTOS =====
                        item {
                            CartaoBI(colorSurface, colorDivider) {
                                Text("Perfil dos gastos", fontWeight = FontWeight.Bold, color = colorTextPrimary)
                                Spacer(Modifier.height(14.dp))
                                BarraDividida("Fixos", bi.totalFixa, "Variáveis", bi.totalGeral - bi.totalFixa, colorAccent, Color(0xFFFFB74D), colorTextPrimary, colorTextSecondary, ::brl)
                                Spacer(Modifier.height(16.dp))
                                BarraDividida("Cartão", bi.totalCartao, "Pix / débito", bi.totalGeral - bi.totalCartao, Color(0xFFE91E63), Color(0xFF03A9F4), colorTextPrimary, colorTextSecondary, ::brl)
                                Spacer(Modifier.height(16.dp))
                                BarraDividida("Pago", bi.totalPago, "A pagar", bi.totalGeral - bi.totalPago, Color(0xFF26A69A), progressTrackBg, colorTextPrimary, colorTextSecondary, ::brl)
                            }
                        }

                        // ===== METAS POR CATEGORIA =====
                        item {
                            CartaoBI(colorSurface, colorDivider) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Metas por categoria", fontWeight = FontWeight.Bold, color = colorTextPrimary)
                                        Text("Toque numa categoria para definir o limite do mês", fontSize = 11.sp, color = colorTextSecondary)
                                    }
                                    val estouradas = bi.porCategoria.count { (c, v) -> (limites[c] ?: 0.0) > 0 && v >= (limites[c] ?: 0.0) * 0.8 }
                                    if (estouradas > 0) {
                                        Surface(color = Color(0xFFE53935).copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
                                            Text("$estouradas em alerta", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE53935), modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                bi.porCategoria.forEach { (categoria, gasto) ->
                                    val limite = limites[categoria] ?: 0.0
                                    val uso = if (limite > 0) (gasto / limite).toFloat() else 0f
                                    val cor = when {
                                        limite == 0.0 -> colorTextSecondary
                                        uso >= 1f -> Color(0xFFE53935)
                                        uso >= 0.8f -> Color(0xFFFB8C00)
                                        else -> Color(0xFF43A047)
                                    }
                                    Column(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                            .clickable { categoriaParaLimite = categoria; valorNovoLimite = if (limite > 0) limite.paraCampo() else ""; mostrarDialogLimite = true }
                                            .padding(vertical = 8.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(iconeCategoria(categoria), null, tint = corCategoria(categoria), modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(categoria, fontSize = 13.sp, color = colorTextPrimary, modifier = Modifier.weight(1f))
                                            Text(
                                                if (limite > 0) "${brl(gasto)} de ${brl(limite)}" else "Definir meta",
                                                fontSize = 12.sp, fontWeight = if (limite > 0) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (limite > 0) cor else colorAccent
                                            )
                                        }
                                        if (limite > 0) {
                                            Spacer(Modifier.height(6.dp))
                                            LinearProgressIndicator(progress = { uso.coerceAtMost(1f) }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = cor, trackColor = progressTrackBg, strokeCap = StrokeCap.Round)
                                        }
                                    }
                                }
                            }
                        }

                        // ===== MAIORES GASTOS =====
                        item {
                            CartaoBI(colorSurface, colorDivider) {
                                Text("Maiores gastos", fontWeight = FontWeight.Bold, color = colorTextPrimary)
                                Spacer(Modifier.height(8.dp))
                                bi.maiores.forEachIndexed { i, d ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("${i + 1}", fontSize = 13.sp, fontWeight = FontWeight.Black, color = colorTextSecondary, modifier = Modifier.width(20.dp))
                                        Box(Modifier.size(34.dp).background(corCategoria(d.categoria).copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                                            Icon(iconeCategoria(d.categoria), null, tint = corCategoria(d.categoria), modifier = Modifier.size(18.dp))
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(d.descricao, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colorTextPrimary, maxLines = 1)
                                            Text(d.categoria, fontSize = 11.sp, color = colorTextSecondary)
                                        }
                                        Text(brl(d.valor), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colorTextPrimary)
                                    }
                                }
                            }
                        }

                        // ===== PROJETOS (fora do custo de vida) =====
                        if (projetosList.isNotEmpty()) {
                            item {
                                CartaoBI(colorSurface, Color(0xFF03A9F4).copy(alpha = 0.3f)) {
                                    Text("Projetos e viagens", fontWeight = FontWeight.Bold, color = Color(0xFF03A9F4))
                                    Text("Não entram no custo de vida acima", fontSize = 11.sp, color = colorTextSecondary)
                                    Spacer(Modifier.height(12.dp))
                                    projetosList.forEach { proj ->
                                        val gasto = despesas.filter { it.projetoId == proj.id }.sumOf { it.valor }
                                        val uso = if (proj.orcamento > 0) (gasto / proj.orcamento).toFloat() else 0f
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(proj.nome, fontSize = 13.sp, color = colorTextPrimary, modifier = Modifier.weight(1f))
                                            Text(if (proj.orcamento > 0) "${brl(gasto)} de ${brl(proj.orcamento)}" else brl(gasto), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colorTextPrimary)
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        LinearProgressIndicator(progress = { uso.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = if (uso > 1f) Color(0xFFE53935) else Color(0xFF03A9F4), trackColor = progressTrackBg, strokeCap = StrokeCap.Round)
                                        Spacer(Modifier.height(12.dp))
                                    }
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(40.dp)) }
                    }
                }
            }
        }
    }

    // PAINEL DE FILTROS
    if (mostrarFiltros) {
        val categoriasDoMes = remember(despesas) { despesas.map { it.categoria }.distinct().sorted() }
        ModalBottomSheet(onDismissRequest = { mostrarFiltros = false }, containerColor = colorSurface) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Filtros", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colorTextPrimary, modifier = Modifier.weight(1f))
                    if (filtros.quantidade > 0) TextButton(onClick = { filtros = FiltrosBI() }) { Text("Limpar tudo", color = colorAccent) }
                }
                GrupoFiltro("Tipo", listOf("Todos", "Fixa", "Variável"), filtros.tipo, colorAccent, colorTextSecondary) { filtros = filtros.copy(tipo = it) }
                GrupoFiltro("Status", listOf("Todos", "Pago", "A pagar"), filtros.status, colorAccent, colorTextSecondary) { filtros = filtros.copy(status = it) }
                GrupoFiltro("Pagamento", listOf("Todos", "Cartão", "Pix / débito"), filtros.pagamento, colorAccent, colorTextSecondary) { filtros = filtros.copy(pagamento = it) }
                Column {
                    Text("Categoria", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colorTextSecondary)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        (listOf("Todas") + categoriasDoMes).forEach { cat ->
                            val sel = filtros.categoria == cat
                            FilterChip(
                                selected = sel, onClick = { filtros = filtros.copy(categoria = cat) },
                                label = { Text(cat) },
                                leadingIcon = if (cat != "Todas") ({ Icon(iconeCategoria(cat), null, Modifier.size(16.dp), tint = if (sel) colorAccent else corCategoria(cat)) }) else null,
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colorAccent.copy(alpha = 0.14f), selectedLabelColor = colorAccent),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }
                Button(onClick = { mostrarFiltros = false }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = colorAccent)) {
                    Text("Ver ${despesasFiltradas.size} lançamento${if (despesasFiltradas.size == 1) "" else "s"}", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // DIALOG DE LIMITES
    if (mostrarDialogLimite) {
        AlertDialog(
            onDismissRequest = { mostrarDialogLimite = false },
            containerColor = colorSurface,
            title = { Text("Definir Limite: $categoriaParaLimite", color = colorTextPrimary) },
            text = {
                OutlinedTextField(
                    value = valorNovoLimite,
                    onValueChange = { valorNovoLimite = it },
                    label = { Text("Valor do Limite", color = colorTextSecondary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colorAccent, unfocusedBorderColor = colorDivider, focusedTextColor = colorTextPrimary, unfocusedTextColor = colorTextPrimary),
                    shape = FormatoCampo
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val v = valorNovoLimite.paraValor() ?: 0.0
                        if (usuario != null && workspaceUid.isNotEmpty()) {
                            vm.salvarLimite(categoriaParaLimite, v) { mostrarDialogLimite = false }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colorAccent)
                ) { Text("Salvar", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { mostrarDialogLimite = false }) { Text("Cancelar", color = colorTextSecondary) } }
        )
    }
}

// =========================================================================
// DADOS E CÁLCULOS DO BI
// =========================================================================
data class MesHistorico(val rotulo: String, val mesAno: String, val renda: Double, val despesas: Double)

data class ResumoBI(
    val totalGeral: Double,
    val totalPago: Double,
    val totalFixa: Double,
    val totalCartao: Double,
    val sobra: Double,
    val taxaPoupanca: Double,
    val porCategoria: List<Pair<String, Double>>,
    val maiores: List<Despesa>
)

fun calcularBI(despesas: List<Despesa>, renda: Double): ResumoBI {
    val custoVida = despesas.filter { it.projetoId == null }
    val total = custoVida.sumOf { it.valor }
    return ResumoBI(
        totalGeral = total,
        totalPago = custoVida.filter { it.status == "Pago" }.sumOf { it.valor },
        totalFixa = custoVida.filter { it.tipo == "Fixa" }.sumOf { it.valor },
        totalCartao = custoVida.filter { !it.cartaoId.isNullOrBlank() && it.cartaoId != "Saldo Conta" }.sumOf { it.valor },
        sobra = renda - total,
        taxaPoupanca = if (renda > 0) (renda - total) / renda * 100 else 0.0,
        porCategoria = custoVida.groupBy { it.categoria }.mapValues { it.value.sumOf { d -> d.valor } }.toList().sortedByDescending { it.second },
        maiores = custoVida.sortedByDescending { it.valor }.take(5)
    )
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.aguardar(): T = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resumeWith(Result.success(it)) }
    addOnFailureListener { cont.resumeWith(Result.failure(it)) }
}

// Busca renda e despesas (custo de vida) dos 6 meses que terminam no mês selecionado
suspend fun carregarHistorico(workspaceUid: String, mesFinal: Calendar): List<MesHistorico> = try {
    val br = Locale("pt", "BR")
    val meses = (5 downTo 0).map { atras ->
        val c = mesFinal.clone() as Calendar
        c.add(Calendar.MONTH, -atras)
        SimpleDateFormat("MM/yyyy", br).format(c.time) to SimpleDateFormat("MMM", br).format(c.time).replace(".", "")
    }
    val usuarioDoc = Firebase.firestore.collection("usuarios").document(workspaceUid)
    val despesasSnap = usuarioDoc.collection("despesas").whereIn("mesAno", meses.map { it.first }).get().aguardar()
    val despesasPorMes = despesasSnap.documents
        .filter { it.getString("projetoId") == null }
        .groupBy { it.getString("mesAno") }
        .mapValues { (_, docs) -> docs.sumOf { it.getDouble("valor") ?: 0.0 } }
    meses.map { (mesAno, rotulo) ->
        val saldo = usuarioDoc.collection("saldos").document(mesAno.replace("/", "-")).get().aguardar()
        val renda = (saldo.getDouble("adiantamento") ?: 0.0) + (saldo.getDouble("pagamento") ?: saldo.getDouble("valor") ?: 0.0) + (saldo.getDouble("extra") ?: 0.0)
        MesHistorico(rotulo, mesAno, renda, despesasPorMes[mesAno] ?: 0.0)
    }
} catch (e: Exception) {
    android.util.Log.e("FluxAi_BI", "Erro ao carregar histórico", e)
    emptyList()
}

// =========================================================================
// COMPONENTES VISUAIS DO BI
// =========================================================================
@Composable
private fun CartaoBI(fundo: Color, borda: Color, conteudo: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = fundo, border = BorderStroke(1.dp, borda), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), content = conteudo)
    }
}

@Composable
private fun MiniKpi(titulo: String, valor: String, cor: Color, corTitulo: Color, modifier: Modifier) {
    Column(modifier) {
        Text(titulo, fontSize = 11.sp, color = corTitulo)
        Text(valor, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = cor, maxLines = 1)
    }
}

@Composable
private fun Legenda(cor: Color, texto: String, corTexto: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(cor, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(4.dp))
        Text(texto, fontSize = 11.sp, color = corTexto)
    }
}

// Barras agrupadas por mês (renda x despesas); o mês selecionado fica em destaque
@Composable
private fun GraficoHistorico(meses: List<MesHistorico>, corDespesa: Color, corTexto: Color, corTextoForte: Color) {
    val maximo = (meses.maxOfOrNull { maxOf(it.renda, it.despesas) } ?: 0.0).coerceAtLeast(1.0)
    val corRenda = Color(0xFF43A047)
    Column {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val larguraGrupo = size.width / meses.size
            val larguraBarra = (larguraGrupo * 0.28f).coerceAtMost(14.dp.toPx())
            meses.forEachIndexed { i, m ->
                val atual = i == meses.lastIndex
                val alfa = if (atual) 1f else 0.45f
                val centro = larguraGrupo * i + larguraGrupo / 2
                val hRenda = (m.renda / maximo * size.height).toFloat()
                val hDesp = (m.despesas / maximo * size.height).toFloat()
                val raio = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                drawRoundRect(corRenda.copy(alpha = alfa), topLeft = androidx.compose.ui.geometry.Offset(centro - larguraBarra - 1.dp.toPx(), size.height - hRenda), size = androidx.compose.ui.geometry.Size(larguraBarra, hRenda), cornerRadius = raio)
                drawRoundRect(corDespesa.copy(alpha = alfa), topLeft = androidx.compose.ui.geometry.Offset(centro + 1.dp.toPx(), size.height - hDesp), size = androidx.compose.ui.geometry.Size(larguraBarra, hDesp), cornerRadius = raio)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            meses.forEachIndexed { i, m ->
                Text(
                    m.rotulo.replaceFirstChar { it.uppercase() }, fontSize = 11.sp,
                    fontWeight = if (i == meses.lastIndex) FontWeight.Bold else FontWeight.Normal,
                    color = if (i == meses.lastIndex) corTextoForte else corTexto,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// Uma barra dividida em duas partes proporcionais, com rótulos e valores embaixo
@Composable
private fun BarraDividida(rotuloA: String, valorA: Double, rotuloB: String, valorB: Double, corA: Color, corB: Color, corTexto: Color, corTextoFraco: Color, formatar: (Double) -> String) {
    val total = (valorA + valorB).coerceAtLeast(0.01)
    val fracaoA = (valorA / total).toFloat().coerceIn(0f, 1f)
    Column {
        Row(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape)) {
            if (fracaoA > 0f) Box(Modifier.weight(fracaoA).fillMaxHeight().background(corA))
            if (fracaoA < 1f) Box(Modifier.weight(1f - fracaoA).fillMaxHeight().background(corB))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("$rotuloA · ${(fracaoA * 100).toInt()}%", fontSize = 11.sp, color = corTextoFraco)
                Text(formatar(valorA), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = corTexto)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("$rotuloB · ${100 - (fracaoA * 100).toInt()}%", fontSize = 11.sp, color = corTextoFraco)
                Text(formatar(valorB), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = corTexto)
            }
        }
    }
}

// =========================================================================
// FILTROS DO BI
// =========================================================================
data class FiltrosBI(
    val tipo: String = "Todos",
    val status: String = "Todos",
    val pagamento: String = "Todos",
    val categoria: String = "Todas"
) {
    val quantidade get() = listOf(tipo != "Todos", status != "Todos", pagamento != "Todos", categoria != "Todas").count { it }

    fun aceita(d: Despesa): Boolean {
        val noCartao = !d.cartaoId.isNullOrBlank() && d.cartaoId != "Saldo Conta"
        return (tipo == "Todos" || d.tipo == tipo) &&
            (status == "Todos" || d.status == status) &&
            (pagamento == "Todos" || (pagamento == "Cartão") == noCartao) &&
            (categoria == "Todas" || d.categoria == categoria)
    }

    // Texto da etiqueta + como removê-la
    fun etiquetas(): List<Pair<String, (FiltrosBI) -> FiltrosBI>> = buildList {
        if (tipo != "Todos") add((if (tipo == "Fixa") "Fixos" else "Variáveis") to { f: FiltrosBI -> f.copy(tipo = "Todos") })
        if (status != "Todos") add((if (status == "Pago") "Pagos" else "A pagar") to { f: FiltrosBI -> f.copy(status = "Todos") })
        if (pagamento != "Todos") add(pagamento to { f: FiltrosBI -> f.copy(pagamento = "Todos") })
        if (categoria != "Todas") add(categoria to { f: FiltrosBI -> f.copy(categoria = "Todas") })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GrupoFiltro(titulo: String, opcoes: List<String>, selecionada: String, corDestaque: Color, corTitulo: Color, onSelecionar: (String) -> Unit) {
    Column {
        Text(titulo, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = corTitulo)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            opcoes.forEachIndexed { i, opcao ->
                SegmentedButton(
                    selected = selecionada == opcao,
                    onClick = { onSelecionar(opcao) },
                    shape = SegmentedButtonDefaults.itemShape(i, opcoes.size),
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = corDestaque.copy(alpha = 0.14f), activeContentColor = corDestaque)
                ) { Text(when (opcao) { "Fixa" -> "Fixos"; "Variável" -> "Variáveis"; "Pago" -> "Pagos"; else -> opcao }, fontSize = 12.sp, maxLines = 1) }
            }
        }
    }
}
