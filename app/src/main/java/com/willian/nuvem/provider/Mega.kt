package com.willian.nuvem.provider

import android.util.Base64
import com.willian.nuvem.Nuvem
import com.willian.nuvem.data.Account
import com.willian.nuvem.data.CloudFile
import com.willian.nuvem.data.Quota
import com.willian.nuvem.data.readFully
import com.willian.nuvem.data.skipFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/*
 * Cliente da Mega escrito direto sobre a API pública do protocolo (https://mega.nz/doc).
 * A Mega não publica o SDK Android em repositório aberto, então o protocolo é implementado aqui.
 * Toda a criptografia roda no aparelho: a senha nunca é guardada, só a sessão e a chave mestra
 * (ambas cifradas pelo Android Keystore).
 */

class MegaException(val code: Int) : IOException(describe(code)) {
    companion object {
        fun describe(c: Int) = when (c) {
            -9 -> "Mega: e-mail ou senha incorretos."
