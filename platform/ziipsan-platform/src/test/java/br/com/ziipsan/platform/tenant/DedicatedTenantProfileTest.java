package br.com.ziipsan.platform.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** P4.6, item 2: perfil do tenant em banco dedicado — cluster, workers e janela proprios. */
class DedicatedTenantProfileTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Test
    @DisplayName("perfil valido: casa com a resolucao DEDICATED do seu cluster")
    void validProfileMatchesResolution() {
        var profile = new DedicatedTenantProfile(TENANT, "dedicado-t1", 4, 2,
                LocalTime.of(1, 0), LocalTime.of(5, 0));
        var resolution = new TenantResolution(TENANT, "tenant_big", "dedicado-t1",
                IsolationMode.DEDICATED, "ACTIVE");
        assertThat(profile.matches(resolution)).isTrue();
        assertThat(profile.matches(new TenantResolution(TENANT, "tenant_big", "dedicado-t1",
                IsolationMode.SCHEMA, "ACTIVE"))).isFalse();
        assertThat(profile.inBatchWindow(LocalTime.of(3, 30))).isTrue();
        assertThat(profile.inBatchWindow(LocalTime.of(12, 0))).isFalse();
    }

    @Test
    @DisplayName("janela que cruza a meia-noite (22:00-02:00) contem 23:30 e 01:00, nao 12:00")
    void windowCrossingMidnight() {
        var profile = new DedicatedTenantProfile(TENANT, "dedicado-t1", 2, 1,
                LocalTime.of(22, 0), LocalTime.of(2, 0));
        assertThat(profile.inBatchWindow(LocalTime.of(23, 30))).isTrue();
        assertThat(profile.inBatchWindow(LocalTime.of(1, 0))).isTrue();
        assertThat(profile.inBatchWindow(LocalTime.of(12, 0))).isFalse();
    }

    @Test
    @DisplayName("cluster 'default' e recusado: dedicado no cluster compartilhado e contradicao")
    void defaultClusterIsRefused() {
        assertThatThrownBy(() -> new DedicatedTenantProfile(TENANT, "default", 2, 1,
                LocalTime.of(1, 0), LocalTime.of(5, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("default");
    }

    @Test
    @DisplayName("janela de comprimento zero e recusada")
    void zeroLengthWindowIsRefused() {
        assertThatThrownBy(() -> new DedicatedTenantProfile(TENANT, "dedicado-t1", 2, 1,
                LocalTime.of(1, 0), LocalTime.of(1, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("comprimento zero");
    }
}
