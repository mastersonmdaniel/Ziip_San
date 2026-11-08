package br.com.ziipsan.intelligence.losses.pressure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

class PressureZonePointAlarmTest {
    static final UUID TEN = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final LocalDateTime T0 = LocalDateTime.of(2026, 3, 10, 2, 0);

    static PressureSeriesPort portWith(String knownCode, List<PressureSeriesPort.PressureSample> series) {
        return new PressureSeriesPort() {
            public boolean exists(UUID t, String code) { return TEN.equals(t) && knownCode.equals(code); }
            public List<PressureSeriesPort.PressureSample> series(UUID t, String code,
                    LocalDateTime from, LocalDateTime to) { return series; }
        };
    }
    static PressureSeriesPort.PressureSample s(int hour, double mca) {
        return new PressureSeriesPort.PressureSample(T0.plusHours(hour), mca);
    }

    @Test @DisplayName("zona sem limites resolvidos nao existe (fronteira dura sem valor de fabrica)")
    void zoneRequiresLimits() {
        assertThatThrownBy(() -> new PressureZone(UUID.randomUUID(), TEN, "ZP-1", "Zona Norte", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("pressure.min_mca");
    }

    @Test @DisplayName("ponto de controle: registro fail-closed — codigo inexistente na ED-17 recusa; existente registra")
    void pointRegisterFailClosed() {
        var port = portWith("MP-PRESS-01", List.of());
        UUID zoneId = UUID.randomUUID();
        assertThatThrownBy(() -> PressurePoint.register(UUID.randomUUID(), TEN, zoneId,
                "PC-1", "MP-INEXISTENTE", "Rua A", port))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ED-17");
        PressurePoint ok = PressurePoint.register(UUID.randomUUID(), TEN, zoneId,
                "PC-1", "MP-PRESS-01", "Rua A", port);
        assertThat(ok.measuringPointCode()).isEqualTo("MP-PRESS-01");
        assertThatThrownBy(() -> PressurePoint.register(UUID.randomUUID(), TEN, zoneId,
                "PC-2", "MP-PRESS-01", null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test @DisplayName("alarme baixa e alta com desvios conhecidos: limites 10..60, serie 8/30/75/12 -> 1 LOW (desvio 2) e 1 HIGH (desvio 15)")
    void alarmsLowAndHigh() {
        var limits = PressureParameters.zoneLimits(10.0, 60.0);
        PressurePoint point = new PressurePoint(UUID.randomUUID(), TEN, UUID.randomUUID(),
                "PC-1", "MP-PRESS-01", "Rua A");
        var series = List.of(s(0, 8.0), s(1, 30.0), s(2, 75.0), s(3, 12.0));
        var ev = PressureAlarmEvaluator.evaluate(point, series, limits);
        assertThat(ev.samples()).isEqualTo(4);
        assertThat(ev.lowCount()).isEqualTo(1);
        assertThat(ev.highCount()).isEqualTo(1);
        assertThat(ev.minObservedMca()).isEqualTo(8.0);
        assertThat(ev.maxObservedMca()).isEqualTo(75.0);
        assertThat(ev.alarms()).extracting(PressureAlarmEvaluator.PressureAlarm::deviationMca)
                .containsExactly(2.0, 15.0);
    }

    @Test @DisplayName("serie vazia recusa a avaliacao (conformidade nao se presume); via porta confere existencia")
    void emptySeriesRefuses() {
        var limits = PressureParameters.zoneLimits(10.0, 60.0);
        PressurePoint point = new PressurePoint(UUID.randomUUID(), TEN, UUID.randomUUID(),
                "PC-1", "MP-PRESS-01", null);
        assertThatThrownBy(() -> PressureAlarmEvaluator.evaluate(point, List.of(), limits))
                .isInstanceOf(IllegalArgumentException.class);
        var port = portWith("OUTRO", List.of(s(0, 30.0)));
        assertThatThrownBy(() -> PressureAlarmEvaluator.evaluateFromPort(point, port, T0, T0.plusHours(4), limits))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ED-17");
    }
}
