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
 * GIS-04 (P2.22): pacotes de edição da V13_9 no PostgreSQL real — guarda
 * de pacote por SQL direto, revisão por outra pessoa, aplicação numa só
 * transação, rebase, trava por área, ciclo de vida e "rede na data".
 *
 * <p>Segue EXATAMENTE o padrão do {@code Gis03TopologyIT}: as migrations
 * do classpath (com o placeholder tenant_id substituído, como o
 * TenantMigrator faz) contra um schema descartável, numa transação
 * desfeita no fim; cada teste monta o seu schema do zero. A diferença é
 * a V13_9 no fim da lista. A massa é sintética (coordenadas em torno de
 * -46, -23). Autor e revisor são UUIDs sintéticos: a identidade do
 * usuário vive no contexto da api; aqui quem fala é o banco.
 *
 * <p>Pós-revisão (correção A1): o {@code SET LOCAL search_path} do
 * setup simula o TenantMigrator (é ele quem fixa o path ao aplicar as
 * migrations, como na Gis03) — mas não pode mascarar a resolução das
 * funções da V13_9 no caminho da app, que não fixa search_path. O teste
 * {@code functionsResolveWithoutTenantSearchPath} volta o path para
 * {@code public} antes de cada exercício e chama tudo qualificado pelo
 * schema: guarda, aplicação e rede na data têm de se resolver sozinhas.
 */
