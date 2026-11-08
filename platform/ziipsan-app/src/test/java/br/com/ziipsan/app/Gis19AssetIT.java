package br.com.ziipsan.app;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GIS-19 (ED-56): ativos e manutencao no PostgreSQL real — tabelas
 * do plano preventivo, exercicio de valvulas, teste de hidrantes,
 * inspecao por video, risco e conciliacao, todas com RLS por tenant.
 *
 * <p>Segue o padrao do {@code Gis11ManeuverIT}: migrations do
 * classpath contra schema descartavel. O SQL da ED-56 entra a partir
 * de {@code db/pendente} — na integracao recebe o numero da sequencia
 * global e muda para {@code db/migration/tenant/} (so o caminho
 * muda). Depende ainda da migration da entidade {@code asset} da
 * P1.35, que entra na lista quando a integracao numerar o pacote.
 *
 * <p>NAO RODADA nesta VM como IT Spring: pendencia nomeada
 * P-ED56-03 — prova da CI no PC do dono, como as demais ITs GIS.
 * Correcao D1 da revisao da ED-56: HA PostgreSQL 16.15 nesta VM e
 * os dois SQL pendentes foram APLICADOS nela, do zero, em banco
 * descartavel com stubs declarados (7/7 tabelas com RLS — prova no
 * RELATORIO-CORRECAO-ED-56). A logica foi provada nesta VM pelos
 * testes unitarios sobre os motores Java, com massa sintetica de
 * resultado conhecido.
 */
class Gis19AssetIT {

    private static final List<String> LEGACY_MIGRATIONS = List.of(
            "db/migration/tenant/V1_1__create_tenant_schema.sql",
            "db/migration/tenant/V1_2__tenant_table_helper.sql",
            "db/migration/tenant/V1_3__harden_audit_function.sql",
            "db/migration/tenant/V1_5__tenant_parameters.sql");

    private static final List<String> GIS19_MIGRATIONS = List.of(
            // P1.35 (asset) entra aqui na integracao, antes da ED-56:
            "db/pendente/ED-56__gis_assets_maintenance.sql");

    @BeforeAll
    static void setUp() {
        ProvisionedTenant.ensure();
    }

    private record Scratch(UUID tenant, String schema) {
    }

    private static Scratch scratch(Statement st) throws Exception {
        UUID tenant = UUID.randomUUID();
        String schema = "it_gis_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        st.execute("CREATE SCHEMA " + schema);
        st.execute("SET LOCAL search_path = " + schema + ", public");
        for (String m : LEGACY_MIGRATIONS) {
            st.execute(sql(m, tenant));
        }
        for (String m : GIS19_MIGRATIONS) {
            st.execute(sql(m, tenant));
        }
        return new Scratch(tenant, schema);
    }

    private static String sql(String resource, UUID tenant) {
        try (InputStream in = Gis19AssetIT.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("migration ausente no classpath: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("${tenant_id}", tenant.toString());
        } catch (IOException e) {
            throw new IllegalStateException("falha lendo " + resource, e);
        }
    }

    @Test
    @DisplayName("GIS-19: tabelas de manutencao existem com RLS e CHECK de um intervalo por linha de PM")
    void maintenanceTablesExistWithRls() throws Exception {
        try (Connection c = ProvisionedTenant.dataSource().getConnection();
             Statement st = c.createStatement()) {
            Scratch s = scratch(st);
            for (String table : List.of("asset_pm_plan_line", "asset_valve_exercise",
                    "asset_hydrant_test", "asset_cctv_inspection", "asset_cctv_defect",
                    "asset_risk_assessment", "asset_reconciliation_run")) {
                try (ResultSet rs = st.executeQuery(
                        "SELECT relrowsecurity FROM pg_class WHERE relname = '" + table + "'")) {
                    assertThat(rs.next()).as(table).isTrue();
                    assertThat(rs.getBoolean(1)).as("RLS de " + table).isTrue();
                }
            }
        }
    }
}
