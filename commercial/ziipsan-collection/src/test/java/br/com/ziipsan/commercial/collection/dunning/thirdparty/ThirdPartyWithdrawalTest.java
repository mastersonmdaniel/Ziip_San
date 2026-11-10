package br.com.ziipsan.commercial.collection.dunning.thirdparty;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Aceite E8: retirada pelas tres causas; retirada e terminal. */
class ThirdPartyWithdrawalTest {

    private final ThirdPartyPorts.CandidateDebt debt =
            ThirdPartyFixtures.candidate("02", "800.00", LocalDate.of(2026, 1, 10));

    private ThirdPartyCollectionService assignedService() {
        var s = ThirdPartyFixtures.service(List.of(debt), Set.of(debt.debtId()));
        var c = ThirdPartyFixtures.readyContract(s, ThirdPartyFixtures.contract(ThirdPartyFixtures.COMPANY, "10"));
        s.assignDebts(ThirdPartyFixtures.TENANT, c.id(), ThirdPartyFixtures.criteria(), LocalDate.of(2026, 3, 1));
        return s;
    }

    @Test void withdrawalByAttributedPayment() {
        var s = assignedService();
        s.attributePayment(new PaymentEvent(UUID.randomUUID(), ThirdPartyFixtures.TENANT, debt.debtId(),
                new BigDecimal("800.00"), LocalDate.of(2026, 3, 15)));
        var a = s.assignments(ThirdPartyFixtures.TENANT).get(0);
        assertEquals(DebtAssignment.Status.WITHDRAWN, a.status());
        assertEquals(DebtAssignment.WithdrawalCause.PAYMENT, a.withdrawalCause());
        assertEquals(LocalDate.of(2026, 3, 15), a.withdrawnOn());
    }

    @Test void withdrawalByInstallmentAgreementViaReturn() {
        var s = assignedService();
        var w = s.processReturn(ThirdPartyFixtures.TENANT, new RemittanceAndReturn.ReturnOccurrence(
                debt.debtId(), RemittanceAndReturn.ReturnOccurrence.Kind.INSTALLMENT_AGREEMENT,
                LocalDate.of(2026, 4, 2)));
        assertEquals(DebtAssignment.Status.WITHDRAWN, w.status());
        assertEquals(DebtAssignment.WithdrawalCause.INSTALLMENT_AGREEMENT, w.withdrawalCause());
        assertEquals(LocalDate.of(2026, 4, 2), w.withdrawnOn());
    }

    @Test void withdrawalByPaymentViaReturn() {
        var s = assignedService();
        var w = s.processReturn(ThirdPartyFixtures.TENANT, new RemittanceAndReturn.ReturnOccurrence(
                debt.debtId(), RemittanceAndReturn.ReturnOccurrence.Kind.PAYMENT, LocalDate.of(2026, 4, 2)));
        assertEquals(DebtAssignment.Status.WITHDRAWN, w.status());
        assertEquals(DebtAssignment.WithdrawalCause.PAYMENT, w.withdrawalCause());
    }

    @Test void withdrawalByWindowEndSweep() {
        var s = assignedService();
        int closed = s.closeExpiredWindows(ThirdPartyFixtures.TENANT, LocalDate.of(2026, 7, 1));
        assertEquals(1, closed);
        var a = s.assignments(ThirdPartyFixtures.TENANT).get(0);
        assertEquals(DebtAssignment.Status.WITHDRAWN, a.status());
        assertEquals(DebtAssignment.WithdrawalCause.WINDOW_END, a.withdrawalCause());
        assertEquals(LocalDate.of(2026, 7, 1), a.withdrawnOn());
    }

    @Test void sweepBeforeWindowEndWithdrawsNothing() {
        var s = assignedService();
        assertEquals(0, s.closeExpiredWindows(ThirdPartyFixtures.TENANT, LocalDate.of(2026, 6, 30)));
        assertEquals(0, s.closeExpiredWindows(ThirdPartyFixtures.TENANT, ThirdPartyFixtures.WINDOW_END));
        assertEquals(DebtAssignment.Status.ACTIVE, s.assignments(ThirdPartyFixtures.TENANT).get(0).status());
    }

    @Test void withdrawalIsTerminal() {
        var s = assignedService();
        s.processReturn(ThirdPartyFixtures.TENANT, new RemittanceAndReturn.ReturnOccurrence(
                debt.debtId(), RemittanceAndReturn.ReturnOccurrence.Kind.PAYMENT, LocalDate.of(2026, 4, 2)));
        var ex = assertThrows(ThirdPartyCollectionException.class, () ->
                s.processReturn(ThirdPartyFixtures.TENANT, new RemittanceAndReturn.ReturnOccurrence(
                        debt.debtId(), RemittanceAndReturn.ReturnOccurrence.Kind.INSTALLMENT_AGREEMENT,
                        LocalDate.of(2026, 4, 3))));
        assertEquals("RETURN_FOR_WITHDRAWN_ASSIGNMENT", ex.code());
        // Nem o record permite segunda retirada:
        var a = s.assignments(ThirdPartyFixtures.TENANT).get(0);
        var ex2 = assertThrows(ThirdPartyCollectionException.class,
                () -> a.withdrawn(DebtAssignment.WithdrawalCause.WINDOW_END, LocalDate.of(2026, 7, 1)));
        assertEquals("ASSIGNMENT_ALREADY_WITHDRAWN", ex2.code());
    }

    @Test void withdrawnAssignmentRequiresCauseAndDate() {
        var ex = assertThrows(ThirdPartyCollectionException.class, () ->
                new DebtAssignment(UUID.randomUUID(), ThirdPartyFixtures.TENANT, UUID.randomUUID(),
                        debt.toMinimalData(), DebtAssignment.Status.WITHDRAWN, null, null));
        assertEquals("ASSIGNMENT_WITHDRAWAL_INCOMPLETE", ex.code());
    }
}