class Gis04ChangeSetIT {

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
            "db/migration/tenant/V13_9__gis_change_sets.sql");

    private static final UUID AUTHOR = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID REVIEWER = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @BeforeAll
    static void setUp() {
        ProvisionedTenant.ensure();
    }

    // ------------------------------------------------------------------ setup

    private static Scratch legacyScratch(Statement st) throws Exception {
        UUID tenant = UUID.randomUUID();
        String schema = "it_gis_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        st.execute("CREATE SCHEMA " + schema);
        st.execute("SET LOCAL search_path = " + schema + ", public");
        for (String m : LEGACY_MIGRATIONS) {
            st.execute(migrationSql(m, tenant));
        }
        asTenant(st, tenant);
        return new Scratch(tenant, schema);
    }

    private static void migrate(Statement st, Scratch s) throws Exception {
        for (String m : GIS_MIGRATIONS) {
            st.execute(migrationSql(m, s.tenant()));
        }
        st.execute("INSERT INTO public.tenant_catalog (id, schema_name, name) VALUES ('"
                + s.tenant() + "', '" + s.schema() + "', 'Tenant descartável GIS-04')"
                + " ON CONFLICT (id) DO NOTHING");
        st.execute("INSERT INTO platform.tenant_vertical (tenant_id, vertical_id, state, activated_at)"
                + " VALUES ('" + s.tenant() + "', 'sanitation', 'ACTIVE', NOW())"
                + " ON CONFLICT (tenant_id, vertical_id) DO NOTHING");
        asTenant(st, s.tenant());
    }

    private static void asTenant(Statement st, UUID tenant) throws SQLException {
        st.execute("SELECT set_config('app.current_tenant', '" + tenant + "', true)");
    }

    private static void expectFailure(Connection c, Statement st, String sql, String messagePart) throws SQLException {
        var savepoint = c.setSavepoint();
        try {
            assertThatThrownBy(() -> st.execute(sql)).isInstanceOf(SQLException.class)
                    .hasMessageContaining(messagePart);
        } finally {
            c.rollback(savepoint);
        }
    }

    private static String migrationSql(String path, UUID tenant) throws IOException {
        try (InputStream in = Gis04ChangeSetIT.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as("migration %s no classpath", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("${tenant_id}", tenant.toString());
        }
    }

    private record Scratch(UUID tenant, String schema) {
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

    // ------------------------------------------------------------- pacotes

    private static UUID newSet(Statement st, UUID tenant, UUID author) throws SQLException {
        UUID id = UUID.randomUUID();
        st.execute("INSERT INTO gis_change_set (id, tenant_id, type, channel, author_id) VALUES ('"
                + id + "', '" + tenant + "', 'CORRECTION', 'WEB', '" + author + "')");
        return id;
    }

    private static UUID insertPlannedNode(Statement st, UUID tenant, String code, UUID setId,
                                          String pointWkt) throws SQLException {
        String typeId = queryString(st,
                "SELECT id FROM gis_feature_type WHERE factory_code = 'gis01:GATE_VALVE'");
        UUID id = UUID.randomUUID();
        st.execute("INSERT INTO gis_node (id, tenant_id, code, feature_type_id, lifecycle_status,"
                + " change_set_id, capture_method, geom) VALUES ('" + id + "', '" + tenant
                + "', '" + code + "', '" + typeId + "', 'PLANNED', '" + setId
                + "', 'DIGITIZED', ST_GeomFromText('" + pointWkt + "', 4674))");
        return id;
    }

    private static void addInsertItem(Statement st, UUID tenant, UUID setId, UUID featureId,
                                      String proposedJson) throws SQLException {
        addInsertItemFor(st, tenant, setId, "gis_node", featureId, proposedJson);
    }

    private static void addInsertItemFor(Statement st, UUID tenant, UUID setId, String table,
                                         UUID featureId, String proposedJson) throws SQLException {
        st.execute("INSERT INTO gis_change_set_item (tenant_id, change_set_id, operation,"
                + " feature_table, feature_id, proposed_state) VALUES ('" + tenant + "', '"
                + setId + "', 'INSERT', '" + table + "', '" + featureId + "', '"
                + proposedJson + "'::jsonb)");
    }

    private static void addUpdateItemFor(Statement st, UUID tenant, UUID setId, String table,
                                         UUID featureId, String proposedJson) throws SQLException {
        st.execute("INSERT INTO gis_change_set_item (tenant_id, change_set_id, operation,"
                + " feature_table, feature_id, base_version, base_snapshot, proposed_state)"
                + " SELECT '" + tenant + "', '" + setId + "', 'UPDATE', '" + table + "', id, version,"
                + " to_jsonb(f), '" + proposedJson + "'::jsonb FROM " + table + " f WHERE id = '"
                + featureId + "'");
    }

    private static UUID insertPlannedStructure(Statement st, UUID tenant, String code, UUID setId,
                                               String pointWkt) throws SQLException {
        String typeId = queryString(st,
                "SELECT id FROM gis_feature_type WHERE factory_code = 'gis01:RESERVOIR'");
        UUID id = UUID.randomUUID();
        st.execute("INSERT INTO gis_structure (id, tenant_id, code, feature_type_id, lifecycle_status,"
                + " change_set_id, capture_method, geom_point) VALUES ('" + id + "', '" + tenant
                + "', '" + code + "', '" + typeId + "', 'PLANNED', '" + setId
                + "', 'DIGITIZED', ST_GeomFromText('" + pointWkt + "', 4674))");
        return id;
    }

    private static void addUpdateItem(Statement st, UUID tenant, UUID setId, UUID featureId,
                                      String proposedJson) throws SQLException {
        st.execute("INSERT INTO gis_change_set_item (tenant_id, change_set_id, operation,"
                + " feature_table, feature_id, base_version, base_snapshot, proposed_state)"
                + " SELECT '" + tenant + "', '" + setId + "', 'UPDATE', 'gis_node', id, version,"
                + " to_jsonb(f), '" + proposedJson + "'::jsonb FROM gis_node f WHERE id = '"
                + featureId + "'");
    }

    private static void submit(Statement st, UUID setId) throws SQLException {
        st.execute("UPDATE gis_change_set SET status = 'SUBMITTED', submitted_at = clock_timestamp()"
                + " WHERE id = '" + setId + "'");
        st.execute("UPDATE gis_change_set SET status = 'IN_REVIEW' WHERE id = '" + setId + "'");
    }

    private static void apply(Statement st, UUID setId, UUID reviewer) throws SQLException {
        st.execute("SELECT gis_apply_change_set('" + setId + "', '" + reviewer + "')");
    }

    /** Cria um nó já em operação pelo caminho legítimo: pacote aplicado. */
    private static UUID operationalNode(Statement st, UUID tenant, String code, String pointWkt,
                                        String installedOn) throws SQLException {
        UUID setId = newSet(st, tenant, AUTHOR);
        UUID nodeId = insertPlannedNode(st, tenant, code, setId, pointWkt);
        addInsertItem(st, tenant, setId, nodeId,
                "{\"lifecycle_status\": \"IN_SERVICE\", \"installed_on\": \"" + installedOn + "\"}");
        submit(st, setId);
        apply(st, setId, REVIEWER);
        return nodeId;
    }

    // ---------------------------------------------------------------- testes

    @Test
    @DisplayName("SQL direto: feição já nasce em operação é recusada; PLANNED é aceita")
    void directInsertInServiceRefused() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                String typeId = queryString(st,
                        "SELECT id FROM gis_feature_type WHERE factory_code = 'gis01:GATE_VALVE'");
                expectFailure(c, st,
                        "INSERT INTO gis_node (tenant_id, code, feature_type_id, lifecycle_status,"
                                + " capture_method, geom) VALUES ('" + s.tenant() + "', 'DIRETO', '"
                                + typeId + "', 'IN_SERVICE', 'DIGITIZED',"
                                + " ST_GeomFromText('POINTZ(-46.0 -23.0 0)', 4674))",
                        "fora de pacote aplicado");
                UUID setId = newSet(st, s.tenant(), AUTHOR);
                insertPlannedNode(st, s.tenant(), "PROJ-1", setId, "POINTZ(-46.0 -23.0 0)");
                assertThat(count(st, "SELECT count(*) FROM gis_node WHERE code = 'PROJ-1'")).isEqualTo(1);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("fluxo completo: pacote aplicado põe a feição em operação e grava revisão e histórico")
    void fullFlowApplies() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                UUID nodeId = operationalNode(st, s.tenant(), "N-OP", "POINTZ(-46.0 -23.0 0)", "2024-01-10");
                assertThat(queryString(st, "SELECT lifecycle_status FROM gis_node WHERE id = '" + nodeId + "'"))
                        .isEqualTo("IN_SERVICE");
                assertThat(count(st, "SELECT count(*) FROM gis_change_set WHERE status = 'APPLIED'")).isEqualTo(1);
                assertThat(count(st, "SELECT count(*) FROM gis_change_set_review WHERE decision = 'APPROVED'"
                        + " AND reviewer_id = '" + REVIEWER + "'")).isEqualTo(1);
                assertThat(count(st, "SELECT count(*) FROM gis_node_history WHERE id = '" + nodeId + "'"))
                        .isEqualTo(1);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("SQL direto: alterar ou apagar feição em operação é recusado; apagar PLANNED de pacote aberto é aceito")
    void directWriteOnOperationalRefused() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                UUID nodeId = operationalNode(st, s.tenant(), "N-OP", "POINTZ(-46.0 -23.0 0)", "2024-01-10");
                expectFailure(c, st, "UPDATE gis_node SET name = 'x' WHERE id = '" + nodeId + "'",
                        "so muda por pacote aplicado");
                expectFailure(c, st, "DELETE FROM gis_node WHERE id = '" + nodeId + "'",
                        "nao pode ser apagada");
                UUID draftSet = newSet(st, s.tenant(), AUTHOR);
                UUID draftNode = insertPlannedNode(st, s.tenant(), "N-RASC", draftSet, "POINTZ(-46.002 -23.0 0)");
                st.execute("DELETE FROM gis_node WHERE id = '" + draftNode + "'");
                assertThat(count(st, "SELECT count(*) FROM gis_node WHERE id = '" + draftNode + "'")).isZero();
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("autor não aprova o próprio pacote: função de aplicação e INSERT de revisão recusam")
    void selfReviewRefusedInDatabase() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                UUID setId = newSet(st, s.tenant(), AUTHOR);
                UUID nodeId = insertPlannedNode(st, s.tenant(), "N-SELF", setId, "POINTZ(-46.0 -23.0 0)");
                addInsertItem(st, s.tenant(), setId, nodeId, "{\"lifecycle_status\": \"IN_SERVICE\"}");
                submit(st, setId);
                expectFailure(c, st, "SELECT gis_apply_change_set('" + setId + "', '" + AUTHOR + "')",
                        "autor nao aprova o proprio pacote");
                expectFailure(c, st,
                        "INSERT INTO gis_change_set_review (tenant_id, change_set_id, decision, reviewer_id)"
                                + " VALUES ('" + s.tenant() + "', '" + setId + "', 'APPROVED', '" + AUTHOR + "')",
                        "autor nao revisa o proprio pacote");
                assertThat(queryString(st, "SELECT status FROM gis_change_set WHERE id = '" + setId + "'"))
                        .isEqualTo("IN_REVIEW");
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("aprovação é atômica: item com base desatualizada desfaz tudo, inclusive o item bom")
    void applyIsAtomicOnStaleItem() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                UUID x = operationalNode(st, s.tenant(), "N-X", "POINTZ(-46.0 -23.0 0)", "2024-01-10");
                UUID y = operationalNode(st, s.tenant(), "N-Y", "POINTZ(-46.002 -23.0 0)", "2024-01-10");
                UUID setId = newSet(st, s.tenant(), AUTHOR);
                addUpdateItem(st, s.tenant(), setId, x, "{\"name\": \"X novo\"}");
                // Item com base inventada (999): a aplicação tem de abortar.
                st.execute("INSERT INTO gis_change_set_item (tenant_id, change_set_id, operation,"
                        + " feature_table, feature_id, base_version, proposed_state) VALUES ('"
                        + s.tenant() + "', '" + setId + "', 'UPDATE', 'gis_node', '" + y
                        + "', 999, '{\"name\": \"Y novo\"}'::jsonb)");
                submit(st, setId);
                expectFailure(c, st, "SELECT gis_apply_change_set('" + setId + "', '" + REVIEWER + "')",
                        "rebase exigido");
                assertThat(queryString(st, "SELECT name FROM gis_node WHERE id = '" + x + "'")).isNull();
                assertThat(queryString(st, "SELECT status FROM gis_change_set WHERE id = '" + setId + "'"))
                        .isEqualTo("IN_REVIEW");
                assertThat(count(st, "SELECT count(*) FROM gis_change_set_review WHERE change_set_id = '"
                        + setId + "'")).isZero();
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("rebase: depois de refazer a base sobre a versão atual, a aplicação passa")
    void rebaseThenApply() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                UUID nodeId = operationalNode(st, s.tenant(), "N-RB", "POINTZ(-46.0 -23.0 0)", "2024-01-10");
                UUID setA = newSet(st, s.tenant(), AUTHOR);
                addUpdateItem(st, s.tenant(), setA, nodeId, "{\"name\": \"via A\"}");
                submit(st, setA);
                UUID setB = newSet(st, s.tenant(), AUTHOR);
                addUpdateItem(st, s.tenant(), setB, nodeId, "{\"name\": \"via B\"}");
                submit(st, setB);
                apply(st, setB, REVIEWER);
                expectFailure(c, st, "SELECT gis_apply_change_set('" + setA + "', '" + REVIEWER + "')",
                        "rebase exigido");
                // Rebase: só a base muda (a guarda do item permite em IN_REVIEW).
                st.execute("UPDATE gis_change_set_item i SET base_version = f.version,"
                        + " base_snapshot = to_jsonb(f) FROM gis_node f"
                        + " WHERE i.change_set_id = '" + setA + "' AND f.id = i.feature_id");
                apply(st, setA, REVIEWER);
                assertThat(queryString(st, "SELECT name FROM gis_node WHERE id = '" + nodeId + "'"))
                        .isEqualTo("via A");
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("validação: ERROR aberto bloqueia; WARNING aberto exige justificativa do revisor")
    void validationGatesApproval() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                UUID setId = newSet(st, s.tenant(), AUTHOR);
                UUID nodeId = insertPlannedNode(st, s.tenant(), "N-VAL", setId, "POINTZ(-46.0 -23.0 0)");
                addInsertItem(st, s.tenant(), setId, nodeId, "{\"lifecycle_status\": \"IN_SERVICE\"}");
                submit(st, setId);
                st.execute("INSERT INTO gis_validation_issue (tenant_id, rule_code, severity,"
                        + " feature_table, feature_id, change_set_id, status) VALUES ('" + s.tenant()
                        + "', 'TEST_ERROR', 'ERROR', 'gis_node', '" + nodeId + "', '" + setId + "', 'OPEN')");
                expectFailure(c, st, "SELECT gis_apply_change_set('" + setId + "', '" + REVIEWER + "')",
                        "severidade erro");
                st.execute("UPDATE gis_validation_issue SET status = 'RESOLVED', resolved_at = clock_timestamp()"
                        + " WHERE change_set_id = '" + setId + "'");
                st.execute("INSERT INTO gis_validation_issue (tenant_id, rule_code, severity,"
                        + " feature_table, feature_id, change_set_id, status) VALUES ('" + s.tenant()
                        + "', 'TEST_WARN', 'WARNING', 'gis_node', '" + nodeId + "', '" + setId + "', 'OPEN')");
                expectFailure(c, st, "SELECT gis_apply_change_set('" + setId + "', '" + REVIEWER + "')",
                        "aviso exige justificativa");
                st.execute("SELECT gis_apply_change_set('" + setId + "', '" + REVIEWER
                        + "', 'aviso conferido em campo')");
                assertThat(queryString(st, "SELECT status FROM gis_change_set WHERE id = '" + setId + "'"))
                        .isEqualTo("APPLIED");
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("trava por área: outro pacote não grava na área travada; expirada a trava, grava")
    void areaLockBlocksOtherPackage() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                UUID nodeId = operationalNode(st, s.tenant(), "N-LK", "POINTZ(-46.0005 -23.0 0)", "2024-01-10");
                UUID lockSet = newSet(st, s.tenant(), AUTHOR);
                st.execute("INSERT INTO gis_edit_lock (tenant_id, change_set_id, geom, owner_id, expires_at)"
                        + " VALUES ('" + s.tenant() + "', '" + lockSet + "',"
                        + " ST_GeomFromText('POLYGON((-46.001 -23.001, -46.000 -23.001, -46.000 -22.999,"
                        + " -46.001 -22.999, -46.001 -23.001))', 4674), '" + AUTHOR
                        + "', clock_timestamp() + INTERVAL '1 day')");
                UUID other = newSet(st, s.tenant(), AUTHOR);
                addUpdateItem(st, s.tenant(), other, nodeId, "{\"name\": \"bloqueado\"}");
                submit(st, other);
                expectFailure(c, st, "SELECT gis_apply_change_set('" + other + "', '" + REVIEWER + "')",
                        "travada");
                st.execute("UPDATE gis_edit_lock SET expires_at = clock_timestamp() - INTERVAL '1 hour'"
                        + " WHERE change_set_id = '" + lockSet + "'");
                apply(st, other, REVIEWER);
                assertThat(queryString(st, "SELECT name FROM gis_node WHERE id = '" + nodeId + "'"))
                        .isEqualTo("bloqueado");
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("máquina de estados do pacote e do ciclo de vida recusam saltos")
    void stateMachinesRefuseJumps() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                UUID setId = newSet(st, s.tenant(), AUTHOR);
                expectFailure(c, st, "UPDATE gis_change_set SET status = 'APPLIED' WHERE id = '" + setId + "'",
                        "transicao de pacote invalida");
                UUID nodeId = operationalNode(st, s.tenant(), "N-SM", "POINTZ(-46.0 -23.0 0)", "2024-01-10");
                expectFailure(c, st,
                        "SELECT gis_apply_change_set('" + setId + "', '" + REVIEWER + "')",
                        "nao pode ser aprovado");
                assertThat(nodeId).isNotNull();
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("rede na data de negócio: instalada entra, abandonada sai, antes da instalação não existe")
    void networkAtBusinessDate() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                UUID alive = operationalNode(st, s.tenant(), "N-VIVA", "POINTZ(-46.0 -23.0 0)", "2024-01-10");
                UUID retired = operationalNode(st, s.tenant(), "N-APOS", "POINTZ(-46.002 -23.0 0)", "2024-01-10");
                UUID retireSet = newSet(st, s.tenant(), AUTHOR);
                st.execute("INSERT INTO gis_change_set_item (tenant_id, change_set_id, operation,"
                        + " feature_table, feature_id, base_version, base_snapshot, proposed_state)"
                        + " SELECT '" + s.tenant() + "', '" + retireSet + "', 'RETIRE', 'gis_node', id,"
                        + " version, to_jsonb(f), '{\"lifecycle_status\": \"ABANDONED\","
                        + " \"retired_reason\": \"REMOVED\", \"abandoned_on\": \"2024-06-01\"}'::jsonb"
                        + " FROM gis_node f WHERE id = '" + retired + "'");
                submit(st, retireSet);
                apply(st, retireSet, REVIEWER);

                assertThat(count(st, "SELECT count(*) FROM gis_network_at_business('2024-03-01')"
                        + " WHERE feature_id IN ('" + alive + "', '" + retired + "')")).isEqualTo(2);
                assertThat(count(st, "SELECT count(*) FROM gis_network_at_business('2024-07-01')"
                        + " WHERE feature_id = '" + retired + "'")).isZero();
                assertThat(count(st, "SELECT count(*) FROM gis_network_at_business('2024-07-01')"
                        + " WHERE feature_id = '" + alive + "'")).isEqualTo(1);
                assertThat(count(st, "SELECT count(*) FROM gis_network_at_business('2023-06-01')"
                        + " WHERE feature_id IN ('" + alive + "', '" + retired + "')")).isZero();
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("rede na data de registro: antes e depois da alteração, cada instante com seu cadastro")
    void networkAtRecorded() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                UUID setId = newSet(st, s.tenant(), AUTHOR);
                UUID nodeId = insertPlannedNode(st, s.tenant(), "N-REC", setId, "POINTZ(-46.0 -23.0 0)");
                addInsertItem(st, s.tenant(), setId, nodeId,
                        "{\"lifecycle_status\": \"IN_SERVICE\", \"installed_on\": \"2024-01-10\", \"name\": \"Nome A\"}");
                submit(st, setId);
                // Projetada não aparece nem na data de registro.
                assertThat(count(st, "SELECT count(*) FROM gis_network_at_recorded(clock_timestamp())"
                        + " WHERE feature_id = '" + nodeId + "'")).isZero();
                apply(st, setId, REVIEWER);
                String mid = queryString(st, "SELECT clock_timestamp()::text");
                UUID set2 = newSet(st, s.tenant(), AUTHOR);
                addUpdateItem(st, s.tenant(), set2, nodeId, "{\"name\": \"Nome B\"}");
                submit(st, set2);
                apply(st, set2, REVIEWER);
                assertThat(count(st, "SELECT count(*) FROM gis_network_at_recorded('" + mid + "')"
                        + " WHERE feature_id = '" + nodeId + "' AND snapshot ->> 'name' = 'Nome A'"))
                        .isEqualTo(1);
                assertThat(count(st, "SELECT count(*) FROM gis_network_at_recorded(clock_timestamp())"
                        + " WHERE feature_id = '" + nodeId + "' AND snapshot ->> 'name' = 'Nome B'"))
                        .isEqualTo(1);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("pacote aguardando revisão aparece na consulta das minhas tarefas (superfície da P2.28)")
    void pendingReviewSurface() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                UUID setId = newSet(st, s.tenant(), AUTHOR);
                UUID nodeId = insertPlannedNode(st, s.tenant(), "N-PEND", setId, "POINTZ(-46.0 -23.0 0)");
                addInsertItem(st, s.tenant(), setId, nodeId, "{\"lifecycle_status\": \"IN_SERVICE\"}");
                assertThat(count(st, "SELECT count(*) FROM gis_change_set_pending_review"
                        + " WHERE change_set_id = '" + setId + "'")).isZero();
                submit(st, setId);
                assertThat(count(st, "SELECT count(*) FROM gis_change_set_pending_review"
                        + " WHERE change_set_id = '" + setId + "' AND item_count = 1")).isEqualTo(1);
                apply(st, setId, REVIEWER);
                assertThat(count(st, "SELECT count(*) FROM gis_change_set_pending_review"
                        + " WHERE change_set_id = '" + setId + "'")).isZero();
            } finally {
                c.rollback();
            }
        }
    }

    // ------------------------------------------------- correções pós-revisão

    @Test
    @DisplayName("correção A2: GUC de aplicação forjada por SQL direto não escreve em operação")
    void forgedGucCannotWriteOperational() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                UUID nodeId = operationalNode(st, s.tenant(), "N-GUC", "POINTZ(-46.0 -23.0 0)", "2024-01-10");
                String typeId = queryString(st,
                        "SELECT id FROM gis_feature_type WHERE factory_code = 'gis01:GATE_VALVE'");
                // (i) GUC com pacote em DRAFT: pacote não está em estado aplicável.
                UUID draftSet = newSet(st, s.tenant(), AUTHOR);
                st.execute("SELECT set_config('app.gis_applying_change_set', '" + draftSet + "', true)");
                expectFailure(c, st, "UPDATE gis_node SET name = 'forjado' WHERE id = '" + nodeId + "'",
                        "so muda por pacote aplicado");
                // (ii) GUC com uuid de pacote inexistente.
                st.execute("SELECT set_config('app.gis_applying_change_set', '"
                        + UUID.randomUUID() + "', true)");
                expectFailure(c, st, "UPDATE gis_node SET name = 'forjado' WHERE id = '" + nodeId + "'",
                        "so muda por pacote aplicado");
                // (iii) GUC com pacote IN_REVIEW, mas a feição NÃO é item dele.
                UUID reviewSet = newSet(st, s.tenant(), AUTHOR);
                UUID otherNode = insertPlannedNode(st, s.tenant(), "N-GUC2", reviewSet,
                        "POINTZ(-46.002 -23.0 0)");
                addInsertItem(st, s.tenant(), reviewSet, otherNode, "{\"lifecycle_status\": \"IN_SERVICE\"}");
                submit(st, reviewSet);
                st.execute("SELECT set_config('app.gis_applying_change_set', '" + reviewSet + "', true)");
                expectFailure(c, st, "UPDATE gis_node SET name = 'forjado' WHERE id = '" + nodeId + "'",
                        "so muda por pacote aplicado");
                // (iv) INSERT nascendo em operação com a GUC do pacote em
                // revisão: a feição nova não é item dele.
                expectFailure(c, st,
                        "INSERT INTO gis_node (tenant_id, code, feature_type_id, lifecycle_status,"
                                + " change_set_id, capture_method, geom) VALUES ('" + s.tenant()
                                + "', 'FORJADO', '" + typeId + "', 'IN_SERVICE', '" + reviewSet
                                + "', 'DIGITIZED', ST_GeomFromText('POINTZ(-46.0 -23.0 0)', 4674))",
                        "fora de pacote aplicado");
                st.execute("SELECT set_config('app.gis_applying_change_set', '', true)");
                assertThat(queryString(st, "SELECT name FROM gis_node WHERE id = '" + nodeId + "'")).isNull();
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("correção A1: sem o schema do tenant no search_path, guarda, aplicação e rede na data se resolvem")
    void functionsResolveWithoutTenantSearchPath() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                String typeId = queryString(st,
                        "SELECT id FROM gis_feature_type WHERE factory_code = 'gis01:GATE_VALVE'");
                UUID nodeId = operationalNode(st, s.tenant(), "N-PATH", "POINTZ(-46.0 -23.0 0)", "2024-01-10");
                UUID setId = newSet(st, s.tenant(), AUTHOR);
                addUpdateItem(st, s.tenant(), setId, nodeId, "{\"name\": \"via caminho sem path\"}");
                submit(st, setId);
                String q = s.schema() + ".";
                // Guarda de pacote qualificada, com o path só em public.
                st.execute("SET LOCAL search_path = public");
                expectFailure(c, st,
                        "INSERT INTO " + q + "gis_node (tenant_id, code, feature_type_id, lifecycle_status,"
                                + " capture_method, geom) VALUES ('" + s.tenant() + "', 'SEMPATH', '"
                                + typeId + "', 'IN_SERVICE', 'DIGITIZED',"
                                + " ST_GeomFromText('POINTZ(-46.0 -23.0 0)', 4674))",
                        "fora de pacote aplicado");
                // Aplicação qualificada, idem.
                st.execute("SET LOCAL search_path = public");
                st.execute("SELECT " + q + "gis_apply_change_set('" + setId + "', '" + REVIEWER + "')");
                // Rede na data qualificada, idem.
                st.execute("SET LOCAL search_path = public");
                assertThat(count(st, "SELECT count(*) FROM " + q + "gis_network_at_business('2024-03-01')"
                        + " WHERE feature_id = '" + nodeId
                        + "' AND snapshot ->> 'name' = 'via caminho sem path'")).isEqualTo(1);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("correção M3: segunda trava sobreposta de outro pacote é recusada; a do mesmo pacote e a distante passam")
    void lockOverlapRefused() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                UUID setA = newSet(st, s.tenant(), AUTHOR);
                UUID setB = newSet(st, s.tenant(), AUTHOR);
                String area = "ST_GeomFromText('POLYGON((-46.001 -23.001, -46.000 -23.001,"
                        + " -46.000 -22.999, -46.001 -22.999, -46.001 -23.001))', 4674)";
                String overlap = "ST_GeomFromText('POLYGON((-46.0008 -23.001, -45.9998 -23.001,"
                        + " -45.9998 -22.999, -46.0008 -22.999, -46.0008 -23.001))', 4674)";
                String far = "ST_GeomFromText('POLYGON((-45.001 -23.001, -45.000 -23.001,"
                        + " -45.000 -22.999, -45.001 -22.999, -45.001 -23.001))', 4674)";
                st.execute("INSERT INTO gis_edit_lock (tenant_id, change_set_id, geom, owner_id, expires_at)"
                        + " VALUES ('" + s.tenant() + "', '" + setA + "', " + area + ", '" + AUTHOR
                        + "', clock_timestamp() + INTERVAL '1 day')");
                expectFailure(c, st,
                        "INSERT INTO gis_edit_lock (tenant_id, change_set_id, geom, owner_id, expires_at)"
                                + " VALUES ('" + s.tenant() + "', '" + setB + "', " + overlap + ", '" + AUTHOR
                                + "', clock_timestamp() + INTERVAL '1 day')",
                        "area ja travada");
                st.execute("INSERT INTO gis_edit_lock (tenant_id, change_set_id, geom, owner_id, expires_at)"
                        + " VALUES ('" + s.tenant() + "', '" + setA + "', " + overlap + ", '" + AUTHOR
                        + "', clock_timestamp() + INTERVAL '1 day')");
                st.execute("INSERT INTO gis_edit_lock (tenant_id, change_set_id, geom, owner_id, expires_at)"
                        + " VALUES ('" + s.tenant() + "', '" + setB + "', " + far + ", '" + AUTHOR
                        + "', clock_timestamp() + INTERVAL '1 day')");
                assertThat(count(st, "SELECT count(*) FROM gis_edit_lock")).isEqualTo(3);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("correção M3: trava sobre o geom_point de gis_structure bloqueia a aplicação de outro pacote")
    void lockOnStructurePointBlocks() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                UUID setS = newSet(st, s.tenant(), AUTHOR);
                UUID structId = insertPlannedStructure(st, s.tenant(), "EST-1", setS, "POINT(-46.0005 -23.0)");
                addInsertItemFor(st, s.tenant(), setS, "gis_structure", structId,
                        "{\"lifecycle_status\": \"IN_SERVICE\", \"installed_on\": \"2024-01-10\"}");
                submit(st, setS);
                apply(st, setS, REVIEWER);
                assertThat(queryString(st, "SELECT lifecycle_status FROM gis_structure WHERE id = '"
                        + structId + "'")).isEqualTo("IN_SERVICE");
                UUID lockSet = newSet(st, s.tenant(), AUTHOR);
                st.execute("INSERT INTO gis_edit_lock (tenant_id, change_set_id, geom, owner_id, expires_at)"
                        + " VALUES ('" + s.tenant() + "', '" + lockSet + "',"
                        + " ST_GeomFromText('POLYGON((-46.001 -23.001, -46.000 -23.001, -46.000 -22.999,"
                        + " -46.001 -22.999, -46.001 -23.001))', 4674), '" + AUTHOR
                        + "', clock_timestamp() + INTERVAL '1 day')");
                UUID other = newSet(st, s.tenant(), AUTHOR);
                addUpdateItemFor(st, s.tenant(), other, "gis_structure", structId, "{\"name\": \"bloqueado\"}");
                submit(st, other);
                expectFailure(c, st, "SELECT gis_apply_change_set('" + other + "', '" + REVIEWER + "')",
                        "travada");
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("correção M2: rede na data de negócio pelo caminho primário (histórico vigente na data)")
    void networkAtBusinessPrimaryPath() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                UUID nodeId = operationalNode(st, s.tenant(), "N-PRIM", "POINTZ(-46.0 -23.0 0)", "2024-01-10");
                // Versão de histórico vigente em 2024 (tempo do sistema
                // retrodatado): é ela que o caminho primário
                // (gis_feature_at no fim da data) tem de devolver — o
                // fallback devolveria os atributos atuais ("Nome atual").
                st.execute("INSERT INTO gis_node_history SELECT f.id, f.tenant_id, f.code,"
                        + " f.feature_type_id, f.catalog_item_id, 'Nome 2024', 'IN_SERVICE',"
                        + " f.retired_reason, f.operational_status, f.normal_status, f.condition_rating,"
                        + " DATE '2024-01-10', f.installed_on_precision, NULL, f.change_set_id,"
                        + " f.attributes, f.schema_version, f.capture_method, f.h_accuracy_m,"
                        + " f.v_accuracy_m, f.vertical_datum, f.captured_on, f.responsible_party_id,"
                        + " f.source, f.source_batch_id, f.source_ref, f.legacy_object_id,"
                        + " f.accuracy_status, f.asset_codes, f.dma_id, f.ground_elevation_m,"
                        + " f.rim_elevation_m, f.invert_elevation_m, f.depth_m, f.symbol_rotation_deg,"
                        + " f.pressure_zone, f.setting, f.geom, f.geom_web, f.created_at, f.updated_at,"
                        + " f.created_by, f.updated_by, f.version, f.tenant_schema,"
                        + " tstzrange(TIMESTAMPTZ '2024-01-10 00:00:00+00',"
                        + " TIMESTAMPTZ '2024-12-31 23:59:59+00'), NULL, NULL, 'TEST'"
                        + " FROM gis_node f WHERE f.id = '" + nodeId + "'");
                UUID renameSet = newSet(st, s.tenant(), AUTHOR);
                addUpdateItem(st, s.tenant(), renameSet, nodeId, "{\"name\": \"Nome atual\"}");
                submit(st, renameSet);
                apply(st, renameSet, REVIEWER);
                assertThat(count(st, "SELECT count(*) FROM gis_network_at_business('2024-03-01')"
                        + " WHERE feature_id = '" + nodeId + "' AND snapshot ->> 'name' = 'Nome 2024'"))
                        .isEqualTo(1);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("feição UNDER_CONSTRUCTION fica fora da rede na data (negócio e registro)")
    void underConstructionOutOfNetworkAt() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                UUID setId = newSet(st, s.tenant(), AUTHOR);
                UUID nodeId = insertPlannedNode(st, s.tenant(), "N-OBRA", setId, "POINTZ(-46.0 -23.0 0)");
                addInsertItem(st, s.tenant(), setId, nodeId,
                        "{\"lifecycle_status\": \"UNDER_CONSTRUCTION\", \"installed_on\": \"2024-01-10\"}");
                submit(st, setId);
                apply(st, setId, REVIEWER);
                assertThat(queryString(st, "SELECT lifecycle_status FROM gis_node WHERE id = '" + nodeId + "'"))
                        .isEqualTo("UNDER_CONSTRUCTION");
                assertThat(count(st, "SELECT count(*) FROM gis_network_at_business('2024-03-01')"
                        + " WHERE feature_id = '" + nodeId + "'")).isZero();
                assertThat(count(st, "SELECT count(*) FROM gis_network_at_recorded(clock_timestamp())"
                        + " WHERE feature_id = '" + nodeId + "'")).isZero();
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("correção M5: item e revisão não se apagam fora de pacote aberto; rebase não move o item de pacote")
    void itemAndReviewDeleteRefused() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                UUID setId = newSet(st, s.tenant(), AUTHOR);
                UUID nodeId = insertPlannedNode(st, s.tenant(), "N-DEL", setId, "POINTZ(-46.0 -23.0 0)");
                addInsertItem(st, s.tenant(), setId, nodeId, "{\"lifecycle_status\": \"IN_SERVICE\"}");
                // Em DRAFT o autor ainda pode remover o item.
                st.execute("DELETE FROM gis_change_set_item WHERE change_set_id = '" + setId + "'");
                assertThat(count(st, "SELECT count(*) FROM gis_change_set_item WHERE change_set_id = '"
                        + setId + "'")).isZero();
                addInsertItem(st, s.tenant(), setId, nodeId, "{\"lifecycle_status\": \"IN_SERVICE\"}");
                submit(st, setId);
                expectFailure(c, st, "DELETE FROM gis_change_set_item WHERE change_set_id = '" + setId + "'",
                        "nao pode ser apagado");
                // "Rebase" que tenta mover o item para outro pacote: recusado.
                UUID otherSet = newSet(st, s.tenant(), AUTHOR);
                expectFailure(c, st,
                        "UPDATE gis_change_set_item SET change_set_id = '" + otherSet
                                + "', base_version = 1 WHERE change_set_id = '" + setId + "'",
                        "itens so mudam");
                apply(st, setId, REVIEWER);
                expectFailure(c, st, "DELETE FROM gis_change_set_review WHERE change_set_id = '" + setId + "'",
                        "nao pode ser apagada");
                assertThat(count(st, "SELECT count(*) FROM gis_change_set_review WHERE change_set_id = '"
                        + setId + "'")).isEqualTo(1);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("reforço B2: guarda em outra tabela, UNDER_CONSTRUCTION direto, DELETE de pacote aprovado e salto de ciclo")
    void guardCoverageAcrossTablesAndLifecycle() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                String nodeType = queryString(st,
                        "SELECT id FROM gis_feature_type WHERE factory_code = 'gis01:GATE_VALVE'");
                String structType = queryString(st,
                        "SELECT id FROM gis_feature_type WHERE factory_code = 'gis01:RESERVOIR'");
                expectFailure(c, st,
                        "INSERT INTO gis_node (tenant_id, code, feature_type_id, lifecycle_status,"
                                + " capture_method, geom) VALUES ('" + s.tenant() + "', 'DIRETO-UC', '"
                                + nodeType + "', 'UNDER_CONSTRUCTION', 'DIGITIZED',"
                                + " ST_GeomFromText('POINTZ(-46.0 -23.0 0)', 4674))",
                        "fora de pacote aplicado");
                expectFailure(c, st,
                        "INSERT INTO gis_structure (tenant_id, code, feature_type_id, lifecycle_status,"
                                + " capture_method, geom_point) VALUES ('" + s.tenant() + "', 'DIRETO-EST', '"
                                + structType + "', 'IN_SERVICE', 'DIGITIZED',"
                                + " ST_GeomFromText('POINT(-46.0 -23.0)', 4674))",
                        "fora de pacote aplicado");
                // DELETE de PLANNED de pacote já aprovado: recusado.
                UUID setA = newSet(st, s.tenant(), AUTHOR);
                UUID nodeA = insertPlannedNode(st, s.tenant(), "N-APR", setA, "POINTZ(-46.0 -23.0 0)");
                addInsertItem(st, s.tenant(), setA, nodeA, "{\"lifecycle_status\": \"IN_SERVICE\"}");
                submit(st, setA);
                st.execute("UPDATE gis_change_set SET status = 'APPROVED', reviewed_by = '" + REVIEWER
                        + "' WHERE id = '" + setA + "'");
                expectFailure(c, st, "DELETE FROM gis_node WHERE id = '" + nodeA + "'",
                        "pertence a pacote aprovado");
                // Salto de ciclo ABANDONED -> IN_SERVICE via pacote: recusado.
                UUID retired = operationalNode(st, s.tenant(), "N-SALTO", "POINTZ(-46.002 -23.0 0)", "2024-01-10");
                UUID retireSet = newSet(st, s.tenant(), AUTHOR);
                st.execute("INSERT INTO gis_change_set_item (tenant_id, change_set_id, operation,"
                        + " feature_table, feature_id, base_version, base_snapshot, proposed_state)"
                        + " SELECT '" + s.tenant() + "', '" + retireSet + "', 'RETIRE', 'gis_node', id,"
                        + " version, to_jsonb(f), '{\"lifecycle_status\": \"ABANDONED\","
                        + " \"retired_reason\": \"REMOVED\", \"abandoned_on\": \"2024-06-01\"}'::jsonb"
                        + " FROM gis_node f WHERE id = '" + retired + "'");
                submit(st, retireSet);
                apply(st, retireSet, REVIEWER);
                UUID jumpSet = newSet(st, s.tenant(), AUTHOR);
                addUpdateItem(st, s.tenant(), jumpSet, retired, "{\"lifecycle_status\": \"IN_SERVICE\"}");
                submit(st, jumpSet);
                expectFailure(c, st, "SELECT gis_apply_change_set('" + jumpSet + "', '" + REVIEWER + "')",
                        "transicao de ciclo de vida invalida");
            } finally {
                c.rollback();
            }
        }
    }
}
