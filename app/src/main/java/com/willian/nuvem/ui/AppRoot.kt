package com.willian.nuvem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.willian.nuvem.data.Provider

@Composable
fun AppRoot(vm: AppViewModel, onAdd: (Provider) -> Unit) {
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snack.showSnackbar(it)
            vm.message = null
        }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val s = vm.nav.last()) {
            is Screen.Home -> HomeScreen(vm, onAdd)
            is Screen.Browser -> BrowserScreen(
                ctl = vm.browser(s.account),
                onExit = { vm.pop() },
                onOpen = { vm.push(Screen.Viewer(s.account, it)) },
                onTrash = { vm.push(Screen.Trash(s.account)) }
            )
            is Screen.Trash -> TrashScreen(
                account = s.account,
                onBack = { vm.pop() },
                onChanged = { vm.browser(s.account).let { c -> c.load(); c.loadQuota() } },
                onMessage = { vm.message = it }
            )
            is Screen.Viewer -> ViewerScreen(
                account = s.account,
                file = s.file,
                onBack = { vm.pop() },
                onMessage = { vm.message = it }
            )
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp))
        if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter).statusBarsPadding())
    }
}
