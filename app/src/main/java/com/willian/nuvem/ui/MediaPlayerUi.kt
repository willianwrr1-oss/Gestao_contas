@file:OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)

package com.willian.nuvem.ui

import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.util.Rational
import android.view.LayoutInflater
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.willian.nuvem.R
import com.willian.nuvem.data.fmtSize
import com.willian.nuvem.data.fmtTime
import com.willian.nuvem.player.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

// ---- estado compartilhado do player --------------------------------------------------------------

@Stable
class PlaybackInfo {
    var pos by mutableLongStateOf(0L)
    var dur by mutableLongStateOf(0L)
    var playing by mutableStateOf(false)
    var buffering by mutableStateOf(false)
    var ended by mutableStateOf(false)
    var speed by mutableFloatStateOf(1f)
    var repeatOne by mutableStateOf(false)
    var tracks by mutableStateOf(Tracks.EMPTY)
    var meta by mutableStateOf(MediaMetadata.EMPTY)
    var mediaId by mutableStateOf<String?>(null)
}

@Composable
fun rememberPlaybackInfo(c: MediaController?): PlaybackInfo {
    val info = remember { PlaybackInfo() }
    DisposableEffect(c) {
        if (c == null) {
            onDispose { }
        } else {
            fun sync() {
                info.playing = c.isPlaying
                info.speed = c.playbackParameters.speed
                info.repeatOne = c.repeatMode == Player.REPEAT_MODE_ONE
                info.tracks = c.currentTracks
                info.meta = c.mediaMetadata
                info.buffering = c.playbackState == Player.STATE_BUFFERING
                info.ended = c.playbackState == Player.STATE_ENDED
                info.mediaId = c.currentMediaItem?.mediaId
            }
            val l = object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = sync()
            }
            c.addListener(l)
            sync()
            onDispose { c.removeListener(l) }
        }
    }
    LaunchedEffect(c) {
        while (isActive) {
            if (c != null) {
                info.pos = c.currentPosition.coerceAtLeast(0L)
                val d = c.duration
                info.dur = if (d == C.TIME_UNSET) 0L else d
            }
            delay(250)
        }
    }
    return info
}

/** Verdadeiro enquanto o app está em picture-in-picture. */
@Composable
fun rememberInPip(): Boolean {
    val ctx = LocalContext.current
    var inPip by remember { mutableStateOf(false) }
    LaunchedEffect(ctx) {
        val act = ctx.findActivity()
        while (isActive) {
            inPip = act?.isInPictureInPictureMode == true
            delay(400)
        }
    }
    return inPip
}

// ---- peças de interface ------------------------------------------------------------------------

@Composable
internal fun Pill(text: String, icon: ImageVector? = null, selected: Boolean = false, dark: Boolean = false, onClick: () -> Unit) {
    val bg = when {
        selected -> MaterialTheme.colorScheme.primary
        dark -> Color(0x40FFFFFF)
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val fg = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        dark -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = bg, contentColor = fg) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

private fun speedLabel(s: Float): String {
    val t = if (s == s.toInt().toFloat()) s.toInt().toString() else s.toString()
    return t.replace('.', ',') + "x"
}

@Composable
private fun SpeedPill(info: PlaybackInfo, c: MediaController?, dark: Boolean) {
    var open by remember { mutableStateOf(false) }
    Box {
        Pill(speedLabel(info.speed), Icons.Filled.Speed, info.speed != 1f, dark) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (s in listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)) {
                DropdownMenuItem(
                    text = { Text(speedLabel(s)) },
                    onClick = { c?.setPlaybackSpeed(s); open = false },
                    trailingIcon = { if (info.speed == s) Icon(Icons.Filled.Check, null) }
                )
            }
        }
    }
}

