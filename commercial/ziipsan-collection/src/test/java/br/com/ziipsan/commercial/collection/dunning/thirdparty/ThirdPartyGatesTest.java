package br.com.ziipsan.commercial.collection.dunning.thirdparty;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Aceite E1/E2/E3 + dupla cessao: gates da cessao e recusas de contrato. */
class ThirdPartyGatesTest {

    private final ThirdPartyPorts.CandidateDebt debt =
            ThirdPartyFixtures.candidate("03", "900.00", LocalDate.of(2026, 1, 10));
    private final LocalDate asOf = LocalDate.of(2026, 3, 1);

    @Test void noOperatorContractRegisteredNoAssignment() {
        var s = ThirdPartyFixtures.service(List.of(debt), Set.of());
        var c = ThirdPartyFixtures.contract(ThirdPartyFixtures.COMPANY, "10");
        s.registerContract(c); // SEM registo de operador
        var ex = assertThrows(ThirdPartyCollectionException.class,
                () -> s.assignDebts(ThirdPartyFixtures.TENANT, c.id(), ThirdPartyFixtures.criteria(), asOf));
        assertEquals("OPERATOR_CONTRACT_NOT_REGISTERED", ex.code());
        assertTrue(s.assignments(ThirdPartyFixtures.TENANT).isEmpty(), "gate falhou: nenhuma cessao criada");
    }

    @Test void operatorContractRegisteredAfterAssignmentDateIsRefused() {
        var s = ThirdPartyFixtures.service(List.of(debt), Set.of());
        var c = ThirdPartyFixtures.contract(ThirdPartyFixtures.COMPANY, "10");
        s.registerContract(c);
        s.registerOperatorContract(new OperatorContract(java.util.UUID.randomUUID(),
                ThirdPartyFixtures.TENANT, c.id(), "OPERADOR-LGPD-FUTURO", asOf.plusDays(1)));
        var ex = assertThrows(ThirdPartyCollectionException.class,
                () -> s.assignDebts(ThirdPartyFixtures.TENANT, c.id(), ThirdPartyFixtures.criteria(), asOf));
        assertEquals("OPERATOR_CONTRACT_REGISTERED_AFTER_ASSIGNMENT", ex.code());
        assertTrue(s.assignments(ThirdPartyFixtures.TENANT).isEmpty(), "gate falhou: nenhuma cessao criada");
    }

    @Test void operatorContractRegisteredOnAssignmentDateIsAccepted() {
        var s = ThirdPartyFixtures.service(List.of(debt), Set.of());
        var c = ThirdPartyFixtures.contract(ThirdPartyFixtures.COMPANY, "10");
        s.registerContract(c);
        s.registerOperatorContract(new OperatorContract(java.util.UUID.randomUUID(),
                ThirdPartyFixtures.TENANT, c.id(), "OPERADOR-LGPD-MESMO-DIA", asOf));
        var created = s.assignDebts(ThirdPartyFixtures.TENANT, c.id(), ThirdPartyFixtures.criteria(), asOf);
        assertEquals(1, created.size(), "registo na propria data da cessao e aceite (anterior ou igual)");
    }

    @Test void operatorContractRegisteredBeforeAssignmentDateIsAccepted() {
        var s = ThirdPartyFixtures.service(List.of(debt), Set.of());
        var c = ThirdPartyFixtures.contract(ThirdPartyFixtures.COMPANY, "10");
        s.registerContract(c);
        s.registerOperatorContract(new OperatorContract(java.util.UUID.randomUUID(),
                ThirdPartyFixtures.TENANT, c.id(), "OPERADOR-LGPD-ANTERIOR", asOf.minusDays(1)));
        var created = s.assignDebts(ThirdPartyFixtures.TENANT, c.id(), ThirdPartyFixtures.criteria(), asOf);
        assertEquals(1, created.size(), "registo anterior a data da cessao e aceite");
    }

