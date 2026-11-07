package br.com.ziipsan.campo.plugins

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Banco local cifrado e apagamento do app de campo (ED-18; ADR-0030).
 *
 * A chave do banco e gerada no Android Keystore (AES-256, nao extraivel,
 * {@code setUserAuthenticationRequired(false)} porque o app de campo trabalha
 * offline por horas) e NUNCA sai do Keystore: nenhum metodo a devolve ao JS.
 * O banco (SQLCipher) e aberto com a chave obtida aqui dentro do nativo pelo
 * helper de abertura da casca; este plugin expoe ao JS so abrir e apagar.
 *
 * wipeAll apaga: arquivo do banco, fotos pendentes e o diretorio de dados do
 * app — e chamado nas diretivas BLOCK e RETIRE (depois do envio, no RETIRE)
 * e no logout. E a resposta ao aparelho perdido/furtado do cenario do plano.
 */
@CapacitorPlugin(name = "FieldSecurity")
class FieldSecurityPlugin : Plugin() {

    private fun databaseFile(): File = File(context.getDatabasePath(DB_NAME).path)

    private fun ensureKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    @PluginMethod
    fun openEncryptedDatabase(call: PluginCall) {
        // Gera/recupera a chave no Keystore; a abertura efetiva do SQLCipher com
        // a chave acontece no helper nativo de dados da casca (fora da ponte).
        ensureKeystoreKey()
        if (!databaseFile().exists()) {
            databaseFile().parentFile?.mkdirs()
        }
        call.resolve(JSObject().put("opened", true))
    }

    @PluginMethod
    fun wipeAll(call: PluginCall) {
        var wiped = true
        // O contrato da ponte promete apagar TAMBEM os tokens: sem isto, o
        // refresh token sobrevivia ao wipe de BLOCK/RETIRE nas preferencias.
        if (!context.deleteSharedPreferences(TOKEN_PREFS_NAME)) wiped = false
        val db = databaseFile()
        if (db.exists() && !db.delete()) wiped = false
        File(db.path + "-wal").delete()
        File(db.path + "-shm").delete()
        context.filesDir?.listFiles()?.forEach { if (!it.deleteRecursively()) wiped = false }
        call.resolve(JSObject().put("wiped", wiped))
    }

    companion object {
        private const val KEY_ALIAS = "ziipsan_campo_db_key"
        private const val DB_NAME = "ziipsan_campo.db"
        // Mesmo arquivo de preferencias do SecureTokenPlugin (a apagar no wipe).
        private const val TOKEN_PREFS_NAME = "ziipsan_campo_tokens"
    }
}
