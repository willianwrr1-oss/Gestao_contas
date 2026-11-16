package com.willian.nuvem.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.willian.nuvem.Nuvem
import com.willian.nuvem.data.Account
import com.willian.nuvem.data.CloudFile
import com.willian.nuvem.data.Downloader
import com.willian.nuvem.data.FileTypes
import com.willian.nuvem.data.Provider
import com.willian.nuvem.data.Quota
import com.willian.nuvem.provider.MegaApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.openid.appauth.AuthState
import java.io.File
import java.io.IOException
import java.util.UUID

sealed interface Screen {
    data object Home : Screen
    data class Browser(val account: Account) : Screen
    data class Trash(val account: Account) : Screen
    data class Viewer(val account: Account, val file: CloudFile) : Screen
}

enum class SortBy(val label: String) { NAME("Nome"), DATE("Data (mais recente)"), SIZE("Tamanho (maior)") }

data class Transfer(val label: String, val progress: Float?)

/** Estado de navegação de uma conta: pasta atual, lista, busca, envio e download. */
class BrowserController(val account: Account, private val scope: CoroutineScope) {
    private val provider get() = Nuvem.provider(account)

    var started = false
    var stack by mutableStateOf(listOf<CloudFile>())
    var items by mutableStateOf(listOf<CloudFile>())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    var searchResults by mutableStateOf<List<CloudFile>?>(null)
    var sort by mutableStateOf(SortBy.NAME)
    var transfer by mutableStateOf<Transfer?>(null)
    var quota by mutableStateOf<Quota?>(null)
    var onMessage: (String) -> Unit = {}

    private var job: Job? = null
    private var searchJob: Job? = null

    val currentId: String? get() = stack.lastOrNull()?.id

    val visible: List<CloudFile>
        get() {
            val base = searchResults
                ?: if (query.isBlank()) items else items.filter { it.name.contains(query.trim(), ignoreCase = true) }
            val cmp: Comparator<CloudFile> = when (sort) {
                SortBy.NAME -> compareBy<CloudFile> { it.name.lowercase() }
                SortBy.DATE -> compareByDescending<CloudFile> { it.modified }
                SortBy.SIZE -> compareByDescending<CloudFile> { it.size }
            }
            return base.sortedWith(compareByDescending<CloudFile> { it.isFolder }.then(cmp))
        }

