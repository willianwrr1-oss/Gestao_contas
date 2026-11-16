package com.willian.nuvem.data

import android.webkit.MimeTypeMap
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Provider(val label: String) {
    GOOGLE("Google Drive"),
    ONEDRIVE("OneDrive"),
    MEGA("Mega"),
    LOCAL("Este celular")
}

data class Account(val id: String, val provider: Provider, val name: String, val email: String)

val LOCAL_ACCOUNT = Account("local", Provider.LOCAL, "Este celular", "")

/** Arquivo ou pasta em qualquer provedor. `id` é específico de cada provedor. */
data class CloudFile(
    val id: String,
    val name: String,
    val isFolder: Boolean,
    val size: Long = -1,
    val modified: Long = 0,
    val mime: String = ""
)

data class Quota(val used: Long, val total: Long) {
    val fraction: Float get() = if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else 0f
}

enum class FileKind { FOLDER, IMAGE, AUDIO, VIDEO, PDF, TEXT, DOCX, XLSX, PPTX, OTHER }

object FileTypes {
    fun ext(name: String) = name.substringAfterLast('.', "").lowercase()

    fun kind(f: CloudFile): FileKind {
        if (f.isFolder) return FileKind.FOLDER
        return when (ext(f.name)) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp" -> FileKind.IMAGE
            "mp3", "m4a", "aac", "wav", "flac", "ogg", "oga", "opus", "amr", "mka" -> FileKind.AUDIO
            "mp4", "mkv", "webm", "3gp", "mov", "avi", "m4v", "ts" -> FileKind.VIDEO
            "pdf" -> FileKind.PDF
            "txt", "md", "csv", "json", "xml", "log", "ini", "yaml", "yml", "html", "htm",
            "css", "js", "kt", "java", "py", "sh", "sql", "conf", "properties", "tsv", "srt", "lrc" -> FileKind.TEXT
            "docx" -> FileKind.DOCX
            "xlsx" -> FileKind.XLSX
            "pptx" -> FileKind.PPTX
            else -> FileKind.OTHER
        }
    }

    fun mime(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext(name)) ?: "application/octet-stream"
}

private val ptBr: Locale = Locale.forLanguageTag("pt-BR")

fun fmtSize(b: Long): String {
    if (b < 0) return ""
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = b.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return if (i == 0) "$b B" else String.format(ptBr, "%.1f %s", v, units[i])
}

fun fmtTime(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(ptBr, "%d:%02d:%02d", h, m, sec) else String.format(ptBr, "%d:%02d", m, sec)
}

fun fmtDate(ms: Long): String =
    if (ms <= 0) "" else SimpleDateFormat("dd/MM/yyyy HH:mm", ptBr).format(Date(ms))

fun InputStream.skipFully(n: Long) {
    var left = n
    val buf = ByteArray(8192)
    while (left > 0) {
        val r = read(buf, 0, minOf(left, buf.size.toLong()).toInt())
        if (r < 0) break
        left -= r
    }
}

fun InputStream.readFully(buf: ByteArray, n: Int) {
    var o = 0
    while (o < n) {
        val r = read(buf, o, n - o)
        if (r < 0) throw IOException("Fim inesperado do arquivo")
        o += r
    }
}
