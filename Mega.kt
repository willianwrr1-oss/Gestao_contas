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
            -26 -> "Mega: esta conta exige o código de verificação em duas etapas."
            -15 -> "Mega: sessão expirada. Remova a conta e entre novamente."
            -16 -> "Mega: conta bloqueada."
            -17 -> "Mega: limite de transferência ou de espaço excedido."
            -3, -4, -18 -> "Mega: servidor ocupado, tente de novo em instantes."
            else -> "Mega: erro $c."
        }
    }
}

object MegaCrypto {
    private val flags = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP

    fun b64d(s: String): ByteArray = Base64.decode(s, flags)
    fun b64e(b: ByteArray): String = Base64.encodeToString(b, flags)

    fun ecb(key: ByteArray, data: ByteArray, encrypt: Boolean): ByteArray {
        val c = Cipher.getInstance("AES/ECB/NoPadding")
        c.init(if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        return c.doFinal(data)
    }

    fun cbc(key: ByteArray, data: ByteArray, encrypt: Boolean): ByteArray {
        val c = Cipher.getInstance("AES/CBC/NoPadding")
        c.init(
            if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"), IvParameterSpec(ByteArray(16))
        )
        return c.doFinal(data)
    }

    fun xor(a: ByteArray, b: ByteArray): ByteArray = ByteArray(a.size) { (a[it].toInt() xor b[it].toInt()).toByte() }

    fun pbkdf2Sha512(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(password, "HmacSHA512"))
        val out = ByteArray(length)
        var pos = 0
        var block = 1
        while (pos < length) {
            mac.update(salt)
            mac.update(byteArrayOf(0, 0, 0, block.toByte()))
            var u = mac.doFinal()
            val t = u.copyOf()
            for (i in 1 until iterations) {
                u = mac.doFinal(u)
                for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
            }
            val n = minOf(t.size, length - pos)
            System.arraycopy(t, 0, out, pos, n)
            pos += n
            block++
        }
        return out
    }

    /** Contas antigas (versão 1): chave derivada com 65536 rodadas de AES. */
    fun prepareKeyV1(password: String): ByteArray {
        val pw = password.toByteArray(Charsets.UTF_8)
        val size = maxOf(16, ((pw.size + 15) / 16) * 16)
        val padded = pw.copyOf(size)
        var pkey = byteArrayOf(
            0x93.toByte(), 0xC4.toByte(), 0x67, 0xE3.toByte(), 0x7D, 0xB0.toByte(), 0xC7.toByte(), 0xA4.toByte(),
            0xD1.toByte(), 0xBE.toByte(), 0x3F, 0x81.toByte(), 0x01, 0x52, 0xCB.toByte(), 0x56
        )
        val ciphers = (0 until size / 16).map {
            Cipher.getInstance("AES/ECB/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(padded.copyOfRange(it * 16, it * 16 + 16), "AES"))
            }
        }
        repeat(65536) { for (c in ciphers) pkey = c.doFinal(pkey) }
        return pkey
    }

    fun userHashV1(email: String, passKey: ByteArray): String {
        val e = email.lowercase().toByteArray(Charsets.UTF_8)
        var h = ByteArray(16)
        for (i in e.indices) h[i % 16] = (h[i % 16].toInt() xor e[i].toInt()).toByte()
        val c = Cipher.getInstance("AES/ECB/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(passKey, "AES"))
        repeat(16384) { h = c.doFinal(h) }
        return b64e(h.copyOfRange(0, 4) + h.copyOfRange(8, 12))
    }

    fun encAttr(key16: ByteArray, name: String): String {
        var b = ("MEGA" + JSONObject().put("n", name).toString()).toByteArray(Charsets.UTF_8)
        b = b.copyOf(b.size + (16 - b.size % 16) % 16)
        return b64e(cbc(key16, b, true))
    }

    private class Mpi(val v: BigInteger, val next: Int)

    private fun mpi(b: ByteArray, off: Int): Mpi {
        val bits = ((b[off].toInt() and 0xff) shl 8) or (b[off + 1].toInt() and 0xff)
        val len = (bits + 7) / 8
        return Mpi(BigInteger(1, b.copyOfRange(off + 2, off + 2 + len)), off + 2 + len)
    }

    /** Decifra o identificador de sessão (csid) com a chave RSA privada da conta. */
    fun decryptSessionId(master: ByteArray, privkB64: String, csidB64: String): String {
        val priv = ecb(master, b64d(privkB64), false)
        val p = mpi(priv, 0)
        val q = mpi(priv, p.next)
        val d = mpi(priv, q.next)
        val c = mpi(b64d(csidB64), 0).v
        val m = c.modPow(d.v, p.v.multiply(q.v))
        var mb = m.toByteArray()
        if (mb.size > 1 && mb[0] == 0.toByte()) mb = mb.copyOfRange(1, mb.size)
        return b64e(mb.copyOfRange(0, 43))
    }
}

class MegaSession(val email: String, val sid: String, val master: ByteArray, val userHandle: String) {
    fun toJson(): String = JSONObject().put("email", email).put("sid", sid)
        .put("master", MegaCrypto.b64e(master)).put("u", userHandle).toString()

