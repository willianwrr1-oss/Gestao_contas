package com.willian.nuvem.ui

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.clickable
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel, onAdd: (Provider) -> Unit) {
    val ctx = LocalContext.current
    val accounts by Nuvem.accounts.flow.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var showMega by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Account?>(null) }
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
                        }
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
                items(group, key = { it.id }) { a -> AccountCard(a, onOpen = { vm.push(Screen.Browser(a)) }, onRemove = { removing = a }) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountCard(a: Account, onOpen: () -> Unit, onRemove: () -> Unit) {
    val quota by produceState<Quota?>(null, a.id) { value = runCatching { Nuvem.provider(a).quota() }.getOrNull() }
    var menu by remember { mutableStateOf(false) }
    Card(onClick = onOpen, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(providerIcon(a.provider), providerColor(a.provider))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    a.email.ifEmpty { a.name },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (a.name.isNotEmpty() && a.name != a.email && a.email.isNotEmpty()) {
                    Text(a.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                QuotaLine(quota)
            }
            androidx.compose.foundation.layout.Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Opções") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Remover conta") }, onClick = { menu = false; onRemove() })
                }
            }
        }
    }
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
