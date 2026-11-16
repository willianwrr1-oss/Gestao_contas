package com.willian.nuvem.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.HorizontalDivider
import com.willian.nuvem.data.Account
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.willian.nuvem.data.CloudFile
import com.willian.nuvem.data.FileKind
import com.willian.nuvem.data.FileTypes
import com.willian.nuvem.data.Provider
import com.willian.nuvem.data.fmtDate
import com.willian.nuvem.data.fmtSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    ctl: BrowserController,
    onExit: () -> Unit,
    onOpen: (CloudFile) -> Unit,
    onTrash: () -> Unit
) {
    LaunchedEffect(ctl) {
        if (!ctl.started) {
            ctl.started = true
            ctl.load()
            ctl.loadQuota()
        }
    }
    var searching by remember { mutableStateOf(ctl.query.isNotEmpty()) }
    var sortMenu by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }
    var toTrash by remember { mutableStateOf<CloudFile?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) ctl.upload(uris)
    }

    BackHandler {
        if (searching) {
            searching = false
            ctl.clearSearch()
        } else if (!ctl.up()) onExit()
    }

    val isLocal = ctl.account.provider == Provider.LOCAL

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        val focus = remember { FocusRequester() }
                        TextField(
                            value = ctl.query,
                            onValueChange = { ctl.search(it) },
                            placeholder = { Text(if (isLocal) "Pesquisar em todo o celular" else "Pesquisar") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus)
                        )
                        LaunchedEffect(Unit) { focus.requestFocus() }
                    } else {
                        Text(
                            ctl.stack.lastOrNull()?.name ?: ctl.account.email.ifEmpty { ctl.account.name },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (searching) {
                            searching = false
                            ctl.clearSearch()
                        } else if (!ctl.up()) onExit()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
                actions = {
                    if (!searching) {
                        IconButton(onClick = { searching = true }) { Icon(Icons.Filled.Search, "Pesquisar") }
                    } else if (ctl.query.isNotEmpty()) {
                        IconButton(onClick = { ctl.clearSearch() }) { Icon(Icons.Filled.Close, "Limpar") }
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(if (ctl.viewMode == ViewMode.LIST) Icons.Filled.Sort else Icons.Filled.GridView, "Exibição")
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            Text(
                                "Ordenar por",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            SortBy.values().forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.label) },
                                    onClick = { ctl.changeSort(s); sortMenu = false },
                                    trailingIcon = { if (ctl.sort == s) Icon(Icons.Filled.Check, null) }
                                )
                            }
                            HorizontalDivider()
                            Text(
                                "Visualização",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            ViewMode.values().forEach { v ->
                                DropdownMenuItem(
                                    text = { Text(v.label) },
                                    onClick = { ctl.changeView(v); sortMenu = false },
                                    trailingIcon = { if (ctl.viewMode == v) Icon(Icons.Filled.Check, null) }
                                )
                            }
                        }
                    }
                    IconButton(onClick = { ctl.load(); ctl.loadQuota() }) { Icon(Icons.Filled.Refresh, "Atualizar") }
                    IconButton(onClick = onTrash) { Icon(Icons.Filled.Delete, "Lixeira") }
                }
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { addMenu = true }) { Icon(Icons.Filled.Add, "Adicionar") }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (isLocal) "Copiar arquivos para cá" else "Enviar arquivos") },
                        leadingIcon = { Icon(Icons.Filled.Upload, null) },
                        onClick = { addMenu = false; picker.launch(arrayOf("*/*")) }
                    )
                    DropdownMenuItem(
                        text = { Text("Nova pasta") },
                        leadingIcon = { Icon(Icons.Filled.CreateNewFolder, null) },
                        onClick = { addMenu = false; newFolder = true }
                    )
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (!searching) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { ctl.goTo(0) }) { Text("Início") }
                    ctl.stack.forEachIndexed { i, f ->
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp))
                        TextButton(onClick = { ctl.goTo(i + 1) }) { Text(f.name, maxLines = 1) }
                    }
                }
                if (ctl.stack.isEmpty()) {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { QuotaLine(ctl.quota) }
                }
            }

            val list = ctl.visible
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val err = ctl.error
                when {
                    err != null -> Column(
                        Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Text(err, color = MaterialTheme.colorScheme.error)
                        Button(onClick = { ctl.load() }) { Text("Tentar de novo") }
                    }
                    ctl.loading && list.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    list.isEmpty() -> Text(
                        if (ctl.query.isNotBlank()) "Nada encontrado" else "Pasta vazia",
                        Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ctl.viewMode == ViewMode.LIST -> LazyColumn(Modifier.fillMaxSize(), state = ctl.listState) {
                        items(list, key = { it.id }) { f ->
                            FileRow(
                                ctl.account, f,
                                onClick = { if (f.isFolder) ctl.enter(f) else onOpen(f) },
                                onDownload = { ctl.download(f) },
                                onTrash = { toTrash = f }
                            )
                        }
                        item { Spacer(Modifier.height(88.dp)) }
                    }
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(ctl.viewMode.cell.dp),
                        modifier = Modifier.fillMaxSize(),
                        state = ctl.gridState,
                        contentPadding = PaddingValues(8.dp, 4.dp, 8.dp, 96.dp)
                    ) {
                        gridItems(list, key = { it.id }) { f ->
                            GridItem(
                                ctl.account, f, ctl.viewMode,
                                onClick = { if (f.isFolder) ctl.enter(f) else onOpen(f) },
                                onDownload = { ctl.download(f) },
                                onTrash = { toTrash = f }
                            )
                        }
                    }
                }
                if (ctl.loading && list.isNotEmpty()) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
            }

            ctl.transfer?.let { t ->
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(t.label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val p = t.progress
                        if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                        else LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                }
            }
        }
    }

    if (newFolder) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newFolder = false },
            title = { Text("Nova pasta") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome") }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { ctl.mkdir(name); newFolder = false }) { Text("Criar") }
            },
            dismissButton = { TextButton(onClick = { newFolder = false }) { Text("Cancelar") } }
        )
    }

    toTrash?.let { f ->
        AlertDialog(
            onDismissRequest = { toTrash = null },
            title = { Text("Mover para a lixeira?") },
            text = { Text(f.name) },
            confirmButton = { TextButton(onClick = { ctl.trash(f); toTrash = null }) { Text("Mover") } },
            dismissButton = { TextButton(onClick = { toTrash = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun FileRow(account: Account, f: CloudFile, onClick: () -> Unit, onDownload: () -> Unit, onTrash: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val kind = FileTypes.kind(f)
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Thumb(account, f, 26.dp, 128, Modifier.size(44.dp)) },
        headlineContent = { Text(f.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val s = listOf(if (f.isFolder) "" else fmtSize(f.size), fmtDate(f.modified)).filter { it.isNotEmpty() }.joinToString("  •  ")
            if (s.isNotEmpty()) Text(s)
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Opções") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (kind != FileKind.FOLDER) {
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
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
