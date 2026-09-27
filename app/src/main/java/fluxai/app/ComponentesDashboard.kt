package fluxai.app

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

// =========================================================================
// CARTÕES EXTRAS DO DASHBOARD
// Compras detectadas nas notificações, categorias perto do limite e envio de comprovante.
// =========================================================================
data class CompraPendente(
    val id: String, val descricao: String, val valor: Double, val banco: String, val noCartao: Boolean,
    val categoria: String, val dia: Int, val mesAno: String, val detectadaEm: Long
)

fun lerCompraPendente(d: DocumentSnapshot) = CompraPendente(
    id = d.id,
    descricao = d.getString("descricao") ?: "Compra",
    valor = d.getDouble("valor") ?: 0.0,
    banco = d.getString("banco") ?: "",
    noCartao = d.getBoolean("noCartao") ?: false,
    categoria = d.getString("categoria") ?: "Outros",
    dia = d.getLong("dia")?.toInt() ?: 1,
    mesAno = d.getString("mesAno") ?: "",
    detectadaEm = d.getTimestamp("detectadaEm")?.toDate()?.time ?: 0L
)

private val moedaBR = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

@Composable
fun AvisoComprasDetectadas(compras: List<CompraPendente>, cor: Color, corSuperficie: Color, corTexto: Color, corTextoFraco: Color, onAbrir: () -> Unit) {
    val qtd = compras.size
    Surface(
        shape = RoundedCornerShape(16.dp), color = corSuperficie, border = BorderStroke(1.dp, Color(0xFF26A69A).copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).clickable(onClickLabel = "Revisar compras", onClick = onAbrir)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(Color(0xFF26A69A).copy(alpha = 0.14f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.NotificationsActive, null, tint = Color(0xFF26A69A))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("$qtd ${if (qtd == 1) "compra detectada" else "compras detectadas"} no banco", fontWeight = FontWeight.SemiBold, color = corTexto, fontSize = 14.sp)
                Text("${moedaBR.format(compras.sumOf { it.valor })} · toque para registrar", fontSize = 12.sp, color = corTextoFraco)
            }
            Icon(Icons.Default.ChevronRight, null, tint = corTextoFraco)
        }
    }
}

// Onde lançar a compra: cartão ou conta cujo nome lembra o banco da notificação
private fun destinoCompra(c: CompraPendente, cartoes: Map<String, Pair<String, String>>, contas: List<ContaBancaria>): Pair<String?, String?> {
    val banco = c.banco.lowercase().substringBefore(" ")
    val cartao = if (c.noCartao) cartoes.entries.firstOrNull { banco.isNotBlank() && banco in it.value.first.lowercase() }?.key else null
    val conta = if (cartao == null) contas.firstOrNull { banco.isNotBlank() && banco in it.nome.lowercase() }?.id ?: contas.singleOrNull()?.id else null
    return cartao to conta
}

private fun registrarCompra(workspaceUid: String, c: CompraPendente, cartoes: Map<String, Pair<String, String>>, contas: List<ContaBancaria>) {
    val db = Firebase.firestore
    val usuarioDoc = db.collection("usuarios").document(workspaceUid)
    val usuario = Firebase.auth.currentUser
    val (cartaoId, contaId) = destinoCompra(c, cartoes, contas)
    val lote = db.batch()
    val pago = cartaoId == null // compra no débito/Pix já saiu da conta
    lote.set(usuarioDoc.collection("despesas").document(), hashMapOf(
        "descricao" to if (cartaoId != null) "[Cartão] ${c.descricao}" else c.descricao,
        "valor" to c.valor, "diaVencimento" to c.dia, "tipo" to "Variável", "categoria" to c.categoria,
        "status" to if (pago) "Pago" else "A pagar", "mesAno" to c.mesAno, "observacao" to "Detectado no ${c.banco}",
        "frequencia" to "Mensal", "cartaoId" to cartaoId, "contaId" to contaId, "projetoId" to null,
        "pagoPor" to if (pago) usuario?.uid else null, "pagoPorNome" to if (pago) usuario?.displayName?.split(" ")?.firstOrNull() else null,
        "criadoEm" to FieldValue.serverTimestamp()
    ))
    if (cartaoId != null) lote.update(usuarioDoc.collection("cartoes").document(cartaoId), "faturaAtual", FieldValue.increment(c.valor))
    if (pago && contaId != null) lote.update(refConta(workspaceUid, contaId), "saldo", FieldValue.increment(-c.valor))
    lote.delete(usuarioDoc.collection("compras_detectadas").document(c.id))
    lote.commit()
}

private fun descartarCompra(workspaceUid: String, c: CompraPendente) {
    Firebase.firestore.collection("usuarios").document(workspaceUid).collection("compras_detectadas").document(c.id).delete()
}

