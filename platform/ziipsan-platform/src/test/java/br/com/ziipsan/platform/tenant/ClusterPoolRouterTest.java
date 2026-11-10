package br.com.ziipsan.platform.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P4.6, item 4: roteamento de pools por cluster e por papel sobre a P2.2.
 * URLs mortas de proposito: os pools nascem com minimumIdle=0 (fabrica da
 * P2.2), logo construir o roteador nao abre conexao — como no
 * RoleDataSourceFactoryTest.
 */
class ClusterPoolRouterTest {

    private static final String DEAD = "jdbc:postgresql://127.0.0.1:59999/ziipsan";

    private static Map<PoolRole, RoleDataSourceFactory.RoleCredentials> creds() {
        return Map.of(
                PoolRole.API, new RoleDataSourceFactory.RoleCredentials("ziipsan_app", "sintetica"),
                PoolRole.BATCH, new RoleDataSourceFactory.RoleCredentials("ziipsan_batch", "sintetica"),
                PoolRole.INGEST, new RoleDataSourceFactory.RoleCredentials("ziipsan_ingest", "sintetica"));
    }

    private static ClusterPoolRouter router() {
        return ClusterPoolRouter.of(List.of(
                new ClusterPoolRouter.ClusterEndpoint("default", DEAD, creds()),
                new ClusterPoolRouter.ClusterEndpoint("dedicado-t1", DEAD, creds())));
    }

    @Test
    @DisplayName("route: tenant DEDICATED recebe o pool do SEU cluster; SCHEMA/SHARED o do cluster deles")
    void routesByClusterAndRole() {
        try (ClusterPoolRouter router = router()) {
            var dedicated = new TenantResolution(UUID.randomUUID(), "tenant_big", "dedicado-t1",
                    IsolationMode.DEDICATED, "ACTIVE");
            var shared = new TenantResolution(UUID.randomUUID(), "tenant_shared", "default",
                    IsolationMode.SHARED, "ACTIVE");

            assertThat(router.route(dedicated, PoolRole.BATCH).getPoolName()).isEqualTo("ziipsan-batch");
            assertThat(router.route(dedicated, PoolRole.BATCH))
                    .isSameAs(router.pool("dedicado-t1", PoolRole.BATCH));
            assertThat(router.route(dedicated, PoolRole.BATCH))
                    .isNotSameAs(router.pool("default", PoolRole.BATCH));
            assertThat(router.route(shared, PoolRole.API))
                    .isSameAs(router.pool("default", PoolRole.API));
            assertThat(router.route(shared, PoolRole.API))
                    .isNotSameAs(router.route(shared, PoolRole.INGEST));
            assertThat(router.clusterIds()).containsExactlyInAnyOrder("default", "dedicado-t1");
        }
    }

    @Test
    @DisplayName("pool: cluster desconhecido falha nomeando o cluster — nunca desvia para o default")
    void unknownClusterFailsLoudly() {
        try (ClusterPoolRouter router = router()) {
            assertThatThrownBy(() -> router.pool("cluster-fantasma", PoolRole.API))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cluster-fantasma");
        }
    }

    @Test
    @DisplayName("route: tenant nao ACTIVE nao recebe pool")
    void inactiveTenantGetsNoPool() {
        try (ClusterPoolRouter router = router()) {
            var suspended = new TenantResolution(UUID.randomUUID(), "t", "default",
                    IsolationMode.SCHEMA, "SUSPENDED");
            assertThatThrownBy(() -> router.route(suspended, PoolRole.API))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SUSPENDED");
        }
    }

    @Test
    @DisplayName("of: endpoint sem credencial de um papel e recusado antes de criar pools")
    void endpointMissingRoleCredentialIsRefused() {
        var incomplete = Map.of(PoolRole.API,
                new RoleDataSourceFactory.RoleCredentials("ziipsan_app", "sintetica"));
        assertThatThrownBy(() -> new ClusterPoolRouter.ClusterEndpoint("c1", DEAD, incomplete))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BATCH");
    }
}
