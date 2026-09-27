package fluxai.app

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.functions.functions
import fluxai.app.ui.theme.LocalDarkTheme
import kotlinx.coroutines.launch
import androidx.core.content.edit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    onAbrirDashboard: () -> Unit,
    onAbrirLancamento: () -> Unit,
    onAbrirAnalytics: () -> Unit,
    onAbrirSettings: () -> Unit,
    onAccountDeleted: () -> Unit,
    onAbrirSobre: () -> Unit,
    onAbrirManutencao: () -> Unit,
    onAbrirCartoes: () -> Unit,
    onAbrirCaixinhas: () -> Unit,
    onAbrirAssinaturas: () -> Unit,
    onAbrirCelular: () -> Unit
) {
    val context = LocalContext.current
    val usuario = Firebase.auth.currentUser

    // VARIÁVEIS DO MENU LATERAL
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // ESTADOS
    var nome by remember { mutableStateOf(usuario?.displayName ?: "") }
    var email by remember { mutableStateOf(usuario?.email ?: "") }

    var salvandoPerfil by remember { mutableStateOf(false) }
    var editandoPerfil by remember { mutableStateOf(false) }
    var enviandoEmailSenha by remember { mutableStateOf(false) }
    var mostrarDialogExclusao by remember { mutableStateOf(false) }
    var excluindoConta by remember { mutableStateOf(false) }

    // =========================================================================
    // === INJETADO: ESTADOS DOS MODAIS DE PROJETO E CATEGORIA ===
    // =========================================================================
    var mostrarDialogCategoria by remember { mutableStateOf(false) }
    var nomeNovaCategoria by remember { mutableStateOf("") }
    var iconeNovaCategoria by remember { mutableStateOf("estrela") }
    var editandoIconeId by remember { mutableStateOf<String?>(null) } // categoria com o seletor de ícone aberto

    var mostrarDialogProjeto by remember { mutableStateOf(false) }
    var nomeNovoProjeto by remember { mutableStateOf("") }
    var orcamentoNovoProjeto by remember { mutableStateOf("") }
    // =========================================================================

    // =========================================================================
    // === INJETADO: ESTADOS DA CONTA CONJUNTA E COR TEMA DA INTERFACE ===
    // =========================================================================
    val sharedPref = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
    var workspaceUid by remember { mutableStateOf(sharedPref.getString("workspace_uid", usuario?.uid ?: "") ?: usuario?.uid ?: "") }

    // Estado da Cor Tema Personalizada (Padrão: Roxo 0xFF7E57C2)
    var selectedColorHex by remember { mutableLongStateOf(sharedPref.getLong("theme_color_hex", 0xFF7E57C2L)) }
    // =========================================================================

    // =========================================
    // TEMA DINÂMICO (Claro/Escuro + Cor de Destaque Personalizada)
    // =========================================
    val isDark = LocalDarkTheme.current

    val colorAccent = Color(selectedColorHex)
    val colorBg = if (isDark) Color(0xFF121212) else Color(0xFFF8F9FA)
    val colorSurface = if (isDark) Color(0xFF1E1E1E) else Color.White
    val colorTextPrimary = if (isDark) Color(0xFFF9FAFB) else Color(0xFF1E1E1E)
    val colorTextSecondary = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val colorDivider = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB)
    val colorDanger = Color(0xFFEF5350) // Vermelho para a Zona de Perigo

    // =========================================================================
    // === INJETADO: BUSCAR CATEGORIAS E PROJETOS EM TEMPO REAL ===
    // =========================================================================
    val vm: ConfiguracoesViewModel = viewModel()
    LaunchedEffect(workspaceUid) { vm.observar(workspaceUid) }
    val categoriasList by vm.categorias.collectAsStateWithLifecycle()
    val projetosList by vm.projetos.collectAsStateWithLifecycle()

    // Automação e dados
    var leituraAtiva by remember { mutableStateOf(leituraNotificacoesAtiva(context)) }
    var lembreteAtivo by remember { mutableStateOf(lembreteDiarioAtivo(context)) }
    var horaLembrete by remember { mutableIntStateOf(horaLembreteDiario(context)) }
    var processandoDados by remember { mutableStateOf(false) }
    var temExemplo by remember { mutableStateOf(false) }
    var backupParaRestaurar by remember { mutableStateOf<ResumoBackup?>(null) }
    LaunchedEffect(workspaceUid) { temExemplo = temDadosExemplo(workspaceUid) }
    // Ao voltar das configurações do Android, atualiza o estado da leitura de notificações
    val ciclo = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(ciclo) {
        val observador = androidx.lifecycle.LifecycleEventObserver { _, evento ->
            if (evento == androidx.lifecycle.Lifecycle.Event.ON_RESUME) leituraAtiva = leituraNotificacoesAtiva(context)
        }
        ciclo.lifecycle.addObserver(observador)
        onDispose { ciclo.lifecycle.removeObserver(observador) }
    }
    val criarBackup = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            processandoDados = true
            coroutineScope.launch {
                runCatching { salvarBackupEm(context, uri, workspaceUid) }
                    .onSuccess { Toast.makeText(context, "Backup salvo com $it registros.", Toast.LENGTH_LONG).show(); registrarAuditoria("Gerou backup completo") }
                    .onFailure { Toast.makeText(context, "Falha no backup: ${it.message}", Toast.LENGTH_LONG).show() }
                processandoDados = false
            }
        }
    }
    val abrirBackup = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) coroutineScope.launch {
            runCatching { lerBackup(context, uri) }
                .onSuccess { backupParaRestaurar = it }
                .onFailure { Toast.makeText(context, it.message ?: "Arquivo inválido.", Toast.LENGTH_LONG).show() }
        }
    }
    // =========================================================================

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MenuLateral(
                drawerState = drawerState,
                coroutineScope = coroutineScope,
                rotaAtual = "settings", // Rota atual para o menu saber onde estamos
                onAbrirDashboard = onAbrirDashboard,
                onAbrirLancamento = onAbrirLancamento,
                onAbrirAnalytics = onAbrirAnalytics,
                onAbrirSettings = { }, // Já estamos aqui
                onAbrirSobre = onAbrirSobre,
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
                    title = { Text("Configurações", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colorTextPrimary) },
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val cores = CoresConfig(colorSurface, colorTextPrimary, colorTextSecondary, colorDivider)

                // ===== CABEÇALHO DO PERFIL =====
                Surface(shape = RoundedCornerShape(20.dp), color = colorSurface, border = BorderStroke(1.dp, colorDivider), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(58.dp).background(colorAccent.copy(alpha = 0.35f), CircleShape).padding(2.dp), contentAlignment = Alignment.Center) {
                                fluxai.app.ui.components.SmartAvatar(photoUrl = usuario?.photoUrl?.toString(), size = 54.dp)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(usuario?.displayName?.ifBlank { null } ?: "Sem nome", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colorTextPrimary)
                                Text(usuario?.email ?: "", fontSize = 12.sp, color = colorTextSecondary)
                            }
                            FilledTonalIconButton(
                                onClick = { editandoPerfil = !editandoPerfil },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = colorAccent.copy(alpha = 0.12f), contentColor = colorAccent)
                            ) { Icon(if (editandoPerfil) Icons.Default.Close else Icons.Default.Edit, "Editar perfil") }
                        }

                        // Formulário do perfil só aparece ao tocar em editar
                        androidx.compose.animation.AnimatedVisibility(visible = editandoPerfil) {
                            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = nome, onValueChange = { nome = it },
                                    label = { Text("Nome de exibição", color = colorTextSecondary) },
                                    modifier = Modifier.fillMaxWidth(),
                                    leadingIcon = { Icon(Icons.Default.Person, null, tint = colorTextSecondary) },
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colorAccent, unfocusedBorderColor = colorDivider, focusedTextColor = colorTextPrimary, unfocusedTextColor = colorTextPrimary),
                                    singleLine = true, shape = FormatoCampo
                                )
                                OutlinedTextField(
                                    value = email, onValueChange = { email = it },
                                    label = { Text("E-mail de acesso", color = colorTextSecondary) },
                                    modifier = Modifier.fillMaxWidth(),
                                    leadingIcon = { Icon(Icons.Default.Email, null, tint = colorTextSecondary) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colorAccent, unfocusedBorderColor = colorDivider, focusedTextColor = colorTextPrimary, unfocusedTextColor = colorTextPrimary),
                                    singleLine = true, shape = FormatoCampo
                                )
                                Button(
                                    onClick = {
                                        if (usuario != null) {
                                            salvandoPerfil = true
                                            val profileUpdates = userProfileChangeRequest { displayName = nome.trim() }
                                            usuario.updateProfile(profileUpdates).addOnCompleteListener { taskNome ->
                                                if (taskNome.isSuccessful) {
                                                    if (email.trim() != usuario.email) {
                                                        usuario.verifyBeforeUpdateEmail(email.trim()).addOnCompleteListener { taskEmail ->
                                                            salvandoPerfil = false
                                                            if (taskEmail.isSuccessful) Toast.makeText(context, "Verifique sua nova caixa de e-mail para confirmar a alteração.", Toast.LENGTH_LONG).show()
                                                            else Toast.makeText(context, "Erro ao alterar e-mail. Tente relogar.", Toast.LENGTH_LONG).show()
                                                        }
                                                    } else {
                                                        salvandoPerfil = false
                                                        editandoPerfil = false
                                                        Toast.makeText(context, "Perfil atualizado!", Toast.LENGTH_SHORT).show()
                                                    }
                                                } else salvandoPerfil = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = colorAccent),
                                    enabled = !salvandoPerfil
                                ) {
                                    if (salvandoPerfil) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    else Text("Salvar alterações", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }

                // ===== CONTA =====
                TituloSecaoConfig("Conta", colorTextSecondary)
                GrupoConfig(cores) {
                    LinhaConfig(
                        Icons.Default.LockReset, Color(0xFF5C6BC0), "Alterar senha",
                        if (enviandoEmailSenha) "Enviando link..." else "Enviamos um link seguro para o seu e-mail", cores
                    ) {
                        if (usuario?.email != null && !enviandoEmailSenha) {
                            enviandoEmailSenha = true
                            Firebase.auth.sendPasswordResetEmail(usuario.email!!)
                                .addOnSuccessListener { enviandoEmailSenha = false; Toast.makeText(context, "Link enviado para ${usuario.email}", Toast.LENGTH_LONG).show() }
                                .addOnFailureListener { enviandoEmailSenha = false; Toast.makeText(context, "Erro ao enviar e-mail.", Toast.LENGTH_SHORT).show() }
                        }
                    }
                }

                // ===== APARÊNCIA =====
                TituloSecaoConfig("Aparência", colorTextSecondary)
                GrupoConfig(cores) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconeConfig(Icons.Default.Palette, colorAccent)
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text("Cor de destaque", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colorTextPrimary)
                                Text("Cor principal da interface", fontSize = 12.sp, color = colorTextSecondary)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        val paletaCores = listOf(0xFF7E57C2L to "Roxo", 0xFF2E7D32L to "Verde", 0xFF0288D1L to "Azul", 0xFFF57C00L to "Laranja", 0xFFD81B60L to "Rosa", 0xFF00897BL to "Teal")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            paletaCores.forEach { (hexColor, nomeCor) ->
                                val selecionada = selectedColorHex == hexColor
                                Box(
                                    modifier = Modifier.size(36.dp)
                                        .border(2.dp, if (selecionada) Color(hexColor) else Color.Transparent, CircleShape)
                                        .padding(4.dp)
                                        .background(Color(hexColor), CircleShape)
                                        .clickable {
                                            selectedColorHex = hexColor
                                            sharedPref.edit { putLong("theme_color_hex", hexColor) }
                                            Toast.makeText(context, "Tema $nomeCor aplicado!", Toast.LENGTH_SHORT).show()
                                        },
                                    contentAlignment = Alignment.Center
                                ) { if (selecionada) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                            }
                        }
                    }
                }

                // ===== ORGANIZAÇÃO =====
                TituloSecaoConfig("Organização", colorTextSecondary)
                GrupoConfig(cores) {
                    LinhaConfig(
                        Icons.Default.Category, Color(0xFFE91E63), "Categorias",
                        if (categoriasList.isEmpty()) "Crie categorias com ícone próprio" else "${categoriasList.size} personalizada${if (categoriasList.size > 1) "s" else ""}", cores
                    ) { mostrarDialogCategoria = true }
                    DivisorConfig(cores)
                    LinhaConfig(
                        Icons.Default.Work, Color(0xFF03A9F4), "Projetos e viagens",
                        if (projetosList.isEmpty()) "Isole gastos de viagens ou obras" else "${projetosList.size} projeto${if (projetosList.size > 1) "s" else ""}", cores
                    ) { mostrarDialogProjeto = true }
                }

                // ===== AUTOMAÇÃO =====
                TituloSecaoConfig("Automação", colorTextSecondary)
                GrupoConfig(cores) {
                    LinhaConfig(
                        Icons.Default.NotificationsActive, Color(0xFF26A69A), "Compras pelas notificações do banco",
                        if (leituraAtiva) "Sugestões aparecem no Dashboard para confirmar" else "Desativada · toque para ativar", cores,
                        trailing = { Text(if (leituraAtiva) "Ativa" else "Ativar", color = if (leituraAtiva) Color(0xFF26A69A) else colorAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    ) { abrirPermissaoNotificacoes(context) }
                    DivisorConfig(cores)
                    LinhaConfig(
                        Icons.Default.Alarm, Color(0xFFFB8C00), "Lembrete diário",
                        if (lembreteAtivo) "Às ${horaLembrete}h, se nada foi registrado no dia" else "Desligado", cores,
                        trailing = {
                            Switch(checked = lembreteAtivo, onCheckedChange = { lembreteAtivo = it; configurarLembreteDiario(context, it, horaLembrete) },
                                colors = SwitchDefaults.colors(checkedTrackColor = colorAccent))
                        }
                    ) { lembreteAtivo = !lembreteAtivo; configurarLembreteDiario(context, lembreteAtivo, horaLembrete) }
                    if (lembreteAtivo) {
                        Row(Modifier.fillMaxWidth().padding(start = 66.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(12, 18, 20, 22).forEach { h ->
                                FilterChip(
                                    selected = horaLembrete == h, onClick = { horaLembrete = h; configurarLembreteDiario(context, true, h) },
                                    label = { Text("${h}h") }, shape = RoundedCornerShape(10.dp),
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colorAccent.copy(alpha = 0.14f), selectedLabelColor = colorAccent)
                                )
                            }
                        }
                    }
                }

                // ===== SEUS DADOS =====
                TituloSecaoConfig("Seus dados", colorTextSecondary)
                GrupoConfig(cores) {
                    LinhaConfig(
                        Icons.Default.CloudDownload, Color(0xFF1E88E5), "Fazer backup completo",
                        if (processandoDados) "Aguarde..." else "Salva tudo num arquivo que você guarda onde quiser", cores
                    ) { if (!processandoDados) criarBackup.launch("FluxAi_backup_${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())}.json") }
                    DivisorConfig(cores)
                    LinhaConfig(Icons.Default.Restore, Color(0xFF5C6BC0), "Restaurar backup", "Traz de volta os dados de um arquivo de backup", cores) {
                        if (!processandoDados) abrirBackup.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                    }
                    DivisorConfig(cores)
                    LinhaConfig(
                        Icons.Default.Science, Color(0xFF8D6E63), if (temExemplo) "Remover dados de exemplo" else "Carregar dados de exemplo",
                        if (temExemplo) "Apaga só o que foi criado como exemplo" else "Preenche o mês com lançamentos fictícios para explorar", cores
                    ) {
                        if (!processandoDados) {
                            processandoDados = true
                            coroutineScope.launch {
                                runCatching { if (temExemplo) removerDadosExemplo(workspaceUid) else carregarDadosExemplo(workspaceUid) }
                                    .onSuccess {
                                        Toast.makeText(context, if (temExemplo) "Dados de exemplo removidos." else "Dados de exemplo carregados.", Toast.LENGTH_SHORT).show()
                                        temExemplo = !temExemplo
                                    }
                                    .onFailure { Toast.makeText(context, "Não foi possível: ${it.message}", Toast.LENGTH_LONG).show() }
                                processandoDados = false
                            }
                        }
                    }
                }

                // ===== CONTA CONJUNTA =====
                TituloSecaoConfig("Conta conjunta", colorTextSecondary)
                SecaoContaConjunta(
                    workspaceUid = workspaceUid,
                    onWorkspaceChange = { workspaceUid = it },
                    colorAccent = colorAccent, colorBg = colorBg, colorSurface = colorSurface,
                    colorTextPrimary = colorTextPrimary, colorTextSecondary = colorTextSecondary,
                    colorDivider = colorDivider, colorDanger = colorDanger
                )

                // ===== ZONA DE PERIGO =====
                TituloSecaoConfig("Zona de perigo", colorDanger)
                GrupoConfig(cores) {
                    LinhaConfig(Icons.Default.DeleteForever, colorDanger, "Excluir minha conta", "Apaga todos os seus dados (LGPD)", cores, corTitulo = colorDanger) { mostrarDialogExclusao = true }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // =========================================================================
        // === INJETADO: MODAIS DE CATEGORIA E PROJETOS ===
        // =========================================================================

        if (mostrarDialogCategoria) {
            AlertDialog(
                onDismissRequest = { mostrarDialogCategoria = false },
                containerColor = colorSurface,
                title = { Text("Categorias Customizadas", fontWeight = FontWeight.Bold, color = colorTextPrimary) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = nomeNovaCategoria,
                            onValueChange = { nomeNovaCategoria = it },
                            label = { Text("Nova Categoria") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(iconePorChave(iconeNovaCategoria), null, tint = colorAccent) },
                            trailingIcon = {
                                IconButton(onClick = {
                                    if (nomeNovaCategoria.isNotBlank()) {
                                        vm.criarCategoria(nomeNovaCategoria, iconeNovaCategoria)
                                        nomeNovaCategoria = ""
                                        iconeNovaCategoria = "estrela"
                                    }
                                }) { Icon(Icons.Default.Add, "Adicionar", tint = colorAccent) }
                            },
                            shape = FormatoCampo
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Ícone", fontSize = 12.sp, color = colorTextSecondary)
                        Spacer(Modifier.height(6.dp))
                        SeletorIconeCategoria(
                            selecionado = iconeNovaCategoria,
                            corDestaque = colorAccent,
                            corIcone = colorTextSecondary,
                            onSelecionar = { iconeNovaCategoria = it },
                            modifier = Modifier.height(150.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        if (categoriasList.isNotEmpty()) Text("Suas categorias (toque no ícone para trocar)", fontSize = 12.sp, color = colorTextSecondary)
                        LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                            items(categoriasList, key = { it.id }) { cat ->
                                Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.size(36.dp).background(corCategoria(cat.nome).copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                            .clickable { editandoIconeId = if (editandoIconeId == cat.id) null else cat.id },
                                        contentAlignment = Alignment.Center
                                    ) { Icon(iconePorChave(cat.icone), "Trocar ícone", tint = corCategoria(cat.nome), modifier = Modifier.size(20.dp)) }
                                    Spacer(Modifier.width(12.dp))
                                    Text(cat.nome, color = colorTextPrimary, modifier = Modifier.weight(1f))
                                    IconButton(
                                        onClick = {
                                            vm.excluirCategoria(cat.id)
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, "Excluir", tint = Color.Red.copy(0.6f))
                                    }
                                }
                                if (editandoIconeId == cat.id) {
                                    SeletorIconeCategoria(
                                        selecionado = cat.icone,
                                        corDestaque = colorAccent,
                                        corIcone = colorTextSecondary,
                                        onSelecionar = { novo ->
                                            vm.trocarIconeCategoria(cat.id, novo)
                                            editandoIconeId = null
                                        },
                                        modifier = Modifier.height(150.dp).padding(bottom = 8.dp)
                                    )
                                }
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { mostrarDialogCategoria = false }) { Text("Fechar") } }
            )
        }

        if (mostrarDialogProjeto) {
            AlertDialog(
                onDismissRequest = { mostrarDialogProjeto = false },
                containerColor = colorSurface,
                title = { Text("Projetos Especiais", fontWeight = FontWeight.Bold, color = colorTextPrimary) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = nomeNovoProjeto,
                            onValueChange = { nomeNovoProjeto = it },
                            label = { Text("Nome do Projeto/Viagem") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = FormatoCampo
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = orcamentoNovoProjeto,
                            onValueChange = { orcamentoNovoProjeto = it },
                            label = { Text("Orçamento Total (Opcional)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = {
                                    if (nomeNovoProjeto.isNotBlank()) {
                                        val orc = orcamentoNovoProjeto.paraValor() ?: 0.0
                                        vm.criarProjeto(nomeNovoProjeto, orc)
                                        nomeNovoProjeto = ""
                                        orcamentoNovoProjeto = ""
                                    }
                                }) { Icon(Icons.Default.Save, "Salvar", tint = Color(0xFF03A9F4)) }
                            },
                            shape = FormatoCampo
                        )
                        Spacer(Modifier.height(16.dp))
                        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                            items(projetosList) { proj ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(proj.nome, color = colorTextPrimary, fontWeight = FontWeight.Bold)
                                        if (proj.orcamento > 0) {
                                            Text("R$ ${proj.orcamento}", color = colorTextSecondary, fontSize = 12.sp)
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            vm.excluirProjeto(proj.id)
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, "Excluir", tint = Color.Red.copy(0.6f))
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { mostrarDialogProjeto = false }) { Text("Fechar") } }
            )
        }

        backupParaRestaurar?.let { b ->
            val data = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("pt", "BR")).format(java.util.Date(b.geradoEm))
            AlertDialog(
                onDismissRequest = { if (!processandoDados) backupParaRestaurar = null },
                containerColor = colorSurface,
                icon = { Icon(Icons.Default.Restore, null, tint = colorAccent) },
                title = { Text("Restaurar backup?", fontWeight = FontWeight.Bold, color = colorTextPrimary) },
                text = {
                    Text(
                        "Backup de $data com ${b.total} registros (${b.porColecao["despesas"] ?: 0} lançamentos). " +
                            "Registros que já existem são substituídos pela versão do backup; o que foi criado depois continua.",
                        color = colorTextSecondary
                    )
                },
                confirmButton = {
                    Button(
                        enabled = !processandoDados,
                        onClick = {
                            processandoDados = true
                            coroutineScope.launch {
                                runCatching { restaurarBackup(workspaceUid, b) }
                                    .onSuccess { Toast.makeText(context, "Backup restaurado.", Toast.LENGTH_LONG).show(); registrarAuditoria("Restaurou backup") }
                                    .onFailure { Toast.makeText(context, "Falha ao restaurar: ${it.message}", Toast.LENGTH_LONG).show() }
                                processandoDados = false
                                backupParaRestaurar = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colorAccent)
                    ) { if (processandoDados) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp) else Text("Restaurar") }
                },
                dismissButton = { TextButton(onClick = { backupParaRestaurar = null }, enabled = !processandoDados) { Text("Cancelar", color = colorTextSecondary) } }
            )
        }

        // =========================================
        // DIALOG DE CONFIRMAÇÃO DE EXCLUSÃO
        // =========================================
        if (mostrarDialogExclusao) {
            AlertDialog(
                onDismissRequest = { mostrarDialogExclusao = false },
                containerColor = colorSurface,
                titleContentColor = colorTextPrimary,
                textContentColor = colorTextSecondary,
                title = { Text("Atenção: Ação Irreversível", fontWeight = FontWeight.Bold) },
                text = {
                    Text("Tem certeza que deseja excluir sua conta? Todos os seus lançamentos, relatórios e dados serão apagados permanentemente e não poderão ser recuperados.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (usuario != null) {
                                excluindoConta = true
                                // Exclusão completa no servidor (dados + subcoleções + login)
                                Firebase.functions("southamerica-east1").getHttpsCallable("excluirConta").call()
                                    .addOnSuccessListener {
                                        excluindoConta = false
                                        mostrarDialogExclusao = false
                                        Firebase.auth.signOut()
                                        context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).edit { remove("workspace_uid"); remove("workspace_nome") }
                                        Toast.makeText(context, "Conta e dados excluídos.", Toast.LENGTH_LONG).show()
                                        onAccountDeleted()
                                    }
                                    .addOnFailureListener { e ->
                                        excluindoConta = false
                                        mostrarDialogExclusao = false
                                        Toast.makeText(context, e.message ?: "Não foi possível excluir a conta.", Toast.LENGTH_LONG).show()
                                    }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colorDanger)
                    ) {
                        if (excluindoConta) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                        else Text("Sim, excluir conta", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { mostrarDialogExclusao = false }, enabled = !excluindoConta) {
                        Text("Cancelar", color = colorTextSecondary)
                    }
                }
            )
        }
    }
}

