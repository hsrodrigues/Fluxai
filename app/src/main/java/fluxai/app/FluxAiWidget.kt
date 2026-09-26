package fluxai.app

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// =========================================================================
// DADOS DO WIDGET
// Ficam no estado do próprio widget (Preferences do Glance): quando mudam, o Glance redesenha sozinho.
// Uma cópia em SharedPreferences serve para preencher widgets adicionados depois.
// =========================================================================
private const val PREFS_WIDGET = "WidgetPrefs"

data class ContaWidget(val descricao: String, val valor: Double, val dia: Int, val vencida: Boolean)

data class DadosWidget(
    val temDados: Boolean,
    val mes: String,
    val sobra: Double,
    val aPagar: Double,
    val pago: Double,
    val renda: Double,
    val despesas: Double,
    val limiteDiario: Double,
    val nivel: NivelPrevisao,
    val contas: List<ContaWidget>,
    val atualizadoEm: String,
    val privado: Boolean
)

private object Chave {
    val TEM_DADOS = booleanPreferencesKey("tem_dados")
    val MES = stringPreferencesKey("mes")
    val SOBRA = doublePreferencesKey("sobra")
    val A_PAGAR = doublePreferencesKey("a_pagar")
    val PAGO = doublePreferencesKey("pago")
    val RENDA = doublePreferencesKey("renda")
    val DESPESAS = doublePreferencesKey("despesas")
    val LIMITE_DIARIO = doublePreferencesKey("limite_diario")
    val NIVEL = stringPreferencesKey("nivel")
    val CONTAS = stringPreferencesKey("contas")
    val ATUALIZADO_EM = stringPreferencesKey("atualizado_em")
    val PRIVADO = booleanPreferencesKey("privado")
}

// "|" separa contas e ";" separa campos; tira esses caracteres da descrição para não quebrar o formato
private fun contasParaTexto(contas: List<ContaWidget>) = contas.take(3).joinToString("|") { c ->
    "${c.descricao.replace("|", " ").replace(";", " ")};${c.valor};${c.dia};${c.vencida}"
}

private fun textoParaContas(txt: String) = txt.split("|").mapNotNull { linha ->
    val c = linha.split(";")
    if (c.size < 4) null else ContaWidget(c[0], c[1].toDoubleOrNull() ?: 0.0, c[2].toIntOrNull() ?: 0, c[3].toBoolean())
}

private fun Preferences.paraDados() = DadosWidget(
    temDados = this[Chave.TEM_DADOS] ?: false,
    mes = this[Chave.MES] ?: "",
    sobra = this[Chave.SOBRA] ?: 0.0,
    aPagar = this[Chave.A_PAGAR] ?: 0.0,
    pago = this[Chave.PAGO] ?: 0.0,
    renda = this[Chave.RENDA] ?: 0.0,
    despesas = this[Chave.DESPESAS] ?: 0.0,
    limiteDiario = this[Chave.LIMITE_DIARIO] ?: 0.0,
    nivel = runCatching { NivelPrevisao.valueOf(this[Chave.NIVEL] ?: "INFO") }.getOrDefault(NivelPrevisao.INFO),
    contas = textoParaContas(this[Chave.CONTAS] ?: ""),
    atualizadoEm = this[Chave.ATUALIZADO_EM] ?: "",
    privado = this[Chave.PRIVADO] ?: false
)

// Copia os dados de SharedPreferences para o estado do widget (mantém a escolha do olho de cada widget)
private fun MutablePreferences.copiarDe(sp: android.content.SharedPreferences) {
    if (!sp.getBoolean("tem_dados", false)) return
    fun d(k: String) = sp.getString(k, null)?.toDoubleOrNull() ?: 0.0
    this[Chave.TEM_DADOS] = true
    this[Chave.MES] = sp.getString("mes", "") ?: ""
    this[Chave.SOBRA] = d("sobra")
    this[Chave.A_PAGAR] = d("a_pagar")
    this[Chave.PAGO] = d("pago")
    this[Chave.RENDA] = d("renda")
    this[Chave.DESPESAS] = d("despesas")
    this[Chave.LIMITE_DIARIO] = d("limite_diario")
    this[Chave.NIVEL] = sp.getString("nivel", "INFO") ?: "INFO"
    this[Chave.CONTAS] = sp.getString("contas", "") ?: ""
    this[Chave.ATUALIZADO_EM] = sp.getString("atualizado_em", "") ?: ""
}

