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
 * GIS-05 (P2.23): base da ligação no PostgreSQL real — catálogo único
 * {@code connection_statuses} com ids preservados, {@code service_connections}
 * 1:1 com o imóvel por tipo enquanto ativa, várias unidades por ligação em
 * condomínio, {@code connection_status_history} substituindo
 * {@code unit_status_history} com a contagem conferida, {@code connected_on}
 * do staging do legado (DAD-10), FK do ramal para a ligação, ramal
 * automático em pacote revisado e a consulta de ligação ativa sem ramal.
 *
 * <p>Segue o padrão do {@code Gis04ChangeSetIT}: migrations do classpath
 * contra um schema descartável, numa transação desfeita no fim. A cadeia
 * é a ordem real de versões (V1, V2, V3_1..V3_14, V9_1, V13_1..V13_10).
 * O cenário do staging roda uma segunda cadeia em que a V14_1 já foi
 * aplicada antes da V3_14 — o upgrade real de um tenant que já importou
 * o legado.
 *
 * <p>Massa sintética em torno de -46, -23. Situações, unidades e OS são
 * UUIDs e códigos sintéticos inseridos por SQL, como nas ITs GIS
 * anteriores: aqui quem fala é o banco.
 */
class Gis05ServiceConnectionIT {

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
            "db/migration/tenant/V3_13__vulnerable_proof_redaction.sql");

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
            "db/migration/tenant/V13_10__gis_service_connection_link.sql");

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
        String schema = "it_gis05_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        st.execute("CREATE SCHEMA " + schema);
        st.execute("SET LOCAL search_path = " + schema + ", public");
        for (String m : LEGACY_MIGRATIONS) {
            st.execute(migrationSql(m, tenant));
        }
        asTenant(st, tenant);
        return new Scratch(tenant, schema);
    }

    private static void migrateRegistry(Statement st, Scratch s) throws Exception {
        for (String m : REGISTRY_MIGRATIONS) {
            st.execute(migrationSql(m, s.tenant()));
        }
    }

    /** Cadeia completa na ordem real, SEM a V3_14: os testes inserem a massa antes dela. */
    private static void migrateAllButV314(Statement st, Scratch s) throws Exception {
        migrateRegistry(st, s);
        st.execute(migrationSql("db/migration/tenant/V9_1__service_order_engine.sql", s.tenant()));
        for (String m : GIS_MIGRATIONS) {
            if (!m.contains("V13_10")) {
                st.execute(migrationSql(m, s.tenant()));
            }
        }
        st.execute("INSERT INTO public.tenant_catalog (id, schema_name, name) VALUES ('"
                + s.tenant() + "', '" + s.schema() + "', 'Tenant descartável GIS-05')"
                + " ON CONFLICT (id) DO NOTHING");
        asTenant(st, s.tenant());
    }

    private static void applyV314(Statement st, Scratch s) throws Exception {
        st.execute(migrationSql(
                "db/migration/tenant/V3_14__service_connections.sql", s.tenant()));
    }

    private static void applyV1310(Statement st, Scratch s) throws Exception {
        st.execute(migrationSql(
                "db/migration/tenant/V13_10__gis_service_connection_link.sql", s.tenant()));
    }

    private static void asTenant(Statement st, UUID tenant) throws SQLException {
        st.execute("SELECT set_config('app.current_tenant', '" + tenant + "', true)");
    }

    private static void expectFailure(Connection c, Statement st, String sql, String messagePart)
            throws SQLException {
        var savepoint = c.setSavepoint();
        try {
            assertThatThrownBy(() -> st.execute(sql)).isInstanceOf(SQLException.class)
                    .hasMessageContaining(messagePart);
        } finally {
            c.rollback(savepoint);
        }
    }

    private static String migrationSql(String path, UUID tenant) throws IOException {
        try (InputStream in = Gis05ServiceConnectionIT.class.getClassLoader().getResourceAsStream(path)) {
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

    // ------------------------------------------------------------------ massa

    private static UUID waterStatus(Statement st, UUID tenant, String code, boolean connected,
                                    boolean supplies) throws SQLException {
        UUID id = UUID.randomUUID();
        st.execute("INSERT INTO water_statuses (id, tenant_id, code, name, has_network, has_connection,"
                + " bills, requires_network, connected, supplies, minimum_consumption, active) VALUES ('"
                + id + "', '" + tenant + "', '" + code + "', 'Situação " + code + "', true, "
                + (connected ? "true" : "false") + ", true, false, " + connected + ", " + supplies
                + ", false, true)");
        return id;
    }

    private static UUID sewerStatus(Statement st, UUID tenant, String code, boolean connected)
            throws SQLException {
        UUID id = UUID.randomUUID();
        st.execute("INSERT INTO sewer_statuses (id, tenant_id, code, name, has_network, has_connection,"
                + " bills, requires_network, connected, minimum_consumption, active) VALUES ('"
                + id + "', '" + tenant + "', '" + code + "', 'Situação " + code + "', true, "
                + (connected ? "true" : "false") + ", true, false, " + connected + ", false, true)");
        return id;
    }

    private static UUID unit(Statement st, UUID tenant, String registration, UUID waterStatusId,
                             UUID sewerStatusId, UUID parentId, String parentRelation,
                             String pointWkt) throws SQLException {
        UUID id = UUID.randomUUID();
        st.execute("INSERT INTO consumer_units (id, tenant_id, registration_number, check_digit, status,"
                + " water_status_id, sewer_status_id, parent_unit_id, parent_relation_type, coordinates)"
                + " VALUES ('" + id + "', '" + tenant + "', '" + registration + "', '0', 'ACTIVE', "
                + (waterStatusId == null ? "NULL" : "'" + waterStatusId + "'") + ", "
                + (sewerStatusId == null ? "NULL" : "'" + sewerStatusId + "'") + ", "
                + (parentId == null ? "NULL" : "'" + parentId + "'") + ", "
                + (parentRelation == null ? "NULL" : "'" + parentRelation + "'") + ", "
                + (pointWkt == null ? "NULL" : "ST_GeomFromText('" + pointWkt + "', 4674)") + ")");
        return id;
    }

    private static UUID serviceOrder(Statement st, UUID tenant) throws SQLException {
        UUID typeId = UUID.randomUUID();
        st.execute("INSERT INTO service_types (id, tenant_id, code, name, operation, effect_code,"
                + " indicator, target_classes) VALUES ('" + typeId + "', '" + tenant
                + "', 'TEST_LINK_" + typeId.toString().substring(0, 8)
                + "', 'Tipo de teste GIS-05', 'LINK', 'NONE', 'INTERNAL', ARRAY['CONSUMER_UNIT'])");
        UUID id = UUID.randomUUID();
        st.execute("INSERT INTO service_orders (id, tenant_id, service_type_id, origin, execution_mode,"
                + " created_by) VALUES ('" + id + "', '" + tenant + "', '" + typeId
                + "', 'INTERNAL', 'INTERNAL', '" + AUTHOR + "')");
        return id;
    }

    private static void unitHistory(Statement st, UUID tenant, UUID unitId, String kind, UUID fromId,
                                    UUID toId, String changedAt, UUID orderId) throws SQLException {
        st.execute("INSERT INTO unit_status_history (id, tenant_id, unit_id, service_kind,"
                + " from_status_id, to_status_id, changed_at, reason, service_order_id) VALUES ('"
                + UUID.randomUUID() + "', '" + tenant + "', '" + unitId + "', '" + kind + "', "
                + (fromId == null ? "NULL" : "'" + fromId + "'") + ", '" + toId + "', '"
                + changedAt + "', 'Mudança de teste', '" + orderId + "')");
    }

    // ------------------------------------------------------------------ testes

    @Test
    @DisplayName("catálogo único: ids preservados dos dois catálogos, kind e system_role derivados")
    void catalogUnifiedPreservesIdsAndRoles() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = scratch(st);
                migrateAllButV314(st, s);
                UUID active = waterStatus(st, s.tenant(), "LIGADO", true, true);
                UUID cut = waterStatus(st, s.tenant(), "CORTADO", true, false);
                UUID noLink = sewerStatus(st, s.tenant(), "SEM_LIG", false);
                applyV314(st, s);
                assertThat(queryString(st, "SELECT kind FROM connection_statuses WHERE id = '" + active + "'"))
                        .isEqualTo("WATER");
                assertThat(queryString(st, "SELECT system_role FROM connection_statuses WHERE id = '" + active + "'"))
                        .isEqualTo("ACTIVE");
                assertThat(queryString(st, "SELECT system_role FROM connection_statuses WHERE id = '" + cut + "'"))
                        .isEqualTo("CUT");
                assertThat(queryString(st, "SELECT system_role FROM connection_statuses WHERE id = '" + noLink + "'"))
                        .isEqualTo("NO_CONNECTION");
                assertThat(queryString(st, "SELECT kind FROM connection_statuses WHERE id = '" + noLink + "'"))
                        .isEqualTo("SEWER");
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("carga inicial: uma ligação por imóvel e tipo; condomínio partilha a ligação da âncora")
    void backfillOneConnectionPerPropertyWithCondominiumUnits() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = scratch(st);
                migrateAllButV314(st, s);
                UUID water = waterStatus(st, s.tenant(), "LIGADO", true, true);
                UUID anchor = unit(st, s.tenant(), "1001", water, null, null, null, "POINT(-46.0 -23.0)");
                UUID child = unit(st, s.tenant(), "1002", water, null, anchor, "CONDOMINIUM_MACRO", null);
                UUID solo = unit(st, s.tenant(), "2001", water, null, null, null, null);
                applyV314(st, s);
                assertThat(count(st, "SELECT count(*) FROM service_connections WHERE kind = 'WATER'"))
                        .isEqualTo(2);
                String anchorConnection = queryString(st,
                        "SELECT id FROM service_connections WHERE property_unit_id = '" + anchor + "'");
                assertThat(anchorConnection).isNotNull();
                assertThat(count(st, "SELECT count(*) FROM service_connection_units WHERE connection_id = '"
                        + anchorConnection + "'")).isEqualTo(2);
                assertThat(count(st, "SELECT count(*) FROM service_connection_units WHERE unit_id = '"
                        + child + "'")).isEqualTo(1);
                assertThat(count(st, "SELECT count(*) FROM service_connection_units WHERE unit_id = '"
                        + solo + "'")).isEqualTo(1);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("histórico migrado por contagem e unit_status_history removida (DAD-09)")
    void historyMigratedByCountAndOldTableDropped() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = scratch(st);
                migrateAllButV314(st, s);
                UUID linked = waterStatus(st, s.tenant(), "LIGADO", true, true);
                UUID cut = waterStatus(st, s.tenant(), "CORTADO", true, false);
                UUID u = unit(st, s.tenant(), "3001", cut, null, null, null, null);
                UUID order = serviceOrder(st, s.tenant());
                unitHistory(st, s.tenant(), u, "WATER", null, linked, "2020-05-01T10:00:00Z", order);
                unitHistory(st, s.tenant(), u, "WATER", linked, cut, "2023-08-15T09:30:00Z", order);
                applyV314(st, s);
                assertThat(count(st, "SELECT count(*) FROM connection_status_history")).isEqualTo(2);
                assertThat(queryString(st, "SELECT to_regclass('unit_status_history')::text")).isNull();
                assertThat(queryString(st, "SELECT connected_on_source FROM service_connections"
                        + " WHERE property_unit_id = '" + u + "'")).isEqualTo("STATUS_HISTORY");
                assertThat(queryString(st, "SELECT connected_on::text FROM service_connections"
                        + " WHERE property_unit_id = '" + u + "'")).isEqualTo("2020-05-01");
                assertThat(count(st, "SELECT count(*) FROM connection_status_history"
                        + " WHERE consumer_unit_id = '" + u + "' AND service_order_id = '" + order + "'"))
                        .isEqualTo(2);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("grupo que só existe no histórico também ganha ligação (nenhuma linha órfã)")
    void historyOnlyGroupGetsConnection() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = scratch(st);
                migrateAllButV314(st, s);
                UUID linked = waterStatus(st, s.tenant(), "LIGADO", true, true);
                UUID u = unit(st, s.tenant(), "4001", null, null, null, null, null);
                UUID order = serviceOrder(st, s.tenant());
                unitHistory(st, s.tenant(), u, "WATER", null, linked, "2019-03-10T08:00:00Z", order);
                applyV314(st, s);
                assertThat(count(st, "SELECT count(*) FROM service_connections WHERE property_unit_id = '"
                        + u + "' AND kind = 'WATER'")).isEqualTo(1);
                assertThat(count(st, "SELECT count(*) FROM connection_status_history")).isEqualTo(1);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("1:1 enquanto ativa: segunda ligação ativa do mesmo tipo é recusada; encerrada, outra nasce")
    void uniqueActiveConnectionPerPropertyAndKind() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = scratch(st);
                migrateAllButV314(st, s);
                UUID water = waterStatus(st, s.tenant(), "LIGADO", true, true);
                UUID u = unit(st, s.tenant(), "5001", water, null, null, null, null);
                applyV314(st, s);
                String insert = "INSERT INTO service_connections (tenant_id, property_unit_id, kind,"
                        + " status_id, connected_on, connected_on_source) VALUES ('" + s.tenant()
                        + "', '" + u + "', 'WATER', '" + water + "', DATE '2026-01-01', 'MANUAL')";
                expectFailure(c, st, insert, "uq_service_connections_active_property");
                st.execute("UPDATE service_connections SET ended_on = DATE '2026-02-01'"
                        + " WHERE property_unit_id = '" + u + "'");
                st.execute(insert);
                assertThat(count(st, "SELECT count(*) FROM service_connections WHERE property_unit_id = '"
                        + u + "'")).isEqualTo(2);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("connected_on vem do staging do legado quando o tenant já importou (DAD-10)")
    void connectedOnFromLegacyStaging() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = scratch(st);
                for (String m : LEGACY_MIGRATIONS) {
                    st.execute(migrationSql(m, s.tenant()));
                }
                migrateRegistry(st, s);
                UUID water = waterStatus(st, s.tenant(), "LIGADO", true, true);
                UUID u = unit(st, s.tenant(), "6001", water, null, null, null, null);
                st.execute(migrationSql("db/migration/tenant/V14_1__legacy_import.sql", s.tenant()));
                UUID batch = UUID.randomUUID();
                st.execute("INSERT INTO legacy_import_batches (id, tenant_id, source_system,"
                        + " mapping_name, mapping_version, external_batch_key, source_kind, status)"
                        + " VALUES ('" + batch + "', '" + s.tenant() + "', 'LEGADO_TESTE',"
                        + " 'teste-v1', '1', 'LOTE-1', 'CSV', 'APPLIED')");
                st.execute("INSERT INTO legacy_import_staging_units (id, tenant_id, batch_id,"
                        + " source_row_no, legacy_key, connected_on, status, applied_unit_id) VALUES ('"
                        + UUID.randomUUID() + "', '" + s.tenant() + "', '" + batch
                        + "', 1, 'CHAVE-1', DATE '2015-06-20', 'APPLIED', '" + u + "')");
                applyV314(st, s);
                assertThat(queryString(st, "SELECT connected_on::text FROM service_connections"
                        + " WHERE property_unit_id = '" + u + "'")).isEqualTo("2015-06-20");
                assertThat(queryString(st, "SELECT connected_on_source FROM service_connections"
                        + " WHERE property_unit_id = '" + u + "'")).isEqualTo("LEGACY_STAGING");
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("V13_10: FK do ramal para a ligação e FK do histórico para a OS existem e valem")
    void foreignKeysFromGis10Enforced() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = scratch(st);
                migrateAllButV314(st, s);
                UUID water = waterStatus(st, s.tenant(), "LIGADO", true, true);
                unit(st, s.tenant(), "7001", water, null, null, null, null);
                applyV314(st, s);
                applyV1310(st, s);
                assertThat(count(st, "SELECT count(*) FROM pg_constraint"
                        + " WHERE conname IN ('fk_gis_connection_service_connection',"
                        + " 'fk_connection_history_service_order')")).isEqualTo(2);
                String connectionId = queryString(st, "SELECT id FROM service_connections LIMIT 1");
                UUID order = serviceOrder(st, s.tenant());
                expectFailure(c, st,
                        "INSERT INTO connection_status_history (tenant_id, connection_id, kind,"
                                + " to_status_id, changed_at, service_order_id) VALUES ('" + s.tenant()
                                + "', '" + connectionId + "', 'WATER', '" + water
                                + "', NOW(), '" + UUID.randomUUID() + "')",
                        "fk_connection_history_service_order");
                st.execute("INSERT INTO connection_status_history (tenant_id, connection_id, kind,"
                        + " to_status_id, changed_at, service_order_id) VALUES ('" + s.tenant()
                        + "', '" + connectionId + "', 'WATER', '" + water + "', NOW(), '" + order + "')");
                assertThat(count(st, "SELECT count(*) FROM connection_status_history")).isEqualTo(1);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("ligação ativa sem ramal: a consulta da V13_10 lista, e exclui NO_CONNECTION e encerrada")
    void activeConnectionsWithoutServiceLineQuery() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = scratch(st);
                migrateAllButV314(st, s);
                UUID water = waterStatus(st, s.tenant(), "LIGADO", true, true);
                UUID noLink = waterStatus(st, s.tenant(), "SEM_LIG", false, false);
                // SEM_LIG nasce sem has_connection: papel NO_CONNECTION.
                st.execute("UPDATE water_statuses SET has_connection = false WHERE id = '" + noLink + "'");
                UUID active = unit(st, s.tenant(), "8001", water, null, null, null, null);
                unit(st, s.tenant(), "8002", noLink, null, null, null, null);
                UUID ended = unit(st, s.tenant(), "8003", water, null, null, null, null);
                applyV314(st, s);
                applyV1310(st, s);
                st.execute("UPDATE service_connections SET ended_on = DATE '2026-01-01'"
                        + " WHERE property_unit_id = '" + ended + "'");
                assertThat(count(st, "SELECT count(*) FROM gis_active_connections_without_service_line('"
                        + s.tenant() + "')")).isEqualTo(1);
                assertThat(queryString(st, "SELECT property_unit_id::text"
                        + " FROM gis_active_connections_without_service_line('" + s.tenant() + "')"))
                        .isEqualTo(active.toString());
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("ramal automático: gera até 100 m em pacote AUTO_SERVICE_LINE e a aprovação põe em operação")
    void autoServiceLineGeneratedInReviewedPackage() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = scratch(st);
                migrateAllButV314(st, s);
                UUID water = waterStatus(st, s.tenant(), "LIGADO", true, true);
                // Imóvel a ~55 m do arco (0,0005 grau de latitude) e outro a ~1,1 km.
                UUID near = unit(st, s.tenant(), "9001", water, null, null, null, "POINT(-46.0 -23.0005)");
                UUID far = unit(st, s.tenant(), "9002", water, null, null, null, "POINT(-46.0 -23.01)");
                applyV314(st, s);
                applyV1310(st, s);
                operationalArc(st, s.tenant());
                UUID setId = UUID.randomUUID();
                st.execute("INSERT INTO gis_change_set (id, tenant_id, type, channel, author_id) VALUES ('"
                        + setId + "', '" + s.tenant() + "', 'AUTO_SERVICE_LINE', 'AUTO', '" + AUTHOR + "')");
                String nearConnection = queryString(st,
                        "SELECT id FROM service_connections WHERE property_unit_id = '" + near + "'");
                String farConnection = queryString(st,
                        "SELECT id FROM service_connections WHERE property_unit_id = '" + far + "'");
                // Uma única execução da função (ela é volátil: cada chamada
                // tentaria gerar de novo, e a guarda de ramal existente é
                // o que a torna idempotente — ver a contagem abaixo).
                assertThat(queryString(st, "WITH g AS (SELECT * FROM gis_generate_auto_service_lines('"
                        + s.tenant() + "', '" + setId + "', 100)) SELECT"
                        + " (SELECT count(*) FROM g WHERE generated)::text || '/' ||"
                        + " (SELECT skip_reason FROM g WHERE service_connection_id = '"
                        + farConnection + "')")).isEqualTo("1/BEYOND_MAX_DISTANCE");
                assertThat(count(st, "SELECT count(*) FROM gis_connection WHERE service_connection_id = '"
                        + nearConnection + "'")).isEqualTo(1);
                assertThat(count(st, "SELECT count(*) FROM gis_connection WHERE service_connection_id = '"
                        + farConnection + "'")).isEqualTo(0);
                assertThat(queryString(st, "SELECT capture_method FROM gis_connection"
                        + " WHERE service_connection_id = '" + nearConnection + "'"))
                        .isEqualTo("AUTO_SERVICE_LINE");
                assertThat(queryString(st, "SELECT lifecycle_status FROM gis_connection"
                        + " WHERE service_connection_id = '" + nearConnection + "'"))
                        .isEqualTo("PLANNED");
                // Revisão por outra pessoa aplica o pacote: ramal em operação.
                String lineId = queryString(st, "SELECT id FROM gis_connection"
                        + " WHERE service_connection_id = '" + nearConnection + "'");
                st.execute("INSERT INTO gis_change_set_item (tenant_id, change_set_id, operation,"
                        + " feature_table, feature_id, proposed_state) VALUES ('" + s.tenant()
                        + "', '" + setId + "', 'INSERT', 'gis_connection', '" + lineId
                        + "', '{\"lifecycle_status\": \"IN_SERVICE\"}'::jsonb)");
                st.execute("UPDATE gis_change_set SET status = 'SUBMITTED', submitted_at = clock_timestamp()"
                        + " WHERE id = '" + setId + "'");
                st.execute("UPDATE gis_change_set SET status = 'IN_REVIEW' WHERE id = '" + setId + "'");
                st.execute("SELECT gis_apply_change_set('" + setId + "', '" + REVIEWER + "')");
                assertThat(queryString(st, "SELECT lifecycle_status FROM gis_connection WHERE id = '"
                        + lineId + "'")).isEqualTo("IN_SERVICE");
                assertThat(count(st, "SELECT count(*) FROM gis_active_connections_without_service_line('"
                        + s.tenant() + "') WHERE service_connection_id = '" + nearConnection + "'"))
                        .isEqualTo(0);
            } finally {
                c.rollback();
            }
        }
    }

    /** Arco de distribuição em operação pelo caminho legítimo (pacote aplicado, como na GIS-04). */
    private static void operationalArc(Statement st, UUID tenant) throws SQLException {
        String typeId = queryString(st,
                "SELECT id FROM gis_feature_type WHERE code = 'WATER_MAIN' LIMIT 1");
        UUID setId = UUID.randomUUID();
        st.execute("INSERT INTO gis_change_set (id, tenant_id, type, channel, author_id) VALUES ('"
                + setId + "', '" + tenant + "', 'CORRECTION', 'WEB', '" + AUTHOR + "')");
        UUID arcId = UUID.randomUUID();
        st.execute("INSERT INTO gis_arc (id, tenant_id, code, feature_type_id, lifecycle_status,"
                + " change_set_id, capture_method, network_function, geom) VALUES ('" + arcId + "', '"
                + tenant + "', 'ARC-REDE-1', '" + typeId + "', 'PLANNED', '" + setId
                + "', 'DIGITIZED', 'WATER_DISTRIBUTION',"
                + " ST_GeomFromText('LINESTRINGZ(-46.001 -23.0 0, -45.999 -23.0 0)', 4674))");
        st.execute("INSERT INTO gis_change_set_item (tenant_id, change_set_id, operation,"
                + " feature_table, feature_id, proposed_state) VALUES ('" + tenant + "', '" + setId
                + "', 'INSERT', 'gis_arc', '" + arcId
                + "', '{\"lifecycle_status\": \"IN_SERVICE\", \"installed_on\": \"2024-01-10\"}'::jsonb)");
        st.execute("UPDATE gis_change_set SET status = 'SUBMITTED', submitted_at = clock_timestamp()"
                + " WHERE id = '" + setId + "'");
        st.execute("UPDATE gis_change_set SET status = 'IN_REVIEW' WHERE id = '" + setId + "'");
        st.execute("SELECT gis_apply_change_set('" + setId + "', '" + REVIEWER + "')");
    }
}
