package fluxai.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import java.util.Date

// =========================================================================
// CONTA CONJUNTA POR CONVITE
// O dono gera um código (vale 24h); o parceiro digita o código e vira membro em usuarios/{dono}/membros/{uid}.
// As regras do Firestore só liberam os dados para o dono e para membros registrados.
// =========================================================================
private data class Membro(val uid: String, val nome: String, val email: String)

// Sem letras/números ambíguos (0/O, 1/I/L)
private fun gerarCodigoConvite() = (1..6).map { "ABCDEFGHJKMNPQRSTUVWXYZ23456789".random() }.joinToString("")

@Composable
fun SecaoContaConjunta(
    workspaceUid: String,
    onWorkspaceChange: (String) -> Unit,
    colorAccent: Color,
    colorBg: Color,
    colorSurface: Color,
    colorTextPrimary: Color,
    colorTextSecondary: Color,
    colorDivider: Color,
    colorDanger: Color
) {
    val context = LocalContext.current
    val usuario = Firebase.auth.currentUser ?: return
    val db = Firebase.firestore
    val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
    val conectado = workspaceUid != usuario.uid

    var codigoGerado by remember { mutableStateOf<String?>(null) }
    var gerando by remember { mutableStateOf(false) }
    var codigoDigitado by remember { mutableStateOf("") }
    var entrando by remember { mutableStateOf(false) }
    var membros by remember { mutableStateOf<List<Membro>>(emptyList()) }
    // null = verificando; false = vínculo antigo sem registro de membro (acesso bloqueado pelas regras)
    var vinculoValido by remember { mutableStateOf<Boolean?>(null) }

    // Dono: acompanha quem entrou na conta
    DisposableEffect(usuario.uid, conectado) {
        val ouvintes = mutableListOf<ListenerRegistration>()
        if (!conectado) {
            ouvintes += db.collection("usuarios").document(usuario.uid).collection("membros").addSnapshotListener { snap, _ ->
                membros = snap?.documents?.map { Membro(it.id, it.getString("nome") ?: "Parceiro(a)", it.getString("email") ?: "") } ?: emptyList()
            }
        }
        onDispose { ouvintes.forEach { it.remove() } }
    }

    // Parceiro: confere se o vínculo está registrado
    LaunchedEffect(workspaceUid) {
        if (conectado) {
            vinculoValido = null
            db.collection("usuarios").document(workspaceUid).collection("membros").document(usuario.uid).get()
                .addOnSuccessListener { vinculoValido = it.exists() }
                .addOnFailureListener { vinculoValido = false }
        }
    }

    fun gerarConvite(tentativa: Int = 0) {
        gerando = true
        val codigo = gerarCodigoConvite()
        val expira = Timestamp(Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000L))
        db.collection("convites").document(codigo)
            .set(mapOf("dono" to usuario.uid, "donoNome" to (usuario.displayName ?: usuario.email ?: ""), "expiraEm" to expira, "criadoEm" to Timestamp.now()))
            .addOnSuccessListener { codigoGerado = codigo; gerando = false }
            .addOnFailureListener {
                // Código já existente é recusado pelas regras: tenta outro
                if (tentativa < 3) gerarConvite(tentativa + 1) else { gerando = false; Toast.makeText(context, "Não foi possível gerar o convite.", Toast.LENGTH_SHORT).show() }
            }
    }

    fun entrarComCodigo() {
        val codigo = codigoDigitado.trim().uppercase()
        if (codigo.length != 6) { Toast.makeText(context, "O código tem 6 caracteres.", Toast.LENGTH_SHORT).show(); return }
        entrando = true
        db.collection("convites").document(codigo).get()
            .addOnSuccessListener { conv ->
                val dono = conv.getString("dono")
                val expira = conv.getTimestamp("expiraEm")
                when {
                    !conv.exists() || dono == null -> { entrando = false; Toast.makeText(context, "Convite não encontrado.", Toast.LENGTH_SHORT).show() }
                    dono == usuario.uid -> { entrando = false; Toast.makeText(context, "Esse convite é seu. Envie para o parceiro(a).", Toast.LENGTH_SHORT).show() }
                    expira == null || expira.toDate().before(Date()) -> { entrando = false; Toast.makeText(context, "Convite expirado. Peça um novo.", Toast.LENGTH_SHORT).show() }
                    else -> db.collection("usuarios").document(dono).collection("membros").document(usuario.uid)
                        .set(mapOf("codigo" to codigo, "nome" to (usuario.displayName ?: ""), "email" to (usuario.email ?: ""), "entrouEm" to Timestamp.now()))
                        .addOnSuccessListener {
                            prefs.edit { putString("workspace_uid", dono); putString("workspace_nome", conv.getString("donoNome") ?: "") }
                            onWorkspaceChange(dono)
                            codigoDigitado = ""
                            entrando = false
                            Toast.makeText(context, "Contas conectadas!", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { entrando = false; Toast.makeText(context, "Não foi possível entrar: convite inválido.", Toast.LENGTH_SHORT).show() }
                }
            }
            .addOnFailureListener { entrando = false; Toast.makeText(context, "Sem conexão. Tente de novo.", Toast.LENGTH_SHORT).show() }
    }

    fun desconectar() {
        val dono = workspaceUid
        db.collection("usuarios").document(dono).collection("membros").document(usuario.uid).delete()
        prefs.edit { putString("workspace_uid", usuario.uid); remove("workspace_nome") }
        onWorkspaceChange(usuario.uid)
        Toast.makeText(context, "Conta desconectada.", Toast.LENGTH_SHORT).show()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colorSurface),
        border = BorderStroke(1.dp, colorDivider)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            if (conectado) {
                // ===== PARCEIRO CONECTADO =====
                val nomeDono = prefs.getString("workspace_nome", null)?.takeIf { it.isNotBlank() }
                val (cor, icone, titulo) = when (vinculoValido) {
                    false -> Triple(Color(0xFFFB8C00), Icons.Default.Warning, "Vínculo pendente")
                    else -> Triple(Color(0xFF4CAF50), Icons.Default.Link, "Conta conjunta ativa")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icone, null, tint = cor)
                    Spacer(Modifier.width(8.dp))
                    Text(titulo, fontWeight = FontWeight.Bold, color = cor)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (vinculoValido == false) "Esta conexão foi feita pelo método antigo e não tem mais acesso. Peça ao dono da conta um código de convite e entre de novo."
                    else "Você está usando a conta de ${nomeDono ?: "seu parceiro(a)"}. Lançamentos, saldos e cartões são compartilhados.",
                    fontSize = 13.sp, color = colorTextSecondary, lineHeight = 18.sp
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { desconectar() },
                    colors = ButtonDefaults.buttonColors(containerColor = colorDanger.copy(alpha = 0.1f), contentColor = colorDanger),
                    modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)
                ) { Text("Sair da conta conjunta", fontWeight = FontWeight.Bold) }
            } else {
                // ===== DONO: CONVIDAR =====
                Text("Convide seu parceiro(a) para compartilhar o fluxo de caixa. O código vale por 24 horas.", fontSize = 13.sp, color = colorTextSecondary, lineHeight = 18.sp)
                Spacer(Modifier.height(14.dp))

                val codigo = codigoGerado
                if (codigo == null) {
                    Button(
                        onClick = { gerarConvite() }, enabled = !gerando,
                        colors = ButtonDefaults.buttonColors(containerColor = colorAccent),
                        modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)
                    ) {
                        if (gerando) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else { Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text("Gerar código de convite", fontWeight = FontWeight.Bold) }
                    }
                } else {
                    Surface(color = colorAccent.copy(alpha = 0.08f), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, colorAccent.copy(alpha = 0.4f)), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Código de convite", fontSize = 12.sp, color = colorTextSecondary)
                            Text(codigo.chunked(3).joinToString(" "), fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp, fontFamily = FontFamily.Monospace, color = colorAccent)
                            Text("Válido por 24 horas", fontSize = 11.sp, color = colorTextSecondary)
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cb.setPrimaryClip(ClipData.newPlainText("Convite FluxAí", codigo))
                                    Toast.makeText(context, "Código copiado!", Toast.LENGTH_SHORT).show()
                                }, shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Copiar") }
                                Button(onClick = {
                                    val envio = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "Entre na minha conta do FluxAí: abra Configurações > Conta Conjunta e digite o código $codigo (vale por 24h).")
                                    }
                                    context.startActivity(Intent.createChooser(envio, "Enviar convite"))
                                }, colors = ButtonDefaults.buttonColors(containerColor = colorAccent), shape = RoundedCornerShape(12.dp)) {
                                    Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Enviar")
                                }
                            }
                        }
                    }
                }

                // Membros atuais
                if (membros.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("Pessoas com acesso", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colorTextPrimary)
                    membros.forEach { m ->
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(36.dp).background(colorAccent.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                                Text((m.nome.ifBlank { m.email }).take(1).uppercase(), fontWeight = FontWeight.Bold, color = colorAccent)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.nome.ifBlank { "Parceiro(a)" }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colorTextPrimary)
                                if (m.email.isNotBlank()) Text(m.email, fontSize = 11.sp, color = colorTextSecondary)
                            }
                            IconButton(onClick = {
                                db.collection("usuarios").document(usuario.uid).collection("membros").document(m.uid).delete()
                                    .addOnSuccessListener { Toast.makeText(context, "Acesso removido.", Toast.LENGTH_SHORT).show() }
                            }) { Icon(Icons.Default.PersonRemove, "Remover acesso", tint = colorDanger) }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp), color = colorDivider)

                // ===== PARCEIRO: ENTRAR COM CÓDIGO =====
                Text("Recebeu um convite?", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colorTextPrimary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = codigoDigitado,
                    onValueChange = { codigoDigitado = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) },
                    label = { Text("Código de 6 caracteres", color = colorTextSecondary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                    shape = FormatoCampo,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colorAccent, unfocusedBorderColor = colorDivider, focusedTextColor = colorTextPrimary, unfocusedTextColor = colorTextPrimary)
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { entrarComCodigo() }, enabled = !entrando && codigoDigitado.length == 6,
                    colors = ButtonDefaults.buttonColors(containerColor = colorAccent),
                    modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)
                ) {
                    if (entrando) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("Entrar na conta conjunta", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
