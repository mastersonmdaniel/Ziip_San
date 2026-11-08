package br.com.ziipsan.commercial.billingrules;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FieldBillCalculatorTest {

    static final UUID TARIFF_VERSION = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    static final UUID CATEGORY = UUID.fromString("00000000-0000-0000-0000-0000000000cc");

    static FieldTariffSnapshot snapshotA() {
        return new FieldTariffSnapshot(TARIFF_VERSION, CATEGORY, 10, new BigDecimal("12.50"),
                List.of(new TariffBracket(10, new BigDecimal("1.50")),
                        new TariffBracket(10, new BigDecimal("2.50")),
                        TariffBracket.open(new BigDecimal("4.00"))),
                true, new BigDecimal("80"), Map.of("tariff", "v2026-01"));
    }

    static FieldBillInput input(int previous, int current, int economies,
                                List<AmountLine> debits, List<AmountLine> credits) {
        return new FieldBillInput(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                previous, current, 5, economies, snapshotA(), debits, credits,
                LocalDate.of(2026, 12, 10));
    }

    @Test
    void simpleAboveMinimum() {
        FieldBillResult r = FieldBillCalculator.calculate(input(1000, 1012, 1, List.of(), List.of()));
        assertThat(r.consumptionM3()).isEqualTo(12);
        assertThat(r.billedM3()).isEqualTo(12);
        assertThat(r.waterValue()).isEqualByComparingTo("3.00");
        assertThat(r.fixedValue()).isEqualByComparingTo("12.50");
        assertThat(r.sewerValue()).isEqualByComparingTo("12.40");
        assertThat(r.total()).isEqualByComparingTo("27.90");
    }

    @Test
    void belowMinimumBillsMinimum() {
        FieldBillResult r = FieldBillCalculator.calculate(input(500, 504, 1, List.of(), List.of()));
        assertThat(r.consumptionM3()).isEqualTo(4);
        assertThat(r.billedM3()).isEqualTo(10);
        assertThat(r.waterValue()).isEqualByComparingTo("0.00");
        assertThat(r.total()).isEqualByComparingTo("22.50");
    }

    @Test
    void integerDivisionRemainderIsNotBilled() {
        // 91 m3 em 3 economias: (91 DIV 3) = 30 por economia — o resto (1 m3)
        // nao e cobrado, a mesma semantica DIV do motor da P2.5.
        FieldBillResult r91 = FieldBillCalculator.calculate(input(2000, 2091, 3, List.of(), List.of()));
        FieldBillResult r90 = FieldBillCalculator.calculate(input(2000, 2090, 3, List.of(), List.of()));
        assertThat(r91.billedM3()).isEqualTo(91);
        assertThat(r91.waterValue()).isEqualByComparingTo(r90.waterValue());
        assertThat(r91.total()).isEqualByComparingTo(r90.total());
    }

    @Test
    void creditsAreCappedAtChargesAndRemainderIsDeclared() {
        FieldTariffSnapshot noSewer = new FieldTariffSnapshot(TARIFF_VERSION, CATEGORY, 10,
                new BigDecimal("10.00"), List.of(TariffBracket.open(new BigDecimal("2.00"))),
                false, null, Map.of());
        FieldBillInput in = new FieldBillInput(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                100, 118, 5, 1, noSewer, List.of(),
                List.of(new AmountLine("CREDITO_ANTERIOR", new BigDecimal("50.00"))),
                LocalDate.of(2026, 12, 10));
        FieldBillResult r = FieldBillCalculator.calculate(in);
        assertThat(r.creditsApplied()).isEqualByComparingTo("26.00");
        assertThat(r.creditsNotApplied()).isEqualByComparingTo("24.00");
        assertThat(r.total()).isEqualByComparingTo("0.00");
    }

    @Test
    void fingerprintIsDeterministicAndSensitiveToReading() {
        FieldBillInput a = input(1000, 1012, 1, List.of(), List.of());
        FieldBillInput same = new FieldBillInput(a.taskId(), a.unitId(), a.installationId(),
                1000, 1012, 5, 1, snapshotA(), List.of(), List.of(), a.dueDate());
        FieldBillInput different = new FieldBillInput(a.taskId(), a.unitId(), a.installationId(),
                1000, 1013, 5, 1, snapshotA(), List.of(), List.of(), a.dueDate());
        assertThat(FieldBillCalculator.calculate(a).fingerprint())
                .isEqualTo(FieldBillCalculator.calculate(same).fingerprint());
        assertThat(FieldBillCalculator.calculate(a).fingerprint())
                .isNotEqualTo(FieldBillCalculator.calculate(different).fingerprint());
    }

    @Test
    void totalInvariantHolds() {
        FieldBillResult r = FieldBillCalculator.calculate(input(2000, 2090, 3,
                List.of(new AmountLine("TAXA_SERVICO", new BigDecimal("15.00"))),
                List.of(new AmountLine("CREDITO_ANTERIOR", new BigDecimal("20.00")))));
        assertThat(r.total()).isEqualByComparingTo(
                r.fixedValue().add(r.waterValue()).add(r.sewerValue())
                        .add(r.debitsValue()).subtract(r.creditsApplied()));
        assertThat(r.total()).isEqualByComparingTo("278.50");
    }
}
