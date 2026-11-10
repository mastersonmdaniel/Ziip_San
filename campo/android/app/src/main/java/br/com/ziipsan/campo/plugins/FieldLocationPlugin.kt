package br.com.ziipsan.campo.plugins

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Localizacao do aparelho para a TRILHA do trabalhador (P-MOB-GPS-01,
 * decisao 59; governanca da ED-20).
 *
 * Le a posicao comum do aparelho pelo LocationManager do Android,
 * SOMENTE em primeiro plano (app aberto): a vigia para quando o app sai
 * de primeiro plano (handleOnStop). A coleta continua com o app fechado
 * (foreground service) e a pendencia P-MOB-GPS-03 — NAO e simulada aqui.
 *
 * Pela ponte so atravessa o fix (plugins/fieldLocation.ts): latitude,
 * longitude, instante ISO 8601 UTC convertido do relogio do proprio fix
 * e a precisao horizontal quando o aparelho a declara. NENHUMA decisao
 * de trilha vive aqui: jornada, aviso, amostragem e fila sao do TS
 * (tracking/fieldTracker.ts) e a ingestao final e do servidor
 * (Tracking.ingest, ED-20). Erros nomeados do contrato, propagados sem
 * reescrita: LOCATION_PERMISSION_DENIED, LOCATION_WATCH_ALREADY_ACTIVE,
 * LOCATION_PROVIDER_UNAVAILABLE.
 *
 * Os fixes da vigia chegam ao TS pelo evento "position" do plugin
 * (notifyListeners) — e esse evento que a fiacao da ponte liga ao
 * onPosition de plugins/fieldLocation.ts.
 *
 * PROVA PENDENTE (P-MOB-GPS-07, precedente P-FA-01): este ficheiro NAO
 * foi compilado nem executado nesta entrega — nao ha SDK Android na VM
 * do produtor. A prova de compilacao e de comportamento em aparelho e
 * da CI Android, antes de qualquer uso real.
 */
@CapacitorPlugin(
    name = "FieldLocation",
    permissions = [
        Permission(
            alias = "location",
            strings = [
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ],
        ),
    ],
)
class FieldLocationPlugin : Plugin() {

    private var watchListener: LocationListener? = null
    private val pendingSingles = mutableListOf<SingleEntry>()

    private class SingleEntry(val call: PluginCall, val listener: LocationListener)

    private inner class WatchListener : LocationListener {
        override fun onLocationChanged(location: Location) {
            notifyListeners(EVENT_POSITION, locationToJs(location))
        }
    }

    private inner class SingleListener(private val call: PluginCall) : LocationListener {
        override fun onLocationChanged(location: Location) {
            locationManager()?.removeUpdates(this)
            synchronized(pendingSingles) { pendingSingles.removeAll { it.listener === this } }
            call.resolve(locationToJs(location))
        }
    }

    // ------------------------------------------------------------------
    // Posicao atual
    // ------------------------------------------------------------------

    @PluginMethod
    fun getCurrentPosition(call: PluginCall) {
        if (!hasLocationPermission()) {
            requestPermissionForAlias(ALIAS_LOCATION, call, "currentPositionPermissionCallback")
            return
        }
        currentPositionGranted(call)
    }

    @PermissionCallback
    fun currentPositionPermissionCallback(call: PluginCall) {
        if (!hasLocationPermission()) {
            call.reject("$ERROR_PERMISSION_DENIED: permissao de localizacao nao concedida")
            return
        }
        currentPositionGranted(call)
    }

    private fun currentPositionGranted(call: PluginCall) {
        val manager = locationManager()
        // Sem LocationManager ou sem fornecedor ativo, o contrato
        // devolve null (nenhum fix) — nao e erro: e o aparelho sem
        // GPS/rede de localizacao disponiveis.
        if (manager == null) return call.resolve(null as JSObject?)
        val provider = activeProvider(manager) ?: return call.resolve(null as JSObject?)
        // Fix NOVO, nunca o ultimo conhecido de cache: um fix antigo
        // devolvido como atual fingiria uma posicao que nao e a de agora.
        val listener = SingleListener(call)
        val entry = SingleEntry(call, listener)
        synchronized(pendingSingles) { pendingSingles.add(entry) }
        try {
            // requestSingleUpdate: via compativel com o minSdk do app
            // (getCurrentLocation exige API 30; o app declara minSdk 24).
            @Suppress("DEPRECATION")
            manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        } catch (e: SecurityException) {
            synchronized(pendingSingles) { pendingSingles.remove(entry) }
            call.reject("$ERROR_PERMISSION_DENIED: permissao revogada durante o pedido")
        }
    }

