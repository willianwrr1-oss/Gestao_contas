package com.willian.nuvem

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.willian.nuvem.ui.LockScreen
import com.willian.nuvem.data.Provider
import com.willian.nuvem.provider.OAuthConfig
import com.willian.nuvem.ui.AppRoot
import com.willian.nuvem.ui.AppViewModel
import com.willian.nuvem.ui.NuvemTheme
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse

class MainActivity : FragmentActivity() {
    private var locked by mutableStateOf(false)
    private var prompting = false
    private var firstStart = true
    private var lastStop = 0L

    private val vm: AppViewModel by viewModels()
    private var pending: Provider? = null

    private val authLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            onAuthResult(res.data)
        }
    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

        locked = shouldLock()
        setContent {
            NuvemTheme(vm.themeMode) {
                if (locked) LockScreen(onUnlock = ::authenticate) else AppRoot(vm, ::startLogin)
            }
        }
    }

    /** Só bloqueia se o usuário ativou e se o celular tem digital, PIN, padrão ou senha cadastrados. */
    private fun shouldLock(): Boolean =
        Nuvem.prefs.getBoolean("lock", true) &&
            BiometricManager.from(this).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) ==
            BiometricManager.BIOMETRIC_SUCCESS

    override fun onStart() {
        super.onStart()
        // Volta do segundo plano depois de mais de 30 s: pede de novo.
        if (!firstStart && SystemClock.elapsedRealtime() - lastStop > 30_000 && shouldLock()) locked = true
        firstStart = false
        if (locked) authenticate()
    }

    override fun onStop() {
        lastStop = SystemClock.elapsedRealtime()
        super.onStop()
    }

    private fun authenticate() {
        if (prompting) return
        prompting = true
        val prompt = BiometricPrompt(
            this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    prompting = false
                    locked = false
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    prompting = false
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Nuvem")
            .setSubtitle("Confirme com sua digital ou a senha do celular")
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(info)
    }

    private fun startLogin(p: Provider) {
        val req = OAuthConfig.request(p)
        if (req == null) {
            vm.message = "Falta configurar o ID do cliente ${p.label} no arquivo oauth.properties (veja o README)."
            return
        }
        pending = p
        authLauncher.launch(Nuvem.authService.getAuthorizationRequestIntent(req))
    }

    private fun onAuthResult(data: Intent?) {
        val p = pending ?: return
        pending = null
        if (data == null) {
            vm.message = "Login cancelado."
            return
        }
        val resp = AuthorizationResponse.fromIntent(data)
        val ex = AuthorizationException.fromIntent(data)
        if (resp == null) {
            vm.message = ex?.errorDescription ?: "Login cancelado."
            return
        }
        vm.busy = true
        Nuvem.authService.performTokenRequest(resp.createTokenExchangeRequest()) { tokenResp, tokenEx ->
            if (tokenResp == null) {
                vm.busy = false
                vm.message = tokenEx?.errorDescription ?: "Falha no login."
            } else {
                vm.finishOAuth(p, AuthState(resp, tokenResp, tokenEx))
            }
        }
    }
}
