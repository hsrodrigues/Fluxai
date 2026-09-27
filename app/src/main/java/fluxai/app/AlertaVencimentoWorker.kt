package fluxai.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

// =========================================================================
// AVISO DE CONTAS A VENCER
// Roda todo dia às 8h: junta numa notificação só as contas vencidas, as de hoje,
// as de amanhã e as dos próximos dias. Com uma conta só, dá para marcar como paga
// direto pela notificação.
// =========================================================================
data class ContaAVencer(val despesa: Despesa, val dias: Int) // dias < 0 = vencida há X dias

// Contas a pagar que vencem até "janela" dias à frente (e as já vencidas), mais urgentes primeiro
fun contasAVencer(despesas: List<Despesa>, hoje: Calendar, janela: Int = 3): List<ContaAVencer> {
    val inicioHoje = (hoje.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    return despesas.filter { it.status == "A pagar" && it.projetoId == null && it.diaVencimento > 0 }.mapNotNull { d ->
        val (mes, ano) = d.mesAno.split("/").map { it.toIntOrNull() ?: return@mapNotNull null }
        val venc = (inicioHoje.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.YEAR, ano); set(Calendar.MONTH, mes - 1)
            set(Calendar.DAY_OF_MONTH, d.diaVencimento.coerceAtMost(getActualMaximum(Calendar.DAY_OF_MONTH)))
        }
        val dias = Math.round((venc.timeInMillis - inicioHoje.timeInMillis) / TimeUnit.DAYS.toMillis(1).toDouble()).toInt()
        if (dias <= janela) ContaAVencer(d, dias) else null
    }.sortedWith(compareBy({ it.dias }, { -it.despesa.valor }))
}

fun quandoVence(dias: Int) = when {
    dias < -1 -> "venceu há ${-dias} dias"
    dias == -1 -> "venceu ontem"
    dias == 0 -> "vence hoje"
    dias == 1 -> "vence amanhã"
    else -> "vence em $dias dias"
}

class AlertaVencimentoWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "fluxai_vencimentos"
        const val TAG_DEBUG = "FLUXAI_DEBUG"
        const val ID_NOTIFICACAO = 5001
    }

    override suspend fun doWork(): Result {
        val usuario = Firebase.auth.currentUser ?: return Result.success()
        val workspace = workspaceAtual(applicationContext, usuario.uid)
        val hoje = Calendar.getInstance()
        // Mês atual e, perto da virada, o seguinte (conta do dia 1º aparece no fim do mês)
        val meses = listOf(0, 1).map { soma ->
            val c = (hoje.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, soma) }
            "%02d/%04d".format(c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR))
        }

        return try {
            // Só filtro por mês: dispensa índice composto no Firestore
            val despesas = Firebase.firestore.collection("usuarios").document(workspace).collection("despesas")
                .whereIn("mesAno", meses).get().await().documents.map { lerDespesa(it) }
            val contas = contasAVencer(despesas, hoje)
            if (contas.isNotEmpty()) avisar(contas, workspace)
            else (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(ID_NOTIFICACAO)

            // Mesma rodada diária: avisa categorias perto ou acima do limite do mês
            runCatching { verificarOrcamentos(applicationContext, workspace) }
                .onFailure { Log.e(TAG_DEBUG, "Falha ao verificar orçamentos: ${it.message}") }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG_DEBUG, "Erro no aviso de vencimentos: ${e.message}")
            Result.retry()
        }
    }

    private fun avisar(contas: List<ContaAVencer>, workspace: String) {
        val context = applicationContext
        val gerenciador = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            gerenciador.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Alertas de Contas", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Contas vencidas e que vencem nos próximos dias"
            })
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val moeda = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
        val vencidas = contas.filter { it.dias < 0 }
        val total = contas.sumOf { it.despesa.valor }
        val titulo = if (contas.size == 1) {
            val c = contas.first()
            "${c.despesa.descricao} ${quandoVence(c.dias)}"
        } else buildString {
            append("${contas.size} contas pedem atenção")
            if (vencidas.isNotEmpty()) append(" (${vencidas.size} vencida${if (vencidas.size > 1) "s" else ""})")
        }
        val resumo = if (contas.size == 1) "${moeda.format(total)}. Toque para abrir o FluxAí." else "Total ${moeda.format(total)}"

        val abrir = PendingIntent.getActivity(
            context, ID_NOTIFICACAO,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ABRIR, MainActivity.ABRIR_DASHBOARD)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val estilo = NotificationCompat.InboxStyle().setBigContentTitle(titulo).setSummaryText(resumo)
        contas.take(6).forEach { c ->
            estilo.addLine("${quandoVence(c.dias).replaceFirstChar { it.uppercase() }} · ${c.despesa.descricao} · ${moeda.format(c.despesa.valor)}")
        }
        if (contas.size > 6) estilo.addLine("e mais ${contas.size - 6}...")

        val aviso = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notificacao_sino)
            .setContentTitle(titulo)
            .setContentText(if (contas.size == 1) resumo else contas.take(3).joinToString(", ") { it.despesa.descricao })
            .setStyle(estilo)
            .setNumber(contas.size)
            .setColor(if (vencidas.isNotEmpty()) 0xFFE53935.toInt() else 0xFF7E57C2.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(abrir)
            .setAutoCancel(true)

        // Uma conta só: botão para dar baixa sem abrir o app (compras de cartão continuam pelo pagamento da fatura)
        contas.singleOrNull()?.despesa?.takeIf { it.cartaoId.isNullOrBlank() }?.let { d ->
            val pagar = PendingIntent.getBroadcast(
                context, d.id.hashCode(),
                Intent(context, AcaoContaReceiver::class.java).putExtra("despesaId", d.id).putExtra("workspace", workspace),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            aviso.addAction(R.drawable.ic_notificacao_sino, "Já paguei", pagar)
        }
        gerenciador.notify(ID_NOTIFICACAO, aviso.build())
    }
}

// Botão "Já paguei" da notificação: marca a conta como paga (ajustando o saldo da conta bancária, se houver)
class AcaoContaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val despesaId = intent.getStringExtra("despesaId") ?: return
        val workspace = intent.getStringExtra("workspace") ?: return
        if (Firebase.auth.currentUser == null) return
        val pendente = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val doc = Firebase.firestore.collection("usuarios").document(workspace).collection("despesas").document(despesaId).get().await()
                if (doc.exists()) mudarStatusDespesa(workspace, lerDespesa(doc), "Pago")
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(AlertaVencimentoWorker.ID_NOTIFICACAO)
            } catch (e: Exception) {
                Log.e(AlertaVencimentoWorker.TAG_DEBUG, "Falha ao marcar como paga: ${e.message}")
            } finally {
                pendente.finish()
            }
        }
    }
}
