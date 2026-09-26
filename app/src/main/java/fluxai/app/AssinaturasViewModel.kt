package fluxai.app

import androidx.lifecycle.ViewModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Assinaturas e contas fixas do mês atual (dados e gravações da tela de Assinaturas)
class AssinaturasViewModel : ViewModel() {
    private val banco = Firebase.firestore
    private var ouvinte: ListenerRegistration? = null
    private var workspaceUid = ""
    val mesAnoAtual: String = SimpleDateFormat("MM/yyyy", Locale("pt", "BR")).format(Calendar.getInstance().time)

    private val _assinaturas = MutableStateFlow<List<Despesa>>(emptyList())
    val assinaturas: StateFlow<List<Despesa>> = _assinaturas.asStateFlow()
    private val _carregando = MutableStateFlow(true)
    val carregando: StateFlow<Boolean> = _carregando.asStateFlow()

    private fun despesas() = banco.collection("usuarios").document(workspaceUid).collection("despesas")

    fun observar(workspace: String) {
        if (workspace == workspaceUid || workspace.isBlank()) return
        workspaceUid = workspace
        ouvinte?.remove()
        _carregando.value = true
        ouvinte = despesas().whereEqualTo("mesAno", mesAnoAtual).addSnapshotListener { snap, _ ->
            if (snap != null) _assinaturas.value = snap.documents.mapNotNull { runCatching { lerDespesa(it) }.getOrNull() }.filter { ehAssinatura(it) }
            _carregando.value = false
        }
    }

    fun criar(nome: String, valor: Double, dia: Int, aoTerminar: (Boolean) -> Unit) {
        despesas().add(mapOf(
            "descricao" to nome.trim(),
            "valor" to valor,
            "diaVencimento" to dia.coerceIn(1, 31),
            "tipo" to "Fixa",
            "frequencia" to "Mensal",
            "categoria" to categoriaPorNome(nome),
            "status" to "A pagar",
            "mesAno" to mesAnoAtual,
            "ordem" to 0
        )).addOnSuccessListener { aoTerminar(true) }.addOnFailureListener { aoTerminar(false) }
    }

    fun editar(d: Despesa, nome: String, valor: Double, dia: Int, aoTerminar: (Boolean) -> Unit) {
        despesas().document(d.id).update(mapOf("descricao" to nome.trim(), "valor" to valor, "diaVencimento" to dia.coerceIn(1, 31)))
            .addOnSuccessListener { aoTerminar(true) }.addOnFailureListener { aoTerminar(false) }
    }

    fun alternarPago(d: Despesa) {
        despesas().document(d.id).update("status", if (d.status == "Pago") "A pagar" else "Pago")
    }

    fun excluir(d: Despesa) {
        despesas().document(d.id).delete()
    }

    override fun onCleared() {
        ouvinte?.remove()
    }
}
