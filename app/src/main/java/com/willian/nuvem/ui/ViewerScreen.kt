package com.willian.nuvem.ui

import android.content.ComponentName
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.willian.nuvem.Nuvem
import com.willian.nuvem.data.Account
import com.willian.nuvem.data.CloudFile
import com.willian.nuvem.data.Downloader
import com.willian.nuvem.data.ExternalOpen
import com.willian.nuvem.data.FileKind
import com.willian.nuvem.data.FileTypes
import com.willian.nuvem.data.OfficeText
import com.willian.nuvem.data.ViewerCache
import com.willian.nuvem.data.fmtTime
import com.willian.nuvem.player.PlaybackService
import com.willian.nuvem.player.mediaUri
import com.willian.nuvem.provider.CloudProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

private sealed interface LoadState {
    data object Loading : LoadState
    data class Ready(val file: File) : LoadState
    data class Failed(val msg: String) : LoadState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    account: Account,
    file: CloudFile,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    position: String? = null,
    onPrev: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null
) {
    val back = { SlideshowState.on = false; onBack() }
    BackHandler(onBack = back)
    val inPip = rememberInPip()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val provider = remember(account.id) { Nuvem.provider(account) }
    val kind = FileTypes.kind(file)
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val immersive = kind == FileKind.VIDEO && landscape
    ImmersiveEffect(immersive)

    Scaffold(
        containerColor = if (kind == FileKind.VIDEO || kind == FileKind.IMAGE) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.background,
        topBar = {
            if (!immersive && !inPip) {
                TopAppBar(
                    title = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                    actions = {
                        if (position != null) Text(position, style = MaterialTheme.typography.labelMedium)
                        IconButton(onClick = { onPrev?.invoke() }, enabled = onPrev != null) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Anterior")
                        }
                        IconButton(onClick = { onNext?.invoke() }, enabled = onNext != null) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Próximo")
                        }
                        IconButton(onClick = {
                            scope.launch {
                                try {
                                    Downloader.save(provider, file) { }
                                    onMessage("Salvo em Downloads/Nuvem.")
                                } catch (e: Exception) {
                                    onMessage("Falha ao baixar: ${e.message}")
                                }
                            }
                        }) { Icon(Icons.Filled.Download, "Baixar") }
                        IconButton(onClick = {
                            scope.launch {
                                try {
                                    ExternalOpen.open(ctx, provider, file)
                                } catch (e: Exception) {
                                    onMessage("Não foi possível abrir: ${e.message}")
                                }
                            }
                        }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, "Abrir em outro app") }
                    }
                )
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (kind) {
                FileKind.AUDIO, FileKind.VIDEO -> MediaContent(account, file, kind, onPrev, onNext, immersive, inPip, back)
                FileKind.IMAGE, FileKind.PDF, FileKind.TEXT, FileKind.DOCX, FileKind.XLSX, FileKind.PPTX ->
                    CachedContent(provider, file, kind, onPrev, onNext)
                else -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(kindIcon(kind), null, Modifier.size(72.dp), tint = kindColor(kind))
                    Text(
                        "Este formato não pode ser exibido dentro do app. Abra em outro aplicativo (Word, Excel, WPS...).",
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = {
                        scope.launch {
                            try {
                                ExternalOpen.open(ctx, provider, file)
                            } catch (e: Exception) {
                                onMessage("Não foi possível abrir: ${e.message}")
                            }
                        }
                    }) { Text("Abrir em outro app") }
                }
            }
        }
    }
}

