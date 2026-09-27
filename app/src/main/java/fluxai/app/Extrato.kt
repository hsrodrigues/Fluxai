package fluxai.app

// =========================================================================
// LEITURA DE EXTRATO (OFX e CSV)
// Transforma o arquivo exportado pelo banco em itens com data, descrição e valor.
// valor é sempre positivo; "entrada" diz se o dinheiro entrou (crédito) ou saiu (débito).
// =========================================================================
data class ItemExtrato(val data: DataSimples, val descricao: String, val valor: Double, val entrada: Boolean)

private fun tagOfx(bloco: String, tag: String): String? =
    Regex("<$tag>([^<\\r\\n]*)", RegexOption.IGNORE_CASE).find(bloco)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

fun lerOFX(texto: String): List<ItemExtrato> =
    texto.split(Regex("<STMTTRN>", RegexOption.IGNORE_CASE)).drop(1).mapNotNull { bloco ->
        val valor = tagOfx(bloco, "TRNAMT")?.replace(",", ".")?.toDoubleOrNull() ?: return@mapNotNull null
        val data = tagOfx(bloco, "DTPOSTED")?.takeIf { it.length >= 8 }?.let {
            DataSimples(it.substring(6, 8).toInt(), it.substring(4, 6).toInt(), it.substring(0, 4).toInt())
        } ?: return@mapNotNull null
        val descricao = listOfNotNull(tagOfx(bloco, "MEMO"), tagOfx(bloco, "NAME")).firstOrNull() ?: "Lançamento"
        if (valor == 0.0) null else ItemExtrato(data, formatarNomeProprio(descricao), kotlin.math.abs(valor), entrada = valor > 0)
    }

// Separa uma linha de CSV respeitando aspas ("Compra, loja X")
fun dividirLinhaCsv(linha: String, separador: Char): List<String> {
    val campos = mutableListOf<String>()
    val atual = StringBuilder()
    var entreAspas = false
    var i = 0
    while (i < linha.length) {
        val c = linha[i]
        when {
            c == '"' && entreAspas && i + 1 < linha.length && linha[i + 1] == '"' -> { atual.append('"'); i++ }
            c == '"' -> entreAspas = !entreAspas
            c == separador && !entreAspas -> { campos += atual.toString().trim(); atual.clear() }
            else -> atual.append(c)
        }
        i++
    }
    campos += atual.toString().trim()
    return campos
}

fun lerDataTexto(texto: String): DataSimples? {
    val t = texto.trim().take(10)
    Regex("""^(\d{1,2})/(\d{1,2})/(\d{2,4})$""").find(t)?.let { m ->
        val (d, mes, a) = m.destructured
        val ano = a.toInt().let { if (it < 100) 2000 + it else it }
        return DataSimples(d.toInt(), mes.toInt(), ano).takeIf { it.mes in 1..12 && it.dia in 1..31 }
    }
    Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(t)?.let { m ->
        val (a, mes, d) = m.destructured
        return DataSimples(d.toInt(), mes.toInt(), a.toInt()).takeIf { it.mes in 1..12 && it.dia in 1..31 }
    }
    return null
}

private fun valorTexto(texto: String): Double? {
    val limpo = texto.replace("R$", "").replace(" ", "").trim()
    val negativo = limpo.startsWith("(") && limpo.endsWith(")")
    return limpo.removePrefix("(").removeSuffix(")").paraValor()?.let { if (negativo) -it else it }
}

// fatura = true: arquivo de fatura de cartão, onde valores positivos são compras (padrão do Nubank)
fun lerCSVExtrato(texto: String, fatura: Boolean): List<ItemExtrato> {
    val linhas = texto.removePrefix("﻿").lines().filter { it.isNotBlank() }
    if (linhas.isEmpty()) return emptyList()
    val cabecalho = linhas.first()
    val separador = listOf(';', ',', '\t').maxBy { s -> cabecalho.count { it == s } }
    val colunas = dividirLinhaCsv(cabecalho, separador).map { it.lowercase() }

    fun coluna(vararg nomes: String) = colunas.indexOfFirst { c -> nomes.any { it in c } }
    var iData = coluna("data", "date")
    var iDesc = coluna("descri", "title", "histór", "histor", "estabelecimento", "memo", "lançamento", "lancamento", "identificador")
    var iValor = coluna("valor", "amount", "quantia")
    // "Identificador" do Nubank é um código; prefere "Descrição" se existir
    colunas.indexOfFirst { "descri" in it }.takeIf { it >= 0 }?.let { iDesc = it }

    val temCabecalho = iData >= 0 && iValor >= 0
    val dados = if (temCabecalho) linhas.drop(1) else linhas
    if (!temCabecalho) {
        // Sem cabeçalho: descobre as colunas pela primeira linha (data, texto, número)
        val amostra = dividirLinhaCsv(dados.first(), separador)
        iData = amostra.indexOfFirst { lerDataTexto(it) != null }
        iValor = amostra.indexOfLast { valorTexto(it) != null && lerDataTexto(it) == null }
        iDesc = amostra.indices.firstOrNull { it != iData && it != iValor && amostra[it].any { c -> c.isLetter() } } ?: -1
        if (iData < 0 || iValor < 0) return emptyList()
    }
    if (iDesc < 0) iDesc = colunas.indices.firstOrNull { it != iData && it != iValor } ?: iData

    return dados.mapNotNull { linha ->
        val campos = dividirLinhaCsv(linha, separador)
        val data = campos.getOrNull(iData)?.let { lerDataTexto(it) } ?: return@mapNotNull null
        val bruto = campos.getOrNull(iValor)?.let { valorTexto(it) } ?: return@mapNotNull null
        if (bruto == 0.0) return@mapNotNull null
        val saida = if (fatura) bruto > 0 else bruto < 0
        val descricao = campos.getOrNull(iDesc)?.ifBlank { null } ?: "Lançamento"
        ItemExtrato(data, formatarNomeProprio(descricao), kotlin.math.abs(bruto), entrada = !saida)
    }
}

