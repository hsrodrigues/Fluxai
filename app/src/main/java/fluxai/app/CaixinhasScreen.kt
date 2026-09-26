package fluxai.app

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import fluxai.app.ui.theme.LocalDarkTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Dicionário de Ícones
val mapaIconesCaixinha = mapOf(
    "savings" to Icons.Default.Savings,
    "flight" to Icons.Default.Flight,
    "car" to Icons.Default.DirectionsCar,
    "home" to Icons.Default.Home,
    "school" to Icons.Default.School,
    "favorite" to Icons.Default.Favorite,
    "shopping" to Icons.Default.ShoppingCart
)

fun obterIconeCaixinha(chave: String): ImageVector {
    return mapaIconesCaixinha[chave] ?: Icons.Default.Savings
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaixinhasScreen(
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
    val user = Firebase.auth.currentUser ?: return
    val isDark = LocalDarkTheme.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val colorBg = if (isDark) Color(0xFF0F0F0F) else Color(0xFFF4F7FA)
    val colorSurface = if (isDark) Color(0xFF1A1A1A) else Color.White

    // =========================================================================
    // === CHAVE MESTRA DA SINCRONIZAÇÃO FAMILIAR ===
    // =========================================================================
    val sharedPref = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
    val workspaceUid = sharedPref.getString("workspace_uid", user.uid) ?: user.uid
    // =========================================================================

    val vm: CaixinhasViewModel = viewModel()
    LaunchedEffect(workspaceUid) { vm.observar(workspaceUid) }
    val caixinhas by vm.caixinhas.collectAsStateWithLifecycle()
    var mostrarModalNovaCaixinha by remember { mutableStateOf(false) }
    var caixinhaParaAporte by remember { mutableStateOf<Caixinha?>(null) }

    var cxNome by remember { mutableStateOf("") }
    var cxMeta by remember { mutableStateOf("") }
    var cxIcone by remember { mutableStateOf("savings") }

    var salvandoCx by remember { mutableStateOf(false) }

    var valorAporte by remember { mutableStateOf("") }
    var tipoAporte by remember { mutableStateOf("depositar") }
    var salvandoAporte by remember { mutableStateOf(false) }

    val colorTextPrimary = if (isDark) Color.White else Color(0xFF1A1A1A)
    val colorTextSecondary = Color(0xFF9CA3AF)
    val colorDivider = if (isDark) Color(0xFF2C2C2C) else Color(0xFFE5E7EB)
    val verde = Color(0xFF43A047)
    val moeda = remember { java.text.NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    var caixinhaParaExcluir by remember { mutableStateOf<Caixinha?>(null) }
    val coresCampo = OutlinedTextFieldDefaults.colors(focusedBorderColor = verde, unfocusedBorderColor = colorDivider, focusedTextColor = colorTextPrimary, unfocusedTextColor = colorTextPrimary)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = { MenuLateral(drawerState, coroutineScope, "caixinhas", onAbrirDashboard, onAbrirLancamento, onAbrirAnalytics, onAbrirSettings, onAbrirSobre, onAbrirManutencao, onLogout, onAbrirCartoes, onAbrirCaixinhas, onAbrirAssinaturas, onAbrirCelular) }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Cofre e metas", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colorTextPrimary) },
                    navigationIcon = { IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, null, tint = colorTextPrimary) } },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colorBg)
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { mostrarModalNovaCaixinha = true }, containerColor = verde, contentColor = Color.White,
                    icon = { Icon(Icons.Default.Add, null) }, text = { Text("Nova meta") }
                )
            },
            containerColor = colorBg
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ===== RESUMO =====
                item {
                    val saldoTotal = caixinhas.sumOf { it.saldo }
                    val metaTotal = caixinhas.sumOf { it.meta }
                    val geral = if (metaTotal > 0) (saldoTotal / metaTotal).toFloat().coerceIn(0f, 1f) else 0f
                    Surface(shape = RoundedCornerShape(20.dp), color = colorSurface, border = BorderStroke(1.dp, colorDivider), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp)) {
                            Text("Total guardado", fontSize = 12.sp, color = colorTextSecondary)
                            Text(moeda.format(saldoTotal), fontSize = 30.sp, fontWeight = FontWeight.Black, color = verde)
                            if (metaTotal > 0) {
                                Spacer(Modifier.height(10.dp))
                                LinearProgressIndicator(progress = { geral }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = verde, trackColor = verde.copy(alpha = 0.12f), strokeCap = StrokeCap.Round)
                                Spacer(Modifier.height(6.dp))
                                Text("${(geral * 100).toInt()}% de ${moeda.format(metaTotal)} somando todas as metas", fontSize = 12.sp, color = colorTextSecondary)
                            }
                        }
                    }
                }

                if (caixinhas.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(88.dp).background(verde.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Savings, null, Modifier.size(44.dp), tint = verde)
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("Nenhuma meta ainda", fontWeight = FontWeight.Bold, color = colorTextPrimary)
                            Text("Crie uma meta (viagem, reserva, carro) e vá guardando aos poucos.", fontSize = 13.sp, color = colorTextSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
                        }
                    }
                } else {
                    items(caixinhas.sortedByDescending { if (it.meta > 0) it.saldo / it.meta else 0.0 }, key = { it.id }) { cx ->
                        CardCaixinha(
                            caixinha = cx, isDark = isDark, moeda = moeda,
                            onGuardar = { tipoAporte = "depositar"; caixinhaParaAporte = cx },
                            onResgatar = { tipoAporte = "resgatar"; caixinhaParaAporte = cx },
                            onDelete = { caixinhaParaExcluir = cx }
                        )
                    }
                }
            }

            if (mostrarModalNovaCaixinha) {
                AlertDialog(
                    onDismissRequest = { mostrarModalNovaCaixinha = false },
                    containerColor = colorSurface,
                    shape = RoundedCornerShape(28.dp),
                    title = { Text("Nova meta", fontWeight = FontWeight.Bold, color = colorTextPrimary) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(value = cxNome, onValueChange = { cxNome = it }, label = { Text("Objetivo (ex.: viagem)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = FormatoCampo, colors = coresCampo)
                            OutlinedTextField(value = cxMeta, onValueChange = { cxMeta = it.replace('.', ',') }, label = { Text("Quanto quer juntar (R$)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(), shape = FormatoCampo, colors = coresCampo)
                            Text("Ícone", fontSize = 12.sp, color = colorTextSecondary)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                mapaIconesCaixinha.keys.forEach { chave ->
                                    val isSel = cxIcone == chave
                                    Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(if (isSel) verde.copy(0.18f) else Color.Transparent).clickable { cxIcone = chave }, contentAlignment = Alignment.Center) {
                                        Icon(imageVector = obterIconeCaixinha(chave), contentDescription = null, tint = if (isSel) verde else colorTextSecondary, modifier = Modifier.size(22.dp))
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val m = cxMeta.paraValor() ?: 0.0
                                if (cxNome.isNotBlank()) {
                                    salvandoCx = true
                                    vm.criar(cxNome, m, cxIcone) { ok ->
                                        salvandoCx = false
                                        if (ok) { mostrarModalNovaCaixinha = false; cxNome = ""; cxMeta = ""; cxIcone = "savings" }
                                        else Toast.makeText(context, "Não foi possível criar a meta.", Toast.LENGTH_SHORT).show()
                                    }
                                } else Toast.makeText(context, "Dê um nome para a meta.", Toast.LENGTH_SHORT).show()
                            },
                            enabled = !salvandoCx,
                            colors = ButtonDefaults.buttonColors(containerColor = verde)
                        ) { Text("Criar meta") }
                    },
                    dismissButton = { TextButton(onClick = { mostrarModalNovaCaixinha = false }) { Text("Cancelar", color = colorTextSecondary) } }
                )
            }

            caixinhaParaExcluir?.let { cx ->
                AlertDialog(
                    onDismissRequest = { caixinhaParaExcluir = null },
                    containerColor = colorSurface,
                    icon = { Icon(Icons.Default.DeleteForever, null, tint = Color(0xFFE53935)) },
                    title = { Text("Excluir \"${cx.nome}\"?", color = colorTextPrimary) },
                    text = { Text(if (cx.saldo > 0) "Esta meta tem ${moeda.format(cx.saldo)} guardados. Resgate o valor antes, se quiser que ele volte para o saldo do mês." else "A meta será removida.", color = colorTextSecondary) },
                    confirmButton = {
                        Button(onClick = {
                            vm.excluir(cx)
                            caixinhaParaExcluir = null
                        }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))) { Text("Excluir") }
                    },
                    dismissButton = { TextButton(onClick = { caixinhaParaExcluir = null }) { Text("Cancelar", color = colorTextSecondary) } }
                )
            }

            caixinhaParaAporte?.let { cx ->
                val guardar = tipoAporte == "depositar"
                val corAcao = if (guardar) verde else Color(0xFFE53935)
                AlertDialog(
                    onDismissRequest = { caixinhaParaAporte = null; valorAporte = "" },
                    containerColor = colorSurface,
                    shape = RoundedCornerShape(28.dp),
                    title = { Text(cx.nome, fontWeight = FontWeight.Bold, color = colorTextPrimary) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                listOf("depositar" to "Guardar", "resgatar" to "Resgatar").forEachIndexed { i, (chave, rotulo) ->
                                    SegmentedButton(
                                        selected = tipoAporte == chave, onClick = { tipoAporte = chave },
                                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                                        colors = SegmentedButtonDefaults.colors(activeContainerColor = corAcao.copy(alpha = 0.14f), activeContentColor = corAcao)
                                    ) { Text(rotulo) }
                                }
                            }
                            Text("Guardado: ${moeda.format(cx.saldo)}", fontSize = 12.sp, color = colorTextSecondary)
                            OutlinedTextField(value = valorAporte, onValueChange = { valorAporte = it.replace('.', ',') }, label = { Text("Valor (R$)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(), shape = FormatoCampo, colors = coresCampo)
                            // Valores rápidos
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(50, 100, 200, 500).forEach { rapido ->
                                    AssistChip(onClick = { valorAporte = "$rapido" }, label = { Text("R$ $rapido") }, shape = RoundedCornerShape(50))
                                }
                            }
                            Text(
                                if (guardar) "Entra como despesa paga no mês atual (sai do seu saldo)." else "Volta como renda extra no mês atual.",
                                fontSize = 11.sp, color = colorTextSecondary
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val v = valorAporte.paraValor() ?: 0.0
                                when {
                                    v <= 0 -> Toast.makeText(context, "Informe um valor.", Toast.LENGTH_SHORT).show()
                                    !guardar && v > cx.saldo -> Toast.makeText(context, "A meta só tem ${moeda.format(cx.saldo)}.", Toast.LENGTH_SHORT).show()
                                    else -> {
                                        salvandoAporte = true
                                        vm.movimentar(cx, guardar, v) { ok ->
                                            salvandoAporte = false
                                            if (ok) {
                                                Toast.makeText(context, if (guardar) "Guardado na meta!" else "Resgatado para o saldo do mês!", Toast.LENGTH_SHORT).show()
                                                caixinhaParaAporte = null; valorAporte = ""
                                            } else Toast.makeText(context, "Não foi possível salvar.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            enabled = !salvandoAporte,
                            colors = ButtonDefaults.buttonColors(containerColor = corAcao)
                        ) { Text(if (guardar) "Guardar" else "Resgatar") }
                    },
                    dismissButton = { TextButton(onClick = { caixinhaParaAporte = null; valorAporte = "" }) { Text("Cancelar", color = colorTextSecondary) } }
                )
            }
        }
    }
}

@Composable
fun CardCaixinha(caixinha: Caixinha, isDark: Boolean, moeda: java.text.NumberFormat, onGuardar: () -> Unit, onResgatar: () -> Unit, onDelete: () -> Unit) {
    val verde = Color(0xFF43A047)
    val corTexto = if (isDark) Color.White else Color(0xFF1A1A1A)
    val corFraca = Color(0xFF9CA3AF)
    val pct = if (caixinha.meta > 0) (caixinha.saldo / caixinha.meta).toFloat() else 0f
    val concluida = caixinha.meta > 0 && pct >= 1f
    var menu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isDark) Color(0xFF1A1A1A) else Color.White,
        border = BorderStroke(1.dp, if (concluida) verde.copy(alpha = 0.5f) else if (isDark) Color(0xFF2C2C2C) else Color(0xFFE5E7EB)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(verde.copy(alpha = 0.12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(obterIconeCaixinha(caixinha.icone), null, tint = verde)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(caixinha.nome, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = corTexto, maxLines = 1)
                    Text(
                        when {
                            caixinha.meta <= 0 -> "Sem valor de meta"
                            concluida -> "Meta alcançada! 🎉"
                            else -> "Faltam ${moeda.format(caixinha.meta - caixinha.saldo)}"
                        },
                        fontSize = 12.sp, color = if (concluida) verde else corFraca
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Mais opções", tint = corFraca) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Excluir meta", color = Color(0xFFE53935)) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFE53935)) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(moeda.format(caixinha.saldo), fontSize = 20.sp, fontWeight = FontWeight.Black, color = corTexto, modifier = Modifier.weight(1f))
                if (caixinha.meta > 0) Text("${(pct * 100).toInt()}% de ${moeda.format(caixinha.meta)}", fontSize = 12.sp, color = corFraca)
            }
            if (caixinha.meta > 0) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress = { pct.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = verde, trackColor = verde.copy(alpha = 0.12f), strokeCap = StrokeCap.Round)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onResgatar, enabled = caixinha.saldo > 0, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Resgatar") }
                Button(onClick = onGuardar, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = verde)) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Guardar")
                }
            }
        }
    }
}
