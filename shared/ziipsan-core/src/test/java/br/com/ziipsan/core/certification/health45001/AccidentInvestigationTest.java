package br.com.ziipsan.core.certification.health45001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccidentInvestigationTest {

    private static final Iso45001Activation ON =
            Iso45001Activation.enabled("SYN-escopo", UUID.randomUUID(), UUID.randomUUID());
    private static final UUID DOCTOR = UUID.randomUUID();
    private static final UUID OUTSIDER = UUID.randomUUID();
    private static final String SECRET = "SYN-DETALHE-DE-SAUDE-QUE-NAO-PODE-VAZAR";

    private static AccidentInvestigation investigation(boolean withHealth) {
        AccidentInvestigation.HealthDetail health = withHealth
                ? new AccidentInvestigation.HealthDetail(SECRET, DOCTOR, LocalDate.of(2026, 1, 20))
                : null;
        return AccidentInvestigation.open(ON, UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 1, 19), "SYN evento", AccidentInvestigation.Severity.INJURY,
                "SYN causa raiz", List.of("SYN-ACAO-1"), UUID.randomUUID(), false, List.of(),
                health);
    }

    @Test
    @DisplayName("leitura autorizada com finalidade devolve o detalhe e fica registrada")
    void authorizedReadIsLogged() {
        AccidentInvestigation inv = investigation(true);
        AccidentInvestigation.HealthAccessPolicy policy =
                new AccidentInvestigation.HealthAccessPolicy(Set.of(DOCTOR));
        String detail = inv.readHealthDetail(policy, DOCTOR, "SYN finalidade assistencial",
                Instant.parse("2026-01-21T10:00:00Z"));
        assertThat(detail).isEqualTo(SECRET);
        assertThat(inv.healthAccessLog()).hasSize(1);
        assertThat(inv.healthAccessLog().get(0).accessorId()).isEqualTo(DOCTOR);
    }

    @Test
    @DisplayName("acessante fora da política é negado e nada é registrado")
    void outsiderDenied() {
        AccidentInvestigation inv = investigation(true);
        AccidentInvestigation.HealthAccessPolicy policy =
                new AccidentInvestigation.HealthAccessPolicy(Set.of(DOCTOR));
        assertThatThrownBy(() -> inv.readHealthDetail(policy, OUTSIDER, "SYN curiosidade",
                Instant.parse("2026-01-21T10:00:00Z"))).isInstanceOf(SecurityException.class);
        assertThat(inv.healthAccessLog()).isEmpty();
    }

    @Test
    @DisplayName("finalidade em branco é recusada mesmo para acessante autorizado")
    void blankPurposeDenied() {
        AccidentInvestigation inv = investigation(true);
        AccidentInvestigation.HealthAccessPolicy policy =
                new AccidentInvestigation.HealthAccessPolicy(Set.of(DOCTOR));
        assertThatThrownBy(() -> inv.readHealthDetail(policy, DOCTOR, "  ",
                Instant.parse("2026-01-21T10:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(inv.healthAccessLog()).isEmpty();
    }

    @Test
    @DisplayName("o JSON da investigação nunca carrega o detalhe de saúde")
    void jsonNeverLeaksHealth() {
        AccidentInvestigation inv = investigation(true);
        assertThat(inv.healthDataPresent()).isTrue();
        assertThat(inv.toJson()).doesNotContain(SECRET).contains("\"healthDataPresent\":true");
    }

    @Test
    @DisplayName("sem ação corretiva referenciada a investigação não nasce")
    void correctiveActionRequired() {
        assertThatThrownBy(() -> AccidentInvestigation.open(ON, UUID.randomUUID(), null,
                LocalDate.of(2026, 1, 19), "SYN", AccidentInvestigation.Severity.NEAR_MISS,
                "SYN causa", List.of(), UUID.randomUUID(), false, List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("relação psicossocial exige os fatores declarados")
    void psychosocialCoherence() {
        assertThatThrownBy(() -> AccidentInvestigation.open(ON, UUID.randomUUID(), null,
                LocalDate.of(2026, 1, 19), "SYN", AccidentInvestigation.Severity.NO_INJURY,
                "SYN causa", List.of("SYN-ACAO-1"), UUID.randomUUID(), true, List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("norma desligada: a investigação não nasce")
    void disabledRefused() {
        assertThatThrownBy(() -> AccidentInvestigation.open(Iso45001Activation.disabled(),
                UUID.randomUUID(), null, LocalDate.of(2026, 1, 19), "SYN",
                AccidentInvestigation.Severity.NEAR_MISS, "SYN", List.of("SYN-A"),
                UUID.randomUUID(), false, List.of(), null))
                .isInstanceOf(IllegalStateException.class);
    }
}
