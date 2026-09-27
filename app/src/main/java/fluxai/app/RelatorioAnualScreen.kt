package fluxai.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

// =========================================================================
// RELATÓRIO ANUAL
// Renda x despesas mês a mês, comparação com o ano anterior, categorias e
// um resumo dos gastos com saúde e educação para a declaração do IR.
// =========================================================================
data class ResumoAno(
    val ano: Int,
    val rendaPorMes: List<Double>,       // 12 posições, janeiro primeiro
    val despesasPorMes: List<Double>,
    val porCategoria: List<Pair<String, Double>>,
    val maiores: List<Despesa>,
    val saude: List<Despesa>,
    val educacao: List<Despesa>,
    val todas: List<Despesa>,
    val ultimoMes: Int = 12              // no ano corrente, só os meses até hoje entram nos totais
) {
    val renda get() = rendaPorMes.take(ultimoMes).sum()
    val despesas get() = despesasPorMes.take(ultimoMes).sum()
}

private val NomesMeses = listOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")

suspend fun carregarResumoAno(workspaceUid: String, ano: Int): ResumoAno {
    val usuario = Firebase.firestore.collection("usuarios").document(workspaceUid)
    val meses = (1..12).map { "%02d/%04d".format(it, ano) }
    val despesas = usuario.collection("despesas").whereIn("mesAno", meses).get().await().documents.map { lerDespesa(it) }
        .filter { it.projetoId == null && !it.descricao.startsWith("Apontamento:") && it.status != "Próximo Mês" }
    val saldos = usuario.collection("saldos").get().await().documents.associateBy { it.id }
    val renda = meses.map { m ->
        saldos[m.replace("/", "-")]?.let { (it.getDouble("adiantamento") ?: 0.0) + (it.getDouble("pagamento") ?: it.getDouble("valor") ?: 0.0) + (it.getDouble("extra") ?: 0.0) } ?: 0.0
    }
    val porMes = meses.map { m -> despesas.filter { it.mesAno == m }.sumOf { it.valor } }
    // Parcelas já lançadas para os próximos meses aparecem no gráfico, mas não somam no ano ainda
    val hoje = Calendar.getInstance()
    val ultimoMes = when {
        ano < hoje.get(Calendar.YEAR) -> 12
        ano == hoje.get(Calendar.YEAR) -> hoje.get(Calendar.MONTH) + 1
        else -> 0
    }
    val ateHoje = despesas.filter { (it.mesAno.substringBefore("/").toIntOrNull() ?: 13) <= ultimoMes }
    return ResumoAno(
        ano = ano,
        rendaPorMes = renda,
        despesasPorMes = porMes,
        porCategoria = ateHoje.groupBy { it.categoria }.mapValues { (_, l) -> l.sumOf { it.valor } }.toList().sortedByDescending { it.second },
        maiores = ateHoje.sortedByDescending { it.valor }.take(5),
        saude = ateHoje.filter { it.categoria == "Saúde" },
        educacao = ateHoje.filter { it.categoria == "Educação" },
        todas = despesas,
        ultimoMes = ultimoMes
    )
}

