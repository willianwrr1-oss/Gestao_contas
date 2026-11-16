package com.willian.nuvem.provider

import com.willian.nuvem.data.Account
import com.willian.nuvem.data.CloudFile
import com.willian.nuvem.data.Quota
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream

/** Contrato único: cada nuvem (e o armazenamento do celular) implementa estas operações. */
interface CloudProvider {
    val account: Account
    val supportsTrashList: Boolean

    /** (nome, e-mail) da conta. */
    suspend fun identity(): Pair<String, String>

    /** Lista uma pasta. `folderId == null` significa a raiz. */
    suspend fun list(folderId: String?): List<CloudFile>
    suspend fun search(query: String): List<CloudFile>
    suspend fun quota(): Quota?

    /** Abre o conteúdo a partir de `offset` (permite avançar em vídeos e músicas). */
    suspend fun open(file: CloudFile, offset: Long = 0): InputStream

    suspend fun upload(
        parentId: String?, name: String, size: Long, mime: String,
        input: InputStream, onProgress: (Long) -> Unit
    )

    suspend fun createFolder(parentId: String?, name: String)
    suspend fun trash(file: CloudFile)
    suspend fun listTrash(): List<CloudFile>
    suspend fun restore(file: CloudFile)
    suspend fun deleteForever(file: CloudFile)
    suspend fun emptyTrash()
}

object Net {
    val JSON = "application/json; charset=utf-8".toMediaType()

    /** Converte uma resposta HTTP de erro em uma mensagem legível (e fecha a resposta). */
    fun error(r: Response): IOException {
        val body = runCatching { r.body?.string() }.getOrNull()
        val code = r.code
        r.close()
        val msg = runCatching {
            val o = JSONObject(body!!)
            o.optJSONObject("error")?.optString("message") ?: o.optString("error_description")
        }.getOrNull()
        return IOException(
            when {
                code == 401 -> "Sessão expirada. Remova a conta e adicione de novo."
                !msg.isNullOrBlank() -> msg
                else -> "Erro HTTP $code"
            }
        )
    }
}

/** Corpo de requisição que envia um InputStream sem carregá-lo todo na memória. */
class StreamBody(
    private val type: okhttp3.MediaType?,
    private val length: Long,
    private val input: InputStream,
    private val onProgress: (Long) -> Unit
) : RequestBody() {
    override fun contentType() = type
    override fun contentLength() = length
    override fun isOneShot() = true
    override fun writeTo(sink: BufferedSink) {
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            sink.write(buf, 0, n)
            total += n
            onProgress(total)
        }
    }
}
