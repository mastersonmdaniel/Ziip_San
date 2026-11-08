package br.com.ziipsan.commercial.collection.activedebt;

import br.com.ziipsan.commercial.collection.dunning.prescription.Prescription;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class FilingInstallmentClosingTest {
    private ActiveDebtEnrollment.Enrollment enrollment(String total, LocalDate due, LocalDate today) {
        var s = new ActiveDebtEnrollment(ActiveDebtTestSupport.gateAt(today), new ActiveDebtEnrollment.Book(),
                Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.GENERAL);
        return s.enrollIndividual(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(new ActiveDebtEnrollment.DebtItem(UUID.randomUUID(), due, new BigDecimal(total), null)), false, Set.of());
    }
    private FilingRecord filing(LocalDate today) {
        return new FilingRecord(ActiveDebtTestSupport.gateAt(today), ActiveDebtTestSupport.params(Map.of()),
                Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.GENERAL);
    }

    @Test void filingBelowEntityLawMinimumIsBlocked() {
        var e = enrollment("999.99", LocalDate.of(2025, 1, 10), LocalDate.of(2026, 11, 8));
        assertThatThrownBy(() -> filing(LocalDate.of(2026, 11, 8)).register(e, "0001234-56.2026.8.24.0000", "1a Vara", false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("da.min_filing_amount");
    }

    @Test void filingAtMinimumRegistersProcessCourtAndPhaseOnly() {
        var e = enrollment("1000.00", LocalDate.of(2025, 1, 10), LocalDate.of(2026, 11, 8));
        var r = filing(LocalDate.of(2026, 11, 8)).register(e, "0001234-56.2026.8.24.0000", "1a Vara da Fazenda", false);
        assertThat(r.processNumber()).isEqualTo("0001234-56.2026.8.24.0000");
        assertThat(r.court()).isEqualTo("1a Vara da Fazenda");
        assertThat(r.phase()).isEqualTo(FilingRecord.Phase.FILED);
        var moved = r.withPhase(FilingRecord.Phase.SUMMONS, LocalDate.of(2026, 12, 1)); // fase INFORMADA de fora
        assertThat(moved.phase()).isEqualTo(FilingRecord.Phase.SUMMONS);
    }

    @Test void missingMinFilingAmountIsExplicitErrorNotInvented() {
        var p = ActiveDebtParameters.fromValues(Map.of());
        assertThatThrownBy(p::minFilingAmount).hasMessageContaining("da.min_filing_amount");
    }

    @Test void installmentPlanSplitsExactlyAndRespectsLimits() {
        var e = enrollment("1000.00", LocalDate.of(2025, 1, 10), LocalDate.of(2026, 11, 8));
        var plan = ActiveDebtInstallment.plan(e, 3, LocalDate.of(2026, 12, 10), ActiveDebtTestSupport.params(Map.of()),
                ActiveDebtTestSupport.gateAt(LocalDate.of(2026, 11, 8)),
                Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.GENERAL, false);
        assertThat(plan.installments()).hasSize(3);
        assertThat(plan.installments().stream().map(ActiveDebtInstallment.Installment::amount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("1000.00");
        assertThat(plan.installments().get(0).amount()).isEqualByComparingTo("333.33");
        assertThat(plan.installments().get(2).amount()).isEqualByComparingTo("333.34");
        assertThatThrownBy(() -> ActiveDebtInstallment.plan(e, 13, LocalDate.of(2026, 12, 10), ActiveDebtTestSupport.params(Map.of()),
                        ActiveDebtTestSupport.gateAt(LocalDate.of(2026, 11, 8)),
                        Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.GENERAL, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1..12");
    }

    @Test void monthlyBalanceFreezesAndReprocessPreservesIt() {
        var e1 = enrollment("1000.00", LocalDate.of(2025, 1, 10), LocalDate.of(2026, 11, 8));
        var frozen = ActiveDebtMonthlyClosing.close(YearMonth.of(2026, 11), List.of(e1));
        assertThat(frozen.totalEnrolled()).isEqualByComparingTo("1000.00");
        var again = ActiveDebtMonthlyClosing.reprocess(frozen, List.of());
        assertThat(again).isEqualTo(frozen);
        assertThatThrownBy(() -> ActiveDebtMonthlyClosing.assertMonthOpen(LocalDate.of(2026, 11, 20), Set.of(YearMonth.of(2026, 11))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("congelado");
        assertThatThrownBy(() -> ActiveDebtMonthlyClosing.close(YearMonth.of(2026, 12), List.of(e1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void samaeCoverageListsDaSliceWithoutInventedSgCodes() {
        assertThat(Samae215DaCoverage.ED31_SLICE).hasSize(10);
        assertThat(Samae215DaCoverage.requirements()).anyMatch(r -> r.contains("POC-36"));
    }
}
