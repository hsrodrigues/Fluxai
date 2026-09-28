package fluxai.app

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

// Última versão publicada no site do FluxAí (Firebase Hosting): versao.json + APK em /download
data class VersaoRemota(val versao: Long, val urlApk: String)

private const val URL_SITE = "https://fluxai-adbdf.web.app"

// Chamar fora da thread principal. Retorna null se não houver versão publicada ou der erro de rede.
fun buscarUltimaVersao(): VersaoRemota? = try {
    val conn = java.net.URL("$URL_SITE/versao.json").openConnection() as java.net.HttpURLConnection
    conn.useCaches = false
    conn.connectTimeout = 8000
    conn.readTimeout = 8000
    if (conn.responseCode != 200) null else {
        val json = org.json.JSONObject(conn.inputStream.bufferedReader().readText())
        val versao = json.optLong("versao", 0L)
        val caminhoApk = json.optString("apk")
        if (versao > 0 && caminhoApk.isNotBlank()) VersaoRemota(versao, "$URL_SITE/$caminhoApk") else null
    }
} catch (e: Exception) {
    android.util.Log.e("FLUXAI_UPDATE", "Erro ao consultar atualização: ${e.message}")
    null
}

fun baixarEInstalarApk(context: Context, urlUrl: String, versaoNova: Long) {
    try {
        val fileName = "Fluxai_Update.apk"
        val destinationFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )

        // Limpa o arquivo anterior se existir para não corromper
        if (destinationFile.exists()) { destinationFile.delete() }

        val request = DownloadManager.Request(urlUrl.toUri())
            .setTitle("FluxAí v$versaoNova")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(destinationFile))

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent?) {
                if (intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) == downloadId) {

                    // --- O PONTO CRÍTICO ---
                    // Deve ser EXATAMENTE ".fileprovider" para bater com seu Manifest
                    val contentUri = FileProvider.getUriForFile(
                        ctxt,
                        "${ctxt.packageName}.fileprovider",
                        destinationFile
                    )

                    val install = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(contentUri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ctxt.startActivity(install)

                    try { ctxt.unregisterReceiver(this) } catch (e: Exception) {}
                }
            }
        }

        ContextCompat.registerReceiver(
            context,
            onComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )

    } catch (e: Exception) {
        Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun instalarApk(context: Context, apkFile: File) {
    if (!apkFile.exists()) {
        Toast.makeText(context, "Arquivo não encontrado!", Toast.LENGTH_SHORT).show()
        return
    }

    // 1. Declaramos a URI aqui fora para que tanto o try quanto o catch possam usar
    val contentUri: Uri

    try {
        // 2. Geramos a URI (Certifique-se de que termina em .provider como no seu Manifest)
        // AGORA VAI BATER: .fileprovider com .fileprovider
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

    } catch (e: Exception) {
        android.util.Log.e("FLUXAI_INSTALL", "Erro ao abrir instalador: ${e.message}")
        Toast.makeText(context, "Falha ao abrir o instalador automático.", Toast.LENGTH_LONG).show()
    }
}