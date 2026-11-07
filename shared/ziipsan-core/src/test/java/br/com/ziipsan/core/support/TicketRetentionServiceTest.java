package br.com.ziipsan.core.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes puros da retencao de chamados (P3.19; RAS-18): calculo do
 * {@code purge_at}, elegibilidade do ensaio/expurgo e o portao
 * arquivistico por valor de {@code reg.legal_nature}. Os fluxos com
 * banco (contagem do ensaio sobre linhas reais, DELETE do expurgo e a
 * secao da exportacao) exigem o banco de integracao da CI total.
 */
class TicketRetentionServiceTest {

    private static final TicketRetentionService.ArchivalApproval COMPLETE =
            new TicketRetentionService.ArchivalApproval(true, true, true, true);

    @Test
    @DisplayName("purge_at: fim da vigencia mais 5 anos, mesma data")
    void purgeDateIsContractEndPlusFiveYears() {
        assertThat(TicketRetentionService.purgeDate(LocalDate.of(2026, 12, 31)))
                .isEqualTo(LocalDate.of(2031, 12, 31));
        assertThat(TicketRetentionService.purgeDate(LocalDate.of(2028, 2, 29)))
                .isEqualTo(LocalDate.of(2033, 2, 28));
        assertThat(TicketRetentionService.RETENTION_YEARS).isEqualTo(5);
    }

    @Test
    @DisplayName("purge_at (instante): mais 5 anos, sem deslocar hora nem fuso")
    void purgeAtInstantPlusFiveYears() {
        OffsetDateTime end = OffsetDateTime.of(2027, 6, 30, 23, 59, 0, 0, ZoneOffset.ofHours(-3));
        assertThat(TicketRetentionService.purgeAt(end))
                .isEqualTo(OffsetDateTime.of(2032, 6, 30, 23, 59, 0, 0, ZoneOffset.ofHours(-3)));
    }

    @Test
    @DisplayName("purge_at sem fim de vigencia: recusado, nunca inventado")
    void purgeAtRequiresContractEnd() {
        assertThatThrownBy(() -> TicketRetentionService.purgeDate(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TicketRetentionService.purgeAt(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ensaio/expurgo: so entra quem tem purge_at vencido")
    void purgeableOnlyWhenPurgeAtDue() {
        OffsetDateTime now = OffsetDateTime.of(2032, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        assertThat(TicketRetentionService.isPurgeable(now.minusSeconds(1), now)).isTrue();
        assertThat(TicketRetentionService.isPurgeable(now, now)).isTrue();
        assertThat(TicketRetentionService.isPurgeable(now.plusSeconds(1), now)).isFalse();
        assertThat(TicketRetentionService.isPurgeable(null, now)).isFalse();
    }

    @Test
    @DisplayName("tenant privado (PRIVATE): expurgo liberado sem exigencia arquivistica")
    void privateTenantNeedsNoArchivalApproval() {
        assertThat(TicketRetentionService.isPublicNature("PRIVATE")).isFalse();
        assertThatCode(() -> TicketRetentionService.requirePurgeAllowed(
                "PRIVATE", TicketRetentionService.ArchivalApproval.NONE))
                .doesNotThrowAnyException();
        assertThatCode(() -> TicketRetentionService.requirePurgeAllowed("PRIVATE", null))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"AUTARCHY", "PUBLIC_COMPANY", "SEM"})
    @DisplayName("tenant publico sem temporalidade/listagem aprovadas: execucao recusada")
    void publicTenantRefusedWithoutArchivalApproval(String legalNature) {
        assertThat(TicketRetentionService.isPublicNature(legalNature)).isTrue();
        assertThatThrownBy(() -> TicketRetentionService.requirePurgeAllowed(
                legalNature, TicketRetentionService.ArchivalApproval.NONE))
                .isInstanceOf(TicketRetentionService.RetentionRefusedException.class)
                .hasMessageContaining("Lei 8.159/1991");
        assertThatThrownBy(() -> TicketRetentionService.requirePurgeAllowed(legalNature, null))
                .isInstanceOf(TicketRetentionService.RetentionRefusedException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"AUTARCHY", "PUBLIC_COMPANY", "SEM"})
    @DisplayName("tenant publico com registro mas sem aprovacao do tenant: recusado")
    void publicTenantRefusedWithRegistrationOnly(String legalNature) {
        var registeredOnly = new TicketRetentionService.ArchivalApproval(true, false, true, false);
        assertThatThrownBy(() -> TicketRetentionService.requirePurgeAllowed(legalNature, registeredOnly))
                .isInstanceOf(TicketRetentionService.RetentionRefusedException.class)
                .hasMessageContaining("nao aprovada pelo tenant");
        var listMissing = new TicketRetentionService.ArchivalApproval(true, true, false, false);
        assertThatThrownBy(() -> TicketRetentionService.requirePurgeAllowed(legalNature, listMissing))
                .isInstanceOf(TicketRetentionService.RetentionRefusedException.class)
                .hasMessageContaining("listagem de eliminacao nao registrada");
    }

    @ParameterizedTest
    @ValueSource(strings = {"AUTARCHY", "PUBLIC_COMPANY", "SEM"})
    @DisplayName("tenant publico com temporalidade e listagem registradas e aprovadas: liberado")
    void publicTenantAllowedWithCompleteApproval(String legalNature) {
        assertThatCode(() -> TicketRetentionService.requirePurgeAllowed(legalNature, COMPLETE))
                .doesNotThrowAnyException();
        assertThat(COMPLETE.complete()).isTrue();
        assertThat(TicketRetentionService.ArchivalApproval.NONE.complete()).isFalse();
    }

    @Test
    @DisplayName("natureza ausente ou fora do dominio: recusado (sem default para privado)")
    void unknownLegalNatureRefused() {
        var refused = org.junit.jupiter.api.Assertions.assertThrows(
                TicketRetentionService.RetentionRefusedException.class,
                () -> TicketRetentionService.requirePurgeAllowed("PUBLIC", COMPLETE));
        assertThat(refused.code()).isEqualTo("legal_nature_unknown");
        assertThatThrownBy(() -> TicketRetentionService.requirePurgeAllowed(null, COMPLETE))
                .isInstanceOf(TicketRetentionService.RetentionRefusedException.class);
        assertThatThrownBy(() -> TicketRetentionService.requirePurgeAllowed("", COMPLETE))
                .isInstanceOf(TicketRetentionService.RetentionRefusedException.class);
    }

    @Test
    @DisplayName("dominio de reg.legal_nature confere com a core V9")
    void legalNatureDomain() {
        assertThat(TicketRetentionService.LEGAL_NATURES)
                .containsExactlyInAnyOrder("PRIVATE", "AUTARCHY", "PUBLIC_COMPANY", "SEM");
        assertThat(TicketRetentionService.PUBLIC_LEGAL_NATURES)
                .containsExactlyInAnyOrder("AUTARCHY", "PUBLIC_COMPANY", "SEM");
    }
}
