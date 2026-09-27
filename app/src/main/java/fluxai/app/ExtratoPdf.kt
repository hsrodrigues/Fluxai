package fluxai.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Calendar

// =========================================================================
// FATURA OU EXTRATO EM PDF
// Bancos brasileiros mandam a fatura em PDF. O Android não extrai texto de PDF,
// então cada página vira imagem, o ML Kit lê o texto no aparelho e a IA separa
// os lançamentos numa lista simples, que passa pela mesma revisão do CSV.
// =========================================================================
private const val PAGINAS_MAXIMO = 12
private const val TEXTO_MAXIMO = 17000 // a Cloud Function aceita até 20 mil caracteres por chamada

class PdfProtegido : Exception("PDF protegido por senha")

// Renderiza as páginas em boa resolução e junta o texto reconhecido
suspend fun textoDoPdf(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val descritor = context.contentResolver.openFileDescriptor(uri, "r") ?: error("Não foi possível abrir o PDF")
    val renderizador = try { PdfRenderer(descritor) } catch (e: SecurityException) { descritor.close(); throw PdfProtegido() }
    val leitor = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    val texto = StringBuilder()
    renderizador.use { pdf ->
        for (i in 0 until minOf(pdf.pageCount, PAGINAS_MAXIMO)) {
            val pagina = pdf.openPage(i)
            // ~2x a resolução da página (72 dpi): o suficiente para o OCR ler valores pequenos
            val escala = 2.2f
            val bitmap = Bitmap.createBitmap((pagina.width * escala).toInt(), (pagina.height * escala).toInt(), Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            pagina.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            pagina.close()
            texto.appendLine(leitor.process(InputImage.fromBitmap(bitmap, 0)).await().text)
            bitmap.recycle()
            if (texto.length > TEXTO_MAXIMO) break
        }
    }
    descritor.close()
    texto.toString().take(TEXTO_MAXIMO)
}

private val SistemaFatura = """
    Você recebe o texto (OCR) de uma fatura de cartão de crédito ou extrato bancário brasileiro.
    Liste TODOS os lançamentos, um por linha, exatamente no formato:
    DD/MM|DESCRIÇÃO|VALOR|TIPO
    - VALOR: número com ponto decimal, sem R$ e sem sinal (ex.: 1234.56).
    - TIPO: S para saída (compra, parcela, tarifa, anuidade, juros, IOF, pagamento feito) ou E para entrada (pagamento da fatura recebido, estorno, crédito, depósito).
    - Mantenha a parcela na descrição quando houver (ex.: "LOJA X 03/10").
    - Ignore totais, limites, resumo da fatura, saldo, próximas faturas e textos informativos.
    Responda só com as linhas, sem cabeçalho, sem markdown e sem comentários.
""".trimIndent()

// Linhas "DD/MM|DESCRIÇÃO|VALOR|TIPO" da IA -> itens. Mês depois do atual = ano passado (fatura antiga).
fun lerLinhasFaturaIA(resposta: String, hoje: DataSimples): List<ItemExtrato> =
    resposta.lines().mapNotNull { linha ->
        val partes = linha.trim().trim('`').split("|").map { it.trim() }
        if (partes.size < 4) return@mapNotNull null
        val (dia, mes) = Regex("""^(\d{1,2})/(\d{1,2})""").find(partes[0])?.destructured?.let { (d, m) -> d.toInt() to m.toInt() } ?: return@mapNotNull null
        if (dia !in 1..31 || mes !in 1..12) return@mapNotNull null
        val valor = partes[2].replace("R$", "").trim().let { v -> if (',' in v) v.paraValor() else v.toDoubleOrNull() }?.let { kotlin.math.abs(it) } ?: return@mapNotNull null
        if (valor == 0.0 || partes[1].isBlank()) return@mapNotNull null
        val ano = if (mes > hoje.mes) hoje.ano - 1 else hoje.ano
        ItemExtrato(DataSimples(dia, mes, ano), formatarNomeProprio(partes[1]), valor, entrada = partes[3].uppercase().startsWith("E"))
    }

suspend fun lerPdfExtrato(context: Context, uri: Uri): List<ItemExtrato> {
    val texto = textoDoPdf(context, uri)
    if (texto.isBlank()) return emptyList()
    val resposta = chamarIA("extrato", SistemaFatura, texto)
    val hoje = Calendar.getInstance()
    return lerLinhasFaturaIA(resposta, DataSimples(hoje.get(Calendar.DAY_OF_MONTH), hoje.get(Calendar.MONTH) + 1, hoje.get(Calendar.YEAR)))
}

private val SistemaSaldo = """
    Você recebe o texto (OCR) de um extrato de conta bancária brasileira.
    Responda APENAS o saldo final mais recente da conta, no formato exato: DD/MM/AAAA|VALOR
    VALOR com ponto decimal, sem R$, com sinal de menos se o saldo for negativo (ex.: 1520.35 ou -80.00).
    Se não houver saldo no texto, responda só: SEM_SALDO
""".trimIndent()

suspend fun lerSaldoPdf(context: Context, uri: Uri): SaldoExtrato? {
    val texto = textoDoPdf(context, uri)
    if (texto.isBlank()) return null
    return lerRespostaSaldoIA(chamarIA("ocr", SistemaSaldo, texto))
}
