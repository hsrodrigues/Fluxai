package fluxai.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

// A ÚNICA DECLARAÇÃO DA CLASSE DESPESA NO APP INTEIRO
data class Despesa(
    val id: String = "",
    val descricao: String = "",
    val valor: Double = 0.0,
    val tipo: String = "",
    val categoria: String = "Outros",
    val status: String = "A pagar",
    val observacao: String = "",
    val diaVencimento: Int = 0
)

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
    } catch (e: Exception) {
        Toast.makeText(context, "Erro ao gerar arquivo: ${e.message}", Toast.LENGTH_LONG).show()
    }
}