    @Test void companyWithoutEd37ProfileGetsNoAssignment() {
        var s = new ThirdPartyCollectionService(new InMemoryThirdPartyCollectionStore(),
                (t, company) -> Optional.of(new ThirdPartyPorts.ContractorDirectoryPort.ContractorStanding(false, true)),
                tenant -> List.of(debt), (t, d) -> true);
        var c = ThirdPartyFixtures.readyContract(s, ThirdPartyFixtures.contract(ThirdPartyFixtures.COMPANY, "10"));
        var ex = assertThrows(ThirdPartyCollectionException.class,
                () -> s.assignDebts(ThirdPartyFixtures.TENANT, c.id(), ThirdPartyFixtures.criteria(), asOf));
        assertEquals("CONTRACTOR_PROFILE_MISSING_ED37", ex.code());
        assertTrue(s.assignments(ThirdPartyFixtures.TENANT).isEmpty());
    }

    @Test void companyWithoutActiveEd37ContractGetsNoAssignment() {
        var s = new ThirdPartyCollectionService(new InMemoryThirdPartyCollectionStore(),
                (t, company) -> Optional.of(new ThirdPartyPorts.ContractorDirectoryPort.ContractorStanding(true, false)),
                tenant -> List.of(debt), (t, d) -> true);
        var c = ThirdPartyFixtures.readyContract(s, ThirdPartyFixtures.contract(ThirdPartyFixtures.COMPANY, "10"));
        var ex = assertThrows(ThirdPartyCollectionException.class,
                () -> s.assignDebts(ThirdPartyFixtures.TENANT, c.id(), ThirdPartyFixtures.criteria(), asOf));
        assertEquals("CONTRACTOR_CONTRACT_INACTIVE_ED37", ex.code());
    }

    @Test void companyUnknownToEd37GetsNoAssignment() {
        var s = new ThirdPartyCollectionService(new InMemoryThirdPartyCollectionStore(),
                (t, company) -> Optional.empty(),
                tenant -> List.of(debt), (t, d) -> true);
        var c = ThirdPartyFixtures.readyContract(s, ThirdPartyFixtures.contract(ThirdPartyFixtures.COMPANY, "10"));
        var ex = assertThrows(ThirdPartyCollectionException.class,
                () -> s.assignDebts(ThirdPartyFixtures.TENANT, c.id(), ThirdPartyFixtures.criteria(), asOf));
        assertEquals("CONTRACTOR_NOT_FOUND_ED37", ex.code());
    }

    @Test void contractorDirectoryPortNotWiredRefusesAssignment() {
        var s = new ThirdPartyCollectionService(new InMemoryThirdPartyCollectionStore(),
                null, tenant -> List.of(debt), (t, d) -> true);
        var c = ThirdPartyFixtures.readyContract(s, ThirdPartyFixtures.contract(ThirdPartyFixtures.COMPANY, "10"));
        var ex = assertThrows(ThirdPartyCollectionException.class,
                () -> s.assignDebts(ThirdPartyFixtures.TENANT, c.id(), ThirdPartyFixtures.criteria(), asOf));
        assertEquals("CONTRACTOR_DIRECTORY_PORT_NOT_WIRED", ex.code());
    }

    @Test void alreadyAssignedDebtIsNotAssignedTwice() {
        var s = ThirdPartyFixtures.service(List.of(debt), Set.of());
        var c1 = ThirdPartyFixtures.readyContract(s, ThirdPartyFixtures.contract(ThirdPartyFixtures.COMPANY, "10"));
        s.assignDebts(ThirdPartyFixtures.TENANT, c1.id(), ThirdPartyFixtures.criteria(), asOf);
        // Segunda cessao da mesma conta (mesmo por outro contrato/empresa):
        var c2 = new ThirdPartyContract(java.util.UUID.randomUUID(), ThirdPartyFixtures.TENANT,
                ThirdPartyFixtures.COMPANY2, "CT-2026-002", ThirdPartyFixtures.WINDOW_START,
                ThirdPartyFixtures.WINDOW_END, new BigDecimal("12"), ThirdPartyContract.Status.ACTIVE);
        ThirdPartyFixtures.readyContract(s, c2);
        var ex = assertThrows(ThirdPartyCollectionException.class,
                () -> s.assignDebts(ThirdPartyFixtures.TENANT, c2.id(), ThirdPartyFixtures.criteria(), asOf));
        assertEquals("DEBT_ALREADY_ASSIGNED", ex.code());
        assertEquals(1, s.assignments(ThirdPartyFixtures.TENANT).size(), "dupla cessao nao criou segunda cessao");
    }

