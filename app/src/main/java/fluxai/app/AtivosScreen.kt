package fluxai.app

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import fluxai.app.ui.theme.LocalAccentColor
import fluxai.app.ui.theme.LocalDarkTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// --- MODELO DE DADOS ATUALIZADO (Com a Placa) ---
data class Ativo(
    val id: String = "",
    val nome: String = "",
    val tipo: String = "Equipamento",
    val unidadeMedida: String = "Horas",
    val usoAtual: Double = 0.0,
    val mtbfPreventiva: Double = 0.0,
    val status: String = "Operando",
    val custoTotal: Double = 0.0, // Histórico vitalício (TCO)
    val placa: String = "" // NOVO: Identificação do veículo
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtivosScreen(
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
    val colorTextSecondary = Color(0xFF9CA3AF)
    val colorAccent = LocalAccentColor.current
    val colorDivider = if (isDark) Color(0xFF2C2C2C) else Color(0xFFE5E7EB)

    // =========================================================================
    // === CHAVE MESTRA DA SINCRONIZAÇÃO FAMILIAR ===
    // =========================================================================
    val sharedPref = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
    val workspaceUid = sharedPref.getString("workspace_uid", user.uid) ?: user.uid
    // =========================================================================

    val vm: AtivosViewModel = viewModel()
    LaunchedEffect(workspaceUid) { vm.observar(workspaceUid) }
    val ativosList by vm.ativos.collectAsStateWithLifecycle()
    var mostrarDialogCadastro by remember { mutableStateOf(false) }
    var ativoParaEditar by remember { mutableStateOf<Ativo?>(null) } // Controle de edição
    var mostrarFipe by remember { mutableStateOf(false) }

    // --- LEITURA EM TEMPO REAL DO BANCO ---
    // Atualizado para escutar o workspaceUid
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MenuLateral(drawerState, coroutineScope, "manutencao", onAbrirDashboard, onAbrirLancamento, onAbrirAnalytics, onAbrirSettings, onAbrirSobre, onAbrirManutencao, onLogout, onAbrirCartoes, onAbrirCaixinhas, onAbrirAssinaturas, onAbrirCelular)
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Ativos & manutenção", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colorTextPrimary) },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, "Abrir menu", tint = colorTextPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colorBg)
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { mostrarDialogCadastro = true }, containerColor = colorAccent, contentColor = Color.White, shape = CircleShape) {
                    Icon(Icons.Default.Add, contentDescription = "Novo Ativo")
                }
            },
            containerColor = colorBg
        ) { padding ->
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {

                // ===== RESUMO =====
                item {
                    val custoTotal = ativosList.sumOf { it.custoTotal }
                    val urgentes = ativosList.count { it.usoRelativo() >= 0.9f }
                    val atencao = ativosList.count { it.usoRelativo() in 0.7f..0.9f }
                    Surface(shape = RoundedCornerShape(20.dp), color = colorSurface, border = BorderStroke(1.dp, colorDivider), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp)) {
                            Text("Custo total de manutenção", fontSize = 12.sp, color = colorTextSecondary)
                            Text(moedaBR(custoTotal), fontSize = 28.sp, fontWeight = FontWeight.Black, color = colorTextPrimary)
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth()) {
                                ResumoAtivo("Itens", "${ativosList.size}", colorTextPrimary, colorTextSecondary, Modifier.weight(1f))
                                ResumoAtivo("Atenção", "$atencao", if (atencao > 0) Color(0xFFFB8C00) else colorTextPrimary, colorTextSecondary, Modifier.weight(1f))
                                ResumoAtivo("Revisão já", "$urgentes", if (urgentes > 0) Color(0xFFE53935) else colorTextPrimary, colorTextSecondary, Modifier.weight(1f))
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }

                if (ativosList.isEmpty()) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(88.dp).background(colorAccent.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Build, null, modifier = Modifier.size(40.dp), tint = colorAccent)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Nenhum item cadastrado", fontWeight = FontWeight.Bold, color = colorTextPrimary)
                            Text("Cadastre seu carro, moto ou equipamentos para acompanhar revisões e custos.", fontSize = 13.sp, color = colorTextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { mostrarDialogCadastro = true }, colors = ButtonDefaults.buttonColors(containerColor = colorAccent), shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Cadastrar item")
                            }
                        }
                    }
                } else {
                    item { Text("SEUS ITENS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colorTextSecondary, letterSpacing = 1.sp, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)) }
                    // Os que precisam de revisão aparecem primeiro
                    items(ativosList.sortedByDescending { it.usoRelativo() }, key = { it.id }) { ativo ->
                        CardAtivo(
                            ativo = ativo,
                            isDark = isDark,
                            colorAccent = colorAccent,
                            onAtualizarUso = { novoUso -> vm.atualizarUso(ativo, novoUso) },
                            onRegistrarGasto = { descricao, valor, zeraCiclo -> vm.registrarGasto(ativo, descricao, valor, zeraCiclo) },
                            onEditClick = { ativoParaEditar = ativo },
                            onDeleteClick = { vm.excluir(ativo) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // ===== FERRAMENTAS: consulta FIPE (recolhível) =====
                item {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp), color = colorSurface, border = BorderStroke(1.dp, colorDivider),
                        modifier = Modifier.fillMaxWidth().clickable { mostrarFipe = !mostrarFipe }
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(36.dp).background(Color(0xFF03A9F4).copy(alpha = 0.14f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PriceCheck, null, tint = Color(0xFF03A9F4), modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Consultar tabela FIPE", fontWeight = FontWeight.Medium, color = colorTextPrimary)
                                Text("Veja quanto seu veículo vale hoje", fontSize = 12.sp, color = colorTextSecondary)
                            }
                            Icon(if (mostrarFipe) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = colorTextSecondary)
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(visible = mostrarFipe) {
                        Column { Spacer(Modifier.height(8.dp)); ConsultaFipeCard() }
                    }
                    Spacer(Modifier.height(88.dp)) // espaço para o botão flutuante
                }
            }

            // --- DIALOG DE CADASTRO DE NOVO ATIVO ---
            if (mostrarDialogCadastro) {
                var nomeAtivo by remember { mutableStateOf("") }
                var tipoAtivo by remember { mutableStateOf("Veículo") }
                var unidade by remember { mutableStateOf("Km") }
                var mtbf by remember { mutableStateOf("") }
                var placa by remember { mutableStateOf("") }
                var salvando by remember { mutableStateOf(false) }

                AlertDialog(
                    onDismissRequest = { mostrarDialogCadastro = false },
                    containerColor = colorSurface,
                    title = { Text("Cadastrar Item", fontWeight = FontWeight.Black, color = colorTextPrimary) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = nomeAtivo, onValueChange = { nomeAtivo = it },
                                label = { Text("Nome (Ex: Meu Carro)") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = FormatoCampo
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = tipoAtivo == "Veículo", onClick = { tipoAtivo = "Veículo"; unidade = "Km" }, label = { Text("Veículo") })
                                FilterChip(selected = tipoAtivo == "Equipamento", onClick = { tipoAtivo = "Equipamento"; unidade = "Dias/Horas" }, label = { Text("Equipamento") })
                            }
                            if (tipoAtivo == "Veículo") {
                                OutlinedTextField(
                                    value = placa, onValueChange = { if(it.length <= 8) placa = it.uppercase() },
                                    label = { Text("Placa (Opcional)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = FormatoCampo
                                )
                            }
                            OutlinedTextField(
                                value = mtbf, onValueChange = { mtbf = it },
                                label = { Text("Limite Preventiva (Em $unidade)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                shape = FormatoCampo
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (nomeAtivo.isNotBlank() && mtbf.isNotBlank()) {
                                    salvando = true
                                    val novoAtivo = hashMapOf(
                                        "nome" to nomeAtivo,
                                        "tipo" to tipoAtivo,
                                        "unidadeMedida" to unidade,
                                        "usoAtual" to 0.0,
                                        "mtbfPreventiva" to (mtbf.paraValor() ?: 0.0),
                                        "status" to "Operando",
                                        "custoTotal" to 0.0,
                                        "placa" to placa
                                    )
                                    vm.criar(novoAtivo) { ok -> salvando = false; if (ok) mostrarDialogCadastro = false }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colorAccent),
                            enabled = !salvando
                        ) { Text("Salvar") }
                    },
                    dismissButton = { TextButton(onClick = { mostrarDialogCadastro = false }) { Text("Cancelar", color = colorTextSecondary) } }
                )
            }

            // --- DIALOG DE EDIÇÃO ---
            if (ativoParaEditar != null) {
                var editNome by remember { mutableStateOf(ativoParaEditar!!.nome) }
                var editMtbf by remember { mutableStateOf(formatarQtd(ativoParaEditar!!.mtbfPreventiva)) }
                var editPlaca by remember { mutableStateOf(ativoParaEditar!!.placa) }
                var salvandoEdit by remember { mutableStateOf(false) }

                AlertDialog(
                    onDismissRequest = { ativoParaEditar = null },
                    containerColor = colorSurface,
                    title = { Text("Editar Item", fontWeight = FontWeight.Black, color = colorTextPrimary) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = editNome, onValueChange = { editNome = it },
                                label = { Text("Nome") }, modifier = Modifier.fillMaxWidth(),
                                shape = FormatoCampo
                            )
                            if (ativoParaEditar!!.tipo == "Veículo") {
                                OutlinedTextField(
                                    value = editPlaca, onValueChange = { if(it.length <= 8) editPlaca = it.uppercase() },
                                    label = { Text("Placa") }, modifier = Modifier.fillMaxWidth(),
                                    shape = FormatoCampo
                                )
                            }
                            OutlinedTextField(
                                value = editMtbf, onValueChange = { editMtbf = it },
                                label = { Text("Limite Preventiva (${ativoParaEditar!!.unidadeMedida})") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                shape = FormatoCampo
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                salvandoEdit = true
                                val updates = hashMapOf<String, Any>(
                                    "nome" to editNome,
                                    "mtbfPreventiva" to (editMtbf.paraValor() ?: 0.0),
                                    "placa" to editPlaca
                                )
                                vm.editar(ativoParaEditar!!.id, updates) { ok -> salvandoEdit = false; if (ok) ativoParaEditar = null }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colorAccent),
                            enabled = !salvandoEdit
                        ) { Text("Atualizar") }
                    },
                    dismissButton = { TextButton(onClick = { ativoParaEditar = null }) { Text("Cancelar", color = colorTextSecondary) } }
                )
            }
        }
    }
}

@Composable
fun CardAtivo(
    ativo: Ativo,
    isDark: Boolean,
    colorAccent: Color,
    onAtualizarUso: (Double) -> Unit,
    onRegistrarGasto: (String, Double, Boolean) -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val corSuperficie = if (isDark) Color(0xFF1A1A1A) else Color.White
    val corTexto = if (isDark) Color.White else Color(0xFF1A1A1A)
    val corTextoFraco = Color(0xFF9CA3AF)
    val corBorda = if (isDark) Color(0xFF2C2C2C) else Color(0xFFE5E7EB)

    val progresso = ativo.usoRelativo()
    val (corStatus, textoStatus) = when {
        ativo.mtbfPreventiva <= 0 -> corTextoFraco to "Sem limite definido"
        progresso >= 1f -> Color(0xFFE53935) to "Revisão vencida"
        progresso >= 0.9f -> Color(0xFFE53935) to "Revisão necessária"
        progresso >= 0.7f -> Color(0xFFFB8C00) to "Atenção"
        else -> Color(0xFF43A047) to "Em dia"
    }
    val unidade = ativo.unidadeMedida.lowercase()
    val restante = ativo.mtbfPreventiva - ativo.usoAtual

    var mostrarAtualizarDialog by remember { mutableStateOf(false) }
    var mostrarGastoDialog by remember { mutableStateOf(false) }
    var confirmarExclusao by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    Surface(shape = RoundedCornerShape(20.dp), color = corSuperficie, border = BorderStroke(1.dp, if (progresso >= 0.9f) corStatus.copy(alpha = 0.5f) else corBorda), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(colorAccent.copy(alpha = 0.14f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(if (ativo.tipo == "Veículo") Icons.Default.DirectionsCar else Icons.Default.HomeRepairService, null, tint = colorAccent)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(ativo.nome, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = corTexto)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                        Surface(shape = RoundedCornerShape(50), color = corStatus.copy(alpha = 0.12f)) {
                            Text(textoStatus, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = corStatus, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                        if (ativo.placa.isNotBlank()) {
                            Spacer(Modifier.width(6.dp))
                            Surface(shape = RoundedCornerShape(4.dp), color = if (isDark) Color(0xFF333333) else Color(0xFFEEEEEE)) {
                                Text(ativo.placa, fontSize = 10.sp, fontWeight = FontWeight.Black, color = if (isDark) Color.LightGray else Color.DarkGray, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Mais opções", tint = corTextoFraco) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, modifier = Modifier.background(corSuperficie)) {
                        DropdownMenuItem(text = { Text("Editar dados", color = corTexto) }, leadingIcon = { Icon(Icons.Default.Edit, null, tint = corTextoFraco) }, onClick = { onEditClick(); menuOpen = false })
                        DropdownMenuItem(text = { Text("Excluir", color = Color(0xFFE53935)) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFE53935)) }, onClick = { confirmarExclusao = true; menuOpen = false })
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Uso e próxima revisão
            if (ativo.mtbfPreventiva > 0) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (restante > 0) "Faltam ${formatarQtd(restante)} $unidade" else "Passou ${formatarQtd(-restante)} $unidade",
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (restante > 0) corTexto else corStatus, modifier = Modifier.weight(1f)
                    )
                    Text("${formatarQtd(ativo.usoAtual)} / ${formatarQtd(ativo.mtbfPreventiva)} $unidade", fontSize = 12.sp, color = corTextoFraco)
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progresso.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = corStatus, trackColor = if (isDark) Color(0xFF333333) else Color(0xFFEEEEEE),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            } else {
                Text("Uso atual: ${formatarQtd(ativo.usoAtual)} $unidade", fontSize = 13.sp, color = corTextoFraco)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Custo acumulado", fontSize = 12.sp, color = corTextoFraco, modifier = Modifier.weight(1f))
                Text(moedaBR(ativo.custoTotal), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = corTexto)
            }

            // Ações principais à vista
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { mostrarAtualizarDialog = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, corBorda), colors = ButtonDefaults.outlinedButtonColors(contentColor = corTexto)
                ) { Icon(Icons.Default.Speed, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Atualizar uso", fontSize = 13.sp) }
                Button(
                    onClick = { mostrarGastoDialog = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colorAccent)
                ) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Lançar gasto", fontSize = 13.sp) }
            }
        }
    }

    if (confirmarExclusao) {
        AlertDialog(
            onDismissRequest = { confirmarExclusao = false },
            containerColor = corSuperficie,
            icon = { Icon(Icons.Default.DeleteForever, null, tint = Color(0xFFE53935)) },
            title = { Text("Excluir ${ativo.nome}?", color = corTexto) },
            text = { Text("O histórico de custos deste item será perdido. Os gastos já lançados no Dashboard continuam lá.", color = corTextoFraco) },
            confirmButton = { Button(onClick = { onDeleteClick(); confirmarExclusao = false }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { confirmarExclusao = false }) { Text("Cancelar", color = corTextoFraco) } }
        )
    }

    if (mostrarAtualizarDialog) {
        var novoUsoStr by remember { mutableStateOf(formatarQtd(ativo.usoAtual)) }
        AlertDialog(
            onDismissRequest = { mostrarAtualizarDialog = false },
            containerColor = corSuperficie,
            title = { Text("Atualizar uso", color = corTexto) },
            text = {
                OutlinedTextField(
                    value = novoUsoStr, onValueChange = { novoUsoStr = it },
                    label = { Text("Uso atual (${ativo.unidadeMedida})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colorAccent, unfocusedBorderColor = corBorda, focusedTextColor = corTexto, unfocusedTextColor = corTexto),
                    shape = FormatoCampo
                )
            },
            confirmButton = {
                Button(onClick = {
                    novoUsoStr.paraValor()?.let(onAtualizarUso)
                    mostrarAtualizarDialog = false
                }, colors = ButtonDefaults.buttonColors(containerColor = colorAccent)) { Text("Atualizar") }
            },
            dismissButton = { TextButton(onClick = { mostrarAtualizarDialog = false }) { Text("Cancelar", color = corTextoFraco) } }
        )
    }

    if (mostrarGastoDialog) {
        var descGasto by remember { mutableStateOf("") }
        var valorGastoStr by remember { mutableStateOf("") }
        var isPreventiva by remember { mutableStateOf(false) }
        val coresCampo = OutlinedTextFieldDefaults.colors(focusedBorderColor = colorAccent, unfocusedBorderColor = corBorda, focusedTextColor = corTexto, unfocusedTextColor = corTexto)

        AlertDialog(
            onDismissRequest = { mostrarGastoDialog = false },
            containerColor = corSuperficie,
            title = { Text("Lançar gasto", color = corTexto) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("O valor soma no custo de ${ativo.nome} e entra no Dashboard como despesa paga.", fontSize = 12.sp, color = corTextoFraco)
                    OutlinedTextField(value = descGasto, onValueChange = { descGasto = it }, label = { Text("Descrição (ex.: troca de óleo, IPVA)") }, modifier = Modifier.fillMaxWidth(), colors = coresCampo, shape = FormatoCampo)
                    OutlinedTextField(
                        value = valorGastoStr, onValueChange = { valorGastoStr = it.replace('.', ',') }, label = { Text("Valor (R$)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), colors = coresCampo, shape = FormatoCampo
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { isPreventiva = !isPreventiva }) {
                        Checkbox(checked = isPreventiva, onCheckedChange = { isPreventiva = it }, colors = CheckboxDefaults.colors(checkedColor = colorAccent))
                        Text("Foi a revisão: zerar o ciclo de uso", fontSize = 13.sp, color = corTexto)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val valor = valorGastoStr.paraValor()
                    if (valor != null && descGasto.isNotBlank()) {
                        onRegistrarGasto(descGasto, valor, isPreventiva)
                        mostrarGastoDialog = false
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = colorAccent)) { Text("Lançar") }
            },
            dismissButton = { TextButton(onClick = { mostrarGastoDialog = false }) { Text("Cancelar", color = corTextoFraco) } }
        )
    }
}

// Quanto do ciclo de manutenção já foi usado (0 = novo, 1 = no limite)
fun Ativo.usoRelativo(): Float = if (mtbfPreventiva > 0) (usoAtual / mtbfPreventiva).toFloat() else 0f

// 12345.0 -> "12.345"; 12.5 -> "12,5"
fun formatarQtd(v: Double): String = java.text.NumberFormat.getNumberInstance(Locale("pt", "BR")).apply { maximumFractionDigits = 1 }.format(v)

private fun moedaBR(v: Double): String = java.text.NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(v)

@Composable
private fun ResumoAtivo(titulo: String, valor: String, cor: Color, corTitulo: Color, modifier: Modifier) {
    Column(modifier) {
        Text(valor, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = cor)
        Text(titulo, fontSize = 11.sp, color = corTitulo)
    }
}

// === INJETADO: Componente Isolado FIPE OTIMIZADO ===
@Composable
fun ConsultaFipeCard() {
    val coroutineScope = rememberCoroutineScope()
    val isDark = LocalDarkTheme.current
    val colorSurface = if (isDark) Color(0xFF1A1A1A) else Color.White
    val colorTextPrimary = if (isDark) Color.White else Color(0xFF1A1A1A)
    val colorTextSecondary = Color(0xFF9CA3AF)
    val colorAccent = LocalAccentColor.current
    val colorDivider = if (isDark) Color(0xFF2C2C2C) else Color(0xFFE0E0E0)

    var codigoFipe by remember { mutableStateOf("") }
    var carregando by remember { mutableStateOf(false) }
    var veiculoNome by remember { mutableStateOf("") }
    var listaPrecos by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var erroBusca by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colorSurface),
        border = BorderStroke(1.dp, colorDivider)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(colorAccent.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.DirectionsCar,
                        contentDescription = "FIPE",
                        tint = colorAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Avaliação Patrimonial (FIPE)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colorTextPrimary)
                    Text("Consulte a desvalorização real", fontSize = 11.sp, color = colorTextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = codigoFipe,
                    onValueChange = { codigoFipe = it },
                    label = { Text("Cód. FIPE (Ex: 001004-9)", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colorAccent,
                        unfocusedBorderColor = colorDivider,
                        focusedTextColor = colorTextPrimary,
                        unfocusedTextColor = colorTextPrimary
                    ),
                    shape = FormatoCampo
                )
                Button(
                    onClick = {
                        if (codigoFipe.isNotBlank()) {
                            carregando = true
                            erroBusca = ""
                            veiculoNome = ""
                            listaPrecos = emptyList()

                            coroutineScope.launch {
                                var codigoFormatadoForError = ""
                                val resultado = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    try {
                                        // === FORMATAÇÃO E FALLBACK PARALLELUM API ===
                                        val apenasNumeros = codigoFipe.replace(Regex("[^0-9]"), "")
                                        val codigoFormatado = if (apenasNumeros.isNotEmpty()) {
                                            val comZeros = apenasNumeros.padStart(7, '0')
                                            "${comZeros.substring(0, 6)}-${comZeros.substring(6)}"
                                        } else {
                                            codigoFipe.trim()
                                        }
                                        codigoFormatadoForError = codigoFormatado

                                        var foundVehicleType = ""
                                        var yearsJson: String? = null

                                        val types = listOf("cars", "motorcycles", "trucks")
                                        for (t in types) {
                                            val urlStr = "https://fipe.parallelum.com.br/api/v2/$t/$codigoFormatado/years"
                                            try {
                                                val conn = java.net.URL(urlStr).openConnection() as java.net.HttpURLConnection
                                                conn.requestMethod = "GET"
                                                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                                                conn.connectTimeout = 5000
                                                if (conn.responseCode == 200) {
                                                    yearsJson = conn.inputStream.bufferedReader().readText()
                                                    foundVehicleType = t
                                                    break
                                                }
                                            } catch (e: Exception) {}
                                        }

                                        if (yearsJson != null) {
                                            val yearsArray = org.json.JSONArray(yearsJson)
                                            val precosTemp = mutableListOf<Pair<String, String>>()
                                            var nomeVeiculoTemp = ""

                                            val maxToFetch = minOf(3, yearsArray.length())
                                            for (i in 0 until maxToFetch) {
                                                val yearId = yearsArray.getJSONObject(i).getString("code")
                                                try {
                                                    val urlStr = "https://fipe.parallelum.com.br/api/v2/$foundVehicleType/$codigoFormatado/years/$yearId"
                                                    val conn = java.net.URL(urlStr).openConnection() as java.net.HttpURLConnection
                                                    conn.requestMethod = "GET"
                                                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                                                    conn.connectTimeout = 5000
                                                    if (conn.responseCode == 200) {
                                                        val priceJson = conn.inputStream.bufferedReader().readText()
                                                        val priceObj = org.json.JSONObject(priceJson)
                                                        if (nomeVeiculoTemp.isEmpty()) {
                                                            nomeVeiculoTemp = "${priceObj.getString("brand")} ${priceObj.getString("model")}"
                                                        }
                                                        precosTemp.add(Pair(priceObj.getInt("modelYear").toString(), priceObj.getString("price")))
                                                    }
                                                } catch (e: Exception) {}
                                            }
                                            Pair(nomeVeiculoTemp, precosTemp)
                                        } else {
                                            null
                                        }
                                    } catch (e: Exception) {
                                        null
                                    }
                                }

                                if (resultado != null) {
                                    veiculoNome = resultado.first
                                    listaPrecos = resultado.second.sortedByDescending { it.first.toIntOrNull() ?: 0 }
                                    if (listaPrecos.isEmpty()) {
                                        erroBusca = "Código ($codigoFormatadoForError) encontrou o veículo, mas falhou ao buscar preços."
                                    }
                                } else {
                                    erroBusca = "Código ($codigoFormatadoForError) não encontrado."
                                }
                                carregando = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colorAccent),
                    modifier = Modifier.height(56.dp).padding(top = 6.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (carregando) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Default.Search, contentDescription = "Buscar", tint = Color.White)
                    }
                }
            }

            if (erroBusca.isNotEmpty()) {
                Text(erroBusca, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }

            if (veiculoNome.isNotEmpty() && listaPrecos.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = colorDivider)
                Spacer(modifier = Modifier.height(12.dp))

                Text(veiculoNome, fontWeight = FontWeight.Black, color = colorAccent, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(8.dp))

                listaPrecos.forEach { (ano, valor) ->
                    val textoAno = if (ano == "32000") "Zero KM" else "Ano $ano"
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(textoAno, fontSize = 13.sp, color = colorTextSecondary)
                        Text(valor, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colorTextPrimary)
                    }
                }
            }
        }
    }
}