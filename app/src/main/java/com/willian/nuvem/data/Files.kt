package com.willian.nuvem.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.willian.nuvem.Nuvem
import com.willian.nuvem.provider.CloudProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

private fun copyWithProgress(input: InputStream, out: OutputStream, total: Long, onProgress: (Float?) -> Unit) {
    val buf = ByteArray(64 * 1024)
    var done = 0L
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        out.write(buf, 0, n)
        done += n
        onProgress(if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null)
    }
}

/** Arquivos temporários para visualizar (imagem, PDF, texto, Office) sem baixar de forma definitiva. */
object ViewerCache {
    private val dir: File get() = File(Nuvem.app.cacheDir, "viewer").also { it.mkdirs() }

    suspend fun fetch(provider: CloudProvider, file: CloudFile, onProgress: (Float?) -> Unit = {}): File =
        withContext(Dispatchers.IO) {
            if (provider.account.provider == Provider.LOCAL) return@withContext File(file.id)
            val safe = file.name.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(80)
            val out = File(dir, "${(provider.account.id + file.id).hashCode().toUInt().toString(16)}_$safe")
            if (out.exists() && out.length() > 0 && (file.size < 0 || out.length() == file.size)) {
                out.setLastModified(System.currentTimeMillis())
                return@withContext out
            }
            val tmp = File(dir, out.name + ".part")
            try {
                provider.open(file, 0).use { input ->
                    tmp.outputStream().use { copyWithProgress(input, it, file.size, onProgress) }
                }
                if (!tmp.renameTo(out)) throw IOException("Não foi possível guardar o arquivo temporário.")
            } finally {
                tmp.delete()
            }
            out
        }
}

/** Salva uma cópia em Downloads/Nuvem. */
object Downloader {
    suspend fun save(provider: CloudProvider, file: CloudFile, onProgress: (Float?) -> Unit) =
        withContext(Dispatchers.IO) {
            val cr = Nuvem.app.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, FileTypes.mime(file.name))
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/Nuvem")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Não foi possível criar o arquivo em Downloads.")
            try {
                provider.open(file, 0).use { input ->
                    (cr.openOutputStream(uri) ?: throw IOException("Não foi possível gravar em Downloads."))
                        .use { copyWithProgress(input, it, file.size, onProgress) }
                }
                cr.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                cr.delete(uri, null, null)
                throw e
            }
        }
}

/** Abre o arquivo em outro aplicativo instalado (Word, Excel, WPS, galeria...). */
object ExternalOpen {
    suspend fun open(ctx: Context, provider: CloudProvider, file: CloudFile) {
        val local = ViewerCache.fetch(provider, file)
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", local)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, FileTypes.mime(file.name))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(intent, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
