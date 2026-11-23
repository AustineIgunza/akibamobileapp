package com.example.mobilefinanceappapplication32

import android.content.Context
import android.content.pm.ApplicationInfo
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class ApiFailure(val status: Int, message: String) : Exception(message)

/** Keeps the server's HTTP-only session cookie encrypted at rest on this device. */
class AkibaSession(context: Context) {
    private val prefs = context.getSharedPreferences("akiba_config", Context.MODE_PRIVATE)
    private val alias = "akiba_session_key"
    val isDebug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    var baseUrl: String
        get() = prefs.getString("api_url", if (isDebug) "http://127.0.0.1:4000" else "") ?: ""
        set(value) { prefs.edit().putString("api_url", value.trim().trimEnd('/')).apply() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }

    fun cookie(): String? {
        return try {
            val encoded = prefs.getString("session", null) ?: return null
            val data = Base64.decode(encoded, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
            String(cipher.doFinal(data.copyOfRange(12, data.size)), Charsets.UTF_8)
        } catch (_: Exception) {
            clearCookie()
            null
        }
    }

    fun saveCookie(cookie: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.iv + cipher.doFinal(cookie.toByteArray(Charsets.UTF_8))
        prefs.edit().putString("session", Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    fun clearCookie() { prefs.edit().remove("session").apply() }
}

class AkibaApi(private val session: AkibaSession) {
    suspend fun get(path: String): JSONObject = request("GET", path)
    suspend fun post(path: String, body: JSONObject = JSONObject()): JSONObject = request("POST", path, body)
    suspend fun patch(path: String, body: JSONObject): JSONObject = request("PATCH", path, body)
    suspend fun delete(path: String): JSONObject = request("DELETE", path)

    private suspend fun request(method: String, path: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val base = session.baseUrl
        if (base.isBlank()) throw ApiFailure(0, "Set the API address in Settings.")
        if (!base.startsWith("https://") && !(session.isDebug && base.startsWith("http://"))) {
            throw ApiFailure(0, "Use an HTTPS API address.")
        }
        val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            session.cookie()?.let { setRequestProperty("Cookie", it) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            body?.let { connection.outputStream.use { stream -> stream.write(it.toString().toByteArray(Charsets.UTF_8)) } }
            val status = connection.responseCode
            connection.getHeaderField("Set-Cookie")?.let { header ->
                val cookie = header.substringBefore(';')
                if (cookie.startsWith("akiba_token=")) {
                    if (cookie.substringAfter('=').isBlank()) session.clearCookie() else session.saveCookie(cookie)
                }
            }
            val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val result = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (status !in 200..299) {
                throw ApiFailure(status, result.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: "Request failed ($status).")
            }
            result
        } finally { connection.disconnect() }
    }
}
