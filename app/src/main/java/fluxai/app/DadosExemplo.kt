package fluxai.app

import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import java.util.Calendar

// =========================================================================
// DADOS DE EXEMPLO (modo demonstração)
// Tudo que é criado aqui leva "exemplo": true, para poder ser removido depois
// sem tocar nos dados reais do usuário.
// =========================================================================
private val ColecoesExemplo = listOf("despesas", "cartoes", "caixinhas", "contas", "saldos")

suspend fun carregarDadosExemplo(workspaceUid: String) {
    val db = Firebase.firestore
    val usuario = db.collection("usuarios").document(workspaceUid)
    val hoje = Calendar.getInstance()
    val mesAno = "%02d/%04d".format(hoje.get(Calendar.MONTH) + 1, hoje.get(Calendar.YEAR))
    val lote = db.batch()

    // Renda do mês só entra se o usuário ainda não cadastrou a dele
    val saldoRef = usuario.collection("saldos").document(mesAno.replace("/", "-"))
    if (!saldoRef.get().await().exists()) {
        lote.set(saldoRef, mapOf("adiantamento" to 2000.0, "pagamento" to 3500.0, "extra" to 0.0, "valor" to 5500.0, "exemplo" to true))
    }

    val cartao = usuario.collection("cartoes").document()
    lote.set(cartao, mapOf("nome" to "Cartão Exemplo", "bandeira" to "Mastercard", "numero" to "1234", "limite" to 3000.0, "faturaAtual" to 389.7, "diaFechamento" to 3, "diaVencimento" to 10, "exemplo" to true))
    val conta = usuario.collection("contas").document()
    lote.set(conta, mapOf("nome" to "Conta Exemplo", "tipo" to "Corrente", "saldo" to 1850.0, "exemplo" to true))
    lote.set(usuario.collection("caixinhas").document(), mapOf("nome" to "Reserva de emergência", "meta" to 10000.0, "saldo" to 2500.0, "icone" to "savings", "prazo" to "12/%04d".format(hoje.get(Calendar.YEAR) + 1), "exemplo" to true))

    // (descrição, valor, dia, categoria, tipo, frequência, pago, no cartão)
    val lancamentos = listOf(
        listOf("Aluguel", 1400.0, 5, "Moradia", "Fixa", "Mensal", true, false),
        listOf("Conta de energia", 180.0, 10, "Moradia", "Fixa", "Quinzenal", true, false),
        listOf("Internet", 99.9, 12, "Moradia", "Fixa", "Quinzenal", false, false),
        listOf("Supermercado", 620.0, 8, "Alimentação", "Variável", "Mensal", true, false),
        listOf("iFood", 89.7, 14, "Alimentação", "Variável", "Mensal", false, true),
        listOf("Uber", 45.0, 15, "Transporte", "Variável", "Mensal", false, true),
        listOf("Netflix", 55.9, 20, "Lazer", "Fixa", "Mensal", false, true),
        listOf("Farmácia", 72.4, 18, "Saúde", "Variável", "Mensal", false, false),
        listOf("Academia", 119.9, 25, "Saúde", "Fixa", "Mensal", false, true)
    )
    lancamentos.forEachIndexed { i, l ->
        val pago = l[6] as Boolean
        val noCartao = l[7] as Boolean
        lote.set(usuario.collection("despesas").document(), hashMapOf(
            "descricao" to (if (noCartao) "[Cartão] " else "") + l[0], "valor" to l[1], "diaVencimento" to l[2], "categoria" to l[3],
            "tipo" to l[4], "frequencia" to l[5], "status" to if (pago) "Pago" else "A pagar", "mesAno" to mesAno, "observacao" to "Exemplo",
            "cartaoId" to if (noCartao) cartao.id else null, "contaId" to if (noCartao) null else conta.id, "projetoId" to null,
            "ordem" to i, "exemplo" to true
        ))
    }
    lote.set(usuario, mapOf("temDadosExemplo" to true), com.google.firebase.firestore.SetOptions.merge())
    lote.commit().await()
}

suspend fun removerDadosExemplo(workspaceUid: String) {
    val db = Firebase.firestore
    val usuario = db.collection("usuarios").document(workspaceUid)
    ColecoesExemplo.forEach { col ->
        val docs = usuario.collection(col).whereEqualTo("exemplo", true).get().await().documents
        docs.chunked(400).forEach { parte ->
            val lote = db.batch()
            parte.forEach { lote.delete(it.reference) }
            lote.commit().await()
        }
    }
    usuario.update("temDadosExemplo", FieldValue.delete()).await()
}

suspend fun temDadosExemplo(workspaceUid: String): Boolean =
    runCatching { Firebase.firestore.collection("usuarios").document(workspaceUid).get().await().getBoolean("temDadosExemplo") == true }.getOrDefault(false)
