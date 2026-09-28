package fluxai.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

// =========================================================================
// JUROS DOS BANCOS
// Ranking semanal do Banco Central por modalidade (do mais barato ao mais caro),
// a taxa média do mercado e onde a sua taxa fica nesse ranking.
// =========================================================================

// Pedido vindo de outra tela (ex.: cadastro de empréstimo): modalidade e taxa ao mês para comparar
var pedidoComparacaoJuros: Pair<String, Double?>? = null

private fun pctBR(v: Double, casas: Int = 2) = String.format(Locale("pt", "BR"), "%,.${casas}f%%", v)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JurosBancosScreen(onLogout: () -> Unit) {
    val c = coresTela()
    val pedido = remember { pedidoComparacaoJuros.also { pedidoComparacaoJuros = null } }
    var modalidade by remember { mutableStateOf(pedido?.first?.let { r -> ModalidadesCredito.firstOrNull { it.rotulo == r } } ?: ModalidadesCredito.first()) }
    var suaTaxa by remember { mutableStateOf(pedido?.second?.let { String.format(Locale("pt", "BR"), "%.2f", it) } ?: "") }
    var busca by remember { mutableStateOf("") }
    var ranking by remember { mutableStateOf<RankingJuros?>(null) }
    var media by remember { mutableStateOf<Pair<String, Double>?>(null) }
    var carregando by remember { mutableStateOf(true) }
    var falhou by remember { mutableStateOf(false) }

    LaunchedEffect(modalidade) {
        carregando = true; falhou = false; ranking = null; media = null
        media = BancoCentral.jurosMedio(modalidade)
        ranking = BancoCentral.ranking(modalidade)
        falhou = ranking == null
        carregando = false
    }

    TelaComMenu(titulo = "Juros dos bancos", rota = "juros", onLogout = onLogout) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("Compare quanto cada banco cobra, com os dados oficiais do Banco Central.", fontSize = 13.sp, color = c.textoFraco, lineHeight = 18.sp)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModalidadesCredito.forEach { m ->
                        FilterChip(
                            selected = modalidade == m, onClick = { modalidade = m }, label = { Text(m.rotulo) }, shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.destaque.copy(alpha = 0.14f), selectedLabelColor = c.destaque)
                        )
                    }
                }
            }

            item {
                Surface(shape = RoundedCornerShape(20.dp), color = c.superficie, border = BorderStroke(1.dp, c.divisor), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Média do mercado · ${modalidade.rotulo}", fontSize = 12.sp, color = c.textoFraco)
                        val m = media
                        if (m == null) Text(if (carregando) "Carregando..." else "Indisponível agora", fontSize = 16.sp, color = c.textoFraco)
                        else {
                            Text("${pctBR(anualParaMensal(m.second))} ao mês", fontSize = 26.sp, fontWeight = FontWeight.Black, color = c.texto)
                            Text("${pctBR(m.second)} ao ano · referência ${m.first.substring(3)}", fontSize = 12.sp, color = c.textoFraco)
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = suaTaxa, onValueChange = { suaTaxa = it.replace('.', ',') }, singleLine = true,
                            label = { Text("Sua taxa ao mês (opcional)") }, suffix = { Text("% a.m.") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(), shape = FormatoCampo, colors = coresCampoTela(c)
                        )
                        val taxa = suaTaxa.paraValor()
                        val r = ranking
                        if (taxa != null && taxa > 0 && r != null) {
                            val maisBaratos = r.bancos.count { it.taxaMes < taxa }
                            val melhor = r.bancos.first()
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Sua taxa equivale a ${pctBR(mensalParaAnual(taxa))} ao ano. " +
                                    if (maisBaratos == 0) "Nenhum banco da lista cobra menos. Ótima taxa!"
                                    else "$maisBaratos de ${r.bancos.size} bancos cobram menos; o mais barato é ${melhor.banco} (${pctBR(melhor.taxaMes)} ao mês).",
                                fontSize = 13.sp, lineHeight = 18.sp,
                                color = if (maisBaratos <= r.bancos.size / 4) Color(0xFF43A047) else if (maisBaratos >= r.bancos.size / 2) Color(0xFFE53935) else c.texto
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = busca, onValueChange = { busca = it }, singleLine = true, placeholder = { Text("Procurar banco") },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = c.textoFraco) },
                    modifier = Modifier.fillMaxWidth(), shape = FormatoCampo, colors = coresCampoTela(c)
                )
            }

            val r = ranking
            when {
                carregando -> item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.destaque) } }
                falhou || r == null -> item { Text("Não foi possível carregar o ranking agora. Tente de novo mais tarde.", color = c.textoFraco, modifier = Modifier.padding(8.dp)) }
                else -> {
                    item {
                        Text(
                            "Semana de ${isoParaBr(r.inicio)} a ${isoParaBr(r.fim)} · do mais barato ao mais caro",
                            fontSize = 12.sp, color = c.textoFraco, modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    }
                    val lista = r.bancos.filter { busca.isBlank() || it.banco.contains(busca.trim(), ignoreCase = true) }
                    if (lista.isEmpty()) item { Text("Nenhum banco com esse nome nesta modalidade.", color = c.textoFraco, modifier = Modifier.padding(8.dp)) }
                    items(lista, key = { "${it.posicao}_${it.banco}" }) { b ->
                        val destaque = b.posicao <= 3
                        Surface(shape = RoundedCornerShape(14.dp), color = c.superficie, border = BorderStroke(1.dp, if (destaque) Color(0xFF43A047).copy(alpha = 0.4f) else c.divisor), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(30.dp).background(if (destaque) Color(0xFF43A047).copy(alpha = 0.14f) else c.divisor.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) {
                                    Text("${b.posicao}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (destaque) Color(0xFF43A047) else c.textoFraco)
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(b.banco, color = c.texto, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${pctBR(b.taxaMes)} a.m.", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.texto)
                                    Text("${pctBR(b.taxaAno)} a.a.", fontSize = 11.sp, color = c.textoFraco)
                                }
                            }
                        }
                    }
                    item {
                        Text(
                            "Fonte: Banco Central do Brasil (taxas médias praticadas por cada instituição na semana). A taxa oferecida a você pode ser diferente, conforme seu perfil e garantias.",
                            fontSize = 11.sp, color = c.textoFraco, lineHeight = 15.sp, modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}
