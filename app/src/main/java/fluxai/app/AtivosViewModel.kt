package fluxai.app

import androidx.lifecycle.ViewModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Dados e gravações da tela de Ativos & manutenção
class AtivosViewModel : ViewModel() {
    private val banco = Firebase.firestore
    private var ouvinte: ListenerRegistration? = null
    private var workspaceUid = ""

    private val _ativos = MutableStateFlow<List<Ativo>>(emptyList())
    val ativos: StateFlow<List<Ativo>> = _ativos.asStateFlow()

    private fun usuarioDoc() = banco.collection("usuarios").document(workspaceUid)
    private fun ativosCol() = usuarioDoc().collection("ativos")

    fun observar(workspace: String) {
        if (workspace == workspaceUid || workspace.isBlank()) return
        workspaceUid = workspace
        ouvinte?.remove()
        ouvinte = ativosCol().addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            _ativos.value = snap.documents.mapNotNull { d ->
                runCatching {
                    Ativo(
                        id = d.id,
                        nome = d.getString("nome") ?: "",
                        tipo = d.getString("tipo") ?: "Equipamento",
                        unidadeMedida = d.getString("unidadeMedida") ?: "Horas",
                        usoAtual = d.getDouble("usoAtual") ?: 0.0,
                        mtbfPreventiva = d.getDouble("mtbfPreventiva") ?: 0.0,
                        status = d.getString("status") ?: "Operando",
                        custoTotal = d.getDouble("custoTotal") ?: 0.0,
                        placa = d.getString("placa") ?: ""
                    )
                }.getOrNull()
            }
        }
    }

    fun criar(dados: Map<String, Any>, aoTerminar: (Boolean) -> Unit) {
        ativosCol().add(dados).addOnSuccessListener { aoTerminar(true) }.addOnFailureListener { aoTerminar(false) }
    }

    fun editar(id: String, dados: Map<String, Any>, aoTerminar: (Boolean) -> Unit) {
        ativosCol().document(id).update(dados).addOnSuccessListener { aoTerminar(true) }.addOnFailureListener { aoTerminar(false) }
    }

    fun atualizarUso(ativo: Ativo, novoUso: Double) {
        ativosCol().document(ativo.id).update("usoAtual", novoUso)
    }

    fun excluir(ativo: Ativo) {
        ativosCol().document(ativo.id).delete()
    }

    // Soma o gasto ao custo do ativo e lança como despesa paga no mês, num lote só
    fun registrarGasto(ativo: Ativo, descricao: String, valor: Double, zeraCiclo: Boolean) {
        val cal = Calendar.getInstance()
        val lote = banco.batch()
        val mudancas = mutableMapOf<String, Any>("custoTotal" to FieldValue.increment(valor))
        if (zeraCiclo) mudancas["usoAtual"] = 0.0
        lote.update(ativosCol().document(ativo.id), mudancas)
        lote.set(usuarioDoc().collection("despesas").document(), hashMapOf(
            "descricao" to "${ativo.nome}: $descricao",
            "valor" to valor,
            "diaVencimento" to cal.get(Calendar.DAY_OF_MONTH),
            "tipo" to if (descricao.contains("IPVA", true) || descricao.contains("Seguro", true)) "Fixa" else "Variável",
            "categoria" to if (ativo.tipo == "Veículo") "Transporte" else "Outros",
            "status" to "Pago",
            "mesAno" to SimpleDateFormat("MM/yyyy", Locale("pt", "BR")).format(cal.time),
            "observacao" to "Lançado via Gestão de Manutenção",
            "frequencia" to "Única"
        ))
        lote.commit()
    }

    override fun onCleared() {
        ouvinte?.remove()
    }
}
