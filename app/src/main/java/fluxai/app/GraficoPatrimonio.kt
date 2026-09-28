package fluxai.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fluxai.app.ui.theme.LocalDarkTheme
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

// =========================================================================
// GRÁFICO DA EVOLUÇÃO DO PATRIMÔNIO
// Uma linha para o patrimônio e uma tracejada para o valor aplicado, no mesmo eixo em R$.
// Cores fixas validadas (daltonismo e contraste) para o tema claro e o escuro.
// Tocar ou arrastar mostra os valores do mês; "Ver em tabela" lista todos os meses.
// =========================================================================
private data class CoresGrafico(val patrimonio: Color, val aplicado: Color)
private val CoresGraficoClaro = CoresGrafico(Color(0xFF7E57C2), Color(0xFF00897B))
private val CoresGraficoEscuro = CoresGrafico(Color(0xFF9575CD), Color(0xFF26A69A))

private val moedaGrafico: NumberFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
private const val MARGEM_ESQUERDA_DP = 52
private const val MARGEM_DIREITA_DP = 8

// Rótulo curto do eixo: R$ 850, R$ 12 mil, R$ 1,2 mi
private fun valorCurto(v: Double): String = when {
    v >= 1_000_000 -> "R$ " + String.format(Locale("pt", "BR"), "%.1f", v / 1_000_000).removeSuffix(",0") + " mi"
    v >= 1_000 -> "R$ " + String.format(Locale("pt", "BR"), "%.1f", v / 1_000).removeSuffix(",0") + " mil"
    else -> "R$ " + v.roundToInt()
}

// Topo "redondo" do eixo (1, 2, 2,5 ou 5 × 10^n) para as linhas de grade caírem em valores legíveis
private fun topoRedondo(max: Double): Double {
    if (max <= 0) return 100.0
    val base = 10.0.pow(floor(log10(max)))
    return listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * base }.first { it >= max }
}

@Composable
fun CardEvolucaoPatrimonio(pontos: List<PontoEvolucao>, c: CoresTela) {
    if (pontos.size < 2) return
    val cores = if (LocalDarkTheme.current) CoresGraficoEscuro else CoresGraficoClaro
    var selecionado by remember(pontos) { mutableStateOf<Int?>(null) }
    var verTabela by remember { mutableStateOf(false) }
    val primeiro = pontos.first()
    val ultimo = pontos.last()
    val variacao = ultimo.patrimonio - primeiro.patrimonio

    Surface(shape = RoundedCornerShape(20.dp), color = c.superficie, border = BorderStroke(1.dp, c.divisor), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text("Evolução do patrimônio", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.texto)
            Text("${if (variacao >= 0) "+" else ""}${moedaGrafico.format(variacao)} desde ${primeiro.rotulo}", fontSize = 12.sp, color = c.textoFraco)
            Spacer(Modifier.height(10.dp))
            // Legenda sempre presente; a linha tracejada diferencia as séries sem depender só da cor
            Row(verticalAlignment = Alignment.CenterVertically) {
                ItemLegenda("Patrimônio", cores.patrimonio, tracejado = false, c)
                Spacer(Modifier.width(16.dp))
                ItemLegenda("Aplicado", cores.aplicado, tracejado = true, c)
            }
            Spacer(Modifier.height(8.dp))

            // Valores do mês tocado (ou de hoje)
            val foco = pontos[selecionado ?: pontos.lastIndex]
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(foco.rotulo.replaceFirstChar { it.uppercase() }, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.texto, modifier = Modifier.weight(1f))
                Text("${moedaGrafico.format(foco.patrimonio)} · aplicado ${moedaGrafico.format(foco.aplicado)}", fontSize = 12.sp, color = c.textoFraco)
            }

            val medidor = rememberTextMeasurer()
            val corGrade = c.divisor
            val corEixo = c.textoFraco
            val corSuperficie = c.superficie
            val resumo = "Gráfico da evolução do patrimônio: de ${moedaGrafico.format(primeiro.patrimonio)} em ${primeiro.rotulo} " +
                "para ${moedaGrafico.format(ultimo.patrimonio)} hoje, com ${moedaGrafico.format(ultimo.aplicado)} aplicados."
            Canvas(
                Modifier.fillMaxWidth().height(190.dp).padding(top = 8.dp)
                    .semantics { contentDescription = resumo }
                    .pointerInput(pontos) { detectTapGestures { selecionado = indicePorX(it.x, size.width.toFloat(), pontos.size) } }
                    .pointerInput(pontos) {
                        detectDragGestures { mudanca, _ -> selecionado = indicePorX(mudanca.position.x, size.width.toFloat(), pontos.size) }
                    }
            ) {
                desenharGrafico(pontos, cores, selecionado, medidor, corGrade, corEixo, corSuperficie)
            }

            TextButton(onClick = { verTabela = !verTabela }, contentPadding = PaddingValues(0.dp)) {
                Text(if (verTabela) "Esconder tabela" else "Ver em tabela", fontSize = 12.sp, color = c.destaque)
            }
            if (verTabela) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("Mês", fontSize = 11.sp, color = c.textoFraco, modifier = Modifier.weight(1f))
                    Text("Aplicado", fontSize = 11.sp, color = c.textoFraco, modifier = Modifier.weight(1.3f))
                    Text("Patrimônio", fontSize = 11.sp, color = c.textoFraco, modifier = Modifier.weight(1.3f))
                }
                pontos.reversed().forEach { p ->
                    HorizontalDivider(color = c.divisor)
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(p.rotulo, fontSize = 12.sp, color = c.texto, modifier = Modifier.weight(1f))
                        Text(moedaGrafico.format(p.aplicado), fontSize = 12.sp, color = c.textoFraco, modifier = Modifier.weight(1.3f))
                        Text(moedaGrafico.format(p.patrimonio), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.texto, modifier = Modifier.weight(1.3f))
                    }
                }
            }
        }
    }
}

