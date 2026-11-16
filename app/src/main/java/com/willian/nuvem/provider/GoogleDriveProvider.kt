package com.willian.nuvem.provider

import com.willian.nuvem.Nuvem
import com.willian.nuvem.data.Account
import com.willian.nuvem.data.CloudFile
import com.willian.nuvem.data.Quota
import com.willian.nuvem.data.skipFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.time.Instant

class GoogleDriveProvider(override val account: Account, private val auth: OAuthSession) : CloudProvider {
    private val http = Nuvem.http
    private val api = "https://www.googleapis.com/drive/v3"
    private val folderMime = "application/vnd.google-apps.folder"
    override val supportsTrashList = true

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
        val mime = o.optString("mimeType")
        val isFolder = mime == folderMime
        var name = o.optString("name")
        // Documentos nativos do Google (Docs, Planilhas...) são exibidos como PDF.
        val native = mime.startsWith("application/vnd.google-apps.") && !isFolder
        if (native && !name.endsWith(".pdf", true)) name += ".pdf"
        val modified = o.optString("modifiedTime").takeIf { it.isNotEmpty() }
            ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
        val size = if (o.has("size")) o.getString("size").toLongOrNull() ?: -1L else -1L
        return CloudFile(o.getString("id"), name, isFolder, size, modified, mime, o.optString("thumbnailLink").ifEmpty { null })
    }

    private suspend fun queryFiles(q: String): List<CloudFile> {
        val out = ArrayList<CloudFile>()
        var page: String? = null
        do {
            val url = "$api/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", q)
                .addQueryParameter("fields", "nextPageToken,files(id,name,mimeType,size,modifiedTime,thumbnailLink)")
                .addQueryParameter("pageSize", "1000")
                .apply { page?.let { addQueryParameter("pageToken", it) } }
                .build()
            val j = json(Request.Builder().url(url))
            val arr = j.optJSONArray("files") ?: JSONArray()
            for (i in 0 until arr.length()) out.add(toFile(arr.getJSONObject(i)))
            page = j.optString("nextPageToken").ifEmpty { null }
        } while (page != null)
        return out
    }

    override suspend fun identity(): Pair<String, String> {
        val u = json(Request.Builder().url("$api/about?fields=user")).optJSONObject("user")
        return (u?.optString("displayName") ?: "Google Drive") to (u?.optString("emailAddress") ?: "")
    }

    override suspend fun list(folderId: String?): List<CloudFile> =
        queryFiles("'${folderId ?: "root"}' in parents and trashed=false")

    override suspend fun search(query: String): List<CloudFile> =
        queryFiles("name contains '${query.replace("\\", "\\\\").replace("'", "\\'")}' and trashed=false")

    override suspend fun quota(): Quota? {
        val q = json(Request.Builder().url("$api/about?fields=storageQuota")).optJSONObject("storageQuota") ?: return null
        val used = q.optString("usage").toLongOrNull() ?: return null
        return Quota(used, q.optString("limit").toLongOrNull() ?: 0L)
    }

    override suspend fun open(file: CloudFile, offset: Long): InputStream = withContext(Dispatchers.IO) {
        val native = file.mime.startsWith("application/vnd.google-apps.")
        val url = if (native) "$api/files/${file.id}/export?mimeType=application%2Fpdf"
        else "$api/files/${file.id}?alt=media"
        val b = Request.Builder().url(url)
        if (offset > 0 && !native) b.header("Range", "bytes=$offset-")
        val r = exec(b)
        if (!r.isSuccessful) throw Net.error(r)
        val s = r.body!!.byteStream()
        if (offset > 0 && (native || r.code != 206)) s.skipFully(offset)
        s
    }

    override suspend fun upload(
        parentId: String?, name: String, size: Long, mime: String,
        input: InputStream, onProgress: (Long) -> Unit
    ) {
        val meta = JSONObject().put("name", name).put("parents", JSONArray().put(parentId ?: "root")).toString()
        val location = withContext(Dispatchers.IO) {
            val token = auth.token()
            val req = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable")
                .header("Authorization", "Bearer $token")
                .header("X-Upload-Content-Type", mime)
                .header("X-Upload-Content-Length", size.toString())
                .post(meta.toRequestBody(Net.JSON))
                .build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw Net.error(r)
                r.header("Location") ?: throw IOException("O Google Drive não devolveu o endereço de envio.")
            }
        }
        withContext(Dispatchers.IO) {
            val token = auth.token()
            val put = Request.Builder().url(location)
                .header("Authorization", "Bearer $token")
                .put(StreamBody(mime.toMediaTypeOrNull(), size, input, onProgress))
                .build()
            http.newCall(put).execute().use { if (!it.isSuccessful) throw Net.error(it) }
        }
    }

    override suspend fun createFolder(parentId: String?, name: String) {
        val body = JSONObject().put("name", name).put("mimeType", folderMime)
            .put("parents", JSONArray().put(parentId ?: "root")).toString()
        json(Request.Builder().url("$api/files").post(body.toRequestBody(Net.JSON)))
    }

    override suspend fun trash(file: CloudFile) {
        json(Request.Builder().url("$api/files/${file.id}").patch("""{"trashed":true}""".toRequestBody(Net.JSON)))
    }

    override suspend fun listTrash(): List<CloudFile> = queryFiles("trashed=true and 'me' in owners")

    override suspend fun restore(file: CloudFile) {
        json(Request.Builder().url("$api/files/${file.id}").patch("""{"trashed":false}""".toRequestBody(Net.JSON)))
    }

    override suspend fun deleteForever(file: CloudFile) {
        json(Request.Builder().url("$api/files/${file.id}").delete())
    }

    override suspend fun emptyTrash() {
        json(Request.Builder().url("$api/files/trash").delete())
    }
}
