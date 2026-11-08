package br.com.ziipsan.platform.portal.verify;

import br.com.ziipsan.core.json.Json;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Telemetria de verificacoes para a aba Seguranca da Central (P3.35):
 * agrega as consultas registradas na V8_3 (sem dado do titular — a fonte
 * ja nasce sem ele) e as denuncias do periodo. E o mesmo dado que alimenta
 * o alerta de volume: pico de {@code NOT_AUTHENTIC} ou de {@code RATE_LIMITED}
 * e sinal de varredura/enumeracao ou de campanha de golpe em curso.
 *
 * <p>JSON somente pela classe {@link Json} do core ({@link #toJson}).
 */
public final class VerificationTelemetry {

    public record WindowCounts(long authentic, long notAuthentic, long rateLimited, long fraudReports) {
        public WindowCounts {
            if (authentic < 0 || notAuthentic < 0 || rateLimited < 0 || fraudReports < 0) {
                throw new IllegalArgumentException("contagens de telemetria nao podem ser negativas");
            }
        }

        public long totalQueries() {
            return authentic + notAuthentic + rateLimited;
        }
    }

    public record TelemetryView(UUID tenantId, Instant from, Instant to, long totalQueries,
                                long authentic, long notAuthentic, long rateLimited, long fraudReports) { }

    /** Porta de contagens sobre as consultas e denuncias registradas (V8_3). */
    public interface TelemetryPort {
        WindowCounts countsInWindow(UUID tenantId, Instant from, Instant to);
    }

    private VerificationTelemetry() { }

    public static TelemetryView build(UUID tenantId, TelemetryPort port, Instant from, Instant to) {
        if (tenantId == null || port == null || from == null || to == null || !to.isAfter(from)) {
            throw new IllegalArgumentException("telemetria exige tenant, porta e janela valida (to depois de from)");
        }
        WindowCounts counts = port.countsInWindow(tenantId, from, to);
        if (counts == null) {
            throw new IllegalStateException("porta de telemetria devolveu contagens nulas");
        }
        return new TelemetryView(tenantId, from, to, counts.totalQueries(), counts.authentic(),
                counts.notAuthentic(), counts.rateLimited(), counts.fraudReports());
    }

    public static String toJson(TelemetryView view) {
        if (view == null) {
            throw new IllegalArgumentException("visao de telemetria e obrigatoria");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("tenantId", view.tenantId().toString());
        map.put("from", view.from().toString());
        map.put("to", view.to().toString());
        map.put("totalQueries", view.totalQueries());
        map.put("authentic", view.authentic());
        map.put("notAuthentic", view.notAuthentic());
        map.put("rateLimited", view.rateLimited());
        map.put("fraudReports", view.fraudReports());
        return Json.write(map);
    }
}
