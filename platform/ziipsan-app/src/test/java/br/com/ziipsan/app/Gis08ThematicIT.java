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

/**
 * GIS-08 (P3.27): temáticos, precisão, OS no mapa e relatórios do
 * cadastro no PostgreSQL real — as views da V13_13 sobre massa
 * sintética em torno de -46, -23, os cenários SG-873 a SG-883 e
 * TR-1059 a TR-1063, a supressão pública da célula de 4 imóveis e a
 * extensão "na data" conferida com o ST_Length geodésico.
 *
 * <p>Segue EXATAMENTE o padrão do {@code Gis06PublicationIT}:
 * migrations do classpath contra schema descartável, transação
 * desfeita no fim. A cadeia é a ordem real de versões das tabelas
 * que as views tocam (legado, registry até a V3_14, billing V4_1,
 * metering V5_1/V5_6, core V9_1 e GIS V13_1..V13_13). Feições
 * nascem pelo caminho legítimo da GIS-04 (pacote aplicado), como
 * na Gis06.
 *
 * <p>NÃO RODADA nesta VM (sem JDK/Docker): pendência da CI no PC
 * do dono, como as demais ITs GIS — ver RELATORIO-P3.27.md.
 */
class Gis08ThematicIT {

    private static final List<String> LEGACY_MIGRATIONS = List.of(
            "db/migration/tenant/V1_1__create_tenant_schema.sql",
            "db/migration/tenant/V1_2__tenant_table_helper.sql",
            "db/migration/tenant/V1_3__harden_audit_function.sql",
            "db/migration/tenant/V1_5__tenant_parameters.sql",
            "db/migration/tenant/V2_2__create_losses_schema.sql",
            "db/migration/tenant/V2_3__create_gis_schema.sql");

    private static final List<String> REGISTRY_MIGRATIONS = List.of(
            "db/migration/tenant/V3_1__create_registry_schema.sql",
            "db/migration/tenant/V3_2__registry_audit_redaction.sql",
            "db/migration/tenant/V3_3__registry_unit_deleted_reason_redaction.sql",
            "db/migration/tenant/V3_4__customer_p016_fields.sql",
            "db/migration/tenant/V3_5__registry_unit_extended_fields.sql",
            "db/migration/tenant/V3_6__registry_territory_fields.sql",
            "db/migration/tenant/V3_7__catalog_frontend_fields.sql",
            "db/migration/tenant/V3_8__holder_situations.sql",
            "db/migration/tenant/V3_9__customer_name_trigram.sql",
            "db/migration/tenant/V3_10__registry_matricula_sequence.sql",
            "db/migration/tenant/V3_11__generated_files.sql",
            "db/migration/tenant/V3_12__registry_r2.sql",
            "db/migration/tenant/V3_13__vulnerable_proof_redaction.sql",
            "db/migration/tenant/V3_14__service_connections.sql");

