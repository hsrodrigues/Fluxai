package fluxai.app

import com.google.firebase.firestore.ListenerRegistration
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import android.annotation.SuppressLint
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
import androidx.compose.material3.*
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
import com.google.firebase.firestore.firestore
import fluxai.app.ui.theme.LocalAccentColor
import fluxai.app.ui.theme.LocalDarkTheme
import kotlinx.coroutines.launch
import java.util.*

data class ContaCelular(
    val id: String = "",
    val titular: String = "",
    val operadora: String = "VIVO",
    val tipoPlano: String = "Controle",
    val valor: Double = 0.0,
    val diaVencimento: Int = 10,
    val status: String = "Ativo"
)

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContasCelularScreen(
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
    val banco = Firebase.firestore
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val sharedPref = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
    val workspaceUid = sharedPref.getString("workspace_uid", usuario?.uid ?: "") ?: usuario?.uid ?: ""

    val isDark = LocalDarkTheme.current
    val colorAccent = LocalAccentColor.current
    val colorBg = if (isDark) Color(0xFF121212) else Color(0xFFF8F9FA)
    val colorSurface = if (isDark) Color(0xFF1E1E1E) else Color.White
    val colorTextPrimary = if (isDark) Color.White else Color(0xFF1E1E1E)
    val colorTextSecondary = Color.Gray
    val colorDivider = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB)

    var contasList by remember { mutableStateOf<List<ContaCelular>>(emptyList()) }
    var mostrarDialogNovaConta by remember { mutableStateOf(false) }

    // Estados do Formulário e Controle de Edição
    var contaParaEditar by remember { mutableStateOf<ContaCelular?>(null) }
    var titular by remember { mutableStateOf("") }
    var valor by remember { mutableStateOf("") }
    var diaVencimento by remember { mutableStateOf("") }
    var operadoraSelecionada by remember { mutableStateOf("VIVO") }
    var tipoPlanoSelecionado by remember { mutableStateOf("Controle") }
    var salvando by remember { mutableStateOf(false) }

    val operadoras = listOf(
        Triple("VIVO", Color(0xFF660099), "Vivo"), // Roxo oficial
        Triple("CLARO", Color(0xFFE4242D), "Claro"), // Vermelho oficial
        Triple("TIM", Color(0xFF004691), "Tim"), // Azul oficial
        Triple("OI", Color(0xFF43B708), "Oi") // Verde oficial
    )

    DisposableEffect(workspaceUid) {
        val ouvintes = mutableListOf<ListenerRegistration>()
        if (usuario != null && workspaceUid.isNotEmpty()) {
            ouvintes += banco.collection("usuarios").document(workspaceUid).collection("contas_celular")
                .addSnapshotListener { snap, _ ->
                    if (snap != null) {
                        contasList = snap.documents.mapNotNull { d ->
                            try {
                                ContaCelular(
                                    id = d.id,
                                    titular = d.getString("titular") ?: "",
                                    operadora = d.getString("operadora") ?: "VIVO",
                                    tipoPlano = d.getString("tipoPlano") ?: "Controle",
                                    valor = d.getDouble("valor") ?: 0.0,
                                    diaVencimento = d.getLong("diaVencimento")?.toInt() ?: 10,
                                    status = d.getString("status") ?: "Ativo"
                                )
                            } catch (e: Exception) { null }
                        }
                    }
                }
        }
        // Desliga os ouvintes ao sair da tela ou trocar a chave (ex.: mês), senão eles se acumulam
        onDispose { ouvintes.forEach { it.remove() } }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MenuLateral(
                drawerState = drawerState,
                coroutineScope = coroutineScope,
                rotaAtual = "celular",
                onAbrirDashboard = onAbrirDashboard,
                onAbrirLancamento = onAbrirLancamento,
                onAbrirAnalytics = onAbrirAnalytics,
                onAbrirSettings = onAbrirSettings,
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
                    title = { Text("Contas de Celular", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colorTextPrimary) },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, "Abrir menu", tint = colorTextPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colorBg)
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        contaParaEditar = null
                        titular = ""
                        valor = ""
                        diaVencimento = ""
                        operadoraSelecionada = "VIVO"
                        tipoPlanoSelecionado = "Controle"
                        mostrarDialogNovaConta = true
                    },
                    containerColor = colorAccent,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Nova Conta")
                }
            },
            containerColor = colorBg
        ) { padding ->
            if (contasList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SettingsCell, null, modifier = Modifier.size(64.dp), tint = colorTextSecondary.copy(0.5f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Nenhuma conta cadastrada.", color = colorTextSecondary)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        Text("Gerencie seus planos Pós e Controle", color = colorTextSecondary, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                    }

                    items(contasList) { conta ->
                        val corOp = operadoras.find { it.first == conta.operadora }?.second ?: colorAccent

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = colorSurface),
                            border = BorderStroke(1.dp, colorDivider)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier.size(width = 64.dp, height = 48.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, colorDivider, RoundedCornerShape(12.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val logo = logoOperadora(conta.operadora)
                                            if (logo != null) {
                                                Image(painterResource(logo), contentDescription = conta.operadora, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp))
                                            } else {
                                                Icon(Icons.Default.Smartphone, null, tint = corOp)
                                            }
                                        }
                                        Spacer(Modifier.width(16.dp))
                                        Column {
                                            Text(conta.titular, fontWeight = FontWeight.Bold, color = colorTextPrimary, fontSize = 16.sp)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(conta.operadora, color = corOp, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text(" • Plano ${conta.tipoPlano}", color = colorTextSecondary, fontSize = 12.sp)
                                            }
                                        }
                                    }

                                    Row {
                                        IconButton(
                                            onClick = {
                                                contaParaEditar = conta
                                                titular = conta.titular
                                                valor = "%.2f".format(conta.valor).replace(".", ",")
                                                diaVencimento = conta.diaVencimento.toString()
                                                operadoraSelecionada = conta.operadora
                                                tipoPlanoSelecionado = conta.tipoPlano
                                                mostrarDialogNovaConta = true
                                            }
                                        ) { Icon(Icons.Default.Edit, null, tint = colorAccent) }

                                        IconButton(
                                            onClick = {
                                                // Exclusão em Cascata (Deleta a conta e todas as despesas vinculadas a ela no Dashboard)
                                                banco.collection("usuarios").document(workspaceUid)
                                                    .collection("despesas").whereEqualTo("celularId", conta.id)
                                                    .get().addOnSuccessListener { query ->
                                                        val batch = banco.batch()
                                                        val refConta = banco.collection("usuarios").document(workspaceUid).collection("contas_celular").document(conta.id)
                                                        batch.delete(refConta)

                                                        query.documents.forEach { doc ->
                                                            batch.delete(doc.reference)
                                                        }

                                                        batch.commit().addOnSuccessListener {
                                                            Toast.makeText(context, "Conta e lançamentos excluídos!", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                            }
                                        ) { Icon(Icons.Default.Delete, null, tint = Color.Red.copy(0.7f)) }
                                    }
                                }

                                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colorDivider)

                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text("Vencimento", fontSize = 12.sp, color = colorTextSecondary)
                                        Text("Todo dia ${conta.diaVencimento}", fontWeight = FontWeight.Bold, color = colorTextPrimary)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Valor do Plano", fontSize = 12.sp, color = colorTextSecondary)
                                        Text("R$ %.2f".format(conta.valor), fontWeight = FontWeight.Black, color = corOp, fontSize = 16.sp)
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        // =========================================================================
        // === MODAL DE LANÇAMENTO E EDIÇÃO DA CONTA ===
        // =========================================================================
        if (mostrarDialogNovaConta) {
            AlertDialog(
                onDismissRequest = { mostrarDialogNovaConta = false },
                containerColor = colorSurface,
                shape = RoundedCornerShape(28.dp),
                title = { Text(if (contaParaEditar == null) "Nova Conta de Celular" else "Editar Conta de Celular", fontWeight = FontWeight.Black, color = colorTextPrimary) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                        OutlinedTextField(
                            value = titular, onValueChange = { titular = it },
                            label = { Text("Titular da Linha") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colorAccent, unfocusedBorderColor = colorDivider, focusedTextColor = colorTextPrimary, unfocusedTextColor = colorTextPrimary),
                            shape = FormatoCampo
                        )

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = valor, onValueChange = { valor = it },
                                label = { Text("Valor") }, prefix = { Text("R$") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colorAccent, unfocusedBorderColor = colorDivider, focusedTextColor = colorTextPrimary, unfocusedTextColor = colorTextPrimary),
                                shape = FormatoCampo
                            )
                            OutlinedTextField(
                                value = diaVencimento, onValueChange = { if(it.length <= 2) diaVencimento = it },
                                label = { Text("Dia") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(0.6f),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colorAccent, unfocusedBorderColor = colorDivider, focusedTextColor = colorTextPrimary, unfocusedTextColor = colorTextPrimary),
                                shape = FormatoCampo
                            )
                        }

                        Text("Operadora", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colorTextSecondary)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            operadoras.forEach { (nomeOp, corOp, _) ->
                                val isSelected = operadoraSelecionada == nomeOp
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(2.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White)
                                        .border(if (isSelected) 2.dp else 1.dp, if (isSelected) corOp else colorDivider, RoundedCornerShape(8.dp))
                                        .clickable { operadoraSelecionada = nomeOp }
                                        .height(44.dp)
                                        .padding(horizontal = 6.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val logo = logoOperadora(nomeOp)
                                    if (logo != null) {
                                        Image(painterResource(logo), contentDescription = nomeOp, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().alpha(if (isSelected) 1f else 0.45f))
                                    } else {
                                        Text(nomeOp, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (isSelected) corOp else colorTextPrimary)
                                    }
                                }
                            }
                        }

                        Text("Tipo de Plano", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colorTextSecondary)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Controle", "Pós-Pago").forEach { tipo ->
                                val isSel = tipoPlanoSelecionado == tipo
                                FilterChip(
                                    selected = isSel,
                                    onClick = { tipoPlanoSelecionado = tipo },
                                    label = { Text(tipo) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colorAccent.copy(0.2f), selectedLabelColor = colorAccent)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val v = valor.paraValor() ?: 0.0
                            val d = diaVencimento.toIntOrNull() ?: 10

                            if (titular.isNotBlank() && v > 0) {
                                salvando = true

                                if (contaParaEditar == null) {
                                    // NOVA CONTA E LANÇAMENTO EM LOTE PARA O ANO
                                    val novaContaRef = banco.collection("usuarios").document(workspaceUid).collection("contas_celular").document()
                                    val novaContaMap = hashMapOf(
                                        "titular" to titular,
                                        "valor" to v,
                                        "diaVencimento" to d,
                                        "operadora" to operadoraSelecionada,
                                        "tipoPlano" to tipoPlanoSelecionado,
                                        "status" to "Ativo"
                                    )

                                    val batch = banco.batch()
                                    batch.set(novaContaRef, novaContaMap)

                                    val cal = Calendar.getInstance()
                                    var mesLoop = cal.get(Calendar.MONTH) + 1
                                    var anoLoop = cal.get(Calendar.YEAR)

                                    for (i in 1..12) {
                                        val mesAnoFormato = String.format(Locale("pt", "BR"), "%02d/%04d", mesLoop, anoLoop)
                                        val despRef = banco.collection("usuarios").document(workspaceUid).collection("despesas").document()
                                        batch.set(despRef, hashMapOf(
                                            "descricao" to "Plano $operadoraSelecionada - $titular",
                                            "valor" to v,
                                            "diaVencimento" to d,
                                            "tipo" to "Fixa",
                                            "categoria" to "Outros", // Pode alterar para Moradia se preferir no Dashboard
                                            "status" to "A pagar",
                                            "mesAno" to mesAnoFormato,
                                            "frequencia" to "Mensal",
                                            "celularId" to novaContaRef.id,
                                            "ordem" to 0
                                        ))
                                        mesLoop++
                                        if (mesLoop > 12) { mesLoop = 1; anoLoop++ }
                                    }

                                    batch.commit().addOnSuccessListener {
                                        salvando = false
                                        mostrarDialogNovaConta = false
                                        titular = ""; valor = ""; diaVencimento = ""
                                        Toast.makeText(context, "Conta criada e lançada em 12 meses!", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    // EDITAR CONTA E ATUALIZAR TODOS OS LANÇAMENTOS
                                    val contaId = contaParaEditar!!.id
                                    val updateMap = mapOf(
                                        "titular" to titular,
                                        "valor" to v,
                                        "diaVencimento" to d,
                                        "operadora" to operadoraSelecionada,
                                        "tipoPlano" to tipoPlanoSelecionado
                                    )

                                    banco.collection("usuarios").document(workspaceUid).collection("despesas")
                                        .whereEqualTo("celularId", contaId)
                                        .get().addOnSuccessListener { query ->
                                            val batch = banco.batch()
                                            val refConta = banco.collection("usuarios").document(workspaceUid).collection("contas_celular").document(contaId)
                                            batch.update(refConta, updateMap)

                                            query.documents.forEach { doc ->
                                                batch.update(doc.reference, mapOf(
                                                    "descricao" to "Plano $operadoraSelecionada - $titular",
                                                    "valor" to v,
                                                    "diaVencimento" to d
                                                ))
                                            }

                                            batch.commit().addOnSuccessListener {
                                                salvando = false
                                                mostrarDialogNovaConta = false
                                                Toast.makeText(context, "Conta e lançamentos atualizados!", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                }
                            } else {
                                Toast.makeText(context, "Preencha Titular e Valor", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colorAccent)
                    ) {
                        if (salvando) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        else Text("Salvar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { mostrarDialogNovaConta = false }) { Text("Cancelar", color = colorTextSecondary) }
                }
            )
        }
    }
}

// Logo oficial de cada operadora (res/drawable/ic_operadora_*)
fun logoOperadora(operadora: String): Int? = when (operadora.uppercase()) {
    "VIVO" -> R.drawable.ic_operadora_vivo
    "CLARO" -> R.drawable.ic_operadora_claro
    "TIM" -> R.drawable.ic_operadora_tim
    "OI" -> R.drawable.ic_operadora_oi
    else -> null
}
