package br.com.sigsan.operations.metering.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.List;

import static br.com.sigsan.operations.metering.rules.Fixtures.bd;
import static br.com.sigsan.operations.metering.rules.Fixtures.calcRes;
import static br.com.sigsan.operations.metering.rules.Fixtures.reading;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Volume de esgoto (relatório 02, 3.10; volumes de E-01, E-02 e E-06). Valores FICTÍCIOS,
 * recalculados à mão. O valor em reais do esgoto (112,00 x 0,80 etc.) é do módulo de faturamento
 * e não está aqui. Regra de arredondamento sob teste: volume x coleta / 100, HALF_UP para inteiro.
 */
class SewageVolumeCalculatorTest {

    /** Água REAL de 27 m3 (média 25, leitura 1000 -> 1027). */
    private ConsumptionResult water27() {
        return calcRes(25, reading(1000, 1027));
    }

    private ConsumptionResult real(int consumption) {
        return calcRes(Math.max(consumption, 12), reading(1000, 1000 + consumption));
    }

    private SewageVolumeCalculator.Result sewage(ConsumptionResult water, ConsumptionResult well, String collection) {
        return SewageVolumeCalculator.calculate(new SewageVolumeCalculator.Input(water, well, null, null, null,
                bd(collection)));
    }

    @ParameterizedTest(name = "coleta {0}% sobre 27 m3 de água -> {1} m3")
    @DisplayName("E-01 e E-02 (volume): 27 x 100% = 27; 27 x 80% = 21,6 -> 22 (HALF_UP)")
    @CsvSource({
            "100, 27",
            // E-02: 27 x 80 / 100 = 21,6 -> 22
            "80,  22",
            // 27 x 50 / 100 = 13,5 -> 14 (HALF_UP; HALF_DOWN daria 13)
            "50,  14",
            // 27 x 33,33 / 100 = 8,9991 -> 9
            "33.33, 9",
            "0,   0",
    })
    void collectionPercentage(String percent, int expected) {
        SewageVolumeCalculator.Result r = sewage(water27(), null, percent);
        assertThat(r.volume()).isEqualTo(expected);
        assertThat(r.type()).isEqualTo(ConsumptionType.REAL);
        assertThat(r.anomaly()).isNull();
    }

    @Test
    @DisplayName("E-06: esgoto sem água, só poço medido de 14 m3, coleta 100%: 14")
    void e06_wellWithoutWater() {
        SewageVolumeCalculator.Result r = sewage(null, real(14), "100");
        assertThat(r.volume()).isEqualTo(14);
    }

    @Test
    @DisplayName("poço medido somado à água faturada: 14 + 27 = 41; 41 x 80% = 32,8 -> 33")
    void wellPlusWater() {
        SewageVolumeCalculator.Result r = sewage(water27(), real(14), "80");
        assertThat(r.volume()).isEqualTo(33);
    }

    @Test
    @DisplayName("tipo de média não leva o percentual de coleta: média de 22 com coleta 80% continua 22")
    void averageTypesSkipCollection() {
        ConsumptionResult average = calcRes(22, reading(1000, null).cycleDate(LocalDate.of(2026, 10, 4)));
        assertThat(average.type()).isEqualTo(ConsumptionType.METER_AVERAGE);
        assertThat(sewage(average, null, "80").volume()).isEqualTo(22);
    }

    @Test
    @DisplayName("poço de tipo média não soma a água: o volume é o do poço (22), sem coleta")
    void wellAverageDoesNotAddWater() {
        ConsumptionResult wellAverage = calcRes(22, reading(1000, null).cycleDate(LocalDate.of(2026, 10, 4)));
        assertThat(sewage(water27(), wellAverage, "80").volume()).isEqualTo(22);
    }

