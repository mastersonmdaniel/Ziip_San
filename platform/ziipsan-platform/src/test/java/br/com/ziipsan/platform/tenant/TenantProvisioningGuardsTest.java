package br.com.ziipsan.platform.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P4.6, correcções R-P46-05/R-P46-07: as guardas do provisionamento que a
 * revisão encontrou sem integração ou permissivas demais, provadas ao
 * nível unitário (as mesmas funções que o caminho de produção executa):
 * DEDICATED no cluster 'default' recusado; isenção do teto condicionada a
 * DEDICATED com cluster próprio; reuso SHARED só com TODOS os ocupantes
 * SHARED do mesmo cluster.
 */
class TenantProvisioningGuardsTest {

    private static String row(UUID id, String cluster, String mode) {
        return id + "|" + cluster + "|" + mode;
    }

    @Test
    @DisplayName("R-P46-05: DEDICATED no cluster 'default' (ou sem cluster) e recusado; com cluster proprio passa")
    void dedicatedOnDefaultClusterIsRefused() {
        assertThatThrownBy(() -> TenantProvisioningService.requireDedicatedOwnCluster(
                "default", IsolationMode.DEDICATED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cluster 'default'");
        assertThatThrownBy(() -> TenantProvisioningService.requireDedicatedOwnCluster(
                null, IsolationMode.DEDICATED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TenantProvisioningService.requireDedicatedOwnCluster(
                "  ", IsolationMode.DEDICATED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> TenantProvisioningService.requireDedicatedOwnCluster(
                "cluster-do-tenant", IsolationMode.DEDICATED))
                .doesNotThrowAnyException();
        // Os demais modos no 'default' nao sao afectados pela guarda.
        assertThatCode(() -> TenantProvisioningService.requireDedicatedOwnCluster(
                "default", IsolationMode.SCHEMA)).doesNotThrowAnyException();
        assertThatCode(() -> TenantProvisioningService.requireDedicatedOwnCluster(
                "default", IsolationMode.SHARED)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("R-P46-05: a isencao do teto so vale para DEDICATED com cluster proprio")
    void ceilingExemptionRequiresDedicatedOwnCluster() {
        assertThat(TenantProvisioningService.ceilingExempt(
                "cluster-do-tenant", IsolationMode.DEDICATED)).isTrue();
        assertThat(TenantProvisioningService.ceilingExempt(
                "default", IsolationMode.DEDICATED)).isFalse();
        assertThat(TenantProvisioningService.ceilingExempt(
                "default", IsolationMode.SCHEMA)).isFalse();
        assertThat(TenantProvisioningService.ceilingExempt(
                "outro-cluster", IsolationMode.SCHEMA)).isFalse();
        assertThat(TenantProvisioningService.ceilingExempt(
                "default", IsolationMode.SHARED)).isFalse();
    }

    @Test
    @DisplayName("R-P46-07: reuso SHARED exige TODOS os ocupantes SHARED do mesmo cluster")
    void sharedReuseRequiresAllOccupantsSharedInSameCluster() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertThat(TenantProvisioningService.canReuseSchemaAsShared(
                List.of(row(a, "default", "SHARED"), row(b, "default", "SHARED")),
                "default")).isTrue();
        // Um ocupante SCHEMA no mesmo schema barra o reuso (schema misto).
        assertThat(TenantProvisioningService.canReuseSchemaAsShared(
                List.of(row(a, "default", "SHARED"), row(b, "default", "SCHEMA")),
                "default")).isFalse();
        // Ocupante SHARED de OUTRO cluster nao autoriza reuso neste.
        assertThat(TenantProvisioningService.canReuseSchemaAsShared(
                List.of(row(a, "cluster-b", "SHARED")), "default")).isFalse();
        // Schema orfao (sem ocupantes registados) nao e reutilizavel.
        assertThat(TenantProvisioningService.canReuseSchemaAsShared(
                List.of(), "default")).isFalse();
        // Linha malformada falha fechado.
        assertThat(TenantProvisioningService.canReuseSchemaAsShared(
                List.of("lixo-sem-formato"), "default")).isFalse();
    }

    @Test
    @DisplayName("R-P46-05: provisionar DEDICATED no 'default' falha ANTES de tocar no banco")
    void provisioningRefusesDedicatedOnDefaultBeforeAnyDbWork() {
        // DataSources nulos de proposito: se a guarda nao disparasse antes
        // de qualquer acesso ao banco, o teste rebentaria com NPE e nao
        // com a recusa nomeada.
        var service = new TenantProvisioningService(null, null, null, null);
        var request = TenantProvisioningService.TenantProvisioningRequest.builder()
                .tenantId(UUID.randomUUID())
                .schemaName("tenant_dedicado_itest")
                .displayName("Dedicado Indevido")
                .clusterId("default")
                .isolationMode("DEDICATED")
                .build();
        assertThatThrownBy(() -> service.provisionTenant(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cluster 'default'");
    }
}
