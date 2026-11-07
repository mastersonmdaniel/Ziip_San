package br.com.ziipsan.campo.plugins

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Refresh token do app de campo (ED-18; RNF-39; ADR-0030).
 *
 * O refresh token (rotativo) vive em EncryptedSharedPreferences cuja chave-mestra
 * e do Android Keystore (AES256_GCM, nao extraivel). NAO existe @PluginMethod que
 * devolva o refresh token ao JavaScript — por construcao desta classe: o JS guarda
 * (uma vez, no fim do login), pede access token e apaga. A troca refresh -> access
 * roda aqui dentro, e a rotacao grava o novo refresh token sem ele atravessar a
 * ponte em nenhum sentido de leitura.
 *
 * tokenEndpoint e clientId sao configurados na casca (BuildConfig na integracao);
 * aqui ficam como constantes de compilacao a preencher pelo pipeline do tenant —
 * nenhum endereco real e inventado nesta entrega.
 */
@CapacitorPlugin(name = "SecureToken")
class SecureTokenPlugin : Plugin() {

    private fun prefs(context: Context) =
        EncryptedSharedPreferences.create(
            context,
            "ziipsan_campo_tokens",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    @PluginMethod
    fun storeRefreshToken(call: PluginCall) {
        val token = call.getString("refreshToken") ?: return call.reject("refreshToken obrigatorio")
        prefs(context).edit().putString(KEY_REFRESH, token).apply()
        call.resolve()
    }

    @PluginMethod
    fun hasRefreshToken(call: PluginCall) {
        val result = JSObject().put("present", prefs(context).contains(KEY_REFRESH))
        call.resolve(result)
    }

    @PluginMethod
    fun clear(call: PluginCall) {
        prefs(context).edit().remove(KEY_REFRESH).apply()
        call.resolve()
    }

    /**
     * Troca o refresh token vigente por um access token (grant refresh_token, com
     * rotacao: o novo refresh token, se vier, substitui o guardado). So o access
     * token e a expiracao voltam ao JS.
     */
    @PluginMethod
    fun getAccessToken(call: PluginCall) {
        val refresh = prefs(context).getString(KEY_REFRESH, null)
            ?: return call.reject("sem refresh token guardado")
        Thread {
            try {
                val conn = (URL(TOKEN_ENDPOINT).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                }
                val body = "grant_type=refresh_token&client_id=$CLIENT_ID&refresh_token=$refresh"
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode !in 200..299) {
                    call.reject("troca de token recusada: HTTP ${conn.responseCode}")
                    return@Thread
                }
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                json.optString("refresh_token").takeIf { it.isNotEmpty() }?.let { rotated ->
                    prefs(context).edit().putString(KEY_REFRESH, rotated).apply()
                }
                val result = JSObject()
                    .put("accessToken", json.getString("access_token"))
                    .put("expiresAt", System.currentTimeMillis() + json.getLong("expires_in") * 1000)
                call.resolve(result)
            } catch (e: Exception) {
                call.reject("falha na troca de token: ${e.javaClass.simpleName}")
            }
        }.start()
    }

    companion object {
        private const val KEY_REFRESH = "refresh_token"
        // Preenchidos na integracao (endpoint do realm ziipsan do ambiente, client
        // publico do app). Placeholders invalidos de proposito nesta entrega.
        private const val TOKEN_ENDPOINT = "https://auth.ziipsan.invalid/realms/ziipsan/protocol/openid-connect/token"
        private const val CLIENT_ID = "ziipsan-campo"
    }
}
