package com.willian.nuvem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.willian.nuvem.data.FileKind
import com.willian.nuvem.data.Provider
import com.willian.nuvem.data.Quota
import com.willian.nuvem.data.fmtSize

fun kindIcon(k: FileKind): ImageVector = when (k) {
    FileKind.FOLDER -> Icons.Filled.Folder
    FileKind.IMAGE -> Icons.Filled.Image
    FileKind.AUDIO -> Icons.Filled.MusicNote
    FileKind.VIDEO -> Icons.Filled.Movie
    FileKind.PDF -> Icons.Filled.PictureAsPdf
    FileKind.TEXT, FileKind.DOCX -> Icons.Filled.Description
    FileKind.XLSX -> Icons.Filled.TableChart
    FileKind.PPTX -> Icons.Filled.Slideshow
    FileKind.OTHER -> Icons.Filled.InsertDriveFile
}

fun kindColor(k: FileKind): Color = when (k) {
    FileKind.FOLDER -> Color(0xFFF9A825)
    FileKind.IMAGE -> Color(0xFF8E24AA)
    FileKind.AUDIO -> Color(0xFFE53935)
    FileKind.VIDEO -> Color(0xFFD81B60)
    FileKind.PDF -> Color(0xFFD32F2F)
    FileKind.TEXT -> Color(0xFF546E7A)
    FileKind.DOCX -> Color(0xFF1E66D0)
    FileKind.XLSX -> Color(0xFF2E7D32)
    FileKind.PPTX -> Color(0xFFEF6C00)
    FileKind.OTHER -> Color(0xFF78909C)
}

fun providerIcon(p: Provider): ImageVector = if (p == Provider.LOCAL) Icons.Filled.PhoneAndroid else Icons.Filled.Cloud

fun providerColor(p: Provider): Color = when (p) {
    Provider.GOOGLE -> Color(0xFF1A73E8)
    Provider.ONEDRIVE -> Color(0xFF0078D4)
    Provider.MEGA -> Color(0xFFD9272E)
    Provider.LOCAL -> Color(0xFF2E7D32)
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color, size: Int = 44) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.55f).dp))
    }
}

@Composable
fun QuotaLine(q: Quota?) {
    if (q == null) return
    Column(Modifier.fillMaxWidth()) {
        if (q.total > 0) {
            LinearProgressIndicator(progress = { q.fraction }, modifier = Modifier.fillMaxWidth())
            Text(
                "${fmtSize(q.used)} usados de ${fmtSize(q.total)}  •  ${fmtSize((q.total - q.used).coerceAtLeast(0))} livres",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                "${fmtSize(q.used)} usados",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