@Composable
fun DialogoComprasDetectadas(
    compras: List<CompraPendente>, workspaceUid: String,
    cartoes: Map<String, Pair<String, String>>, contas: List<ContaBancaria>,
    cor: Color, corSuperficie: Color, corTexto: Color, corTextoFraco: Color, onFechar: () -> Unit
) {
    LaunchedEffect(compras.isEmpty()) { if (compras.isEmpty()) onFechar() }
    var editandoCategoria by remember { mutableStateOf<String?>(null) }
    var categorias by remember { mutableStateOf<Map<String, String>>(emptyMap()) } // id -> categoria escolhida
    fun comCategoria(c: CompraPendente) = c.copy(categoria = categorias[c.id] ?: c.categoria)

    AlertDialog(
        onDismissRequest = onFechar,
        containerColor = corSuperficie,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Compras detectadas", fontWeight = FontWeight.Bold, color = corTexto) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(compras, key = { it.id }) { c0 ->
                    val c = comCategoria(c0)
                    val (cartaoId, contaId) = destinoCompra(c, cartoes, contas)
                    val destino = cartaoId?.let { "Cartão ${cartoes[it]?.first}" } ?: contaId?.let { id -> contas.firstOrNull { it.id == id }?.nome } ?: "Conta / Pix"
                    Surface(shape = RoundedCornerShape(14.dp), color = cor.copy(alpha = 0.05f), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(c.descricao, fontWeight = FontWeight.SemiBold, color = corTexto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${c.banco} · dia ${c.dia} · $destino", fontSize = 11.sp, color = corTextoFraco, maxLines = 1)
                                }
                                Text(moedaBR.format(c.valor), fontWeight = FontWeight.Bold, color = corTexto)
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) {
                                    AssistChip(
                                        onClick = { editandoCategoria = c.id },
                                        label = { Text(c.categoria, fontSize = 12.sp) },
                                        leadingIcon = { Icon(iconeCategoria(c.categoria), null, Modifier.size(16.dp), tint = corCategoria(c.categoria)) },
                                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, "Trocar categoria", Modifier.size(16.dp)) },
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    DropdownMenu(expanded = editandoCategoria == c.id, onDismissRequest = { editandoCategoria = null }) {
                                        CategoriasPadrao.forEach { cat ->
                                            DropdownMenuItem(text = { Text(cat) }, leadingIcon = { Icon(iconeCategoria(cat), null, tint = corCategoria(cat)) },
                                                onClick = { categorias = categorias + (c.id to cat); editandoCategoria = null })
                                        }
                                    }
                                }
                                IconButton(onClick = { descartarCompra(workspaceUid, c) }) { Icon(Icons.Default.Close, "Descartar ${c.descricao}", tint = corTextoFraco) }
                                FilledTonalIconButton(
                                    onClick = { registrarCompra(workspaceUid, c, cartoes, contas) },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = cor.copy(alpha = 0.15f), contentColor = cor)
                                ) { Icon(Icons.Default.Check, "Registrar ${c.descricao}") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { compras.forEach { registrarCompra(workspaceUid, comCategoria(it), cartoes, contas) }; onFechar() }, colors = ButtonDefaults.buttonColors(containerColor = cor)) {
                Text("Registrar todas")
            }
        },
        dismissButton = { TextButton(onClick = onFechar) { Text("Depois", color = corTextoFraco) } }
    )
}

@Composable
fun CardAlertasOrcamento(alertas: List<AlertaOrcamento>, corSuperficie: Color, corBorda: Color, corTexto: Color, corTextoFraco: Color, onAbrir: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = corSuperficie, border = BorderStroke(1.dp, corBorda), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Speed, null, tint = Color(0xFFFB8C00), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Limites do mês", fontWeight = FontWeight.Bold, color = corTexto, modifier = Modifier.weight(1f))
                TextButton(onClick = onAbrir) { Text("Ajustar", fontSize = 12.sp) }
            }
            alertas.take(4).forEach { a ->
                val cor = if (a.estourou) Color(0xFFE53935) else Color(0xFFFB8C00)
                Column(Modifier.padding(vertical = 6.dp).semantics(mergeDescendants = true) {}) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(iconeCategoria(a.categoria), null, tint = corCategoria(a.categoria), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(a.categoria, fontSize = 13.sp, color = corTexto, modifier = Modifier.weight(1f))
                        Text("${moedaBR.format(a.gasto)} de ${moedaBR.format(a.limite)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = cor)
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { a.uso.toFloat().coerceAtMost(1f) }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        color = cor, trackColor = cor.copy(alpha = 0.15f), strokeCap = StrokeCap.Round
                    )
                    if (a.estourou) Text("Passou ${moedaBR.format(a.gasto - a.limite)} do limite", fontSize = 11.sp, color = cor, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

fun enviarComprovanteComAviso(context: Context, workspaceUid: String, despesa: Despesa, uri: Uri, escopo: CoroutineScope) {
    Toast.makeText(context, "Enviando comprovante...", Toast.LENGTH_SHORT).show()
    escopo.launch {
        runCatching { enviarComprovante(context, workspaceUid, despesa.id, uri) }
            .onSuccess { Toast.makeText(context, "Comprovante anexado.", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(context, "Não foi possível enviar: ${it.message}", Toast.LENGTH_LONG).show() }
    }
}
