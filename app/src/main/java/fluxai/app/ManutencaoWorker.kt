package fluxai.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import kotlinx.coroutines.tasks.await

class ManutencaoWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "fluxai_manutencao"
    }

    override suspend fun doWork(): Result {
        val user = Firebase.auth.currentUser ?: return Result.success()
        val db = Firebase.firestore

        return try {
            // Pega a lista de equipamentos/veículos do usuário
            val snapshot = db.collection("usuarios").document(workspaceAtual(applicationContext, user.uid)).collection("ativos").get().await()

            for (document in snapshot.documents) {
                val nome = document.getString("nome") ?: "Item"
                val usoAtual = document.getDouble("usoAtual") ?: 0.0
                val mtbf = document.getDouble("mtbfPreventiva") ?: 0.0

                if (mtbf > 0) {
                    val progresso = usoAtual / mtbf

                    // Se o desgaste atingiu 90%, ele dispara a notificação
                    if (progresso >= 0.9) {
                        enviarNotificacao(
                            id = document.id.hashCode(),
                            titulo = "Atenção: Manutenção Próxima 🛠️",
                            mensagem = "A preventiva de '$nome' está perto do limite. Agende uma revisão para evitar problemas!"
                        )
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("FLUXAI_MANUTENCAO", "Erro ao verificar manutenções: ${e.message}")
            Result.retry()
        }
    }

    private fun enviarNotificacao(id: Int, titulo: String, mensagem: String) {
        val context = applicationContext
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Avisos de Manutenção", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notificacao_sino)
            .setContentTitle(titulo)
            .setContentText(mensagem)
            .setStyle(NotificationCompat.BigTextStyle().bigText(mensagem))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(id, builder.build())
            }
        } else {
            notificationManager.notify(id, builder.build())
        }
    }
}