// Último resumo do mês salvo pelo Dashboard (usado também no menu lateral); null se ainda não houver
fun lerResumoSalvo(context: Context): DadosWidget? {
    val sp = context.getSharedPreferences(PREFS_WIDGET, Context.MODE_PRIVATE)
    if (!sp.getBoolean("tem_dados", false)) return null
    fun d(k: String) = sp.getString(k, null)?.toDoubleOrNull() ?: 0.0
    return DadosWidget(
        temDados = true,
        mes = sp.getString("mes", "") ?: "",
        sobra = d("sobra"), aPagar = d("a_pagar"), pago = d("pago"), renda = d("renda"), despesas = d("despesas"),
        limiteDiario = d("limite_diario"),
        nivel = runCatching { NivelPrevisao.valueOf(sp.getString("nivel", "INFO") ?: "INFO") }.getOrDefault(NivelPrevisao.INFO),
        contas = textoParaContas(sp.getString("contas", "") ?: ""),
        atualizadoEm = sp.getString("atualizado_em", "") ?: "",
        privado = false
    )
}

suspend fun salvarDadosWidget(
    context: Context, mes: String, sobra: Double, aPagar: Double, pago: Double, renda: Double, despesas: Double,
    limiteDiario: Double, nivel: NivelPrevisao, contas: List<ContaWidget>
) {
    val sp = context.getSharedPreferences(PREFS_WIDGET, Context.MODE_PRIVATE)
    sp.edit()
        .putBoolean("tem_dados", true)
        .putString("mes", mes)
        .putString("sobra", sobra.toString())
        .putString("a_pagar", aPagar.toString())
        .putString("pago", pago.toString())
        .putString("renda", renda.toString())
        .putString("despesas", despesas.toString())
        .putString("limite_diario", limiteDiario.toString())
        .putString("nivel", nivel.name)
        .putString("contas", contasParaTexto(contas))
        .putString("atualizado_em", SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date()))
        .commit()

    GlanceAppWidgetManager(context).getGlanceIds(FluxAiWidget::class.java).forEach { id ->
        updateAppWidgetState(context, id) { it.copiarDe(sp) }
        FluxAiWidget().update(context, id)
    }
}

// Botão do olho: esconde/mostra os valores deste widget
class AlternarPrivacidadeWidget : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { it[Chave.PRIVADO] = !(it[Chave.PRIVADO] ?: false) }
        FluxAiWidget().update(context, glanceId)
    }
}

class FluxAiWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FluxAiWidget()
}

// =========================================================================
// VISUAL
// =========================================================================
private val Branco = ColorProvider(Color.White)
private val BrancoSuave = ColorProvider(Color.White.copy(alpha = 0.65f))
private val BrancoFraco = ColorProvider(Color.White.copy(alpha = 0.45f))
private val Verde = Color(0xFF66BB6A)
private val Vermelho = Color(0xFFEF5350)

private fun corNivel(n: NivelPrevisao) = when (n) {
    NivelPrevisao.OK -> Verde
    NivelPrevisao.ATENCAO -> Color(0xFFFFA726)
    NivelPrevisao.RISCO -> Vermelho
    NivelPrevisao.INFO -> Color(0xFF64B5F6)
}

private fun textoNivel(n: NivelPrevisao) = when (n) {
    NivelPrevisao.OK -> "Ritmo saudável"
    NivelPrevisao.ATENCAO -> "Margem apertada"
    NivelPrevisao.RISCO -> "Atenção ao orçamento"
    NivelPrevisao.INFO -> "Mês começando"
}

private val moeda = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
private fun brl(v: Double, privado: Boolean) = if (privado) "R$ •••••" else moeda.format(v).replace(Char(0xA0), ' ')

