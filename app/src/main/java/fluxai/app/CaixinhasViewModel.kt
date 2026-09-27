package fluxai.app

import androidx.lifecycle.ViewModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Dados e gravações do Cofre/Metas. Sobrevive a rotação de tela; o ouvinte é desligado em onCleared.
class CaixinhasViewModel : ViewModel() {
    private val banco = Firebase.firestore
    private var ouvinte: ListenerRegistration? = null
    private var workspaceUid = ""

    private val _caixinhas = MutableStateFlow<List<Caixinha>>(emptyList())
    val caixinhas: StateFlow<List<Caixinha>> = _caixinhas.asStateFlow()

    private fun usuarioDoc() = banco.collection("usuarios").document(workspaceUid)

    fun observar(workspace: String) {
        if (workspace == workspaceUid || workspace.isBlank()) return
        workspaceUid = workspace
        ouvinte?.remove()
        ouvinte = usuarioDoc().collection("caixinhas").addSnapshotListener { snap, _ ->
            _caixinhas.value = snap?.documents?.mapNotNull { d ->
                runCatching { Caixinha(d.id, d.getString("nome") ?: "", d.getDouble("meta") ?: 0.0, d.getDouble("saldo") ?: 0.0, d.getString("icone") ?: "savings", d.getString("prazo") ?: "") }.getOrNull()
            } ?: emptyList()
        }
    }

    // Prazo vazio segura a meta sem data; com prazo, o app calcula quanto guardar por mês
    fun definirPrazo(cx: Caixinha, prazo: String) {
        usuarioDoc().collection("caixinhas").document(cx.id).update("prazo", prazo)
    }

    fun criar(nome: String, meta: Double, icone: String, prazo: String, aoTerminar: (Boolean) -> Unit) {
        usuarioDoc().collection("caixinhas")
            .add(mapOf("nome" to nome.trim(), "meta" to meta, "saldo" to 0.0, "icone" to icone, "prazo" to prazo))
            .addOnSuccessListener { aoTerminar(true) }
            .addOnFailureListener { aoTerminar(false) }
    }

    fun excluir(cx: Caixinha) {
        usuarioDoc().collection("caixinhas").document(cx.id).delete()
    }

    // Guardar vira despesa paga no mês; resgatar vira renda extra. Tudo num lote, com incrementos (seguro na conta conjunta).
    fun movimentar(cx: Caixinha, guardar: Boolean, valor: Double, aoTerminar: (Boolean) -> Unit) {
        val cal = Calendar.getInstance()
        val mesAnoAtual = SimpleDateFormat("MM/yyyy", Locale("pt", "BR")).format(cal.time)
        val lote = banco.batch()
        lote.update(usuarioDoc().collection("caixinhas").document(cx.id), "saldo", FieldValue.increment(if (guardar) valor else -valor))
        if (guardar) {
            lote.set(usuarioDoc().collection("despesas").document(), hashMapOf(
                "descricao" to "Apontamento: ${cx.nome}",
                "valor" to valor,
                "diaVencimento" to cal.get(Calendar.DAY_OF_MONTH),
                "tipo" to "Variável",
                "categoria" to "Outros",
                "status" to "Pago",
                "mesAno" to mesAnoAtual,
                "observacao" to "Transferido para a meta",
                "frequencia" to "Única"
            ))
        } else {
            lote.set(
                usuarioDoc().collection("saldos").document(mesAnoAtual.replace("/", "-")),
                mapOf("extra" to FieldValue.increment(valor), "valor" to FieldValue.increment(valor)),
                SetOptions.merge()
            )
        }
        lote.commit().addOnSuccessListener { aoTerminar(true) }.addOnFailureListener { aoTerminar(false) }
    }

    override fun onCleared() {
        ouvinte?.remove()
    }
}
