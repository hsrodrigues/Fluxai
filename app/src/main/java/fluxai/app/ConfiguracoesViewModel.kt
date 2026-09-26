package fluxai.app

import androidx.lifecycle.ViewModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Categorias personalizadas e projetos (tela de Configurações)
class ConfiguracoesViewModel : ViewModel() {
    private val banco = Firebase.firestore
    private val ouvintes = mutableListOf<ListenerRegistration>()
    private var workspaceUid = ""

    private val _categorias = MutableStateFlow<List<CategoriaCustom>>(emptyList())
    val categorias: StateFlow<List<CategoriaCustom>> = _categorias.asStateFlow()
    private val _projetos = MutableStateFlow<List<Projeto>>(emptyList())
    val projetos: StateFlow<List<Projeto>> = _projetos.asStateFlow()

    private fun categoriasCol() = banco.collection("usuarios").document(workspaceUid).collection("categorias_custom")
    private fun projetosCol() = banco.collection("usuarios").document(workspaceUid).collection("projetos")

    fun observar(workspace: String) {
        if (workspace == workspaceUid || workspace.isBlank()) return
        workspaceUid = workspace
        ouvintes.forEach { it.remove() }
        ouvintes.clear()
        ouvintes += categoriasCol().addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            _categorias.value = snap.documents.mapNotNull { d ->
                runCatching { CategoriaCustom(d.id, d.getString("nome") ?: "", icone = d.getString("icone") ?: "estrela") }.getOrNull()
            }
            atualizarIconesCustom(snap)
        }
        ouvintes += projetosCol().addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            _projetos.value = snap.documents.mapNotNull { d ->
                runCatching { Projeto(d.id, d.getString("nome") ?: "", d.getDouble("orcamento") ?: 0.0) }.getOrNull()
            }
        }
    }

    fun criarCategoria(nome: String, icone: String) { categoriasCol().add(mapOf("nome" to nome.trim(), "icone" to icone)) }
    fun trocarIconeCategoria(id: String, icone: String) { categoriasCol().document(id).update("icone", icone) }
    fun excluirCategoria(id: String) { categoriasCol().document(id).delete() }

    fun criarProjeto(nome: String, orcamento: Double) { projetosCol().add(mapOf("nome" to nome.trim(), "orcamento" to orcamento)) }
    fun excluirProjeto(id: String) { projetosCol().document(id).delete() }

    override fun onCleared() {
        ouvintes.forEach { it.remove() }
    }
}
