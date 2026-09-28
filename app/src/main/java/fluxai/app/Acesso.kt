package fluxai.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.functions
import android.widget.Toast
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale

// =========================================================================
// ACESSO: todo cadastro novo fica pendente até o administrador ativar.
// A trava de verdade está no servidor (firestore.rules, storage.rules e Functions);
// aqui o app só mostra a tela certa.
// =========================================================================
const val EMAIL_ADMIN = "hsrodrigues01@gmail.com"

fun ehAdmin(usuario: FirebaseUser? = Firebase.auth.currentUser) =
    usuario != null && usuario.email == EMAIL_ADMIN && usuario.isEmailVerified

enum class StatusAcesso { SEM_LOGIN, CARREGANDO, ATIVO, PENDENTE }

// Acompanha o login e o registro acesso/{uid}; cria o registro pendente no primeiro acesso
@Composable
fun rememberStatusAcesso(): State<StatusAcesso> {
    val status = remember { mutableStateOf(StatusAcesso.CARREGANDO) }
    var usuario by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val ouvinte = FirebaseAuth.AuthStateListener { usuario = it.currentUser }
        Firebase.auth.addAuthStateListener(ouvinte)
        onDispose { Firebase.auth.removeAuthStateListener(ouvinte) }
    }

    DisposableEffect(usuario?.uid) {
        val atual = usuario
        when {
            atual == null -> { status.value = StatusAcesso.SEM_LOGIN; return@DisposableEffect onDispose { } }
            ehAdmin(atual) -> { status.value = StatusAcesso.ATIVO; return@DisposableEffect onDispose { } }
        }
        status.value = StatusAcesso.CARREGANDO
        val ref = Firebase.firestore.collection("acesso").document(atual.uid)
        val registro = ref.addSnapshotListener { doc, erro ->
            if (erro != null || doc == null) return@addSnapshotListener
            if (!doc.exists()) {
                // Só confia na ausência vinda do servidor: o cache local pode estar vazio
                if (!doc.metadata.isFromCache) {
                    ref.set(mapOf(
                        "nome" to (atual.displayName ?: ""),
                        "email" to (atual.email ?: ""),
                        "ativo" to false,
                        "criadoEm" to Timestamp.now()
                    ))
                    status.value = StatusAcesso.PENDENTE
                }
                return@addSnapshotListener
            }
            status.value = if (doc.getBoolean("ativo") == true) StatusAcesso.ATIVO else StatusAcesso.PENDENTE
        }
        onDispose { registro.remove() }
    }
    return status
}

@Composable
fun TelaAguardandoAtivacao(onSair: () -> Unit) {
    val usuario = Firebase.auth.currentUser
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2A1558), Color(0xFF1C1236), Color(0xFF0E0A1A)))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.HourglassTop, null, tint = Color.White, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(20.dp))
            Text("Aguardando ativação", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(10.dp))
            Text(
                "Sua conta foi criada, mas ainda precisa ser liberada pelo administrador do FluxAí. " +
                    "Assim que for ativada, o app abre sozinho.",
                color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 21.sp
            )
            if (!usuario?.email.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(usuario?.email ?: "", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
            }
            Spacer(Modifier.height(32.dp))
            OutlinedButton(onClick = onSair, shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sair e usar outra conta", color = Color.White)
            }
        }
    }
}

// =========================================================================
// ADMIN: lista de contas com o botão de ativar/desativar (só para o administrador)
// =========================================================================
private data class ContaAcesso(val uid: String, val nome: String, val email: String, val ativo: Boolean, val criadoEm: Timestamp?)

@Composable
fun AdminUsuariosScreen(onLogout: () -> Unit) {
    val c = coresTela()
    var contas by remember { mutableStateOf<List<ContaAcesso>?>(null) }
    var erro by remember { mutableStateOf<String?>(null) }
    var recarregar by remember { mutableIntStateOf(0) }
    var importando by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()

    // Contas criadas antes da ativação só aparecem aqui depois de importadas (entram pendentes)
    fun importarContas() {
        importando = true
        escopo.launch {
            val msg = try {
                val dados = Firebase.functions("southamerica-east1").getHttpsCallable("importarContas").call().await().getData() as? Map<*, *>
                val novas = (dados?.get("novas") as? Number)?.toInt() ?: 0
                if (novas == 0) "Nenhuma conta nova para importar." else "$novas conta(s) importada(s) como pendentes."
            } catch (e: Exception) {
                "Falha ao importar: ${e.message}"
            }
            importando = false
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    DisposableEffect(recarregar) {
        val registro = Firebase.firestore.collection("acesso").orderBy("criadoEm", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null) { erro = e.message; return@addSnapshotListener }
                erro = null
                contas = snap?.documents?.map {
                    ContaAcesso(it.id, it.getString("nome") ?: "", it.getString("email") ?: "", it.getBoolean("ativo") == true, it.getTimestamp("criadoEm"))
                }
            }
        onDispose { registro.remove() }
    }

    TelaComMenu(
        titulo = "Usuários", rota = "admin_usuarios", onLogout = onLogout,
        acoes = {
            IconButton(onClick = { importarContas() }, enabled = !importando) {
                if (importando) CircularProgressIndicator(Modifier.size(20.dp), color = c.destaque, strokeWidth = 2.dp)
                else Icon(Icons.Default.GroupAdd, "Importar contas existentes", tint = c.texto)
            }
            IconButton(onClick = { recarregar++ }) { Icon(Icons.Default.Refresh, "Atualizar", tint = c.texto) }
        }
    ) { padding ->
        val lista = contas
        when {
            erro != null -> Text("Erro ao carregar: $erro", color = c.textoFraco, modifier = Modifier.padding(padding).padding(24.dp))
            lista == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.destaque) }
            lista.isEmpty() -> Text("Nenhum cadastro ainda. Toque no ícone de pessoas no topo para importar as contas existentes.", color = c.textoFraco, modifier = Modifier.padding(padding).padding(24.dp))
            else -> {
                val formato = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR")) }
                val pendentes = lista.count { !it.ativo }
                LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Text(
                            if (pendentes > 0) "$pendentes aguardando ativação" else "Todas as contas estão ativas",
                            color = c.textoFraco, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    items(lista, key = { it.uid }) { conta ->
                        Surface(shape = RoundedCornerShape(14.dp), color = c.superficie, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(conta.nome.ifBlank { "(sem nome)" }, color = c.texto, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(conta.email, color = c.textoFraco, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        (if (conta.ativo) "Ativo" else "Pendente") + (conta.criadoEm?.let { " · desde ${formato.format(it.toDate())}" } ?: ""),
                                        color = if (conta.ativo) Color(0xFF43A047) else Color(0xFFFB8C00), fontSize = 12.sp
                                    )
                                }
                                Switch(
                                    checked = conta.ativo,
                                    onCheckedChange = { novo -> Firebase.firestore.collection("acesso").document(conta.uid).update("ativo", novo) },
                                    colors = SwitchDefaults.colors(checkedTrackColor = c.destaque)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
