package br.com.ziipsan.reporting.panels;

import br.com.ziipsan.reporting.bi.ReplicaOnlyGuard;
import br.com.ziipsan.reporting.bi.SupersetDataSource;
import br.com.ziipsan.reporting.bi.TenantBiRole;
import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ED-49: a fronteira OPE-22 (console em tempo quase real fora do BI, na
 * ED-93) e a guarda de replica REAL da ED-47 sobre as fontes dos paineis.
 */
class RealtimeAndSupersetGuardTest {

    @Test
    void realtimeRequestToAnyStandardPanelIsRefusedNamingEd93() {
        for (PanelKind kind : PanelKind.values()) {
            assertThat(RealtimeConsoleBoundary.isRealtimeConsole(kind)).isFalse();
            assertThatThrownBy(() -> RealtimeConsoleBoundary.requirePeriodicOnly(kind, true))
                    .isInstanceOf(IndicatorValidationException.class)
                    .hasMessageContaining("OPE22_REALTIME_CONSOLE_OUT_OF_BI")
                    .hasMessageContaining("ED-93")
                    .hasMessageContaining("OPE-22");
        }
        // Pedido periodico (o unico do BI) passa sem efeito:
        RealtimeConsoleBoundary.requirePeriodicOnly(PanelKind.OPERACIONAL, false);
    }

    @Test
    void manifestListsTheSevenPanelsOnReplicaReadingTheP341Registry() {
        String json = PanelSupersetManifest.manifestJson();
        for (PanelDefinition definition : StandardPanelCatalog.all()) {
            assertThat(json).contains(definition.supersetSlug());
        }
        assertThat(json).contains("READ_REPLICA").contains("P3.41 (JOR-19)")
                .contains("FORA_DO_BI_ED-93 (OPE-22)");
    }

    @Test
    void replicaGuardFromEd47AcceptsReplicaAndRefusesPrimaryForPanelSources() {
        var primary = new SupersetDataSource.Endpoint("db-primary", 5432, "ziipsan");
        ReplicaOnlyGuard guard = new ReplicaOnlyGuard(primary);
        var replica = new SupersetDataSource("painel-replica", PanelFixtures.TENANT,
                TenantBiRole.roleNameFor(PanelFixtures.TENANT),
                new SupersetDataSource.Endpoint("db-replica", 5432, "ziipsan"),
                SupersetDataSource.Kind.READ_REPLICA);
        PanelSupersetManifest.requireReplicaOnly(guard, List.of(replica));

        var onPrimary = new SupersetDataSource("painel-primario", PanelFixtures.TENANT,
                TenantBiRole.roleNameFor(PanelFixtures.TENANT), primary,
                SupersetDataSource.Kind.READ_REPLICA);
        assertThatThrownBy(() -> PanelSupersetManifest.requireReplicaOnly(guard, List.of(onPrimary)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bi-datasource-on-primary");
        var wrongKind = new SupersetDataSource("painel-outro", PanelFixtures.TENANT,
                TenantBiRole.roleNameFor(PanelFixtures.TENANT),
                new SupersetDataSource.Endpoint("db-replica", 5432, "ziipsan"),
                SupersetDataSource.Kind.OTHER);
        assertThatThrownBy(() -> PanelSupersetManifest.requireReplicaOnly(guard, List.of(wrongKind)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bi-datasource-not-replica");
    }
}
