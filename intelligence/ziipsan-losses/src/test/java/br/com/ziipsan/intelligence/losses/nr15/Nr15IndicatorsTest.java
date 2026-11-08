package br.com.ziipsan.intelligence.losses.nr15;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class Nr15IndicatorsTest {

    @Test
    @DisplayName("periodo mensal (2026-03): indices por ligacao segregados reais/aparentes, conferidos a mao")
    void monthlyIndicesSegregated() {
        YearMonth march = YearMonth.of(2026, 3);
        var snapshots = List.of(
                Nr15Fixtures.snapshot(Nr15Fixtures.inputsA(march, 500.0, true)),
                Nr15Fixtures.snapshot(Nr15Fixtures.inputsB(march, 200.0, true)));
        var indicators = Nr15Indicators.forPeriod("2026-03", snapshots);
        // ligacao-dias = (1000 + 500) x 31 = 46.500
        assertThat(indicators.totalLossesLPerConnDay())
                .isCloseTo(4800.0 * 1000 / 46500.0, within(0.001));
        assertThat(indicators.realLossesLPerConnDayOrNull())
                .isCloseTo(4100.0 * 1000 / 46500.0, within(0.001));
        assertThat(indicators.apparentLossesLPerConnDayOrNull())
                .isCloseTo(700.0 * 1000 / 46500.0, within(0.001));
        assertThat(indicators.nonRevenuePercent()).isCloseTo(5400.0 / 14500.0 * 100, within(0.001));
    }

    @Test
    @DisplayName("IVI e ILI por periodo: um so valor (CARL/UARL agregado), IVI == ILI")
    void iviIsIliAggregated() {
        YearMonth march = YearMonth.of(2026, 3);
        var snapshots = List.of(
                Nr15Fixtures.snapshot(Nr15Fixtures.inputsA(march, 500.0, true)),
                Nr15Fixtures.snapshot(Nr15Fixtures.inputsB(march, 200.0, true)));
        var indicators = Nr15Indicators.forPeriod("2026-03", snapshots);
        // UARL(A) = (18x12 + 0,8x1000 + 25x5) x 30 = 34.230 L/dia -> 1.061,13 m3 em 31 dias
        // UARL(B) = (18x6 + 0,8x500 + 25x2,5) x 30 = 17.115 L/dia -> 530,565 m3
        // ILI = (3000 + 1100) / (1061,13 + 530,565) = 2,5759
        assertThat(indicators.iliOrNull()).isCloseTo(2.5759, within(0.01));
        assertThat(indicators.iviOrNull()).isEqualTo(indicators.iliOrNull());
    }

    @Test
    @DisplayName("ILI anual agregado sobre as 24 fotos: soma(CARL)/soma(UARL) = 2,6253")
    void annualIliAggregated() {
        var indicators = Nr15Indicators.forPeriod("2026", Nr15Fixtures.yearSnapshots());
        // UARL anual: A 34.230 x 365 / 1000 = 12.493,95 m3; B 17.115 x 365 / 1000 = 6.246,975 m3
        // ILI = 49.200 / 18.740,925 = 2,62527
        assertThat(indicators.iliOrNull()).isCloseTo(2.6253, within(0.001));
        assertThat(indicators.realLossesLPerConnDayOrNull())
                .isCloseTo(49200.0 * 1000 / 547500.0, within(0.001));
        assertThat(indicators.apparentLossesLPerConnDayOrNull())
                .isCloseTo(8400.0 * 1000 / 547500.0, within(0.001));
    }

    @Test
    @DisplayName("um DMC com perda real mas sem ILI na ED-85: ILI do periodo AUSENTE (sem media, sem subconjunto)")
    void iliAbsentWhenAnyDmcLacksIt() {
        YearMonth march = YearMonth.of(2026, 3);
        var snapshots = List.of(
                Nr15Fixtures.snapshot(Nr15Fixtures.inputsA(march, 500.0, true)),
                Nr15Fixtures.snapshot(Nr15Fixtures.inputsB(march, 200.0, false)));
        var indicators = Nr15Indicators.forPeriod("2026-03", snapshots);
        assertThat(indicators.realLossesLPerConnDayOrNull()).isNotNull();
        assertThat(indicators.iliOrNull()).isNull();
        assertThat(indicators.iviOrNull()).isNull();
    }

    @Test
    @DisplayName("cobertura de medicao: hidrometacao 90% e macromedicao 95% das contagens; contagem invalida recusa")
    void meteringCoverageRatios() {
        var counts = new MeteringCoveragePort.CoverageCounts(900, 1000, 9500.0, 10000.0);
        assertThat(counts.hydroMeteringPercentOrNull()).isEqualTo(90.0);
        assertThat(counts.macroMeteringPercentOrNull()).isEqualTo(95.0);
        var indicators = Nr15Indicators.forPeriod("2026", Nr15Fixtures.yearSnapshots())
                .withCoverage(counts);
        assertThat(indicators.hydroMeteringPercentOrNull()).isEqualTo(90.0);
        assertThat(indicators.macroMeteringPercentOrNull()).isEqualTo(95.0);
        // Sem contagens, os indices de cobertura ficam ausentes (null), nunca estimados.
        var without = Nr15Indicators.forPeriod("2026", Nr15Fixtures.yearSnapshots());
        assertThat(without.hydroMeteringPercentOrNull()).isNull();
        assertThat(without.macroMeteringPercentOrNull()).isNull();
        assertThatThrownBy(() -> new MeteringCoveragePort.CoverageCounts(1100, 1000, 0.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hidrometradas");
    }
}
