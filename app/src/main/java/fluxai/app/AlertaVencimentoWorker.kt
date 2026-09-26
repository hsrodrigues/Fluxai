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
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AlertaVencimentoWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "fluxai_vencimentos"
        const val TAG_DEBUG = "FLUXAI_DEBUG"
    }

    override suspend fun doWork(): Result {
        val usuario = Firebase.auth.currentUser ?: run {
            Log.d(TAG_DEBUG, "Worker encerrado: Usuário deslogado.")
            return Result.success()
        }
        val db = Firebase.firestore

        val calAmanha = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }

        // MELHORIA 1: Mantido como Int para bater EXATAMENTE com o tipo salvo no HomeScreen
        val diaBusca = calAmanha.get(Calendar.DAY_OF_MONTH)
        val mesAnoBusca = SimpleDateFormat("MM/yyyy", Locale("pt", "BR")).format(calAmanha.time)

        Log.d(TAG_DEBUG, "Buscando: Dia $diaBusca (Int) e Mês $mesAnoBusca (String)")

        return try {
            val snapshot = db.collection("usuarios")
                .document(workspaceAtual(applicationContext, usuario.uid)) // conta conjunta: avisa das contas do dono
                .collection("despesas")
                .whereEqualTo("mesAno", mesAnoBusca)
                .whereEqualTo("diaVencimento", diaBusca) // Busca usando Int
                .whereEqualTo("status", "A pagar")
                .get()
                .await()

            Log.d(TAG_DEBUG, "Consulta finalizada. Contas encontradas: ${snapshot.size()}")

            if (!snapshot.isEmpty) {
                snapshot.documents.forEach { doc ->
                    val nome = doc.getString("descricao") ?: "Conta"
                    val valor = doc.getDouble("valor") ?: 0.0

                    Log.d(TAG_DEBUG, "Enviando notificação para: $nome")
                    enviarNotificacao(
                        id = doc.id.hashCode(),
                        titulo = "Vencimento Amanhã! 💸",
                        mensagem = "Sua conta '$nome' de R$ %.2f vence amanhã.".format(valor)
                    )
                }
            } else {
                Log.d(TAG_DEBUG, "Nenhuma conta pendente para amanhã.")
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG_DEBUG, "ERRO NO WORKER: ${e.message}")

            // MELHORIA 2: Trava de segurança para não drenar bateria em caso de falta de índice
            if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                Log.e(TAG_DEBUG, "Falta criar o índice no Firebase! Clique no link do erro acima no Logcat para criar. Parando o Worker para economizar bateria.")
                return Result.failure()
            }

            // Se for outro erro (ex: sem internet), ele tenta de novo mais tarde
            Result.retry()
        }
    }

    private fun enviarNotificacao(id: Int, titulo: String, mensagem: String) {
        val context = applicationContext
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Alertas de Contas", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notifica sobre contas que vencem amanhã"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        val pendingIntent = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notificacao_sino) // Garanta que este ícone existe na pasta drawable
            .setContentTitle(titulo)
            .setContentText(mensagem)
            .setStyle(NotificationCompat.BigTextStyle().bigText(mensagem))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(id, builder.build())
            } else {
                Log.d(TAG_DEBUG, "Notificação bloqueada: Falta permissão POST_NOTIFICATIONS")
            }
        } else {
            notificationManager.notify(id, builder.build())
        }
    }
}