/** Esconde as barras do sistema em vídeos na horizontal. */
@Composable
private fun ImmersiveEffect(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(on) {
        val window = view.context.findActivity()?.window
        val c = window?.let { WindowCompat.getInsetsController(it, view) }
        if (on) {
            c?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { c?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

// ---- áudio e vídeo -----------------------------------------------------------------------------

@OptIn(UnstableApi::class)
@Composable
private fun MediaContent(
    account: Account,
    file: CloudFile,
    kind: FileKind,
    onPrev: (() -> Unit)?,
    onNext: (() -> Unit)?,
    immersive: Boolean,
    inPip: Boolean,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val mediaId = "${account.id}|${file.id}"
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var askPos by remember { mutableStateOf<Long?>(null) }
    var started by remember { mutableStateOf(false) }
    var playCalled by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val future = MediaController.Builder(ctx, SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))).buildAsync()
        future.addListener({ runCatching { controller = future.get() } }, ContextCompat.getMainExecutor(ctx))
        onDispose {
            // Vídeo para ao sair; música continua tocando em segundo plano.
            if (kind == FileKind.VIDEO) runCatching { controller?.pause() }
            MediaController.releaseFuture(future)
            controller = null
        }
    }

    fun play(c: MediaController, startMs: Long) {
        val item = MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(mediaUri(account, file))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(file.name).build())
            .build()
        playCalled = true
        c.setMediaItem(item, startMs)
        c.prepare()
        c.play()
    }

    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        if (started) return@LaunchedEffect
        started = true
        val alreadyLoaded = c.currentMediaItem?.mediaId == mediaId &&
            c.playbackState != Player.STATE_IDLE && c.playbackState != Player.STATE_ENDED
        if (alreadyLoaded) {
            playCalled = true
            return@LaunchedEffect
        }
        val saved = Nuvem.positions.get(mediaId)
        if (saved != null && saved.first > 5000 && (saved.second <= 0 || saved.first < saved.second - 5000)) {
            askPos = saved.first
        } else {
            play(c, 0)
        }
    }

    askPos?.let { pos ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Continuar de onde parou?") },
            text = { Text("Você parou em ${fmtTime(pos)}.") },
            confirmButton = {
                TextButton(onClick = { controller?.let { play(it, pos) }; askPos = null }) { Text("Continuar de ${fmtTime(pos)}") }
            },
            dismissButton = {
                TextButton(onClick = { controller?.let { play(it, 0) }; askPos = null }) { Text("Recomeçar") }
            }
        )
    }

    if (kind == FileKind.VIDEO) {
        VideoPlayerUi(controller, file.name, file.size, immersive, inPip, onBack)
    } else {
        val info = rememberPlaybackInfo(controller)
        // Ao terminar uma música, passa para a próxima da pasta (se houver).
        LaunchedEffect(info.ended, playCalled, info.mediaId) {
            if (info.ended && playCalled && info.mediaId == mediaId && !info.repeatOne) {
                delay(800)
                if (info.ended && onNext != null) onNext()
            }
        }
        AudioPlayerUi(controller, file.name, file.size, info, onPrev, onNext)
    }
}

// ---- imagem, PDF, texto e Office -----------------------------------------------------------------

@Composable
private fun CachedContent(provider: CloudProvider, file: CloudFile, kind: FileKind, onPrev: (() -> Unit)?, onNext: (() -> Unit)?) {
    var progress by remember { mutableStateOf<Float?>(null) }
    val state by produceState<LoadState>(LoadState.Loading, file.id) {
        value = try {
            LoadState.Ready(ViewerCache.fetch(provider, file) { progress = it })
        } catch (e: Exception) {
            LoadState.Failed(e.message ?: "Não foi possível abrir o arquivo.")
        }
    }
    when (val s = state) {
        is LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val p = progress
            if (p != null) CircularProgressIndicator(progress = { p }) else CircularProgressIndicator()
        }
        is LoadState.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(s.msg, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        is LoadState.Ready -> when (kind) {
            FileKind.IMAGE -> ImageViewerUi(s.file, file.name, onPrev, onNext)
            FileKind.PDF -> PdfContent(s.file)
            FileKind.TEXT -> TextContent(s.file)
            FileKind.DOCX -> OfficeLines(s.file, FileKind.DOCX)
            FileKind.PPTX -> OfficeLines(s.file, FileKind.PPTX)
            FileKind.XLSX -> SheetContent(s.file)
            else -> Unit
        }
    }
}

/**
 * Zoom com dois dedos para conteúdos que rolam (texto, planilha, PDF). Só reage quando há 2+ dedos,
 * então a rolagem com um dedo continua normal.
 */
private fun Modifier.pinchZoom(onZoom: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.size >= 2) {
                val z = event.calculateZoom()
                if (z != 1f) {
                    onZoom(z)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}

@Composable
private fun ImageContent(f: File) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    AsyncImage(
        model = f,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                })
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset = if (scale == 1f) Offset.Zero else offset + pan
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
    )
}

private fun readHead(f: File, max: Int): Pair<String, Boolean> {
    val size = minOf(f.length(), max.toLong()).toInt()
    val buf = ByteArray(size)
    var o = 0
    f.inputStream().use { ins ->
        while (o < size) {
            val r = ins.read(buf, o, size - o)
            if (r < 0) break
            o += r
        }
    }
    return String(buf, 0, o, Charsets.UTF_8) to (f.length() > max)
}

@Composable
private fun TextContent(f: File) {
    val data by produceState<Pair<List<String>, Boolean>?>(null, f) {
        value = withContext(Dispatchers.IO) {
            runCatching { readHead(f, 5_000_000) }.map { it.first.lines() to it.second }
                .getOrDefault(listOf("Não foi possível ler o arquivo.") to false)
        }
    }
    val d = data
    if (d == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        LinesView(d.first, mono = true, truncated = d.second)
    }
}

