package br.com.ziipsan.campo

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.ziipsan.campo.plugins.FieldDatabaseSupport
import br.com.ziipsan.campo.plugins.UnknownTaskException
import java.io.File
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Teste INSTRUMENTADO da RG2-C-P4 (lote mobile-integracao-p03-p04, frente A):
 * exercita o banco local SQLCipher REAL (FieldDatabaseSupport) no aparelho/
 * emulador — gravar e reler apos FECHAR e REABRIR o banco, upsert por
 * taskId, erro nomeado de tarefa desconhecida, clearRoute so da rota e o
 * ficheiro da passphrase embrulhada.
 *
 * ESTADO HONESTO: NAO RODADO na producao deste pacote — esta VM nao tem
 * SDK Android (precedente da ED-18: os instrumentados dela tambem foram
 * entregues sem execucao local). A prova executada aqui e a dos testes TS
 * (node --test) sobre a fiacao; este teste e a prova do nativo, para o CI/
 * aparelho do dono.
 */
@RunWith(AndroidJUnit4::class)
class FieldDatabaseInstrumentedTest {

    private lateinit var context: Context

    private fun routePackageJson(routeId: String, vararg taskIds: String): String {
        val tasks = JSONArray()
        for ((i, taskId) in taskIds.withIndex()) {
            tasks.put(
                org.json.JSONObject()
                    .put("taskId", taskId)
                    .put("unitId", "unit-$taskId")
                    .put("installationId", "inst-$taskId")
                    .put("kind", "WATER")
                    .put("reference", "2026-11")
                    .put("previousReading", 12000)
                    .put("meterDigits", 5)
                    .put("sequence", i + 1),
            )
        }
        return org.json.JSONObject()
            .put("routeId", routeId)
            .put("reference", "2026-11")
            .put("downloadedAt", "2026-11-10T06:00:00Z")
            .put(
                "occurrences",
                JSONArray().put(
                    org.json.JSONObject()
                        .put("code", "NORMAL")
                        .put("requiresPhoto", false)
                        .put("requiresReading", true),
                ),
            )
            .put("tasks", tasks)
            .toString()
    }

    private fun readingJson(taskId: String, reading: Int): String =
        org.json.JSONObject()
            .put("taskId", taskId)
            .put("reading", reading)
            .put("occurrenceCode", org.json.JSONObject.NULL)
            .put("photoRefs", JSONArray())
            .put("ocrValue", org.json.JSONObject.NULL)
            .put("capturedAt", "2026-11-10T09:00:00Z")
            .toString()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        FieldDatabaseSupport.closeOpenDatabase()
        context.deleteDatabase(FieldDatabaseSupport.DB_NAME)
        FieldDatabaseSupport.deleteWrappedPassphrase(context)
    }

    @After
    fun tearDown() {
        FieldDatabaseSupport.closeOpenDatabase()
        context.deleteDatabase(FieldDatabaseSupport.DB_NAME)
        FieldDatabaseSupport.deleteWrappedPassphrase(context)
    }

    @Test
    fun saveAndReadBackAfterReopen() {
        var db = FieldDatabaseSupport.openDatabase(context)
        FieldDatabaseSupport.saveRoute(db, routePackageJson("rota-1", "task-1", "task-2"))
        FieldDatabaseSupport.saveReading(db, readingJson("task-1", 12012))
        // Reload real: fecha e reabre — a leitura tem de vir do disco cifrado.
        FieldDatabaseSupport.closeOpenDatabase()
        db = FieldDatabaseSupport.openDatabase(context)
        val pending = JSONArray(FieldDatabaseSupport.pendingReadingsJson(db, "rota-1"))
        assertEquals(1, pending.length())
        assertEquals("task-1", pending.getJSONObject(0).getString("taskId"))
        assertEquals(12012, pending.getJSONObject(0).getInt("reading"))
    }

    @Test
    fun upsertByTaskIdDoesNotDuplicate() {
        val db = FieldDatabaseSupport.openDatabase(context)
        FieldDatabaseSupport.saveRoute(db, routePackageJson("rota-1", "task-1"))
        FieldDatabaseSupport.saveReading(db, readingJson("task-1", 12012))
        FieldDatabaseSupport.saveReading(db, readingJson("task-1", 12020))
        val pending = JSONArray(FieldDatabaseSupport.pendingReadingsJson(db, "rota-1"))
        assertEquals(1, pending.length())
        assertEquals(12020, pending.getJSONObject(0).getInt("reading"))
    }

    @Test
    fun unknownTaskIsRejectedWithNamedError() {
        val db = FieldDatabaseSupport.openDatabase(context)
        FieldDatabaseSupport.saveRoute(db, routePackageJson("rota-1", "task-1"))
        val error = assertThrows(UnknownTaskException::class.java) {
            FieldDatabaseSupport.saveReading(db, readingJson("task-fantasma", 1))
        }
        assertTrue(error.message!!.startsWith("UNKNOWN_TASK"))
        assertEquals("[]", FieldDatabaseSupport.pendingReadingsJson(db, "rota-1"))
    }

    @Test
    fun clearRouteOnlyClearsThatRoute() {
        val db = FieldDatabaseSupport.openDatabase(context)
        FieldDatabaseSupport.saveRoute(db, routePackageJson("rota-1", "task-1"))
        FieldDatabaseSupport.saveRoute(db, routePackageJson("rota-2", "task-9"))
        FieldDatabaseSupport.saveReading(db, readingJson("task-1", 12012))
        FieldDatabaseSupport.saveReading(db, readingJson("task-9", 777))
        FieldDatabaseSupport.clearRoute(db, "rota-1")
        assertEquals("[]", FieldDatabaseSupport.pendingReadingsJson(db, "rota-1"))
        assertEquals(1, JSONArray(FieldDatabaseSupport.pendingReadingsJson(db, "rota-2")).length())
    }

    @Test
    fun wrappedPassphraseFileExistsAndWipeRemovesIt() {
        FieldDatabaseSupport.openDatabase(context)
        val blob = File(context.filesDir, FieldDatabaseSupport.WRAPPED_PASSPHRASE_FILE)
        assertTrue(blob.exists())
        // Blob = [1 byte IV len][IV 12][ciphertext 32 + tag 16] = 61 bytes:
        // nunca a passphrase em claro (que teria 32 bytes crus / 64 em hex).
        assertEquals(61, blob.readBytes().size)
        assertTrue(FieldDatabaseSupport.deleteWrappedPassphrase(context))
        assertFalse(blob.exists())
    }
}
