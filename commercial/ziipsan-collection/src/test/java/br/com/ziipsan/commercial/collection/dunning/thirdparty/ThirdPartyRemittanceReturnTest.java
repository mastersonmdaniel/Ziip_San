package br.com.ziipsan.commercial.collection.dunning.thirdparty;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Aceite E6: ida-e-volta — remessa das cessoes activas, retorno processado. */
class ThirdPartyRemittanceReturnTest {

    private final ThirdPartyPorts.CandidateDebt d1 =
            ThirdPartyFixtures.candidate("21", "1000.00", LocalDate.of(2026, 1, 10));
    private final ThirdPartyPorts.CandidateDebt d2 =
            ThirdPartyFixtures.candidate("22", "2000.00", LocalDate.of(2026, 1, 5));

    private ThirdPartyCollectionService assignedService(ThirdPartyContract[] contractOut) {
        var s = ThirdPartyFixtures.service(List.of(d2, d1), Set.of());
        var c = ThirdPartyFixtures.readyContract(s, ThirdPartyFixtures.contract(ThirdPartyFixtures.COMPANY, "10"));
        contractOut[0] = c;
        s.assignDebts(ThirdPartyFixtures.TENANT, c.id(), ThirdPartyFixtures.criteria(), LocalDate.of(2026, 3, 1));
        return s;
    }

    @Test void remittanceContainsExactlyActiveAssignmentsWithWhitelistLines() {
        var holder = new ThirdPartyContract[1];
        var s = assignedService(holder);
        var remittance = s.buildRemittance(ThirdPartyFixtures.TENANT, holder[0].id(), LocalDate.of(2026, 3, 2));
        assertEquals(2, remittance.entries().size());
        for (MinimalDebtorData e : remittance.entries()) {
            String line = RemittanceAndReturn.Remittance.lineOf(e);
            assertEquals(5, line.split("\\|").length, "linha da remessa so tem os 5 campos da whitelist");
            assertTrue(remittance.content().contains(line));
        }
        // Determinismo: mesma entrada, mesmo conteudo, linhas ordenadas por debtId.
        var again = s.buildRemittance(ThirdPartyFixtures.TENANT, holder[0].id(), LocalDate.of(2026, 3, 2));
        assertEquals(remittance.content(), again.content());
        assertTrue(remittance.content().indexOf(d1.debtId().toString())
                < remittance.content().indexOf(d2.debtId().toString()), "ordenado por debtId");
    }

    @Test void roundTripReturnWithdrawsTheRightAssignmentOnly() {
        var holder = new ThirdPartyContract[1];
        var s = assignedService(holder);
        var remittance = s.buildRemittance(ThirdPartyFixtures.TENANT, holder[0].id(), LocalDate.of(2026, 3, 2));
        assertEquals(2, remittance.entries().size());
        var w = s.processReturn(ThirdPartyFixtures.TENANT, new RemittanceAndReturn.ReturnOccurrence(
                d1.debtId(), RemittanceAndReturn.ReturnOccurrence.Kind.INSTALLMENT_AGREEMENT,
                LocalDate.of(2026, 3, 20)));
        assertEquals(DebtAssignment.WithdrawalCause.INSTALLMENT_AGREEMENT, w.withdrawalCause());
        // A outra cessao continua activa e sai na remessa seguinte; a retirada nao.
        var next = s.buildRemittance(ThirdPartyFixtures.TENANT, holder[0].id(), LocalDate.of(2026, 3, 21));
        assertEquals(List.of(d2.toMinimalData()), next.entries());
    }

    @Test void returnNoProgressKeepsAssignmentActive() {
        var holder = new ThirdPartyContract[1];
        var s = assignedService(holder);
        var a = s.processReturn(ThirdPartyFixtures.TENANT, new RemittanceAndReturn.ReturnOccurrence(
                d1.debtId(), RemittanceAndReturn.ReturnOccurrence.Kind.NO_PROGRESS, LocalDate.of(2026, 3, 20)));
        assertEquals(DebtAssignment.Status.ACTIVE, a.status());
    }

    @Test void debtorIdentifierContainingSeparatorIsRefusedAtMinimalDataConstruction() {
        // R-ED68-03: '|' e o separador da linha de remessa (5 campos); um
        // identificador com '|' e recusado na construcao do payload minimo,
        // ponto unico por onde toda a linha de remessa nasce.
        var ex = assertThrows(ThirdPartyCollectionException.class, () ->
                new MinimalDebtorData(d1.debtId(), d1.unitId(), d1.amount(), d1.dueDate(), "DEV|21"));
        assertEquals("MINIMAL_DATA_DEBTOR_ID_CONTAINS_SEPARATOR", ex.code());
        // O mesmo vale no caminho real da cessao: candidata -> payload minimo.
        var candidate = new ThirdPartyPorts.CandidateDebt(d1.debtId(), d1.unitId(),
                d1.amount(), d1.dueDate(), "DEV|21");
        var ex2 = assertThrows(ThirdPartyCollectionException.class, candidate::toMinimalData);
        assertEquals("MINIMAL_DATA_DEBTOR_ID_CONTAINS_SEPARATOR", ex2.code());
    }

    @Test void returnForUnknownDebtIsRefused() {
        var holder = new ThirdPartyContract[1];
        var s = assignedService(holder);
        var ex = assertThrows(ThirdPartyCollectionException.class, () ->
                s.processReturn(ThirdPartyFixtures.TENANT, new RemittanceAndReturn.ReturnOccurrence(
                        UUID.randomUUID(), RemittanceAndReturn.ReturnOccurrence.Kind.PAYMENT,
                        LocalDate.of(2026, 3, 20))));
        assertEquals("RETURN_FOR_UNKNOWN_ASSIGNMENT", ex.code());
    }
}
