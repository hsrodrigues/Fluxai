package fluxai.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// Formato padrão dos campos de texto (mesmo da tela de Novo Lançamento)
val FormatoCampo = RoundedCornerShape(12.dp)

// Converte o valor digitado pelo usuário para Double, aceitando os formatos comuns no Brasil:
// "1.500,00", "1500,00", "1500", "R$ 1.500" e também "1500.50" (ponto como decimal).
// Ponto seguido de exatamente 3 dígitos é tratado como separador de milhar ("1.500" = 1500).
fun String.paraValor(): Double? {
    val limpo = replace("R$", "").replace(" ", "").replace(" ", "").trim()
    if (limpo.isEmpty()) return null
    val normalizado = when {
        ',' in limpo -> limpo.replace(".", "").replace(",", ".")
        limpo.count { it == '.' } > 1 -> limpo.replace(".", "")
        Regex("""^-?\d{1,3}\.\d{3}$""").matches(limpo) -> limpo.replace(".", "")
        else -> limpo
    }
    return normalizado.toDoubleOrNull()
}

// Valor para preencher um campo de texto no padrão brasileiro: 1234.5 -> "1234,50"
fun Double.paraCampo(): String = String.format(java.util.Locale("pt", "BR"), "%.2f", this)

// Conta cujos dados estão em uso: a própria ou a do dono, quando o usuário é membro de uma conta conjunta
fun workspaceAtual(context: Context, uidProprio: String): String =
    context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).getString("workspace_uid", uidProprio)?.ifBlank { null } ?: uidProprio

// Cartões antigos guardavam o número completo: deixa só os 4 últimos dígitos no banco
fun limparNumeroCartao(doc: com.google.firebase.firestore.DocumentSnapshot) {
    val numero = doc.getString("numero") ?: return
    val digitos = numero.filter { it.isDigit() }
    if (digitos.length > 4) doc.reference.update("numero", digitos.takeLast(4))
}

// CLASSES DE DADOS COMPARTILHADAS
data class Despesa(
    val id: String = "",
    val descricao: String = "",
    val valor: Double = 0.0,
    val tipo: String = "",
    val categoria: String = "Outros",
    val status: String = "A pagar",
    val observacao: String = "",
    val diaVencimento: Int = 0,
    val frequencia: String = "Mensal",
    val mesAno: String = "",
    val cartaoId: String? = null,
    val projetoId: String? = null,
    val contaId: String? = null,       // conta bancária que paga (ou pagou) o lançamento
    val pagoPor: String? = null,       // uid de quem marcou como pago (conta conjunta)
    val pagoPorNome: String? = null,
    val comprovante: String? = null    // caminho da foto do comprovante no Firebase Storage
)

data class Cartao(
    val id: String = "",
    val nome: String = "",
    val bandeira: String = "Mastercard",
    val limite: Double = 0.0,
    val faturaAtual: Double = 0.0,
    val diaFechamento: Int = 1,
    val diaVencimento: Int = 1
)

data class Caixinha(
    val id: String = "",
    val nome: String = "",
    val meta: Double = 0.0,
    val saldo: Double = 0.0,
    val icone: String = "fa-shield-halved",
    val prazo: String = ""  // "MM/yyyy"; vazio = meta sem data
)

data class CategoriaCustom(
    val id: String = "",
    val nome: String = "",
    val corHex: String = "#7E57C2",
    val icone: String = "estrela"
)

data class Projeto(
    val id: String = "",
    val nome: String = "",
    val orcamento: Double = 0.0
)

// FUNÇÃO DE AUDITORIA INVISÍVEL
fun registrarAuditoria(acao: String) {
    val usuario = Firebase.auth.currentUser ?: return
    val banco = Firebase.firestore

    val registro = hashMapOf(
        "acao" to acao,
        "dataHora" to Date(),
        "dispositivo" to Build.MODEL // Salva o modelo do celular (ex: SM-G998B)
    )

    banco.collection("usuarios").document(usuario.uid).collection("auditoria").add(registro)
}

// FUNÇÃO DE EXPORTAR CSV
fun exportarDespesasParaCSV(context: Context, despesas: List<Despesa>, mesAno: String) {
    if (despesas.isEmpty()) {
        Toast.makeText(context, "Não há dados para exportar!", Toast.LENGTH_SHORT).show()
        return
    }

    val nomeArquivo = "FluxAi_${mesAno.replace("/", "_")}.csv"
    val cabecalho = "Descricao;Valor;Categoria;Tipo;Status;Dia Vencimento;Observacao\n"

    val corpo = StringBuilder()
    corpo.append(cabecalho)
    despesas.forEach { d ->
        corpo.append("${d.descricao};${d.valor};${d.categoria};${d.tipo};${d.status};${d.diaVencimento};${d.observacao}\n")
    }

    try {
        val cachePath = File(context.cacheDir, "reports")
        cachePath.mkdirs()
        val stream = FileOutputStream("$cachePath/$nomeArquivo")
        stream.write(corpo.toString().toByteArray())
        stream.close()

        val newFile = File(cachePath, nomeArquivo)
        val contentUri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", newFile)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Relatório FluxAí - $mesAno")
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Enviar Relatório"))

        // REGISTRA A AUDITORIA DE EXPORTAÇÃO
        registrarAuditoria("Exportou relatório CSV do mês $mesAno")

    } catch (e: Exception) {
        Toast.makeText(context, "Erro ao gerar arquivo: ${e.message}", Toast.LENGTH_LONG).show()
    }
}