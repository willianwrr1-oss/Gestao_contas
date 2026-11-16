package com.willian.nuvem.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.willian.nuvem.Nuvem
import com.willian.nuvem.data.Account
import com.willian.nuvem.data.CloudFile
import com.willian.nuvem.data.Provider
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.io.InputStream

/** Endereço que o player entende: nuvem://<idDaConta>/<nome>?id=<idDoArquivo>&size=<bytes> */
fun mediaUri(account: Account, file: CloudFile): Uri =
    if (account.provider == Provider.LOCAL) Uri.fromFile(File(file.id))
    else Uri.Builder().scheme("nuvem").authority(account.id)
        .appendPath(file.name)
        .appendQueryParameter("id", file.id)
        .appendQueryParameter("size", file.size.toString())
        .build()

/** Fonte de dados do Media3 que lê o arquivo pelo provedor (Drive, OneDrive, Mega). */
@UnstableApi
class ProviderDataSource : BaseDataSource(true) {
    private var uri: Uri? = null
    private var stream: InputStream? = null
    private var remaining = C.LENGTH_UNSET.toLong()
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val u = dataSpec.uri
        val provider = Nuvem.providerById(u.authority ?: "") ?: throw IOException("Conta não encontrada.")
        val size = u.getQueryParameter("size")?.toLongOrNull() ?: -1L
        val id = u.getQueryParameter("id") ?: throw IOException("Arquivo inválido.")
        val file = CloudFile(id, u.lastPathSegment ?: "", false, size)
        stream = runBlocking { provider.open(file, dataSpec.position) }
        remaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            size >= 0 -> size - dataSpec.position
            else -> C.LENGTH_UNSET.toLong()
        }
        opened = true
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val toRead = if (remaining == C.LENGTH_UNSET.toLong()) length else minOf(remaining, length.toLong()).toInt()
        val n = stream!!.read(buffer, offset, toRead)
        if (n == -1) return C.RESULT_END_OF_INPUT
        if (remaining != C.LENGTH_UNSET.toLong()) remaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        try {
            stream?.close()
        } finally {
            stream = null
            uri = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    class Factory : DataSource.Factory {
        override fun createDataSource(): DataSource = ProviderDataSource()
    }
}
