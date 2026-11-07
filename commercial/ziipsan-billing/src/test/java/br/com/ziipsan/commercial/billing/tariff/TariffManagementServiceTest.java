package br.com.ziipsan.commercial.billing.tariff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fluxo de gestão (P2.29): reajuste, revisão, cópia e histórico.
 * Origem: T1 fechada em 2026-05-31 (versão id 100, de 2026-01-01 a 2026-05-31);
 * nova vigência a partir de 2026-06-01. Publicação em 2026-04-15 → 47 dias
 * até a vigência (≥ 30). Publicação em 2026-05-20 → 12 dias (< 30, recusado).
 */
class TariffManagementServiceTest {

    private static TariffVersion closedSource() {
        return new TariffVersion(id(100), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 31),
                List.of(t1Res(), t1Com()), null);
    }

    private static TariffManagementService.ReadjustmentRequest request(LocalDate publishedOn) {
        return requestTacit(publishedOn, false, null);
    }

    /** Pedido com os fatos da homologação tácita (previsão na norma + prazo) vindos do parâmetro. */
    private static TariffManagementService.ReadjustmentRequest requestTacit(LocalDate publishedOn,
                                                                            boolean tacitAllowed,
                                                                            Integer tacitDays) {
        return new TariffManagementService.ReadjustmentRequest(
                LocalDate.of(2025, 6, 1), "IPCA", d("5"), null, ReadjustFormula.INDEX_ONLY,
                "Res. ARESC 001/2026", publishedOn, 30, tacitAllowed, tacitDays);
    }

    @Test
    @DisplayName("reajuste por IPCA de 5% gera nova versão com metadado completo e método registrado")
    void readjustmentProposal() {
        TariffManagementService.Proposal proposal = TariffManagementService.proposeReadjustment(
                closedSource(), List.of(closedSource()), id(200),
                LocalDate.of(2026, 6, 1), null, request(LocalDate.of(2026, 4, 15)),
                TariffApprovalRule.ApprovalMode.EXPRESS, LocalDate.of(2026, 4, 20));

        assertThat(proposal.version().ruleFor(RES, null).minCharge()).isEqualByComparingTo("31.5000");
        assertThat(proposal.metadata().changeType()).isEqualTo(TariffVersionChangeType.READJUSTMENT);
        assertThat(proposal.metadata().sourceVersionId()).isEqualTo(id(100));
        assertThat(proposal.metadata().indexCode()).isEqualTo("IPCA");
        assertThat(proposal.metadata().appliedPct()).isEqualByComparingTo("5.0000");
        assertThat(proposal.metadata().baseDate()).isEqualTo(LocalDate.of(2025, 6, 1));
        assertThat(proposal.metadata().calculationMethod()).contains("IPCA").contains("5.0000");
    }

    @Test
    @DisplayName("reajuste com publicação a menos de 30 dias da vigência é RECUSADO (teste do aceite)")
    void readjustmentPublishedTooLateIsRefused() {
        assertThatThrownBy(() -> TariffManagementService.proposeReadjustment(
                closedSource(), List.of(closedSource()), id(200),
                LocalDate.of(2026, 6, 1), null, request(LocalDate.of(2026, 5, 20)),
                TariffApprovalRule.ApprovalMode.EXPRESS, LocalDate.of(2026, 5, 25)))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("12 dias");
    }

    @Test
    @DisplayName("vigência que se sobrepõe à versão conhecida é recusada (espelho da EXCLUDE da V4_1)")
    void overlappingValidityIsRefused() {
        // origem ABERTA (sem validTo): qualquer vigência nova se sobrepõe
        assertThatThrownBy(() -> TariffManagementService.proposeReadjustment(
                t1(), List.of(t1()), id(200),
                LocalDate.of(2026, 6, 1), null, request(LocalDate.of(2026, 4, 15)),
                TariffApprovalRule.ApprovalMode.EXPRESS, LocalDate.of(2026, 4, 20)))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("sobrepõe");
        // origem fechada em 31/05, nova vigência começando em 15/05: sobrepõe
        assertThatThrownBy(() -> TariffManagementService.proposeCopy(
                closedSource(), List.of(closedSource()), id(201),
                LocalDate.of(2026, 5, 15), null))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("sobrepõe");
    }

    @Test
    @DisplayName("revisão por ato: preços do ato entram; sem ato, a revisão é recusada")
    void reviewByAgencyAct() {
        List<CategoryRule> revised = List.of(
                res(10, "32.00", block(20, "4.50"), block(50, "6.50"), block(null, "9.50")),
                t1Com());

        TariffManagementService.Proposal proposal = TariffManagementService.proposeReview(
                closedSource(), List.of(closedSource()), id(202),
                LocalDate.of(2026, 6, 1), null, revised, null,
                "Res. AGIR 010/2026", LocalDate.of(2026, 4, 15), 30,
                "REVISO por ato: precos fixados diretamente pelo ato, sem indice.",
                TariffApprovalRule.ApprovalMode.EXPRESS, LocalDate.of(2026, 4, 20), false, null);

        assertThat(proposal.version().ruleFor(RES, null).minCharge()).isEqualByComparingTo("32.00");
        assertThat(proposal.metadata().changeType()).isEqualTo(TariffVersionChangeType.REVIEW);
        assertThat(proposal.metadata().regulatoryAct()).isEqualTo("Res. AGIR 010/2026");

        assertThatThrownBy(() -> TariffManagementService.proposeReview(
                closedSource(), List.of(closedSource()), id(203),
                LocalDate.of(2026, 6, 1), null, revised, null,
                null, LocalDate.of(2026, 4, 15), 30, "metodo",
                TariffApprovalRule.ApprovalMode.EXPRESS, LocalDate.of(2026, 4, 20), false, null))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("ato");
    }

    @Test
    @DisplayName("cópia pelo serviço: mesma estrutura, tipo COPY e método registrado para o histórico")
    void copyThroughTheService() {
        TariffManagementService.Proposal proposal = TariffManagementService.proposeCopy(
                closedSource(), List.of(closedSource()), id(204),
                LocalDate.of(2026, 6, 1), null);

        assertThat(proposal.version().ruleFor(COM, null).minCharge()).isEqualByComparingTo("50.00");
        assertThat(proposal.metadata().changeType()).isEqualTo(TariffVersionChangeType.COPY);
        assertThat(proposal.metadata().calculationMethod()).contains("COPIA");
    }

    @Test
    @DisplayName("homologação tácita no fluxo: prazo de 15 dias da publicação de 15/04 consuma em 30/04 e entra no metadado")
    void tacitApprovalInTheFlow() {
        LocalDate approvedOn = TariffApprovalRule.tacitApprovalDate(
                true, LocalDate.of(2026, 4, 15), 15, LocalDate.of(2026, 5, 1));
        assertThat(approvedOn).isEqualTo(LocalDate.of(2026, 4, 30));

        TariffManagementService.Proposal proposal = TariffManagementService.proposeReadjustment(
                closedSource(), List.of(closedSource()), id(205),
                LocalDate.of(2026, 6, 1), null, requestTacit(LocalDate.of(2026, 4, 15), true, 15),
                TariffApprovalRule.ApprovalMode.TACIT, approvedOn);

        assertThat(proposal.metadata().approvalMode()).isEqualTo(TariffApprovalRule.ApprovalMode.TACIT);
        assertThat(proposal.metadata().approvedOn()).isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    @DisplayName("homologação tácita SEM previsão na norma é RECUSADA pelo serviço, mesmo com a data da consumação certa")
    void tacitApprovalWithoutNormProvisionIsRefusedByTheService() {
        // 30/04 seria a consumação se a norma previsse (15/04 + 15 dias) — sem a
        // previsão, nem a data certa salva: os dois fatos são impostos no serviço.
        assertThatThrownBy(() -> TariffManagementService.proposeReadjustment(
                closedSource(), List.of(closedSource()), id(206),
                LocalDate.of(2026, 6, 1), null, requestTacit(LocalDate.of(2026, 4, 15), false, 15),
                TariffApprovalRule.ApprovalMode.TACIT, LocalDate.of(2026, 4, 30)))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("não a prevê");
    }

    @Test
    @DisplayName("homologação tácita sem prazo configurado é RECUSADA pelo serviço (o prazo não se presume)")
    void tacitApprovalWithoutConfiguredTermIsRefusedByTheService() {
        assertThatThrownBy(() -> TariffManagementService.proposeReadjustment(
                closedSource(), List.of(closedSource()), id(207),
                LocalDate.of(2026, 6, 1), null, requestTacit(LocalDate.of(2026, 4, 15), true, null),
                TariffApprovalRule.ApprovalMode.TACIT, LocalDate.of(2026, 4, 30)))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("sem prazo");
    }

    @Test
    @DisplayName("homologação tácita com data FABRICADA é RECUSADA pelo serviço: antes do prazo e depois da consumação")
    void tacitApprovalWithFabricatedDateIsRefusedByTheService() {
        // consumação real: 15/04 + 15 dias = 30/04. 20/04 é antes do prazo.
        assertThatThrownBy(() -> TariffManagementService.proposeReadjustment(
                closedSource(), List.of(closedSource()), id(208),
                LocalDate.of(2026, 6, 1), null, requestTacit(LocalDate.of(2026, 4, 15), true, 15),
                TariffApprovalRule.ApprovalMode.TACIT, LocalDate.of(2026, 4, 20)))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("ainda não consumada");
        // 10/05 é depois do prazo, mas não é a data da consumação: data inventada.
        assertThatThrownBy(() -> TariffManagementService.proposeReadjustment(
                closedSource(), List.of(closedSource()), id(209),
                LocalDate.of(2026, 6, 1), null, requestTacit(LocalDate.of(2026, 4, 15), true, 15),
                TariffApprovalRule.ApprovalMode.TACIT, LocalDate.of(2026, 5, 10)))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("fabricada")
                .hasMessageContaining("2026-04-30");
    }

    @Test
    @DisplayName("revisão com homologação tácita sem previsão na norma é RECUSADA pelo serviço")
    void reviewTacitApprovalWithoutNormProvisionIsRefusedByTheService() {
        List<CategoryRule> revised = List.of(
                res(10, "32.00", block(20, "4.50"), block(50, "6.50"), block(null, "9.50")),
                t1Com());

        assertThatThrownBy(() -> TariffManagementService.proposeReview(
                closedSource(), List.of(closedSource()), id(210),
                LocalDate.of(2026, 6, 1), null, revised, null,
                "Res. AGIR 010/2026", LocalDate.of(2026, 4, 15), 30, "metodo",
                TariffApprovalRule.ApprovalMode.TACIT, LocalDate.of(2026, 4, 30), false, 15))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("não a prevê");
    }

    @Test
    @DisplayName("reajuste sem fórmula é recusado com TariffConfigurationException, não NullPointerException")
    void nullFormulaIsRefusedCleanly() {
        TariffManagementService.ReadjustmentRequest noFormula = new TariffManagementService.ReadjustmentRequest(
                LocalDate.of(2025, 6, 1), "IPCA", d("5"), null, null,
                "Res. ARESC 001/2026", LocalDate.of(2026, 4, 15), 30, false, null);

        assertThatThrownBy(() -> TariffManagementService.proposeReadjustment(
                closedSource(), List.of(closedSource()), id(211),
                LocalDate.of(2026, 6, 1), null, noFormula,
                TariffApprovalRule.ApprovalMode.EXPRESS, LocalDate.of(2026, 4, 20)))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("fórmula");
    }

    // ------------------------------------------------------------------------------------------
    // Histórico público (NR 13)
    // ------------------------------------------------------------------------------------------

    private static TariffHistory.VersionWithMetadata versionWith(int idNum, LocalDate from, LocalDate to,
                                                                 VersionChangeMetadata metadata,
                                                                 List<CategoryRule> rules) {
        return new TariffHistory.VersionWithMetadata(new TariffVersion(id(idNum), from, to, rules, null), metadata);
    }

    @Test
    @DisplayName("histórico de 5 anos em 2026-06-01: janela desde 2021-06-01 exclui a versão de 2019 e mantém método de cada versão")
    void publicHistoryWindow() {
        VersionChangeMetadata m2019 = new VersionChangeMetadata(
                TariffVersionChangeType.INITIAL, null, null, null, null, null, null, null,
                null, null, null, null, "Estrutura inicial pelo motor de faixas progressivas.");
        VersionChangeMetadata m2024 = new VersionChangeMetadata(
                TariffVersionChangeType.REVIEW, id(110), null, null, null, null, null, null,
                "Res. ARESC 020/2023", LocalDate.of(2023, 11, 1), null, null,
                "REVISO por ato: precos do ato.");
        VersionChangeMetadata m2026 = new VersionChangeMetadata(
                TariffVersionChangeType.READJUSTMENT, id(120), LocalDate.of(2025, 6, 1), "IPCA",
                d("5"), null, d("5"), ReadjustFormula.INDEX_ONLY,
                "Res. ARESC 001/2026", LocalDate.of(2026, 4, 15),
                TariffApprovalRule.ApprovalMode.EXPRESS, LocalDate.of(2026, 4, 20),
                "REAJUSTE POR INDICE: IPCA 5%.");

        List<TariffHistory.VersionWithMetadata> all = List.of(
                versionWith(110, LocalDate.of(2019, 1, 1), LocalDate.of(2019, 12, 31), m2019, List.of(t1Res())),
                versionWith(120, LocalDate.of(2024, 1, 1), LocalDate.of(2025, 12, 31), m2024, List.of(t1Res())),
                versionWith(130, LocalDate.of(2026, 1, 1), null, m2026, List.of(t1Res())));

        List<TariffHistory.HistoryEntry> history = TariffHistory.publicHistory(
                all, LocalDate.of(2026, 6, 1), 5);

        assertThat(history).extracting(TariffHistory.HistoryEntry::versionId)
                .containsExactly(id(120), id(130));
        assertThat(history).allSatisfy(e -> assertThat(e.calculationMethod()).isNotBlank());
        assertThat(history.get(1).appliedPct()).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("histórico abaixo de 5 anos é recusado (piso travado) e versão da janela sem método é recusada")
    void historyFloorAndMethodAreLocked() {
        assertThatThrownBy(() -> TariffHistory.publicHistory(List.of(), LocalDate.of(2026, 6, 1), 3))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("5 anos");

        VersionChangeMetadata copyWithoutMethod = new VersionChangeMetadata(
                TariffVersionChangeType.COPY, id(100), null, null, null, null, null, null,
                null, null, null, null, null);
        List<TariffHistory.VersionWithMetadata> all = List.of(
                versionWith(140, LocalDate.of(2026, 1, 1), null, copyWithoutMethod, List.of(t1Res())));

        assertThatThrownBy(() -> TariffHistory.publicHistory(all, LocalDate.of(2026, 6, 1), 5))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("método de cálculo");
    }
}
