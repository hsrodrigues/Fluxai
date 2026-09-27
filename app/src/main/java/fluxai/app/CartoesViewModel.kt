package fluxai.app

import androidx.lifecycle.ViewModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Final do cartão (4 dígitos) e validade, usados só no formulário de edição
data class ExtrasCartao(val final4: String, val validade: String)

// Dados e gravações da tela de Cartões
class CartoesViewModel : ViewModel() {
    private val banco = Firebase.firestore
    private var ouvinte: ListenerRegistration? = null
    private var ouvinteDespesas: ListenerRegistration? = null
    private var workspaceUid = ""
    private var cartoesBrutos: List<Cartao> = emptyList()
    private var emAberto: Map<String, Double>? = null // null = ainda carregando os lançamentos

    private val _cartoes = MutableStateFlow<List<Cartao>>(emptyList())
    val cartoes: StateFlow<List<Cartao>> = _cartoes.asStateFlow()
    private var extras: Map<String, ExtrasCartao> = emptyMap()

    private fun colecao() = banco.collection("usuarios").document(workspaceUid).collection("cartoes")

    // A fatura mostrada é a soma das compras do cartão ainda não pagas (em qualquer mês).
    // O campo "faturaAtual" guardado é corrigido quando estiver diferente: exclusões, importações
    // e mudanças de status feitas por qualquer caminho deixam de desalinhar o valor do cartão.
    private fun publicar() {
        val somas = emAberto
        _cartoes.value = if (somas == null) cartoesBrutos else cartoesBrutos.map { c ->
            val real = Math.round((somas[c.id] ?: 0.0) * 100) / 100.0
            if (kotlin.math.abs(real - c.faturaAtual) > 0.005) colecao().document(c.id).update("faturaAtual", real)
            c.copy(faturaAtual = real)
        }
    }

    fun observar(workspace: String) {
        if (workspace == workspaceUid || workspace.isBlank()) return
        workspaceUid = workspace
        ouvinte?.remove()
        ouvinteDespesas?.remove()
        emAberto = null
        ouvinteDespesas = banco.collection("usuarios").document(workspaceUid).collection("despesas")
            .whereNotEqualTo("status", "Pago")
            .addSnapshotListener { snap, erro ->
                // Sem conexão ou sem resposta do servidor, mantém o valor guardado (não "zera" a fatura)
                if (erro != null || snap == null || snap.metadata.isFromCache) return@addSnapshotListener
                emAberto = faturaEmAberto(snap.documents.map { lerDespesa(it) })
                publicar()
            }
        ouvinte = colecao().addSnapshotListener { snap, _ ->
            val docs = snap?.documents ?: return@addSnapshotListener
            docs.forEach { limparNumeroCartao(it) }
            extras = docs.associate { d -> d.id to ExtrasCartao((d.getString("numero") ?: "").filter { it.isDigit() }.takeLast(4), d.getString("validade") ?: "") }
            cartoesBrutos = docs.mapNotNull { d ->
                runCatching {
                    Cartao(d.id, d.getString("nome") ?: "", d.getString("bandeira") ?: "Mastercard", d.getDouble("limite") ?: 0.0,
                        d.getDouble("faturaAtual") ?: 0.0, d.getLong("diaFechamento")?.toInt() ?: 1, d.getLong("diaVencimento")?.toInt() ?: 1)
                }.getOrNull()
            }
            publicar()
        }
    }

    fun extrasDe(id: String) = extras[id] ?: ExtrasCartao("", "")

    // id nulo = novo cartão (fatura começa zerada); senão atualiza os dados do cartão existente
    fun salvar(id: String?, nome: String, bandeira: String, final4: String, validade: String, limite: Double, diaFechamento: Int, diaVencimento: Int, aoTerminar: (Boolean) -> Unit) {
        val dados = mutableMapOf<String, Any>(
            "nome" to nome.trim(),
            "bandeira" to bandeira,
            "numero" to final4.filter { it.isDigit() }.takeLast(4), // só o final: número completo de cartão não fica no banco
            "validade" to validade,
            "limite" to limite,
            "diaFechamento" to diaFechamento.coerceIn(1, 31),
            "diaVencimento" to diaVencimento.coerceIn(1, 31)
        )
        val tarefa = if (id == null) colecao().add(dados + ("faturaAtual" to 0.0)) else colecao().document(id).update(dados)
        tarefa.addOnSuccessListener { aoTerminar(true) }.addOnFailureListener { aoTerminar(false) }
    }

    fun excluir(c: Cartao) {
        colecao().document(c.id).delete()
    }

    override fun onCleared() {
        ouvinte?.remove()
        ouvinteDespesas?.remove()
    }
}