    // ------------------------------------------------------------------
    // Vigia em primeiro plano
    // ------------------------------------------------------------------

    @PluginMethod
    fun startWatch(call: PluginCall) {
        if (!hasLocationPermission()) {
            requestPermissionForAlias(ALIAS_LOCATION, call, "startWatchPermissionCallback")
            return
        }
        startWatchGranted(call)
    }

    @PermissionCallback
    fun startWatchPermissionCallback(call: PluginCall) {
        if (!hasLocationPermission()) {
            call.reject("$ERROR_PERMISSION_DENIED: permissao de localizacao nao concedida")
            return
        }
        startWatchGranted(call)
    }

    private fun startWatchGranted(call: PluginCall) {
        val minIntervalMs = call.getInt("minIntervalMs")?.toLong()
            ?: return call.reject("minIntervalMs obrigatorio")
        if (watchListener != null) {
            return call.reject("$ERROR_WATCH_ALREADY_ACTIVE: ja existe uma vigia ativa")
        }
        val manager = locationManager()
            ?: return call.reject("$ERROR_PROVIDER_UNAVAILABLE: LocationManager indisponivel")
        val provider = activeProvider(manager)
            ?: return call.reject("$ERROR_PROVIDER_UNAVAILABLE: nenhum fornecedor de localizacao ativo")
        val listener = WatchListener()
        try {
            // Distancia minima 0 de proposito: a cadencia de COLETA da
            // trilha e a amostragem do motor TS; aqui so se pede ao
            // sistema o intervalo minimo entre entregas.
            manager.requestLocationUpdates(
                provider, minIntervalMs, 0f, listener, Looper.getMainLooper(),
            )
        } catch (e: SecurityException) {
            return call.reject("$ERROR_PERMISSION_DENIED: permissao revogada durante o pedido")
        }
        watchListener = listener
        call.resolve()
    }

    @PluginMethod
    fun stopWatch(call: PluginCall) {
        stopWatchInternal()
        call.resolve()
    }

    private fun stopWatchInternal() {
        val listener = watchListener ?: return
        watchListener = null
        locationManager()?.removeUpdates(listener)
    }

    // ------------------------------------------------------------------
    // Ciclo de vida: primeiro plano apenas (P-MOB-GPS-03 fica fora daqui)
    // ------------------------------------------------------------------

    override fun handleOnStop() {
        stopWatchInternal()
        settlePendingSinglesWithoutFix()
        super.handleOnStop()
    }

    override fun handleOnDestroy() {
        stopWatchInternal()
        settlePendingSinglesWithoutFix()
        super.handleOnDestroy()
    }

    /** Saindo de primeiro plano sem fix novo: resolve null, como o contrato declara. */
    private fun settlePendingSinglesWithoutFix() {
        val pending: List<SingleEntry>
        synchronized(pendingSingles) {
            pending = pendingSingles.toList()
            pendingSingles.clear()
        }
        val manager = locationManager()
        for (single in pending) {
            manager?.removeUpdates(single.listener)
            single.call.resolve(null as JSObject?)
        }
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    private fun locationManager(): LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

    /** GPS quando ativo; senao o fornecedor de rede; senao nenhum. */
    private fun activeProvider(manager: LocationManager): String? = when {
        manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
            LocationManager.NETWORK_PROVIDER
        else -> null
    }

    private fun locationToJs(location: Location): JSObject {
        val data = JSObject()
        data.put("latitude", location.latitude)
        data.put("longitude", location.longitude)
        data.put("at", isoUtc(location.time))
        if (location.hasAccuracy()) {
            data.put("accuracyM", location.accuracy.toDouble())
        } else {
            data.put("accuracyM", JSONObject.NULL)
        }
        return data
    }

    private fun isoUtc(epochMs: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date(epochMs))
    }

    companion object {
        private const val ALIAS_LOCATION = "location"
        private const val EVENT_POSITION = "position"
        private const val ERROR_PERMISSION_DENIED = "LOCATION_PERMISSION_DENIED"
        private const val ERROR_WATCH_ALREADY_ACTIVE = "LOCATION_WATCH_ALREADY_ACTIVE"
        private const val ERROR_PROVIDER_UNAVAILABLE = "LOCATION_PROVIDER_UNAVAILABLE"
    }
}