    @Test
    @DisplayName("volume mínimo de esgoto: 5 m3 sobe para 8 com aviso e anormalidade; a coleta de 50% vem depois (8 x 50% = 4)")
    void sewerMinimum() {
        ConsumptionResult water5 = calcRes(10, reading(1000, 1005));
        assertThat(water5.billedConsumption()).isEqualTo(5);

        SewageVolumeCalculator.Result full = SewageVolumeCalculator.calculate(
                new SewageVolumeCalculator.Input(water5, null, null, null, 8, bd("100")));
        assertThat(full.volume()).isEqualTo(8);
        assertThat(full.anomaly()).isEqualTo(ConsumptionAnomalyKind.MINIMUM_FIXED);
        assertThat(full.warnings()).extracting(CalculationWarning::code)
                .containsExactly(WarningCode.SEWER_MINIMUM_APPLIED);

        SewageVolumeCalculator.Result half = SewageVolumeCalculator.calculate(
                new SewageVolumeCalculator.Input(water5, null, null, null, 8, bd("50")));
        assertThat(half.volume()).isEqualTo(4);

        // acima do mínimo nada muda
        SewageVolumeCalculator.Result above = SewageVolumeCalculator.calculate(
                new SewageVolumeCalculator.Input(water27(), null, null, null, 8, bd("100")));
        assertThat(above.volume()).isEqualTo(27);
        assertThat(above.warnings()).isEmpty();
    }

    @Test
    @DisplayName("consumo informado no poço sobrepõe o volume: 20, tipo INFORMADO (a água de 30 não soma)")
    void informedWellOverrides() {
        ConsumptionResult informedWell = calcRes(25, reading(1000, 1027).informedConsumption(20));
        ConsumptionResult water30 = calcRes(30, reading(1000, 1030));
        SewageVolumeCalculator.Result r = sewage(water30, informedWell, "100");
        assertThat(r.volume()).isEqualTo(20);
        assertThat(r.type()).isEqualTo(ConsumptionType.INFORMED);
    }

    @Test
    @DisplayName("sem água nem poço medido: usa o volume de referência informado, ou recusa")
    void referenceVolume() {
        SewageVolumeCalculator.Result r = SewageVolumeCalculator.calculate(new SewageVolumeCalculator.Input(null, null,
                10, ConsumptionType.NOT_METERED, null, bd("100")));
        assertThat(r.volume()).isEqualTo(10);
        assertThat(r.type()).isEqualTo(ConsumptionType.NOT_METERED);

        assertThatThrownBy(() -> sewage(null, null, "100")).isInstanceOfSatisfying(MeteringInputException.class,
                e -> assertThat(e.getCode()).isEqualTo(MeteringErrorCode.SEWER_VOLUME_UNAVAILABLE));
    }

    @Test
    @DisplayName("percentual de coleta ausente ou fora de 0 a 100 é recusado")
    void collectionPercentValidation() {
        assertThatThrownBy(() -> new SewageVolumeCalculator.Input(water27(), null, null, null, null, null))
                .isInstanceOf(MeteringInputException.class);
        assertThatThrownBy(() -> new SewageVolumeCalculator.Input(water27(), null, null, null, null, bd("100.01")))
                .isInstanceOf(MeteringInputException.class);
        assertThatThrownBy(() -> new SewageVolumeCalculator.Input(water27(), null, null, null, null, bd("-1")))
                .isInstanceOf(MeteringInputException.class);
    }

    @Test
    @DisplayName("água não faturável (faturado 0) gera esgoto 0, e a decisão fica visível no tipo")
    void nonBillableWaterGivesZero() {
        ConsumptionResult notBillable = Fixtures.calc(Fixtures.policy(), Fixtures.input(
                reading(1000, 1027).build(), List.of(Fixtures.res(1)), Fixtures.history(25))
                .billable(ConsumptionType.ESTIMATED));
        assertThat(notBillable.billedConsumption()).isZero();
        assertThat(sewage(notBillable, null, "100").volume()).isZero();
    }
}
