package fluxai.app

import com.google.firebase.Firebase
import com.google.firebase.functions.functions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.pow

// =========================================================================
// DADOS DE MERCADO E CÁLCULO DE RENDIMENTO
// CDI, Selic e IPCA vêm da API oficial do Banco Central (séries SGS, sem chave).
// Ações, FIIs, ETFs e cripto vêm da Cloud Function "cotacoes".
// Datas sempre no formato "aaaa-mm-dd" (texto ordena igual à data).
// =========================================================================

// Dia sequencial (dias desde 1970) de uma data "aaaa-mm-dd", para contar dias entre datas
fun diaSequencial(data: String): Long {
    val (a, m, d) = data.split("-").map { it.toInt() }
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(a, m - 1, d) }
    return cal.timeInMillis / 86_400_000L
}

fun hojeIso(): String {
    val c = Calendar.getInstance()
    return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}

fun inicioDoMesIso(): String = hojeIso().substring(0, 8) + "01"

// "dd/mm/aaaa" <-> "aaaa-mm-dd"
fun isoParaBr(data: String): String = data.split("-").let { if (it.size == 3) "${it[2]}/${it[1]}/${it[0]}" else data }
fun brParaIso(data: String): String? {
    val p = data.trim().split("/")
    if (p.size != 3) return null
    val (d, m, a) = p.map { it.toIntOrNull() ?: return null }
    if (a !in 1990..2100 || m !in 1..12 || d !in 1..31) return null
    return "%04d-%02d-%02d".format(a, m, d)
}

data class Cotacao(val preco: Double, val variacaoDia: Double, val precoInicioMes: Double, val nome: String)

object Mercado {
    private val trava = Mutex()
    // Série diária (CDI ou Selic): data -> taxa do dia em %. Guardada a partir da data mais antiga já pedida.
    private val seriesDiarias = mutableMapOf<Int, Pair<String, List<Pair<String, Double>>>>()
    private var ipca: Pair<String, List<Pair<String, Double>>>? = null   // mês "aaaa-mm" -> % no mês
    private var selicMeta: Double? = null
    private val cotacoes = mutableMapOf<String, Pair<Long, Cotacao>>()
    private var carregadoEm = 0L
    private const val VALIDADE_MS = 30 * 60 * 1000L

    const val SERIE_CDI = 12
    const val SERIE_SELIC = 11
    const val SERIE_POUPANCA = 195

    // A API do Banco Central às vezes devolve uma página de erro no lugar do JSON: tenta até 3 vezes
    fun lerJsonBcb(url: String): JSONArray {
        var ultimoErro: Exception? = null
        repeat(3) { tentativa ->
            try {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 10000; conn.readTimeout = 15000
                conn.setRequestProperty("Accept", "application/json")
                if (conn.responseCode == 404) return JSONArray()
                val texto = conn.inputStream.bufferedReader().readText().trim()
                if (conn.responseCode == 200 && texto.startsWith("[")) return JSONArray(texto)
                ultimoErro = java.io.IOException("Banco Central respondeu ${conn.responseCode}")
            } catch (e: Exception) { ultimoErro = e }
            Thread.sleep(800L * (tentativa + 1))
        }
        throw ultimoErro ?: java.io.IOException("Banco Central indisponível")
    }