    private static final List<String> MODULE_MIGRATIONS = List.of(
            "db/migration/tenant/V4_1__create_billing_schema.sql",
            "db/migration/tenant/V5_1__create_metering_schema.sql",
            "db/migration/tenant/V5_6__consumption_record_versioning.sql",
            "db/migration/tenant/V9_1__service_order_engine.sql");

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
            "db/migration/tenant/V13_13__gis_thematic_maps.sql");

    private static final UUID AUTHOR = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID REVIEWER = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID CREW = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @BeforeAll
    static void setUp() {
        ProvisionedTenant.ensure();
    }

    // ------------------------------------------------------------------ setup

    private record Scratch(UUID tenant, String schema) {
    }

    private static Scratch scratch(Statement st) throws Exception {
        UUID tenant = UUID.randomUUID();
        String schema = "it_gis08_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        st.execute("CREATE SCHEMA " + schema);
        st.execute("SET LOCAL search_path = " + schema + ", public");
        for (String m : LEGACY_MIGRATIONS) {
            st.execute(migrationSql(m, tenant));
        }
        for (String m : REGISTRY_MIGRATIONS) {
            st.execute(migrationSql(m, tenant));
        }
        for (String m : MODULE_MIGRATIONS) {
            st.execute(migrationSql(m, tenant));
        }
        for (String m : GIS_MIGRATIONS) {
            st.execute(migrationSql(m, tenant));
        }
        st.execute("INSERT INTO public.tenant_catalog (id, schema_name, name) VALUES ('"
                + tenant + "', '" + schema + "', 'Tenant descartável GIS-08')"
                + " ON CONFLICT (id) DO NOTHING");
        st.execute("INSERT INTO platform.tenant_vertical (tenant_id, vertical_id, state, activated_at)"
                + " VALUES ('" + tenant + "', 'sanitation', 'ACTIVE', NOW())"
                + " ON CONFLICT (tenant_id, vertical_id) DO NOTHING");
        st.execute("SELECT set_config('app.current_tenant', '" + tenant + "', true)");
        return new Scratch(tenant, schema);
    }

    private static String migrationSql(String path, UUID tenant) throws IOException {
        try (InputStream in = Gis08ThematicIT.class.getClassLoader().getResourceAsStream(path)) {
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

    // ------------------------------------------------------------------ massa comercial

    private static UUID sector(Statement st, UUID tenant, int code, String name) throws SQLException {
        UUID locality = UUID.randomUUID();
        st.execute("INSERT INTO localities (id, tenant_id, code, name) VALUES ('"
                + locality + "', '" + tenant + "', " + code + ", 'Localidade " + code + "')");
        UUID sector = UUID.randomUUID();
        st.execute("INSERT INTO commercial_sectors (id, tenant_id, locality_id, code, name) VALUES ('"
                + sector + "', '" + tenant + "', '" + locality + "', " + code + ", '" + name + "')");
        return sector;
    }

    private static UUID block(Statement st, UUID tenant, UUID sectorId, int code) throws SQLException {
        UUID block = UUID.randomUUID();
        st.execute("INSERT INTO blocks (id, tenant_id, sector_id, code) VALUES ('"
                + block + "', '" + tenant + "', '" + sectorId + "', " + code + ")");
        return block;
    }

    private static UUID unit(Statement st, UUID tenant, String registration, UUID blockId,
                             String pointWkt, String categoryCode) throws SQLException {
        UUID id = UUID.randomUUID();
        st.execute("INSERT INTO consumer_units (id, tenant_id, registration_number, check_digit,"
                + " status, block_id, lot, sublot, coordinates) VALUES ('" + id + "', '" + tenant
                + "', '" + registration + "', '0', 'ACTIVE', "
                + (blockId == null ? "NULL" : "'" + blockId + "'") + ", 1, 1, "
                + (pointWkt == null ? "NULL" : "ST_GeomFromText('" + pointWkt + "', 4674)") + ")");
        if (categoryCode != null) {
            UUID category = UUID.randomUUID();
            st.execute("INSERT INTO categories (id, tenant_id, code, name) VALUES ('"
                    + category + "', '" + tenant + "', '" + categoryCode + "', '" + categoryCode
                    + "') ON CONFLICT DO NOTHING");
            String categoryId = queryString(st,
                    "SELECT id FROM categories WHERE code = '" + categoryCode + "'");
            String existingSub = queryString(st,
                    "SELECT id FROM subcategories WHERE code = '" + categoryCode + "-U'");
            UUID subcategory = existingSub == null ? UUID.randomUUID() : UUID.fromString(existingSub);
            if (existingSub == null) {
                st.execute("INSERT INTO subcategories (id, tenant_id, category_id, code, name)"
                        + " VALUES ('" + subcategory + "', '" + tenant + "', '" + categoryId
                        + "', '" + categoryCode + "-U', 'Única')");
            }
            st.execute("INSERT INTO unit_subcategories (tenant_id, unit_id, subcategory_id, economies)"
                    + " VALUES ('" + tenant + "', '" + id + "', '" + subcategory + "', 1)");
        }
        return id;
    }

    private static void consumption(Statement st, UUID tenant, UUID unitId, int billed,
                                    Integer average, String type) throws SQLException {
        st.execute("INSERT INTO consumption_records (tenant_id, unit_id, service_kind,"
                + " reference, billed_consumption, calculated_consumption, billable,"
                + " billing_paused, consumption_type, average_consumption, discarded_remainder)"
                + " VALUES ('" + tenant + "', '" + unitId + "', 'WATER', '2026-10-01', "
                + billed + ", " + billed + ", TRUE, FALSE, '" + type + "', "
                + (average == null ? "NULL" : average) + ", 0)");
    }

    private static void bill(Statement st, UUID tenant, UUID unitId, int daysOverdue) throws SQLException {
        st.execute("INSERT INTO bills (tenant_id, unit_id, reference_month, status,"
                + " water_value, sewer_value, debits_value, credits_value, taxes_value,"
                + " total_value, due_date) VALUES ('" + tenant + "', '" + unitId
                + "', '2026-08-01', 'NORMAL', 10, 0, 0, 0, 0, 10, CURRENT_DATE - " + daysOverdue + ")");
    }

    // ------------------------------------------------------------------ feições pelo pacote

    private static UUID newSet(Statement st, UUID tenant) throws SQLException {
        UUID setId = UUID.randomUUID();
        st.execute("INSERT INTO gis_change_set (id, tenant_id, type, channel, author_id) VALUES ('"
                + setId + "', '" + tenant + "', 'CORRECTION', 'WEB', '" + AUTHOR + "')");
        return setId;
    }

    private static void applySet(Statement st, UUID setId) throws SQLException {
        st.execute("UPDATE gis_change_set SET status = 'SUBMITTED',"
                + " submitted_at = clock_timestamp() WHERE id = '" + setId + "'");
        st.execute("UPDATE gis_change_set SET status = 'IN_REVIEW' WHERE id = '" + setId + "'");
        st.execute("SELECT gis_apply_change_set('" + setId + "', '" + REVIEWER + "')");
    }

    private static void addItem(Statement st, UUID tenant, UUID setId, String table, UUID featureId,
                                String proposedJson) throws SQLException {
        st.execute("INSERT INTO gis_change_set_item (tenant_id, change_set_id, operation,"
                + " feature_table, feature_id, proposed_state) VALUES ('" + tenant + "', '"
                + setId + "', 'INSERT', '" + table + "', '" + featureId + "', '"
                + proposedJson + "'::jsonb)");
    }

    private static UUID nodeViaSet(Statement st, UUID tenant, UUID setId, String code,
                                   String factoryCode, String captureMethod, Double hAccuracy,
                                   String pointWkt) throws SQLException {
        String typeId = queryString(st,
                "SELECT id FROM gis_feature_type WHERE factory_code = '" + factoryCode + "'");
        UUID id = UUID.randomUUID();
        st.execute("INSERT INTO gis_node (id, tenant_id, code, feature_type_id, lifecycle_status,"
                + " change_set_id, capture_method, h_accuracy_m, geom) VALUES ('" + id + "', '"
                + tenant + "', '" + code + "', '" + typeId + "', 'PLANNED', '" + setId + "', '"
                + captureMethod + "', " + (hAccuracy == null ? "NULL" : hAccuracy)
                + ", ST_GeomFromText('" + pointWkt + "', 4674))");
        addItem(st, tenant, setId, "gis_node", id, "{\"lifecycle_status\": \"IN_SERVICE\"}");
        return id;
    }

    // ------------------------------------------------------------------ testes

    @Test
    @DisplayName("sementes: 14 temáticos de fábrica; SG-882 sem classes e SG-877/878/879 sem tipos configurados")
    void factorySeeds() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                scratch(st);
                assertThat(count(st, "SELECT count(*) FROM gis_thematic_map WHERE origin = 'FACTORY'"))
                        .isEqualTo(14);
                assertThat(queryString(st, "SELECT classes_json::text FROM gis_thematic_map"
                        + " WHERE key = 'grandes-consumidores'")).isEqualTo("[]");
                assertThat(count(st, "SELECT count(*) FROM gis_thematic_map"
                        + " WHERE key IN ('os-vazamento-cavalete', 'os-vazamento-rede',"
                        + " 'os-falta-agua-qualidade')"
                        + " AND filter_json -> 'serviceTypeCodes' = '[]'::jsonb")).isEqualTo(3);
                assertThat(count(st, "SELECT count(*) FROM gis_thematic_map"
                        + " WHERE personal AND aggregation NOT IN ('BLOCK', 'SECTOR')")).isZero();
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("SG-873/874/875/883: comparação com a média, faturado pela média, zero e filtro por categoria")
    void consumptionScenarios() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = scratch(st);
                UUID sector = sector(st, s.tenant(), 1, "Centro");
                UUID block = block(st, s.tenant(), sector, 10);
                UUID below = unit(st, s.tenant(), "1001", block, "POINT(-46.0000 -23.0000)", "RES");
                UUID above = unit(st, s.tenant(), "1002", block, "POINT(-46.0005 -23.0000)", "RES");
                UUID zeroAvg = unit(st, s.tenant(), "1003", block, "POINT(-46.0010 -23.0000)", "COM");
                consumption(st, s.tenant(), below, 5, 10, "REAL");
                consumption(st, s.tenant(), above, 20, 10, "REAL");
                consumption(st, s.tenant(), zeroAvg, 0, 8, "METER_AVERAGE");

                assertThat(queryString(st, "SELECT comparison FROM gis_thematic_unit_consumption"
                        + " WHERE unit_id = '" + below + "'")).isEqualTo("BELOW_AVERAGE");
                assertThat(queryString(st, "SELECT comparison FROM gis_thematic_unit_consumption"
                        + " WHERE unit_id = '" + above + "'")).isEqualTo("ABOVE_AVERAGE");
                assertThat(count(st, "SELECT count(*) FROM gis_thematic_unit_consumption"
                        + " WHERE unit_id = '" + zeroAvg + "' AND zero_consumption"
                        + " AND billed_by_average")).isEqualTo(1);
                // SG-883: o filtro por categoria recorta a mesma fonte.
                assertThat(count(st, "SELECT count(*) FROM gis_thematic_unit_consumption"
                        + " WHERE category_code = 'RES'")).isEqualTo(2);
                assertThat(count(st, "SELECT count(*) FROM gis_thematic_unit_consumption"
                        + " WHERE category_code = 'COM'")).isEqualTo(1);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("vigência (V3_12/ED-13): categoria e economias da view-base ignoram a linha expirada da unidade")
    void unitBaseRespectsSubcategoryValidity() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = scratch(st);
                // O helper grava a linha vigente: categoria RES, 1 economia,
                // valid_from = CURRENT_DATE (default da V3_12), valid_to nulo.
                UUID unit = unit(st, s.tenant(), "7001", null, "POINT(-46.0015 -23.0000)", "RES");
                st.execute("UPDATE unit_subcategories SET economies = 2"
                        + " WHERE tenant_id = '" + s.tenant() + "' AND unit_id = '" + unit + "'");
                // Linha EXPIRADA da mesma unidade: categoria AAA (menor código —
                // venceria o desempate da view sem o predicado de vigência) com
                // 3 economias em 2020. A EXCLUDE da V3_12 é por subcategoria,
                // logo as duas linhas coexistem.
                UUID oldCategory = UUID.randomUUID();
                st.execute("INSERT INTO categories (id, tenant_id, code, name) VALUES ('"
                        + oldCategory + "', '" + s.tenant() + "', 'AAA', 'AAA')");
                UUID oldSubcategory = UUID.randomUUID();
                st.execute("INSERT INTO subcategories (id, tenant_id, category_id, code, name)"
                        + " VALUES ('" + oldSubcategory + "', '" + s.tenant() + "', '"
                        + oldCategory + "', 'AAA-U', 'Única antiga')");
                st.execute("INSERT INTO unit_subcategories (tenant_id, unit_id, subcategory_id,"
                        + " economies, valid_from, valid_to) VALUES ('" + s.tenant() + "', '"
                        + unit + "', '" + oldSubcategory
                        + "', 3, DATE '2020-01-01', DATE '2020-12-31')");

                // Categoria é a da linha vigente, não a expirada de menor código.
                assertThat(queryString(st, "SELECT category_code FROM gis_thematic_unit_base"
                        + " WHERE unit_id = '" + unit + "'")).isEqualTo("RES");
                // Economias somam só o período vigente (2), não os dois períodos (3 + 2).
                assertThat(count(st, "SELECT economies FROM gis_thematic_unit_base"
                        + " WHERE unit_id = '" + unit + "'")).isEqualTo(2);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("SG-876/880/881: ligação cortada, hidrômetro vigente e fatura vencida há mais de 60 dias")
    void connectionMeterOverdueScenarios() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = scratch(st);
                UUID sector = sector(st, s.tenant(), 2, "Norte");
                UUID block = block(st, s.tenant(), sector, 20);
                UUID cut = unit(st, s.tenant(), "2001", block, "POINT(-46.0020 -23.0000)", "RES");
                UUID recent = unit(st, s.tenant(), "2002", block, "POINT(-46.0025 -23.0000)", "RES");

                UUID status = UUID.randomUUID();
                st.execute("INSERT INTO connection_statuses (id, tenant_id, kind, code, name,"
                        + " system_role) VALUES ('" + status + "', '" + s.tenant()
                        + "', 'WATER', 'CUT1', 'Cortada', 'CUT')");
                UUID connection = UUID.randomUUID();
                st.execute("INSERT INTO service_connections (id, tenant_id, property_unit_id,"
                        + " kind, status_id, connected_on, connected_on_source) VALUES ('"
                        + connection + "', '" + s.tenant() + "', '" + cut
                        + "', 'WATER', '" + status + "', '2020-01-10', 'MANUAL')");
                st.execute("INSERT INTO service_connection_units (tenant_id, connection_id, unit_id)"
                        + " VALUES ('" + s.tenant() + "', '" + connection + "', '" + cut + "')");
                assertThat(count(st, "SELECT count(*) FROM gis_thematic_connection_status"
                        + " WHERE unit_id = '" + cut + "' AND system_role = 'CUT'")).isEqualTo(1);

                UUID meter = UUID.randomUUID();
                st.execute("INSERT INTO meters (id, tenant_id, meter_number, model, digits, status)"
                        + " VALUES ('" + meter + "', '" + s.tenant() + "', 'H-1', 'VOLUMETRICO', 5,"
                        + " 'INSTALLED')");
                st.execute("INSERT INTO meter_installations (tenant_id, meter_id, unit_id, kind,"
                        + " installed_on, installed_reading, is_replacement) VALUES ('"
                        + s.tenant() + "', '" + meter + "', '" + cut
                        + "', 'WATER', CURRENT_DATE - 400, 0, FALSE)");
                assertThat(count(st, "SELECT count(*) FROM gis_thematic_meter_age"
                        + " WHERE unit_id = '" + cut + "' AND meter_type = 'VOLUMETRICO'"
                        + " AND age_years >= 1")).isEqualTo(1);

                bill(st, s.tenant(), cut, 70);
                bill(st, s.tenant(), recent, 30);
                assertThat(count(st, "SELECT count(*) FROM gis_thematic_unit_overdue"
                        + " WHERE unit_id = '" + cut + "'")).isEqualTo(1);
                assertThat(count(st, "SELECT count(*) FROM gis_thematic_unit_overdue"
                        + " WHERE unit_id = '" + recent + "'")).isZero();
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("SG-491/TR-534: OS aberta com alvo na unidade entra na camada com posição e equipe")
    void serviceOrderOnMap() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = scratch(st);
                UUID unit = unit(st, s.tenant(), "3001", null, "POINT(-46.0030 -23.0000)", null);
                UUID type = UUID.randomUUID();
                st.execute("INSERT INTO service_types (id, tenant_id, code, name, operation,"
                        + " effect_code, indicator, target_classes) VALUES ('" + type + "', '"
                        + s.tenant() + "', 'LEAK_TEST', 'Vazamento de teste', 'REPAIR',"
                        + " 'TEST_EFFECT', 'FIELD', ARRAY['CONSUMER_UNIT'])");
                UUID order = UUID.randomUUID();
                st.execute("INSERT INTO service_orders (id, tenant_id, service_type_id, origin,"
                        + " execution_mode, created_by) VALUES ('" + order + "', '" + s.tenant()
                        + "', '" + type + "', 'INTERNAL', 'FIELD', '" + AUTHOR + "')");
                st.execute("UPDATE service_orders SET status = 'OPEN' WHERE id = '" + order + "'");
                st.execute("INSERT INTO service_order_targets (tenant_id, order_id, target_type,"
                        + " target_id) VALUES ('" + s.tenant() + "', '" + order
                        + "', 'CONSUMER_UNIT', '" + unit + "')");
                st.execute("INSERT INTO service_order_crew (tenant_id, order_id, user_id, role)"
                        + " VALUES ('" + s.tenant() + "', '" + order + "', '" + CREW + "', 'LEAD')");
                assertThat(count(st, "SELECT count(*) FROM gis_thematic_service_order"
                        + " WHERE order_id = '" + order + "' AND geom IS NOT NULL"
                        + " AND service_type_code = 'LEAK_TEST' AND status = 'OPEN'")).isEqualTo(1);
                assertThat(count(st, "SELECT count(*) FROM gis_thematic_service_order"
                        + " WHERE order_id = '" + order + "' AND '" + CREW
                        + "' = ANY(crew_user_ids)")).isEqualTo(1);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("precisão (§3.8): digitalizado é aproximado; RTK dentro e fora da meta separam no relatório")
    void precisionGroups() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = scratch(st);
                UUID set = newSet(st, s.tenant());
                nodeViaSet(st, s.tenant(), set, "N-DIG", "gis01:GATE_VALVE", "DIGITIZED", null,
                        "POINTZ(-46.0040 -23.0 0)");
                nodeViaSet(st, s.tenant(), set, "N-RTK-OK", "gis01:GATE_VALVE", "RTK_FIXED", 0.05,
                        "POINTZ(-46.0041 -23.0 0)");
                nodeViaSet(st, s.tenant(), set, "N-RTK-OUT", "gis01:GATE_VALVE", "RTK_FIXED", 0.50,
                        "POINTZ(-46.0042 -23.0 0)");
                applySet(st, set);
                assertThat(count(st, "SELECT count(*) FROM gis_thematic_precision_feature"
                        + " WHERE precision_group = 'APPROXIMATE'")).isEqualTo(1);
                assertThat(count(st, "SELECT count(*) FROM gis_thematic_precision_feature"
                        + " WHERE precision_group = 'SURVEYED_WITHIN_TARGET'")).isEqualTo(1);
                assertThat(count(st, "SELECT count(*) FROM gis_thematic_precision_feature"
                        + " WHERE precision_group = 'SURVEYED_OUT_OF_TARGET'")).isEqualTo(1);
                assertThat(count(st, "SELECT count(*) FROM rpt_gis_precisao_cadastro"
                        + " WHERE grupo_precisao = 'APPROXIMATE'")).isEqualTo(1);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("extensão (TR-1062): a função na data soma o ST_Length geodésico e respeita o installed_on")
    void extensionAtDate() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = scratch(st);
                UUID set = newSet(st, s.tenant());
                UUID n1 = nodeViaSet(st, s.tenant(), set, "E1", "gis01:JUNCTION", "DIGITIZED", null,
                        "POINTZ(-46.0050 -23.0 0)");
                UUID n2 = nodeViaSet(st, s.tenant(), set, "E2", "gis01:JUNCTION", "DIGITIZED", null,
                        "POINTZ(-46.0060 -23.0 0)");
                String arcType = queryString(st,
                        "SELECT id FROM gis_feature_type WHERE factory_code = 'gis01:WATER_MAIN'");
                UUID arc = UUID.randomUUID();
                st.execute("INSERT INTO gis_arc (id, tenant_id, code, feature_type_id,"
                        + " lifecycle_status, change_set_id, capture_method, network_function,"
                        + " installed_on, geom) VALUES ('" + arc + "', '" + s.tenant()
                        + "', 'ARC-EXT', '" + arcType + "', 'PLANNED', '" + set
                        + "', 'DIGITIZED', 'DISTRIBUTION', '1995-06-15',"
                        + " ST_GeomFromText('LINESTRINGZ(-46.0050 -23.0 0, -46.0060 -23.0 0)', 4674))");
                addItem(st, s.tenant(), set, "gis_arc", arc,
                        "{\"lifecycle_status\": \"IN_SERVICE\"}");
                applySet(st, set);

                String geodesic = queryString(st,
                        "SELECT length_m::text FROM gis_arc WHERE id = '" + arc + "'");
                assertThat(queryString(st, "SELECT extensao_m::text FROM gis_network_extension_at('2026-01-01')"
                        + " WHERE dominio = 'WATER' AND decada_inicio = 1990")).isEqualTo(geodesic);
                assertThat(count(st, "SELECT count(*) FROM gis_network_extension_at('1990-01-01')"))
                        .isZero();
                assertThat(count(st, "SELECT count(*) FROM rpt_gis_extensao_rede"
                        + " WHERE dominio = 'WATER' AND funcao = 'DISTRIBUTION'")).isEqualTo(1);
                assertThat(n1).isNotNull();
                assertThat(n2).isNotNull();
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("público (§12): célula de 4 imóveis não publica; a de 6 publica — com o HAVING da view-fonte")
    void publicAggregationSuppression() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = scratch(st);
                UUID small = sector(st, s.tenant(), 3, "Pequeno");
                UUID smallBlock = block(st, s.tenant(), small, 30);
                for (int i = 0; i < 4; i++) {
                    UUID u = unit(st, s.tenant(), "40" + i, smallBlock,
                            "POINT(-46.007" + i + " -23.0000)", "RES");
                    consumption(st, s.tenant(), u, 10, 10, "REAL");
                }
                UUID big = sector(st, s.tenant(), 4, "Grande");
                UUID bigBlock = block(st, s.tenant(), big, 40);
                for (int i = 0; i < 6; i++) {
                    UUID u = unit(st, s.tenant(), "50" + i, bigBlock,
                            "POINT(-46.008" + i + " -23.0000)", "RES");
                    consumption(st, s.tenant(), u, 10, 10, "REAL");
                }
                // A mesma forma do GisThematicSql.publicAggregateSql (SECTOR, mínimo 5).
                assertThat(count(st, "SELECT count(*) FROM ("
                        + " SELECT sector_id FROM gis_thematic_unit_consumption"
                        + " GROUP BY sector_id HAVING COUNT(DISTINCT unit_id) >= 5) t")).isEqualTo(1);
                assertThat(queryString(st, "SELECT sector_id::text FROM ("
                        + " SELECT sector_id FROM gis_thematic_unit_consumption"
                        + " GROUP BY sector_id HAVING COUNT(DISTINCT unit_id) >= 5) t"))
                        .isEqualTo(big.toString());
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("TR-1059: seleção por polígono conta unidades, economias e consumo médio só de dentro")
    void polygonStatistics() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = scratch(st);
                UUID inside1 = unit(st, s.tenant(), "6001", null, "POINT(-46.0100 -23.0000)", "RES");
                UUID inside2 = unit(st, s.tenant(), "6002", null, "POINT(-46.0105 -23.0005)", "RES");
                UUID outside = unit(st, s.tenant(), "6003", null, "POINT(-46.0200 -23.0000)", "RES");
                consumption(st, s.tenant(), inside1, 10, 10, "REAL");
                consumption(st, s.tenant(), inside2, 20, 10, "REAL");
                consumption(st, s.tenant(), outside, 99, 10, "REAL");
                String polygon = "{\"type\":\"Polygon\",\"coordinates\":[[[-46.011,-23.001],"
                        + "[-46.009,-23.001],[-46.009,-22.999],[-46.011,-22.999],[-46.011,-23.001]]]}";
                try (ResultSet rs = st.executeQuery(
                        "SELECT (SELECT COUNT(*) FROM gis_thematic_unit_base b"
                                + " WHERE b.geom IS NOT NULL AND ST_Within(b.geom,"
                                + " ST_SetSRID(ST_GeomFromGeoJSON('" + polygon + "'), 4674))) AS units,"
                                + " (SELECT AVG(c.billed_consumption) FROM gis_thematic_unit_consumption c"
                                + " WHERE c.unit_id IN (SELECT unit_id FROM gis_thematic_unit_base b"
                                + " WHERE ST_Within(b.geom, ST_SetSRID(ST_GeomFromGeoJSON('"
                                + polygon + "'), 4674)))) AS avg_consumption")) {
                    rs.next();
                    assertThat(rs.getInt("units")).isEqualTo(2);
                    assertThat(rs.getBigDecimal("avg_consumption"))
                            .isEqualByComparingTo(new java.math.BigDecimal("15.0000000000000000"));
                }
            } finally {
                c.rollback();
            }
        }
    }
}