fun lerExtrato(nomeArquivo: String, texto: String, fatura: Boolean): List<ItemExtrato> =
    if (nomeArquivo.lowercase().endsWith(".ofx") || "<OFX>" in texto.uppercase()) lerOFX(texto) else lerCSVExtrato(texto, fatura)

// =========================================================================
// SALDO DO EXTRATO DA CONTA
// No extrato da conta só interessa o saldo final: os gastos o usuário já registra no app.
// =========================================================================
data class SaldoExtrato(val valor: Double, val data: DataSimples?)

private fun dataOfx(texto: String?): DataSimples? =
    texto?.takeIf { it.length >= 8 && it.take(8).all(Char::isDigit) }?.let { DataSimples(it.substring(6, 8).toInt(), it.substring(4, 6).toInt(), it.substring(0, 4).toInt()) }

// OFX: bloco LEDGERBAL (saldo contábil) ou, na falta dele, AVAILBAL (disponível)
fun lerSaldoOFX(texto: String): SaldoExtrato? =
    listOf("LEDGERBAL", "AVAILBAL").firstNotNullOfOrNull { bloco ->
        val trecho = Regex("<$bloco>(.*?)(</$bloco>|<(?:AVAILBAL|LEDGERBAL|/STMTRS|/CCSTMTRS)>)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(texto)?.groupValues?.get(1) ?: return@firstNotNullOfOrNull null
        val valor = Regex("<BALAMT>([^<\r\n]+)", RegexOption.IGNORE_CASE).find(trecho)?.groupValues?.get(1)?.trim()?.replace(",", ".")?.toDoubleOrNull()
            ?: return@firstNotNullOfOrNull null
        SaldoExtrato(valor, dataOfx(Regex("<DTASOF>([^<\r\n]+)", RegexOption.IGNORE_CASE).find(trecho)?.groupValues?.get(1)?.trim()))
    }

// CSV com coluna "Saldo": pega o saldo da linha mais recente (o arquivo pode vir em ordem crescente ou decrescente)
fun lerSaldoCSV(texto: String): SaldoExtrato? {
    val linhas = texto.removePrefix("﻿").lines().filter { it.isNotBlank() }
    if (linhas.size < 2) return null
    val separador = listOf(';', ',', '\t').maxBy { s -> linhas.first().count { it == s } }
    val colunas = dividirLinhaCsv(linhas.first(), separador).map { it.lowercase() }
    val iSaldo = colunas.indexOfFirst { "saldo" in it }.takeIf { it >= 0 } ?: return null
    val iData = colunas.indexOfFirst { "data" in it || "date" in it }
    val registros = linhas.drop(1).mapNotNull { l ->
        val campos = dividirLinhaCsv(l, separador)
        val saldo = campos.getOrNull(iSaldo)?.replace("R$", "")?.replace(" ", "")?.paraValor() ?: return@mapNotNull null
        SaldoExtrato(saldo, campos.getOrNull(iData)?.let { lerDataTexto(it) })
    }
    if (registros.isEmpty()) return null
    fun chave(d: DataSimples?) = d?.let { it.ano * 10000 + it.mes * 100 + it.dia } ?: 0
    val decrescente = chave(registros.first().data) > chave(registros.last().data)
    return if (decrescente) registros.first() else registros.last()
}

// Resposta da IA para o PDF: "DD/MM/AAAA|VALOR" (valor pode ser negativo)
fun lerRespostaSaldoIA(resposta: String): SaldoExtrato? {
    val m = Regex("""(\d{1,2}/\d{1,2}/\d{2,4})?\s*\|\s*(-?[\d.,]+)""").find(resposta) ?: return null
    val bruto = m.groupValues[2]
    val valor = (if (',' in bruto) bruto.paraValor() else bruto.toDoubleOrNull()) ?: return null
    return SaldoExtrato(valor, m.groupValues[1].takeIf { it.isNotBlank() }?.let { lerDataTexto(it) })
}
