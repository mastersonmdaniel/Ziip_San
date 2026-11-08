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
 * GIS-14 (ED-65): resíduos sólidos no PostgreSQL real — catálogo do
 * §4.5 carregado, setor e vínculo por ST_Contains (premissa das
 * decisões LINKED/UNLINKED/REVIEW da regra Java), descarte irregular
 * com foto e pedido de limpeza (FKs), grafo de logradouros com
 * projeção e distância pgRouting (ou a recusa explícita sem a
 * extensão), rota e paradas.
 *
 * <p>Segue o padrão do {@code Gis06PublicationIT}: migrations do
 * classpath contra schema descartável. A migration da ED-65 entra
 * DEPOIS da V13_12 a partir de {@code db/pendente} — na integração
 * ela recebe o número reservado da sequência e muda para
 * {@code db/migration/tenant/V13_<n>__gis_solid_waste.sql} (só o
 * caminho muda; o conteúdo é o mesmo).
 *
 * <p>NÃO RODADA nesta VM (sem PostgreSQL/PostGIS/pgRouting no
 * ambiente desta execução): pendência da CI no PC do dono, como as
 * demais ITs GIS — ver RELATORIO-ED-65.md. A lógica que a IT confere
 * no banco foi provada nesta VM pelos testes unitários executados
 * (26) e pelo verificador de coerência SQL x DDL real (72 checagens).
 */
