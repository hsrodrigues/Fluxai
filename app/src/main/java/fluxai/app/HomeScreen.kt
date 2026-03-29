package fluxai.app

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAbrirDashboard: () -> Unit,
    onAbrirAnalytics: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val usuario = Firebase.auth.currentUser
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Estados do Formulário
    var descricao by remember { mutableStateOf("") }
    var valor by remember { mutableStateOf("") }
    var diaVencimento by remember { mutableStateOf("") }
    var observacao by remember { mutableStateOf("") }
    var tipoDespesa by remember { mutableStateOf("Variável") }
    var categoriaDespesa by remember { mutableStateOf("Outros") }

    // Estado para o Mês Retroativo
    var calendar by remember { mutableStateOf(Calendar.getInstance()) }
    var mesAnoSelecionado by remember { mutableStateOf(SimpleDateFormat("MM/yyyy", Locale("pt", "BR")).format(calendar.time)) }

    // Controles de UI
    var expandidoCategoria by remember { mutableStateOf(false) }
    var mostrarDialogCalendario by remember { mutableStateOf(false) }
    var salvando by remember { mutableStateOf(false) }

    val categorias = listOf("Moradia", "Alimentação", "Transporte", "Saúde", "Educação", "Lazer", "Outros")

    // Cores do Tema
    val colorAccent = Color(0xFF7E57C2)
    val colorBg = Color(0xFFF8F9FA)
    val colorSurface = Color.White
    val colorTextPrimary = Color(0xFF1E1E1E)
    val colorTextSecondary = Color(0xFF6B7280)
    val colorDarkBg = Color(0xFF1E1E1E)

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
                NavigationDrawerItem(label = { Text("Novo Lançamento", fontWeight = FontWeight.Medium) }, selected = true, icon = { Icon(Icons.Default.AddCircle, null) }, colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = colorAccent.copy(alpha = 0.1f), selectedIconColor = colorAccent, selectedTextColor = colorAccent), onClick = { coroutineScope.launch { drawerState.close() } }, modifier = Modifier.padding(horizontal = 12.dp))
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
                    title = { Text("Novo Lançamento", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colorTextPrimary) },
                    navigationIcon = { IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, null, tint = colorTextPrimary) } },
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
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = colorSurface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {

                        // CAMPO: MÊS REFERÊNCIA (RETROATIVO)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = mesAnoSelecionado,
                                onValueChange = { },
                                readOnly = true,
                                label = { Text("Mês de Referência") },
                                leadingIcon = { Icon(Icons.Default.CalendarMonth, null, tint = colorAccent) },
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, tint = Color.Gray) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colorAccent)
                            )
                            Box(modifier = Modifier.matchParentSize().clickable { mostrarDialogCalendario = true })
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // CAMPO: DESCRIÇÃO
                        OutlinedTextField(
                            value = descricao,
                            onValueChange = { descricao = it },
                            label = { Text("Descrição da despesa") },
                            leadingIcon = { Icon(Icons.Default.Description, null, tint = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colorAccent)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // ROW: VALOR E DIA
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = valor,
                                onValueChange = { valor = it },
                                label = { Text("Valor (R$)") },
                                leadingIcon = { Icon(Icons.Default.AttachMoney, null, tint = Color.Gray) },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colorAccent)
                            )
                            OutlinedTextField(
                                value = diaVencimento,
                                onValueChange = { if(it.length <= 2) diaVencimento = it },
                                label = { Text("Dia") },
                                modifier = Modifier.weight(0.6f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colorAccent)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // BOTÕES: FIXA / VARIÁVEL
                        Text("Tipo de Despesa", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colorTextSecondary, modifier = Modifier.padding(bottom = 8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { tipoDespesa = "Fixa" },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if(tipoDespesa=="Fixa") colorAccent else Color(0xFFE5E7EB)),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = if(tipoDespesa=="Fixa") colorAccent.copy(0.1f) else Color.Transparent)
                            ) { Text("Fixa", color = if(tipoDespesa=="Fixa") colorAccent else Color.Gray, fontWeight = FontWeight.Bold) }

                            OutlinedButton(
                                onClick = { tipoDespesa = "Variável" },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if(tipoDespesa=="Variável") colorAccent else Color(0xFFE5E7EB)),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = if(tipoDespesa=="Variável") colorAccent.copy(0.1f) else Color.Transparent)
                            ) { Text("Variável", color = if(tipoDespesa=="Variável") colorAccent else Color.Gray, fontWeight = FontWeight.Bold) }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // CAMPO: CATEGORIA
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = categoriaDespesa,
                                onValueChange = { },
                                readOnly = true,
                                label = { Text("Categoria") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colorAccent),
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
                            )
                            Box(modifier = Modifier.matchParentSize().clickable { expandidoCategoria = !expandidoCategoria })
                            DropdownMenu(
                                expanded = expandidoCategoria,
                                onDismissRequest = { expandidoCategoria = false },
                                modifier = Modifier.fillMaxWidth(0.8f).background(Color.White)
                            ) {
                                categorias.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat) },
                                        onClick = { categoriaDespesa = cat; expandidoCategoria = false }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // CAMPO: OBSERVAÇÃO
                        OutlinedTextField(
                            value = observacao,
                            onValueChange = { observacao = it },
                            label = { Text("Observações (Opcional)") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colorAccent)
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        // BOTÃO SALVAR
                        Button(
                            onClick = {
                                if (usuario != null && descricao.isNotBlank() && valor.isNotBlank() && diaVencimento.isNotBlank()) {
                                    salvando = true
                                    val vFinal = valor.replace(",", ".").toDoubleOrNull() ?: 0.0
                                    val dFinal = diaVencimento.toIntOrNull() ?: 0

                                    val despesaMap = hashMapOf(
                                        "descricao" to descricao,
                                        "valor" to vFinal,
                                        "diaVencimento" to dFinal,
                                        "tipo" to tipoDespesa,
                                        "categoria" to categoriaDespesa,
                                        "status" to "A pagar",
                                        "mesAno" to mesAnoSelecionado, // Salva no mês escolhido!
                                        "observacao" to observacao
                                    )

                                    Firebase.firestore.collection("usuarios").document(usuario.uid).collection("despesas").add(despesaMap)
                                        .addOnSuccessListener {
                                            salvando = false
                                            Toast.makeText(context, "Despesa Registrada!", Toast.LENGTH_SHORT).show()
                                            descricao = ""; valor = ""; diaVencimento = ""; observacao = ""
                                            onAbrirDashboard() // Volta para o Dashboard automaticamente após salvar
                                        }
                                        .addOnFailureListener {
                                            salvando = false
                                            Toast.makeText(context, "Erro ao salvar.", Toast.LENGTH_SHORT).show()
                                        }
                                } else {
                                    Toast.makeText(context, "Preencha Descrição, Valor e Dia.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colorAccent),
                            enabled = !salvando
                        ) {
                            if(salvando) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                Text("Registrar Lançamento", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    // DIALOG DO CALENDÁRIO (Para mês retroativo)
    if (mostrarDialogCalendario) {
        var anoTemp by remember { mutableStateOf(calendar.get(Calendar.YEAR)) }
        val mesesAbrev = listOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")

        AlertDialog(
            onDismissRequest = { mostrarDialogCalendario = false },
            title = { Text("Mês de Referência", fontWeight = FontWeight.Bold) },
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
                                        calendar.set(Calendar.YEAR, anoTemp)
                                        calendar.set(Calendar.MONTH, mesIndex)
                                        mesAnoSelecionado = SimpleDateFormat("MM/yyyy", Locale("pt", "BR")).format(calendar.time)
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
}