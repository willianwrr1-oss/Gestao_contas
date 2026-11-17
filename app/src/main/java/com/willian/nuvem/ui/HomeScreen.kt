package com.willian.nuvem.ui

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Surface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.willian.nuvem.Nuvem
import com.willian.nuvem.data.Account
import com.willian.nuvem.data.LOCAL_ACCOUNT
import com.willian.nuvem.data.Provider
import com.willian.nuvem.data.Quota
import com.willian.nuvem.data.fmtSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel, onAdd: (Provider) -> Unit) {
    val ctx = LocalContext.current
    val accounts by Nuvem.accounts.flow.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var showMega by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Account?>(null) }
    var editing by remember { mutableStateOf<Account?>(null) }
    val labels by Nuvem.accounts.labels.collectAsStateWithLifecycle()
    var granted by remember { mutableStateOf(Environment.isExternalStorageManager()) }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) granted = Environment.isExternalStorageManager()
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Minhas nuvens", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { vm.toggleLock() }) {
                        Icon(
                            if (vm.lockOn) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            contentDescription = "Bloqueio por digital ou senha"
                        )
                    }
                    IconButton(onClick = { vm.cycleTheme() }) {
                        Icon(
                            when (vm.themeMode) {
                                1 -> Icons.Filled.LightMode
                                2 -> Icons.Filled.DarkMode
                                else -> Icons.Filled.BrightnessAuto
                            },
                            contentDescription = "Alternar tema"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Adicionar conta") }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                val quota by produceState<Quota?>(null, granted) {
                    value = if (granted) runCatching { Nuvem.local.quota() }.getOrNull() else null
                }
                Card(
                    onClick = {
                        if (granted) vm.push(Screen.Browser(LOCAL_ACCOUNT))
                        else ctx.startActivity(
                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                        )
                    },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(providerIcon(Provider.LOCAL), providerColor(Provider.LOCAL))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Este celular", style = MaterialTheme.typography.titleMedium)
                            if (granted) QuotaLine(quota)
                            else Text(
                                "Toque para permitir o acesso a todos os arquivos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            labels[LOCAL_ACCOUNT.id]?.let { LabelTag(it) }
                        }
                        IconButton(onClick = { editing = LOCAL_ACCOUNT }) { Icon(Icons.Filled.Edit, "Etiqueta") }
                    }
                }
            }

            for (p in listOf(Provider.GOOGLE, Provider.ONEDRIVE, Provider.MEGA)) {
                val group = accounts.filter { it.provider == p }
                if (group.isEmpty()) continue
                item(key = "h_${p.name}") {
                    Text(
                        p.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp, start = 4.dp)
                    )
                }
                items(group.chunked(2), key = { "g_" + it.first().id }) { pair ->
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (a in pair) {
                            AccountTile(
                                a, labels[a.id],
                                Modifier.weight(1f).fillMaxHeight(),
                                onOpen = { vm.push(Screen.Browser(a)) },
                                onEdit = { editing = a },
                                onRemove = { removing = a }
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            if (accounts.isEmpty()) {
                item {
                    Text(
                        "Adicione suas contas do Google Drive, OneDrive e Mega. Você pode ter várias contas do mesmo serviço.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Adicionar conta") },
            text = {
                Column {
                    for (p in listOf(Provider.GOOGLE, Provider.ONEDRIVE, Provider.MEGA)) {
                        ListItem(
                            modifier = Modifier.clickable {
                                showAdd = false
                                if (p == Provider.MEGA) showMega = true else onAdd(p)
                            },
                            leadingContent = { IconBadge(providerIcon(p), providerColor(p), 40) },
                            headlineContent = { Text(p.label) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAdd = false }) { Text("Cancelar") } }
        )
    }

    if (showMega) MegaLoginDialog(
        onDismiss = { showMega = false },
        onLogin = { e, p, c -> showMega = false; vm.addMega(e, p, c) }
    )

    editing?.let { a ->
        LabelDialog(
            title = a.email.ifEmpty { a.name },
            initial = labels[a.id].orEmpty(),
            onDismiss = { editing = null },
            onSave = { Nuvem.accounts.setLabel(a.id, it); editing = null }
        )
    }

    removing?.let { a ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remover conta?") },
            text = { Text("A conta ${a.email.ifEmpty { a.name }} será removida deste app. Seus arquivos na nuvem não são apagados.") },
            confirmButton = { TextButton(onClick = { vm.removeAccount(a); removing = null }) { Text("Remover") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun UsageRing(q: Quota?, diameter: Dp = 72.dp) {
    val frac = q?.takeIf { it.total > 0 }?.fraction ?: 0f
    val color = when {
        frac >= 0.9f -> MaterialTheme.colorScheme.error
        frac >= 0.8f -> Color(0xFFB26A00)
        else -> MaterialTheme.colorScheme.primary
    }
    val track = MaterialTheme.colorScheme.outlineVariant
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 7.dp.toPx()
            val arc = Size(this.size.width - w, this.size.height - w)
            val tl = Offset(w / 2, w / 2)
            drawArc(track, 0f, 360f, false, topLeft = tl, size = arc, style = Stroke(w))
            if (frac > 0f) drawArc(color, -90f, 360f * frac, false, topLeft = tl, size = arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        Text(
            when {
                q == null -> "…"
                q.total > 0 -> "${(frac * 100).roundToInt()}%"
                else -> fmtSize(q.used)
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun LabelTag(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.padding(top = 6.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountTile(a: Account, label: String?, modifier: Modifier, onOpen: () -> Unit, onEdit: () -> Unit, onRemove: () -> Unit) {
    val quota by produceState<Quota?>(null, a.id) { value = runCatching { Nuvem.provider(a).quota() }.getOrNull() }
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = onOpen,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                UsageRing(quota)
                Spacer(Modifier.height(8.dp))
                Text(
                    a.email.ifEmpty { a.name }.substringBefore("@"),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                val q = quota
                if (q != null && q.total > 0) {
                    Text(
                        "${fmtSize((q.total - q.used).coerceAtLeast(0))} livres",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!label.isNullOrBlank()) LabelTag(label)
            }
            Box(Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.MoreVert, "Opções") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Etiqueta") }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Remover conta") }, onClick = { menu = false; onRemove() })
                }
            }
        }
    }
}

@Composable
private fun LabelDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Etiqueta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 80) text = it },
                    label = { Text("Anotação") },
                    placeholder = { Text("Ex.: fotos da família") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Deixe em branco para apagar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun MegaLoginDialog(onDismiss: () -> Unit, onLogin: (String, String, String?) -> Unit) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Entrar na Mega") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = email, onValueChange = { email = it }, label = { Text("E-mail") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it }, label = { Text("Senha") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = code, onValueChange = { code = it }, label = { Text("Código de 2 etapas (se usar)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "A senha não fica guardada: ela só é usada agora, no aparelho, para abrir sua conta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = email.isNotBlank() && pass.isNotEmpty(),
                onClick = { onLogin(email, pass, code.ifBlank { null }) }
            ) { Text("Entrar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
