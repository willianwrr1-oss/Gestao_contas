package com.willian.nuvem

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.willian.nuvem.data.Account
import com.willian.nuvem.data.AccountStore
import com.willian.nuvem.data.LOCAL_ACCOUNT
import com.willian.nuvem.data.PositionStore
import com.willian.nuvem.data.Provider
import com.willian.nuvem.provider.CloudProvider
import com.willian.nuvem.provider.GoogleDriveProvider
import com.willian.nuvem.provider.LocalProvider
import com.willian.nuvem.provider.MegaProvider
import com.willian.nuvem.provider.OAuthSession
import com.willian.nuvem.provider.OneDriveProvider
import net.openid.appauth.AuthorizationService
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class NuvemApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Nuvem.init(this)
    }
}

/** Objetos compartilhados pelo app, pela tela e pelo serviço de reprodução (mesmo processo). */
object Nuvem {
    lateinit var app: Context
        private set
    lateinit var accounts: AccountStore
        private set
    lateinit var positions: PositionStore
        private set
    lateinit var prefs: SharedPreferences
        private set

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    val authService: AuthorizationService by lazy { AuthorizationService(app) }
    val local: LocalProvider by lazy { LocalProvider(app) }

    private val providers = ConcurrentHashMap<String, CloudProvider>()

    fun init(ctx: Context) {
        app = ctx.applicationContext
        accounts = AccountStore(app)
        positions = PositionStore(app)
        prefs = app.getSharedPreferences("ui", Context.MODE_PRIVATE)
        cleanCache()
    }

    fun provider(a: Account): CloudProvider {
        if (a.provider == Provider.LOCAL) return local
        return providers.getOrPut(a.id) {
            when (a.provider) {
                Provider.GOOGLE -> GoogleDriveProvider(a, OAuthSession(a.id))
                Provider.ONEDRIVE -> OneDriveProvider(a, OAuthSession(a.id))
                Provider.MEGA -> MegaProvider.create(a)
                Provider.LOCAL -> local
            }
        }
    }

    fun providerById(id: String): CloudProvider? =
        if (id == LOCAL_ACCOUNT.id) local else accounts.find(id)?.let { provider(it) }

    fun forget(id: String) {
        providers.remove(id)
    }

    /** Apaga arquivos temporários de visualização com mais de 2 dias. */
    private fun cleanCache() {
        val limit = System.currentTimeMillis() - 2L * 24 * 3600 * 1000
        File(app.cacheDir, "viewer").listFiles()?.forEach { if (it.lastModified() < limit) it.delete() }
    }
}