    companion object {
        fun fromJson(s: String): MegaSession {
            val o = JSONObject(s)
            return MegaSession(o.getString("email"), o.getString("sid"), MegaCrypto.b64d(o.getString("master")), o.getString("u"))
        }
    }
}

class MegaApi(private val http: OkHttpClient, var sid: String? = null) {
    private var seq = (System.currentTimeMillis() % 1_000_000).toInt()

    /** Envia um comando e devolve a resposta (JSONObject, JSONArray, Int ou String). */
    suspend fun call(cmd: JSONObject): Any = withContext(Dispatchers.IO) {
        var attempt = 0
        var result: Any? = null
        while (result == null) {
            val url = "https://g.api.mega.co.nz/cs?id=${seq++}" + (sid?.let { "&sid=$it" } ?: "")
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) Nuvem/1.0")
                .post(JSONArray().put(cmd).toString().toRequestBody("text/plain;charset=UTF-8".toMediaType()))
                .build()
            var code = 0
            val text = http.newCall(req).execute().use { code = it.code; it.body?.string().orEmpty().trim() }
            val r: Any = when {
                text.startsWith("[") -> JSONArray(text).get(0)
                text.startsWith("{") -> JSONObject(text)
                text.toIntOrNull() != null -> text.toInt()
                else -> throw IOException("Resposta inesperada da Mega (HTTP $code): " + text.take(120).ifBlank { "vazia" })
            }
            if (r is Int && r < 0) {
                if ((r == -3 || r == -18) && attempt < 6) {
                    delay(500L shl attempt)
                    attempt++
                    continue
                }
                throw MegaException(r)
            }
            result = r
        }
        result!!
    }

    suspend fun login(email: String, password: String, mfa: String?): MegaSession {
        val mail = email.trim().lowercase()
        val pre = call(JSONObject().put("a", "us0").put("user", mail)) as JSONObject
        val passKey: ByteArray
        val userHash: String
        if (pre.optInt("v", 1) == 2) {
            val dk = MegaCrypto.pbkdf2Sha512(password.toByteArray(Charsets.UTF_8), MegaCrypto.b64d(pre.getString("s")), 100_000, 32)
            passKey = dk.copyOfRange(0, 16)
            userHash = MegaCrypto.b64e(dk.copyOfRange(16, 32))
        } else {
            passKey = MegaCrypto.prepareKeyV1(password)
            userHash = MegaCrypto.userHashV1(mail, passKey)
        }
        val cmd = JSONObject().put("a", "us").put("user", mail).put("uh", userHash)
        if (!mfa.isNullOrBlank()) cmd.put("mfa", mfa.trim())
        val r = call(cmd) as JSONObject
        val master = MegaCrypto.ecb(passKey, MegaCrypto.b64d(r.getString("k")), false)
        val session = if (r.has("tsid")) r.getString("tsid")
        else MegaCrypto.decryptSessionId(master, r.getString("privk"), r.getString("csid"))
        sid = session
        val me = call(JSONObject().put("a", "ug")) as JSONObject
        return MegaSession(mail, session, master, me.getString("u"))
    }
}

class MegaNode(
    val h: String,
    val p: String,
    val t: Int,          // 0 arquivo, 1 pasta, 2 nuvem, 3 entrada, 4 lixeira
    val name: String,
    val size: Long,
    val ts: Long,
    val aesKey: ByteArray,
    val nonce: ByteArray?,
    val restoreTo: String?
)

