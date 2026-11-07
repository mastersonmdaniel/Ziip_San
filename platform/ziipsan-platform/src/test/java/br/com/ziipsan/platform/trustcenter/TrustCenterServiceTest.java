package br.com.ziipsan.platform.trustcenter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P3.38: a visão pública do Trust Center — fato com fonte entra;
 * fonte ausente vira seção PENDENTE declarada, nunca conteúdo inventado.
 *
 * <p>O item de certificação abaixo reproduz os fatos da P0.26 como
 * fixture (provedor AWS, região sa-east-1, ISO como certificações e SOC
 * como relatórios de asseguração) para provar que a visão preserva a
 * distinção e a fonte; a entrega real à página vem do relatório
 * arquivado na P0.44 via {@link TrustCenterSource}.
 */
class TrustCenterServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    private static TrustCenterService.ProviderCertification providerFixture() {
        return new TrustCenterService.ProviderCertification("AWS", "sa-east-1",
                List.of("ISO 9001", "ISO 27001", "ISO 27017"),
                List.of("SOC 1", "SOC 3"),
                "P0.26 — relatórios do AWS Artifact arquivados no kit da P0.44");
    }

    private static Subprocessor listedSub() {
        return new Subprocessor("Provedor Nacional (fixture)", "Hospedagem de arquivos",
                "Guardar anexos cifrados", "BR", true, false, true,
                Subprocessor.ChangeStatus.ACTIVE, null, null);
    }

    @Test
    @DisplayName("P3.38: sem nenhuma fonte entregue, todas as seções de fonte são PENDING — e o JSON não afirma nada além disso")
    void emptySourcesRenderPendingSections() {
        TrustCenterService.TrustCenterView view = TrustCenterService.buildView(
                List.of(), List.of(), null, null, null, DisclosurePolicy.pending(), false, NOW);
        assertThat(view.subprocessorsSection().state())
                .isEqualTo(TrustCenterService.SectionState.PENDING_SOURCE);
        assertThat(view.providerCertificationsSection().state())
                .isEqualTo(TrustCenterService.SectionState.PENDING_SOURCE);
        assertThat(view.pentest().state()).isEqualTo(TrustCenterService.SectionState.PENDING_SOURCE);
        assertThat(view.pentest().summary()).isNull();
        assertThat(view.dpo().state()).isEqualTo(TrustCenterService.SectionState.PENDING_SOURCE);
        assertThat(view.dpo().name()).isNull();
        assertThat(view.disclosure().state()).isEqualTo(TrustCenterService.SectionState.PENDING_DECISION);
        assertThat(view.disclosure().acknowledgementTarget()).isNull();
        assertThat(view.asvsEvidence().state()).isEqualTo(TrustCenterService.SectionState.PENDING_SOURCE);
        // O critério ASVS é fato de spec e aparece mesmo com a evidência pendente.
        assertThat(view.asvsEvidence().note()).contains("ASVS 5.0");

        String json = TrustCenterService.toJson(view);
        assertThat(json).contains("PENDING_SOURCE").contains("PENDING_DECISION");
        // Nenhum contato de pesquisador nem relato existe na visão pública.
        assertThat(json).doesNotContain("reporterContact").doesNotContain("VULN-");
    }

    @Test
    @DisplayName("P3.38: com fontes entregues, seções ficam AVAILABLE e a distinção certificação × relatório do provedor é preservada")
    void deliveredSourcesRenderAvailableSections() {
        TrustCenterService.TrustCenterView view = TrustCenterService.buildView(
                List.of(listedSub()), List.of(providerFixture()),
                "Resumo público do pentest (fixture de teste).", "Encarregado Fixture",
                "dpo@example.invalid",
                new DisclosurePolicy("mailto:security@example.invalid",
                        "https://example.invalid/trust-center/politica", null,
                        Duration.ofDays(2), null),
                true, NOW);
        assertThat(view.subprocessorsSection().state()).isEqualTo(TrustCenterService.SectionState.AVAILABLE);
        assertThat(view.subprocessors()).hasSize(1);
        assertThat(view.providerCertificationsSection().state())
                .isEqualTo(TrustCenterService.SectionState.AVAILABLE);
        assertThat(view.providerCertifications().get(0).certifications())
                .containsExactly("ISO 9001", "ISO 27001", "ISO 27017");
        assertThat(view.providerCertifications().get(0).assuranceReports())
                .containsExactly("SOC 1", "SOC 3");
        assertThat(view.pentest().state()).isEqualTo(TrustCenterService.SectionState.AVAILABLE);
        assertThat(view.dpo().state()).isEqualTo(TrustCenterService.SectionState.AVAILABLE);
        assertThat(view.disclosure().state()).isEqualTo(TrustCenterService.SectionState.AVAILABLE);
        assertThat(view.asvsEvidence().state()).isEqualTo(TrustCenterService.SectionState.AVAILABLE);
        assertThat(view.statusPagePath()).isEqualTo(TrustCenterService.STATUS_PAGE_PATH);
    }

    @Test
    @DisplayName("P3.38: estrangeiro sem cláusulas-padrão NÃO entra na visão pública, e mudança anunciada vai para a seção de avisos")
    void viewFiltersUnpublishableAndSeparatesAnnouncedChanges() {
        Subprocessor blocked = new Subprocessor("Foreign Provider (fixture)", "Delivery",
                "Enviar notificações", "US", true, false, true,
                Subprocessor.ChangeStatus.ACTIVE, null, null);
        Subprocessor changing = new Subprocessor("Provedor Nacional (fixture)", "Hospedagem de arquivos",
                "Guardar anexos cifrados", "BR", true, false, true,
                Subprocessor.ChangeStatus.CHANGE_ANNOUNCED, NOW, NOW.plusSeconds(30L * 24 * 3600));
        TrustCenterService.TrustCenterView view = TrustCenterService.buildView(
                List.of(blocked, changing), List.of(), null, null, null,
                DisclosurePolicy.pending(), false, NOW);
        assertThat(view.subprocessors()).isEmpty();
        assertThat(view.announcedChanges()).hasSize(1);
        assertThat(view.announcedChanges().get(0).changeAnnounced()).isTrue();
        assertThat(view.announcedChanges().get(0).changeEffectiveAt())
                .isEqualTo(NOW.plusSeconds(30L * 24 * 3600));
        // A seção não está vazia (há a mudança), logo não é "pendente".
        assertThat(view.subprocessorsSection().state()).isEqualTo(TrustCenterService.SectionState.AVAILABLE);
        assertThat(TrustCenterService.toJson(view)).doesNotContain("Foreign Provider");
    }
}
