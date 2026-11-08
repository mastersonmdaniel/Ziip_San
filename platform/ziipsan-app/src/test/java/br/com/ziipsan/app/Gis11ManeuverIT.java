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
 * GIS-11 (ED-54): manobras no PostgreSQL/PostGIS real — tabelas do
 * plano/setor/período, funções de traçado sobre a topologia
 * persistida e o polígono de roteamento (ST_ConvexHull).
 *
 * <p>Segue o padrão do {@code Gis13DrainIT}: migrations do classpath
 * contra schema descartável. O SQL da ED-54 entra DEPOIS da V13_12 a
 * partir de {@code db/pendente} — na integração ele recebe o número
 * da sequência global e muda para {@code db/migration/tenant/} (só o
 * caminho muda; o conteúdo é o mesmo).
 *
 * <p>NÃO RODADA nesta VM (sem PostgreSQL/PostGIS aqui): pendência
 * nomeada P-ED54-02 — prova da CI no PC do dono, como as demais ITs
 * GIS. A lógica que esta IT confere no banco (traçado/isolamento)
 * foi provada nesta VM pelos testes unitários executados sobre os
 * motores Java (ManeuverGraph/IsolationEngine), com massa sintética
 * de resultado conhecido; a divergência banco × Java em massa
 * sintética é item desta IT na CI.
 */
class Gis11ManeuverIT {

    private static final List<String> LEGACY_MIGRATIONS = List.of(
            "db/migration/tenant/V1_1__create_tenant_schema.sql",
            "db/migration/tenant/V1_2__tenant_table_helper.sql",
            "db/migration/tenant/V1_3__harden_audit_function.sql",
            "db/migration/tenant/V1_5__tenant_parameters.sql",
            "db/migration/tenant/V2_2__create_losses_schema.sql",
            "db/migration/tenant/V2_3__create_gis_schema.sql");

    private static final List<String> GIS_MIGRATIONS = List.of(
            "db/migration/tenant/V13_1__gis_classification_catalog.sql",
            "db/migration/tenant/V13_2__gis_features_history_migration.sql",
            "db/migration/tenant/V13_3__gis_responsible_party_redaction.sql",
            "db/migration/tenant/V13_4__gis_catalog_approval.sql",
            "db/migration/tenant/V13_5__gis_check_feature_qualified.sql",
            "db/migration/tenant/V13_6__gis_check_feature_search_path.sql",
            "db/migration/tenant/V13_7__gis_write_history_explicit_columns.sql",
            "db/migration/tenant/V13_8__gis_topology_validation.sql",
            "db/migration/tenant/V13_9__gis_change_sets.sql",
            "db/migration/tenant/V13_10__gis_service_connection_link.sql",
            "db/migration/tenant/V13_11__gis_import_staging.sql",
            "db/migration/tenant/V13_12__gis_publication_tiles_styles.sql",
            // ED-54 (pendente de numeração na entrega; ver javadoc):
            "db/pendente/ED-54__gis_maneuver.sql");

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
        for (String m : GIS_MIGRATIONS) {
            st.execute(sql(m, tenant));
        }
        return new Scratch(tenant, schema);
    }

    private static String sql(String resource, UUID tenant) {
        try (InputStream in = Gis11ManeuverIT.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("migration ausente no classpath: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("${tenant_id}", tenant.toString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("tabelas da GIS-11 e funcoes de tracado/poligono existem apos o SQL pendente")
    void estruturaCarregada() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs().getConnection();
             Statement st = c.createStatement()) {
            Scratch s = scratch(st);
            ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '"
                            + s.schema() + "' AND table_name IN ('gis_maneuver_sector',"
                            + " 'gis_maneuver_plan', 'gis_maneuver_plan_valve',"
                            + " 'gis_maneuver_outage_period', 'gis_maneuver_affected_units')");
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(5);
            rs = st.executeQuery(
                    "SELECT COUNT(*) FROM pg_proc WHERE proname IN"
                            + " ('gis_trace_downstream', 'gis_trace_upstream', 'gis_routing_polygon')");
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(3);
        }
    }

    @Test
    @DisplayName("tracado em rede vazia devolve so o no semente; poligono de conjunto vazio e nulo")
    void tracadoRedeVazia() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs().getConnection();
             Statement st = c.createStatement()) {
            Scratch s = scratch(st);
            UUID node = UUID.randomUUID();
            ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM gis_trace_downstream('"
                    + s.tenant() + "', '" + node + "')");
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
            rs = st.executeQuery("SELECT gis_routing_polygon('" + s.tenant()
                    + "', ARRAY[]::uuid[]) IS NULL");
            rs.next();
            assertThat(rs.getBoolean(1)).isTrue();
        }
    }

    @Test
    @DisplayName("setor de manobra pre-calculado grava e le de volta")
    void setorRoundtrip() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs().getConnection();
             Statement st = c.createStatement()) {
            Scratch s = scratch(st);
            st.execute("INSERT INTO gis_maneuver_sector (tenant_id, sector_code, node_ids,"
                    + " total_economies, has_source, computed_at) VALUES ('" + s.tenant()
                    + "', 'MS-01', '[]'::jsonb, 0, false, '2026-11-08 00:00:00')");
            ResultSet rs = st.executeQuery("SELECT sector_code FROM gis_maneuver_sector");
            rs.next();
            assertThat(rs.getString(1)).isEqualTo("MS-01");
        }
    }
}