    private fun baixarSgs(serie: Int, desde: String): List<Pair<String, Double>> {
        // A API aceita no máximo 10 anos por consulta: busca em blocos de 5 anos
        val saida = mutableListOf<Pair<String, Double>>()
        var ano = desde.substring(0, 4).toInt()
        val anoFim = hojeIso().substring(0, 4).toInt()
        var inicio = desde
        while (ano <= anoFim) {
            val fimBloco = minOf(ano + 4, anoFim)
            val url = "https://api.bcb.gov.br/dados/serie/bcdata.sgs.$serie/dados?formato=json" +
                "&dataInicial=${isoParaBr(inicio)}&dataFinal=31/12/$fimBloco"
            val arr = lerJsonBcb(url)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val iso = brParaIso(o.getString("data")) ?: continue
                o.getString("valor").toDoubleOrNull()?.let { saida += iso to it }
            }
            ano = fimBloco + 1
            inicio = "$ano-01-01"
        }
        return saida
    }

    private suspend fun serieDiaria(serie: Int, desde: String): List<Pair<String, Double>> = trava.withLock {
        val guardada = seriesDiarias[serie]
        if (guardada != null && guardada.first <= desde && System.currentTimeMillis() - carregadoEm < VALIDADE_MS) return guardada.second
        val inicio = minOf(desde, guardada?.first ?: desde)
        val dados = withContext(Dispatchers.IO) { baixarSgs(serie, inicio) }
        seriesDiarias[serie] = inicio to dados
        carregadoEm = System.currentTimeMillis()
        dados
    }

    private suspend fun ipcaMensal(desde: String): List<Pair<String, Double>> = trava.withLock {
        val guardado = ipca
        if (guardado != null && guardado.first <= desde && System.currentTimeMillis() - carregadoEm < VALIDADE_MS) return guardado.second
        val inicio = minOf(desde, guardado?.first ?: desde).substring(0, 8) + "01"
        val dados = withContext(Dispatchers.IO) { baixarSgs(433, inicio) }.map { (d, v) -> d.substring(0, 7) to v }
        ipca = inicio to dados
        dados
    }

    private suspend fun selicMetaAtual(): Double = trava.withLock {
        selicMeta ?: withContext(Dispatchers.IO) {
            lerJsonBcb("https://api.bcb.gov.br/dados/serie/bcdata.sgs.432/dados/ultimos/1?formato=json").getJSONObject(0).getString("valor").toDouble()
        }.also { selicMeta = it }
    }

    // CDI anual de hoje (para exibir): taxa diária mais recente anualizada em 252 dias úteis
    suspend fun cdiAnualAtual(): Double? = runCatching {
        val ultima = serieDiaria(SERIE_CDI, diasAtras(10)).lastOrNull()?.second ?: return null
        ((1 + ultima / 100).pow(252) - 1) * 100
    }.getOrNull()

    private fun diasAtras(n: Int): String {
        val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -n) }
        return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    // Fator de correção de um valor aplicado em "de" até "ate" (exclusive o dia da aplicação)
    suspend fun fatorRendaFixa(indexador: String, taxa: Double, de: String, ate: String): Double {
        if (ate <= de) return 1.0
        return when (indexador) {
            INDEXADOR_CDI, INDEXADOR_SELIC -> {
                val serie = serieDiaria(if (indexador == INDEXADOR_CDI) SERIE_CDI else SERIE_SELIC, de)
                serie.filter { it.first > de && it.first <= ate }
                    .fold(1.0) { f, (_, diaria) -> f * (1 + diaria / 100 * taxa / 100) }
            }
            INDEXADOR_PREFIXADO -> (1 + taxa / 100).pow(diasUteis(de, ate) / 252.0)
            INDEXADOR_IPCA -> fatorIpca(de, ate) * (1 + taxa / 100).pow(diasUteis(de, ate) / 252.0)
            INDEXADOR_POUPANCA -> fatorPoupanca(de, ate)
            else -> 1.0
        }
    }

    // Dias úteis pela própria série do CDI (só tem dias úteis); sem internet, estima 252/365 dos dias corridos
    private suspend fun diasUteis(de: String, ate: String): Int = runCatching {
        serieDiaria(SERIE_CDI, de).count { it.first > de && it.first <= ate }
            .takeIf { it > 0 || diaSequencial(ate) - diaSequencial(de) < 5 }
    }.getOrNull() ?: ((diaSequencial(ate) - diaSequencial(de)) * 252 / 365).toInt()

    // IPCA do período, proporcional aos dias de cada mês. Meses ainda sem índice usam o último publicado.
    private suspend fun fatorIpca(de: String, ate: String): Double {
        val meses = ipcaMensal(de)
        if (meses.isEmpty()) return 1.0
        val porMes = meses.toMap()
        val ultimo = meses.last().second
        var fator = 1.0
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(de.substring(0, 4).toInt(), de.substring(5, 7).toInt() - 1, 1) }
        val diaDe = diaSequencial(de); val diaAte = diaSequencial(ate)
        while (true) {
            val chave = "%04d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
            val inicioMes = cal.timeInMillis / 86_400_000L
            val diasNoMes = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val fimMes = inicioMes + diasNoMes
            if (inicioMes >= diaAte) break
            val cobertos = (minOf(fimMes, diaAte) - maxOf(inicioMes, diaDe)).coerceAtLeast(0)
            if (cobertos > 0) fator *= (1 + (porMes[chave] ?: ultimo) / 100).pow(cobertos.toDouble() / diasNoMes)
            cal.add(Calendar.MONTH, 1)
        }
        return fator
    }

    // Poupança: rentabilidade oficial de cada aniversário (série 195 do Banco Central, já com a TR).
    // Rende só no aniversário; depósitos nos dias 29, 30 e 31 fazem aniversário no dia 1º do mês seguinte.
    // Aniversário ainda sem índice publicado usa a regra: 0,5% ao mês com Selic acima de 8,5%, senão 70% da Selic.
    private suspend fun fatorPoupanca(de: String, ate: String): Double {
        val (a1, m1, d1) = de.split("-").map { it.toInt() }
        val (a2, m2, d2) = ate.split("-").map { it.toInt() }
        var meses = (a2 - a1) * 12 + (m2 - m1)
        if (d2 < d1) meses--
        if (meses <= 0) return 1.0
        val oficial = runCatching { serieDiaria(SERIE_POUPANCA, de).toMap() }.getOrDefault(emptyMap())
        val estimada = runCatching { selicMetaAtual() }.getOrDefault(10.0).let { selic -> if (selic > 8.5) 0.5 else ((1 + selic * 0.7 / 100).pow(1.0 / 12) - 1) * 100 }
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(a1, m1 - 1, minOf(d1, 28)) }
        var fator = 1.0
        repeat(meses) {
            val aniversario = if (d1 > 28) {
                val c = (cal.clone() as Calendar).apply { add(Calendar.MONTH, 1); set(Calendar.DAY_OF_MONTH, 1) }
                "%04d-%02d-01".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
            } else "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, d1)
            fator *= 1 + (oficial[aniversario] ?: estimada) / 100
            cal.add(Calendar.MONTH, 1)
        }
        return fator
    }

    // Fechamento de cada mês ("aaaa-mm" -> preço) dos últimos 12 meses, para o gráfico de evolução
    private val historicos = mutableMapOf<String, Pair<Long, Map<String, Double>>>()

    suspend fun historicoPrecos(ativos: List<Pair<String, Boolean>>): Map<String, Map<String, Double>> {
        val agora = System.currentTimeMillis()
        val faltando = ativos.distinct().filter { (t, _) -> historicos[t]?.let { agora - it.first > 60 * 60 * 1000L } ?: true }
        if (faltando.isNotEmpty()) runCatching {
            val pedido = mapOf("ativos" to faltando.map { (t, cripto) -> mapOf("ticker" to t, "tipo" to if (cripto) "cripto" else "b3") })
            val dados = Firebase.functions("southamerica-east1").getHttpsCallable("historicoPrecos").call(pedido).await().getData() as? Map<*, *>
            (dados?.get("historico") as? Map<*, *>)?.forEach { (k, v) ->
                val meses = (v as? Map<*, *>)?.mapNotNull { (mes, preco) -> (preco as? Number)?.let { mes.toString() to it.toDouble() } }?.toMap() ?: return@forEach
                historicos[k.toString()] = agora to meses
            }
        }
        return ativos.mapNotNull { (t, _) -> historicos[t]?.let { t to it.second } }.toMap()
    }

    // Cotações pela function; o que falhar fica sem cotação (o app mostra pelo preço médio)
    suspend fun cotacoes(ativos: List<Pair<String, Boolean>>): Map<String, Cotacao> {
        val agora = System.currentTimeMillis()
        val faltando = ativos.distinct().filter { (t, _) -> cotacoes[t]?.let { agora - it.first > 5 * 60 * 1000L } ?: true }
        if (faltando.isNotEmpty()) runCatching {
            val pedido = mapOf("ativos" to faltando.map { (t, cripto) -> mapOf("ticker" to t, "tipo" to if (cripto) "cripto" else "b3") })
            val dados = Firebase.functions("southamerica-east1").getHttpsCallable("cotacoes").call(pedido).await().getData() as? Map<*, *>
            (dados?.get("cotacoes") as? Map<*, *>)?.forEach { (k, v) ->
                val m = v as? Map<*, *> ?: return@forEach
                val preco = (m["preco"] as? Number)?.toDouble() ?: return@forEach
                cotacoes[k.toString()] = agora to Cotacao(
                    preco, (m["variacaoDia"] as? Number)?.toDouble() ?: 0.0,
                    (m["precoInicioMes"] as? Number)?.toDouble() ?: preco, m["nome"]?.toString() ?: k.toString()
                )
            }
        }
        return ativos.mapNotNull { (t, _) -> cotacoes[t]?.let { t to it.second } }.toMap()
    }
}

const val INDEXADOR_CDI = "cdi"
const val INDEXADOR_SELIC = "selic"
const val INDEXADOR_PREFIXADO = "prefixado"
const val INDEXADOR_IPCA = "ipca"
const val INDEXADOR_POUPANCA = "poupanca"

// Imposto de renda regressivo da renda fixa, pelo tempo da aplicação
fun aliquotaIR(dias: Long): Double = when {
    dias <= 180 -> 0.225
    dias <= 360 -> 0.20
    dias <= 720 -> 0.175
    else -> 0.15
}
