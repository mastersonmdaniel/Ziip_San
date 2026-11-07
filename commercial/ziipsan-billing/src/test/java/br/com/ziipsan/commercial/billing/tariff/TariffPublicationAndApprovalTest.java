package br.com.ziipsan.commercial.billing.tariff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Trava de publicação (30 dias, NR 10) e homologação tácita (P2.29).
 * Datas contadas à mão: 2026-01-01 + 30 dias = 2026-01-31.
 */
class TariffPublicationAndApprovalTest {

    private static final LocalDate PUBLISHED = LocalDate.of(2026, 1, 1);

    @Test
    @DisplayName("publicação a 29 dias da vigência é RECUSADA (o teste do aceite)")
    void publicationTwentyNineDaysBeforeIsRefused() {
        assertThatThrownBy(() -> TariffPublicationRule.validate(
                PUBLISHED, LocalDate.of(2026, 1, 30), 30))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("29 dias")
                .hasMessageContaining("30");
    }

    @Test
    @DisplayName("publicação a exatamente 30 dias da vigência é aceita; a 31 também")
    void publicationThirtyDaysBeforeIsAccepted() {
        TariffPublicationRule.validate(PUBLISHED, LocalDate.of(2026, 1, 31), 30);
        TariffPublicationRule.validate(PUBLISHED, LocalDate.of(2026, 2, 1), 30);
    }

    @Test
    @DisplayName("parâmetro com prazo MAIOR vale: agência pede 45 dias, 40 é recusado e 45 é aceito")
    void largerConfiguredMinimumApplies() {
        assertThat(TariffPublicationRule.effectiveMinDays(45)).isEqualTo(45);
        assertThatThrownBy(() -> TariffPublicationRule.validate(
                PUBLISHED, LocalDate.of(2026, 2, 10), 45))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("45 dias");
        TariffPublicationRule.validate(PUBLISHED, LocalDate.of(2026, 2, 15), 45);
    }

    @Test
    @DisplayName("parâmetro NÃO reduz o mínimo: pedir 10 dias não afrouxa a trava de 30 (travado)")
    void configuredMinimumBelowTheFloorDoesNotLoosenTheLock() {
        assertThat(TariffPublicationRule.effectiveMinDays(10)).isEqualTo(30);
        assertThat(TariffPublicationRule.effectiveMinDays(0)).isEqualTo(30);
        assertThatThrownBy(() -> TariffPublicationRule.validate(
                PUBLISHED, LocalDate.of(2026, 1, 30), 10))
                .isInstanceOf(TariffConfigurationException.class);
        TariffPublicationRule.validate(PUBLISHED, LocalDate.of(2026, 1, 31), 10);
    }

    @Test
    @DisplayName("sem data de publicação não há como conferir a antecedência: recusado")
    void missingPublicationDateIsRefused() {
        assertThatThrownBy(() -> TariffPublicationRule.validate(null, LocalDate.of(2026, 3, 1), 30))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("publicação");
    }

    // ------------------------------------------------------------------------------------------
    // Homologação tácita
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("homologação tácita RECUSADA quando a norma da agência não a prevê")
    void tacitApprovalRefusedWhenAgencyNormDoesNotProvideIt() {
        assertThatThrownBy(() -> TariffApprovalRule.tacitApprovalDate(
                false, PUBLISHED, 15, LocalDate.of(2026, 2, 1)))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("não a prevê");
    }

    @Test
    @DisplayName("homologação tácita: publica 01/01, prazo 15 dias → homologa em 16/01; consultar em 15/01 é recusado")
    void tacitApprovalOnlyAfterTheTerm() {
        assertThatThrownBy(() -> TariffApprovalRule.tacitApprovalDate(
                true, PUBLISHED, 15, LocalDate.of(2026, 1, 15)))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("2026-01-16");
        assertThat(TariffApprovalRule.tacitApprovalDate(true, PUBLISHED, 15, LocalDate.of(2026, 1, 16)))
                .isEqualTo(LocalDate.of(2026, 1, 16));
    }

    @Test
    @DisplayName("homologação tácita sem prazo configurado é recusada (o prazo não se presume)")
    void tacitApprovalWithoutTermIsRefused() {
        assertThatThrownBy(() -> TariffApprovalRule.tacitApprovalDate(
                true, PUBLISHED, null, LocalDate.of(2026, 2, 1)))
                .isInstanceOf(TariffConfigurationException.class);
        assertThatThrownBy(() -> TariffApprovalRule.tacitApprovalDate(
                true, PUBLISHED, 0, LocalDate.of(2026, 2, 1)))
                .isInstanceOf(TariffConfigurationException.class);
    }

    @Test
    @DisplayName("homologação expressa exige a data do ato e ela não antecede a publicação")
    void expressApproval() {
        assertThat(TariffApprovalRule.expressApprovalDate(PUBLISHED, LocalDate.of(2026, 1, 20)))
                .isEqualTo(LocalDate.of(2026, 1, 20));
        assertThatThrownBy(() -> TariffApprovalRule.expressApprovalDate(PUBLISHED, null))
                .isInstanceOf(TariffConfigurationException.class);
        assertThatThrownBy(() -> TariffApprovalRule.expressApprovalDate(
                PUBLISHED, LocalDate.of(2025, 12, 31)))
                .isInstanceOf(TariffConfigurationException.class);
    }
}
