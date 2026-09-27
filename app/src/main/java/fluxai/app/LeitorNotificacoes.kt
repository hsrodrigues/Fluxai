package fluxai.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

// =========================================================================
// LANÇAMENTO AUTOMÁTICO PELAS NOTIFICAÇÕES DO BANCO
// Lê só notificações de apps de bancos conhecidos, extrai valor e estabelecimento
// e guarda uma sugestão em usuarios/{conta}/compras_detectadas. Nada vira despesa
// sem o usuário confirmar no Dashboard. O texto original da notificação não é salvo.
// =========================================================================
data class CompraDetectada(val descricao: String, val valor: Double, val banco: String, val noCartao: Boolean)

val BancosConhecidos = mapOf(
    "com.nu.production" to "Nubank",
    "br.com.intermedium" to "Inter",
    "com.c6bank.app" to "C6 Bank",
    "com.itau" to "Itaú",
    "com.itau.iti" to "iti",
    "com.bradesco" to "Bradesco",
    "br.com.bradesco.next" to "Next",
    "com.santander.app" to "Santander",
    "br.com.bb.android" to "Banco do Brasil",
    "br.gov.caixa.tem" to "Caixa Tem",
    "br.com.gabba.Caixa" to "Caixa",
    "com.picpay" to "PicPay",
    "com.mercadopago.wallet" to "Mercado Pago",
    "br.com.uol.ps.myaccount" to "PagBank",
    "br.com.neon" to "Neon",
    "com.btg.pactual.banking" to "BTG",
    "br.com.original.bank" to "Original",
    "com.sicredi.app" to "Sicredi",
    "br.com.sicoobnet" to "Sicoob"
)

private val regexValor = Regex("""R\$\s?(-?[\d.]+,\d{2})""")
private val palavrasGasto = listOf("compra", "pagamento", "pix enviado", "pix realizado", "transferência enviada", "transferencia enviada", "você pagou", "voce pagou", "você enviou", "voce enviou", "débito", "debito", "saque")
private val palavrasIgnorar = listOf("recebeu", "recebido", "recebida", "estorno", "estornad", "reembolso", "cashback", "rendeu", "rendimento", "fatura fechou", "fatura fechada", "pagamento da fatura", "fatura paga", "negada", "recusada", "não autorizada", "nao autorizada", "cancelada", "agendad", "código", "codigo de verificação", "token")
// Termina o nome do estabelecimento: "... em IFOOD para o cartão final 1234", "... no MERCADO X em 12/03"
private val regexEstabelecimento = Regex(
    """(?i)\b(?:em|no|na|para|p/)\s+(.+?)(?=\s+(?:(?:para o|pelo|pela|no cartão|no cartao|com o cartão|com cartão|com o|às|foi|final|no dia|no valor|realizad|aprovad|via)\b|(?:em|as)\s+\d)|[.;,!\n]|$)"""
)

fun interpretarNotificacaoBanco(pacote: String, titulo: String, texto: String): CompraDetectada? {
    val banco = BancosConhecidos[pacote] ?: return null
    val completo = "$titulo. $texto".replace(' ', ' ')
    val minusculo = completo.lowercase()
    if (palavrasIgnorar.any { it in minusculo }) return null
    if (palavrasGasto.none { it in minusculo }) return null

    val matchValor = regexValor.find(completo) ?: return null
    val valor = matchValor.groupValues[1].paraValor()?.let { kotlin.math.abs(it) } ?: return null
    if (valor <= 0) return null

    // Procura o estabelecimento primeiro depois do valor, depois no texto todo
    val depois = completo.substring(matchValor.range.last + 1)
    val candidato = (regexEstabelecimento.find(depois) ?: regexEstabelecimento.find(completo))
        ?.groupValues?.get(1)?.trim()
        ?.takeIf { it.length in 2..60 && regexValor.find(it) == null && !it.lowercase().startsWith("seu ") && !it.lowercase().startsWith("sua ") }
    val descricao = candidato?.let { formatarNomeProprio(it) } ?: "Compra $banco"
    val noCartao = "cartão" in minusculo || "cartao" in minusculo || "crédito" in minusculo || "credito" in minusculo
    return CompraDetectada(descricao, valor, banco, noCartao)
}

fun leituraNotificacoesAtiva(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

fun abrirPermissaoNotificacoes(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(context, LeitorNotificacoesBanco::class.java).flattenToString())
    } else Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

class LeitorNotificacoesBanco : NotificationListenerService() {
    companion object {
        private const val CANAL = "fluxai_compras_detectadas"
        private const val PREFS = "LeitorBanco"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in BancosConhecidos) return
        val usuario = Firebase.auth.currentUser ?: return
        val extras = sbn.notification.extras
        val titulo = extras.getCharSequence("android.title")?.toString() ?: ""
        val texto = (extras.getCharSequence("android.bigText") ?: extras.getCharSequence("android.text"))?.toString() ?: ""
        val compra = interpretarNotificacaoBanco(sbn.packageName, titulo, texto) ?: return

        // O mesmo aviso costuma chegar mais de uma vez (atualização da notificação): ignora repetições por 10 minutos
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val chave = "${compra.banco}|${compra.valor}|${compra.descricao}"
        val agora = System.currentTimeMillis()
        if (agora - prefs.getLong(chave, 0L) < 10 * 60 * 1000L) return
        prefs.edit().apply {
            // Limpa entradas antigas para o arquivo não crescer para sempre
            prefs.all.filter { (_, v) -> v is Long && agora - v > 24 * 60 * 60 * 1000L }.keys.forEach { remove(it) }
            putLong(chave, agora)
        }.apply()

        val hoje = Calendar.getInstance()
        Firebase.firestore.collection("usuarios").document(workspaceAtual(this, usuario.uid)).collection("compras_detectadas").add(
            mapOf(
                "descricao" to compra.descricao,
                "valor" to compra.valor,
                "banco" to compra.banco,
                "noCartao" to compra.noCartao,
                "categoria" to sugerirCategoria(compra.descricao),
                "dia" to hoje.get(Calendar.DAY_OF_MONTH),
                "mesAno" to "%02d/%04d".format(hoje.get(Calendar.MONTH) + 1, hoje.get(Calendar.YEAR)),
                "detectadaEm" to Timestamp.now(),
                "detectadaPor" to usuario.uid
            )
        )
        avisar(compra)
    }

    private fun avisar(compra: CompraDetectada) {
        val gerenciador = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            gerenciador.createNotificationChannel(NotificationChannel(CANAL, "Compras detectadas", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Avisa quando uma compra do banco pode ser registrada no FluxAí"
            })
        }
        val abrir = PendingIntent.getActivity(
            this, 7001,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_ABRIR, MainActivity.ABRIR_DASHBOARD).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val valor = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(compra.valor)
        val aviso = NotificationCompat.Builder(this, CANAL)
            .setSmallIcon(R.drawable.ic_notificacao_sino)
            .setContentTitle("Compra detectada: $valor")
            .setContentText("${compra.descricao} (${compra.banco}). Toque para registrar no FluxAí.")
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .build()
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            runCatching { gerenciador.notify(compra.hashCode(), aviso) }
        }
    }
}
