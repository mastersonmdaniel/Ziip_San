package br.com.ziipsan.operations.metering.telemetry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** A detecção dos alarmes padronizados sobre a série horária (ED-17), nos limiares exatos. */
class TelemetryAlarmRuleTest {

    private static final UUID POINT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID CHANNEL = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-11-10T00:00:00Z");

    private static HourlySample hour(int offset, String volume, String reverse, Set<MeterAlarmKind> flags) {
        return new HourlySample(POINT, CHANNEL, UNIT, T0.plusHours(offset), new BigDecimal(volume),
                new BigDecimal(reverse), DataState.MEASURED, flags);
    }

    @Test
    @DisplayName("fluxo contínuo dispara ao atingir o limiar de horas, e não antes")
    void continuousFlowFiresAtThreshold() {
        List<HourlySample> two = IntStream.range(0, 2).mapToObj(h -> hour(h, "5", "0", Set.of())).toList();
        assertThat(TelemetryAlarmRule.detect(two, 3, new BigDecimal("50"))).isEmpty();

        List<HourlySample> three = IntStream.range(0, 3).mapToObj(h -> hour(h, "5", "0", Set.of())).toList();
        List<TelemetryAlarmRule.Detected> detected = TelemetryAlarmRule.detect(three, 3, new BigDecimal("50"));
        assertThat(detected).hasSize(1);
        assertThat(detected.get(0).kind()).isEqualTo(MeterAlarmKind.CONTINUOUS_FLOW);
        assertThat(detected.get(0).at()).isEqualTo(T0.plusHours(2));
    }

    @Test
    @DisplayName("uma hora sem fluxo quebra a sequência de fluxo contínuo")
    void gapBreaksContinuousRun() {
        List<HourlySample> samples = List.of(
                hour(0, "5", "0", Set.of()), hour(1, "5", "0", Set.of()),
                hour(2, "0", "0", Set.of()), hour(3, "5", "0", Set.of()));
        assertThat(TelemetryAlarmRule.detect(samples, 3, new BigDecimal("50"))).isEmpty();
    }

    @Test
    @DisplayName("um buraco de horas sem amostra também quebra a sequência de fluxo contínuo")
    void missingHourBreaksContinuousRun() {
        // Horas 0, 1 e 3 com fluxo: sem a hora 2 não há 3 horas contínuas.
        List<HourlySample> samples = List.of(
                hour(0, "5", "0", Set.of()), hour(1, "5", "0", Set.of()), hour(3, "5", "0", Set.of()));
        assertThat(TelemetryAlarmRule.detect(samples, 3, new BigDecimal("50"))).isEmpty();
    }

    @Test
    @DisplayName("fluxo reverso acumulado dispara ao atingir o limiar em litros")
    void reverseFlowFiresAtThreshold() {
        List<HourlySample> samples = List.of(
                hour(0, "0", "20", Set.of()), hour(1, "0", "20", Set.of()), hour(2, "0", "10", Set.of()));
        List<TelemetryAlarmRule.Detected> detected = TelemetryAlarmRule.detect(samples, 24, new BigDecimal("50"));
        assertThat(detected).hasSize(1);
        assertThat(detected.get(0).kind()).isEqualTo(MeterAlarmKind.REVERSE_FLOW);
        assertThat(detected.get(0).at()).isEqualTo(T0.plusHours(2));
    }

    @Test
    @DisplayName("os alarmes sinalizados pelo dispositivo são repassados um a um")
    void deviceFlagsArePassedThrough() {
        List<HourlySample> samples = List.of(hour(0, "0", "0",
                Set.of(MeterAlarmKind.EMPTY_PIPE, MeterAlarmKind.TAMPERING, MeterAlarmKind.OVERFLOW)));
        List<TelemetryAlarmRule.Detected> detected = TelemetryAlarmRule.detect(samples, 24, new BigDecimal("50"));
        assertThat(detected).extracting(TelemetryAlarmRule.Detected::kind)
                .containsExactlyInAnyOrder(MeterAlarmKind.EMPTY_PIPE, MeterAlarmKind.TAMPERING,
                        MeterAlarmKind.OVERFLOW);
    }
}
