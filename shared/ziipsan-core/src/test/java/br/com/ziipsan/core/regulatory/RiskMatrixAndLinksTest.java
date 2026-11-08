package br.com.ziipsan.core.regulatory;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Matriz (NR 5/ANA) sobre a P3.31 e vinculo com reversiveis da ED-56. */
class RiskMatrixAndLinksTest {

    private ContractRiskMatrix.Entry entry(UUID contractId, UUID riskId) {
        return new ContractRiskMatrix.Entry(UUID.randomUUID(), ContractFixtures.TENANT, contractId, riskId,
                ContractRiskMatrix.Allocation.SHARED, "Matriz, item sintetico 1");
    }

    @Test
    void matrixEntryReferencingP331RiskIsAccepted() {
        RegulatoryContractService s = ContractFixtures.service();
        RegulatoryContract.Contract c = ContractFixtures.concession();
        s.registerContract(c);
        s.addRiskEntry(entry(c.id(), ContractFixtures.KNOWN_RISK));
        ContractRiskMatrix.Matrix matrix = s.riskMatrix(ContractFixtures.TENANT, c.id());
        assertThat(matrix.isEmpty()).isFalse();
        assertThat(matrix.entries()).hasSize(1);
        assertThat(matrix.entries().get(0).allocation()).isEqualTo(ContractRiskMatrix.Allocation.SHARED);
    }

    @Test
    void matrixEntryWithUnknownRiskIsRefused() {
        RegulatoryContractService s = ContractFixtures.service();
        RegulatoryContract.Contract c = ContractFixtures.concession();
        s.registerContract(c);
        assertThatThrownBy(() -> s.addRiskEntry(entry(c.id(), ContractFixtures.UNKNOWN_RISK)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("P3.31");
        assertThat(s.riskMatrix(ContractFixtures.TENANT, c.id()).isEmpty()).isTrue();
    }

    @Test
    void duplicateRiskInMatrixIsRefused() {
        RegulatoryContractService s = ContractFixtures.service();
        RegulatoryContract.Contract c = ContractFixtures.concession();
        s.registerContract(c);
        s.addRiskEntry(entry(c.id(), ContractFixtures.KNOWN_RISK));
        assertThatThrownBy(() -> s.addRiskEntry(entry(c.id(), ContractFixtures.KNOWN_RISK)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ja esta na matriz");
    }

    @Test
    void reversibleAssetLinkAcceptsOnlyExistingReversibleAssets() {
        RegulatoryContractService s = ContractFixtures.service();
        RegulatoryContract.Contract c = ContractFixtures.concession();
        s.registerContract(c);
        s.linkReversibleAsset(ContractFixtures.TENANT, c.id(), ContractFixtures.REVERSIBLE_ASSET);
        assertThatThrownBy(() -> s.linkReversibleAsset(ContractFixtures.TENANT, c.id(), ContractFixtures.NON_REVERSIBLE_ASSET))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nao e bem reversivel");
        assertThatThrownBy(() -> s.linkReversibleAsset(ContractFixtures.TENANT, c.id(), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("inexistente no inventario");
        assertThatThrownBy(() -> s.linkReversibleAsset(ContractFixtures.TENANT, c.id(), ContractFixtures.REVERSIBLE_ASSET))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ja vinculado");
    }

    @Test
    void operationsOnContractOfAnotherTenantAreRefused() {
        RegulatoryContractService s = ContractFixtures.service();
        RegulatoryContract.Contract c = ContractFixtures.concession();
        s.registerContract(c);
        assertThatThrownBy(() -> s.addDocument(new RegulatoryContract.ContractDocument(
                UUID.randomUUID(), ContractFixtures.OTHER_TENANT, c.id(), RegulatoryContract.DocumentKind.CONTRACT,
                "Doc", "vault://sintetico", LocalDate.of(2025, 1, 1))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("inexistente neste tenant");
    }

    @Test
    void emptyParametersStillAllowRegistration() {
        RegulatoryContractService s = ContractFixtures.service(Map.of());
        assertThat(s.registerContract(ContractFixtures.concession())).isNotNull();
    }
}
