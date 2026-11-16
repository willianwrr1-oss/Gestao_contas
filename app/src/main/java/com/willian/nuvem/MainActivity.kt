package com.willian.nuvem

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.willian.nuvem.data.Provider
import com.willian.nuvem.provider.OAuthConfig
import com.willian.nuvem.ui.AppRoot
import com.willian.nuvem.ui.AppViewModel
import com.willian.nuvem.ui.NuvemTheme
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse

class MainActivity : ComponentActivity() {
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

        setContent {
            NuvemTheme(vm.themeMode) { AppRoot(vm, ::startLogin) }
        }
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
