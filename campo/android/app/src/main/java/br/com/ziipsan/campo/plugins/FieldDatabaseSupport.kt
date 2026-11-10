package br.com.ziipsan.campo.plugins

import android.content.ContentValues
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

/**
 * Erro NOMEADO de tarefa desconhecida no saveReading: a leitura cuja taskId
 * nao consta do mapa task->route de nenhuma rota gravada e recusada com
 * este nome — nunca gravada como orfa silenciosa (contrato em
 * sync/sqlCipherLocalStore.ts e plugins/fieldDatabase.ts).
 */
class UnknownTaskException(taskId: String) :
    Exception("UNKNOWN_TASK: tarefa desconhecida para saveReading: $taskId")

/**
 * Banco local cifrado do app de campo (RG2-C-P4 / P-04; ED-18; ADR-0030).
 *
 * DESENHO DA CHAVE — resolve o achado SS4-B da revisao cruzada da ED-18:
 * a chave AES-GCM do Android Keystore e NAO-EXTRAIVEL, logo os bytes dela
 * nunca estao disponiveis e ela NAO pode ser a passphrase do SQLCipher
 * (que precisa dos bytes). O desenho correto, implementado aqui:
 *  1. na primeira abertura, gera-se uma PASSPHRASE aleatoria de 32 bytes
 *     (SecureRandom) aqui dentro do nativo;
 *  2. a passphrase e EMBRULHADA (AES-GCM) pela chave nao-extraivel do
 *     Keystore (alias proprio WRAP_KEY_ALIAS); o blob embrulhado
 *     (IV + ciphertext) fica num ficheiro nos dados privados do app
 *     (filesDir) — sem a chave do Keystore, o blob nao revela nada;
 *  3. em cada abertura, o desembrulho acontece SO em memoria, no nativo,
 *     e a passphrase (em hex) abre o SQLCipher. A passphrase NUNCA
 *     atravessa a ponte para o JavaScript e nunca e gravada em claro.
 *
 * Tabelas (duas, como nomeadas no brief do lote):
 *  - routes(route_id PK, package_json, task_map_json): o pacote de rota
 *    (JSON do RoutePackage real) e o mapa task->route (array JSON das
 *    taskIds do pacote) gravados pelo saveRoute;
 *  - pending_readings(task_id PK, route_id, reading_json): a fila local
 *    de leituras (JSON do PendingReading real), upsert por task_id.
 *
 * O singleton de abertura vive aqui para o wipe do FieldSecurityPlugin
 * nao lutar com uma ligacao aberta: ele chama closeOpenDatabase() antes
 * de apagar os ficheiros e deleteWrappedPassphrase() para apagar o blob.
 */
object FieldDatabaseSupport {

    // Mesmo ficheiro de banco que o FieldSecurityPlugin apaga no wipeAll.
    const val DB_NAME = "ziipsan_campo.db"
    const val WRAPPED_PASSPHRASE_FILE = "ziipsan_campo_db_passphrase.bin"
    private const val WRAP_KEY_ALIAS = "ziipsan_campo_db_wrap_key"
    private const val PASSPHRASE_BYTES = 32
    private const val GCM_TAG_BITS = 128

    @Volatile
    private var database: SQLiteDatabase? = null

    // ------------------------------------------------------------------
    // Abertura / fecho (singleton)
    // ------------------------------------------------------------------

