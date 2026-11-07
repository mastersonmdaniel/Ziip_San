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

/**
 * Os alertas ao consumidor da curva horária (ED-17): consumo contínuo por
 * 24 horas e pico, e os dois usos da curva — exibição ao usuário pelo
 * parâmetro e prova na revisão por vazamento oculto sempre.
 */
class ConsumerAlertAndAccessTest {

    private static final UUID POINT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID CHANNEL = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-11-09T00:00:00Z");

    private static HourlySample hour(int offset, String volume) {
        return new HourlySample(POINT, CHANNEL, UNIT, T0.plusHours(offset), new BigDecimal(volume),
                BigDecimal.ZERO, DataState.MEASURED, Set.of());
    }

    @Test
    @DisplayName("24 horas seguidas com consumo disparam o alerta de consumo contínuo; 23 não")
    void continuous24Hours() {
        List<HourlySample> day = IntStream.range(0, 24).mapToObj(h -> hour(h, "5")).toList();
        assertThat(ContinuousConsumptionRule.hasContinuous24Hours(day)).isTrue();
        assertThat(ContinuousConsumptionRule.hasContinuous24Hours(day.subList(0, 23))).isFalse();

        List<HourlySample> withGap = IntStream.range(0, 24)
                .mapToObj(h -> hour(h, h == 10 ? "0" : "5")).toList();
        assertThat(ContinuousConsumptionRule.hasContinuous24Hours(withGap)).isFalse();

        // 24 amostras com uma hora FALTANDO (0..24 sem a 10) cobrem 25 horas
        // de relógio: não são 24 horas contínuas e não disparam.
        List<HourlySample> withMissingHour = IntStream.range(0, 25)
                .filter(h -> h != 10)
                .mapToObj(h -> hour(h, "5")).toList();
        assertThat(withMissingHour).hasSize(24);
        assertThat(ContinuousConsumptionRule.hasContinuous24Hours(withMissingHour)).isFalse();
    }

    @Test
    @DisplayName("pico é a hora estritamente acima do limiar; igual ao limiar não dispara; sem limiar não há pico")
    void peakAboveThreshold() {
        List<HourlySample> samples = List.of(hour(0, "10"), hour(1, "900"), hour(2, "20"));
        assertThat(ContinuousConsumptionRule.peakAbove(samples, new BigDecimal("850")))
                .hasValueSatisfying(peak -> assertThat(peak.volumeLitres()).isEqualByComparingTo("900"));
        assertThat(ContinuousConsumptionRule.peakAbove(samples, new BigDecimal("900"))).isEmpty();
        assertThat(ContinuousConsumptionRule.peakAbove(samples, null)).isEmpty();
    }

    @Test
    @DisplayName("a curva só é visível ao usuário com o parâmetro ligado, mas é prova na revisão sempre")
    void hourlyProfileVisibilityAndEvidence() {
        assertThat(HourlyProfileAccessRule.visibleToCustomer(true)).isTrue();
        assertThat(HourlyProfileAccessRule.visibleToCustomer(false)).isFalse();
        assertThat(HourlyProfileAccessRule.usableAsLeakReviewEvidence(true)).isTrue();
        assertThat(HourlyProfileAccessRule.usableAsLeakReviewEvidence(false)).isTrue();
    }
}
