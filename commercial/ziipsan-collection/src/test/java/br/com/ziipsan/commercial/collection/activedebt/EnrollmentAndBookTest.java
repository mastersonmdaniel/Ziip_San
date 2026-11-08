package br.com.ziipsan.commercial.collection.activedebt;

import br.com.ziipsan.commercial.collection.dunning.prescription.Prescription;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class EnrollmentAndBookTest {
    private static final UUID TENANT = UUID.randomUUID(), UNIT = UUID.randomUUID(), CUST = UUID.randomUUID();

    private ActiveDebtEnrollment service(LocalDate today) {
        return new ActiveDebtEnrollment(ActiveDebtTestSupport.gateAt(today), new ActiveDebtEnrollment.Book(),
                Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.GENERAL);
    }
    private static ActiveDebtEnrollment.DebtItem item(String amount, LocalDate due) {
        return new ActiveDebtEnrollment.DebtItem(UUID.randomUUID(), due, new BigDecimal(amount), null);
    }

    @Test void individualEnrollmentCreatesSequentialBookTerms() {
        var s = service(LocalDate.of(2026, 11, 8));
        var e1 = s.enrollIndividual(TENANT, UNIT, CUST, List.of(item("500.00", LocalDate.of(2025, 1, 10))), false, Set.of());
        var e2 = s.enrollIndividual(TENANT, UNIT, CUST, List.of(item("700.00", LocalDate.of(2025, 2, 10))), false, Set.of());
        assertThat(e1.bookTermNumber()).isEqualTo(1); assertThat(e2.bookTermNumber()).isEqualTo(2);
        assertThat(e1.total()).isEqualByComparingTo("500.00");
    }

    @Test void individualEnrollmentOfPrescribedDebtIsRefused() {
        var s = service(LocalDate.of(2026, 11, 8));
        assertThatThrownBy(() -> s.enrollIndividual(TENANT, UNIT, CUST,
                List.of(item("500.00", LocalDate.of(2016, 3, 10))), false, Set.of()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ENROLLMENT");
    }

    @Test void periodicEnrollmentEnrollsEligibleAndReportsBlockedWithReason() {
        var s = service(LocalDate.of(2026, 11, 8));
        var ok = item("300.00", LocalDate.of(2025, 5, 1));
        var prescribed = item("900.00", LocalDate.of(2015, 1, 1));
        var r = s.enrollPeriodic(TENANT, List.of(new ActiveDebtEnrollment.EnrollmentRequest(UNIT, CUST, List.of(ok, prescribed))), false, Set.of());
        assertThat(r.enrolled()).hasSize(1);
        assertThat(r.enrolled().get(0).items()).containsExactly(ok);
        assertThat(r.blocked()).hasSize(1);
        assertThat(r.blocked().get(0).item()).isEqualTo(prescribed);
        assertThat(r.blocked().get(0).reason()).contains("PRESCRIBED");
    }

    @Test void enrollmentInFrozenMonthIsRefused() {
        var s = service(LocalDate.of(2026, 11, 8));
        assertThatThrownBy(() -> s.enrollIndividual(TENANT, UNIT, CUST,
                List.of(item("500.00", LocalDate.of(2025, 1, 10))), false, Set.of(YearMonth.of(2026, 11))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("congelado");
    }
}
