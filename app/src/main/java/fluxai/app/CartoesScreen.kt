package fluxai.app

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Calendar
import java.util.Locale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import android.content.Context
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartoesScreen(
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

    // =========================================================================
    // === CHAVE MESTRA DA SINCRONIZAÇÃO FAMILIAR ===
    // =========================================================================
    val sharedPref = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
    val workspaceUid = sharedPref.getString("workspace_uid", user.uid) ?: user.uid
    // =========================================================================

    val vm: CartoesViewModel = viewModel()
    LaunchedEffect(workspaceUid) { vm.observar(workspaceUid) }
    val cartoes by vm.cartoes.collectAsStateWithLifecycle()
    var mostrarModalNovoCartao by remember { mutableStateOf(false) }

    var cNome by remember { mutableStateOf("") }
    var cLimite by remember { mutableStateOf("") }
    var cDiaF by remember { mutableStateOf("") }
    var cDiaV by remember { mutableStateOf("") }

    var cNumero by remember { mutableStateOf("") }
    var cValidade by remember { mutableStateOf("") }
    var cBandeira by remember { mutableStateOf("Mastercard") }
    var expandidoBandeira by remember { mutableStateOf(false) }
    val listaBandeiras = listOf("Mastercard", "Visa", "Elo", "Amex", "Hipercard")
    var salvando by remember { mutableStateOf(false) }

    // =========================================================================
    // === INJETADO: Variáveis de Estado para Edição ===
    // =========================================================================
    var cartaoParaEditar by remember { mutableStateOf<Cartao?>(null) }
    var eNome by remember { mutableStateOf("") }
    var eLimite by remember { mutableStateOf("") }
    var eDiaF by remember { mutableStateOf("") }
    var eDiaV by remember { mutableStateOf("") }
    var eNumero by remember { mutableStateOf("") }
    var eValidade by remember { mutableStateOf("") }
    var eBandeira by remember { mutableStateOf("Mastercard") }
    var expandidoBandeiraEdit by remember { mutableStateOf(false) }
    var salvandoEdit by remember { mutableStateOf(false) }
    // =========================================================================

    var cartaoParaExcluir by remember { mutableStateOf<Cartao?>(null) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = { MenuLateral(drawerState, coroutineScope, "cartoes", onAbrirDashboard, onAbrirLancamento, onAbrirAnalytics, onAbrirSettings, onAbrirSobre, onAbrirManutencao, onLogout, onAbrirCartoes, onAbrirCaixinhas, onAbrirAssinaturas, onAbrirCelular) }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Cartões", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, null) } },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colorBg)
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(onClick = { mostrarModalNovoCartao = true }, containerColor = Color(0xFF2196F3), contentColor = Color.White, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Novo cartão") })
            },
            containerColor = colorBg
        ) { padding ->
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                item {
                    val moeda = java.text.NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
                    val limiteTotal = cartoes.sumOf { it.limite }
                    val faturaTotal = cartoes.sumOf { it.faturaAtual }
                    val uso = if (limiteTotal > 0) (faturaTotal / limiteTotal).toFloat().coerceIn(0f, 1f) else 0f
                    val corUso = when { uso > 0.8f -> Color(0xFFE53935); uso > 0.5f -> Color(0xFFFB8C00); else -> Color(0xFF2196F3) }
                    Surface(
                        shape = RoundedCornerShape(20.dp), color = colorSurface,
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF2C2C2C) else Color(0xFFE5E7EB)),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp)
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text("Limite disponível", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                            Text(moeda.format(limiteTotal - faturaTotal), fontSize = 30.sp, fontWeight = FontWeight.Black, color = if (isDark) Color.White else Color(0xFF1A1A1A))
                            if (limiteTotal > 0) {
                                Spacer(Modifier.height(10.dp))
                                LinearProgressIndicator(progress = { uso }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = corUso, trackColor = corUso.copy(alpha = 0.12f))
                                Spacer(Modifier.height(6.dp))
                                Text("${moeda.format(faturaTotal)} em faturas · ${(uso * 100).toInt()}% do limite total", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                            }
                        }
                    }
                }
                if (cartoes.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(88.dp).background(Color(0xFF2196F3).copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.CreditCard, null, Modifier.size(44.dp), tint = Color(0xFF2196F3))
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("Nenhum cartão cadastrado", fontWeight = FontWeight.Bold, color = if (isDark) Color.White else Color(0xFF1A1A1A))
                            Text("Cadastre seus cartões para acompanhar faturas e limite.", fontSize = 13.sp, color = Color(0xFF9CA3AF), modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                items(cartoes) { cartao ->
                    CardCartaoCredito(
                        cartao = cartao,
                        isDark = isDark,
                        onEdit = {
                            // Ao clicar em Editar, puxa os dados e abre o Modal
                            eNome = cartao.nome
                            eLimite = cartao.limite.paraCampo()
                            eDiaF = cartao.diaFechamento.toString()
                            eDiaV = cartao.diaVencimento.toString()
                            eBandeira = cartao.bandeira
                            vm.extrasDe(cartao.id).let { eNumero = it.final4; eValidade = it.validade }
                            cartaoParaEditar = cartao
                        },
                        onDelete = { cartaoParaExcluir = cartao }
                    )
                }
            }

            cartaoParaExcluir?.let { c ->
                AlertDialog(
                    onDismissRequest = { cartaoParaExcluir = null },
                    containerColor = colorSurface,
                    icon = { Icon(Icons.Default.DeleteForever, null, tint = Color(0xFFE53935)) },
                    title = { Text("Excluir ${c.nome}?") },
                    text = { Text("Os lançamentos já feitos neste cartão continuam no Dashboard.") },
                    confirmButton = {
                        Button(onClick = {
                            vm.excluir(c)
                            cartaoParaExcluir = null
                        }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))) { Text("Excluir") }
                    },
                    dismissButton = { TextButton(onClick = { cartaoParaExcluir = null }) { Text("Cancelar") } }
                )
            }

            if (mostrarModalNovoCartao) {
                AlertDialog(
                    onDismissRequest = { mostrarModalNovoCartao = false },
                    containerColor = colorSurface,
                    shape = RoundedCornerShape(28.dp),
                    title = { Text("Novo Cartão", fontWeight = FontWeight.Black) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {

                            val numFormatado = "•••• •••• •••• " + cNumero.padStart(4, '•')
                            val valFormatada = cValidade.padEnd(4, '•').take(4).let { "${it.take(2)}/${it.takeLast(2)}" }
                            val nomeFormatado = if (cNome.isBlank()) "NOME DO TITULAR" else cNome.uppercase()

                            val corCard = when(cBandeira) {
                                "Mastercard" -> listOf(Color(0xFF673AB7), Color(0xFF512DA8))
                                "Visa" -> listOf(Color(0xFF1976D2), Color(0xFF0D47A1))
                                "Elo" -> listOf(Color(0xFF212121), Color(0xFF000000))
                                "Amex" -> listOf(Color(0xFF00796B), Color(0xFF004D40))
                                "Hipercard" -> listOf(Color(0xFFD32F2F), Color(0xFFB71C1C))
                                else -> listOf(Color(0xFF607D8B), Color(0xFF455A64))
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth().height(190.dp).padding(bottom = 8.dp),
                                shape = RoundedCornerShape(16.dp),
                                elevation = CardDefaults.cardElevation(8.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize().background(Brush.linearGradient(corCard))) {
                                    Column(modifier = Modifier.padding(20.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.size(width = 36.dp, height = 26.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFFFD54F), RoundedCornerShape(4.dp)).border(1.dp, Color(0xFFFFCA28), RoundedCornerShape(4.dp)))
                                                Spacer(Modifier.width(8.dp))
                                                Icon(Icons.Default.Wifi, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.rotate(90f).size(20.dp))
                                            }
                                            BandeiraLogo(bandeira = cBandeira)
                                        }
                                        Text(numFormatado, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                                            Text(nomeFormatado, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("VALID THRU", color = Color.White.copy(alpha = 0.5f), fontSize = 8.sp)
                                                Text(valFormatada, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(value = cNome, onValueChange = { cNome = it }, label = { Text("Nome (Ex: Nubank)") }, modifier = Modifier.fillMaxWidth(), shape = FormatoCampo)

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = cNumero, onValueChange = { cNumero = it.filter { c -> c.isDigit() }.take(4) }, label = { Text("Final (4 dígitos)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1.5f), shape = FormatoCampo)
                                OutlinedTextField(value = cValidade, onValueChange = { if(it.length <= 4) cValidade = it }, label = { Text("Validade") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), shape = FormatoCampo)
                            }

                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = cBandeira, onValueChange = { }, readOnly = true, label = { Text("Bandeira") },
                                    modifier = Modifier.fillMaxWidth(), trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                                    shape = FormatoCampo
                                )
                                Box(modifier = Modifier.matchParentSize().clickable { expandidoBandeira = !expandidoBandeira })
                                DropdownMenu(expanded = expandidoBandeira, onDismissRequest = { expandidoBandeira = false }, modifier = Modifier.fillMaxWidth(0.7f).background(colorSurface)) {
                                    listaBandeiras.forEach { band ->
                                        DropdownMenuItem(text = { Text(band, color = if(isDark) Color.White else Color.Black) }, onClick = { cBandeira = band; expandidoBandeira = false })
                                    }
                                }
                            }

                            OutlinedTextField(value = cLimite, onValueChange = { cLimite = it }, label = { Text("Limite Total (R$)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = FormatoCampo)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = cDiaF, onValueChange = { cDiaF = it }, label = { Text("Fechamento") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), shape = FormatoCampo)
                                OutlinedTextField(value = cDiaV, onValueChange = { cDiaV = it }, label = { Text("Vencimento") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), shape = FormatoCampo)
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val lim = cLimite.paraValor() ?: 0.0
                                val df = cDiaF.toIntOrNull() ?: 1
                                val dv = cDiaV.toIntOrNull() ?: 1
                                if (cNome.isNotBlank() && lim > 0) {
                                    salvando = true
                                    vm.salvar(null, cNome, cBandeira, cNumero, cValidade, lim, df, dv) { ok ->
                                        salvando = false
                                        if (ok) { mostrarModalNovoCartao = false; cNome = ""; cNumero = ""; cValidade = ""; cLimite = ""; cDiaF = ""; cDiaV = ""; cBandeira = "Mastercard" }
                                        }
                                }
                            },
                            enabled = !salvando,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                        ) { Text("Salvar") }
                    },
                    dismissButton = { TextButton(onClick = { mostrarModalNovoCartao = false }) { Text("Cancelar", color = Color.Gray) } }
                )
            }

            // =========================================================================
            // === INJETADO: MODAL DE EDIÇÃO DE CARTÃO ===
            // =========================================================================
            if (cartaoParaEditar != null) {
                AlertDialog(
                    onDismissRequest = { cartaoParaEditar = null },
                    containerColor = colorSurface,
                    shape = RoundedCornerShape(28.dp),
                    title = { Text("Editar Cartão", fontWeight = FontWeight.Black) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {

                            val numFormatado = "•••• •••• •••• " + eNumero.padStart(4, '•')
                            val valFormatada = eValidade.padEnd(4, '•').take(4).let { "${it.take(2)}/${it.takeLast(2)}" }
                            val nomeFormatado = if (eNome.isBlank()) "NOME DO TITULAR" else eNome.uppercase()

                            val corCard = when(eBandeira) {
                                "Mastercard" -> listOf(Color(0xFF673AB7), Color(0xFF512DA8))
                                "Visa" -> listOf(Color(0xFF1976D2), Color(0xFF0D47A1))
                                "Elo" -> listOf(Color(0xFF212121), Color(0xFF000000))
                                "Amex" -> listOf(Color(0xFF00796B), Color(0xFF004D40))
                                "Hipercard" -> listOf(Color(0xFFD32F2F), Color(0xFFB71C1C))
                                else -> listOf(Color(0xFF607D8B), Color(0xFF455A64))
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth().height(190.dp).padding(bottom = 8.dp),
                                shape = RoundedCornerShape(16.dp),
                                elevation = CardDefaults.cardElevation(8.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize().background(Brush.linearGradient(corCard))) {
                                    Column(modifier = Modifier.padding(20.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.size(width = 36.dp, height = 26.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFFFD54F), RoundedCornerShape(4.dp)).border(1.dp, Color(0xFFFFCA28), RoundedCornerShape(4.dp)))
                                                Spacer(Modifier.width(8.dp))
                                                Icon(Icons.Default.Wifi, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.rotate(90f).size(20.dp))
                                            }
                                            BandeiraLogo(bandeira = eBandeira)
                                        }
                                        Text(numFormatado, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                                            Text(nomeFormatado, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("VALID THRU", color = Color.White.copy(alpha = 0.5f), fontSize = 8.sp)
                                                Text(valFormatada, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(value = eNome, onValueChange = { eNome = it }, label = { Text("Nome (Ex: Nubank)") }, modifier = Modifier.fillMaxWidth(), shape = FormatoCampo)

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = eNumero, onValueChange = { eNumero = it.filter { c -> c.isDigit() }.take(4) }, label = { Text("Final (4 dígitos)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1.5f), shape = FormatoCampo)
                                OutlinedTextField(value = eValidade, onValueChange = { if(it.length <= 4) eValidade = it }, label = { Text("Validade") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), shape = FormatoCampo)
                            }

                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = eBandeira, onValueChange = { }, readOnly = true, label = { Text("Bandeira") },
                                    modifier = Modifier.fillMaxWidth(), trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                                    shape = FormatoCampo
                                )
                                Box(modifier = Modifier.matchParentSize().clickable { expandidoBandeiraEdit = !expandidoBandeiraEdit })
                                DropdownMenu(expanded = expandidoBandeiraEdit, onDismissRequest = { expandidoBandeiraEdit = false }, modifier = Modifier.fillMaxWidth(0.7f).background(colorSurface)) {
                                    listaBandeiras.forEach { band ->
                                        DropdownMenuItem(text = { Text(band, color = if(isDark) Color.White else Color.Black) }, onClick = { eBandeira = band; expandidoBandeiraEdit = false })
                                    }
                                }
                            }

                            OutlinedTextField(value = eLimite, onValueChange = { eLimite = it }, label = { Text("Limite Total (R$)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = FormatoCampo)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = eDiaF, onValueChange = { eDiaF = it }, label = { Text("Fechamento") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), shape = FormatoCampo)
                                OutlinedTextField(value = eDiaV, onValueChange = { eDiaV = it }, label = { Text("Vencimento") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), shape = FormatoCampo)
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val lim = eLimite.paraValor() ?: 0.0
                                val df = eDiaF.toIntOrNull() ?: 1
                                val dv = eDiaV.toIntOrNull() ?: 1
                                if (eNome.isNotBlank() && lim > 0) {
                                    salvandoEdit = true
                                    vm.salvar(cartaoParaEditar!!.id, eNome, eBandeira, eNumero, eValidade, lim, df, dv) { ok ->
                                        salvandoEdit = false
                                        if (ok) cartaoParaEditar = null
                                        }
                                }
                            },
                            enabled = !salvandoEdit,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                        ) { Text("Salvar") }
                    },
                    dismissButton = { TextButton(onClick = { cartaoParaEditar = null }) { Text("Cancelar", color = Color.Gray) } }
                )
            }
            // =========================================================================
        }
    }
}

// Logo da bandeira do cartão (null se a bandeira não for conhecida)
fun logoBandeiraCartao(bandeira: String): Int? = when (bandeira.lowercase()) {
    "mastercard" -> R.drawable.ic_bandeira_mastercard
    "visa" -> R.drawable.ic_bandeira_visa
    "elo" -> R.drawable.ic_bandeira_elo
    "amex" -> R.drawable.ic_bandeira_amex
    "hipercard" -> R.drawable.ic_bandeira_hipercard
    else -> null
}

@Composable
fun BandeiraLogo(bandeira: String, modifier: Modifier = Modifier) {
    val logoRes = logoBandeiraCartao(bandeira)

    if (logoRes != null) {
        // Selo branco para o logo ficar legível sobre qualquer cor de cartão
        Surface(color = Color.White, shape = RoundedCornerShape(6.dp), modifier = modifier) {
            Image(
                painter = painterResource(id = logoRes),
                contentDescription = bandeira,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(width = 48.dp, height = 30.dp).padding(horizontal = 4.dp, vertical = 3.dp)
            )
        }
    } else {
        Icon(Icons.Default.CreditCard, null, tint = Color.White, modifier = modifier)
    }
}

@Composable
fun CardCartaoCredito(cartao: Cartao, isDark: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    val moeda = remember { java.text.NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    val progresso = if (cartao.limite > 0) (cartao.faturaAtual / cartao.limite).toFloat() else 0f
    val gradiente = when (cartao.bandeira.lowercase()) {
        "visa" -> listOf(Color(0xFF1A1F71), Color(0xFF2B4FB5))
        "elo" -> listOf(Color(0xFF111111), Color(0xFF3A3A3A))
        "amex" -> listOf(Color(0xFF2E77BB), Color(0xFF5AA0DB))
        "hipercard" -> listOf(Color(0xFF8B0000), Color(0xFFB3131B))
        else -> listOf(Color(0xFF232526), Color(0xFF414345))
    }
    // Dias até o vencimento da fatura
    val hoje = Calendar.getInstance()
    val venc = (hoje.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, cartao.diaVencimento.coerceIn(1, getActualMaximum(Calendar.DAY_OF_MONTH)))
        if (before(hoje) && get(Calendar.DAY_OF_YEAR) != hoje.get(Calendar.DAY_OF_YEAR)) add(Calendar.MONTH, 1)
    }
    val diasParaVencer = ((venc.timeInMillis - hoje.timeInMillis) / 86_400_000L).toInt()
    val melhorDia = if (cartao.diaFechamento >= 28) 1 else cartao.diaFechamento + 1
    var menu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (isDark) Color(0xFF1A1A1A) else Color.White,
        border = BorderStroke(1.dp, if (isDark) Color(0xFF2C2C2C) else Color(0xFFE5E7EB)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Column {
            // Frente do cartão
            Box(Modifier.fillMaxWidth().background(Brush.linearGradient(gradiente)).padding(18.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(cartao.nome, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White, modifier = Modifier.weight(1f))
                        BandeiraLogo(bandeira = cartao.bandeira)
                        Box {
                            IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.MoreVert, "Mais opções", tint = Color.White.copy(alpha = 0.8f)) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Editar") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onEdit() })
                                DropdownMenuItem(text = { Text("Excluir", color = Color(0xFFE53935)) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFE53935)) }, onClick = { menu = false; onDelete() })
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("Fatura atual", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                    Text(moeda.format(cartao.faturaAtual), fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White)
                }
            }
            // Detalhes
            Column(Modifier.padding(16.dp)) {
                val cor = when { progresso > 0.8f -> Color(0xFFE53935); progresso > 0.5f -> Color(0xFFFB8C00); else -> Color(0xFF2196F3) }
                LinearProgressIndicator(progress = { progresso.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = cor, trackColor = cor.copy(alpha = 0.12f))
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text("${(progresso * 100).toInt()}% usado", fontSize = 12.sp, color = Color(0xFF9CA3AF), modifier = Modifier.weight(1f))
                    Text("Disponível ${moeda.format(cartao.limite - cartao.faturaAtual)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (isDark) Color.White else Color(0xFF1A1A1A))
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    InfoCartao("Fecha", "dia ${cartao.diaFechamento}", isDark, Modifier.weight(1f))
                    InfoCartao("Vence", if (diasParaVencer == 0) "hoje" else "dia ${cartao.diaVencimento} (${diasParaVencer}d)", isDark, Modifier.weight(1.3f), destaque = diasParaVencer in 0..3 && cartao.faturaAtual > 0)
                    InfoCartao("Melhor compra", "dia $melhorDia", isDark, Modifier.weight(1.1f))
                }
            }
        }
    }
}

@Composable
private fun InfoCartao(titulo: String, valor: String, isDark: Boolean, modifier: Modifier, destaque: Boolean = false) {
    Column(modifier) {
        Text(titulo, fontSize = 11.sp, color = Color(0xFF9CA3AF))
        Text(valor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (destaque) Color(0xFFE53935) else if (isDark) Color.White else Color(0xFF1A1A1A))
    }
}
