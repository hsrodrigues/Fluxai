package fluxai.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class ServicoMensageriaFirebase : FirebaseMessagingService() {

    // Chamado quando o Firebase gera/renova o token deste aparelho.
    // Hoje os avisos são enviados em massa pelo Painel, então não precisamos salvar o token.
    override fun onNewToken(token: String) {
        super.onNewToken(token)
    }

    // Essa função é disparada na hora que a mensagem chega do servidor
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Pega o título e o texto que você vai digitar lá no Painel do Firebase
        val titulo = remoteMessage.notification?.title ?: "FluxAí"
        val corpo = remoteMessage.notification?.body ?: "Nova mensagem recebida!"

        enviarNotificacaoPush(titulo, corpo)
    }

    private fun enviarNotificacaoPush(titulo: String, mensagem: String) {
        val channelId = "fluxai_push_avisos"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Cria o canal de comunicação para Android novo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Avisos do Sistema",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Configura para abrir a MainActivity quando o usuário clicar na notificação
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notificacao_sino) // Reutilizamos o seu sino!
            .setContentTitle(titulo)
            .setContentText(mensagem)
            .setStyle(NotificationCompat.BigTextStyle().bigText(mensagem))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        // Usa o tempo atual como ID para as notificações não se sobreporem
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}