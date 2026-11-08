package br.com.ziipsan.platform.training;

import br.com.ziipsan.platform.implantation.CloneAnonymization;
import br.com.ziipsan.platform.implantation.CloneGate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PreTrainingTenantTest {

    private static final UUID CLONE_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID PROD_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final UUID ASSESSOR = UUID.fromString("00000000-0000-0000-0000-000000000303");
    private static final Instant T0 = Instant.parse("2026-11-01T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-10T10:00:00Z");

    private static CloneGate.CloneRequest cloneRequest(CloneAnonymization.Purpose purpose) {
        return new CloneGate.CloneRequest(PROD_TENANT, true, false, purpose, T1);
    }

    private static CloneGate.ReidentificationAssessment viableAssessment() {
        return new CloneGate.ReidentificationAssessment(PROD_TENANT, T0, ASSESSOR, true,
                "RIPD-TENANT-EXEMPLO-SINTETICO");
    }

    @Test
    @DisplayName("massa sintética marcada, varredura limpa e fiscal desligado: pronto")
    void syntheticMassReady() {
        PreTrainingTenant.Spec spec = new PreTrainingTenant.Spec(CLONE_TENANT,
                PreTrainingTenant.Source.SYNTHETIC_MASS, true, 0, false, null, null);
        assertThat(PreTrainingTenant.ready(spec)).isTrue();
    }

    @Test
    @DisplayName("clone mascarado com RIPD viável e anterior e finalidade TRAINING: pronto")
    void maskedCloneReady() {
        PreTrainingTenant.Spec spec = new PreTrainingTenant.Spec(CLONE_TENANT,
                PreTrainingTenant.Source.MASKED_CLONE, true, 0, false,
                cloneRequest(CloneAnonymization.Purpose.TRAINING), viableAssessment());
        assertThat(PreTrainingTenant.ready(spec)).isTrue();
    }

    @Test
    @DisplayName("clone sem avaliação do RIPD é bloqueado pelo portão da ED-09")
    void cloneWithoutRipdBlocked() {
        PreTrainingTenant.Spec spec = new PreTrainingTenant.Spec(CLONE_TENANT,
                PreTrainingTenant.Source.MASKED_CLONE, true, 0, false,
                cloneRequest(CloneAnonymization.Purpose.TRAINING), null);
        assertThat(PreTrainingTenant.ready(spec)).isFalse();
        assertThat(PreTrainingTenant.blockers(spec))
                .anyMatch(b -> b.contains("RIPD"));
    }

    @Test
    @DisplayName("clone com finalidade HOMOLOGATION não serve de ambiente de treinamento")
    void homologationCloneBlocked() {
        PreTrainingTenant.Spec spec = new PreTrainingTenant.Spec(CLONE_TENANT,
                PreTrainingTenant.Source.MASKED_CLONE, true, 0, false,
                cloneRequest(CloneAnonymization.Purpose.HOMOLOGATION), viableAssessment());
        assertThat(PreTrainingTenant.ready(spec)).isFalse();
        assertThat(PreTrainingTenant.blockers(spec))
                .anyMatch(b -> b.contains("TRAINING"));
    }

    @Test
    @DisplayName("massa sintética com pedido de clone associado é recusada (coerência origem x clone)")
    void syntheticMassWithCloneBlocked() {
        PreTrainingTenant.Spec spec = new PreTrainingTenant.Spec(CLONE_TENANT,
                PreTrainingTenant.Source.SYNTHETIC_MASS, true, 0, false,
                cloneRequest(CloneAnonymization.Purpose.TRAINING), viableAssessment());
        assertThat(PreTrainingTenant.ready(spec)).isFalse();
        assertThat(PreTrainingTenant.blockers(spec))
                .anyMatch(b -> b.contains("massa sintética não tem clone"));
    }

    @Test
    @DisplayName("varredura suja, tenant sem marca de sintético e fiscal ligado bloqueiam")
    void dirtyTenantBlocked() {
        PreTrainingTenant.Spec spec = new PreTrainingTenant.Spec(CLONE_TENANT,
                PreTrainingTenant.Source.SYNTHETIC_MASS, false, 3, true, null, null);
        assertThat(PreTrainingTenant.ready(spec)).isFalse();
        assertThat(PreTrainingTenant.blockers(spec)).hasSize(3);
    }
}