@Composable
fun RelatorioAnualScreen(onLogout: () -> Unit) {
    val context = LocalContext.current
    val usuario = Firebase.auth.currentUser ?: return
    val workspaceUid = remember { workspaceAtual(context, usuario.uid) }
    val c = coresTela()
    val moeda = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    var ano by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var resumo by remember { mutableStateOf<ResumoAno?>(null) }
    var anterior by remember { mutableStateOf<ResumoAno?>(null) }
    var carregando by remember { mutableStateOf(true) }
    var erro by remember { mutableStateOf(false) }

    LaunchedEffect(ano, workspaceUid) {
        carregando = true; erro = false
        runCatching {
            resumo = carregarResumoAno(workspaceUid, ano)
            anterior = carregarResumoAno(workspaceUid, ano - 1)
        }.onFailure { erro = true }
        carregando = false
    }

    TelaComMenu(
        titulo = "Relatório anual", rota = "relatorio", onLogout = onLogout,
        acoes = {
            IconButton(onClick = { resumo?.let { exportarDespesasParaCSV(context, it.todas, "ano $ano") } }, enabled = resumo?.todas?.isNotEmpty() == true) {
                Icon(Icons.Default.IosShare, "Exportar o ano em CSV", tint = c.destaque)
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.Center, Alignment.CenterVertically) {
                IconButton(onClick = { ano-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Ano anterior", tint = c.texto) }
                Surface(shape = RoundedCornerShape(50), color = c.superficie, border = BorderStroke(1.dp, c.divisor)) {
                    Text("$ano", fontWeight = FontWeight.Bold, color = c.texto, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
                }
                IconButton(onClick = { ano++ }, enabled = ano < Calendar.getInstance().get(Calendar.YEAR)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Próximo ano", tint = c.texto)
                }
            }

            val r = resumo
            when {
                carregando -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = c.destaque) }
                erro || r == null -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                    Text("Não foi possível carregar o relatório. Confira a conexão.", color = c.textoFraco, textAlign = TextAlign.Center)
                }
                r.despesas == 0.0 && r.renda == 0.0 -> Column(Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.size(88.dp).background(c.destaque.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Assessment, null, Modifier.size(44.dp), tint = c.destaque)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Sem dados em $ano", fontWeight = FontWeight.Bold, color = c.texto)
                }
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item { CartaoResumoAno(r, anterior, moeda, c) }
                    item { CartaoMesAMes(r, moeda, c) }
                    item {
                        CartaoRelatorio("Onde o dinheiro foi no ano", c) {
                            r.porCategoria.forEach { (cat, v) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(32.dp).background(corCategoria(cat).copy(alpha = 0.14f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                        Icon(iconeCategoria(cat), null, tint = corCategoria(cat), modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row {
                                            Text(cat, fontSize = 14.sp, color = c.texto, modifier = Modifier.weight(1f))
                                            Text(moeda.format(v), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.texto)
                                        }
                                        Text("${(v / r.despesas * 100).toInt()}% das despesas · média ${moeda.format(v / r.ultimoMes.coerceAtLeast(1))}/mês", fontSize = 11.sp, color = c.textoFraco)
                                    }
                                }
                            }
                        }
                    }
                    item { CartaoImpostoRenda(r, moeda, c) }
                    item {
                        CartaoRelatorio("Maiores gastos do ano", c) {
                            r.maiores.forEach { d ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(d.descricao, fontSize = 14.sp, color = c.texto, maxLines = 1)
                                        Text("${d.categoria} · ${d.mesAno}", fontSize = 11.sp, color = c.textoFraco)
                                    }
                                    Text(moeda.format(d.valor), fontWeight = FontWeight.Bold, color = c.texto)
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun CartaoRelatorio(titulo: String, c: CoresTela, conteudo: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = c.superficie, border = BorderStroke(1.dp, c.divisor), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(titulo, fontWeight = FontWeight.Bold, color = c.texto, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(10.dp))
            conteudo()
        }
    }
}

@Composable
private fun CartaoResumoAno(r: ResumoAno, anterior: ResumoAno?, moeda: NumberFormat, c: CoresTela) {
    val sobra = r.renda - r.despesas
    CartaoRelatorio("Resumo de ${r.ano}", c) {
        Text("Sobra no ano", fontSize = 12.sp, color = c.textoFraco)
        Text(moeda.format(sobra), fontSize = 30.sp, fontWeight = FontWeight.Black, color = if (sobra >= 0) c.texto else Color(0xFFE53935))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) { Text("Renda", fontSize = 11.sp, color = c.textoFraco); Text(moeda.format(r.renda), fontWeight = FontWeight.Bold, color = Color(0xFF43A047)) }
            Column(Modifier.weight(1f)) { Text("Despesas", fontSize = 11.sp, color = c.textoFraco); Text(moeda.format(r.despesas), fontWeight = FontWeight.Bold, color = Color(0xFFE53935)) }
            Column(Modifier.weight(0.7f)) {
                Text("Poupança", fontSize = 11.sp, color = c.textoFraco)
                Text(if (r.renda > 0) "${(sobra / r.renda * 100).toInt()}%" else "—", fontWeight = FontWeight.Bold, color = c.texto)
            }
        }
        // Comparação com o ano anterior, quando houver dados
        anterior?.takeIf { it.despesas > 0 }?.let { a ->
            val variacao = (r.despesas - a.despesas) / a.despesas * 100
            val subiu = variacao > 0
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (subiu) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown, null, tint = if (subiu) Color(0xFFE53935) else Color(0xFF43A047), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Despesas ${if (subiu) "+" else ""}${"%.0f".format(variacao)}% em relação a ${a.ano} (${moeda.format(a.despesas)})", fontSize = 12.sp, color = if (subiu) Color(0xFFE53935) else Color(0xFF43A047))
            }
        }
    }
}

@Composable
private fun CartaoMesAMes(r: ResumoAno, moeda: NumberFormat, c: CoresTela) {
    val maximo = (r.rendaPorMes + r.despesasPorMes).maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    val corRenda = Color(0xFF43A047)
    val descricao = NomesMeses.indices.joinToString("; ") { i -> "${NomesMeses[i]}: renda ${moeda.format(r.rendaPorMes[i])}, despesas ${moeda.format(r.despesasPorMes[i])}" }
    CartaoRelatorio("Mês a mês", c) {
        Canvas(Modifier.fillMaxWidth().height(130.dp).semantics { contentDescription = "Gráfico de renda e despesas por mês. $descricao" }) {
            val grupo = size.width / 12
            val barra = (grupo * 0.3f).coerceAtMost(10.dp.toPx())
            for (i in 0 until 12) {
                val centro = grupo * i + grupo / 2
                val hR = (r.rendaPorMes[i] / maximo * size.height).toFloat()
                val hD = (r.despesasPorMes[i] / maximo * size.height).toFloat()
                val raio = CornerRadius(3.dp.toPx())
                val alfa = if (i < r.ultimoMes) 1f else 0.35f // meses que ainda não chegaram ficam apagados
                drawRoundRect(corRenda.copy(alpha = alfa), Offset(centro - barra - 1.dp.toPx(), size.height - hR), Size(barra, hR), raio)
                drawRoundRect(c.destaque.copy(alpha = alfa), Offset(centro + 1.dp.toPx(), size.height - hD), Size(barra, hD), raio)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            NomesMeses.forEach { Text(it.take(1), fontSize = 10.sp, color = c.textoFraco, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(8.dp))
        val comGasto = r.despesasPorMes.take(r.ultimoMes).filter { it > 0 }
        if (r.ultimoMes < 12 && r.despesasPorMes.drop(r.ultimoMes).any { it > 0 }) {
            Text("Barras claras: parcelas já lançadas para os próximos meses (fora dos totais).", fontSize = 11.sp, color = c.textoFraco)
        }
        if (comGasto.isNotEmpty()) {
            val (iMaior, maior) = r.despesasPorMes.take(r.ultimoMes).withIndex().maxBy { it.value }
            Text("Média de ${moeda.format(comGasto.average())} por mês com lançamentos. Mês mais caro: ${NomesMeses[iMaior]} (${moeda.format(maior)}).", fontSize = 12.sp, color = c.textoFraco, lineHeight = 16.sp)
        }
    }
}

// Resumo para a declaração: saúde não tem limite de dedução; educação tem limite anual por pessoa
@Composable
private fun CartaoImpostoRenda(r: ResumoAno, moeda: NumberFormat, c: CoresTela) {
    CartaoRelatorio("Para o Imposto de Renda", c) {
        Text("Gastos que costumam ser dedutíveis na declaração de ${r.ano + 1}.", fontSize = 12.sp, color = c.textoFraco)
        Spacer(Modifier.height(12.dp))
        listOf(
            Triple("Saúde", r.saude, "Consultas, exames, planos de saúde e dentista. Sem limite de valor."),
            Triple("Educação", r.educacao, "Escola, faculdade e pós. Limite anual de R$ 3.561,50 por pessoa; cursos livres e idiomas não entram.")
        ).forEach { (titulo, lista, regra) ->
            val comComprovante = lista.count { !it.comprovante.isNullOrBlank() }
            Surface(shape = RoundedCornerShape(14.dp), color = corCategoria(titulo).copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(iconeCategoria(titulo), null, tint = corCategoria(titulo), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(titulo, fontWeight = FontWeight.SemiBold, color = c.texto, modifier = Modifier.weight(1f))
                        Text(moeda.format(lista.sumOf { it.valor }), fontWeight = FontWeight.Bold, color = c.texto)
                    }
                    Text(regra, fontSize = 11.sp, color = c.textoFraco, lineHeight = 15.sp, modifier = Modifier.padding(top = 4.dp))
                    if (lista.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { comComprovante.toFloat() / lista.size }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                            color = corCategoria(titulo), trackColor = c.divisor, strokeCap = StrokeCap.Round, drawStopIndicator = {}
                        )
                        Text("$comComprovante de ${lista.size} lançamentos com comprovante anexado", fontSize = 11.sp, color = c.textoFraco, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        Text("Confira as regras da Receita Federal do ano da declaração e guarde os recibos com CPF/CNPJ do prestador.", fontSize = 11.sp, color = c.textoFraco, lineHeight = 15.sp)
    }
}
