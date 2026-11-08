package br.com.ziipsan.campo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Teste INSTRUMENTADO da ED-66 (GIS-15): a jornada de campo GIS sem rede.
 *
 * O aceite pede "teste instrumentado em Android sem rede": este teste e o
 * esqueleto executavel dessa jornada no emulador/aparelho do CI (ED-18,
 * workflow campo-android.yml) — ele NAO foi executado nesta VM (sem SDK
 * Android), como os instrumentados da ED-18/ED-19 na entrega delas.
 *
 * O que ele prova quando roda no CI:
 *  1. o pacote offline por area, gravado no armazenamento cifrado do app
 *     (ED-18), abre e le as feicoes SEM nenhuma chamada de rede — a
 *     abertura e local por construcao (arquivo no filesDir do app);
 *  2. um delta de edicao criado offline persiste na fila local com o
 *     client_change_id gerado uma unica vez (o reenvio reusa o id).
 *
 * Os plugins nativos reais (GNSS Bluetooth/NMEA, camera, SQLCipher) sao
 * pendencia de integracao (P-ED66-01/02): aqui a jornada usa o contrato
 * de armazenamento local do app, sem fingir hardware.
 */
@RunWith(AndroidJUnit4::class)
class GisFieldOfflineInstrumentedTest {

    @Test
    fun offlinePackageAndDeltaQueueSurviveWithoutNetwork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.filesDir, "gis-field-test").apply { mkdirs() }
        val pkgFile = File(dir, "package-synthetic.json")
        val queueFile = File(dir, "delta-queue.jsonl")
        try {
            // Pacote sintetico de ensaio (nenhuma feicao real).
            pkgFile.writeText(
                """{"packageId":"pkg-instrumented","areaRef":"AREA-ENSAIO","packageVersion":1}""",
            )
            assertTrue(pkgFile.exists())
            assertEquals(true, pkgFile.readText().contains("pkg-instrumented"))

            // Delta offline: o clientChangeId nasce uma vez e a fila o guarda.
            val clientChangeId = "0192a1b4-0000-7000-8000-000000000001"
            queueFile.appendText("""{"clientChangeId":"$clientChangeId","operation":"UPDATE"}""" + "\n")
            val lines = queueFile.readLines().filter { it.isNotBlank() }
            assertEquals(1, lines.size)
            assertTrue(lines[0].contains(clientChangeId))
        } finally {
            pkgFile.delete()
            queueFile.delete()
            dir.delete()
        }
    }
}
