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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

// Última versão publicada no site do FluxAí (Firebase Hosting): versao.json + APK em /download
// minima: versões abaixo dela ficam bloqueadas até atualizar
data class VersaoRemota(val versao: Long, val urlApk: String, val minima: Long = 0L)

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
        if (versao > 0 && caminhoApk.isNotBlank()) VersaoRemota(versao, "$URL_SITE/$caminhoApk", json.optLong("minima", 0L)) else null
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
// =========================================================================
// ATUALIZAÇÃO OBRIGATÓRIA: versão abaixo da mínima publicada no site não abre o app.
// Confere ao abrir e sempre que o app volta para a tela; sem internet, deixa usar.
// =========================================================================
@Composable
fun rememberAtualizacaoObrigatoria(): State<VersaoRemota?> {
    val bloqueio = remember { mutableStateOf<VersaoRemota?>(null) }
    val ciclo = LocalLifecycleOwner.current
    var retomadas by remember { mutableIntStateOf(0) }
    DisposableEffect(ciclo) {
        val observador = LifecycleEventObserver { _, evento ->
            if (evento == Lifecycle.Event.ON_RESUME) retomadas++
        }
        ciclo.lifecycle.addObserver(observador)
        onDispose { ciclo.lifecycle.removeObserver(observador) }
    }
    LaunchedEffect(retomadas) {
        val remota = withContext(Dispatchers.IO) { buscarUltimaVersao() } ?: return@LaunchedEffect
        bloqueio.value = remota.takeIf { BuildConfig.VERSION_CODE < it.minima }
    }
    return bloqueio
}

@Composable
fun TelaAtualizacaoObrigatoria(remota: VersaoRemota) {
    val context = LocalContext.current
    var baixando by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF2A1558), Color(0xFF1C1236), Color(0xFF0E0A1A)))
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(Icons.Default.SystemUpdate, null, tint = Color.White, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(20.dp))
            Text("Atualização obrigatória", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(10.dp))
            Text(
                "Esta versão do FluxAí (v${BuildConfig.VERSION_NAME}) não é mais aceita. Atualize para a v1.0.${remota.versao} para continuar. Seus dados continuam salvos.",
                color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 21.sp
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = { baixando = true; baixarEInstalarApk(context, remota.urlApk, remota.versao) },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7E57C2)),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Default.Download, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (baixando) "Baixando... toque para tentar de novo" else "Atualizar agora", fontWeight = FontWeight.Bold)
            }
            if (baixando) {
                Spacer(Modifier.height(12.dp))
                Text("Acompanhe o download na barra de notificações.", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
        }
    }
}