// Ponto mais próximo do toque (a área de toque é a largura toda, maior que a marca)
private fun androidx.compose.ui.input.pointer.PointerInputScope.indicePorX(x: Float, largura: Float, total: Int): Int {
    val esquerda = MARGEM_ESQUERDA_DP.dp.toPx()
    val util = largura - esquerda - MARGEM_DIREITA_DP.dp.toPx()
    return ((x - esquerda) / util * (total - 1)).roundToInt().coerceIn(0, total - 1)
}

@Composable
private fun ItemLegenda(texto: String, cor: Color, tracejado: Boolean, c: CoresTela) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 18.dp, height = 10.dp)) {
            drawLine(
                cor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = if (tracejado) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx())) else null
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(texto, fontSize = 12.sp, color = c.textoFraco)
    }
}

private fun DrawScope.desenharGrafico(
    pontos: List<PontoEvolucao>, cores: CoresGrafico, selecionado: Int?, medidor: TextMeasurer,
    corGrade: Color, corEixo: Color, corSuperficie: Color
) {
    val esquerda = MARGEM_ESQUERDA_DP.dp.toPx()
    val direita = MARGEM_DIREITA_DP.dp.toPx()
    val base = 22.dp.toPx()
    val topo = 6.dp.toPx()
    val largura = size.width - esquerda - direita
    val altura = size.height - base - topo
    val maximo = topoRedondo(pontos.maxOf { maxOf(it.patrimonio, it.aplicado) } * 1.05)
    fun x(i: Int) = esquerda + largura * i / (pontos.size - 1)
    fun y(v: Double) = topo + altura * (1 - (v / maximo)).toFloat()
    val estiloEixo = TextStyle(fontSize = 10.sp, color = corEixo)

    // Grade recessiva: 4 faixas com os valores à esquerda
    for (i in 0..4) {
        val v = maximo * i / 4
        val yy = y(v)
        drawLine(corGrade, Offset(esquerda, yy), Offset(size.width - direita, yy), strokeWidth = 1.dp.toPx())
        val t = medidor.measure(valorCurto(v), estiloEixo)
        drawText(t, topLeft = Offset(esquerda - t.size.width - 6.dp.toPx(), yy - t.size.height / 2))
    }
    // Meses no eixo x (pula alguns quando são muitos; o último, "hoje", sempre aparece)
    val passo = ceil(pontos.size / 6.0).toInt().coerceAtLeast(1)
    pontos.forEachIndexed { i, p ->
        val ultimoOuMarcado = i == pontos.lastIndex || (i % passo == 0 && pontos.lastIndex - i >= passo)
        if (ultimoOuMarcado) {
            val t = medidor.measure(p.rotulo, estiloEixo)
            val xx = (x(i) - t.size.width / 2).coerceIn(esquerda - 4.dp.toPx(), size.width - t.size.width.toFloat())
            drawText(t, topLeft = Offset(xx, size.height - base + 6.dp.toPx()))
        }
    }

    // Área suave sob o patrimônio e as duas linhas de 2dp
    val linhaPatrimonio = Path().apply { pontos.forEachIndexed { i, p -> if (i == 0) moveTo(x(i), y(p.patrimonio)) else lineTo(x(i), y(p.patrimonio)) } }
    val area = Path().apply { addPath(linhaPatrimonio); lineTo(x(pontos.lastIndex), y(0.0)); lineTo(x(0), y(0.0)); close() }
    drawPath(area, Brush.verticalGradient(listOf(cores.patrimonio.copy(alpha = 0.18f), cores.patrimonio.copy(alpha = 0f)), startY = topo, endY = y(0.0)))
    val linhaAplicado = Path().apply { pontos.forEachIndexed { i, p -> if (i == 0) moveTo(x(i), y(p.aplicado)) else lineTo(x(i), y(p.aplicado)) } }
    drawPath(linhaAplicado, cores.aplicado, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
    drawPath(linhaPatrimonio, cores.patrimonio, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))

    // Linha guia no mês tocado e marcadores (8dp, com anel da cor da superfície)
    val foco = selecionado ?: pontos.lastIndex
    if (selecionado != null) drawLine(corEixo.copy(alpha = 0.5f), Offset(x(foco), topo), Offset(x(foco), y(0.0)), strokeWidth = 1.dp.toPx())
    listOf(pontos[foco].aplicado to cores.aplicado, pontos[foco].patrimonio to cores.patrimonio).forEach { (v, cor) ->
        drawCircle(corSuperficie, radius = 6.dp.toPx(), center = Offset(x(foco), y(v)))
        drawCircle(cor, radius = 4.dp.toPx(), center = Offset(x(foco), y(v)))
    }
}