    fun load() {
        job?.cancel()
        job = scope.launch {
            loading = true
            error = null
            try {
                items = provider.list(currentId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            }
            loading = false
        }
    }

    fun loadQuota() {
        scope.launch { quota = runCatching { provider.quota() }.getOrNull() }
    }

    fun enter(f: CloudFile) {
        clearSearch()
        stack = stack + f
        load()
    }

    fun goTo(depth: Int) {
        clearSearch()
        stack = stack.take(depth)
        load()
    }

    /** Volta um nível. Retorna false se já está na raiz (então a tela deve sair). */
    fun up(): Boolean {
        if (query.isNotEmpty() || searchResults != null) {
            clearSearch()
            return true
        }
        if (stack.isEmpty()) return false
        stack = stack.dropLast(1)
        load()
        return true
    }

    fun clearSearch() {
        searchJob?.cancel()
        query = ""
        searchResults = null
    }

    fun search(q: String) {
        query = q
        searchJob?.cancel()
        searchResults = null
        if (q.isBlank()) return
        searchJob = scope.launch {
            delay(500)
            loading = true
            try {
                searchResults = provider.search(q.trim())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onMessage("Busca: ${e.message}")
            }
            loading = false
        }
    }

    fun trash(f: CloudFile) {
        scope.launch {
            try {
                provider.trash(f)
                onMessage(if (account.provider == Provider.ONEDRIVE) "Movido para a lixeira do OneDrive." else "Movido para a lixeira.")
                items = items.filter { it.id != f.id }
                searchResults = searchResults?.filter { it.id != f.id }
                loadQuota()
            } catch (e: Exception) {
                onMessage("Não foi possível excluir: ${e.message}")
            }
        }
    }

    fun mkdir(name: String) {
        scope.launch {
            try {
                provider.createFolder(currentId, name.trim())
                load()
            } catch (e: Exception) {
                onMessage("Não foi possível criar a pasta: ${e.message}")
            }
        }
    }

    fun upload(uris: List<Uri>) {
        scope.launch {
            val cr = Nuvem.app.contentResolver
            for ((i, uri) in uris.withIndex()) {
                var name = "arquivo"
                var size = -1L
                cr.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val si = c.getColumnIndex(OpenableColumns.SIZE)
                        if (ni >= 0) name = c.getString(ni) ?: name
                        if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                    }
                }
                val label = "Enviando ${i + 1}/${uris.size}: $name"
                transfer = Transfer(label, 0f)
                var tmp: File? = null
                try {
                    var input = cr.openInputStream(uri) ?: throw IOException("Não foi possível abrir o arquivo.")
                    if (size < 0) {
                        tmp = File.createTempFile("envio", null, Nuvem.app.cacheDir)
                        tmp.outputStream().use { o -> input.use { it.copyTo(o) } }
                        size = tmp.length()
                        input = tmp.inputStream()
                    }
                    val total = size
                    input.use {
                        provider.upload(currentId, name, total, FileTypes.mime(name), it) { sent ->
                            transfer = Transfer(label, if (total > 0) sent.toFloat() / total else null)
                        }
                    }
                } catch (e: Exception) {
                    onMessage("Falha ao enviar $name: ${e.message}")
                } finally {
                    tmp?.delete()
                }
            }
            transfer = null
            load()
            loadQuota()
        }
    }

    fun download(f: CloudFile) {
        scope.launch {
            val label = "Baixando ${f.name}"
            transfer = Transfer(label, 0f)
            try {
                Downloader.save(provider, f) { transfer = Transfer(label, it) }
                onMessage("Salvo em Downloads/Nuvem.")
            } catch (e: Exception) {
                onMessage("Falha ao baixar: ${e.message}")
            }
            transfer = null
        }
    }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val nav = mutableStateListOf<Screen>(Screen.Home)
    var message by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    var themeMode by mutableIntStateOf(Nuvem.prefs.getInt("theme", 0))
        private set

    private val browsers = HashMap<String, BrowserController>()

    fun browser(a: Account): BrowserController = browsers.getOrPut(a.id) {
        BrowserController(a, viewModelScope).also { it.onMessage = { m -> message = m } }
    }

    fun push(s: Screen) {
        nav.add(s)
    }

    fun pop() {
        if (nav.size > 1) nav.removeAt(nav.lastIndex)
    }

    fun cycleTheme() {
        themeMode = (themeMode + 1) % 3
        Nuvem.prefs.edit().putInt("theme", themeMode).apply()
    }

    fun removeAccount(a: Account) {
        browsers.remove(a.id)
        Nuvem.forget(a.id)
        Nuvem.accounts.remove(a.id)
        while (nav.size > 1) nav.removeAt(nav.lastIndex)
    }

    /** Conclui o login OAuth (Google ou Microsoft): descobre quem é a conta e a cadastra. */
    fun finishOAuth(p: Provider, state: AuthState) {
        viewModelScope.launch {
            busy = true
            val id = UUID.randomUUID().toString().replace("-", "")
            try {
                val json = state.jsonSerializeString()
                val temp = Account(id, p, p.label, "")
                Nuvem.accounts.upsert(temp, json)
                val (name, email) = Nuvem.provider(temp).identity()
                val existing = Nuvem.accounts.flow.value
                    .firstOrNull { it.provider == p && it.email.equals(email, true) && it.id != id }
                if (existing != null) {
                    Nuvem.accounts.setSecret(existing.id, json)
                    Nuvem.forget(existing.id)
                    Nuvem.forget(id)
                    Nuvem.accounts.remove(id)
                    message = "Essa conta já estava cadastrada. Login renovado."
                } else {
                    Nuvem.accounts.upsert(Account(id, p, name, email), null)
                    message = "Conta adicionada: $email"
                }
            } catch (e: Exception) {
                Nuvem.forget(id)
                Nuvem.accounts.remove(id)
                message = "Não foi possível entrar: ${e.message}"
            }
            busy = false
        }
    }

    fun addMega(email: String, password: String, mfa: String?) {
        viewModelScope.launch {
            busy = true
            try {
                val s = MegaApi(Nuvem.http).login(email, password, mfa)
                val existing = Nuvem.accounts.flow.value
                    .firstOrNull { it.provider == Provider.MEGA && it.email.equals(s.email, true) }
                if (existing != null) {
                    Nuvem.accounts.setSecret(existing.id, s.toJson())
                    Nuvem.forget(existing.id)
                    message = "Essa conta já estava cadastrada. Login renovado."
                } else {
                    val id = UUID.randomUUID().toString().replace("-", "")
                    Nuvem.accounts.upsert(Account(id, Provider.MEGA, s.email, s.email), s.toJson())
                    message = "Conta Mega adicionada: ${s.email}"
                }
            } catch (e: Exception) {
                message = e.message ?: "Não foi possível entrar na Mega."
            }
            busy = false
        }
    }
}

fun Context.findActivity(): android.app.Activity? {
    var c = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}
