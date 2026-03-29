package fluxai.app

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ai.client.generativeai.GenerativeModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onLogout: () -> Unit,
    onAbrirAnalytics: () -> Unit,
    onAbrirLancamento: () -> Unit
) {
    val context = LocalContext.current
    val usuario = Firebase.auth.currentUser
    val banco = Firebase.firestore
    val coroutineScope = rememberCoroutineScope()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var calendar by remember { mutableStateOf(Calendar.getInstance()) }
    var mesAnoSelecionado by remember { mutableStateOf(SimpleDateFormat("MM/yyyy", Locale("pt", "BR")).format(calendar.time)) }
    var mesNome by remember { mutableStateOf(SimpleDateFormat("MMMM yyyy", Locale("pt", "BR")).format(calendar.time).replaceFirstChar { it.uppercase() }) }
    var categoriaSelecionada by remember { mutableStateOf("Todas") }

    var saldoString by remember { mutableStateOf("") }
    var despesasRaw by remember { mutableStateOf<List<Despesa>>(emptyList()) }

    var mostrarDialogCalendario by remember { mutableStateOf(false) }
    var despesaParaExcluir by remember { mutableStateOf<Despesa?>(null) }
    var despesaParaEditar by remember { mutableStateOf<Despesa?>(null) }
    var mostrarDialogImportar by remember { mutableStateOf(false) }
    var mesOrigemImportacao by remember { mutableStateOf("") }
    var mostrarDialogIA by remember { mutableStateOf(false) }
    var respostaIA by remember { mutableStateOf("") }
    var carregandoIA by remember { mutableStateOf(false) }

    // Cores do Tema Clean
    val colorAccent = Color(0xFF7E57C2)
    val colorBg = Color(0xFFF8F9FA)
    val colorSurface = Color.White
    val colorTextPrimary = Color(0xFF1E1E1E)
    val colorTextSecondary = Color(0xFF6B7280)
    val colorDarkBg = Color(0xFF1E1E1E)

    LaunchedEffect(mesAnoSelecionado) {
        if (usuario != null) {
            banco.collection("usuarios").document(usuario.uid)
                .collection("saldos").document(mesAnoSelecionado.replace("/", "-"))
                .get().addOnSuccessListener { doc ->
                    saldoString = if (doc.exists()) (doc.getDouble("valor") ?: 0.0).toString() else ""
                }

            banco.collection("usuarios").document(usuario.uid).collection("despesas")
                .whereEqualTo("mesAno", mesAnoSelecionado)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        despesasRaw = snapshot.documents.map { doc ->
                            Despesa(
                                id = doc.id,
                                descricao = doc.getString("descricao") ?: "",
                                valor = doc.getDouble("valor") ?: 0.0,
                                tipo = doc.getString("tipo") ?: "",
                                categoria = doc.getString("categoria") ?: "Outros",
                                status = doc.getString("status") ?: "A pagar",
                                observacao = doc.getString("observacao") ?: "",
                                diaVencimento = doc.getLong("diaVencimento")?.toInt() ?: 0
                            )
                        }
                    }
                }
        }
    }

    val despesasFiltradas = if (categoriaSelecionada == "Todas") despesasRaw else despesasRaw.filter { it.categoria == categoriaSelecionada }
    val ordenadas = despesasFiltradas.sortedWith(compareBy({ it.status == "Pago" }, { it.diaVencimento }))

    val saldoRenda = saldoString.replace(",", ".").toDoubleOrNull() ?: 0.0
    val totalAPagar = despesasRaw.filter { it.status == "A pagar" }.sumOf { it.valor }
    val totalPago = despesasRaw.filter { it.status == "Pago" }.sumOf { it.valor }
    val sobra = saldoRenda - totalAPagar - totalPago

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

                NavigationDrawerItem(label = { Text("Meu Dashboard", fontWeight = FontWeight.Medium) }, selected = true, icon = { Icon(Icons.Default.Dashboard, null) }, colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = colorAccent.copy(alpha = 0.1f), selectedIconColor = colorAccent, selectedTextColor = colorAccent), onClick = { coroutineScope.launch { drawerState.close() } }, modifier = Modifier.padding(horizontal = 12.dp))
                Spacer(modifier = Modifier.height(8.dp))
                NavigationDrawerItem(label = { Text("Novo Lançamento", fontWeight = FontWeight.Medium) }, selected = false, icon = { Icon(Icons.Default.AddCircle, null, tint = colorTextSecondary) }, onClick = { coroutineScope.launch { drawerState.close() }; onAbrirLancamento() }, modifier = Modifier.padding(horizontal = 12.dp))
                Spacer(modifier = Modifier.height(8.dp))
                NavigationDrawerItem(label = { Text("Análise BI", fontWeight = FontWeight.Medium) }, selected = false, icon = { Icon(Icons.Default.PieChart, null, tint = colorTextSecondary) }, onClick = { coroutineScope.launch { drawerState.close() }; onAbrirAnalytics() }, modifier = Modifier.padding(horizontal = 12.dp))

                Spacer(modifier = Modifier.weight(1f))

                NavigationDrawerItem(label = { Text("Sair da Conta", fontWeight = FontWeight.Medium) }, selected = false, icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) }, colors = NavigationDrawerItemDefaults.colors(unselectedIconColor = Color(0xFFEF5350), unselectedTextColor = Color(0xFFEF5350)), onClick = { coroutineScope.launch { drawerState.close(); Firebase.auth.signOut(); onLogout() } }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp))
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        // LOGO REMOVIDA DE ACORDO COM O SEU PEDIDO
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { mostrarDialogCalendario = true }.padding(8.dp)) {
                            Text(mesNome, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colorTextPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Selecionar Mês", tint = colorTextPrimary)
                        }
                    },
                    navigationIcon = { IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, null, tint = colorTextPrimary) } },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colorBg)
                )
            },
            containerColor = colorBg
        ) { padding ->
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {

                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = colorSurface), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            OutlinedTextField(
                                value = saldoString, onValueChange = { saldoString = it }, label = { Text("Minha Renda Mensal", color = colorTextSecondary) }, modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    IconButton(onClick = {
                                        if (usuario != null) {
                                            val v = saldoString.replace(",", ".").toDoubleOrNull() ?: 0.0
                                            banco.collection("usuarios").document(usuario.uid).collection("saldos").document(mesAnoSelecionado.replace("/", "-")).set(mapOf("valor" to v))
                                            Toast.makeText(context, "Renda salva!", Toast.LENGTH_SHORT).show()
                                        }
                                    }) { Icon(Icons.Default.Save, null, tint = colorAccent) }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colorAccent, unfocusedBorderColor = Color(0xFFE5E7EB)), singleLine = true
                            )

                            Spacer(modifier = Modifier.height(20.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(36.dp).background(Color(0xFFFFF3E0), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.MoneyOff, null, tint = Color(0xFFF57C00), modifier = Modifier.size(18.dp)) }; Spacer(modifier = Modifier.width(8.dp)); Column { Text("A Pagar", fontSize = 12.sp, color = colorTextSecondary); Text("R$ %.2f".format(totalAPagar), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colorTextPrimary) } }
                                Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(36.dp).background(Color(0xFFE8F5E9), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp)) }; Spacer(modifier = Modifier.width(8.dp)); Column { Text("Pago", fontSize = 12.sp, color = colorTextSecondary); Text("R$ %.2f".format(totalPago), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colorTextPrimary) } }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color(0xFFF3F4F6))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Sobra Atual", fontWeight = FontWeight.Medium, color = colorTextSecondary, fontSize = 14.sp)
                                Text("R$ %.2f".format(sobra), fontSize = 24.sp, fontWeight = FontWeight.Black, color = if(sobra >= 0) Color(0xFF4CAF50) else Color(0xFFEF5350))
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = {
                            mostrarDialogIA = true
                            carregandoIA = true
                            coroutineScope.launch {
                                try {
                                    val apiKey = fluxai.app.BuildConfig.GEMINI_API_KEY
                                    val model = GenerativeModel("gemini-2.5-flash", apiKey)
                                    val prompt = "Analise R$ $saldoRenda renda, sobra R$ $sobra. Gastos: ${despesasRaw.size}. Dê 3 dicas úteis."
                                    respostaIA = model.generateContent(prompt).text ?: "Não foi possível gerar a análise."
                                } catch(e: Exception) {
                                    respostaIA = "Erro de conexão: Verifique seu local.properties e dê um Rebuild no projeto."
                                } finally {
                                    carregandoIA = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp).padding(bottom = 24.dp), colors = ButtonDefaults.buttonColors(containerColor = colorAccent.copy(alpha = 0.1f), contentColor = colorAccent), shape = RoundedCornerShape(16.dp), elevation = ButtonDefaults.buttonElevation(0.dp)
                    ) { Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("Consultoria Inteligente", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                }

                item {
                    Column {
                        Text("Categorias", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colorTextSecondary, modifier = Modifier.padding(bottom = 8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                            val cats = listOf("Todas", "Moradia", "Alimentação", "Transporte", "Saúde", "Educação", "Lazer", "Outros")
                            items(cats) { cat ->
                                val selecionado = categoriaSelecionada == cat
                                Surface(modifier = Modifier.clickable { categoriaSelecionada = cat }, shape = RoundedCornerShape(percent = 50), color = if (selecionado) colorAccent else Color.White, border = if (selecionado) null else BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                                    Text(text = cat, color = if (selecionado) Color.White else colorTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Lançamentos", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = colorTextPrimary)
                            OutlinedButton(
                                onClick = {
                                    if (usuario != null) {
                                        val calAnterior = calendar.clone() as Calendar
                                        calAnterior.add(Calendar.MONTH, -1)
                                        val mesOrigem = SimpleDateFormat("MM/yyyy", Locale("pt", "BR")).format(calAnterior.time)
                                        Toast.makeText(context, "Puxando fixas de $mesOrigem...", Toast.LENGTH_SHORT).show()

                                        banco.collection("usuarios").document(usuario.uid).collection("despesas").whereEqualTo("mesAno", mesOrigem).whereEqualTo("tipo", "Fixa").get().addOnSuccessListener { query ->
                                            if (query.isEmpty) { Toast.makeText(context, "Nenhuma fixa em $mesOrigem", Toast.LENGTH_SHORT).show() }
                                            else {
                                                val batch = banco.batch()
                                                query.documents.forEach { doc ->
                                                    val novaRef = banco.collection("usuarios").document(usuario.uid).collection("despesas").document()
                                                    val dados = doc.data?.toMutableMap() ?: mutableMapOf()
                                                    dados["mesAno"] = mesAnoSelecionado; dados["status"] = "A pagar"; batch.set(novaRef, dados)
                                                }
                                                batch.commit().addOnSuccessListener { Toast.makeText(context, "Fixas importadas com sucesso!", Toast.LENGTH_SHORT).show() }
                                            }
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), modifier = Modifier.height(36.dp)
                            ) { Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(14.dp), tint = colorAccent); Spacer(modifier = Modifier.width(6.dp)); Text("Puxar Fixas", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colorAccent) }
                        }
                    }
                }

                if (ordenadas.isEmpty()) {
                    item { Column(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Inbox, null, modifier = Modifier.size(64.dp), tint = Color.LightGray); Spacer(modifier = Modifier.height(16.dp)); Text("Nenhum lançamento no mês selecionado.", color = colorTextSecondary, fontSize = 14.sp) } }
                } else {
                    items(ordenadas) { despesa ->
                        DespesaCardUI(
                            despesa = despesa,
                            onStatusChange = { novo -> banco.collection("usuarios").document(usuario!!.uid).collection("despesas").document(despesa.id).update("status", novo) },
                            onEditClick = { despesaParaEditar = despesa },
                            onDeleteClick = { despesaParaExcluir = despesa }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }

    // --- DIALOGS ---
    if (mostrarDialogCalendario) {
        var anoTemp by remember { mutableStateOf(calendar.get(Calendar.YEAR)) }
        val mesesAbrev = listOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")

        AlertDialog(
            onDismissRequest = { mostrarDialogCalendario = false },
            title = { Text("Selecionar Mês", fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { anoTemp-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null) }
                        Text(anoTemp.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colorAccent)
                        IconButton(onClick = { anoTemp++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    val rows = mesesAbrev.chunked(4)
                    Column {
                        rows.forEachIndexed { rowIndex, rowMonths ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                rowMonths.forEachIndexed { colIndex, mesStr ->
                                    val mesIndex = rowIndex * 4 + colIndex
                                    val isSelecionado = calendar.get(Calendar.MONTH) == mesIndex && calendar.get(Calendar.YEAR) == anoTemp
                                    TextButton(onClick = {
                                        calendar.set(Calendar.YEAR, anoTemp); calendar.set(Calendar.MONTH, mesIndex)
                                        mesAnoSelecionado = SimpleDateFormat("MM/yyyy", Locale("pt", "BR")).format(calendar.time)
                                        mesNome = SimpleDateFormat("MMMM yyyy", Locale("pt", "BR")).format(calendar.time).replaceFirstChar { it.uppercase() }
                                        mostrarDialogCalendario = false
                                    }) { Text(mesStr, color = if (isSelecionado) colorAccent else Color.Gray, fontWeight = if (isSelecionado) FontWeight.Bold else FontWeight.Normal) }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { mostrarDialogCalendario = false }) { Text("Cancelar", color = Color.Gray) } }
        )
    }

    if (mostrarDialogIA) { AlertDialog(onDismissRequest = { mostrarDialogIA = false }, confirmButton = { TextButton(onClick = { mostrarDialogIA = false }) { Text("Fechar", color = colorAccent) } }, title = { Row(verticalAlignment = Alignment.CenterVertically){ Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFFFFD700)); Spacer(modifier = Modifier.width(8.dp)); Text("Consultoria IA") } }, text = { Box(modifier = Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) { Text(if(carregandoIA) "Analisando seus dados..." else respostaIA) } }) }
    if (despesaParaExcluir != null) { AlertDialog(onDismissRequest = { despesaParaExcluir = null }, title = { Text("Excluir") }, text = { Text("Apagar '${despesaParaExcluir!!.descricao}'?") }, confirmButton = { Button(onClick = { banco.collection("usuarios").document(usuario!!.uid).collection("despesas").document(despesaParaExcluir!!.id).delete(); despesaParaExcluir = null }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Sim") } }, dismissButton = { TextButton(onClick = { despesaParaExcluir = null }) { Text("Cancelar", color = colorTextSecondary) } }) }

    if (despesaParaEditar != null) {
        var eDesc by remember { mutableStateOf(despesaParaEditar!!.descricao) }
        var eVal by remember { mutableStateOf(despesaParaEditar!!.valor.toString()) }
        var eDia by remember { mutableStateOf(despesaParaEditar!!.diaVencimento.toString()) }
        var eTipo by remember { mutableStateOf(despesaParaEditar!!.tipo) }
        var eCat by remember { mutableStateOf(despesaParaEditar!!.categoria) }

        AlertDialog(
            onDismissRequest = { despesaParaEditar = null }, title = { Text("Editar Lançamento", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = eDesc, onValueChange = { eDesc = it }, label = { Text("Descrição") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = eVal, onValueChange = { eVal = it }, label = { Text("Valor (R$)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = eDia, onValueChange = { if(it.length <= 2) eDia = it }, label = { Text("Dia do Vencimento") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { eTipo = "Fixa" }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (eTipo == "Fixa") colorAccent.copy(alpha=0.1f) else Color.Transparent)) { Text("Fixa", color = if (eTipo == "Fixa") colorAccent else Color.Gray) }
                        OutlinedButton(onClick = { eTipo = "Variável" }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (eTipo == "Variável") colorAccent.copy(alpha=0.1f) else Color.Transparent)) { Text("Variável", color = if (eTipo == "Variável") colorAccent else Color.Gray) }
                    }

                    Text("Categoria:", fontSize = 12.sp, color = Color.Gray)
                    LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val categorias = listOf("Moradia", "Alimentação", "Transporte", "Saúde", "Educação", "Lazer", "Outros")
                        items(categorias) { cat ->
                            OutlinedButton(onClick = { eCat = cat }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (eCat == cat) colorAccent.copy(alpha=0.1f) else Color.Transparent), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), modifier = Modifier.height(32.dp)) { Text(cat, fontSize = 12.sp, color = if (eCat == cat) colorAccent else Color.Gray) }
                        }
                    }
                }
            },
            confirmButton = {
                Button(colors = ButtonDefaults.buttonColors(containerColor = colorAccent), onClick = {
                    val valorFormatado = eVal.replace(",", ".").toDoubleOrNull()
                    if (eDesc.isNotBlank() && valorFormatado != null) {
                        banco.collection("usuarios").document(usuario!!.uid).collection("despesas").document(despesaParaEditar!!.id).update(mapOf("descricao" to eDesc, "valor" to valorFormatado, "diaVencimento" to (eDia.toIntOrNull() ?: 0), "tipo" to eTipo, "categoria" to eCat)).addOnSuccessListener { Toast.makeText(context, "Atualizado!", Toast.LENGTH_SHORT).show(); despesaParaEditar = null }
                    }
                }) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { despesaParaEditar = null }) { Text("Cancelar", color = colorTextSecondary) } }
        )
    }
}

@Composable
fun ResumoMiniUI(label: String, valor: Double, icone: ImageVector, cor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(38.dp).background(cor.copy(alpha = 0.15f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Icon(icone, null, tint = cor, modifier = Modifier.size(20.dp)) }
        Spacer(modifier = Modifier.width(10.dp))
        Column { Text(label, fontSize = 12.sp, color = Color(0xFF6B7280), fontWeight = FontWeight.Medium); Text("R$ %.2f".format(valor), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF111827)) }
    }
}

@Composable
fun DespesaCardUI(despesa: Despesa, onStatusChange: (String) -> Unit, onEditClick: () -> Unit, onDeleteClick: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }

    val (colorStatusBg, colorStatusText) = when (despesa.status) {
        "Pago" -> Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32))
        "A pagar" -> Pair(Color(0xFFFFF3E0), Color(0xFFEF6C00))
        "Próximo Mês" -> Pair(Color(0xFFE3F2FD), Color(0xFF1565C0))
        "Renegociar" -> Pair(Color(0xFFFFEBEE), Color(0xFFC62828))
        else -> Pair(Color(0xFFF3F4F6), Color(0xFF6B7280))
    }

    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFF3F4F6), RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).background(Color(0xFFF8F9FA), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.AttachMoney, null, tint = Color(0xFF7E57C2), modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(despesa.descricao, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF111827))
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(despesa.categoria, fontSize = 12.sp, color = Color.Gray)
                    if (despesa.diaVencimento > 0) {
                        Text(" • Dia ${despesa.diaVencimento}", fontSize = 12.sp, color = if (despesa.status != "Pago") Color(0xFFEF5350) else Color.Gray, fontWeight = if(despesa.status != "Pago") FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("R$ %.2f".format(despesa.valor), fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFF111827))
                    Box {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp).padding(start = 4.dp)) { Icon(Icons.Default.MoreVert, null, tint = Color.Gray) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, modifier = Modifier.background(Color.White)) {
                            val opcoes = listOf("Pago", "A pagar", "Próximo Mês", "Renegociar")
                            opcoes.forEach { opcao ->
                                DropdownMenuItem(text = { Text(opcao, fontWeight = FontWeight.Medium) }, onClick = { onStatusChange(opcao); menuOpen = false })
                            }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Editar Informações", fontWeight = FontWeight.Medium) }, onClick = { onEditClick(); menuOpen = false })
                            DropdownMenuItem(text = { Text("Excluir", color = Color.Red, fontWeight = FontWeight.Medium) }, onClick = { onDeleteClick(); menuOpen = false })
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = colorStatusBg,
                    modifier = Modifier.clickable { menuOpen = true }
                ) {
                    Text(despesa.status.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colorStatusText, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), letterSpacing = 0.5.sp)
                }
            }
        }
    }
}