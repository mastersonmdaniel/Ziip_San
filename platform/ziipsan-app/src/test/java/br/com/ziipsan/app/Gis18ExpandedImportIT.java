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
 * GIS-18 (ED-55): fila de casamento (SG-1349), conciliacao (SG-1356) e
 * implantacao em tres etapas com teto de 60 dias da amostra, sobre o
 * staging da GIS-07 (V13_11) e o SQL pendente da ED-55. Padrao
 * Gis07ImportIT. Os formatos novos (DGN v7, INP, GNSS) sao cobertos
 * nas regras puras (GisExpandedImportRulesTest): a leitura binaria e
 * do worker GDAL e o leitor de INP e da GIS-12 (ED-63) — esta IT prova
 * o que e do banco: fila, decisao e travas.
 */
class Gis18ExpandedImportIT {

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
            "db/migration/tenant/V13_11__gis_import_staging.sql",
            "db/pendente/ED-55__gis_qgis_views_and_expanded_import.sql");

    private static final String CORE_V12 =
            "db/migration/core/V12__gis_db_role.sql";
    private static final String HASH_A = "a".repeat(64);
    private static final UUID DECIDER = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @BeforeAll
    static void setUp() {
        ProvisionedTenant.ensure();
    }

    private record Scratch(UUID tenant, String schema, UUID batch) {
    }

    private static Scratch scratch(Statement st) throws Exception {
        UUID tenant = UUID.randomUUID();
        String schema = "it_gis_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        st.execute("CREATE SCHEMA " + schema);
        st.execute("SET LOCAL search_path = " + schema + ", public");
        // Core V12 antes das views qgis_* (platform.gis_role_tenant()).
        st.execute(migrationSql(CORE_V12, tenant));
        for (String m : LEGACY_MIGRATIONS) {
            st.execute(migrationSql(m, tenant));
        }
        for (String m : GIS_MIGRATIONS) {
            st.execute(migrationSql(m, tenant));
        }
        st.execute("INSERT INTO public.tenant_catalog (id, schema_name, name) VALUES ('"
                + tenant + "', '" + schema + "', 'Tenant descartavel GIS-18')"
                + " ON CONFLICT (id) DO NOTHING");
        st.execute("SELECT set_config('app.current_tenant', '" + tenant + "', true)");
        UUID batch = UUID.randomUUID();
        st.execute("INSERT INTO gis_import_batch (id, tenant_id, source_format,"
                + " original_file_name, original_sha256) VALUES ('" + batch + "', '"
                + tenant + "', 'DGN_V7', 'rede.dgn', '" + HASH_A + "')");
        return new Scratch(tenant, schema, batch);
    }

    private static String migrationSql(String path, UUID tenant) throws IOException {
        try (InputStream in = Gis18ExpandedImportIT.class.getClassLoader().getResourceAsStream(path)) {
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

    @Test
    @DisplayName("fila SG-1349: item sem chave e recusado; REJECTED sem decisor e recusado")
    void matchQueueGuards() throws Exception {
        try (Connection c = ProvisionedTenant.connect(); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            Scratch s = scratch(st);
            st.execute("INSERT INTO gis_unit_match_queue (tenant_id, batch_id,"
                    + " registration_key, match_method, match_status) VALUES ('"
                    + s.tenant() + "', '" + s.batch() + "', 'MAT-1', 'REGISTRATION', 'MATCHED')");
            var sp = c.setSavepoint();
            assertThatThrownBy(() -> st.execute(
                    "INSERT INTO gis_unit_match_queue (tenant_id, batch_id, match_status)"
                            + " VALUES ('" + s.tenant() + "', '" + s.batch()
                            + "', 'PENDING_REVIEW')"))
                    .isInstanceOf(SQLException.class);
            c.rollback(sp);
            var sp2 = c.setSavepoint();
            assertThatThrownBy(() -> st.execute(
                    "INSERT INTO gis_unit_match_queue (tenant_id, batch_id,"
                            + " registration_key, match_status) VALUES ('" + s.tenant()
                            + "', '" + s.batch() + "', 'MAT-2', 'REJECTED')"))
                    .isInstanceOf(SQLException.class);
            c.rollback(sp2);
            assertThat(count(st, "SELECT count(*) FROM gis_unit_match_queue")).isEqualTo(1);
            c.rollback();
        }
    }

    @Test
    @DisplayName("conciliacao SG-1356: item sem referencia e recusado; resolucao sem resolvedor e recusada")
    void reconciliationGuards() throws Exception {
        try (Connection c = ProvisionedTenant.connect(); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            Scratch s = scratch(st);
            st.execute("INSERT INTO gis_reconciliation_item (tenant_id, batch_id,"
                    + " outcome, base_ref) VALUES ('" + s.tenant() + "', '" + s.batch()
                    + "', 'ONLY_IN_BASE', 'base:123')");
            var sp = c.setSavepoint();
            assertThatThrownBy(() -> st.execute(
                    "INSERT INTO gis_reconciliation_item (tenant_id, batch_id, outcome)"
                            + " VALUES ('" + s.tenant() + "', '" + s.batch()
                            + "', 'AGREED')"))
                    .isInstanceOf(SQLException.class);
            c.rollback(sp);
            // Correcao D9 da revisao cruzada: o titulo prometia
            // "resolucao exige resolvedor" sem nenhum assert disso.
            // Agora prova de fato: resolution sem resolved_by e
            // recusada pelo ck_gis_reconciliation_resolution, e com
            // resolved_by e aceita.
            var sp2 = c.setSavepoint();
            assertThatThrownBy(() -> st.execute(
                    "INSERT INTO gis_reconciliation_item (tenant_id, batch_id,"
                            + " outcome, base_ref, resolution) VALUES ('" + s.tenant()
                            + "', '" + s.batch() + "', 'ONLY_IN_BASE', 'base:124',"
                            + " 'KEEP_BASE')"))
                    .isInstanceOf(SQLException.class);
            c.rollback(sp2);
            st.execute("INSERT INTO gis_reconciliation_item (tenant_id, batch_id,"
                    + " outcome, base_ref, resolution, resolved_by) VALUES ('"
                    + s.tenant() + "', '" + s.batch() + "', 'ONLY_IN_BASE', 'base:125',"
                    + " 'KEEP_BASE', '" + DECIDER + "')");
            assertThat(count(st, "SELECT count(*) FROM gis_reconciliation_item"
                    + " WHERE resolution IS NOT NULL AND resolved_by IS NOT NULL"))
                    .isEqualTo(1);
            c.rollback();
        }
    }

    @Test
    @DisplayName("lote INP entra apos a ampliacao do CHECK da ED-55; formato desconhecido segue recusado")
    void inpBatchAcceptedAfterCheckExtension() throws Exception {
        try (Connection c = ProvisionedTenant.connect(); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            Scratch s = scratch(st);
            // Correcao D3: INP_EPANET/INP_SWMM nao existiam no CHECK
            // da V13_11; o SQL pendente da ED-55 (rodado no scratch,
            // depois da V13_11) amplia a lista.
            st.execute("INSERT INTO gis_import_batch (id, tenant_id, source_format,"
                    + " original_file_name, original_sha256, source_srs_epsg, srs_source)"
                    + " VALUES ('" + UUID.randomUUID() + "', '" + s.tenant()
                    + "', 'INP_EPANET', 'rede.inp', '" + "d".repeat(64)
                    + "', 31982, 'OPERATOR_DECLARED')");
            st.execute("INSERT INTO gis_import_batch (id, tenant_id, source_format,"
                    + " original_file_name, original_sha256, source_srs_epsg, srs_source)"
                    + " VALUES ('" + UUID.randomUUID() + "', '" + s.tenant()
                    + "', 'INP_SWMM', 'drenagem.inp', '" + "b".repeat(64)
                    + "', 31982, 'OPERATOR_DECLARED')");
            assertThat(count(st, "SELECT count(*) FROM gis_import_batch"
                    + " WHERE source_format LIKE 'INP_%'")).isEqualTo(2);
            // Correcao D3a (pos-confirmacao): a ampliacao tem de
            // REMOVER a constraint automatica da V13_11, nao somar a
            // ela. A primeira versao do bloco procurava a definicao
            // por 'source_format IN (' — forma que o PostgreSQL nunca
            // devolve (ele desparseia o IN como '= ANY (ARRAY[...])')
            // — e as duas constraints conviviam, recusando o INP.
            // Ao final, exatamente UMA constraint de lista de
            // source_format vigora: a recriada pela ED-55.
            assertThat(count(st, "SELECT count(*) FROM pg_constraint"
                    + " WHERE conrelid = 'gis_import_batch'::regclass"
                    + " AND contype = 'c'"
                    + " AND pg_get_expr(conbin, conrelid)"
                    + " LIKE '%source_format = ANY (ARRAY[%'")).isEqualTo(1);
            // A ampliacao nao derrubou o CHECK: formato fora da lista
            // completa continua recusado.
            var sp = c.setSavepoint();
            assertThatThrownBy(() -> st.execute(
                    "INSERT INTO gis_import_batch (tenant_id, source_format,"
                            + " original_file_name, original_sha256) VALUES ('"
                            + s.tenant() + "', 'INP', 'x.inp', '" + "c".repeat(64) + "')"))
                    .isInstanceOf(SQLException.class);
            c.rollback(sp);
            c.rollback();
        }
    }

    @Test
    @DisplayName("fila SG-1349: item AMBIGUOUS pode virar MATCHED com o candidato escolhido gravado")
    void ambiguousItemCanBeMatchedWithChosenCandidate() throws Exception {
        try (Connection c = ProvisionedTenant.connect(); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            Scratch s = scratch(st);
            // Correcao D4 no nivel do banco: o item ambiguo nasce sem
            // candidato unico, mas com o conjunto avaliado gravado; a
            // decisao humana informa o escolhido e ele fica no item.
            UUID unitA = UUID.randomUUID();
            UUID unitB = UUID.randomUUID();
            UUID item = UUID.randomUUID();
            st.execute("INSERT INTO gis_unit_match_queue (id, tenant_id, batch_id,"
                    + " registration_key, candidate_options, match_method, match_status)"
                    + " VALUES ('" + item + "', '" + s.tenant() + "', '" + s.batch()
                    + "', 'MAT-9', '[\"" + unitA + "\", \"" + unitB + "\"]'::jsonb,"
                    + " 'REGISTRATION', 'AMBIGUOUS')");
            st.execute("UPDATE gis_unit_match_queue SET match_status = 'MATCHED',"
                    + " candidate_unit_id = '" + unitB + "', decided_by = '" + DECIDER
                    + "', decided_at = clock_timestamp() WHERE id = '" + item + "'");
            assertThat(count(st, "SELECT count(*) FROM gis_unit_match_queue"
                    + " WHERE match_status = 'MATCHED' AND candidate_unit_id = '"
                    + unitB + "' AND decided_by = '" + DECIDER + "'")).isEqualTo(1);
            c.rollback();
        }
    }

    @Test
    @DisplayName("implantacao: amostra com prazo alem de 60 dias e recusada pelo banco")
    void rolloutSampleWindowGuard() throws Exception {
        try (Connection c = ProvisionedTenant.connect(); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            Scratch s = scratch(st);
            st.execute("INSERT INTO gis_rollout_plan (tenant_id, stage, title,"
                    + " is_sample_network, started_on, sample_deadline_on) VALUES ('"
                    + s.tenant() + "', 'STAGE_1', 'Amostra', TRUE,"
                    + " DATE '2027-08-02', DATE '2027-10-01')");
            var sp = c.setSavepoint();
            assertThatThrownBy(() -> st.execute(
                    "INSERT INTO gis_rollout_plan (tenant_id, stage, title,"
                            + " is_sample_network, started_on, sample_deadline_on) VALUES ('"
                            + s.tenant() + "', 'STAGE_2', 'Amostra tardia', TRUE,"
                            + " DATE '2027-08-02', DATE '2027-10-02')"))
                    .isInstanceOf(SQLException.class);
            c.rollback(sp);
            c.rollback();
        }
    }

    @Test
    @DisplayName("decisor da fila fica registrado (decisao humana auditavel)")
    void matchDecisionRecordsDecider() throws Exception {
        try (Connection c = ProvisionedTenant.connect(); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            Scratch s = scratch(st);
            UUID item = UUID.randomUUID();
            st.execute("INSERT INTO gis_unit_match_queue (id, tenant_id, batch_id,"
                    + " normalized_address, match_method, match_status) VALUES ('" + item
                    + "', '" + s.tenant() + "', '" + s.batch()
                    + "', 'RUA A 10', 'ADDRESS_PREFIX', 'PENDING_REVIEW')");
            st.execute("UPDATE gis_unit_match_queue SET match_status = 'REJECTED',"
                    + " decided_by = '" + DECIDER + "', decided_at = clock_timestamp()"
                    + " WHERE id = '" + item + "'");
            assertThat(count(st, "SELECT count(*) FROM gis_unit_match_queue"
                    + " WHERE decided_by = '" + DECIDER + "'")).isEqualTo(1);
            c.rollback();
        }
    }
}
