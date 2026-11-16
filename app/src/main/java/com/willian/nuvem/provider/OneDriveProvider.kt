package com.willian.nuvem.provider

import android.net.Uri
import com.willian.nuvem.Nuvem
import com.willian.nuvem.data.Account
import com.willian.nuvem.data.CloudFile
import com.willian.nuvem.data.Quota
import com.willian.nuvem.data.readFully
import com.willian.nuvem.data.skipFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.time.Instant

class OneDriveProvider(override val account: Account, private val auth: OAuthSession) : CloudProvider {
    private val http = Nuvem.http
    private val api = "https://graph.microsoft.com/v1.0"
    private val select = "\$select=id,name,size,folder,file,lastModifiedDateTime"

    // A API do Graph não expõe a lixeira do OneDrive pessoal.
    override val supportsTrashList = false

    private suspend fun exec(b: Request.Builder): Response = withContext(Dispatchers.IO) {
        val token = auth.token()
        http.newCall(b.header("Authorization", "Bearer $token").build()).execute()
    }

    private suspend fun json(b: Request.Builder): JSONObject = withContext(Dispatchers.IO) {
        exec(b).use { r ->
            if (!r.isSuccessful) throw Net.error(r)
            val t = r.body?.string().orEmpty()
            if (t.isBlank()) JSONObject() else JSONObject(t)
        }
    }

    private fun toFile(o: JSONObject): CloudFile {
        val isFolder = o.has("folder")
        val modified = o.optString("lastModifiedDateTime").takeIf { it.isNotEmpty() }
            ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
        return CloudFile(
            o.getString("id"), o.optString("name"), isFolder,
            if (isFolder) -1L else o.optLong("size", -1L), modified,
            o.optJSONObject("file")?.optString("mimeType").orEmpty()
        )
    }

    private suspend fun pages(firstUrl: String): List<CloudFile> {
        val out = ArrayList<CloudFile>()
        var url: String? = firstUrl
        while (url != null) {
            val j = json(Request.Builder().url(url))
            val arr = j.optJSONArray("value")
            if (arr != null) for (i in 0 until arr.length()) out.add(toFile(arr.getJSONObject(i)))
            url = j.optString("@odata.nextLink").ifEmpty { null }
        }
        return out
    }

    override suspend fun identity(): Pair<String, String> {
        val j = json(Request.Builder().url("$api/me?\$select=displayName,userPrincipalName,mail"))
        val email = j.optString("mail").ifEmpty { j.optString("userPrincipalName") }
        return j.optString("displayName").ifEmpty { "OneDrive" } to email
    }

    override suspend fun list(folderId: String?): List<CloudFile> {
        val base = if (folderId == null) "$api/me/drive/root/children" else "$api/me/drive/items/$folderId/children"
        return pages("$base?\$top=200&$select")
    }

    override suspend fun search(query: String): List<CloudFile> {
        val q = Uri.encode(query.replace("'", "''"))
        return pages("$api/me/drive/root/search(q='$q')?\$top=100&$select")
    }

    override suspend fun quota(): Quota? {
        val q = json(Request.Builder().url("$api/me/drive?\$select=quota")).optJSONObject("quota") ?: return null
        return Quota(q.optLong("used"), q.optLong("total"))
    }

    override suspend fun open(file: CloudFile, offset: Long): InputStream = withContext(Dispatchers.IO) {
        val b = Request.Builder().url("$api/me/drive/items/${file.id}/content")
        if (offset > 0) b.header("Range", "bytes=$offset-")
        val r = exec(b)
        if (!r.isSuccessful) throw Net.error(r)
        val s = r.body!!.byteStream()
        if (offset > 0 && r.code != 206) s.skipFully(offset)
        s
    }

    override suspend fun upload(
        parentId: String?, name: String, size: Long, mime: String,
        input: InputStream, onProgress: (Long) -> Unit
    ) {
        val enc = Uri.encode(name)
        val base = if (parentId == null) "$api/me/drive/root:/$enc:" else "$api/me/drive/items/$parentId:/$enc:"
        if (size == 0L) {
            json(Request.Builder().url("$base/content").put(ByteArray(0).toRequestBody()))
            return
        }
        val session = json(
            Request.Builder().url("$base/createUploadSession")
                .post("""{"item":{"@microsoft.graph.conflictBehavior":"rename"}}""".toRequestBody(Net.JSON))
        )
        val uploadUrl = session.getString("uploadUrl")
        val chunk = 3_276_800 // múltiplo de 320 KiB, exigido pela API
        val buf = ByteArray(chunk)
        var sent = 0L
        withContext(Dispatchers.IO) {
            while (sent < size) {
                val n = minOf(chunk.toLong(), size - sent).toInt()
                input.readFully(buf, n)
                // A URL de envio já vem autorizada: não se envia o token nela.
                val req = Request.Builder().url(uploadUrl)
                    .header("Content-Range", "bytes $sent-${sent + n - 1}/$size")
                    .put(buf.toRequestBody(null, 0, n))
                    .build()
                http.newCall(req).execute().use { if (!it.isSuccessful) throw Net.error(it) }
                sent += n
                onProgress(sent)
            }
        }
    }

    override suspend fun createFolder(parentId: String?, name: String) {
        val base = if (parentId == null) "$api/me/drive/root/children" else "$api/me/drive/items/$parentId/children"
        val body = JSONObject().put("name", name).put("folder", JSONObject())
            .put("@microsoft.graph.conflictBehavior", "rename").toString()
        json(Request.Builder().url(base).post(body.toRequestBody(Net.JSON)))
    }

    override suspend fun trash(file: CloudFile) {
        // O item vai para a lixeira do próprio OneDrive (onedrive.live.com).
        json(Request.Builder().url("$api/me/drive/items/${file.id}").delete())
    }

    override suspend fun listTrash(): List<CloudFile> =
        throw IOException("A lixeira do OneDrive só pode ser vista pelo site onedrive.live.com.")

    override suspend fun restore(file: CloudFile): Unit = throw IOException("Restaure pelo site do OneDrive.")
    override suspend fun deleteForever(file: CloudFile): Unit = throw IOException("Exclua pelo site do OneDrive.")
    override suspend fun emptyTrash(): Unit = throw IOException("Esvazie pelo site do OneDrive.")
}
