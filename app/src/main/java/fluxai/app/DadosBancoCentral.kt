package fluxai.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

// =========================================================================
// DADOS ABERTOS DO BANCO CENTRAL (dadosabertos.bcb.gov.br), sem chave:
// - Boletim Focus: o que o mercado espera de IPCA e Selic
// - Juros médios de pessoa física por modalidade (séries SGS, % ao ano)
// - Ranking semanal de juros por banco (API Olinda "taxaJuros")
// - Dólar PTAX, a cotação oficial do Banco Central
// Tudo com cache em memória; falha de rede devolve null e a tela simplesmente não mostra.
// =========================================================================

private fun olinda(url: String): JSONObject {
    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
    conn.connectTimeout = 10000; conn.readTimeout = 20000
    conn.setRequestProperty("Accept", "application/json")
    val texto = conn.inputStream.bufferedReader().readText()
    return JSONObject(texto)
}

private fun codificar(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

data class ExpectativaFocus(val indicador: String, val ano: Int, val mediana: Double, val dataPesquisa: String)

// Modalidade de crédito: nome no ranking por banco (Olinda) e série da taxa média (SGS, % ao ano)
data class ModalidadeCredito(val rotulo: String, val nomeRanking: String, val serieMedia: Int)

val ModalidadesCredito = listOf(
    ModalidadeCredito("Cartão rotativo", "Cartão de crédito - rotativo total - Prefixado", 22022),
    ModalidadeCredito("Cartão parcelado", "Cartão de crédito - parcelado - Prefixado", 22023),
    ModalidadeCredito("Cheque especial", "Cheque especial - Prefixado", 20741),
    ModalidadeCredito("Crédito pessoal", "Crédito pessoal não consignado - Prefixado", 20742),
    ModalidadeCredito("Consignado INSS", "Crédito pessoal consignado INSS - Prefixado", 20746),
    ModalidadeCredito("Consignado privado", "Crédito pessoal consignado privado - Prefixado", 20744),
    ModalidadeCredito("Consignado público", "Crédito pessoal consignado público - Prefixado", 20745),
    ModalidadeCredito("Veículos", "Aquisição de veículos - Prefixado", 20749)
)

fun modalidadeCredito(rotulo: String) = ModalidadesCredito.first { it.rotulo == rotulo }

data class TaxaBanco(val posicao: Int, val banco: String, val taxaMes: Double, val taxaAno: Double)
data class RankingJuros(val modalidade: ModalidadeCredito, val inicio: String, val fim: String, val bancos: List<TaxaBanco>)

data class Ptax(val venda: Double, val compra: Double, val dataHora: String)

// Juros ao mês equivalentes a uma taxa ao ano (e vice-versa)
fun anualParaMensal(aa: Double) = (Math.pow(1 + aa / 100, 1.0 / 12) - 1) * 100
fun mensalParaAnual(am: Double) = (Math.pow(1 + am / 100, 12.0) - 1) * 100

// Taxa mensal de um empréstimo (Price) a partir do valor recebido, da parcela e do número de parcelas.
// Busca por bisseção: acha i com valor = parcela × (1 - (1+i)^-n) / i. Null se a conta não fecha (parcelas menores que o recebido).
fun taxaMensalEmprestimo(recebido: Double, parcela: Double, parcelas: Int): Double? {
    if (recebido <= 0 || parcela <= 0 || parcelas <= 0 || parcela * parcelas <= recebido) return null
    fun valorPresente(i: Double) = parcela * (1 - Math.pow(1 + i, -parcelas.toDouble())) / i
    var baixo = 1e-7; var alto = 1.0
    if (valorPresente(alto) > recebido) return null
    repeat(200) {
        val meio = (baixo + alto) / 2
        if (valorPresente(meio) > recebido) baixo = meio else alto = meio
    }
    return (baixo + alto) / 2 * 100
}

object BancoCentral {
    private var focus: Pair<Long, List<ExpectativaFocus>>? = null
    private val medias = mutableMapOf<Int, Pair<Long, Pair<String, Double>>>()
    private val rankings = mutableMapOf<String, Pair<Long, RankingJuros>>()
    private var ptax: Pair<Long, Ptax>? = null
    private const val UMA_HORA = 60 * 60 * 1000L

    private fun valido(em: Long, validade: Long = 6 * UMA_HORA) = System.currentTimeMillis() - em < validade

    // Mediana mais recente do Focus para IPCA e Selic (fim de ano), ano atual e próximo
    suspend fun expectativas(): List<ExpectativaFocus>? {
        focus?.let { if (valido(it.first)) return it.second }
        return runCatching {
            withContext(Dispatchers.IO) {
                val filtro = codificar("(Indicador eq 'IPCA' or Indicador eq 'Selic') and baseCalculo eq 0")
                val json = olinda("https://olinda.bcb.gov.br/olinda/servico/Expectativas/versao/v1/odata/ExpectativasMercadoAnuais" +
                    "?\$top=40&\$filter=$filtro&\$orderby=Data%20desc&\$format=json&\$select=Indicador,Data,DataReferencia,Mediana")
                val valores = json.getJSONArray("value")
                val anoAtual = hojeIso().substring(0, 4).toInt()
                val lista = (0 until valores.length()).map { valores.getJSONObject(it) }
                    .map { ExpectativaFocus(it.getString("Indicador"), it.getString("DataReferencia").toInt(), it.getDouble("Mediana"), it.getString("Data")) }
                val ultimaPesquisa = lista.maxOfOrNull { it.dataPesquisa } ?: return@withContext emptyList()
                lista.filter { it.dataPesquisa == ultimaPesquisa && it.ano in anoAtual..anoAtual + 1 }
                    .sortedWith(compareBy({ it.indicador }, { it.ano }))
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() }?.also { focus = System.currentTimeMillis() to it }
    }

    // Taxa média do mercado (% ao ano) e o mês de referência
    suspend fun jurosMedio(modalidade: ModalidadeCredito): Pair<String, Double>? {
        medias[modalidade.serieMedia]?.let { if (valido(it.first, 24 * UMA_HORA)) return it.second }
        return runCatching {
            withContext(Dispatchers.IO) {
                val o = Mercado.lerJsonBcb("https://api.bcb.gov.br/dados/serie/bcdata.sgs.${modalidade.serieMedia}/dados/ultimos/1?formato=json").getJSONObject(0)
                o.getString("data") to o.getString("valor").toDouble()
            }
        }.getOrNull()?.also { medias[modalidade.serieMedia] = System.currentTimeMillis() to it }
    }

    // Ranking da semana mais recente: posição 1 é o banco mais barato
    suspend fun ranking(modalidade: ModalidadeCredito): RankingJuros? {
        rankings[modalidade.rotulo]?.let { if (valido(it.first)) return it.second }
        return runCatching {
            withContext(Dispatchers.IO) {
                val base = "https://olinda.bcb.gov.br/olinda/servico/taxaJuros/versao/v2/odata/TaxasJurosDiariaPorInicioPeriodo"
                val inicio = olinda("$base?\$top=1&\$orderby=InicioPeriodo%20desc&\$format=json&\$select=InicioPeriodo")
                    .getJSONArray("value").getJSONObject(0).getString("InicioPeriodo")
                val filtro = codificar("InicioPeriodo eq '$inicio' and Segmento eq 'PESSOA FÍSICA' and Modalidade eq '${modalidade.nomeRanking}'")
                val valores = olinda("$base?\$top=300&\$filter=$filtro&\$orderby=Posicao&\$format=json&\$select=FimPeriodo,Posicao,InstituicaoFinanceira,TaxaJurosAoMes,TaxaJurosAoAno")
                    .getJSONArray("value")
                val bancos = (0 until valores.length()).map { valores.getJSONObject(it) }.map {
                    TaxaBanco(it.getInt("Posicao"), nomeBanco(it.getString("InstituicaoFinanceira")), it.getDouble("TaxaJurosAoMes"), it.getDouble("TaxaJurosAoAno"))
                }
                val fim = if (valores.length() > 0) valores.getJSONObject(0).getString("FimPeriodo") else inicio
                RankingJuros(modalidade, inicio, fim, bancos)
            }
        }.getOrNull()?.takeIf { it.bancos.isNotEmpty() }?.also { rankings[modalidade.rotulo] = System.currentTimeMillis() to it }
    }

    // Dólar PTAX mais recente (busca os últimos 7 dias para cobrir fim de semana e feriado)
    suspend fun dolarPtax(): Ptax? {
        ptax?.let { if (valido(it.first, UMA_HORA)) return it.second }
        return runCatching {
            withContext(Dispatchers.IO) {
                val hoje = java.util.Calendar.getInstance()
                val fmt = { c: java.util.Calendar -> "%02d-%02d-%04d".format(c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH), c.get(java.util.Calendar.YEAR)) }
                val inicio = (hoje.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_MONTH, -7) }
                val valores = olinda("https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/odata/CotacaoDolarPeriodo(dataInicial=@i,dataFinalCotacao=@f)" +
                    "?@i='${fmt(inicio)}'&@f='${fmt(hoje)}'&\$format=json&\$orderby=dataHoraCotacao%20desc&\$top=1").getJSONArray("value")
                val o = valores.getJSONObject(0)
                Ptax(o.getDouble("cotacaoVenda"), o.getDouble("cotacaoCompra"), o.getString("dataHoraCotacao"))
            }
        }.getOrNull()?.also { ptax = System.currentTimeMillis() to it }
    }
}

// "BCO SANTANDER (BRASIL) S.A." -> "Santander (Brasil)": nomes mais legíveis na lista
fun nomeBanco(nome: String): String =
    nome.replace(Regex("""\b(BCO|BANCO)\b\.?""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\b(S\.?A\.?|S/A|LTDA\.?|SCFI|CFI|S\.A)\s*$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+"), " ").trim().trim('-', ',', '.').trim()
        .lowercase().split(" ").joinToString(" ") { p ->
            val i = p.indexOfFirst { it.isLetter() }
            when {
                p.length <= 3 && p.all { it.isLetter() } && p !in setOf("do", "da", "de", "dos", "das", "e") -> p.uppercase()
                i >= 0 -> p.substring(0, i) + p[i].uppercase() + p.substring(i + 1)   // "(brasil)" -> "(Brasil)"
                else -> p
            }
        }
