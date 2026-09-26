package fluxai.app

import com.google.firebase.Firebase
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.functions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Chamada à IA pela Cloud Function "groqChat": a chave da Groq fica no servidor, fora do APK.
// tipo = "ocr" (leitura de nota/boleto) ou "consultor" (análise do Dashboard)
class FalhaIA(mensagem: String) : Exception(mensagem)

suspend fun chamarIA(tipo: String, sistema: String?, usuario: String): String =
    chamarIAConversa(tipo, buildList {
        if (sistema != null) add("system" to sistema)
        add("user" to usuario)
    })

// Conversa completa: lista de (papel, texto), com papel = "system", "user" ou "assistant"
suspend fun chamarIAConversa(tipo: String, conversa: List<Pair<String, String>>): String {
    val mensagens = conversa.map { (papel, texto) -> mapOf("role" to papel, "content" to texto) }
    val chamada = Firebase.functions("southamerica-east1").getHttpsCallable("groqChat")
    return suspendCancellableCoroutine { cont ->
        chamada.call(mapOf("tipo" to tipo, "mensagens" to mensagens))
            .addOnSuccessListener { resultado ->
                val conteudo = (resultado.getData() as? Map<*, *>)?.get("conteudo") as? String ?: ""
                cont.resume(conteudo)
            }
            .addOnFailureListener { e ->
                val msg = when ((e as? FirebaseFunctionsException)?.code) {
                    FirebaseFunctionsException.Code.UNAUTHENTICATED -> "Faça login novamente para usar a IA."
                    FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> e.message ?: "Limite diário da IA atingido."
                    FirebaseFunctionsException.Code.UNAVAILABLE, FirebaseFunctionsException.Code.INVALID_ARGUMENT -> e.message ?: "Falha na IA."
                    null -> "Sem internet ou falha de conexão."
                    else -> "Falha na IA: ${e.message}"
                }
                cont.resumeWithException(FalhaIA(msg))
            }
    }
}
