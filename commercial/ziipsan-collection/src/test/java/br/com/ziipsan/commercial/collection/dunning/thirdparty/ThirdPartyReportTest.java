package br.com.ziipsan.commercial.collection.dunning.thirdparty;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Aceite E10: relatorio de pagamentos e remuneracao por empresa e periodo. */
class ThirdPartyReportTest {

    private final ThirdPartyPorts.CandidateDebt d1 =
            ThirdPartyFixtures.candidate("31", "1000.00", LocalDate.of(2026, 1, 10));
    private final ThirdPartyPorts.CandidateDebt d2 =
            ThirdPartyFixtures.candidate("32", "500.00", LocalDate.of(2026, 1, 10));

    private ThirdPartyCollectionService scenario() {
        var s = ThirdPartyFixtures.service(List.of(d1, d2), Set.of(d1.debtId(), d2.debtId()));
        var c = ThirdPartyFixtures.readyContract(s, ThirdPartyFixtures.contract(ThirdPartyFixtures.COMPANY, "10"));
        s.assignDebts(ThirdPartyFixtures.TENANT, c.id(), ThirdPartyFixtures.criteria(), LocalDate.of(2026, 3, 1));
        // Dois pagamentos atribuidos em meses diferentes (10%):
        s.attributePayment(new PaymentEvent(UUID.randomUUID(), ThirdPartyFixtures.TENANT, d1.debtId(),
                new BigDecimal("1000.00"), LocalDate.of(2026, 3, 15))); // remuneracao 100.00
        s.attributePayment(new PaymentEvent(UUID.randomUUID(), ThirdPartyFixtures.TENANT, d2.debtId(),
                new BigDecimal("500.00"), LocalDate.of(2026, 5, 10)));  // remuneracao 50.00
        return s;
    }

    @Test void reportAggregatesPaymentsAndRemunerationForCompany() {
        var s = scenario();
        var r = s.report(ThirdPartyFixtures.TENANT, ThirdPartyFixtures.COMPANY,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
        assertEquals(2, r.attributedPaymentCount());
        assertEquals(new BigDecimal("1500.00"), r.totalPayments());
        assertEquals(new BigDecimal("150.00"), r.totalRemuneration());
    }

    @Test void reportFiltersByPeriod() {
        var s = scenario();
        var march = s.report(ThirdPartyFixtures.TENANT, ThirdPartyFixtures.COMPANY,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
        assertEquals(1, march.attributedPaymentCount());
        assertEquals(new BigDecimal("1000.00"), march.totalPayments());
        assertEquals(new BigDecimal("100.00"), march.totalRemuneration());
    }

    @Test void reportForOtherCompanyIsZeroNotError() {
        var s = scenario();
        var r = s.report(ThirdPartyFixtures.TENANT, ThirdPartyFixtures.COMPANY2,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
        assertEquals(0, r.attributedPaymentCount());
        assertEquals(0, r.totalPayments().signum());
        assertEquals(0, r.totalRemuneration().signum());
    }

    @Test void reportCountsOnlyAttributedPayments() {
        var s = ThirdPartyFixtures.service(List.of(d1), Set.of()); // sem acordo valido
        var c = ThirdPartyFixtures.readyContract(s, ThirdPartyFixtures.contract(ThirdPartyFixtures.COMPANY, "10"));
        s.assignDebts(ThirdPartyFixtures.TENANT, c.id(), ThirdPartyFixtures.criteria(), LocalDate.of(2026, 3, 1));
        s.attributePayment(new PaymentEvent(UUID.randomUUID(), ThirdPartyFixtures.TENANT, d1.debtId(),
                new BigDecimal("1000.00"), LocalDate.of(2026, 3, 15)));
        var r = s.report(ThirdPartyFixtures.TENANT, ThirdPartyFixtures.COMPANY,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
        assertEquals(0, r.attributedPaymentCount(), "pagamento nao atribuido nao entra no relatorio");
    }
}
