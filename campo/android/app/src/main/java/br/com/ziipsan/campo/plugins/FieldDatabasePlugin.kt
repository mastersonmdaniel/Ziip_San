package br.com.ziipsan.campo.plugins

import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import org.json.JSONException

/**
 * Banco local cifrado do app de campo (RG2-C-P4 / P-04).
 *
 * Implementa, sobre o SQLCipher aberto por FieldDatabaseSupport, as quatro
 * operacoes do LocalStore (sync/syncEngine.ts) cuja implementacao real
 * faltava: saveRoute, pendingReadings, saveReading e clearRoute — a porta
 * TS e sync/sqlCipherLocalStore.ts sobre plugins/fieldDatabase.ts.
 *
 * Pela ponte so atravessa JSON dos DTOs reais (RoutePackage /
 * PendingReading). A passphrase do SQLCipher NUNCA atravessa a ponte:
 * ela nasce, e embrulhada pela chave nao-extraivel do Android Keystore e
 * desembrulhada so em memoria dentro do nativo (ver FieldDatabaseSupport,
 * desenho que resolve o achado SS4-B da revisao cruzada da ED-18).
 */
@CapacitorPlugin(name = "FieldDatabase")
class FieldDatabasePlugin : Plugin() {

    @PluginMethod
    fun saveRoute(call: PluginCall) {
        val pkgJson = call.getString("routePackageJson")
            ?: return call.reject("routePackageJson obrigatorio")
        try {
            FieldDatabaseSupport.saveRoute(FieldDatabaseSupport.openDatabase(context), pkgJson)
            call.resolve()
        } catch (e: JSONException) {
            call.reject("routePackageJson invalido: ${e.message}")
        } catch (e: Exception) {
            call.reject("falha ao gravar a rota: ${e.javaClass.simpleName}")
        }
    }

    @PluginMethod
    fun pendingReadings(call: PluginCall) {
        val routeId = call.getString("routeId") ?: return call.reject("routeId obrigatorio")
        try {
            val json = FieldDatabaseSupport.pendingReadingsJson(
                FieldDatabaseSupport.openDatabase(context),
                routeId,
            )
            call.resolve(JSObject().put("readingsJson", json))
        } catch (e: Exception) {
            call.reject("falha ao ler as pendencias: ${e.javaClass.simpleName}")
        }
    }

    @PluginMethod
    fun saveReading(call: PluginCall) {
        val readingJson = call.getString("readingJson")
            ?: return call.reject("readingJson obrigatorio")
        try {
            FieldDatabaseSupport.saveReading(FieldDatabaseSupport.openDatabase(context), readingJson)
            call.resolve()
        } catch (e: UnknownTaskException) {
            // Erro NOMEADO do contrato: a mensagem (UNKNOWN_TASK: ...) e o
            // nome que o TS propaga — nao a reescrever aqui.
            call.reject(e.message)
        } catch (e: JSONException) {
            call.reject("readingJson invalido: ${e.message}")
        } catch (e: Exception) {
            call.reject("falha ao gravar a leitura: ${e.javaClass.simpleName}")
        }
    }

    @PluginMethod
    fun clearRoute(call: PluginCall) {
        val routeId = call.getString("routeId") ?: return call.reject("routeId obrigatorio")
        try {
            FieldDatabaseSupport.clearRoute(FieldDatabaseSupport.openDatabase(context), routeId)
            call.resolve()
        } catch (e: Exception) {
            call.reject("falha ao limpar a rota: ${e.javaClass.simpleName}")
        }
    }
}
