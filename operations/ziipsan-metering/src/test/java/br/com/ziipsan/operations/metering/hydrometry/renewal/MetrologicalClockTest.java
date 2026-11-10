package br.com.ziipsan.operations.metering.hydrometry.renewal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** Teste §4.2 da estimativa da II-5: o limite exacto do prazo entra conforme a regra declarada. */
class MetrologicalClockTest {

    private MeterRenewalSnapshot withLastVerification(LocalDate last) {
        return RenewalFixtures.snapshot("11", "H-011",
                MeterRenewalSnapshot.MeterParkStatus.INSTALLED,
                LocalDate.of(2019, 3, 1), 0, "TEC_A", last, null);
    }

    @Test @DisplayName("limite exacto: última 2019-03-01 + 7 anos = 2026-03-01; na referência 2026-03-01 está VENCIDO")
    void exactLimitIsOverdue() {
        var a = MetrologicalClock.assess(withLastVerification(LocalDate.of(2019, 3, 1)),
                LocalDate.of(2026, 3, 1), 7, 90);
        assertEquals(LocalDate.of(2026, 3, 1), a.nextVerificationOn());
        assertEquals(MetrologicalClock.ClockStatus.OVERDUE, a.status());
        assertEquals(0L, a.daysUntilDue());
    }

    @Test @DisplayName("fronteira do horizonte inclusiva: próxima = referência + horizonte -> A VENCER")
    void horizonEdgeIsDueWithin() {
        // referência 2026-01-30 + 30 dias = 2026-03-01 = próxima verificação.
        var a = MetrologicalClock.assess(withLastVerification(LocalDate.of(2019, 3, 1)),
                LocalDate.of(2026, 1, 30), 7, 30);
        assertEquals(MetrologicalClock.ClockStatus.DUE_WITHIN_HORIZON, a.status());
        assertEquals(30L, a.daysUntilDue());
    }

    @Test @DisplayName("um dia além do horizonte -> EM DIA")
    void beyondHorizonIsCurrent() {
        var a = MetrologicalClock.assess(withLastVerification(LocalDate.of(2019, 3, 1)),
                LocalDate.of(2026, 1, 30), 7, 29);
        assertEquals(MetrologicalClock.ClockStatus.CURRENT, a.status());
    }

    @Test @DisplayName("vencido há um ano: dias negativos contam o atraso (2025-01-01 -> 2026-01-01 = -365)")
    void overdueDaysAreNegative() {
        var snap = RenewalFixtures.snapshot("12", "H-012",
                MeterRenewalSnapshot.MeterParkStatus.INSTALLED,
                LocalDate.of(2018, 1, 1), 0, "TEC_A", LocalDate.of(2018, 1, 1), null);
        var a = MetrologicalClock.assess(snap, LocalDate.of(2026, 1, 1), 7, 90);
        assertEquals(LocalDate.of(2025, 1, 1), a.nextVerificationOn());
        assertEquals(MetrologicalClock.ClockStatus.OVERDUE, a.status());
        assertEquals(-365L, a.daysUntilDue());
    }

    @Test @DisplayName("sem verificação registada: UNKNOWN, sem data inventada")
    void noVerificationIsUnknown() {
        var a = MetrologicalClock.assess(withLastVerification(null),
                LocalDate.of(2026, 1, 1), 7, 90);
        assertEquals(MetrologicalClock.ClockStatus.UNKNOWN_NO_VERIFICATION, a.status());
        assertNull(a.nextVerificationOn());
        assertNull(a.daysUntilDue());
    }

    @Test @DisplayName("intervalo acima de 7 é recusado pela regra reutilizada da ED-14 (só reduz), mesmo sem verificação")
    void intervalAboveSevenRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                MetrologicalClock.assess(withLastVerification(LocalDate.of(2019, 3, 1)),
                        LocalDate.of(2026, 1, 1), 8, 90));
        assertThrows(IllegalArgumentException.class, () ->
                MetrologicalClock.assess(withLastVerification(null),
                        LocalDate.of(2026, 1, 1), 8, 90));
    }

    @Test @DisplayName("intervalo menor é aceite (só reduz): última 2021-01-01 + 5 = 2026-01-01 -> vencido na referência")
    void shorterIntervalApplies() {
        var snap = RenewalFixtures.snapshot("13", "H-013",
                MeterRenewalSnapshot.MeterParkStatus.INSTALLED,
                LocalDate.of(2021, 1, 1), 0, "TEC_A", LocalDate.of(2021, 1, 1), null);
        var a = MetrologicalClock.assess(snap, LocalDate.of(2026, 1, 1), 5, 90);
        assertEquals(LocalDate.of(2026, 1, 1), a.nextVerificationOn());
        assertEquals(MetrologicalClock.ClockStatus.OVERDUE, a.status());
    }
}
