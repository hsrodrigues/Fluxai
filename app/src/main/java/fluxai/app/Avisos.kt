package fluxai.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

// =========================================================================
// AVISOS EM SEGUNDO PLANO
// Lembrete diário para registrar os gastos e alerta de orçamento por categoria.
// =========================================================================
private const val PREFS = "AppPrefs"
private const val CANAL_LEMBRETE = "fluxai_lembrete_diario"
private const val CANAL_ORCAMENTO = "fluxai_orcamento"

fun lembreteDiarioAtivo(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("lembrete_diario", false)
fun horaLembreteDiario(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("lembrete_hora", 20)

// substituir = true quando o usuário muda o horário; na abertura do app mantém o agendamento que já existe
fun agendarLembreteDiario(context: Context, substituir: Boolean = false) {
    val agora = Calendar.getInstance()
    val alvo = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, horaLembreteDiario(context)); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        if (before(agora)) add(Calendar.DAY_OF_MONTH, 1)
    }
    val tarefa = PeriodicWorkRequestBuilder<LembreteDiarioWorker>(24, TimeUnit.HOURS)
        .setInitialDelay(alvo.timeInMillis - agora.timeInMillis, TimeUnit.MILLISECONDS)
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "LembreteDiario", if (substituir) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP, tarefa
    )
}

fun configurarLembreteDiario(context: Context, ativo: Boolean, hora: Int) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean("lembrete_diario", ativo); putInt("lembrete_hora", hora) }
    if (ativo) agendarLembreteDiario(context, substituir = true)
    else WorkManager.getInstance(context).cancelUniqueWork("LembreteDiario")
}

private fun notificar(context: Context, canal: String, nomeCanal: String, id: Int, titulo: String, texto: String, abrir: String) {
    val gerenciador = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        gerenciador.createNotificationChannel(NotificationChannel(canal, nomeCanal, NotificationManager.IMPORTANCE_DEFAULT))
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return
    val intent = Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ABRIR, abrir)
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    val pendente = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    gerenciador.notify(
        id,
        NotificationCompat.Builder(context, canal)
            .setSmallIcon(R.drawable.ic_notificacao_sino)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setContentIntent(pendente)
            .setAutoCancel(true)
            .build()
    )
}

// Só lembra se nada foi registrado hoje (lançamentos novos guardam "criadoEm")
class LembreteDiarioWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val usuario = Firebase.auth.currentUser ?: return Result.success()
        val inicioDoDia = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val registrouHoje = runCatching {
            !Firebase.firestore.collection("usuarios").document(workspaceAtual(applicationContext, usuario.uid)).collection("despesas")
                .whereGreaterThanOrEqualTo("criadoEm", Timestamp(inicioDoDia.time)).limit(1).get().await().isEmpty
        }.getOrDefault(false)
        if (!registrouHoje) {
            notificar(
                applicationContext, CANAL_LEMBRETE, "Lembrete diário", 4101,
                "Registrou seus gastos hoje?", "Leva 10 segundos e mantém a previsão do mês certinha. Toque para lançar.",
                MainActivity.ABRIR_LANCAMENTO
            )
        }
        return Result.success()
    }
}

// Avisa uma vez por mês quando uma categoria passa de 80% e de 100% do limite definido na Análise BI
suspend fun verificarOrcamentos(context: Context, workspaceUid: String) {
    val banco = Firebase.firestore.collection("usuarios").document(workspaceUid)
    val limites = (banco.collection("configuracoes").document("limites").get().await().data ?: return)
        .mapValues { (it.value as? Number)?.toDouble() ?: 0.0 }
    if (limites.values.none { it > 0 }) return
    val hoje = Calendar.getInstance()
    val mesAno = "%02d/%04d".format(hoje.get(Calendar.MONTH) + 1, hoje.get(Calendar.YEAR))
    val despesas = banco.collection("despesas").whereEqualTo("mesAno", mesAno).get().await().documents.map { lerDespesa(it) }
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val moeda = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    alertasOrcamento(despesas, limites).forEach { a ->
        val faixa = if (a.estourou) 100 else 80
        val chave = "orcamento_avisado_${mesAno}_${a.categoria}_$faixa"
        if (prefs.getBoolean(chave, false)) return@forEach
        prefs.edit { putBoolean(chave, true) }
        val texto = if (a.estourou) "Você já gastou ${moeda.format(a.gasto)} em ${a.categoria}, acima do limite de ${moeda.format(a.limite)}."
        else "${a.categoria} já usou ${(a.uso * 100).toInt()}% do limite do mês (${moeda.format(a.gasto)} de ${moeda.format(a.limite)})."
        notificar(
            context, CANAL_ORCAMENTO, "Orçamento por categoria", ("orc" + a.categoria + faixa).hashCode(),
            if (a.estourou) "Limite de ${a.categoria} estourado" else "${a.categoria} perto do limite", texto, MainActivity.ABRIR_DASHBOARD
        )
    }
}
