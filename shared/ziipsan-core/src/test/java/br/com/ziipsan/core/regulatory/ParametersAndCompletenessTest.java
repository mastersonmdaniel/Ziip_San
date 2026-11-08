package br.com.ziipsan.core.regulatory;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Parametros declarados (classe d) e completude nomeada. */
class ParametersAndCompletenessTest {

    @Test
    void strictParsingOfDeclaredParameters() {
        assertThat(ContractParameters.declaredContractType("concession"))
                .isEqualTo(RegulatoryContract.ContractType.CONCESSION);
        assertThat(ContractParameters.declaredContractType(null)).isNull();
        assertThat(ContractParameters.declaredContractType("  ")).isNull();
        assertThatThrownBy(() -> ContractParameters.declaredContractType("LEASE"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reg.contract_type");
        assertThat(ContractParameters.declaredRiskMatrixRequired("true")).isTrue();
        assertThat(ContractParameters.declaredRiskMatrixRequired("false")).isFalse();
        assertThat(ContractParameters.declaredRiskMatrixRequired(null)).isNull();
        assertThatThrownBy(() -> ContractParameters.declaredRiskMatrixRequired("yes"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reg.risk_matrix_required");
    }

    @Test
    void completeContractHasNoGaps() {
        RegulatoryContractService s = ContractFixtures.service();
        RegulatoryContract.Contract c = ContractFixtures.concession();
        s.registerContract(c);
        s.addRiskEntry(new ContractRiskMatrix.Entry(UUID.randomUUID(), ContractFixtures.TENANT, c.id(),
                ContractFixtures.KNOWN_RISK, ContractRiskMatrix.Allocation.PROVIDER, "Matriz, item sintetico 1"));
        s.addDocument(new RegulatoryContract.ContractDocument(UUID.randomUUID(), ContractFixtures.TENANT, c.id(),
                RegulatoryContract.DocumentKind.CONTRACT, "Contrato sintetico", "vault://sintetico/contrato",
                LocalDate.of(2020, 1, 2)));
        RegulatoryContractService.Completeness completeness = s.completeness(ContractFixtures.TENANT, c.id());
        assertThat(completeness.complete()).isTrue();
        s.assertComplete(ContractFixtures.TENANT, c.id()); // nao lanca
    }

    @Test
    void undeclaredParametersAndMissingPiecesAreNamedGaps() {
        RegulatoryContractService s = ContractFixtures.service(Map.of());
        RegulatoryContract.Contract bare = new RegulatoryContract.Contract(
                UUID.randomUUID(), ContractFixtures.TENANT, ContractFixtures.MUNICIPALITY, "Municipio Sintetico",
                RegulatoryContract.ServiceKind.WATER_SUPPLY, RegulatoryContract.ContractType.CONCESSION,
                "CONTRATO-SINTETICO-002",
                new RegulatoryContract.Parties("Municipio Sintetico", "Prestador Sintetico SA", null),
                "Lei municipal sintetica 001/2020",
                new RegulatoryContract.Validity(LocalDate.of(2020, 1, 1), null), null, null);
        s.registerContract(bare);
        RegulatoryContractService.Completeness completeness = s.completeness(ContractFixtures.TENANT, bare.id());
        assertThat(completeness.gaps()).containsExactlyInAnyOrder(
                RegulatoryContractService.CompletenessGap.RISK_MATRIX_REQUIREMENT_UNDECLARED,
                RegulatoryContractService.CompletenessGap.REBALANCING_CLAUSE_NOT_DECLARED,
                RegulatoryContractService.CompletenessGap.INDEMNITY_CLAUSE_NOT_DECLARED,
                RegulatoryContractService.CompletenessGap.NO_DOCUMENT_REGISTERED,
                RegulatoryContractService.CompletenessGap.CONTRACT_TYPE_PARAMETER_UNDECLARED);
        assertThatThrownBy(() -> s.assertComplete(ContractFixtures.TENANT, bare.id()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("lacunas");
    }

    @Test
    void requiredButEmptyMatrixAndDivergentTypeAreNamedGaps() {
        RegulatoryContractService s = ContractFixtures.service(Map.of(
                ContractParameters.CONTRACT_TYPE, "PPP",
                ContractParameters.RISK_MATRIX_REQUIRED, "true"));
        RegulatoryContract.Contract c = ContractFixtures.concession(); // CONCESSION no cadastro
        s.registerContract(c);
        s.addDocument(new RegulatoryContract.ContractDocument(UUID.randomUUID(), ContractFixtures.TENANT, c.id(),
                RegulatoryContract.DocumentKind.CONTRACT, "Contrato sintetico", "vault://sintetico/contrato",
                LocalDate.of(2020, 1, 2)));
        assertThat(s.completeness(ContractFixtures.TENANT, c.id()).gaps()).containsExactlyInAnyOrder(
                RegulatoryContractService.CompletenessGap.RISK_MATRIX_REQUIRED_BUT_EMPTY,
                RegulatoryContractService.CompletenessGap.CONTRACT_TYPE_DIVERGES_FROM_PARAMETER);
    }

    @Test
    void matrixNotRequiredDeclaredFalseDoesNotChargeMatrix() {
        RegulatoryContractService s = ContractFixtures.service(Map.of(
                ContractParameters.CONTRACT_TYPE, "CONCESSION",
                ContractParameters.RISK_MATRIX_REQUIRED, "false"));
        RegulatoryContract.Contract c = ContractFixtures.concession();
        s.registerContract(c);
        s.addDocument(new RegulatoryContract.ContractDocument(UUID.randomUUID(), ContractFixtures.TENANT, c.id(),
                RegulatoryContract.DocumentKind.CONTRACT, "Contrato sintetico", "vault://sintetico/contrato",
                LocalDate.of(2020, 1, 2)));
        assertThat(s.completeness(ContractFixtures.TENANT, c.id()).complete()).isTrue();
    }
}
