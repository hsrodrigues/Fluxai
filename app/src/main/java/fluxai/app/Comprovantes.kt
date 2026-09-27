package fluxai.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

// =========================================================================
// COMPROVANTES
// Foto do recibo/nota ligada a um lançamento, guardada no Firebase Storage em
// usuarios/{conta}/comprovantes/{lançamento}.jpg (ou .pdf). A imagem é reduzida antes do envio;
// o PDF sobe como está, até 10 MB.
// =========================================================================
private const val LADO_MAXIMO = 1600
private const val PDF_MAXIMO = 10 * 1024 * 1024

private fun caminhoComprovante(workspaceUid: String, despesaId: String, extensao: String) = "usuarios/$workspaceUid/comprovantes/$despesaId.$extensao"

fun comprovanteEhPdf(caminho: String?) = caminho?.endsWith(".pdf", ignoreCase = true) == true

// Reduz para no máximo 1600 px e JPEG 80%: um recibo fica com ~200 KB
private fun comprimirImagem(context: Context, uri: Uri): ByteArray {
    val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
    var amostra = 1
    while (limites.outWidth / amostra > LADO_MAXIMO * 2 || limites.outHeight / amostra > LADO_MAXIMO * 2) amostra *= 2
    val original = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = amostra }) }
        ?: error("Imagem inválida")
    val escala = minOf(1f, LADO_MAXIMO.toFloat() / maxOf(original.width, original.height))
    val final = if (escala < 1f) Bitmap.createScaledBitmap(original, (original.width * escala).toInt(), (original.height * escala).toInt(), true) else original
    return ByteArrayOutputStream().use { saida -> final.compress(Bitmap.CompressFormat.JPEG, 80, saida); saida.toByteArray() }
}

class ComprovanteGrande : Exception("O PDF passa de 10 MB.")

suspend fun enviarComprovante(context: Context, workspaceUid: String, despesaId: String, uri: Uri) {
    val pdf = context.contentResolver.getType(uri) == "application/pdf"
    val bytes = withContext(Dispatchers.IO) {
        if (pdf) context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Arquivo inválido")
        else comprimirImagem(context, uri)
    }
    if (bytes.size > PDF_MAXIMO) throw ComprovanteGrande()
    val caminho = caminhoComprovante(workspaceUid, despesaId, if (pdf) "pdf" else "jpg")
    val tipo = if (pdf) "application/pdf" else "image/jpeg"
    Firebase.storage.reference.child(caminho).putBytes(bytes, StorageMetadata.Builder().setContentType(tipo).build()).await()
    // Trocou de foto para PDF (ou o contrário): apaga o arquivo antigo com a outra extensão
    val antigo = Firebase.firestore.collection("usuarios").document(workspaceUid).collection("despesas").document(despesaId).get().await().getString("comprovante")
    if (antigo != null && antigo != caminho) runCatching { Firebase.storage.reference.child(antigo).delete().await() }
    Firebase.firestore.collection("usuarios").document(workspaceUid).collection("despesas").document(despesaId).update("comprovante", caminho).await()
}

suspend fun removerComprovante(workspaceUid: String, despesa: Despesa) {
    despesa.comprovante?.let { runCatching { Firebase.storage.reference.child(it).delete().await() } }
    Firebase.firestore.collection("usuarios").document(workspaceUid).collection("despesas").document(despesa.id).update("comprovante", FieldValue.delete()).await()
}

// Mensagem clara para as falhas de envio mais comuns (sem o texto técnico do Firebase)
fun mensagemErroComprovante(e: Throwable): String = if (e is ComprovanteGrande) "O PDF passa de 10 MB. Escolha um arquivo menor." else when ((e as? com.google.firebase.storage.StorageException)?.errorCode) {
    com.google.firebase.storage.StorageException.ERROR_OBJECT_NOT_FOUND,
    com.google.firebase.storage.StorageException.ERROR_BUCKET_NOT_FOUND -> "O armazenamento de comprovantes ainda não está ativo no servidor."
    com.google.firebase.storage.StorageException.ERROR_NOT_AUTHORIZED -> "Sem permissão para salvar o comprovante nesta conta."
    com.google.firebase.storage.StorageException.ERROR_RETRY_LIMIT_EXCEEDED -> "Sem conexão. Tente anexar de novo quando a internet voltar."
    else -> "Não foi possível enviar o comprovante."
}

// Ao excluir um lançamento, a foto vai junto
fun apagarArquivoComprovante(despesa: Despesa) {
    despesa.comprovante?.let { Firebase.storage.reference.child(it).delete() }
}

@Composable
fun DialogoComprovante(despesa: Despesa, workspaceUid: String, c: CoresTela, onFechar: () -> Unit) {
    var url by remember { mutableStateOf<String?>(null) }
    var falhou by remember { mutableStateOf(false) }
    var removendo by remember { mutableStateOf(false) }
    val escopo = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val pdf = comprovanteEhPdf(despesa.comprovante)
    LaunchedEffect(despesa.comprovante) {
        url = runCatching { Firebase.storage.reference.child(despesa.comprovante ?: "").downloadUrl.await().toString() }.getOrNull()
        falhou = url == null
    }
    AlertDialog(
        onDismissRequest = onFechar,
        containerColor = c.superficie,
        shape = RoundedCornerShape(28.dp),
        icon = { Icon(Icons.Default.Receipt, null, tint = c.destaque) },
        title = { Text("Comprovante", fontWeight = FontWeight.Bold, color = c.texto) },
        text = {
            Box(Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 460.dp), contentAlignment = Alignment.Center) {
                when {
                    falhou -> Text("Não foi possível abrir o comprovante. Confira a conexão.", color = c.textoFraco)
                    url == null -> CircularProgressIndicator(color = c.destaque)
                    // PDF abre no leitor do celular (o link é temporário e só funciona para quem tem acesso)
                    pdf -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PictureAsPdf, null, tint = Color(0xFFE53935), modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Comprovante em PDF", color = c.texto, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))) }
                                    .onFailure { android.widget.Toast.makeText(context, "Nenhum app para abrir PDF.", android.widget.Toast.LENGTH_SHORT).show() }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = c.destaque)
                        ) { Text("Abrir PDF") }
                    }
                    else -> AsyncImage(
                        model = url, contentDescription = "Comprovante de ${despesa.descricao}",
                        contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onFechar) { Text("Fechar", color = c.destaque) } },
        dismissButton = {
            TextButton(
                onClick = {
                    removendo = true
                    escopo.launch { runCatching { removerComprovante(workspaceUid, despesa) }; removendo = false; onFechar() }
                },
                enabled = !removendo
            ) {
                Icon(Icons.Default.DeleteOutline, null, tint = Color(0xFFE53935), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Remover", color = Color(0xFFE53935))
            }
        }
    )
}