class FluxAiWidget : GlanceAppWidget() {
    companion object {
        private val PEQUENO = DpSize(120.dp, 100.dp)
        private val MEDIO = DpSize(250.dp, 110.dp)
        private val GRANDE = DpSize(250.dp, 230.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(PEQUENO, MEDIO, GRANDE))

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Widget recém-adicionado: começa com o último resumo salvo pelo app
        val sp = context.getSharedPreferences(PREFS_WIDGET, Context.MODE_PRIVATE)
        updateAppWidgetState(context, id) { if (it[Chave.TEM_DADOS] != true) it.copiarDe(sp) }
        provideContent { Conteudo(currentState<Preferences>().paraDados()) }
    }

    @Composable
    private fun Conteudo(dados: DadosWidget) {
        val context = LocalContext.current
        val tamanho = LocalSize.current
        val abrirApp = actionStartActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP))

        Column(
            modifier = GlanceModifier.fillMaxSize()
                .background(ImageProvider(R.drawable.widget_fundo))
                .cornerRadius(24.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .clickable(abrirApp)
        ) {
            Cabecalho(dados)
            Spacer(GlanceModifier.height(8.dp))

            if (!dados.temDados) {
                Text("Abra o app para carregar seu resumo do mês.", style = TextStyle(color = BrancoSuave, fontSize = 13.sp), maxLines = 3)
                Spacer(GlanceModifier.defaultWeight())
                BotaoLancar()
                return@Column
            }

            // Coluna própria para o conteúdo: mantém cada nível abaixo do limite de 10 filhos do Glance
            Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                when {
                    tamanho.width < MEDIO.width -> LayoutPequeno(dados)
                    tamanho.height < GRANDE.height -> LayoutMedio(dados)
                    else -> LayoutGrande(dados)
                }
            }
        }
    }

    @Composable
    private fun Cabecalho(dados: DadosWidget) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(ImageProvider(R.drawable.widget_logo), contentDescription = "FluxAí", modifier = GlanceModifier.size(20.dp))
            Spacer(GlanceModifier.width(6.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text("FluxAí", style = TextStyle(color = Branco, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                if (dados.mes.isNotEmpty()) Text(dados.mes, style = TextStyle(color = BrancoFraco, fontSize = 10.sp), maxLines = 1)
            }
            Image(
                ImageProvider(if (dados.privado) R.drawable.ic_widget_olho_fechado else R.drawable.ic_widget_olho),
                contentDescription = if (dados.privado) "Mostrar valores" else "Esconder valores",
                modifier = GlanceModifier.size(30.dp).padding(5.dp).clickable(actionRunCallback<AlternarPrivacidadeWidget>())
            )
        }
    }

    @Composable
    private fun BlocoSobra(dados: DadosWidget, tamanhoValor: Int) {
        // Glance aceita no máximo 10 filhos diretos por Column/Row: agrupar evita itens descartados
        Column {
            Text("Sobra do mês", style = TextStyle(color = BrancoSuave, fontSize = 11.sp))
            Text(
                brl(dados.sobra, dados.privado),
                style = TextStyle(color = if (dados.sobra < 0 && !dados.privado) ColorProvider(Vermelho) else Branco, fontSize = tamanhoValor.sp, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = GlanceModifier.size(7.dp).cornerRadius(4.dp).background(corNivel(dados.nivel))) {}
                Spacer(GlanceModifier.width(5.dp))
                Text(textoNivel(dados.nivel), style = TextStyle(color = ColorProvider(corNivel(dados.nivel)), fontSize = 11.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        }
        }
    }

    @Composable
    private fun ColumnScope.LayoutPequeno(dados: DadosWidget) {
        BlocoSobra(dados, 20)
        Spacer(GlanceModifier.defaultWeight())
        if (dados.limiteDiario > 0) {
            Text("${brl(dados.limiteDiario, dados.privado)}/dia", style = TextStyle(color = BrancoSuave, fontSize = 11.sp), maxLines = 1)
        }
    }

    @Composable
    private fun ColumnScope.LayoutMedio(dados: DadosWidget) {
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            Column(modifier = GlanceModifier.defaultWeight()) { BlocoSobra(dados, 22) }
            Spacer(GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                MiniInfo("A pagar", brl(dados.aPagar, dados.privado), Color(0xFFFFB74D))
                Spacer(GlanceModifier.height(6.dp))
                MiniInfo("Pode gastar/dia", brl(dados.limiteDiario, dados.privado), Color(0xFF81C784))
            }
        }
        BotaoLancar()
    }

    @Composable
    private fun ColumnScope.LayoutGrande(dados: DadosWidget) {
        BlocoSobra(dados, 28)
        Spacer(GlanceModifier.height(10.dp))

        // Quanto da renda já está comprometido com despesas
        val comprometido = if (dados.renda > 0) (dados.despesas / dados.renda).toFloat().coerceIn(0f, 1f) else 0f
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text("Renda comprometida", style = TextStyle(color = BrancoSuave, fontSize = 11.sp), modifier = GlanceModifier.defaultWeight())
                Text(if (dados.privado) "••%" else "${(comprometido * 100).toInt()}%", style = TextStyle(color = Branco, fontSize = 11.sp, fontWeight = FontWeight.Bold))
            }
            Spacer(GlanceModifier.height(4.dp))
            LinearProgressIndicator(
                progress = comprometido,
                modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
                color = ColorProvider(if (comprometido > 0.9f) Vermelho else if (comprometido > 0.75f) Color(0xFFFFA726) else Color(0xFFB39DDB)),
                backgroundColor = ColorProvider(Color.White.copy(alpha = 0.12f))
            )
        }
        Spacer(GlanceModifier.height(10.dp))

        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Cartao(GlanceModifier.defaultWeight(), "A pagar", brl(dados.aPagar, dados.privado), Color(0xFFFFB74D))
            Spacer(GlanceModifier.width(8.dp))
            Cartao(GlanceModifier.defaultWeight(), "Pago", brl(dados.pago, dados.privado), Verde)
        }

        if (dados.contas.isNotEmpty()) {
            Column(modifier = GlanceModifier.fillMaxWidth().padding(top = 10.dp)) {
                Text("Próximas contas", style = TextStyle(color = BrancoSuave, fontSize = 11.sp, fontWeight = FontWeight.Medium))
                Spacer(GlanceModifier.height(4.dp))
                dados.contas.take(3).forEach { conta ->
                    Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (conta.vencida) "Venceu" else "Dia ${conta.dia}",
                            style = TextStyle(color = ColorProvider(if (conta.vencida) Vermelho else Color(0xFFB39DDB)), fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            modifier = GlanceModifier.width(50.dp)
                        )
                        Text(conta.descricao, style = TextStyle(color = Branco, fontSize = 12.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                        Text(brl(conta.valor, dados.privado), style = TextStyle(color = Branco, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                    }
                }
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (dados.atualizadoEm.isNotEmpty()) {
                Text("Atualizado às ${dados.atualizadoEm}", style = TextStyle(color = BrancoFraco, fontSize = 10.sp), modifier = GlanceModifier.defaultWeight())
            } else Spacer(GlanceModifier.defaultWeight())
            BotaoLancar(compacto = true)
        }
    }

    @Composable
    private fun MiniInfo(titulo: String, valor: String, cor: Color) {
        Text(titulo, style = TextStyle(color = BrancoSuave, fontSize = 10.sp), maxLines = 1)
        Text(valor, style = TextStyle(color = ColorProvider(cor), fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
    }

    @Composable
    private fun Cartao(modifier: GlanceModifier, titulo: String, valor: String, cor: Color) {
        Column(modifier = modifier.background(ImageProvider(R.drawable.widget_cartao)).cornerRadius(14.dp).padding(horizontal = 10.dp, vertical = 8.dp)) {
            MiniInfo(titulo, valor, cor)
        }
    }

    @Composable
    private fun BotaoLancar(compacto: Boolean = false) {
        val context = LocalContext.current
        // Abre o app direto na tela de Novo Lançamento
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_ABRIR, MainActivity.ABRIR_LANCAMENTO)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        Row(
            modifier = (if (compacto) GlanceModifier else GlanceModifier.fillMaxWidth())
                .background(ImageProvider(R.drawable.widget_botao))
                .cornerRadius(14.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .clickable(actionStartActivity(intent)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(ImageProvider(R.drawable.ic_widget_add), contentDescription = null, modifier = GlanceModifier.size(16.dp))
            Spacer(GlanceModifier.width(4.dp))
            Text("Lançar", style = TextStyle(color = Branco, fontSize = 13.sp, fontWeight = FontWeight.Bold))
        }
    }
}
