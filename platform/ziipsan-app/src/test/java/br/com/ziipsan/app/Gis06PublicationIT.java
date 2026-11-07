package br.com.ziipsan.app;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GIS-06 (P3.25): publicação do GIS da V13_12 no PostgreSQL real —
 * camadas e estilos semeados, tile MVT por ST_AsMVT com isolamento
 * por tenant, faixa de tiles do envelope e a invalidação ligada à
 * aprovação de pacote (versão da camada + gis_tile_dirty).
 *
 * <p>Segue EXATAMENTE o padrão do {@code Gis04ChangeSetIT}:
 * migrations do classpath contra schema descartável, transação
 * desfeita no fim; a lista vai da V13_1 à V13_12 (V13_10 e V13_11
 * incluídas: a cadeia real as tem). Massa sintética em torno de
 * -46, -23; o nó em operação nasce pelo caminho legítimo da GIS-04
 * (pacote aplicado), que é também quem dispara a marcação de tiles.
 *
 * <p>NÃO RODADA nesta VM (sem JDK/Docker): pendência da CI no PC
 * do dono, como as demais ITs GIS — ver RELATORIO-P3.25.md.
 */
class Gis06PublicationIT {

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
            "db/migration/tenant/V13_12__gis_publication_tiles_styles.sql");

    private static final UUID AUTHOR = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID REVIEWER = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @BeforeAll
    static void setUp() {
        ProvisionedTenant.ensure();
    }

    // ------------------------------------------------------------------ setup

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
                + tenant + "', '" + schema + "', 'Tenant descartável GIS-06')"
                + " ON CONFLICT (id) DO NOTHING");
        st.execute("INSERT INTO platform.tenant_vertical (tenant_id, vertical_id, state, activated_at)"
                + " VALUES ('" + tenant + "', 'sanitation', 'ACTIVE', NOW())"
                + " ON CONFLICT (tenant_id, vertical_id) DO NOTHING");
        st.execute("SELECT set_config('app.current_tenant', '" + tenant + "', true)");
        return new Scratch(tenant, schema);
    }

    private static String migrationSql(String path, UUID tenant) throws IOException {
        try (InputStream in = Gis06PublicationIT.class.getClassLoader().getResourceAsStream(path)) {
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

    private static String queryString(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    /** Nó em operação pelo caminho legítimo da GIS-04 (pacote aplicado). */
    private static UUID operationalNode(Statement st, UUID tenant, String code,
                                        String pointWkt) throws SQLException {
        String typeId = queryString(st,
                "SELECT id FROM gis_feature_type WHERE factory_code = 'gis01:GATE_VALVE'");
        UUID setId = UUID.randomUUID();
        st.execute("INSERT INTO gis_change_set (id, tenant_id, type, channel, author_id) VALUES ('"
                + setId + "', '" + tenant + "', 'CORRECTION', 'WEB', '" + AUTHOR + "')");
        UUID nodeId = UUID.randomUUID();
        st.execute("INSERT INTO gis_node (id, tenant_id, code, feature_type_id, lifecycle_status,"
                + " change_set_id, capture_method, geom) VALUES ('" + nodeId + "', '" + tenant
                + "', '" + code + "', '" + typeId + "', 'PLANNED', '" + setId
                + "', 'DIGITIZED', ST_GeomFromText('" + pointWkt + "', 4674))");
        st.execute("INSERT INTO gis_change_set_item (tenant_id, change_set_id, operation,"
                + " feature_table, feature_id, proposed_state) VALUES ('" + tenant + "', '"
                + setId + "', 'INSERT', 'gis_node', '" + nodeId
                + "', '{\"lifecycle_status\": \"IN_SERVICE\"}'::jsonb)");
        st.execute("UPDATE gis_change_set SET status = 'SUBMITTED',"
                + " submitted_at = clock_timestamp() WHERE id = '" + setId + "'");
        st.execute("UPDATE gis_change_set SET status = 'IN_REVIEW' WHERE id = '" + setId + "'");
        st.execute("SELECT gis_apply_change_set('" + setId + "', '" + REVIEWER + "')");
        return nodeId;
    }

    // ------------------------------------------------------------------ testes

    @Test
    @DisplayName("sementes: 12 camadas (2 pessoais), OSM padrão e estilo de fábrica por domínio e estado")
    void publicationSeeds() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = scratch(st);
                assertThat(count(st, "SELECT count(*) FROM gis_layer")).isEqualTo(12);
                assertThat(count(st, "SELECT count(*) FROM gis_layer WHERE personal"))
                        .isEqualTo(2);
                assertThat(count(st, "SELECT count(*) FROM gis_layer"
                        + " WHERE personal AND key IN ('service-lines', 'properties')"))
                        .isEqualTo(2);
                assertThat(count(st, "SELECT count(*) FROM gis_layer"
                        + " WHERE key = 'service-lines' AND tile_min_zoom = 17")).isEqualTo(1);
                assertThat(count(st, "SELECT count(*) FROM gis_basemap"
                        + " WHERE key = 'osm' AND is_default")).isEqualTo(1);
                // 5 domínios × 4 estados + accuracy + 4 genéricas > 0.
                assertThat(count(st, "SELECT count(*) FROM gis_style WHERE origin = 'FACTORY'"))
                        .isGreaterThanOrEqualTo(24);
                assertThat(queryString(st, "SELECT color_token FROM gis_style"
                        + " WHERE domain_key = 'WATER' AND lifecycle_status = 'ABANDONED'"
                        + " AND accuracy_status IS NULL AND origin = 'FACTORY'"))
                        .isEqualTo("map-abandoned");
                assertThat(queryString(st, "SELECT line_dash::text FROM gis_style"
                        + " WHERE domain_key = 'WATER' AND lifecycle_status = 'PLANNED'"
                        + " AND accuracy_status IS NULL AND origin = 'FACTORY'"))
                        .isEqualTo("[3, 2]");
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("gis_tile_range: o envelope de um tile devolve exatamente esse tile")
    void tileRangeOfTileEnvelopeIsTheTile() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                scratch(st);
                try (ResultSet rs = st.executeQuery(
                        "SELECT x_min, x_max, y_min, y_max FROM gis_tile_range("
                                + "ST_TileEnvelope(15, 12196, 18540), 15)")) {
                    rs.next();
                    assertThat(rs.getInt(1)).isEqualTo(12196);
                    assertThat(rs.getInt(2)).isEqualTo(12196);
                    assertThat(rs.getInt(3)).isEqualTo(18540);
                    assertThat(rs.getInt(4)).isEqualTo(18540);
                }
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("tile MVT: ST_AsMVT gera para o tenant dono e nada para outro tenant")
    void mvtTileIsTenantScoped() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = scratch(st);
                UUID nodeId = operationalNode(st, s.tenant(), "N-TILE", "POINTZ(-46.0 -23.0 0)");
                assertThat(nodeId).isNotNull();
                int[] tile = new int[2];
                try (ResultSet rs = st.executeQuery(
                        "SELECT x_min, y_min FROM gis_tile_range(geom_web, 15)"
                                + " FROM gis_node WHERE id = '" + nodeId + "'")) {
                    rs.next();
                    tile[0] = rs.getInt(1);
                    tile[1] = rs.getInt(2);
                }
                byte[] own = runTile(c, s.tenant(), tile[0], tile[1]);
                assertThat(own).isNotNull();
                assertThat(own.length).isPositive();

                byte[] otherTenant = runTile(c, UUID.randomUUID(), tile[0], tile[1]);
                assertThat(otherTenant == null || otherTenant.length == 0).isTrue();

                byte[] farAway = runTile(c, s.tenant(), 0, 0);
                assertThat(farAway == null || farAway.length == 0).isTrue();
            } finally {
                c.rollback();
            }
        }
    }

    /**
     * O SQL do tile de nós de água, espelho literal do que
     * GisTileSql monta para WATER_NODES (a IT não importa classes
     * do módulo gis — mesmo desenho das ITs irmãs, só SQL): se o
     * formato mudar lá, muda aqui e a divergência aparece na
     * revisão, nunca em silêncio.
     */
    private static final String WATER_NODES_TILE_SQL = """
            WITH tile AS (
                SELECT ST_TileEnvelope(?, ?, ?) AS env
            ), envelope AS (
                SELECT env, ST_Expand(env,
                           (ST_XMax(env) - ST_XMin(env)) * 64.0 / 4096.0) AS env_margin
                FROM tile
            ), features AS (
                SELECT f.id::text AS id, f.code AS code,
                       ft.code AS type_code,
                       ci.dn_mm AS dn,
                       NULLIF(m.code, 'UNKNOWN') AS material,
                       f.lifecycle_status AS lifecycle_status,
                       f.operational_status AS operational_status,
                       f.accuracy_status AS accuracy_status,
                       ST_AsMVTGeom(f.geom_web, envelope.env, 4096, 64, true) AS geom
                FROM gis_node f
                CROSS JOIN envelope
                JOIN gis_feature_type ft
                  ON ft.id = f.feature_type_id AND ft.tenant_id = f.tenant_id
                JOIN gis_feature_class fc
                  ON fc.id = ft.class_id AND fc.tenant_id = ft.tenant_id
                LEFT JOIN gis_catalog_item ci
                  ON ci.id = f.catalog_item_id AND ci.tenant_id = f.tenant_id
                LEFT JOIN gis_material m
                  ON m.id = ci.material_id AND m.tenant_id = f.tenant_id
                WHERE f.tenant_id = ?
                  AND f.geom_web IS NOT NULL
                  AND f.geom_web && envelope.env_margin
                  AND (ft.min_zoom IS NULL OR ft.min_zoom <= ?)
                  AND fc.domain = 'WATER'
            )
            SELECT ST_AsMVT(features.*, ?, 4096, 'geom') FROM features
            """;

    private static byte[] runTile(Connection c, UUID tenant, int x, int y) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(WATER_NODES_TILE_SQL)) {
            ps.setInt(1, 15);
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setObject(4, tenant);
            ps.setInt(5, 15);
            ps.setString(6, "water-nodes");
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBytes(1) : null;
            }
        }
    }

    @Test
    @DisplayName("aprovação de pacote marca tiles sujos (z10..z18) e sobe a versão da camada")
    void approvalMarksTilesDirtyAndBumpsVersion() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = scratch(st);
                long versionBefore = Long.parseLong(queryString(st,
                        "SELECT layer_version FROM gis_layer WHERE key = 'water-nodes'"));
                operationalNode(st, s.tenant(), "N-DIRTY", "POINTZ(-46.0 -23.0 0)");
                long versionAfter = Long.parseLong(queryString(st,
                        "SELECT layer_version FROM gis_layer WHERE key = 'water-nodes'"));
                assertThat(versionAfter).isEqualTo(versionBefore + 1);
                assertThat(count(st, "SELECT count(*) FROM gis_tile_dirty"
                        + " WHERE layer_key = 'water-nodes' AND processed_at IS NULL"))
                        .isEqualTo(9);
                assertThat(count(st, "SELECT count(DISTINCT z) FROM gis_tile_dirty"
                        + " WHERE layer_key = 'water-nodes'")).isEqualTo(9);
                assertThat(count(st, "SELECT count(*) FROM gis_tile_dirty"
                        + " WHERE z < 10 OR z > 18")).isZero();
            } finally {
                c.rollback();
            }
        }
    }

    /**
     * O INSERT ... ON CONFLICT do {@code GisStyleService.saveOverride},
     * espelho literal (correção pós-revisão M4): a unicidade por
     * INSERT puro não provava a inferência do alvo de conflito com
     * o índice de expressões {@code uq_gis_style_scope} — este
     * teste grava DUAS vezes no mesmo escopo pelo caminho do
     * serviço e exige uma linha só, com os campos da segunda
     * gravação. Se o formato mudar no serviço, muda aqui.
     */
    private static final String SAVE_OVERRIDE_SQL = """
            INSERT INTO gis_style (id, tenant_id, domain_key, feature_type_id,
                    lifecycle_status, accuracy_status, color_token, line_width,
                    line_dash, halo, icon_key, label_field, label_min_zoom,
                    origin, created_by, updated_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, 'TENANT', ?, ?)
            ON CONFLICT (tenant_id, origin,
                COALESCE(domain_key, ''),
                COALESCE(feature_type_id,
                    '00000000-0000-0000-0000-000000000000'::uuid),
                lifecycle_status, COALESCE(accuracy_status, ''))
            DO UPDATE SET color_token = EXCLUDED.color_token,
                    line_width = EXCLUDED.line_width,
                    line_dash = EXCLUDED.line_dash,
                    halo = EXCLUDED.halo,
                    icon_key = EXCLUDED.icon_key,
                    label_field = EXCLUDED.label_field,
                    label_min_zoom = EXCLUDED.label_min_zoom,
                    active = TRUE, updated_at = clock_timestamp(),
                    updated_by = EXCLUDED.updated_by
            """;

    private static void saveOverride(Connection c, UUID tenant, String colorToken,
                                     double lineWidth) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SAVE_OVERRIDE_SQL)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenant);
            ps.setString(3, "WATER");
            ps.setNull(4, java.sql.Types.OTHER);
            ps.setString(5, "IN_SERVICE");
            ps.setNull(6, java.sql.Types.VARCHAR);
            ps.setString(7, colorToken);
            ps.setDouble(8, lineWidth);
            ps.setString(9, "[3, 2]");
            ps.setNull(10, java.sql.Types.BOOLEAN);
            ps.setNull(11, java.sql.Types.VARCHAR);
            ps.setNull(12, java.sql.Types.VARCHAR);
            ps.setNull(13, java.sql.Types.INTEGER);
            ps.setObject(14, AUTHOR);
            ps.setObject(15, AUTHOR);
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("M4: upsert do saveOverride — segunda gravação no mesmo escopo atualiza a mesma linha")
    void styleOverrideUpsertUpdatesSameRow() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = scratch(st);
                saveOverride(c, s.tenant(), "map-sewer", 2.0);
                saveOverride(c, s.tenant(), "map-storm", 3.5);
                assertThat(count(st, "SELECT count(*) FROM gis_style"
                        + " WHERE origin = 'TENANT' AND domain_key = 'WATER'"
                        + " AND lifecycle_status = 'IN_SERVICE'"
                        + " AND accuracy_status IS NULL")).isEqualTo(1);
                assertThat(queryString(st, "SELECT color_token FROM gis_style"
                        + " WHERE origin = 'TENANT' AND domain_key = 'WATER'"
                        + " AND lifecycle_status = 'IN_SERVICE'"))
                        .isEqualTo("map-storm");
                assertThat(queryString(st, "SELECT line_width::text FROM gis_style"
                        + " WHERE origin = 'TENANT' AND domain_key = 'WATER'"
                        + " AND lifecycle_status = 'IN_SERVICE'"))
                        .startsWith("3.5");
                // A fábrica do mesmo escopo continua intacta ao lado.
                assertThat(count(st, "SELECT count(*) FROM gis_style"
                        + " WHERE origin = 'FACTORY' AND domain_key = 'WATER'"
                        + " AND lifecycle_status = 'IN_SERVICE'"
                        + " AND accuracy_status IS NULL")).isEqualTo(1);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("override do tenant não apaga a fábrica e a unicidade por escopo vale")
    void styleOverrideCoexistsWithFactory() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = scratch(st);
                st.execute("INSERT INTO gis_style (tenant_id, domain_key, lifecycle_status,"
                        + " color_token, origin) VALUES ('" + s.tenant()
                        + "', 'WATER', 'IN_SERVICE', 'map-sewer', 'TENANT')");
                assertThat(count(st, "SELECT count(*) FROM gis_style WHERE domain_key = 'WATER'"
                        + " AND lifecycle_status = 'IN_SERVICE' AND accuracy_status IS NULL"))
                        .isEqualTo(2);
                var savepoint = c.setSavepoint();
                try {
                    st.execute("INSERT INTO gis_style (tenant_id, domain_key, lifecycle_status,"
                            + " color_token, origin) VALUES ('" + s.tenant()
                            + "', 'WATER', 'IN_SERVICE', 'map-sewer', 'TENANT')");
                    throw new AssertionError("segundo override no mesmo escopo devia falhar");
                } catch (SQLException expected) {
                    c.rollback(savepoint);
                }
            } finally {
                c.rollback();
            }
        }
    }
}
