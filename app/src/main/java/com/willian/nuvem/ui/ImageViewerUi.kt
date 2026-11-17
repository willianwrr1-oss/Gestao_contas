package com.willian.nuvem.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Scale
import coil.size.Size
import coil.transform.Transformation
import com.willian.nuvem.data.fmtSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/** Apresentação de slides: fica ligada enquanto o usuário navega entre as fotos. */
object SlideshowState {
    var on by mutableStateOf(false)
}

private class RotateTransformation(private val degrees: Int) : Transformation {
    override val cacheKey: String = "nuvem-rot-$degrees"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (degrees % 360 == 0) return input
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(input, 0, 0, input.width, input.height, m, true)
    }
}

private class ExifResult(val rows: List<Pair<String, String>>, val gps: Pair<Double, Double>?)

private fun readExif(f: File, name: String): ExifResult {
    val br = Locale.forLanguageTag("pt-BR")
    val rows = mutableListOf<Pair<String, String>>()
    rows += "Arquivo" to name
    rows += "Tamanho" to fmtSize(f.length())
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.absolutePath, o)
    if (o.outWidth > 0 && o.outHeight > 0) {
        val mp = o.outWidth.toDouble() * o.outHeight / 1_000_000.0
        rows += "Dimensões" to "${o.outWidth} × ${o.outHeight} (${String.format(br, "%.1f", mp)} MP)"
    }
    val e = runCatching { ExifInterface(f.absolutePath) }.getOrNull() ?: return ExifResult(rows, null)
    fun t(tag: String): String? = e.getAttribute(tag)?.trim()?.takeIf { it.isNotEmpty() }

    val cam = listOfNotNull(t(ExifInterface.TAG_MAKE), t(ExifInterface.TAG_MODEL)).joinToString(" ")
    if (cam.isNotEmpty()) rows += "Câmera" to cam
    val lens = t("LensModel")
    if (lens != null) rows += "Lente" to lens
    val date = t(ExifInterface.TAG_DATETIME_ORIGINAL) ?: t(ExifInterface.TAG_DATETIME)
    if (date != null) rows += "Data da foto" to date

    val exp = e.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
    if (exp > 0) rows += "Velocidade" to (if (exp < 1.0) "1/${(1.0 / exp).roundToInt()} s" else String.format(br, "%.1f s", exp))
    val fn = e.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
    if (fn > 0) rows += "Abertura" to String.format(br, "f/%.1f", fn)
    val iso = t("PhotographicSensitivity") ?: t("ISOSpeedRatings") ?: t(ExifInterface.TAG_ISO)
    if (iso != null) rows += "ISO" to iso
    val fl = e.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
    if (fl > 0) {
        val eq = t(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM)
        rows += "Distância focal" to (String.format(br, "%.1f mm", fl) + if (eq != null) " (equiv. 35 mm: $eq mm)" else "")
    }
    val flash = e.getAttributeInt(ExifInterface.TAG_FLASH, -1)
    if (flash >= 0) rows += "Flash" to (if ((flash and 1) == 1) "Disparado" else "Não disparado")
    val wb = e.getAttributeInt(ExifInterface.TAG_WHITE_BALANCE, -1)
    if (wb >= 0) rows += "Balanço de branco" to (if (wb == 0) "Automático" else "Manual")
    val sw = t(ExifInterface.TAG_SOFTWARE)
    if (sw != null) rows += "Software" to sw

    var gps: Pair<Double, Double>? = null
    val ll = FloatArray(2)
    if (e.getLatLong(ll)) {
        gps = ll[0].toDouble() to ll[1].toDouble()
        rows += "Local (GPS)" to String.format(Locale.US, "%.5f, %.5f", ll[0], ll[1])
        val alt = e.getAltitude(Double.NaN)
        if (!alt.isNaN()) rows += "Altitude" to String.format(br, "%.0f m", alt)
    }
    return ExifResult(rows, gps)
}

@Composable
fun ImageViewerUi(f: File, name: String, onPrev: (() -> Unit)?, onNext: (() -> Unit)?) {
    val ctx = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var turns by remember { mutableIntStateOf(0) }
    var chrome by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }
    val slide = SlideshowState.on

    LaunchedEffect(slide, f.path) {
        if (slide) {
            delay(4000)
            if (onNext != null) onNext() else SlideshowState.on = false
        }
    }

    val exif by androidx.compose.runtime.produceState<ExifResult?>(null, f.path, showInfo) {
        value = if (showInfo) withContext(Dispatchers.IO) { runCatching { readExif(f, name) }.getOrNull() } else null
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = ImageRequest.Builder(ctx)
                .data(f)
                .size(2600, 2600)
                .scale(Scale.FIT)
                .transformations(RotateTransformation(((turns % 4) + 4) % 4 * 90))
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { chrome = !chrome },
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else scale = 2.5f
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 8f)
                        if (scale <= 1f) {
                            offset = Offset.Zero
                        } else {
                            val mx = size.width * (scale - 1f) / 2f
                            val my = size.height * (scale - 1f) / 2f
                            val n = offset + pan
                            offset = Offset(n.x.coerceIn(-mx, mx), n.y.coerceIn(-my, my))
                        }
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )

        if (chrome) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                Pill("Girar", Icons.Filled.RotateLeft, false, true) { turns -= 1 }
                Pill("Girar", Icons.Filled.RotateRight, false, true) { turns += 1 }
                Pill(if (slide) "Parar slides" else "Slides", if (slide) Icons.Filled.Pause else Icons.Filled.Slideshow, slide, true) {
                    SlideshowState.on = !slide
                }
                if (scale > 1.01f) Pill("${(scale * 100).roundToInt()}%", Icons.Filled.ZoomIn, true, true) {
                    scale = 1f
                    offset = Offset.Zero
                }
                Pill("Informações", Icons.Filled.Info, false, true) { showInfo = true }
            }
        }
    }

    if (showInfo) {
        val r = exif
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("Informações da foto") },
            text = {
                if (r == null) Text("Lendo...")
                else Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((k, v) in r.rows) {
                        Column {
                            Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(v, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Fechar") } },
            dismissButton = {
                val g = r?.gps
                if (g != null) TextButton(onClick = {
                    runCatching {
                        ctx.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("geo:${g.first},${g.second}?q=${g.first},${g.second}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }) { Text("Ver no mapa") }
            }
        )
    }
}
