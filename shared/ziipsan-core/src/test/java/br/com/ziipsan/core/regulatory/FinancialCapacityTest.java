package br.com.ziipsan.core.regulatory;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Painel do Decreto 11.598/2023 com massa sintetica de resultado
 * conhecido: exportacao-base tem margem 0,20, endividamento 0,60,
 * retorno 0,10 e suficiencia 1,50 — os quatro MEETS.
 */
class FinancialCapacityTest {

    private static FinancialCapacity.ErpExport base() {
        return new FinancialCapacity.ErpExport(2025, "ERP-SINTETICO-2025",
                new BigDecimal("100"), new BigDecimal("60"), new BigDecimal("40"),
                new BigDecimal("1000"), new BigDecimal("600"), new BigDecimal("1000"),
                new BigDecimal("1000"), new BigDecimal("300"), new BigDecimal("200"));
    }

    @Test
    void knownExportMeetsAllFourThresholds() {
        FinancialCapacity.Panel p = FinancialCapacity.panel(base());
        assertThat(p.margin().value()).isEqualByComparingTo("0.200000");
        assertThat(p.leverage().value()).isEqualByComparingTo("0.600000");
        assertThat(p.returnOnEquity().value()).isEqualByComparingTo("0.100000");
        assertThat(p.cashSufficiency().value()).isEqualByComparingTo("1.500000");
        assertThat(p.allMeet()).isTrue();
        assertThat(p.hasIndeterminate()).isFalse();
    }

    @Test
    void leverageExactlyOneMeetsAndCashExactlyOneDoesNot() {
        FinancialCapacity.ErpExport edge = new FinancialCapacity.ErpExport(2025, "ERP-EDGE",
                new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100"),
                new BigDecimal("1000"), new BigDecimal("1000"), new BigDecimal("100"),
                new BigDecimal("200"), new BigDecimal("200"));
        FinancialCapacity.Panel p = FinancialCapacity.panel(edge);
        assertThat(p.leverage().verdict()).isEqualTo(FinancialCapacity.Verdict.MEETS); // <= 1 (aceite)
        assertThat(p.cashSufficiency().verdict()).isEqualTo(FinancialCapacity.Verdict.DOES_NOT_MEET); // exige > 1
        assertThat(p.allMeet()).isFalse();
    }

    @Test
    void negativeMarginAndReturnDoNotMeet() {
        FinancialCapacity.ErpExport loss = new FinancialCapacity.ErpExport(2025, "ERP-LOSS",
                new BigDecimal("-300"), new BigDecimal("60"), new BigDecimal("40"), new BigDecimal("1000"),
                new BigDecimal("600"), new BigDecimal("1000"), new BigDecimal("1000"),
                new BigDecimal("300"), new BigDecimal("200"));
        FinancialCapacity.Panel p = FinancialCapacity.panel(loss);
        assertThat(p.margin().verdict()).isEqualTo(FinancialCapacity.Verdict.DOES_NOT_MEET);
        assertThat(p.returnOnEquity().verdict()).isEqualTo(FinancialCapacity.Verdict.DOES_NOT_MEET);
    }

    @Test
    void nonPositiveDenominatorIsIndeterminateNeverPresumed() {
        FinancialCapacity.ErpExport zero = new FinancialCapacity.ErpExport(2025, "ERP-ZERO",
                new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("600"), new BigDecimal("1000"), BigDecimal.ZERO,
                new BigDecimal("300"), BigDecimal.ZERO);
        FinancialCapacity.Panel p = FinancialCapacity.panel(zero);
        assertThat(p.margin().verdict()).isEqualTo(FinancialCapacity.Verdict.INDETERMINATE);
        assertThat(p.returnOnEquity().verdict()).isEqualTo(FinancialCapacity.Verdict.INDETERMINATE);
        assertThat(p.cashSufficiency().verdict()).isEqualTo(FinancialCapacity.Verdict.INDETERMINATE);
        assertThat(p.leverage().verdict()).isEqualTo(FinancialCapacity.Verdict.MEETS);
        assertThat(p.allMeet()).isFalse();
    }

    @Test
    void panelIsRefusedForDirectProvisionAndOfferedUnderContract() {
        RegulatoryContractService s = ContractFixtures.service();
        RegulatoryContract.Contract direct = ContractFixtures.contract(
                RegulatoryContract.ContractType.DIRECT_PROVISION, RegulatoryContract.ServiceKind.WATER_SUPPLY,
                LocalDate.of(2020, 1, 1), null);
        s.registerContract(direct);
        assertThat(s.panelOfferedTo(ContractFixtures.TENANT)).isFalse();
        assertThatThrownBy(() -> s.financialPanel(ContractFixtures.TENANT, direct.id(), base()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sob contrato");

        RegulatoryContractService s2 = ContractFixtures.service();
        RegulatoryContract.Contract concession = ContractFixtures.concession();
        s2.registerContract(concession);
        assertThat(s2.panelOfferedTo(ContractFixtures.TENANT)).isTrue();
        assertThat(s2.financialPanel(ContractFixtures.TENANT, concession.id(), base()).allMeet()).isTrue();
    }

    @Test
    void financialPanelsAreIsolatedByTenant() {
        // Correcao D4 (revisao ED-96): o store de referencia filtra os paineis
        // por tenantId, como os demais metodos — painel do tenant A nao pode
        // vazar para o tenant B nem com o mesmo contractId.
        InMemoryContractStore store = new InMemoryContractStore();
        UUID contractId = UUID.randomUUID();
        store.saveFinancialPanel(ContractFixtures.TENANT, contractId, FinancialCapacity.panel(base()));
        assertThat(store.financialPanels(ContractFixtures.TENANT, contractId)).hasSize(1);
        assertThat(store.financialPanels(ContractFixtures.OTHER_TENANT, contractId)).isEmpty();
    }
}
