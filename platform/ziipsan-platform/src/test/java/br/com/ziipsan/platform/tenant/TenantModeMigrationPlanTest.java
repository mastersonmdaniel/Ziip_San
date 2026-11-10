package br.com.ziipsan.platform.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ziipsan.platform.tenant.TenantModeMigrationPlan.Step;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** P4.6, item 7: plano da mudanca de modo — sequencia fechada, variacoes por origem, recusas. */
class TenantModeMigrationPlanTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Test
    @DisplayName("SHARED -> SCHEMA: troca do catalogo e tardia; fonte apaga-se por tenant_id + shredding")
    void sharedToSchemaPlan() {
        var plan = TenantModeMigrationPlan.plan(TENANT, IsolationMode.SHARED, IsolationMode.SCHEMA,
                "tenant_shared", "default", "tenant_proprio", "default");
        assertThat(plan.steps()).containsExactly(
                Step.VALIDATE_TARGET, Step.FREEZE_WRITES, Step.EXPORT_SOURCE_BY_TENANT_ID,
                Step.PROVISION_TARGET, Step.APPLY_MIGRATIONS_BY_ROLLOUT,
                Step.COPY_DATA_AND_VERIFY_COUNTS, Step.VERIFY_RLS_ISOLATION,
                Step.SWITCH_CATALOG_RESOLUTION, Step.ERASE_SOURCE_BY_TENANT_ID_AND_SHRED_KEY,
                Step.UNFREEZE_WRITES, Step.RECORD_EVIDENCE);
        assertThat(plan.steps().indexOf(Step.SWITCH_CATALOG_RESOLUTION))
                .isGreaterThan(plan.steps().indexOf(Step.VERIFY_RLS_ISOLATION));
    }

    @Test
    @DisplayName("SCHEMA -> DEDICATED: fonte descomissiona-se por schema; destino exige cluster proprio")
    void schemaToDedicatedPlan() {
        var plan = TenantModeMigrationPlan.plan(TENANT, IsolationMode.SCHEMA, IsolationMode.DEDICATED,
                "tenant_proprio", "default", "tenant_proprio", "dedicado-t1");
        assertThat(plan.steps()).contains(Step.DECOMMISSION_SOURCE_SCHEMA);
        assertThat(plan.steps()).doesNotContain(Step.ERASE_SOURCE_BY_TENANT_ID_AND_SHRED_KEY);
        assertThat(plan.targetClusterId()).isEqualTo("dedicado-t1");

        assertThatThrownBy(() -> TenantModeMigrationPlan.plan(TENANT,
                IsolationMode.SCHEMA, IsolationMode.DEDICATED,
                "tenant_proprio", "default", "tenant_proprio", "default"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cluster proprio");
    }

    @Test
    @DisplayName("mesmo modo e saida de SHARED para o mesmo schema sao recusados")
    void invalidTransitionsAreRefused() {
        assertThatThrownBy(() -> TenantModeMigrationPlan.plan(TENANT,
                IsolationMode.SCHEMA, IsolationMode.SCHEMA,
                "a", "default", "b", "default"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sem efeito");
        assertThatThrownBy(() -> TenantModeMigrationPlan.plan(TENANT,
                IsolationMode.SHARED, IsolationMode.SCHEMA,
                "tenant_shared", "default", "tenant_shared", "default"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("distinto do compartilhado");
    }

    @Test
    @DisplayName("ClusterRolloutPlanner (item 5): agrupa por cluster, preserva ordem, exclui inactivos")
    void rolloutGroupingByCluster() {
        var a = new TenantResolution(UUID.randomUUID(), "s_a", "default", IsolationMode.SCHEMA, "ACTIVE");
        var b = new TenantResolution(UUID.randomUUID(), "s_b", "dedicado-t1", IsolationMode.DEDICATED, "ACTIVE");
        var c = new TenantResolution(UUID.randomUUID(), "s_c", "default", IsolationMode.SHARED, "SUSPENDED");
        var d = new TenantResolution(UUID.randomUUID(), "s_d", "default", IsolationMode.SHARED, "ACTIVE");

        var grouped = ClusterRolloutPlanner.byCluster(List.of(a, b, c, d));

        assertThat(grouped.keySet()).containsExactlyInAnyOrder("default", "dedicado-t1");
        assertThat(grouped.get("default")).extracting(TenantMigrationRollout.Tenant::schemaName)
                .containsExactly("s_a", "s_d");
        assertThat(grouped.get("dedicado-t1")).extracting(TenantMigrationRollout.Tenant::schemaName)
                .containsExactly("s_b");
    }
}