    @Synchronized
    fun openDatabase(context: Context): SQLiteDatabase {
        database?.let { if (it.isOpen) return it }
        SQLiteDatabase.loadLibs(context)
        val dbFile = context.getDatabasePath(DB_NAME)
        dbFile.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, obtainPassphrase(context), null)
        createSchema(db)
        database = db
        return db
    }

    /** Fecha a ligacao aberta, se houver. Chamado pelo wipeAll antes de apagar. */
    @Synchronized
    fun closeOpenDatabase() {
        database?.let { if (it.isOpen) it.close() }
        database = null
    }

    /** Apaga o blob da passphrase embrulhada. Chamado pelo wipeAll. */
    fun deleteWrappedPassphrase(context: Context): Boolean {
        val file = File(context.filesDir, WRAPPED_PASSPHRASE_FILE)
        return !file.exists() || file.delete()
    }

    // ------------------------------------------------------------------
    // Operacoes do LocalStore (a semantica mora aqui; o plugin delega)
    // ------------------------------------------------------------------

    fun createSchema(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS routes (" +
                "route_id TEXT PRIMARY KEY, " +
                "package_json TEXT NOT NULL, " +
                "task_map_json TEXT NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS pending_readings (" +
                "task_id TEXT PRIMARY KEY, " +
                "route_id TEXT NOT NULL, " +
                "reading_json TEXT NOT NULL)",
        )
    }

    /** Grava o pacote e deriva o mapa task->route das tasks do proprio pacote. */
    fun saveRoute(db: SQLiteDatabase, routePackageJson: String) {
        val pkg = JSONObject(routePackageJson)
        val routeId = pkg.getString("routeId")
        val tasks = pkg.getJSONArray("tasks")
        val taskIds = JSONArray()
        for (i in 0 until tasks.length()) {
            taskIds.put(tasks.getJSONObject(i).getString("taskId"))
        }
        val values = ContentValues().apply {
            put("route_id", routeId)
            put("package_json", routePackageJson)
            put("task_map_json", taskIds.toString())
        }
        db.insertWithOnConflict("routes", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Leituras pendentes da rota, como array JSON dos PendingReading gravados. */
    fun pendingReadingsJson(db: SQLiteDatabase, routeId: String): String {
        val out = JSONArray()
        db.rawQuery(
            "SELECT reading_json FROM pending_readings WHERE route_id = ? ORDER BY rowid",
            arrayOf(routeId),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                out.put(JSONObject(cursor.getString(0)))
            }
        }
        return out.toString()
    }

    /**
     * Upsert por taskId. A rota deriva do mapa task->route gravado no
     * saveRoute; tarefa fora de qualquer mapa = UnknownTaskException.
     */
    fun saveReading(db: SQLiteDatabase, readingJson: String) {
        val reading = JSONObject(readingJson)
        val taskId = reading.getString("taskId")
        val routeId = findRouteForTask(db, taskId) ?: throw UnknownTaskException(taskId)
        val values = ContentValues().apply {
            put("task_id", taskId)
            put("route_id", routeId)
            put("reading_json", readingJson)
        }
        db.insertWithOnConflict("pending_readings", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Apaga pacote, mapa e leituras SOMENTE da rota indicada. */
    fun clearRoute(db: SQLiteDatabase, routeId: String) {
        db.delete("pending_readings", "route_id = ?", arrayOf(routeId))
        db.delete("routes", "route_id = ?", arrayOf(routeId))
    }

    private fun findRouteForTask(db: SQLiteDatabase, taskId: String): String? {
        db.rawQuery("SELECT route_id, task_map_json FROM routes", null).use { cursor ->
            while (cursor.moveToNext()) {
                val taskIds = JSONArray(cursor.getString(1))
                for (i in 0 until taskIds.length()) {
                    if (taskIds.getString(i) == taskId) return cursor.getString(0)
                }
            }
        }
        return null
    }

    // ------------------------------------------------------------------
    // Passphrase: geracao, embrulho (Keystore) e desembrulho em memoria
    // ------------------------------------------------------------------

    private fun obtainPassphrase(context: Context): String {
        val file = File(context.filesDir, WRAPPED_PASSPHRASE_FILE)
        if (file.exists()) {
            return unwrapPassphrase(file.readBytes()).toHex()
        }
        val raw = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        file.writeBytes(wrapPassphrase(raw))
        return raw.toHex()
    }

    /** Formato do blob: [1 byte: tamanho do IV][IV][ciphertext+GCM tag]. */
    private fun wrapPassphrase(raw: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, ensureWrapKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(raw)
        return byteArrayOf(iv.size.toByte()) + iv + ciphertext
    }

    private fun unwrapPassphrase(blob: ByteArray): ByteArray {
        val ivSize = blob[0].toInt()
        val iv = blob.copyOfRange(1, 1 + ivSize)
        val ciphertext = blob.copyOfRange(1 + ivSize, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, ensureWrapKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun ensureWrapKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(WRAP_KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                WRAP_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
