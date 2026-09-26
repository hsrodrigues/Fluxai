package fluxai.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

// Dados da Análise BI: lançamentos e renda do mês escolhido, limites, projetos e histórico de 6 meses
class AnaliseViewModel : ViewModel() {
    private val banco = Firebase.firestore
    private var workspaceUid = ""
    private var mesAno = ""
    private val ouvintesFixos = mutableListOf<ListenerRegistration>()   // limites e projetos
    private val ouvintesDoMes = mutableListOf<ListenerRegistration>()   // despesas e saldo do mês

    private val _despesas = MutableStateFlow<List<Despesa>>(emptyList())
    val despesas: StateFlow<List<Despesa>> = _despesas.asStateFlow()
    private val _carregando = MutableStateFlow(true)
    val carregando: StateFlow<Boolean> = _carregando.asStateFlow()
    private val _renda = MutableStateFlow(0.0)
    val renda: StateFlow<Double> = _renda.asStateFlow()
    private val _limites = MutableStateFlow<Map<String, Double>>(emptyMap())
    val limites: StateFlow<Map<String, Double>> = _limites.asStateFlow()
    private val _projetos = MutableStateFlow<List<Projeto>>(emptyList())
    val projetos: StateFlow<List<Projeto>> = _projetos.asStateFlow()
    private val _historico = MutableStateFlow<List<MesHistorico>>(emptyList())
    val historico: StateFlow<List<MesHistorico>> = _historico.asStateFlow()

    private fun usuarioDoc() = banco.collection("usuarios").document(workspaceUid)

    // Chamado sempre que a conta ou o mês mudam; só refaz o que for necessário
    fun observar(workspace: String, mes: String, calendario: Calendar) {
        if (workspace.isBlank()) return
        if (workspace != workspaceUid) {
            workspaceUid = workspace
            mesAno = ""
            ouvintesFixos.forEach { it.remove() }; ouvintesFixos.clear()
            ouvintesFixos += usuarioDoc().collection("configuracoes").document("limites").addSnapshotListener { snap, _ ->
                if (snap != null && snap.exists()) _limites.value = (snap.data ?: emptyMap()).mapValues { (it.value as? Number)?.toDouble() ?: 0.0 }
            }
            ouvintesFixos += usuarioDoc().collection("projetos").addSnapshotListener { snap, _ ->
                if (snap != null) _projetos.value = snap.documents.mapNotNull { d -> runCatching { Projeto(d.id, d.getString("nome") ?: "", d.getDouble("orcamento") ?: 0.0) }.getOrNull() }
            }
        }
        if (mes == mesAno) return
        mesAno = mes
        _carregando.value = true
        ouvintesDoMes.forEach { it.remove() }; ouvintesDoMes.clear()
        ouvintesDoMes += usuarioDoc().collection("despesas").whereEqualTo("mesAno", mes).addSnapshotListener { snap, erro ->
            if (erro == null) _despesas.value = snap?.documents?.mapNotNull { runCatching { lerDespesa(it) }.getOrNull() } ?: emptyList()
            _carregando.value = false
        }
        ouvintesDoMes += usuarioDoc().collection("saldos").document(mes.replace("/", "-")).addSnapshotListener { doc, _ ->
            _renda.value = if (doc != null && doc.exists()) {
                (doc.getDouble("adiantamento") ?: 0.0) + (doc.getDouble("pagamento") ?: doc.getDouble("valor") ?: 0.0) + (doc.getDouble("extra") ?: 0.0)
            } else 0.0
        }
        val fimDoHistorico = calendario.clone() as Calendar
        viewModelScope.launch { _historico.value = carregarHistorico(workspaceUid, fimDoHistorico) }
    }

    fun salvarLimite(categoria: String, valor: Double, aoTerminar: () -> Unit) {
        usuarioDoc().collection("configuracoes").document("limites")
            .set(mapOf(categoria to valor), SetOptions.merge())
            .addOnSuccessListener { aoTerminar() }
    }

    override fun onCleared() {
        ouvintesFixos.forEach { it.remove() }
        ouvintesDoMes.forEach { it.remove() }
    }
}
