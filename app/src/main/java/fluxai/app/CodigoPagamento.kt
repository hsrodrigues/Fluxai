package fluxai.app

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs

// =========================================================================
// LEITURA DE BOLETO E PIX
// Entende o código de barras do boleto (44 dígitos), a linha digitável (47 ou 48 dígitos)
// e o Pix Copia e Cola (BR Code). Tudo local, sem internet e sem IA.
// =========================================================================
data class DataSimples(val dia: Int, val mes: Int, val ano: Int) {
    val mesAno: String get() = "%02d/%04d".format(mes, ano)
}

data class DadosPagamento(
    val tipo: String,            // "Boleto", "Conta de consumo" ou "Pix"
    val valor: Double?,          // null quando o código não traz valor
    val vencimento: DataSimples?,
    val beneficiario: String?,
    val codigo: String
)

private fun hojeSimples(): DataSimples {
    val c = Calendar.getInstance()
    return DataSimples(c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR))
}

// Tenta todos os formatos; null se o texto não for um código de pagamento reconhecido
fun interpretarCodigoPagamento(texto: String, hoje: DataSimples = hojeSimples()): DadosPagamento? {
    val bruto = texto.trim()
    if (bruto.startsWith("000201")) return lerPix(bruto)
    val digitos = bruto.filter { it.isDigit() }
    // Só números, pontos e espaços: evita tratar textos quaisquer com muitos números como boleto
    if (bruto.any { !it.isDigit() && it !in " .-\n" }) return null
    return when (digitos.length) {
        44 -> lerCodigoBarras(digitos, hoje)
        47 -> lerCodigoBarras(linhaBancariaParaBarras(digitos), hoje)
        48 -> if (digitos.startsWith("8")) lerCodigoBarras(linhaConvenioParaBarras(digitos), hoje) else null
        else -> null
    }
}

// Linha digitável do boleto bancário (47 dígitos) -> código de barras (44)
fun linhaBancariaParaBarras(l: String): String =
    l.substring(0, 4) + l[32] + l.substring(33, 47) + l.substring(4, 9) + l.substring(10, 20) + l.substring(21, 31)

// Contas de consumo/tributos (48 dígitos): 4 blocos de 11 dígitos + dígito verificador
fun linhaConvenioParaBarras(l: String): String = (0 until 4).joinToString("") { l.substring(it * 12, it * 12 + 11) }

private fun lerCodigoBarras(b: String, hoje: DataSimples): DadosPagamento? {
    if (b.length != 44) return null
    if (b[0] == '8') {
        // Arrecadação: o 3º dígito diz se o campo de valor é em reais (6 ou 7)
        val valor = if (b[2] == '6' || b[2] == '7') b.substring(4, 15).toLong() / 100.0 else null
        return DadosPagamento("Conta de consumo", valor?.takeIf { it > 0 }, null, null, b)
    }
    val fator = b.substring(5, 9).toInt()
    val valor = b.substring(9, 19).toLong() / 100.0
    return DadosPagamento("Boleto", valor.takeIf { it > 0 }, vencimentoPorFator(fator, hoje), null, b)
}

// O fator de vencimento conta dias desde 07/10/1997 e voltou a 1000 em 22/02/2025.
// Usa a data candidata mais próxima de hoje.
fun vencimentoPorFator(fator: Int, hoje: DataSimples = hojeSimples()): DataSimples? {
    if (fator <= 0) return null
    fun somarDias(dia: Int, mes: Int, ano: Int, dias: Int): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(ano, mes - 1, dia); add(Calendar.DAY_OF_YEAR, dias) }
    val referencia = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(hoje.ano, hoje.mes - 1, hoje.dia) }
    val candidatas = listOfNotNull(
        somarDias(7, 10, 1997, fator),
        if (fator >= 1000) somarDias(22, 2, 2025, fator - 1000) else null
    )
    val melhor = candidatas.minBy { abs(it.timeInMillis - referencia.timeInMillis) }
    return DataSimples(melhor.get(Calendar.DAY_OF_MONTH), melhor.get(Calendar.MONTH) + 1, melhor.get(Calendar.YEAR))
}

// BR Code do Pix: campos no formato ID(2) + TAMANHO(2) + VALOR
fun lerCamposEmv(texto: String): Map<String, String> {
    val campos = mutableMapOf<String, String>()
    var i = 0
    while (i + 4 <= texto.length) {
        val id = texto.substring(i, i + 2)
        val tamanho = texto.substring(i + 2, i + 4).toIntOrNull() ?: break
        if (i + 4 + tamanho > texto.length) break
        campos[id] = texto.substring(i + 4, i + 4 + tamanho)
        i += 4 + tamanho
    }
    return campos
}

private fun lerPix(texto: String): DadosPagamento? {
    val campos = lerCamposEmv(texto)
    val conta = campos["26"]?.let { lerCamposEmv(it) } ?: return null
    if (conta["00"]?.lowercase() != "br.gov.bcb.pix") return null
    val nome = campos["59"]?.trim()?.takeIf { it.isNotBlank() }?.let { formatarNomeProprio(it) }
    return DadosPagamento("Pix", campos["54"]?.toDoubleOrNull()?.takeIf { it > 0 }, null, nome, texto)
}

// "IFOOD *RESTAURANTE" -> "Ifood *Restaurante"; nomes já com minúsculas ficam como estão
fun formatarNomeProprio(nome: String): String {
    val limpo = nome.trim().replace(Regex("\\s+"), " ")
    if (limpo.any { it.isLowerCase() }) return limpo
    return limpo.lowercase().split(" ").joinToString(" ") { p -> p.replaceFirstChar { it.titlecase() }.let { if (it.startsWith("*")) "*" + it.drop(1).replaceFirstChar { c -> c.titlecase() } else it } }
}
