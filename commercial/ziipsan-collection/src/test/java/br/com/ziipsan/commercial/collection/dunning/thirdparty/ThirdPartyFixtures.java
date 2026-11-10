package br.com.ziipsan.commercial.collection.dunning.thirdparty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Fixtures partilhadas dos testes da ED-68 (valores de TESTE, declarados aqui). */
final class ThirdPartyFixtures {

    static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID COMPANY = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final UUID COMPANY2 = UUID.fromString("33333333-3333-3333-3333-333333333333");
    static final LocalDate WINDOW_START = LocalDate.of(2026, 1, 1);
    static final LocalDate WINDOW_END = LocalDate.of(2026, 6, 30);

    static ThirdPartyContract contract(UUID company, String percent) {
        return new ThirdPartyContract(UUID.randomUUID(), TENANT, company, "CT-2026-001",
                WINDOW_START, WINDOW_END, new BigDecimal(percent), ThirdPartyContract.Status.ACTIVE);
    }

    static OperatorContract operator(UUID contractId) {
        return new OperatorContract(UUID.randomUUID(), TENANT, contractId,
                "OPERADOR-LGPD-2026-001", LocalDate.of(2025, 12, 20));
    }

    static ThirdPartyPorts.CandidateDebt candidate(String suffix, String amount, LocalDate due) {
        return new ThirdPartyPorts.CandidateDebt(
                UUID.fromString("00000000-0000-0000-0000-0000000000" + suffix),
                UUID.fromString("10000000-0000-0000-0000-0000000000" + suffix),
                new BigDecimal(amount), due, "DEV-" + suffix);
    }

    static ThirdPartyPorts.ContractorDirectoryPort eligibleDirectory() {
        return (tenant, company) -> Optional.of(
                new ThirdPartyPorts.ContractorDirectoryPort.ContractorStanding(true, true));
    }

    /** Servico completo e elegivel, com as candidatas dadas e acordo valido configuravel. */
    static ThirdPartyCollectionService service(List<ThirdPartyPorts.CandidateDebt> candidates,
                                               java.util.Set<UUID> debtsWithValidAgreement) {
        return new ThirdPartyCollectionService(new InMemoryThirdPartyCollectionStore(),
                eligibleDirectory(),
                tenant -> candidates,
                (tenant, debt) -> debtsWithValidAgreement.contains(debt));
    }

    /** Contrato registado + operador registado; devolve o contrato. */
    static ThirdPartyContract readyContract(ThirdPartyCollectionService s, ThirdPartyContract c) {
        s.registerContract(c);
        s.registerOperatorContract(operator(c.id()));
        return c;
    }

    static AssignmentCriteria criteria() {
        return new AssignmentCriteria(30, new BigDecimal("100.00"), null, null, null);
    }
}
