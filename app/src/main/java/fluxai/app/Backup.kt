package fluxai.app

import android.content.Context
import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

// =========================================================================
// BACKUP COMPLETO EM JSON
// Exporta todas as coleções da conta num arquivo que o usuário guarda onde quiser
// (Drive, Arquivos...). A restauração regrava os documentos com os mesmos IDs,
// então importar o mesmo backup duas vezes não duplica nada.
// =========================================================================
val ColecoesBackup = listOf(
    "despesas", "saldos", "cartoes", "contas", "caixinhas", "categorias_custom",
    "projetos", "configuracoes", "ativos", "contas_celular"
)

private const val MARCA_TIMESTAMP = "__timestamp"

// Firestore -> JSON (datas viram {"__timestamp": milissegundos})
fun valorParaJson(v: Any?): Any? = when (v) {
    null -> JSONObject.NULL
    is Timestamp -> JSONObject().put(MARCA_TIMESTAMP, v.toDate().time)
    is Date -> JSONObject().put(MARCA_TIMESTAMP, v.time)
    is Map<*, *> -> JSONObject().also { o -> v.forEach { (k, x) -> o.put(k.toString(), valorParaJson(x)) } }
    is List<*> -> JSONArray().also { a -> v.forEach { a.put(valorParaJson(it)) } }
    is Number, is Boolean, is String -> v
    else -> v.toString()
}

// JSON -> Firestore
fun jsonParaValor(v: Any?): Any? = when (v) {
    null, JSONObject.NULL -> null
    is JSONObject -> if (v.length() == 1 && v.has(MARCA_TIMESTAMP)) Timestamp(Date(v.getLong(MARCA_TIMESTAMP)))
        else v.keys().asSequence().associateWith { jsonParaValor(v.get(it)) }
    is JSONArray -> (0 until v.length()).map { jsonParaValor(v.get(it)) }
    is Int -> v.toLong()
    else -> v
}

suspend fun gerarBackup(workspaceUid: String): String {
    val usuario = Firebase.firestore.collection("usuarios").document(workspaceUid)
    val raiz = JSONObject()
        .put("app", "FluxAí")
        .put("versao", 1)
        .put("geradoEm", System.currentTimeMillis())
        .put("perfil", valorParaJson(usuario.get().await().data ?: emptyMap<String, Any>()))
    val colecoes = JSONObject()
    ColecoesBackup.forEach { nome ->
        val docs = JSONObject()
        usuario.collection(nome).get().await().documents.forEach { d -> docs.put(d.id, valorParaJson(d.data ?: emptyMap<String, Any>())) }
        colecoes.put(nome, docs)
    }
    raiz.put("colecoes", colecoes)
    return raiz.toString()
}

suspend fun salvarBackupEm(context: Context, uri: Uri, workspaceUid: String): Int {
    val json = gerarBackup(workspaceUid)
    withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } ?: error("Não foi possível gravar o arquivo") }
    return JSONObject(json).getJSONObject("colecoes").let { c -> c.keys().asSequence().sumOf { c.getJSONObject(it).length() } }
}

data class ResumoBackup(val geradoEm: Long, val porColecao: Map<String, Int>, val json: JSONObject) {
    val total get() = porColecao.values.sum()
}

suspend fun lerBackup(context: Context, uri: Uri): ResumoBackup = withContext(Dispatchers.IO) {
    val texto = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("Arquivo vazio")
    val json = JSONObject(texto)
    require(json.optString("app") == "FluxAí") { "Este arquivo não é um backup do FluxAí." }
    val colecoes = json.getJSONObject("colecoes")
    ResumoBackup(json.optLong("geradoEm"), colecoes.keys().asSequence().filter { it in ColecoesBackup }.associateWith { colecoes.getJSONObject(it).length() }, json)
}

suspend fun restaurarBackup(workspaceUid: String, backup: ResumoBackup) {
    val db = Firebase.firestore
    val usuario = db.collection("usuarios").document(workspaceUid)
    (jsonParaValor(backup.json.optJSONObject("perfil")) as? Map<*, *>)?.let { perfil ->
        @Suppress("UNCHECKED_CAST")
        if (perfil.isNotEmpty()) usuario.set(perfil as Map<String, Any?>, SetOptions.merge()).await()
    }
    val colecoes = backup.json.getJSONObject("colecoes")
    // Coleções fora da lista conhecida são ignoradas (arquivo editado ou de outra versão)
    val gravacoes = colecoes.keys().asSequence().filter { it in ColecoesBackup }.flatMap { nome ->
        val docs = colecoes.getJSONObject(nome)
        docs.keys().asSequence().map { id -> Triple(nome, id, jsonParaValor(docs.getJSONObject(id))) }
    }.toList()
    gravacoes.chunked(400).forEach { parte ->
        val lote = db.batch()
        parte.forEach { (nome, id, dados) ->
            @Suppress("UNCHECKED_CAST")
            lote.set(usuario.collection(nome).document(id), dados as Map<String, Any?>)
        }
        lote.commit().await()
    }
}
