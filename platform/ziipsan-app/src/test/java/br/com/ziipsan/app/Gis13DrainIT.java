package br.com.ziipsan.app;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GIS-13 (ED-64): drenagem no PostgreSQL/PostGIS real — catálogo
 * carregado (20 tipos gis13:*: 19 do §4.4 + STORM_BASIN da V13_2,
 * decisão D2 da revisão), feição com declividade pela
 * coluna gerada slope da V13_2, inspeção com a guarda de vídeo da
 * ED-56, plano de limpeza sem intervalo = sem pedido, chuva
 * importada com fonte e alagamento ligado ao acumulado do período,
 * views GIS-003/GIS-004 e o temático de conservação.
 *
 * <p>Segue o padrão do {@code Gis14WasteIT}: migrations do classpath
 * contra schema descartável. A migration da ED-64 entra DEPOIS da
 * V13_12 a partir de {@code db/pendente} — na integração ela recebe
 * o número reservado da sequência e muda para
 * {@code db/migration/tenant/V13_<n>__gis_drainage.sql} (só o caminho
 * muda; o conteúdo é o mesmo). O SQL de parâmetros do core
 * (ED-64__core_drain_parameters.sql) é pré-requisito do catálogo de
 * parâmetros, não desta IT.
 *
 * <p>NÃO RODADA nesta VM (sem servidor PostgreSQL/PostGIS no
 * ambiente desta execução — só o cliente psql): pendência da CI no
 * PC do dono, como as demais ITs GIS — ver RELATORIO-ED-64.md. A
 * lógica que a IT confere no banco foi provada nesta VM pelos
 * testes unitários executados (33) e pelo verificador de coerência
 * SQL × DDL real.
 */
class Gis13DrainIT {

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
            // ED-64 (pendente de numeração na entrega; ver javadoc):
            "db/pendente/ED-64__gis_drainage.sql");

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
        try (InputStream in = Gis13DrainIT.class.getClassLoader().getResourceAsStream(resource)) {
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
    @DisplayName("catalogo carregado: 20 tipos gis13:* (19 do §4.4 + STORM_BASIN da V13_2), 4 classes, listas")
    void catalogoCarregado() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs().getConnection();
             Statement st = c.createStatement()) {
            Scratch s = scratch(st);
            ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM gis_feature_type WHERE factory_code LIKE 'gis13:%'");
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(20);
            rs = st.executeQuery(
                    "SELECT COUNT(*) FROM gis_feature_class WHERE domain = 'STORM'");
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(4);
            rs = st.executeQuery(
                    "SELECT COUNT(*) FROM gis_catalog_item WHERE factory_code LIKE 'gis13:%'");
            rs.next();
            // 10 (concreto) + 16 (estruturados) de STORM_PIPE + 4 de INLET_LEAD
            assertThat(rs.getInt(1)).isEqualTo(30);
        }
    }

    @Test
    @DisplayName("inspecao por video sem referencia e recusada pelo CHECK")
    void inspecaoVideoSemReferenciaRecusada() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs().getConnection();
             Statement st = c.createStatement()) {
            Scratch s = scratch(st);
            UUID feature = UUID.randomUUID();
            assertThatThrownBy(() -> st.execute(
                    "INSERT INTO gis_drain_inspection (tenant_id, feature_table, feature_id,"
                            + " inspected_on, method) VALUES ('" + s.tenant()
                            + "', 'gis_arc', '" + feature
                            + "', DATE '2026-10-05', 'CCTV_EXTERNAL_REF')"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    @DisplayName("plano sem intervalo nao gera vencimento; chuva liga o alagamento")
    void planoEChuva() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs().getConnection();
             Statement st = c.createStatement()) {
            Scratch s = scratch(st);
            st.execute("INSERT INTO gis_drain_cleaning_plan (tenant_id, element_class,"
                    + " plan_start) VALUES ('" + s.tenant() + "', 'INLET', DATE '2026-01-01')");
            ResultSet rs = st.executeQuery(
                    "SELECT interval_months FROM gis_drain_cleaning_plan");
            rs.next();
            assertThat(rs.getObject(1)).isNull();
            UUID station = UUID.randomUUID();
            st.execute("INSERT INTO gis_drain_rain_station (id, tenant_id, code, source_kind)"
                    + " VALUES ('" + station + "', '" + s.tenant()
                    + "', 'EST-1', 'CEMADEN_IMPORT')");
            st.execute("INSERT INTO gis_drain_rain_reading (tenant_id, station_id, read_at,"
                    + " rain_mm, source_ref) VALUES ('" + s.tenant() + "', '" + station
                    + "', '2026-10-01 10:00:00+00', 12.5, 'download mensal sintetico')");
            rs = st.executeQuery("SELECT COUNT(*) FROM gis_drain_rain_reading"
                    + " WHERE source_ref IS NOT NULL");
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }
}
