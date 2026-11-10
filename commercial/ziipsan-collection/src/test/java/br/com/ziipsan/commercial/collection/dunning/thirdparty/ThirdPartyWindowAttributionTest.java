package br.com.ziipsan.commercial.collection.dunning.thirdparty;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Aceite E7/E9: janela, acordo valido da P4.1, remuneracao e idempotencia. */
class ThirdPartyWindowAttributionTest {

    private final ThirdPartyPorts.CandidateDebt debt =
            ThirdPartyFixtures.candidate("01", "1000.00", LocalDate.of(2026, 1, 10));

    private ThirdPartyCollectionService assignedService(Set<UUID> withAgreement) {
        var s = ThirdPartyFixtures.service(List.of(debt), withAgreement);
        var c = ThirdPartyFixtures.readyContract(s, ThirdPartyFixtures.contract(ThirdPartyFixtures.COMPANY, "10"));
        s.assignDebts(ThirdPartyFixtures.TENANT, c.id(), ThirdPartyFixtures.criteria(), LocalDate.of(2026, 3, 1));
        return s;
    }

    private PaymentEvent payment(LocalDate paidOn, String amount) {
        return new PaymentEvent(UUID.randomUUID(), ThirdPartyFixtures.TENANT, debt.debtId(),
                new BigDecimal(amount), paidOn);
    }

    @Test void paymentInsideWindowWithValidAgreementIsAttributed() {
        var s = assignedService(Set.of(debt.debtId()));
        var r = s.attributePayment(payment(LocalDate.of(2026, 3, 15), "1000.00"));
        assertTrue(r.attributed());
        assertEquals("ATTRIBUTED", r.reasonCode());
        assertNotNull(r.remuneration());
    }

    @Test void paymentOnWindowBoundariesIsAttributed() {
        var s1 = assignedService(Set.of(debt.debtId()));
        assertTrue(s1.attributePayment(payment(ThirdPartyFixtures.WINDOW_START, "100.00")).attributed());
        var s2 = assignedService(Set.of(debt.debtId()));
        assertTrue(s2.attributePayment(payment(ThirdPartyFixtures.WINDOW_END, "100.00")).attributed());
    }

    @Test void paymentBeforeWindowIsNotAttributed() {
        var s = assignedService(Set.of(debt.debtId()));
        var r = s.attributePayment(payment(LocalDate.of(2025, 12, 31), "1000.00"));
        assertFalse(r.attributed());
        assertEquals("OUTSIDE_CONTRACT_WINDOW", r.reasonCode());
        assertTrue(s.remunerations(ThirdPartyFixtures.TENANT).isEmpty(), "sem atribuicao, sem remuneracao");
    }

    @Test void paymentAfterWindowIsNotAttributed() {
        var s = assignedService(Set.of(debt.debtId()));
        var r = s.attributePayment(payment(LocalDate.of(2026, 7, 1), "1000.00"));
        assertFalse(r.attributed());
        assertEquals("OUTSIDE_CONTRACT_WINDOW", r.reasonCode());
        assertTrue(s.remunerations(ThirdPartyFixtures.TENANT).isEmpty());
    }

    @Test void paymentInsideWindowWithoutValidAgreementIsNotAttributed() {
        var s = assignedService(Set.of());
        var r = s.attributePayment(payment(LocalDate.of(2026, 3, 15), "1000.00"));
        assertFalse(r.attributed());
        assertEquals("NO_VALID_AGREEMENT_P41", r.reasonCode());
        assertTrue(s.remunerations(ThirdPartyFixtures.TENANT).isEmpty());
        assertEquals(DebtAssignment.Status.ACTIVE, s.assignments(ThirdPartyFixtures.TENANT).get(0).status(),
                "pagamento nao atribuido nao retira a cessao");
    }

    @Test void paymentForDebtNeverAssignedIsNotAttributed() {
        var s = ThirdPartyFixtures.service(List.of(), Set.of());
        var event = new PaymentEvent(UUID.randomUUID(), ThirdPartyFixtures.TENANT, UUID.randomUUID(),
                new BigDecimal("10.00"), LocalDate.of(2026, 3, 15));
        var r = s.attributePayment(event);
        assertFalse(r.attributed());
        assertEquals("DEBT_NOT_ASSIGNED", r.reasonCode());
    }

    @Test void remunerationIsContractPercentTimesAttributedPayment() {
        var s = assignedService(Set.of(debt.debtId()));
        var r = s.attributePayment(payment(LocalDate.of(2026, 3, 15), "1000.00"));
        assertEquals(new BigDecimal("100.00"), r.remuneration().remunerationAmount(), "10% de 1000.00");
        assertEquals(new BigDecimal("10"), r.remuneration().percentApplied());
        assertEquals(new BigDecimal("1000.00"), r.remuneration().paymentAmount());
    }

    @Test void remunerationRoundsHalfUpToCents() {
        // 15% de 333.33 = 49.9995 -> 50.00 (HALF_UP, escala 2 declarada)
        assertEquals(new BigDecimal("50.00"),
                RemunerationEntry.compute(new BigDecimal("15"), new BigDecimal("333.33")));
        // 10% de 0.05 = 0.005 -> 0.01
        assertEquals(new BigDecimal("0.01"),
                RemunerationEntry.compute(new BigDecimal("10"), new BigDecimal("0.05")));
    }

    @Test void samePaymentEventTwiceGeneratesOneRemuneration() {
        var s = assignedService(Set.of(debt.debtId()));
        var event = payment(LocalDate.of(2026, 3, 15), "1000.00");
        var first = s.attributePayment(event);
        var second = s.attributePayment(event);
        assertTrue(first.attributed());
        assertTrue(second.attributed());
        assertEquals("ALREADY_RECORDED_IDEMPOTENT", second.reasonCode());
        assertEquals(first.remuneration().id(), second.remuneration().id());
        assertEquals(1, s.remunerations(ThirdPartyFixtures.TENANT).size(), "um evento, uma so remuneracao");
    }

    @Test void validAgreementPortNotWiredRefusesAttribution() {
        var store = new InMemoryThirdPartyCollectionStore();
        var s = new ThirdPartyCollectionService(store, ThirdPartyFixtures.eligibleDirectory(),
                tenant -> List.of(debt), null);
        var c = ThirdPartyFixtures.readyContract(s, ThirdPartyFixtures.contract(ThirdPartyFixtures.COMPANY, "10"));
        s.assignDebts(ThirdPartyFixtures.TENANT, c.id(), ThirdPartyFixtures.criteria(), LocalDate.of(2026, 3, 1));
        var ex = assertThrows(ThirdPartyCollectionException.class,
                () -> s.attributePayment(payment(LocalDate.of(2026, 3, 15), "1000.00")));
        assertEquals("VALID_AGREEMENT_PORT_NOT_WIRED", ex.code());
    }
}