@Composable
private fun LinesView(lines: List<String>, mono: Boolean, truncated: Boolean = false) {
    var zoom by remember { mutableFloatStateOf(1f) }
    SelectionContainer {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp).pinchZoom { zoom = (zoom * it).coerceIn(0.6f, 4f) }) {
            items(lines) { l ->
                Text(
                    l,
                    fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                    fontSize = (if (mono) 13f else 15f).times(zoom).sp,
                    lineHeight = (if (mono) 18f else 22f).times(zoom).sp
                )
            }
            if (truncated) item { Text("… (arquivo grande: mostrando só o início)", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun OfficeLines(f: File, kind: FileKind) {
    val lines by produceState<List<String>?>(null, f) {
        value = withContext(Dispatchers.IO) {
            runCatching { (if (kind == FileKind.DOCX) OfficeText.docx(f) else OfficeText.pptx(f)).lines() }
                .getOrDefault(listOf("Não foi possível ler este arquivo. Use \"Abrir em outro app\"."))
        }
    }
    val l = lines
    if (l == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    else Column(Modifier.fillMaxSize()) {
        Text(
            "Texto extraído, sem formatação. Para ver o layout original use \"Abrir em outro app\".",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp)
        )
        LinesView(l, mono = false)
    }
}

@Composable
private fun SheetContent(f: File) {
    val rows by produceState<List<List<String>>?>(null, f) {
        value = withContext(Dispatchers.IO) { runCatching { OfficeText.xlsx(f) }.getOrDefault(emptyList()) }
    }
    val r = rows
    if (r == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else if (r.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Planilha vazia ou formato não reconhecido. Use \"Abrir em outro app\".", Modifier.padding(24.dp), textAlign = TextAlign.Center)
        }
    } else {
        var zoom by remember { mutableFloatStateOf(1f) }
        val cellW = (130f * zoom).dp
        Column(Modifier.fillMaxSize()) {
            Text(
                "Primeira planilha, só os valores. Para fórmulas e formatação use \"Abrir em outro app\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
            Box(Modifier.weight(1f).fillMaxWidth().pinchZoom { zoom = (zoom * it).coerceIn(0.6f, 3f) }.horizontalScroll(rememberScrollState())) {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(r) { i, row ->
                        Row(Modifier.then(if (i == 0) Modifier.background(MaterialTheme.colorScheme.surfaceContainer) else Modifier)) {
                            row.forEach { cell ->
                                Text(
                                    cell,
                                    Modifier.width(cellW).padding(horizontal = 8.dp, vertical = 6.dp),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = (13f * zoom).sp
                                )
                            }
                        }
                        HorizontalDivider(Modifier.width(cellW * maxOf(1, row.size)))
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfContent(f: File) {
    val renderer = remember(f) {
        runCatching { PdfRenderer(ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)) }.getOrNull()
    }
    if (renderer == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Não foi possível abrir este PDF.", color = MaterialTheme.colorScheme.error)
        }
        return
    }
    DisposableEffect(renderer) { onDispose { runCatching { renderer.close() } } }
    val mutex = remember(renderer) { Mutex() }
    var zoom by remember { mutableFloatStateOf(1f) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // As páginas são desenhadas com um pouco mais de resolução para o zoom não ficar borrado.
        val widthPx = (with(LocalDensity.current) { maxWidth.roundToPx() } * 1.5f).toInt().coerceAtMost(1800)
        val pageW = maxWidth
        Box(
            Modifier.fillMaxSize()
                .pinchZoom { zoom = (zoom * it).coerceIn(1f, 3f) }
                .horizontalScroll(rememberScrollState())
        ) {
        LazyColumn(Modifier.width(pageW * zoom).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(renderer.pageCount) { index ->
                val bmp by produceState<Bitmap?>(null, index, widthPx) {
                    value = withContext(Dispatchers.Default) {
                        runCatching {
                            mutex.withLock {
                                renderer.openPage(index).use { page ->
                                    val h = (widthPx.toFloat() / page.width * page.height).toInt().coerceAtLeast(1)
                                    val b = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
                                    b.eraseColor(AndroidColor.WHITE)
                                    page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    b
                                }
                            }
                        }.getOrNull()
                    }
                }
                val b = bmp
                if (b != null) {
                    Image(b.asImageBitmap(), contentDescription = "Página ${index + 1}", modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                } else {
                    Box(Modifier.fillMaxWidth().aspectRatio(0.707f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
        }
        }
    }
}
