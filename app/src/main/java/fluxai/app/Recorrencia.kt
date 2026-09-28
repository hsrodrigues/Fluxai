package fluxai.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// =========================================================================
// CONTAS RECORRENTES
// Sugere trazer do mês anterior as despesas fixas e as marcadas como "Próximo Mês"
// que ainda não existem no mês aberto. Nada é criado sem a confirmação do usuário.
// =========================================================================
private val regexParcela = Regex(".*\\(\\d+/\\d+\\)$")

fun mesAnterior(mesAno: String): String {
    val (m, a) = mesAno.split("/").map { it.toInt() }
    val c = Calendar.getInstance().apply { set(a, m - 1, 1); add(Calendar.MONTH, -1) }
    return SimpleDateFormat("MM/yyyy", Locale("pt", "BR")).format(c.time)
}

private fun nomeBase(descricao: String) = descricao.trim().lowercase()

// Despesas do mês anterior que deveriam se repetir e ainda não estão no mês atual
fun candidatasRecorrencia(anteriores: List<Despesa>, atuais: List<Despesa>): List<Despesa> {
    val jaExistem = atuais.map { nomeBase(it.descricao) }.toSet()
    return anteriores.filter { d ->
        val repete = d.status == "Próximo Mês" ||
            (d.tipo == "Fixa" && d.projetoId == null && d.frequencia != "Única" && !d.descricao.matches(regexParcela))
        repete && nomeBase(d.descricao) !in jaExistem
    }.distinctBy { nomeBase(it.descricao) }
}

fun lerDespesa(d: com.google.firebase.firestore.DocumentSnapshot) = Despesa(
    id = d.id,
    descricao = d.getString("descricao") ?: "",
    valor = d.getDouble("valor") ?: 0.0,
    tipo = d.getString("tipo") ?: "Variável",
    categoria = d.getString("categoria") ?: "Outros",
    status = d.getString("status") ?: "A pagar",
    observacao = d.getString("observacao") ?: "",
    diaVencimento = d.getLong("diaVencimento")?.toInt() ?: 0,
    frequencia = d.getString("frequencia") ?: "Mensal",
    mesAno = d.getString("mesAno") ?: "",
    cartaoId = d.getString("cartaoId"),
    projetoId = d.getString("projetoId"),
    contaId = d.getString("contaId"),
    pagoPor = d.getString("pagoPor"),
    pagoPorNome = d.getString("pagoPorNome"),
    comprovante = d.getString("comprovante"),
    caixinhaId = d.getString("caixinhaId")
)

// Grava as escolhidas no mês atual como "A pagar", num lote só
fun trazerRecorrentes(workspaceUid: String, mesAno: String, escolhidas: List<Despesa>, onFim: (Boolean) -> Unit) {
    val db = Firebase.firestore
    val usuario = db.collection("usuarios").document(workspaceUid)
    val lote = db.batch()
    escolhidas.forEach { d ->
        lote.set(usuario.collection("despesas").document(), hashMapOf(
            "descricao" to d.descricao,
            "valor" to d.valor,
            "diaVencimento" to d.diaVencimento,
            "tipo" to d.tipo,
            "categoria" to d.categoria,
            "status" to "A pagar",
            "mesAno" to mesAno,
            "observacao" to d.observacao,
            "frequencia" to d.frequencia,
            "cartaoId" to d.cartaoId,
            "projetoId" to d.projetoId,
            "contaId" to d.contaId
        ))
        val cartao = d.cartaoId
        if (!cartao.isNullOrBlank() && cartao != "Saldo Conta") {
            lote.update(usuario.collection("cartoes").document(cartao), "faturaAtual", FieldValue.increment(d.valor))
        }
    }
    lote.commit().addOnSuccessListener { onFim(true) }.addOnFailureListener { onFim(false) }
}

@Composable
fun AvisoRecorrencia(qtd: Int, total: Double, mesOrigem: String, cor: Color, corSuperficie: Color, corTexto: Color, corTextoFraco: Color, onAbrir: () -> Unit, onDispensar: () -> Unit) {
    val moeda = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    Surface(
        shape = RoundedCornerShape(16.dp), color = corSuperficie, border = BorderStroke(1.dp, cor.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).clickable(onClick = onAbrir)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(cor.copy(alpha = 0.14f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.EventRepeat, null, tint = cor)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("$qtd ${if (qtd == 1) "conta" else "contas"} de $mesOrigem para trazer", fontWeight = FontWeight.SemiBold, color = corTexto, fontSize = 14.sp)
                Text("Fixas e adiadas · ${moeda.format(total)} · toque para revisar", fontSize = 12.sp, color = corTextoFraco)
            }
            IconButton(onClick = onDispensar) { Icon(Icons.Default.Close, "Dispensar", tint = corTextoFraco) }
        }
    }
}

@Composable
fun DialogoRecorrencia(candidatas: List<Despesa>, cor: Color, corSuperficie: Color, corTexto: Color, corTextoFraco: Color, onConfirmar: (List<Despesa>) -> Unit, onFechar: () -> Unit) {
    val moeda = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    val marcadas = remember(candidatas) { mutableStateListOf<String>().apply { addAll(candidatas.map { it.id }) } }
    val escolhidas = candidatas.filter { it.id in marcadas }
    AlertDialog(
        onDismissRequest = onFechar,
        containerColor = corSuperficie,
        title = { Text("Trazer para este mês", fontWeight = FontWeight.Bold, color = corTexto) },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(candidatas, key = { it.id }) { d ->
                    Row(
                        Modifier.fillMaxWidth().clickable { if (d.id in marcadas) marcadas.remove(d.id) else marcadas.add(d.id) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = d.id in marcadas, onCheckedChange = { if (it) marcadas.add(d.id) else marcadas.remove(d.id) }, colors = CheckboxDefaults.colors(checkedColor = cor))
                        Column(Modifier.weight(1f)) {
                            Text(d.descricao, color = corTexto, fontSize = 14.sp, maxLines = 1)
                            Text(if (d.status == "Próximo Mês") "Adiada do mês passado" else "Dia ${d.diaVencimento} · ${d.categoria}", fontSize = 11.sp, color = if (d.status == "Próximo Mês") cor else corTextoFraco)
                        }
                        Text(moeda.format(d.valor), fontWeight = FontWeight.SemiBold, color = corTexto, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirmar(escolhidas) }, enabled = escolhidas.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = cor)) {
                Text("Trazer ${escolhidas.size} · ${moeda.format(escolhidas.sumOf { it.valor })}")
            }
        },
        dismissButton = { TextButton(onClick = onFechar) { Text("Agora não", color = corTextoFraco) } }
    )
}