// =========================================================================
// COMPONENTES DA TELA DE CONFIGURAÇÕES (linhas agrupadas estilo ajustes do sistema)
// =========================================================================
data class CoresConfig(val superficie: Color, val texto: Color, val textoSecundario: Color, val divisor: Color)

@Composable
private fun TituloSecaoConfig(titulo: String, cor: Color) {
    Text(titulo.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cor, letterSpacing = 1.sp, modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun GrupoConfig(cores: CoresConfig, conteudo: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = cores.superficie, border = BorderStroke(1.dp, cores.divisor), modifier = Modifier.fillMaxWidth()) {
        Column(content = conteudo)
    }
}

@Composable
private fun DivisorConfig(cores: CoresConfig) {
    HorizontalDivider(color = cores.divisor, modifier = Modifier.padding(start = 66.dp))
}

@Composable
private fun IconeConfig(icone: androidx.compose.ui.graphics.vector.ImageVector, cor: Color) {
    Box(Modifier.size(36.dp).background(cor.copy(alpha = 0.14f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        Icon(icone, null, tint = cor, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun LinhaConfig(
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    cor: Color,
    titulo: String,
    subtitulo: String,
    cores: CoresConfig,
    corTitulo: Color = cores.texto,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconeConfig(icone, cor)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = corTitulo)
            if (subtitulo.isNotBlank()) Text(subtitulo, fontSize = 12.sp, color = cores.textoSecundario, maxLines = 1)
        }
        if (trailing != null) trailing() else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = cores.textoSecundario.copy(alpha = 0.6f))
    }
}