    @Test void closedContractGetsNoAssignment() {
        var s = ThirdPartyFixtures.service(List.of(debt), Set.of());
        var closed = ThirdPartyFixtures.contract(ThirdPartyFixtures.COMPANY, "10")
                .withStatus(ThirdPartyContract.Status.CLOSED);
        ThirdPartyFixtures.readyContract(s, closed);
        var ex = assertThrows(ThirdPartyCollectionException.class,
                () -> s.assignDebts(ThirdPartyFixtures.TENANT, closed.id(), ThirdPartyFixtures.criteria(), asOf));
        assertEquals("CONTRACT_NOT_ACTIVE", ex.code());
    }

    @Test void contractWithoutPercentIsRefusedNoDefaultInvented() {
        var ex = assertThrows(ThirdPartyCollectionException.class, () ->
                new ThirdPartyContract(java.util.UUID.randomUUID(), ThirdPartyFixtures.TENANT,
                        ThirdPartyFixtures.COMPANY, "CT-X", ThirdPartyFixtures.WINDOW_START,
                        ThirdPartyFixtures.WINDOW_END, null, ThirdPartyContract.Status.ACTIVE));
        assertEquals("REMUNERATION_PERCENT_REQUIRED", ex.code());
        var ex2 = assertThrows(ThirdPartyCollectionException.class, () ->
                new ThirdPartyContract(java.util.UUID.randomUUID(), ThirdPartyFixtures.TENANT,
                        ThirdPartyFixtures.COMPANY, "CT-X", ThirdPartyFixtures.WINDOW_START,
                        ThirdPartyFixtures.WINDOW_END, new BigDecimal("150"), ThirdPartyContract.Status.ACTIVE));
        assertEquals("REMUNERATION_PERCENT_OUT_OF_RANGE", ex2.code());
    }

    @Test void contractWithInvertedWindowIsRefused() {
        var ex = assertThrows(ThirdPartyCollectionException.class, () ->
                new ThirdPartyContract(java.util.UUID.randomUUID(), ThirdPartyFixtures.TENANT,
                        ThirdPartyFixtures.COMPANY, "CT-X", ThirdPartyFixtures.WINDOW_END,
                        ThirdPartyFixtures.WINDOW_START, new BigDecimal("10"), ThirdPartyContract.Status.ACTIVE));
        assertEquals("CONTRACT_WINDOW_INVERTED", ex.code());
    }

    @Test void operatorRegistrationRequiresExistingContractAndIsUnique() {
        var s = ThirdPartyFixtures.service(List.of(debt), Set.of());
        var ex = assertThrows(ThirdPartyCollectionException.class,
                () -> s.registerOperatorContract(ThirdPartyFixtures.operator(java.util.UUID.randomUUID())));
        assertEquals("CONTRACT_NOT_FOUND", ex.code());
        var c = ThirdPartyFixtures.readyContract(s, ThirdPartyFixtures.contract(ThirdPartyFixtures.COMPANY, "10"));
        var ex2 = assertThrows(ThirdPartyCollectionException.class,
                () -> s.registerOperatorContract(ThirdPartyFixtures.operator(c.id())));
        assertEquals("OPERATOR_CONTRACT_DUPLICATE", ex2.code());
    }
}
