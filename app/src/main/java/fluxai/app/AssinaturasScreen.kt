package fluxai.app

import android.widget.Toast

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextAlign
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssinaturasScreen(
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
    val user = Firebase.auth.currentUser ?: return
    val isDark = LocalDarkTheme.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val colorBg = if (isDark) Color(0xFF0F0F0F) else Color(0xFFF4F7FA)
    val colorSurface = if (isDark) Color(0xFF1A1A1A) else Color.White
    val colorTextPrimary = if (isDark) Color.White else Color(0xFF1A1A1A)
    val colorTextSecondary = Color.Gray

    // =========================================================================
    // === CHAVE MESTRA DA SINCRONIZAÇÃO FAMILIAR ===
    // =========================================================================
    val sharedPref = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
    val workspaceUid = sharedPref.getString("workspace_uid", user.uid) ?: user.uid
    // =========================================================================

    val vm: AssinaturasViewModel = viewModel()
    LaunchedEffect(workspaceUid) { vm.observar(workspaceUid) }
    val assinaturas by vm.assinaturas.collectAsStateWithLifecycle()
    val carregando by vm.carregando.collectAsStateWithLifecycle()

    val cal = Calendar.getInstance()

    // =========================================================================
    // === INJETADO: Variáveis de Estado para os Modais ===
    // =========================================================================
    var mostrarModalNova by remember { mutableStateOf(false) }
    var despesaParaEditar by remember { mutableStateOf<Despesa?>(null) }
    var despesaParaExcluir by remember { mutableStateOf<Despesa?>(null) }

    // Variáveis da Nova Assinatura
    var aNome by remember { mutableStateOf("") }
    var aValor by remember { mutableStateOf("") }
    var aDia by remember { mutableStateOf("") }
    var salvando by remember { mutableStateOf(false) }
    // =========================================================================

    val totalAssinaturas = assinaturas.sumOf { it.valor }

    val rosa = Color(0xFFE91E63)
    val colorDivider = if (isDark) Color(0xFF2C2C2C) else Color(0xFFE5E7EB)
    val moeda = remember { java.text.NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    val hoje = cal.get(Calendar.DAY_OF_MONTH)
    // Vence nos próximos 7 dias e ainda não foi paga
    val (emBreve, demais) = assinaturas.sortedBy { it.diaVencimento }.partition { it.status != "Pago" && it.diaVencimento in hoje..(hoje + 7) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MenuLateral(drawerState, coroutineScope, "assinaturas", onAbrirDashboard, onAbrirLancamento, onAbrirAnalytics, onAbrirSettings, onAbrirSobre, onAbrirManutencao, onLogout, onAbrirCartoes, onAbrirCaixinhas, onAbrirAssinaturas, onAbrirCelular)
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Assinaturas e contas fixas", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colorTextPrimary) },
                    navigationIcon = { IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, null, tint = colorTextPrimary) } },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colorBg)
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { mostrarModalNova = true }, containerColor = rosa, contentColor = Color.White,
                    icon = { Icon(Icons.Default.Add, null) }, text = { Text("Nova") }
                )
            },
            containerColor = colorBg
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Surface(shape = RoundedCornerShape(20.dp), color = colorSurface, border = BorderStroke(1.dp, colorDivider), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp)) {
                            Text("Custo fixo por mês", fontSize = 12.sp, color = colorTextSecondary)
                            Text(moeda.format(totalAssinaturas), fontSize = 30.sp, fontWeight = FontWeight.Black, color = colorTextPrimary)
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text(moeda.format(totalAssinaturas * 12), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = rosa)
                                    Text("por ano", fontSize = 11.sp, color = colorTextSecondary)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("${assinaturas.size}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colorTextPrimary)
                                    Text(if (assinaturas.size == 1) "serviço" else "serviços", fontSize = 11.sp, color = colorTextSecondary)
                                }
                                Column(Modifier.weight(1f)) {
                                    val pagas = assinaturas.count { it.status == "Pago" }
                                    Text("$pagas/${assinaturas.size}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF43A047))
                                    Text("pagas no mês", fontSize = 11.sp, color = colorTextSecondary)
                                }
                            }
                        }
                    }
                }

                if (carregando) {
                    item { Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) { CircularProgressIndicator(color = rosa) } }
                } else if (assinaturas.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(88.dp).background(rosa.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Autorenew, null, Modifier.size(44.dp), tint = rosa)
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("Nenhuma conta fixa neste mês", fontWeight = FontWeight.Bold, color = colorTextPrimary)
                            Text("Cadastre streaming, academia, internet, luz... tudo que se repete todo mês.", fontSize = 13.sp, color = colorTextSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
                        }
                    }
                } else {
                    if (emBreve.isNotEmpty()) {
                        item { TituloAssinaturas("Vence em breve", Color(0xFFFB8C00)) }
                        items(emBreve, key = { it.id }) { desp ->
                            LinhaAssinatura(desp, hoje, isDark, moeda, onEditar = { despesaParaEditar = desp }, onExcluir = { despesaParaExcluir = desp },
                                onAlternarPago = { vm.alternarPago(desp) })
                        }
                    }
                    if (demais.isNotEmpty()) {
                        item { TituloAssinaturas(if (emBreve.isEmpty()) "Todas" else "Demais", colorTextSecondary) }
                        items(demais, key = { it.id }) { desp ->
                            LinhaAssinatura(desp, hoje, isDark, moeda, onEditar = { despesaParaEditar = desp }, onExcluir = { despesaParaExcluir = desp },
                                onAlternarPago = { vm.alternarPago(desp) })
                        }
                    }
                }
            }

            // =========================================================================
            // === INJETADO: Modais de Ação (Adicionar, Editar e Excluir) ===
            // =========================================================================
            if (mostrarModalNova) {
                AlertDialog(
                    onDismissRequest = { mostrarModalNova = false },
                    containerColor = colorSurface,
                    shape = RoundedCornerShape(28.dp),
                    title = { Text("Nova assinatura", fontWeight = FontWeight.Bold, color = colorTextPrimary) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Será registrada automaticamente como Fixa e Mensal.", fontSize = 12.sp, color = colorTextSecondary)
                            OutlinedTextField(value = aNome, onValueChange = { aNome = it }, label = { Text("Nome (Ex: Netflix)") }, modifier = Modifier.fillMaxWidth(), shape = FormatoCampo)
                            OutlinedTextField(value = aValor, onValueChange = { aValor = it }, label = { Text("Valor Mensal (R$)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), shape = FormatoCampo)
                            OutlinedTextField(value = aDia, onValueChange = { if(it.length <= 2) aDia = it }, label = { Text("Dia do Vencimento") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = FormatoCampo)
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val v = aValor.paraValor() ?: 0.0
                                val d = aDia.toIntOrNull() ?: 1
                                if (aNome.isNotBlank() && v > 0) {
                                    salvando = true
                                    vm.criar(aNome, v, d) { ok ->
                                        salvando = false
                                        if (ok) { mostrarModalNova = false; aNome = ""; aValor = ""; aDia = "" }
                                        else Toast.makeText(context, "Não foi possível salvar.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                            enabled = !salvando
                        ) { Text("Salvar") }
                    },
                    dismissButton = { TextButton(onClick = { mostrarModalNova = false }) { Text("Cancelar", color = Color.Gray) } }
                )
            }

            if (despesaParaEditar != null) {
                var eNome by remember { mutableStateOf(despesaParaEditar!!.descricao) }
                var eValor by remember { mutableStateOf("%.2f".format(despesaParaEditar!!.valor).replace(".", ",")) }
                var eDia by remember { mutableStateOf(despesaParaEditar!!.diaVencimento.toString()) }

                AlertDialog(
                    onDismissRequest = { despesaParaEditar = null },
                    containerColor = colorSurface,
                    shape = RoundedCornerShape(28.dp),
                    title = { Text("Editar", fontWeight = FontWeight.Bold, color = colorTextPrimary) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(value = eNome, onValueChange = { eNome = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth(), shape = FormatoCampo)
                            OutlinedTextField(value = eValor, onValueChange = { eValor = it }, label = { Text("Valor") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = FormatoCampo)
                            OutlinedTextField(value = eDia, onValueChange = { if(it.length <= 2) eDia = it }, label = { Text("Dia") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(100.dp), shape = FormatoCampo)
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val v = eValor.paraValor() ?: 0.0
                                val d = eDia.toIntOrNull() ?: 1
                                vm.editar(despesaParaEditar!!, eNome, v, d) { ok -> if (ok) despesaParaEditar = null }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))
                        ) { Text("Atualizar") }
                    },
                    dismissButton = { TextButton(onClick = { despesaParaEditar = null }) { Text("Cancelar", color = Color.Gray) } }
                )
            }

            if (despesaParaExcluir != null) {
                AlertDialog(
                    onDismissRequest = { despesaParaExcluir = null },
                    containerColor = colorSurface,
                    title = { Text("Excluir deste mês?", fontWeight = FontWeight.Bold, color = colorTextPrimary) },
                    text = { Text("'${despesaParaExcluir!!.descricao}' sai do mês atual.", color = colorTextSecondary) },
                    confirmButton = {
                        Button(
                            onClick = {
                                vm.excluir(despesaParaExcluir!!)
                                despesaParaExcluir = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) { Text("Excluir", color = Color.White) }
                    },
                    dismissButton = { TextButton(onClick = { despesaParaExcluir = null }) { Text("Voltar", color = Color.Gray) } }
                )
            }
            // =========================================================================
        }
    }
}

// Assinatura/conta fixa: despesa fixa do custo de vida, fora compras parceladas e itens adiados
fun ehAssinatura(d: Despesa): Boolean =
    d.tipo == "Fixa" && d.projetoId == null && d.status != "Próximo Mês" && !d.descricao.matches(Regex(".*\\(\\d+/\\d+\\)$"))

// Categoria sugerida pelo nome (mesma regra da leitura de notas: contas de consumo vão para Moradia)
fun categoriaPorNome(nome: String): String {
    val n = nome.lowercase()
    return when {
        listOf("luz", "energia", "enel", "cemig", "copel", "água", "agua", "sabesp", "gás", "gas", "internet", "condomínio", "condominio", "aluguel").any { it in n } -> "Moradia"
        listOf("academia", "smartfit", "gympass", "wellhub", "plano de saúde", "unimed", "farmácia").any { it in n } -> "Saúde"
        listOf("curso", "escola", "faculdade", "alura", "udemy").any { it in n } -> "Educação"
        else -> "Lazer"
    }
}

private fun iconeAssinatura(nome: String): Pair<androidx.compose.ui.graphics.vector.ImageVector, Color> {
    val n = nome.lowercase()
    return when {
        listOf("netflix", "prime", "hbo", "max", "disney", "globoplay", "paramount", "youtube").any { it in n } -> Icons.Default.Movie to Color(0xFFE50914)
        listOf("spotify", "deezer", "music", "tidal").any { it in n } -> Icons.Default.MusicNote to Color(0xFF1DB954)
        listOf("academia", "smartfit", "gympass", "wellhub").any { it in n } -> Icons.Default.FitnessCenter to Color(0xFFFF9800)
        listOf("internet", "claro", "vivo", "tim", "oi ", "wifi").any { it in n } -> Icons.Default.Wifi to Color(0xFF2196F3)
        listOf("luz", "energia", "enel", "cemig").any { it in n } -> Icons.Default.Lightbulb to Color(0xFFFFC107)
        listOf("água", "agua", "sabesp").any { it in n } -> Icons.Default.WaterDrop to Color(0xFF03A9F4)
        listOf("icloud", "google one", "drive", "dropbox").any { it in n } -> Icons.Default.Cloud to Color(0xFF5C6BC0)
        listOf("aluguel", "condomínio", "condominio").any { it in n } -> Icons.Default.Home to Color(0xFF8D6E63)
        else -> Icons.Default.Autorenew to Color(0xFF9E9E9E)
    }
}

@Composable
private fun TituloAssinaturas(texto: String, cor: Color) {
    Text(texto.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cor, letterSpacing = 1.sp, modifier = Modifier.padding(start = 4.dp, top = 10.dp))
}

@Composable
private fun LinhaAssinatura(desp: Despesa, hoje: Int, isDark: Boolean, moeda: java.text.NumberFormat, onEditar: () -> Unit, onExcluir: () -> Unit, onAlternarPago: () -> Unit) {
    val (icone, cor) = iconeAssinatura(desp.descricao)
    val corTexto = if (isDark) Color.White else Color(0xFF1A1A1A)
    val corFraca = Color(0xFF9CA3AF)
    val pago = desp.status == "Pago"
    val atrasada = !pago && desp.diaVencimento in 1 until hoje
    var menu by remember { mutableStateOf(false) }

    Surface(
        onClick = onEditar,
        shape = RoundedCornerShape(16.dp),
        color = if (isDark) Color(0xFF1A1A1A) else Color.White,
        border = BorderStroke(1.dp, if (atrasada) Color(0xFFE53935).copy(alpha = 0.4f) else if (isDark) Color(0xFF2C2C2C) else Color(0xFFE5E7EB)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconeLancamento(desp.descricao, icone, cor)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(desp.descricao, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = corTexto, maxLines = 1)
                Text(
                    when {
                        pago -> "Pago · dia ${desp.diaVencimento}"
                        atrasada -> "Atrasada · venceu dia ${desp.diaVencimento}"
                        desp.diaVencimento == hoje -> "Vence hoje"
                        else -> "Vence dia ${desp.diaVencimento}"
                    },
                    fontSize = 12.sp, color = when { pago -> Color(0xFF43A047); atrasada -> Color(0xFFE53935); else -> corFraca }
                )
            }
            Text(moeda.format(desp.valor), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = corTexto)
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Mais opções", tint = corFraca) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(if (pago) "Marcar como a pagar" else "Marcar como paga") }, leadingIcon = { Icon(if (pago) Icons.Default.Undo else Icons.Default.CheckCircle, null) }, onClick = { menu = false; onAlternarPago() })
                    DropdownMenuItem(text = { Text("Editar") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onEditar() })
                    DropdownMenuItem(text = { Text("Excluir", color = Color(0xFFE53935)) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFE53935)) }, onClick = { menu = false; onExcluir() })
                }
            }
        }
    }
}
