package br.com.ziipsan.campo.plugins

import br.com.ziipsan.campo.billing.FieldBillingFacade
import br.com.ziipsan.commercial.billingrules.AmountLine
import br.com.ziipsan.commercial.billingrules.ArtifactHash
import br.com.ziipsan.commercial.billingrules.FieldBillInput
import br.com.ziipsan.commercial.billingrules.FieldTariffSnapshot
import br.com.ziipsan.commercial.billingrules.TariffBracket
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * Plugin Android da emissao simultanea (ED-19; ADR-0026).
 *
 * Expoe ao app de campo (Capacitor, ED-18) tres operacoes — e nenhuma delas
 * reimplementa regra:
 *  - verifyArtifactHash: confere o SHA-256 do JAR ziipsan-billing-rules
 *    embarcado contra o hash que o servidor carimbou no pacote da rota. O
 *    caminho do JAR e resolvido na integracao (o APK o carrega como
 *    dependencia; o hash e calculado sobre os bytes da entrada do artefato
 *    no APK — ver docs/campo/ED-19-EMISSAO-SIMULTANEA.md);
 *  - calculateFieldBill: monta o FieldBillInput a partir do JSON da tarefa
 *    e devolve o FieldBillResult do artefato compartilhado;
 *  - runContractFixtures: executa os vetores billing-contract-fixtures
 *    embarcados (a mesma lista que a JVM roda) e devolve as divergencias.
 *
 * A impressao Bluetooth (Zebra ZQ) NAO vive neste plugin: o comando termico
 * (ZPL/CPCL) e gerado pelo ThermalRenderer do artefato e enviado pelo
 * transporte Bluetooth do app — transporte e ensaio de hardware, fora do
 * modulo puro.
 */
@CapacitorPlugin(name = "BillingRules")
class BillingRulesPlugin : Plugin() {

    @PluginMethod
    fun verifyArtifactHash(call: PluginCall) {
        val expected = call.getString("expectedSha256")
            ?: return call.reject("expectedSha256 obrigatorio (hash do pacote da rota)")
        val artifactBase64 = call.getString("artifactBase64")
            ?: return call.reject("artifactBase64 obrigatorio nesta ponte de integracao")
        val bytes = android.util.Base64.decode(artifactBase64, android.util.Base64.DEFAULT)
        val actual = ArtifactHash.sha256Hex(bytes)
        val result = JSObject().put("match", expected.equals(actual, ignoreCase = true)).put("actual", actual)
        call.resolve(result)
    }

    @PluginMethod
    fun calculateFieldBill(call: PluginCall) {
        val json = call.getObject("input") ?: return call.reject("input obrigatorio")
        try {
            val result = FieldBillingFacade.calculate(parseInput(json))
            call.resolve(resultToJs(result))
        } catch (e: IllegalArgumentException) {
            call.reject(e.message)
        } catch (e: IllegalStateException) {
            call.reject(e.message)
        }
    }

    @PluginMethod
    fun runContractFixtures(call: PluginCall) {
        val failures = FieldBillingFacade.runContractFixtures()
        val array = JSONArray()
        failures.forEach { array.put(it) }
        call.resolve(JSObject().put("failures", array).put("passed", failures.isEmpty()))
    }

    private fun parseInput(json: JSONObject): FieldBillInput {
        val snap = json.getJSONObject("snapshot")
        val bracketsJson = snap.getJSONArray("brackets")
        val brackets = ArrayList<TariffBracket>()
        for (i in 0 until bracketsJson.length()) {
            val b = bracketsJson.getJSONObject(i)
            val width = if (b.isNull("widthM3")) null else b.getInt("widthM3")
            brackets.add(TariffBracket(width, BigDecimal(b.getString("pricePerM3"))))
        }
        val params = HashMap<String, String>()
        val rawParams = snap.optJSONObject("parameterVersions")
        if (rawParams != null) {
            val keys = rawParams.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                params[key] = rawParams.getString(key)
            }
        }
        val snapshot = FieldTariffSnapshot(
            UUID.fromString(snap.getString("tariffVersionId")),
            UUID.fromString(snap.getString("categoryId")),
            snap.getInt("minimumM3PerEconomy"),
            BigDecimal(snap.getString("fixedChargePerEconomy")),
            brackets,
            snap.getBoolean("sewerEnabled"),
            if (snap.isNull("sewerPct")) null else BigDecimal(snap.getString("sewerPct")),
            params,
        )
        return FieldBillInput(
            UUID.fromString(json.getString("taskId")),
            UUID.fromString(json.getString("unitId")),
            UUID.fromString(json.getString("installationId")),
            json.getInt("previousReading"),
            json.getInt("currentReading"),
            json.getInt("meterDigits"),
            json.getInt("economies"),
            snapshot,
            parseLines(json.optJSONArray("debits")),
            parseLines(json.optJSONArray("credits")),
            LocalDate.parse(json.getString("dueDate")),
        )
    }

    private fun parseLines(array: JSONArray?): List<AmountLine> {
        if (array == null) return emptyList()
        val out = ArrayList<AmountLine>()
        for (i in 0 until array.length()) {
            val line = array.getJSONObject(i)
            out.add(AmountLine(line.getString("code"), BigDecimal(line.getString("amount"))))
        }
        return out
    }

    private fun resultToJs(result: br.com.ziipsan.commercial.billingrules.FieldBillResult): JSObject {
        return JSObject()
            .put("taskId", result.taskId().toString())
            .put("consumptionM3", result.consumptionM3())
            .put("billedM3", result.billedM3())
            .put("waterValue", result.waterValue().toPlainString())
            .put("fixedValue", result.fixedValue().toPlainString())
            .put("sewerValue", result.sewerValue().toPlainString())
            .put("debitsValue", result.debitsValue().toPlainString())
            .put("creditsApplied", result.creditsApplied().toPlainString())
            .put("creditsNotApplied", result.creditsNotApplied().toPlainString())
            .put("total", result.total().toPlainString())
            .put("fingerprint", result.fingerprint())
    }
}
