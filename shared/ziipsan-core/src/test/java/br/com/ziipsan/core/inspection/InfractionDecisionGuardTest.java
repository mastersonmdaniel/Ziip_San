package br.com.ziipsan.core.inspection;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Correcao D1 da revisao cruzada da ED-24: guarda CONSERVADORA
 * (fail-closed) [A CONFIRMAR NA INTEGRACAO] do prazo de defesa em
 * {@code InfractionLifecycle.decide} — sem recurso interposto, decidir
 * antes de decorrido {@code infraction.appeal_days} da emissao e
 * recusado; apos o prazo, permitido; com recurso, a decisao e a do
 * recurso e o fluxo original nao muda.
 */
class InfractionDecisionGuardTest {

    private static final LocalDateTime ISSUED = LocalDateTime.of(2026, 11, 9, 10, 0);
    private static final UUID JUDGE = UUID.fromString("00000000-0000-0000-0000-0000000000e9");
    private static final BigDecimal AMOUNT = new BigDecimal("500.00");
    /** appealDays = 30 sobre 2026-11-09 => appealDeadline = 2026-12-09. */
    private static final LocalDateTime DEADLINE_DAY = LocalDateTime.of(2026, 12, 9, 15, 0);
    private static final LocalDateTime AFTER_DEADLINE = LocalDateTime.of(2026, 12, 10, 9, 0);

    private static InfractionLifecycle.Case issuedNotice(InspectionParameters.ChargeTiming timing) {
        return InfractionLifecycle.issue(FraudRecordTest.fraud(FraudRecord.FraudStatus.CONFIRMED),
                AMOUNT, ISSUED, 30, timing).newCase();
    }

    @Test
    void earlyDecisionWithoutAppealIsRefused() {
        InfractionLifecycle.Case notice = issuedNotice(InspectionParameters.ChargeTiming.AFTER_DECISION);
        // Dia seguinte a emissao: manter e anular sao recusados.
        assertThrows(IllegalStateException.class,
                () -> InfractionLifecycle.decide(notice, true, JUDGE, ISSUED.plusDays(1)));
        assertThrows(IllegalStateException.class,
                () -> InfractionLifecycle.decide(notice, false, JUDGE, ISSUED.plusDays(1)));
        // No proprio dia do prazo a defesa ainda esta aberta: tambem recusado.
        assertThrows(IllegalStateException.class,
                () -> InfractionLifecycle.decide(notice, true, JUDGE, DEADLINE_DAY));
        // A recusa nao decide o auto.
        assertFalse(notice.decided());
    }

    @Test
    void earlyDecisionWithoutAppealIsRefusedOnIssueTimingToo() {
        InfractionLifecycle.Case notice = issuedNotice(
                InspectionParameters.ChargeTiming.ON_ISSUE_SUSPENDED_BY_APPEAL);
        assertEquals(InfractionLifecycle.DebitState.LAUNCHED, notice.debitState());
        assertThrows(IllegalStateException.class,
                () -> InfractionLifecycle.decide(notice, true, JUDGE, ISSUED.plusDays(2)));
    }

    @Test
    void decisionWithoutAppealAfterDeadlineIsAllowed() {
        InfractionLifecycle.Case notice = issuedNotice(InspectionParameters.ChargeTiming.AFTER_DECISION);
        InfractionLifecycle.Transition upheld =
                InfractionLifecycle.decide(notice, true, JUDGE, AFTER_DEADLINE);
        assertEquals(InfractionLifecycle.NoticeStatus.DECIDED_UPHELD, upheld.newCase().status());
        assertEquals(InfractionLifecycle.DebitState.LAUNCHED, upheld.newCase().debitState());
        assertEquals(java.util.List.of(InfractionLifecycle.Effect.LAUNCH_DEBIT), upheld.effects());

        InfractionLifecycle.Transition annulled =
                InfractionLifecycle.decide(notice, false, JUDGE, AFTER_DEADLINE);
        assertEquals(InfractionLifecycle.NoticeStatus.DECIDED_ANNULLED, annulled.newCase().status());
        assertEquals(InfractionLifecycle.DebitState.NEVER_LAUNCHED_ANNULLED, annulled.newCase().debitState());
    }

    @Test
    void appealDecisionFlowIsUnchangedByTheGuard() {
        InfractionLifecycle.Case notice = issuedNotice(InspectionParameters.ChargeTiming.AFTER_DECISION);
        InfractionLifecycle.Case appealed =
                InfractionLifecycle.appeal(notice, ISSUED.plusDays(3)).newCase();
        // Decisao do recurso ANTES do fim do prazo de defesa: fluxo original, permitida.
        InfractionLifecycle.Transition decided =
                InfractionLifecycle.decide(appealed, true, JUDGE, ISSUED.plusDays(10));
        assertEquals(InfractionLifecycle.NoticeStatus.DECIDED_UPHELD, decided.newCase().status());
        assertEquals(InfractionLifecycle.DebitState.LAUNCHED, decided.newCase().debitState());
    }
}
