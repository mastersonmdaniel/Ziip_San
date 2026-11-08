package br.com.ziipsan.core.regulatory;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cadastro por municipio e servico, vigencia e campos obrigatorios do aceite. */
class RegulatoryContractTest {

    @Test
    void registersContractByMunicipalityAndService() {
        RegulatoryContractService s = ContractFixtures.service();
        RegulatoryContract.Contract c = ContractFixtures.concession();
        assertThat(s.registerContract(c)).isEqualTo(c);
        assertThat(c.underContract()).isTrue();
        assertThat(c.validity().covers(LocalDate.of(2030, 6, 1))).isTrue();
        assertThat(c.validity().covers(LocalDate.of(2019, 6, 1))).isFalse();
    }

    @Test
    void overlappingValidityForSameMunicipalityAndServiceIsRefused() {
        RegulatoryContractService s = ContractFixtures.service();
        s.registerContract(ContractFixtures.concession());
        RegulatoryContract.Contract overlapping = ContractFixtures.contract(
                RegulatoryContract.ContractType.PPP, RegulatoryContract.ServiceKind.WATER_SUPPLY,
                LocalDate.of(2040, 1, 1), LocalDate.of(2060, 1, 1));
        assertThatThrownBy(() -> s.registerContract(overlapping))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sobreposta");
    }

    @Test
    void sameMunicipalityDifferentServiceIsAnotherRecord() {
        RegulatoryContractService s = ContractFixtures.service();
        s.registerContract(ContractFixtures.concession());
        RegulatoryContract.Contract sewage = ContractFixtures.contract(
                RegulatoryContract.ContractType.CONCESSION, RegulatoryContract.ServiceKind.SEWAGE,
                LocalDate.of(2020, 1, 1), LocalDate.of(2050, 1, 1));
        assertThat(s.registerContract(sewage)).isEqualTo(sewage);
    }

    @Test
    void successiveValidityForSameRecorteIsAccepted() {
        RegulatoryContractService s = ContractFixtures.service();
        s.registerContract(ContractFixtures.concession());
        RegulatoryContract.Contract next = ContractFixtures.contract(
                RegulatoryContract.ContractType.CONCESSION, RegulatoryContract.ServiceKind.WATER_SUPPLY,
                LocalDate.of(2050, 1, 2), LocalDate.of(2080, 1, 1));
        assertThat(s.registerContract(next)).isEqualTo(next);
    }

    @Test
    void invalidMunicipalityCodeAndInvertedValidityAreRefused() {
        assertThatThrownBy(() -> new RegulatoryContract.Contract(
                java.util.UUID.randomUUID(), ContractFixtures.TENANT, "123", "X",
                RegulatoryContract.ServiceKind.WATER_SUPPLY, RegulatoryContract.ContractType.CONCESSION, "R",
                new RegulatoryContract.Parties("A", "B", null), "Lei",
                new RegulatoryContract.Validity(LocalDate.of(2020, 1, 1), null), null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("IBGE");
        assertThatThrownBy(() -> new RegulatoryContract.Validity(
                LocalDate.of(2030, 1, 1), LocalDate.of(2020, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("posterior");
    }

    @Test
    void directProvisionIsNotUnderContract() {
        RegulatoryContract.Contract direct = ContractFixtures.contract(
                RegulatoryContract.ContractType.DIRECT_PROVISION, RegulatoryContract.ServiceKind.WATER_SUPPLY,
                LocalDate.of(2020, 1, 1), null);
        assertThat(direct.underContract()).isFalse();
    }

    @Test
    void amendmentBeforeValidityAndDuplicateRefAreRefused() {
        RegulatoryContractService s = ContractFixtures.service();
        RegulatoryContract.Contract c = ContractFixtures.concession();
        s.registerContract(c);
        RegulatoryContract.ContractAmendment early = new RegulatoryContract.ContractAmendment(
                java.util.UUID.randomUUID(), ContractFixtures.TENANT, c.id(), "ADT-1",
                LocalDate.of(2019, 1, 1), RegulatoryContract.AmendmentKind.GENERAL, "Aditivo sintetico", null);
        assertThatThrownBy(() -> s.addAmendment(early))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("antes do inicio");
        RegulatoryContract.ContractAmendment ok = new RegulatoryContract.ContractAmendment(
                java.util.UUID.randomUUID(), ContractFixtures.TENANT, c.id(), "ADT-1",
                LocalDate.of(2025, 1, 1), RegulatoryContract.AmendmentKind.GOAL_UPDATE_NR8,
                "Aditivo de metas sintetico (NR 8/ANA)", null);
        s.addAmendment(ok);
        assertThatThrownBy(() -> s.addAmendment(new RegulatoryContract.ContractAmendment(
                java.util.UUID.randomUUID(), ContractFixtures.TENANT, c.id(), "ADT-1",
                LocalDate.of(2026, 1, 1), RegulatoryContract.AmendmentKind.GENERAL, "Outro", null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ja registrado");
    }

    @Test
    void goalLinkedToUnknownAmendmentIsRefusedAndToKnownIsAccepted() {
        RegulatoryContractService s = ContractFixtures.service();
        RegulatoryContract.Contract c = ContractFixtures.concession();
        s.registerContract(c);
        RegulatoryContract.ContractAmendment amendment = new RegulatoryContract.ContractAmendment(
                java.util.UUID.randomUUID(), ContractFixtures.TENANT, c.id(), "ADT-9",
                LocalDate.of(2025, 6, 1), RegulatoryContract.AmendmentKind.GOAL_UPDATE_NR8, "Metas", null);
        RegulatoryContract.ContractGoal orphan = new RegulatoryContract.ContractGoal(
                java.util.UUID.randomUUID(), ContractFixtures.TENANT, c.id(), "Meta sintetica",
                null, new java.math.BigDecimal("99"), "%", 2033, amendment.id());
        assertThatThrownBy(() -> s.addGoal(orphan))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("aditivo inexistente");
        s.addAmendment(amendment);
        s.addGoal(orphan); // nao lanca: aditivo agora existe
    }
}
