package br.com.ziipsan.core.certification.infosec27001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Aceite da ED-73: campanha periódica de recertificação de acessos por
 * papel e por escopo, com decisão do gestor e registro, sobre o catálogo
 * de permissões da ED-06 consumido por porta (dublê sintético da porta,
 * sem duplicar o catálogo real).
 */
class AccessRecertificationCampaignTest {

    private static final Iso27001Activation ON = Iso27001Activation.enabled("escopo SYN",
            UUID.fromString("00000000-0000-0000-0000-000000000101"),
            UUID.fromString("00000000-0000-0000-0000-000000000102"));
    private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-000000000301");

    private static AccessRecertificationCampaign.PermissionCatalogPort catalog() {
        return () -> List.of(
                new AccessRecertificationCampaign.CatalogGrant("SYN-ADMIN", "TENANT", "registry:write"),
                new AccessRecertificationCampaign.CatalogGrant("SYN-ADMIN", "TENANT", "registry:read"),
                new AccessRecertificationCampaign.CatalogGrant("SYN-READER", "TENANT", "registry:read"));
    }

    private static AccessRecertificationCampaign campaign() {
        return AccessRecertificationCampaign.open(ON, "SYN campanha 2026-1",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), catalog());
    }

    @Test
    @DisplayName("campanha nasce do retrato do catálogo: um item por papel × escopo × permissão")
    void opensFromCatalogSnapshot() {
        AccessRecertificationCampaign c = campaign();
        assertThat(c.items()).hasSize(3);
        assertThat(c.pendingItems()).hasSize(3);
        assertThat(c.fullyDecided()).isFalse();
    }

    @Test
    @DisplayName("decisão do gestor é registrada e tira o item dos pendentes")
    void managerDecisionRecorded() {
        AccessRecertificationCampaign c = campaign();
        var item = c.decide("SYN-ADMIN|TENANT|registry:write",
                AccessRecertificationCampaign.ManagerDecision.KEEP, MANAGER,
                Instant.parse("2026-01-15T10:00:00Z"), "acesso ainda necessário (sintético)");
        assertThat(item.decision()).isEqualTo(AccessRecertificationCampaign.ManagerDecision.KEEP);
        assertThat(item.decidedBy()).isEqualTo(MANAGER);
        assertThat(c.pendingItems()).hasSize(2);
    }

    @Test
    @DisplayName("decisão duplicada, sem justificativa ou de item estranho é recusada")
    void invalidDecisionsRefused() {
        AccessRecertificationCampaign c = campaign();
        c.decide("SYN-ADMIN|TENANT|registry:read",
                AccessRecertificationCampaign.ManagerDecision.REVOKE, MANAGER,
                Instant.parse("2026-01-15T10:00:00Z"), "papel extinto (sintético)");
        assertThatThrownBy(() -> c.decide("SYN-ADMIN|TENANT|registry:read",
                AccessRecertificationCampaign.ManagerDecision.KEEP, MANAGER,
                Instant.parse("2026-01-16T10:00:00Z"), "outra"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> c.decide("SYN-READER|TENANT|registry:read",
                AccessRecertificationCampaign.ManagerDecision.KEEP, MANAGER,
                Instant.parse("2026-01-16T10:00:00Z"), " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> c.decide("SYN-XXX|TENANT|registry:read",
                AccessRecertificationCampaign.ManagerDecision.KEEP, MANAGER,
                Instant.parse("2026-01-16T10:00:00Z"), "nota"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("campanha só está completa quando todo item tem decisão — nada mantido por omissão")
    void fullyDecidedOnlyWhenAllDecided() {
        AccessRecertificationCampaign c = campaign();
        for (var item : List.copyOf(c.items())) {
            c.decide(item.grant().key(), AccessRecertificationCampaign.ManagerDecision.KEEP,
                    MANAGER, Instant.parse("2026-01-20T10:00:00Z"), "revisado (sintético)");
        }
        assertThat(c.fullyDecided()).isTrue();
        assertThat(c.pendingItems()).isEmpty();
    }

    @Test
    @DisplayName("catálogo vazio, prazo inválido e norma desligada recusam a abertura")
    void openRefusals() {
        assertThatThrownBy(() -> AccessRecertificationCampaign.open(ON, "x",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), List::of))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AccessRecertificationCampaign.open(ON, "x",
                LocalDate.of(2026, 1, 31), LocalDate.of(2026, 1, 1), catalog()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AccessRecertificationCampaign.open(Iso27001Activation.disabled(),
                "x", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), catalog()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("correção D4: campo da concessão acima do VARCHAR(120) do SQL é recusado no Java")
    void grantFieldCeilingEnforced() {
        String tooLong = "x".repeat(AccessRecertificationCampaign.CatalogGrant.GRANT_FIELD_MAX + 1);
        assertThatThrownBy(() -> new AccessRecertificationCampaign.CatalogGrant(
                tooLong, "TENANT", "registry:read")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AccessRecertificationCampaign.CatalogGrant(
                "SYN-A", tooLong, "registry:read")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AccessRecertificationCampaign.CatalogGrant(
                "SYN-A", "TENANT", tooLong)).isInstanceOf(IllegalArgumentException.class);
        String atCeiling = "x".repeat(AccessRecertificationCampaign.CatalogGrant.GRANT_FIELD_MAX);
        assertThat(new AccessRecertificationCampaign.CatalogGrant(atCeiling, "TENANT", "p").role())
                .isEqualTo(atCeiling);
    }

    @Test
    @DisplayName("correção D1/P6: item forjado incoerente é recusado no construtor canônico")
    void forgedItemCoherenceEnforced() {
        var grant = new AccessRecertificationCampaign.CatalogGrant("SYN-A", "TENANT", "registry:read");
        // Decisão sem decisor/instante/justificativa não se forja:
        assertThatThrownBy(() -> new AccessRecertificationCampaign.Item(grant,
                AccessRecertificationCampaign.ManagerDecision.KEEP, null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AccessRecertificationCampaign.Item(grant,
                AccessRecertificationCampaign.ManagerDecision.KEEP, MANAGER, null, "nota"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AccessRecertificationCampaign.Item(grant,
                AccessRecertificationCampaign.ManagerDecision.KEEP, MANAGER,
                Instant.parse("2026-01-15T10:00:00Z"), " "))
                .isInstanceOf(IllegalArgumentException.class);
        // Pendente com dados de decisão também não se forja:
        assertThatThrownBy(() -> new AccessRecertificationCampaign.Item(grant, null, MANAGER,
                Instant.parse("2026-01-15T10:00:00Z"), "nota"))
                .isInstanceOf(IllegalArgumentException.class);
        // E os dois estados honestos nascem:
        assertThat(new AccessRecertificationCampaign.Item(grant, null, null, null, null).pending())
                .isTrue();
        assertThat(new AccessRecertificationCampaign.Item(grant,
                AccessRecertificationCampaign.ManagerDecision.REVOKE, MANAGER,
                Instant.parse("2026-01-15T10:00:00Z"), "  nota  ").note()).isEqualTo("nota");
    }

    @Test
    @DisplayName("concessão duplicada no retrato do catálogo é recusada")
    void duplicateGrantRefused() {
        AccessRecertificationCampaign.PermissionCatalogPort dup = () -> List.of(
                new AccessRecertificationCampaign.CatalogGrant("SYN-A", "TENANT", "registry:read"),
                new AccessRecertificationCampaign.CatalogGrant("SYN-A", "TENANT", "registry:read"));
        assertThatThrownBy(() -> AccessRecertificationCampaign.open(ON, "x",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), dup))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
