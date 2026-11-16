package com.willian.nuvem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.willian.nuvem.data.Account
import com.willian.nuvem.data.CloudFile
import com.willian.nuvem.data.FileKind
import com.willian.nuvem.data.FileTypes
import com.willian.nuvem.data.Provider
import java.io.File

/** O que o Coil deve carregar como miniatura (ou null quando só dá para mostrar o ícone). */
fun thumbModel(account: Account, f: CloudFile, px: Int): Any? {
    if (f.isFolder) return null
    val kind = FileTypes.kind(f)
    if (account.provider == Provider.LOCAL) {
        return if (kind == FileKind.IMAGE || kind == FileKind.VIDEO) File(f.id) else null
    }
    val t = f.thumb
        ?: return if (kind == FileKind.IMAGE && f.size in 1..12_000_000) CloudThumb(account, f) else null
    return if (account.provider == Provider.GOOGLE) t.replace(Regex("=s\\d+$"), "=s$px") else t
}

/** Quadrado com a miniatura; enquanto carrega (ou se não houver) aparece o ícone do tipo. */
@Composable
fun Thumb(account: Account, f: CloudFile, iconSize: Dp, px: Int, modifier: Modifier = Modifier) {
    val kind = FileTypes.kind(f)
    val model = remember(f.id, f.thumb, px) { thumbModel(account, f, px) }
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).background(kindColor(kind).copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(kindIcon(kind), null, tint = kindColor(kind), modifier = Modifier.size(iconSize))
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Célula da visualização em miniaturas. */
@Composable
fun GridItem(
    account: Account,
    f: CloudFile,
    mode: ViewMode,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onTrash: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(4.dp)) {
        Box {
            Thumb(account, f, mode.icon.dp, mode.px, Modifier.fillMaxWidth().aspectRatio(1f))
            Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.40f))
                        .clickable { menu = true },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.MoreVert, "Opções", tint = Color.White, modifier = Modifier.size(18.dp)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (!f.isFolder) {
                        DropdownMenuItem(
                            text = { Text("Baixar para o celular") },
                            leadingIcon = { Icon(Icons.Filled.Download, null) },
                            onClick = { menu = false; onDownload() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Mover para a lixeira") },
                        leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        onClick = { menu = false; onTrash() }
                    )
                }
            }
        }
        Text(
            f.name,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontSize = if (mode == ViewMode.GRID_S) 11.sp else 13.sp,
            lineHeight = if (mode == ViewMode.GRID_S) 14.sp else 16.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
