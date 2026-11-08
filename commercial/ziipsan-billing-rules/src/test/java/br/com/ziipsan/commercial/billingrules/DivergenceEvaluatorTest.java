package br.com.ziipsan.commercial.billingrules;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DivergenceEvaluatorTest {

    private FieldBillInput input(UUID tariffVersion, int previous, int current) {
        FieldTariffSnapshot snapshot = new FieldTariffSnapshot(tariffVersion,
                FieldBillCalculatorTest.CATEGORY, 10, new BigDecimal("12.50"),
                List.of(new TariffBracket(10, new BigDecimal("1.50")),
                        TariffBracket.open(new BigDecimal("4.00"))),
                true, new BigDecimal("80"), Map.of("tariff", "v2026-01"));
        return new FieldBillInput(UUID.fromString("00000000-0000-0000-0000-0000000000f1"),
                UUID.randomUUID(), UUID.randomUUID(), previous, current, 5, 1, snapshot,
                List.of(), List.of(), LocalDate.of(2026, 12, 10));
    }

    @Test
    void identicalRecalculationIsNotDivergent() {
        FieldBillInput field = input(FieldBillCalculatorTest.TARIFF_VERSION, 1000, 1012);
        FieldBillInput server = input(FieldBillCalculatorTest.TARIFF_VERSION, 1000, 1012);
        DivergenceEvaluator.Divergence d = DivergenceEvaluator.evaluate(field,
                FieldBillCalculator.calculate(field), server, FieldBillCalculator.calculate(server));
        assertThat(d.divergent()).isFalse();
        assertThat(d.differences()).isEmpty();
    }

    @Test
    void differentReadingDivergesInValueWithExplanation() {
        FieldBillInput field = input(FieldBillCalculatorTest.TARIFF_VERSION, 1000, 1012);
        FieldBillInput server = input(FieldBillCalculatorTest.TARIFF_VERSION, 1000, 1020);
        DivergenceEvaluator.Divergence d = DivergenceEvaluator.evaluate(field,
                FieldBillCalculator.calculate(field), server, FieldBillCalculator.calculate(server));
        assertThat(d.divergent()).isTrue();
        assertThat(d.basisDivergent()).isFalse();
        assertThat(d.differences()).extracting(DivergenceEvaluator.FieldDifference::field)
                .contains("consumptionM3", "waterValue", "sewerValue", "total");
    }

    @Test
    void differentTariffVersionDivergesInBasisEvenIfTotalsMatch() {
        UUID otherVersion = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
        FieldBillInput field = input(otherVersion, 1000, 1012);
        FieldBillInput server = input(FieldBillCalculatorTest.TARIFF_VERSION, 1000, 1012);
        DivergenceEvaluator.Divergence d = DivergenceEvaluator.evaluate(field,
                FieldBillCalculator.calculate(field), server, FieldBillCalculator.calculate(server));
        assertThat(d.divergent()).isTrue();
        assertThat(d.basisDivergent()).isTrue();
    }
}
