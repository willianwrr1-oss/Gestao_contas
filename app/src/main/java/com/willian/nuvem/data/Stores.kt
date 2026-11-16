package com.willian.nuvem.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Criptografa segredos (tokens, chaves) com uma chave guardada no Android Keystore. */
object SecretStore {
    private const val ALIAS = "nuvem_master_key"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val out = c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun decrypt(text: String): String? = try {
        val all = Base64.decode(text, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, all, 0, 12))
        String(c.doFinal(all, 12, all.size - 12), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }
}

class AccountStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("accounts", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    val flow: StateFlow<List<Account>> = state

    private fun load(): List<Account> {
        val arr = JSONArray(sp.getString("list", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Account(o.getString("id"), Provider.valueOf(o.getString("provider")), o.optString("name"), o.optString("email"))
        }
    }

    private fun save(list: List<Account>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("provider", it.provider.name).put("name", it.name).put("email", it.email))
        }
        sp.edit().putString("list", arr.toString()).apply()
        state.value = list
    }

    fun find(id: String): Account? = state.value.firstOrNull { it.id == id }

    fun upsert(a: Account, secret: String?) {
        if (secret != null) setSecret(a.id, secret)
        val cur = state.value
        save(if (cur.any { it.id == a.id }) cur.map { if (it.id == a.id) a else it } else cur + a)
    }

    fun remove(id: String) {
        sp.edit().remove("s_$id").apply()
        save(state.value.filter { it.id != id })
    }

    fun secret(id: String): String? = sp.getString("s_$id", null)?.let { SecretStore.decrypt(it) }

    fun setSecret(id: String, value: String) {
        sp.edit().putString("s_$id", SecretStore.encrypt(value)).commit()
    }
}

/** Última posição de reprodução de cada música/vídeo. */
class PositionStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("positions", Context.MODE_PRIVATE)

    /** Retorna (posição, duração) em milissegundos. */
    fun get(key: String): Pair<Long, Long>? {
        val p = sp.getString(key, null)?.split("|") ?: return null
        if (p.size != 2) return null
        return (p[0].toLongOrNull() ?: return null) to (p[1].toLongOrNull() ?: 0L)
    }

    fun put(key: String, pos: Long, dur: Long) {
        if (dur > 0 && pos > dur - 3000) clear(key) else sp.edit().putString(key, "$pos|$dur").apply()
    }

    fun clear(key: String) {
        sp.edit().remove(key).apply()
    }
}