class Gis14WasteIT {

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
            // ED-65 (pendente de numeração na entrega; ver javadoc):
            "db/pendente/ED-65__gis_solid_waste.sql");

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
            st.execute(migrationSql(m, tenant));
        }
        for (String m : GIS_MIGRATIONS) {
            st.execute(migrationSql(m, tenant));
        }
        st.execute("INSERT INTO public.tenant_catalog (id, schema_name, name) VALUES ('"
                + tenant + "', '" + schema + "', 'Tenant descartável GIS-14')"
                + " ON CONFLICT (id) DO NOTHING");
        st.execute("SELECT set_config('app.current_tenant', '" + tenant + "', true)");
        return new Scratch(tenant, schema);
    }

    private static String migrationSql(String path, UUID tenant) throws IOException {
        try (InputStream in = Gis14WasteIT.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as("migration %s no classpath", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("${tenant_id}", tenant.toString());
        }
    }

    private static int count(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static String typeId(Statement st, String factoryCode) throws SQLException {
        try (ResultSet rs = st.executeQuery(
                "SELECT id FROM gis_feature_type WHERE factory_code = '" + factoryCode + "'")) {
            rs.next();
            return rs.getString(1);
        }
    }

    /** Setor quadrado em torno de (-48.70, -26.20), 0.02 de lado. */
    private static UUID sector(Statement st, UUID tenant, String code,
                               double lon, double lat, double half) throws SQLException {
        UUID id = UUID.randomUUID();
        String poly = "POLYGON((" + (lon - half) + " " + (lat - half) + ","
                + (lon + half) + " " + (lat - half) + ","
                + (lon + half) + " " + (lat + half) + ","
                + (lon - half) + " " + (lat + half) + ","
                + (lon - half) + " " + (lat - half) + "))";
        st.execute("INSERT INTO gis_zone (id, tenant_id, code, feature_type_id,"
                + " lifecycle_status, capture_method, attributes, zone_type, derivation, geom)"
                + " VALUES ('" + id + "', '" + tenant + "', '" + code + "', '"
                + typeId(st, "gis14:COLLECTION_SECTOR")
                + "', 'IN_SERVICE', 'DESIGN',"
                + " '{\"collection_kind\":\"CONVENTIONAL\",\"collection_days\":[\"MON\",\"THU\"],"
                + "\"shift\":\"MORNING\",\"executor\":\"Teste\"}'::jsonb,"
                + " 'COLLECTION_SECTOR', 'DRAWN', ST_Multi(ST_GeomFromText('" + poly + "', 4674)))");
        return id;
    }

    @Test
    @DisplayName("GIS-14: catalogo do §4.5, listas e camadas SG-1340 carregados")
    void catalogLoaded() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
            scratch(st);
            assertThat(count(st, "SELECT count(*) FROM gis_feature_type"
                    + " WHERE factory_code LIKE 'gis14:%'")).isEqualTo(13);
            assertThat(count(st, "SELECT count(*) FROM gis_feature_class"
                    + " WHERE code IN ('WASTE_POINT','WASTE_FACILITY','COLLECTION_SECTOR')"))
                    .isEqualTo(3);
            assertThat(count(st, "SELECT count(*) FROM gis_value_list"
                    + " WHERE list_code IN ('waste_fraction','collection_kind','dump_kind',"
                    + "'dump_status','vehicle_kind')")).isEqualTo(26);
            assertThat(count(st, "SELECT count(*) FROM gis_cad_layer_map m"
                    + " JOIN gis_import_profile p ON p.id = m.profile_id"
                    + " WHERE p.key = 'GENERICO' AND m.feature_type_code LIKE 'gis14:%'"))
                    .isEqualTo(13);
            }
            c.rollback();
        }
    }

    @Test
    @DisplayName("GIS-14: ST_Contains da premissa do vinculo imovel-setor (1/0/2 setores)")
    void propertySectorContainment() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
            Scratch sc = scratch(st);
            sector(st, sc.tenant(), "SET-1", -48.70, -26.20, 0.01);
            String inside = "SELECT count(*) FROM gis_zone WHERE zone_type = 'COLLECTION_SECTOR'"
                    + " AND lifecycle_status = 'IN_SERVICE' AND ST_Contains(geom,"
                    + " ST_GeomFromText('POINT(-48.70 -26.20)', 4674))";
            assertThat(count(st, inside)).isEqualTo(1);
            String outside = inside.replace("-48.70 -26.20", "-48.60 -26.10");
            assertThat(count(st, outside)).isEqualTo(0);
            sector(st, sc.tenant(), "SET-2", -48.70, -26.20, 0.02);
            assertThat(count(st, inside)).isEqualTo(2);
            }
            c.rollback();
        }
    }

    @Test
    @DisplayName("GIS-14: descarte irregular com foto aceita pedido de limpeza; sem ponto, FK recusa")
    void dumpPhotoAndCleanup() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
            Scratch sc = scratch(st);
            UUID poi = UUID.randomUUID();
            st.execute("INSERT INTO gis_poi (id, tenant_id, code, feature_type_id,"
                    + " lifecycle_status, capture_method, attributes, geom) VALUES ('"
                    + poi + "', '" + sc.tenant() + "', 'DESC-1', '"
                    + typeId(st, "gis14:ILLEGAL_DUMP")
                    + "', 'IN_SERVICE', 'FIELD',"
                    + " '{\"identified_on\":\"2026-11-01\",\"dump_kind\":\"CONSTRUCTION\","
                    + "\"estimated_volume_m3\":2.5,\"dump_status\":\"ACTIVE\"}'::jsonb,"
                    + " ST_GeomFromText('POINT(-48.70 -26.20)', 4674))");
            st.execute("INSERT INTO gis_attachment (id, tenant_id, target_feature_table,"
                    + " target_feature_id, attachment_type, document_id) VALUES ('"
                    + UUID.randomUUID() + "', '" + sc.tenant() + "', 'gis_poi', '" + poi
                    + "', 'PHOTO', '" + UUID.randomUUID() + "')");
            st.execute("INSERT INTO gis_waste_cleanup_request (id, tenant_id, dump_poi_id,"
                    + " status, requested_on) VALUES ('" + UUID.randomUUID() + "', '"
                    + sc.tenant() + "', '" + poi + "', 'REQUESTED', CURRENT_DATE)");
            assertThat(count(st, "SELECT count(*) FROM gis_waste_cleanup_request")).isEqualTo(1);
            assertThatThrownBy(() -> st.execute(
                    "INSERT INTO gis_waste_cleanup_request (id, tenant_id, dump_poi_id,"
                            + " status, requested_on) VALUES ('" + UUID.randomUUID() + "', '"
                            + sc.tenant() + "', '" + UUID.randomUUID()
                            + "', 'REQUESTED', CURRENT_DATE)"))
                    .isInstanceOf(SQLException.class);
            }
            c.rollback();
        }
    }

    @Test
    @DisplayName("GIS-14: grafo projetado e distancia pgRouting (400 m) ou recusa explicita sem a extensao")
    void streetGraphAndPgRouting() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
            Scratch sc = scratch(st);
            // Mesmo grafo de resultado conhecido dos testes unitários.
            double[][] pts = {{-48.70, -26.20}, {-48.69, -26.20}, {-48.68, -26.20},
                    {-48.67, -26.20}, {-48.66, -26.20}};
            long[] pgr = new long[5];
            for (int i = 0; i < 5; i++) {
                try (ResultSet rs = st.executeQuery(
                        "INSERT INTO gis_waste_street_vertex (tenant_id, geom) VALUES ('"
                                + sc.tenant() + "', ST_GeomFromText('POINT("
                                + pts[i][0] + " " + pts[i][1] + ")', 4674)) RETURNING pgr_id")) {
                    rs.next();
                    pgr[i] = rs.getLong(1);
                }
            }
            long[][] edges = {{0, 1, 100, 100}, {1, 2, 100, 100}, {0, 2, 500, 500},
                    {2, 3, 100, 100}, {3, 4, 100, 100}, {1, 4, 1000, 1000}, {4, 0, 100, -1}};
            for (long[] e : edges) {
                st.execute("INSERT INTO gis_waste_street_edge (tenant_id, source_pgr, target_pgr,"
                        + " cost_m, reverse_cost_m) VALUES ('" + sc.tenant() + "', "
                        + pgr[(int) e[0]] + ", " + pgr[(int) e[1]] + ", " + e[2] + ", " + e[3] + ")");
            }
            boolean hasPgr = count(st,
                    "SELECT count(*) FROM pg_extension WHERE extname = 'pgrouting'") > 0;
            if (hasPgr) {
                try (ResultSet rs = st.executeQuery("SELECT gis_waste_pgr_distance('"
                        + sc.tenant() + "', " + pgr[0] + ", " + pgr[4] + ")")) {
                    rs.next();
                    assertThat(rs.getDouble(1)).isEqualTo(400.0);
                }
            } else {
                assertThatThrownBy(() -> st.executeQuery("SELECT gis_waste_pgr_distance('"
                        + sc.tenant() + "', " + pgr[0] + ", " + pgr[4] + ")"))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("pgrouting");
            }
            }
            c.rollback();
        }
    }
}
