package com.willian.nuvem.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.willian.nuvem.Nuvem
import com.willian.nuvem.data.Account
import com.willian.nuvem.data.CloudFile
import com.willian.nuvem.data.FileTypes
import com.willian.nuvem.data.fmtDate
import com.willian.nuvem.data.fmtSize
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(account: Account, onBack: () -> Unit, onChanged: () -> Unit, onMessage: (String) -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val provider = remember(account.id) { Nuvem.provider(account) }
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<CloudFile>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<CloudFile?>(null) }

    LaunchedEffect(tick) {
        if (provider.supportsTrashList) {
            try {
                items = provider.listTrash()
                loadError = null
            } catch (e: Exception) {
                loadError = e.message
            }
        }
    }

    fun act(ok: String, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                onMessage(ok)
                tick++
                onChanged()
            } catch (e: Exception) {
                onMessage(e.message ?: "Erro")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lixeira") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                actions = {
                    if (items?.isNotEmpty() == true) TextButton(onClick = { confirmEmpty = true }) { Text("Esvaziar") }
                }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val list = items
            when {
                !provider.supportsTrashList -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Os itens excluídos do OneDrive vão para a lixeira do próprio OneDrive. " +
                            "A Microsoft não permite ver nem esvaziar essa lixeira por aplicativos; use o site.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://onedrive.live.com/")))
                    }) { Text("Abrir onedrive.live.com") }
                }
                loadError != null -> Text(loadError ?: "", Modifier.align(Alignment.Center).padding(24.dp), color = MaterialTheme.colorScheme.error)
                list == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                list.isEmpty() -> Text("A lixeira está vazia", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.id }) { f ->
                        val kind = FileTypes.kind(f)
                        Column {
                            ListItem(
                                leadingContent = { Icon(kindIcon(kind), null, tint = kindColor(kind)) },
                                headlineContent = { Text(f.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    val s = listOf(if (f.isFolder) "" else fmtSize(f.size), fmtDate(f.modified))
                                        .filter { it.isNotEmpty() }.joinToString("  •  ")
                                    if (s.isNotEmpty()) Text(s)
                                },
                                trailingContent = {
                                    IconButton(onClick = { act("Restaurado.") { provider.restore(f) } }) {
                                        Icon(Icons.Filled.RestoreFromTrash, "Restaurar")
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                            Row(Modifier.padding(start = 16.dp)) {
                                TextButton(onClick = { confirmDelete = f }) {
                                    Icon(Icons.Filled.DeleteForever, null)
                                    Text("  Excluir definitivamente")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Esvaziar a lixeira?") },
            text = { Text("Todos os itens serão apagados para sempre.") },
            confirmButton = {
                TextButton(onClick = { confirmEmpty = false; act("Lixeira esvaziada.") { provider.emptyTrash() } }) { Text("Esvaziar") }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Cancelar") } }
        )
    }

    confirmDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Excluir definitivamente?") },
            text = { Text("${f.name}\n\nEsta ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; act("Excluído.") { provider.deleteForever(f) } }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") } }
        )
    }
}
