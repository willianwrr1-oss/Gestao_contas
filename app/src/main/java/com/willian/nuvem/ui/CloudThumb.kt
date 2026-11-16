package com.willian.nuvem.ui

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import com.willian.nuvem.Nuvem
import com.willian.nuvem.data.Account
import com.willian.nuvem.data.CloudFile
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okio.Buffer
import okio.source

/**
 * Miniatura gerada baixando a própria imagem (para nuvens que não entregam miniatura, como a Mega).
 * O Coil reduz a imagem ao tamanho da célula na hora de decodificar.
 */
data class CloudThumb(val account: Account, val file: CloudFile)

private val gate = Semaphore(3)

class CloudThumbKeyer : Keyer<CloudThumb> {
    override fun key(data: CloudThumb, options: Options): String =
        "cloud:${data.account.id}:${data.file.id}:${data.file.size}:${data.file.modified}"
}

class CloudThumbFetcher(private val m: CloudThumb, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val buf = Buffer()
        gate.withPermit {
            Nuvem.provider(m.account).open(m.file, 0).use { buf.writeAll(it.source()) }
        }
        return SourceResult(
            source = ImageSource(buf, options.context),
            mimeType = null,
            dataSource = DataSource.NETWORK
        )
    }

    class Factory : Fetcher.Factory<CloudThumb> {
        override fun create(data: CloudThumb, options: Options, imageLoader: ImageLoader): Fetcher =
            CloudThumbFetcher(data, options)
    }
}