class MegaProvider(
    override val account: Account,
    private val api: MegaApi,
    private val session: MegaSession
) : CloudProvider {
    private val http = Nuvem.http
    private val master = session.master
    private val mutex = Mutex()
    @Volatile private var nodes: Map<String, MegaNode> = emptyMap()
    private var rootH = ""
    private var rubbishH = ""
    private var lastRefresh = 0L

    override val supportsTrashList = true

    companion object {
        fun create(a: Account): MegaProvider {
            val json = Nuvem.accounts.secret(a.id)
                ?: throw IOException("Sessão da Mega não encontrada. Remova a conta e entre novamente.")
            val s = MegaSession.fromJson(json)
            return MegaProvider(a, MegaApi(Nuvem.http, s.sid), s)
        }
    }

    // ---- árvore de nós -------------------------------------------------------------------------

    private suspend fun ensure(force: Boolean = false) = mutex.withLock {
        if (force || nodes.isEmpty() || System.currentTimeMillis() - lastRefresh > 60_000) refreshLocked()
    }

    private suspend fun refreshLocked() {
        val r = api.call(JSONObject().put("a", "f").put("c", 1).put("r", 1)) as JSONObject
        val arr = r.optJSONArray("f") ?: JSONArray()
        val map = HashMap<String, MegaNode>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val t = o.optInt("t")
            val h = o.getString("h")
            if (t >= 2) {
                if (t == 2) rootH = h
                if (t == 4) rubbishH = h
                map[h] = MegaNode(h, "", t, "", 0, 0, ByteArray(16), null, null)
                continue
            }
            runCatching { decryptNode(o) }.getOrNull()?.let { map[h] = it }
        }
        nodes = map
        lastRefresh = System.currentTimeMillis()
    }

    private fun decryptNode(o: JSONObject): MegaNode? {
        val keyField = o.optString("k")
        if (keyField.isEmpty()) return null
        val seg = keyField.split('/').firstOrNull { it.substringBefore(':') == session.userHandle } ?: return null
        val full = MegaCrypto.ecb(master, MegaCrypto.b64d(seg.substringAfter(':')), false)
        val t = o.optInt("t")
        val aes: ByteArray
        var nonce: ByteArray? = null
        if (t == 0 && full.size >= 32) {
            aes = MegaCrypto.xor(full.copyOfRange(0, 16), full.copyOfRange(16, 32))
            nonce = full.copyOfRange(16, 24)
        } else {
            aes = full.copyOf(16)
        }
        val raw = String(MegaCrypto.cbc(aes, MegaCrypto.b64d(o.getString("a")), false), Charsets.UTF_8)
        if (!raw.startsWith("MEGA{")) return null
        val attr = JSONObject(raw.substring(4).trimEnd('\u0000'))
        return MegaNode(
            o.getString("h"), o.optString("p"), t, attr.optString("n"),
            o.optLong("s", -1), o.optLong("ts") * 1000, aes, nonce, attr.optString("rr").ifEmpty { null }
        )
    }

    private fun toFile(n: MegaNode) = CloudFile(n.h, n.name, n.t == 1, if (n.t == 1) -1 else n.size, n.ts)

    private fun inRubbish(n: MegaNode): Boolean {
        var cur: MegaNode? = n
        var guard = 0
        while (cur != null && guard++ < 200) {
            if (cur.p == rubbishH || cur.h == rubbishH) return true
            cur = nodes[cur.p]
        }
        return false
    }

    // ---- CloudProvider ---------------------------------------------------------------------------

    override suspend fun identity() = session.email to session.email

    override suspend fun list(folderId: String?): List<CloudFile> {
        ensure()
        val parent = folderId ?: rootH
        return nodes.values.filter { it.p == parent && it.t in 0..1 }.map { toFile(it) }
    }

    override suspend fun search(query: String): List<CloudFile> {
        ensure()
        val q = query.lowercase()
        return nodes.values.filter { it.t in 0..1 && it.name.lowercase().contains(q) && !inRubbish(it) }
            .take(300).map { toFile(it) }
    }

    override suspend fun quota(): Quota? {
        val r = api.call(JSONObject().put("a", "uq").put("strg", 1)) as JSONObject
        return Quota(r.optLong("cstrg"), r.optLong("mstrg"))
    }

    override suspend fun open(file: CloudFile, offset: Long): InputStream = withContext(Dispatchers.IO) {
        ensure()
        val n = nodes[file.id] ?: throw IOException("Arquivo não encontrado na Mega.")
        val nonce = n.nonce ?: throw IOException("Isto não é um arquivo.")
        val r = api.call(JSONObject().put("a", "g").put("g", 1).put("ssl", 1).put("n", n.h)) as JSONObject
        val url = r.getString("g").replace("http://", "https://")
        val size = r.getLong("s")
        if (offset >= size) return@withContext ByteArrayInputStream(ByteArray(0))
        val aligned = offset - offset % 16
        val resp = http.newCall(Request.Builder().url("$url/$aligned-${size - 1}").build()).execute()
        if (!resp.isSuccessful) {
            val code = resp.code
            resp.close()
            throw IOException("Mega: erro $code ao baixar.")
        }
        val iv = ByteBuffer.allocate(16).put(nonce).putLong(aligned / 16).array()
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(n.aesKey, "AES"), IvParameterSpec(iv))
        val s = CipherInputStream(resp.body!!.byteStream(), cipher)
        s.skipFully(offset - aligned)
        s
    }

    private fun chunkSize(i: Int): Int = if (i < 8) 131_072 * (i + 1) else 1_048_576

    override suspend fun upload(
        parentId: String?, name: String, size: Long, mime: String,
        input: InputStream, onProgress: (Long) -> Unit
    ) {
        if (size <= 0L) throw IOException("A Mega não aceita enviar arquivos vazios por aqui.")
        ensure()
        val parent = parentId ?: rootH
        val up = api.call(JSONObject().put("a", "u").put("s", size)) as JSONObject
        val uploadUrl = up.getString("p").replace("http://", "https://")

        val rnd = SecureRandom()
        val key = ByteArray(16).also { rnd.nextBytes(it) }
        val nonce = ByteArray(8).also { rnd.nextBytes(it) }
        val ctr = Cipher.getInstance("AES/CTR/NoPadding")
        ctr.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(nonce + ByteArray(8)))
        val ecb = Cipher.getInstance("AES/ECB/NoPadding")
        ecb.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        val chunkIv = nonce + nonce

        var fileMac = ByteArray(16)
        var offset = 0L
        var idx = 0
        var token = ""
        withContext(Dispatchers.IO) {
            while (offset < size) {
                val len = minOf(chunkSize(idx).toLong(), size - offset).toInt()
                val plain = ByteArray(len)
                input.readFully(plain, len)

                // MAC do trecho (CBC-MAC) e acumulação no MAC do arquivo
                var cm = chunkIv.copyOf()
                var i = 0
                while (i < len) {
                    val blk = ByteArray(16)
                    System.arraycopy(plain, i, blk, 0, minOf(16, len - i))
                    cm = ecb.doFinal(MegaCrypto.xor(cm, blk))
                    i += 16
                }
                fileMac = ecb.doFinal(MegaCrypto.xor(fileMac, cm))

                val enc = ctr.update(plain)
                val req = Request.Builder().url("$uploadUrl/$offset")
                    .post(enc.toRequestBody("application/octet-stream".toMediaType())).build()
                val body = http.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) throw IOException("Mega: erro ${r.code} ao enviar.")
                    r.body?.string().orEmpty().trim()
                }
                if (body.length > 20) token = body
                else if (body.startsWith("-")) throw MegaException(body.toIntOrNull() ?: -1)
                offset += len
                idx++
                onProgress(offset)
            }
        }
        if (token.isEmpty()) throw IOException("A Mega não confirmou o envio.")

        // chave final do nó: [k0^n0, k1^n1, k2^m0, k3^m1, n0, n1, m0, m1], m = MAC condensado
        val mac = ByteArray(8)
        for (i in 0 until 4) {
            mac[i] = (fileMac[i].toInt() xor fileMac[i + 4].toInt()).toByte()
            mac[i + 4] = (fileMac[i + 8].toInt() xor fileMac[i + 12].toInt()).toByte()
        }
        val nodeKey = ByteArray(32)
        for (i in 0 until 8) nodeKey[i] = (key[i].toInt() xor nonce[i].toInt()).toByte()
        for (i in 8 until 16) nodeKey[i] = (key[i].toInt() xor mac[i - 8].toInt()).toByte()
        System.arraycopy(nonce, 0, nodeKey, 16, 8)
        System.arraycopy(mac, 0, nodeKey, 24, 8)

        val node = JSONObject().put("h", token).put("t", 0)
            .put("a", MegaCrypto.encAttr(key, name))
            .put("k", MegaCrypto.b64e(MegaCrypto.ecb(master, nodeKey, true)))
        api.call(JSONObject().put("a", "p").put("t", parent).put("n", JSONArray().put(node)))
        ensure(true)
    }

    override suspend fun createFolder(parentId: String?, name: String) {
        ensure()
        val key = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val node = JSONObject().put("h", "xxxxxxxx").put("t", 1)
            .put("a", MegaCrypto.encAttr(key, name))
            .put("k", MegaCrypto.b64e(MegaCrypto.ecb(master, key, true)))
        api.call(JSONObject().put("a", "p").put("t", parentId ?: rootH).put("n", JSONArray().put(node)))
        ensure(true)
    }

    override suspend fun trash(file: CloudFile) {
        ensure()
        api.call(JSONObject().put("a", "m").put("n", file.id).put("t", rubbishH))
        ensure(true)
    }

    override suspend fun listTrash(): List<CloudFile> {
        ensure(true)
        return nodes.values.filter { it.p == rubbishH && it.t in 0..1 }.map { toFile(it) }
    }

    override suspend fun restore(file: CloudFile) {
        ensure()
        val target = nodes[file.id]?.restoreTo?.takeIf { nodes.containsKey(it) } ?: rootH
        api.call(JSONObject().put("a", "m").put("n", file.id).put("t", target))
        ensure(true)
    }

    override suspend fun deleteForever(file: CloudFile) {
        api.call(JSONObject().put("a", "d").put("n", file.id))
        ensure(true)
    }

    override suspend fun emptyTrash() {
        ensure(true)
        nodes.values.filter { it.p == rubbishH }.forEach { api.call(JSONObject().put("a", "d").put("n", it.h)) }
        ensure(true)
    }
}