@Composable
private fun SeekRow(info: PlaybackInfo, c: MediaController?, dark: Boolean) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val dur = info.dur.coerceAtLeast(1L)
    val shown = drag ?: (info.pos.toFloat() / dur).coerceIn(0f, 1f)
    val fg = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = shown,
            onValueChange = { drag = it },
            onValueChangeFinished = {
                drag?.let { c?.seekTo((it * dur).toLong()) }
                drag = null
            },
            enabled = info.dur > 0
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmtTime((shown * dur).toLong()), color = fg, style = MaterialTheme.typography.labelMedium)
            Text(fmtTime(info.dur), color = fg, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun InfoDialog(title: String, rows: List<Pair<String, String>>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((k, v) in rows) {
                    Column {
                        Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(v, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

private fun selectedFormat(t: Tracks, type: Int): Format? {
    for (g in t.groups) {
        if (g.type != type) continue
        for (i in 0 until g.length) if (g.isTrackSelected(i)) return g.getTrackFormat(i)
    }
    return null
}

private fun technicalRows(name: String, size: Long, dur: Long, t: Tracks): List<Pair<String, String>> {
    val rows = mutableListOf<Pair<String, String>>()
    rows += "Arquivo" to name
    if (size >= 0) rows += "Tamanho" to fmtSize(size)
    if (dur > 0) rows += "Duração" to fmtTime(dur)
    selectedFormat(t, C.TRACK_TYPE_VIDEO)?.let { f ->
        if (f.width > 0 && f.height > 0) rows += "Resolução" to "${f.width} × ${f.height}"
        if (f.frameRate > 0f) rows += "Quadros por segundo" to String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", f.frameRate)
        (f.codecs ?: f.sampleMimeType)?.let { rows += "Codec de vídeo" to it }
        if (f.bitrate > 0) rows += "Taxa de vídeo" to "${f.bitrate / 1000} kbps"
    }
    selectedFormat(t, C.TRACK_TYPE_AUDIO)?.let { f ->
        (f.codecs ?: f.sampleMimeType)?.let { rows += "Codec de áudio" to it }
        if (f.channelCount > 0) rows += "Canais" to f.channelCount.toString()
        if (f.sampleRate > 0) rows += "Taxa de amostragem" to "${f.sampleRate} Hz"
        if (f.bitrate > 0) rows += "Taxa de áudio" to "${f.bitrate / 1000} kbps"
    }
    return rows
}

private fun clampOffset(o: Offset, scale: Float, size: IntSize): Offset {
    val mx = size.width * (scale - 1f) / 2f
    val my = size.height * (scale - 1f) / 2f
    return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
}

// ---- vídeo -------------------------------------------------------------------------------------

@Composable
fun VideoPlayerUi(
    controller: MediaController?,
    title: String,
    fileSize: Long,
    immersive: Boolean,
    inPip: Boolean,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val act = ctx.findActivity()
    val ctl by rememberUpdatedState(controller)
    val info = rememberPlaybackInfo(controller)

    var controls by remember { mutableStateOf(true) }
    var touch by remember { mutableIntStateOf(0) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var fit by remember { mutableIntStateOf(0) }
    var orient by remember { mutableIntStateOf(0) }
    var hud by remember { mutableStateOf<String?>(null) }
    var abA by remember { mutableStateOf<Long?>(null) }
    var abB by remember { mutableStateOf<Long?>(null) }
    var showInfo by remember { mutableStateOf(false) }
    var audioMenu by remember { mutableStateOf(false) }

    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol) }
    var bright by remember {
        val b = act?.window?.attributes?.screenBrightness ?: -1f
        mutableFloatStateOf(if (b < 0f) 0.5f else b)
    }

    fun poke() { touch++ }

    LaunchedEffect(controls, info.playing, touch) {
        if (controls && info.playing) {
            delay(3500)
            controls = false
        }
    }
    LaunchedEffect(hud) {
        if (hud != null) {
            delay(900)
            hud = null
        }
    }
    DisposableEffect(orient) {
        act?.requestedOrientation = when (orient) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            2 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose { }
    }
    DisposableEffect(Unit) {
        val original = act?.window?.attributes?.screenBrightness ?: -1f
        onDispose {
            act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            act?.window?.let { w ->
                val lp = w.attributes
                lp.screenBrightness = original
                w.attributes = lp
            }
        }
    }
    LaunchedEffect(abA, abB, controller) {
        val a = abA
        val b = abB
        val c = controller
        if (a != null && b != null && c != null) {
            while (isActive) {
                if (c.currentPosition >= b) c.seekTo(a)
                delay(120)
            }
        }
    }

    fun seekBy(delta: Long) {
        val c = ctl ?: return
        var t = (c.currentPosition + delta).coerceAtLeast(0L)
        val d = c.duration
        if (d != C.TIME_UNSET) t = t.coerceAtMost(d)
        c.seekTo(t)
        hud = if (delta > 0) "+${delta / 1000} s" else "−${-delta / 1000} s"
    }

    fun togglePlay() {
        val c = ctl ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0)
            c.play()
        }
    }

    fun stepFrame(dir: Int) {
        val c = ctl ?: return
        c.pause()
        val fps = selectedFormat(c.currentTracks, C.TRACK_TYPE_VIDEO)?.frameRate?.takeIf { it > 0f } ?: 30f
        c.seekTo((c.currentPosition + dir * (1000f / fps)).toLong().coerceAtLeast(0L))
    }

    fun adjust(left: Boolean, delta: Float) {
        if (left) {
            bright = (bright + delta * 1.3f).coerceIn(0.02f, 1f)
            act?.window?.let { w ->
                val lp = w.attributes
                lp.screenBrightness = bright
                w.attributes = lp
            }
            hud = "Brilho ${(bright * 100).roundToInt()}%"
        } else {
            vol = (vol + delta * 1.3f).coerceIn(0f, 1f)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, (vol * maxVol).roundToInt(), 0)
            hud = "Volume ${(vol * 100).roundToInt()}%"
        }
    }

    fun enterPip() {
        val a = act ?: return
        val vs = ctl?.videoSize
        val w = vs?.width ?: 0
        val h = vs?.height ?: 0
        val ratio = if (w > 0 && h > 0) {
            val f = w.toFloat() / h
            when {
                f > 2.3f -> Rational(23, 10)
                f < 0.45f -> Rational(9, 20)
                else -> Rational(w, h)
            }
        } else Rational(16, 9)
        runCatching { a.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(ratio).build()) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            Modifier
                .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controls = !controls; poke() },
                    onDoubleTap = { o ->
                        if (scale > 1.01f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            val third = size.width / 3f
                            when {
                                o.x < third -> seekBy(-10_000)
                                o.x > 2 * third -> seekBy(10_000)
                                else -> togglePlay()
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val left = down.position.x < size.width / 2f
                    val slop = viewConfiguration.touchSlop
                    var mode = 0
                    var tx = 0f
                    var ty = 0f
                    do {
                        val ev = awaitPointerEvent()
                        val pressed = ev.changes.count { it.pressed }
                        if (pressed >= 2) {
                            mode = 1
                            scale = (scale * ev.calculateZoom()).coerceIn(1f, 5f)
                            offset = clampOffset(offset + ev.calculatePan(), scale, size)
                            ev.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (pressed == 1) {
                            val ch = ev.changes.first { it.pressed }
                            run {
                                val d = ch.positionChange()
                                if (mode == 0) {
                                    tx += d.x
                                    ty += d.y
                                    if (abs(ty) > slop && abs(ty) > abs(tx)) mode = if (scale > 1.01f) 1 else 2
                                    else if (abs(tx) > slop) mode = if (scale > 1.01f) 1 else 3
                                }
                                if (mode == 1 && scale > 1.01f) {
                                    offset = clampOffset(offset + d, scale, size)
                                    ch.consume()
                                } else if (mode == 2) {
                                    adjust(left, -d.y / size.height)
                                    ch.consume()
                                }
                            }
                        }
                    } while (ev.changes.any { it.pressed })
                    if (scale <= 1.01f) {
                        scale = 1f
                        offset = Offset.Zero
                    }
                }
            }
        ) {
        AndroidView(
            factory = { c ->
                (LayoutInflater.from(c).inflate(R.layout.nuvem_player_texture, null) as PlayerView).apply {
                    useController = false
                    keepScreenOn = true
                }
            },
            update = {
                it.player = controller
                it.resizeMode = when (fit) {
                    1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
        )
        }

        if (info.buffering && !inPip) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)

        hud?.let {
            if (!inPip) Surface(
                Modifier.align(Alignment.Center),
                shape = RoundedCornerShape(12.dp),
                color = Color(0x99000000),
                contentColor = Color.White
            ) { Text(it, Modifier.padding(horizontal = 18.dp, vertical = 10.dp), style = MaterialTheme.typography.titleMedium) }
        }

        if (controls && !inPip) {
            Column(Modifier.fillMaxSize()) {
                if (immersive) {
                    Row(
                        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = Color.White) }
                        Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        IconButton(onClick = { seekBy(-10_000); poke() }, Modifier.size(56.dp)) {
                            Icon(Icons.Filled.Replay10, "Voltar 10 segundos", Modifier.size(36.dp), tint = Color.White)
                        }
                        IconButton(
                            onClick = { togglePlay(); poke() },
                            Modifier.size(72.dp).clip(RoundedCornerShape(50)).background(Color(0x66000000))
                        ) {
                            Icon(
                                if (info.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                if (info.playing) "Pausar" else "Reproduzir",
                                Modifier.size(48.dp), tint = Color.White
                            )
                        }
                        IconButton(onClick = { seekBy(10_000); poke() }, Modifier.size(56.dp)) {
                            Icon(Icons.Filled.Forward10, "Avançar 10 segundos", Modifier.size(36.dp), tint = Color.White)
                        }
                    }
                }
                Column(
                    Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))).padding(bottom = 8.dp)
                ) {
                    SeekRow(info, controller, dark = true)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SpeedPill(info, controller, dark = true)
                        Pill(
                            when (fit) { 1 -> "Preencher"; 2 -> "Cortar"; else -> "Ajustar" },
                            Icons.Filled.AspectRatio, fit != 0, true
                        ) { fit = (fit + 1) % 3; poke() }
                        Pill(
                            when (orient) { 1 -> "Horizontal"; 2 -> "Vertical"; else -> "Rotação auto" },
                            Icons.Filled.ScreenRotation, orient != 0, true
                        ) { orient = (orient + 1) % 3; poke() }
                        Pill(
                            when {
                                abA == null -> "A-B"
                                abB == null -> "A ${fmtTime(abA!!)}"
                                else -> "A-B ${fmtTime(abA!!)}–${fmtTime(abB!!)}"
                            },
                            Icons.Filled.Repeat, abA != null, true
                        ) {
                            val now = controller?.currentPosition ?: 0L
                            when {
                                abA == null -> abA = now
                                abB == null -> if (now > abA!! + 500) abB = now else abA = now
                                else -> { abA = null; abB = null }
                            }
                            poke()
                        }
                        Pill("◀ Quadro", dark = true) { stepFrame(-1); poke() }
                        Pill("Quadro ▶", dark = true) { stepFrame(1); poke() }
                        val audioGroups = info.tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                        val audioCount = audioGroups.sumOf { it.length }
                        if (audioCount > 1) {
                            Box {
                                Pill("Áudio", Icons.Filled.Audiotrack, false, true) { audioMenu = true; poke() }
                                DropdownMenu(expanded = audioMenu, onDismissRequest = { audioMenu = false }) {
                                    var n = 0
                                    for (g in audioGroups) {
                                        for (i in 0 until g.length) {
                                            n++
                                            val f = g.getTrackFormat(i)
                                            val name = f.label ?: f.language?.takeIf { it != "und" } ?: "Faixa $n"
                                            DropdownMenuItem(
                                                text = { Text(name) },
                                                onClick = {
                                                    ctl?.let { c ->
                                                        c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
                                                            .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i)).build()
                                                    }
                                                    audioMenu = false
                                                },
                                                trailingIcon = { if (g.isTrackSelected(i)) Icon(Icons.Filled.Check, null) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (scale > 1.01f) Pill("${(scale * 100).roundToInt()}%", Icons.Filled.ZoomIn, true, true) {
                            scale = 1f
                            offset = Offset.Zero
                        }
                        Pill("Informações", Icons.Filled.Info, false, true) { showInfo = true; poke() }
                        Pill("Miniatura", Icons.Filled.PictureInPictureAlt, false, true) { enterPip() }
                    }
                }
            }
        }
    }

    if (showInfo) InfoDialog("Informações do vídeo", technicalRows(title, fileSize, info.dur, info.tracks)) { showInfo = false }
}

// ---- música ------------------------------------------------------------------------------------

@Composable
fun AudioPlayerUi(
    controller: MediaController?,
    fileName: String,
    fileSize: Long,
    info: PlaybackInfo,
    onPrev: (() -> Unit)?,
    onNext: (() -> Unit)?
) {
    val ctl by rememberUpdatedState(controller)
    var showInfo by remember { mutableStateOf(false) }
    var sleepMenu by remember { mutableStateOf(false) }

    val meta = info.meta
    val baseName = fileName.substringBeforeLast('.')
    val title = meta.title?.toString()?.takeIf { it.isNotBlank() && it != fileName } ?: baseName
    val artist = (meta.artist ?: meta.albumArtist)?.toString()?.takeIf { it.isNotBlank() }
    val album = meta.albumTitle?.toString()?.takeIf { it.isNotBlank() }
    val subtitle = listOfNotNull(artist, album).joinToString(" • ")

    val art by produceState<ImageBitmap?>(null, meta.artworkData) {
        val data = meta.artworkData
        value = if (data == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(data, 0, data.size, o)
                var s = 1
                while (o.outWidth / s > 1200 || o.outHeight / s > 1200) s *= 2
                val o2 = BitmapFactory.Options().apply { inSampleSize = s }
                BitmapFactory.decodeByteArray(data, 0, data.size, o2)?.asImageBitmap()
            }.getOrNull()
        }
    }

    val sleepEnd = PlaybackService.current?.sleepEndsAt ?: 0L
    val sleepLeft = if (sleepEnd > 0) ((sleepEnd - System.currentTimeMillis()) / 60_000L + 1).toInt() else 0

    fun togglePlay() {
        val c = ctl ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0)
            c.play()
        }
    }
    fun seekBy(delta: Long) {
        val c = ctl ?: return
        var t = (c.currentPosition + delta).coerceAtLeast(0L)
        val d = c.duration
        if (d != C.TIME_UNSET) t = t.coerceAtMost(d)
        c.seekTo(t)
    }

    @Composable
    fun Cover(modifier: Modifier) {
        val a = art
        Box(
            modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))),
            contentAlignment = Alignment.Center
        ) {
            if (a != null) Image(a, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Filled.MusicNote, null, Modifier.fillMaxSize(0.45f), tint = Color.White.copy(alpha = 0.9f))
        }
    }

    @Composable
    fun Panel(modifier: Modifier) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(16.dp))
            SeekRow(info, controller, dark = false)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = { onPrev?.invoke() }, enabled = onPrev != null, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Filled.SkipPrevious, "Faixa anterior", Modifier.size(32.dp))
                }
                IconButton(onClick = { seekBy(-10_000) }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Filled.Replay10, "Voltar 10 segundos", Modifier.size(30.dp))
                }
                IconButton(
                    onClick = { togglePlay() },
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        if (info.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (info.playing) "Pausar" else "Reproduzir",
                        Modifier.size(42.dp), tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                IconButton(onClick = { seekBy(10_000) }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Filled.Forward10, "Avançar 10 segundos", Modifier.size(30.dp))
                }
                IconButton(onClick = { onNext?.invoke() }, enabled = onNext != null, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Filled.SkipNext, "Próxima faixa", Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SpeedPill(info, controller, dark = false)
                Pill(
                    if (info.repeatOne) "Repetir faixa" else "Repetir",
                    if (info.repeatOne) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    info.repeatOne, false
                ) { ctl?.repeatMode = if (info.repeatOne) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE }
                Box {
                    Pill(if (sleepLeft > 0) "Dormir em $sleepLeft min" else "Dormir", Icons.Filled.Bedtime, sleepLeft > 0, false) { sleepMenu = true }
                    DropdownMenu(expanded = sleepMenu, onDismissRequest = { sleepMenu = false }) {
                        for (m in listOf(0, 15, 30, 45, 60, 90)) {
                            DropdownMenuItem(
                                text = { Text(if (m == 0) "Desativar" else "$m minutos") },
                                onClick = { PlaybackService.current?.setSleepTimer(m); sleepMenu = false }
                            )
                        }
                    }
                }
                Pill("Informações", Icons.Filled.Info, false, false) { showInfo = true }
            }
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.background)))
    ) {
        val wide = maxWidth > maxHeight
        if (wide) {
            Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(Modifier.fillMaxHeight())
                Panel(Modifier.weight(1f).verticalScroll(rememberScrollState()))
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Cover(Modifier.widthIn(max = 340.dp).fillMaxWidth())
                Spacer(Modifier.height(24.dp))
                Panel(Modifier.fillMaxWidth())
            }
        }
    }

    if (showInfo) {
        val rows = mutableListOf<Pair<String, String>>()
        rows += "Título" to title
        artist?.let { rows += "Artista" to it }
        album?.let { rows += "Álbum" to it }
        meta.albumArtist?.toString()?.takeIf { it.isNotBlank() && it != artist }?.let { rows += "Artista do álbum" to it }
        meta.genre?.toString()?.takeIf { it.isNotBlank() }?.let { rows += "Gênero" to it }
        (meta.recordingYear ?: meta.releaseYear)?.let { rows += "Ano" to it.toString() }
        meta.trackNumber?.let { rows += "Faixa" to it.toString() }
        rows += technicalRows(fileName, fileSize, info.dur, info.tracks)
        InfoDialog("Informações da música", rows) { showInfo = false }
    }
}
