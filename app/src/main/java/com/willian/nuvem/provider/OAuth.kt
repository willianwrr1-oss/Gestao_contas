package com.willian.nuvem.provider

import android.net.Uri
import com.willian.nuvem.BuildConfig
import com.willian.nuvem.Nuvem
import com.willian.nuvem.data.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Monta o pedido de login (OAuth com PKCE) de cada serviço. */
object OAuthConfig {
    private const val GOOGLE_SCOPE = "https://www.googleapis.com/auth/drive"
    private const val MS_SCOPE = "Files.ReadWrite User.Read offline_access"

    fun request(p: Provider): AuthorizationRequest? = when (p) {
        Provider.GOOGLE -> {
            if (BuildConfig.GOOGLE_CLIENT_ID.isBlank()) null
            else AuthorizationRequest.Builder(
                AuthorizationServiceConfiguration(
                    Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
                    Uri.parse("https://oauth2.googleapis.com/token")
                ),
                BuildConfig.GOOGLE_CLIENT_ID,
                ResponseTypeValues.CODE,
                Uri.parse("${BuildConfig.GOOGLE_SCHEME}:/oauth2redirect")
            )
                .setScope(GOOGLE_SCOPE)
                .setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
                .setAdditionalParameters(mapOf("access_type" to "offline"))
                .build()
        }
        Provider.ONEDRIVE -> {
            if (BuildConfig.MS_CLIENT_ID.isBlank()) null
            else AuthorizationRequest.Builder(
                AuthorizationServiceConfiguration(
                    Uri.parse("https://login.microsoftonline.com/common/oauth2/v2.0/authorize"),
                    Uri.parse("https://login.microsoftonline.com/common/oauth2/v2.0/token")
                ),
                BuildConfig.MS_CLIENT_ID,
                ResponseTypeValues.CODE,
                Uri.parse("com.willian.nuvem://auth")
            )
                .setScope(MS_SCOPE)
                .setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
                .build()
        }
        else -> null
    }
}

/** Guarda o estado do login de uma conta e entrega um token de acesso sempre válido. */
class OAuthSession(private val accountId: String) {
    private var state: AuthState? = null

    private fun loadState(): AuthState {
        state?.let { return it }
        val json = Nuvem.accounts.secret(accountId)
            ?: throw IOException("Login não encontrado. Remova a conta e adicione de novo.")
        return AuthState.jsonDeserialize(json).also { state = it }
    }

    suspend fun token(): String = withContext(Dispatchers.Main) { fresh() }

    private suspend fun fresh(): String = suspendCancellableCoroutine { cont ->
        val st = try {
            loadState()
        } catch (e: Exception) {
            cont.resumeWithException(e)
            return@suspendCancellableCoroutine
        }
        st.performActionWithFreshTokens(Nuvem.authService) { accessToken, _, ex ->
            Nuvem.accounts.setSecret(accountId, st.jsonSerializeString())
            if (accessToken != null) cont.resume(accessToken)
            else cont.resumeWithException(
                IOException(ex?.errorDescription ?: "Sessão expirada. Remova a conta e adicione de novo.")
            )
        }
    }
}
