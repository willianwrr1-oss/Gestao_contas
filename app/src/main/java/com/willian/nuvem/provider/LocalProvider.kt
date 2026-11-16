package com.willian.nuvem.provider

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.willian.nuvem.data.CloudFile
import com.willian.nuvem.data.LOCAL_ACCOUNT
import com.willian.nuvem.data.Quota
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.coroutineContext

/** Armazenamento do próprio celular (exige a permissão "Acesso a todos os arquivos"). */
class LocalProvider(private val ctx: Context) : CloudProvider {
    override val account = LOCAL_ACCOUNT
    override val supportsTrashList = true

    val root: File = Environment.getExternalStorageDirectory()
    private val trashDir: File
        get() = (ctx.getExternalFilesDir("lixeira") ?: File(ctx.filesDir, "lixeira")).also { it.mkdirs() }
    private val sp = ctx.getSharedPreferences("local_trash", Context.MODE_PRIVATE)

    private fun fileOf(id: String?) = if (id == null) root else File(id)

    private fun toFile(x: File) = CloudFile(
        x.absolutePath, x.name, x.isDirectory,
        if (x.isDirectory) -1L else x.length(), x.lastModified()
    )

    override suspend fun identity() = "Este celular" to ""

    override suspend fun list(folderId: String?): List<CloudFile> = withContext(Dispatchers.IO) {
        val files = fileOf(folderId).listFiles() ?: throw IOException("Sem permissão para ler esta pasta.")
        files.map { toFile(it) }
    }

    override suspend fun search(query: String): List<CloudFile> = withContext(Dispatchers.IO) {
        val job: Job? = coroutineContext[Job]
        val q = query.lowercase()
        root.walkTopDown()
            .onFail { _, _ -> }
            .takeWhile { job?.isActive != false }
            .filter { it != root && it.name.lowercase().contains(q) }
            .take(300)
            .map { toFile(it) }
            .toList()
    }

    override suspend fun quota(): Quota? {
        val st = StatFs(root.path)
        return Quota(st.totalBytes - st.availableBytes, st.totalBytes)
    }

    override suspend fun open(file: CloudFile, offset: Long): InputStream = withContext(Dispatchers.IO) {
        FileInputStream(File(file.id)).also { if (offset > 0) it.channel.position(offset) }
    }

    override suspend fun upload(
        parentId: String?, name: String, size: Long, mime: String,
        input: InputStream, onProgress: (Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val dir = fileOf(parentId)
        var target = File(dir, name)
        var n = 1
        while (target.exists()) {
            target = File(dir, "${name.substringBeforeLast('.')} ($n)${name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }}")
            n++
        }
        target.outputStream().use { out ->
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val r = input.read(buf)
                if (r < 0) break
                out.write(buf, 0, r)
                total += r
                onProgress(total)
            }
        }
    }

    override suspend fun createFolder(parentId: String?, name: String) {
        withContext(Dispatchers.IO) {
            if (!File(fileOf(parentId), name).mkdirs()) throw IOException("Não foi possível criar a pasta.")
        }
    }

    private fun move(src: File, dst: File) {
        if (!src.renameTo(dst)) {
            src.copyRecursively(dst, true)
            src.deleteRecursively()
        }
    }

    override suspend fun trash(file: CloudFile) = withContext(Dispatchers.IO) {
        val src = File(file.id)
        val tn = "${System.currentTimeMillis()}_${src.name}"
        move(src, File(trashDir, tn))
        sp.edit().putString(
            tn,
            JSONObject().put("orig", src.absolutePath).put("name", src.name).put("dir", src.isDirectory)
                .put("size", if (src.isDirectory) -1 else File(trashDir, tn).length())
                .put("ts", System.currentTimeMillis()).toString()
        ).apply()
    }

    override suspend fun listTrash(): List<CloudFile> = withContext(Dispatchers.IO) {
        sp.all.keys.mapNotNull { key ->
            val f = File(trashDir, key)
            if (!f.exists()) return@mapNotNull null
            val o = JSONObject(sp.getString(key, "{}")!!)
            CloudFile(key, o.optString("name", key), o.optBoolean("dir"), o.optLong("size", -1), o.optLong("ts"))
        }
    }

    override suspend fun restore(file: CloudFile) = withContext(Dispatchers.IO) {
        val o = JSONObject(sp.getString(file.id, "{}")!!)
        var dst = File(o.optString("orig", File(root, file.name).absolutePath))
        dst.parentFile?.mkdirs()
        if (dst.exists()) dst = File(dst.parentFile, dst.nameWithoutExtension + " (restaurado)" +
                (if (dst.extension.isEmpty()) "" else "." + dst.extension))
        move(File(trashDir, file.id), dst)
        sp.edit().remove(file.id).apply()
    }

    override suspend fun deleteForever(file: CloudFile) = withContext(Dispatchers.IO) {
        File(trashDir, file.id).deleteRecursively()
        sp.edit().remove(file.id).apply()
        Unit
    }

    override suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        trashDir.listFiles()?.forEach { it.deleteRecursively() }
        sp.edit().clear().apply()
    }
}
