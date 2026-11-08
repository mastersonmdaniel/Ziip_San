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
 * GIS-10 (ED-55): acesso QGIS no PostgreSQL real — core V12
 * (platform.gis_db_role) + views qgis_* e qgis_edit_* do SQL pendente
 * da ED-55. Prova o aceite do §7.1 item 7: a role de um tenant nao le
 * nem grava outro tenant (schema proprio aqui; o cenario de schema
 * compartilhado usa a mesma view, porque o filtro e pelo tenant da
 * role e nao pelo schema — ver Gis18ExpandedImportIT para a GIS-18),
 * tabela base sem escrita para a role, e escrita pela view editavel
 * caindo no pacote QGIS DRAFT do usuario, sem tocar na rede.
 *
 * <p>Padrao Gis04ChangeSetIT/Gis07ImportIT: migrations do classpath
 * em schema descartavel. O SQL da ED-55 entra como
 * {@code db/pendente/ED-55__gis_qgis_views_and_expanded_import.sql};
 * quando a integracao lhe atribuir o numero Flyway da serie V13_x, o
 * caminho desta lista muda para o nome numerado (pendencia declarada
 * no relatorio da ED-55). Roles de banco criadas aqui sao descartadas
 * no fim de cada teste (DROP ROLE IF EXISTS).
 *
 * <p>GRANTS DA ROLE (correcao A1 pos-confirmacao): a prescricao
 * original desta IT concedia SOMENTE as views ("NADA nas tabelas
 * base") — incompativel com {@code security_invoker = true}: o
 * PostgreSQL confere os privilegios da base como o invocador, e a
 * role recebia {@code permission denied for table gis_node} em toda
 * leitura e edicao (achado GRAVE A1 da revisao de confirmacao,
 * provado em banco real). O desenho corrigido, aplicado em
 * {@code createQgisRole}, concede o SELECT minimo nas cinco tabelas
 * base sob as views — a escrita em si e do gatilho SECURITY DEFINER
 * sobre as tabelas de pacote — mantendo a role sem
 * INSERT/UPDATE/DELETE em tabela base, sem grant nas tabelas de
 * pacote e sem BYPASSRLS; a barreira entre tenants e a RLS somada
 * ao filtro pelo tenant da role. As assercoes abaixo provam o
 * desenho corrigido, inclusive a escrita DIRETA na base recusada.
 *
 * <p>LIMITE DECLARADO da prova (correcao D9 da revisao cruzada): o
 * setup roda na conexao do migrador com a GUC
 * {@code app.current_tenant} setada — ela e exigencia da RLS das
 * tabelas do tenant (register_tenant_table: WITH CHECK pela GUC)
 * para os INSERTs de seed/migracao, NAO e a identidade da sessao
 * QGIS. A identidade QGIS e resolvida por
 * {@code platform.gis_role_tenant()} via session_user, sem GUC — o
 * que o teste {@code roleIdentityDoesNotDependOnSessionGuc} prova
 * com a GUC esvaziada. O que esta IT NAO prova: leitura/escrita
 * pelas views com a GUC ausente em conexao separada — com
 * security_invoker=true, a RLS da tabela base avalia a GUC da sessao
 * invocadora, e a sessao QGIS real nunca a seta; como o endpoint
 * dedicado/rotina trata essa GUC (ou se a leitura passa a outro
 * desenho) e pendencia de integracao nomeada no relatorio
 * (pendencia 12, quadro completo pos-confirmacao). As assercoes de
 * isolamento abaixo valem, portanto, para o desenho entregue
 * (filtro pelo tenant da role + RLS com GUC do setup).
 */
class Gis10QgisIT {

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
            "db/migration/tenant/V13_11__gis_import_staging.sql",
            "db/pendente/ED-55__gis_qgis_views_and_expanded_import.sql");
    private static final String CORE_V12 =
            "db/migration/core/V12__gis_db_role.sql";

    private static final UUID USER_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID USER_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

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
        // Core V12 antes das views: qgis_* resolvem platform.gis_role_tenant().
        st.execute(migrationSql(CORE_V12, tenant));
        for (String m : LEGACY_MIGRATIONS) {
            st.execute(migrationSql(m, tenant));
        }
        for (String m : GIS_MIGRATIONS) {
            st.execute(migrationSql(m, tenant));
        }
        st.execute("INSERT INTO public.tenant_catalog (id, schema_name, name) VALUES ('"
                + tenant + "', '" + schema + "', 'Tenant descartavel GIS-10')"
                + " ON CONFLICT (id) DO NOTHING");
        st.execute("SELECT set_config('app.current_tenant', '" + tenant + "', true)");
        return new Scratch(tenant, schema);
    }

    private static String migrationSql(String path, UUID tenant) throws IOException {
        try (InputStream in = Gis10QgisIT.class.getClassLoader().getResourceAsStream(path)) {
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

    /** Cria a role real (sem privilegio) e o mapeamento ACTIVE (core V12). */
    private static String createQgisRole(Statement st, Scratch s, UUID user,
                                         String roleName) throws Exception {
        st.execute("DROP ROLE IF EXISTS " + roleName);
        st.execute("CREATE ROLE " + roleName
                + " LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION"
                + " CONNECTION LIMIT 2");
        st.execute("ALTER ROLE " + roleName + " SET statement_timeout = '30s'");
        st.execute("INSERT INTO platform.gis_db_role (role_name, tenant_id,"
                + " user_id, status, valid_until, requested_by, approved_by)"
                + " VALUES ('" + roleName + "', '" + s.tenant() + "', '" + user
                + "', 'ACTIVE', NOW() + INTERVAL '30 days', '" + USER_A
                + "', '" + USER_B + "')");
        st.execute("GRANT USAGE ON SCHEMA " + s.schema() + " TO " + roleName);
        // Correcao R1 (pos-segunda-confirmacao): USAGE no schema
        // platform — sem ele, a chamada por nome
        // platform.gis_role_tenant()/gis_role_user() como a role
        // (teste roleIdentityDoesNotDependOnSessionGuc) falha com
        // permission denied for schema platform sob a ACL padrao.
        st.execute("GRANT USAGE ON SCHEMA platform TO " + roleName);
        // Prescricao corrigida (A1 pos-confirmacao): views de leitura,
        // views editaveis e o SELECT MINIMO nas tabelas base sob as
        // views — sem ele, o security_invoker faz toda leitura/edicao
        // falhar com permission denied (a prescricao anterior, so
        // views, era a codificacao do defeito A1). NENHUM
        // INSERT/UPDATE/DELETE em tabela base e NENHUM grant nas
        // tabelas de pacote: a escrita continua sendo so pelo
        // gatilho SECURITY DEFINER, no pacote DRAFT do usuario.
        st.execute("GRANT SELECT ON " + s.schema() + ".qgis_node, "
                + s.schema() + ".qgis_arc, " + s.schema() + ".qgis_structure, "
                + s.schema() + ".qgis_connection, " + s.schema() + ".qgis_zone"
                + " TO " + roleName);
        st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON "
                + s.schema() + ".qgis_edit_node, " + s.schema() + ".qgis_edit_arc, "
                + s.schema() + ".qgis_edit_structure TO " + roleName);
        st.execute("GRANT SELECT ON " + s.schema() + ".gis_node, "
                + s.schema() + ".gis_arc, " + s.schema() + ".gis_structure, "
                + s.schema() + ".gis_connection, " + s.schema() + ".gis_zone"
                + " TO " + roleName);
        st.execute("GRANT EXECUTE ON FUNCTION platform.gis_role_tenant(),"
                + " platform.gis_role_user() TO " + roleName);
        return roleName;
    }

    private static UUID seedNode(Statement st, Scratch s, String code) throws SQLException {
        String typeId = queryString(st,
                "SELECT id FROM gis_feature_type WHERE factory_code = 'gis01:GATE_VALVE'");
        UUID id = UUID.randomUUID();
        // Correcao D2 da revisao cruzada: o seed anterior inseria
        // IN_SERVICE direto (a gis_package_guard da V13_9 recusa —
        // Gis04ChangeSetIT.directInsertInServiceRefused) com POINT
        // 2D numa coluna geometry(PointZ, 4674). Seed correto:
        // PLANNED (a guarda so recusa IN_SERVICE/UNDER_CONSTRUCTION
        // fora de pacote) com POINTZ. As views qgis_* nao filtram
        // ciclo de vida, logo a contagem dos testes segue valida, e o
        // UPDATE pela qgis_edit_* continua exercitando o pacote
        // DRAFT do usuario.
        st.execute("INSERT INTO gis_node (id, tenant_id, code, feature_type_id,"
                + " lifecycle_status, capture_method, geom) VALUES ('" + id + "', '"
                + s.tenant() + "', '" + code + "', '" + typeId
                + "', 'PLANNED', 'DIGITIZED', ST_GeomFromText('POINTZ(-48.5 -26.9 0)', 4674))");
        return id;
    }

    @Test
    @DisplayName("role le so o proprio tenant pela view; sem mapeamento ativo, nao le nada")
    void roleReadsOnlyOwnTenant() throws Exception {
        try (Connection c = ProvisionedTenant.connect(); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            Scratch a = scratch(st);
            seedNode(st, a, "N-A1");
            String role = createQgisRole(st, a, USER_A, "qgis_it_a1");
            st.execute("SET SESSION AUTHORIZATION " + role);
            assertThat(count(st, "SELECT count(*) FROM " + a.schema() + ".qgis_node"))
                    .isEqualTo(1);
            // Correcao A1: com o SELECT minimo na base (security_
            // invoker), a leitura DIRETA na base tambem so enxerga o
            // proprio tenant (RLS) — e a escrita direta na base segue
            // RECUSADA: a role nao tem INSERT/UPDATE/DELETE nela.
            assertThat(count(st, "SELECT count(*) FROM " + a.schema() + ".gis_node"))
                    .isEqualTo(1);
            // Savepoint: o erro esperado aborta a transacao corrente
            // no PostgreSQL; o rollback ao savepoint destrava a IT.
            var spBase = c.setSavepoint();
            assertThatThrownBy(() -> st.execute(
                    "UPDATE " + a.schema() + ".gis_node SET attributes = '{}'::jsonb"))
                    .isInstanceOf(SQLException.class);
            c.rollback(spBase);
            st.execute("SET SESSION AUTHORIZATION " + role);
            // Revogacao: o mapeamento sai de ACTIVE e a view esvazia na
            // proxima consulta (revogacao efetiva, §7.1 item 6).
            st.execute("RESET SESSION AUTHORIZATION");
            st.execute("UPDATE platform.gis_db_role SET status = 'REVOKED'"
                    + " WHERE role_name = '" + role + "'");
            st.execute("SET SESSION AUTHORIZATION " + role);
            assertThat(count(st, "SELECT count(*) FROM " + a.schema() + ".qgis_node"))
            .isZero();
            st.execute("RESET SESSION AUTHORIZATION");
            st.execute("DROP ROLE IF EXISTS " + role);
            c.rollback();
        }
    }

    @Test
    @DisplayName("identidade da role nao depende da GUC da sessao (sessao QGIS real nunca a seta)")
    void roleIdentityDoesNotDependOnSessionGuc() throws Exception {
        try (Connection c = ProvisionedTenant.connect(); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            Scratch a = scratch(st);
            String role = createQgisRole(st, a, USER_A, "qgis_it_a3");
            st.execute("SET SESSION AUTHORIZATION " + role);
            // Sessao como a do QGIS real: sem app.current_tenant
            // (valor vazio == nao setada para a politica de RLS, que
            // usa NULLIF(current_setting(...), '')). A resolucao do
            // tenant/usuario e por session_user (core V12), nao por GUC.
            st.execute("SELECT set_config('app.current_tenant', '', true)");
            assertThat(queryString(st, "SELECT platform.gis_role_tenant()::text"))
                    .isEqualTo(a.tenant().toString());
            assertThat(queryString(st, "SELECT platform.gis_role_user()::text"))
                    .isEqualTo(USER_A.toString());
            st.execute("RESET SESSION AUTHORIZATION");
            st.execute("DROP ROLE IF EXISTS " + role);
            c.rollback();
        }
    }

    @Test
    @DisplayName("role de B nao le o schema de A nem escreve na tabela base de A")
    void roleCannotReadOrWriteAnotherTenant() throws Exception {
        try (Connection c = ProvisionedTenant.connect(); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            Scratch a = scratch(st);
            seedNode(st, a, "N-A1");
            Scratch b = scratch(st);
            String roleB = createQgisRole(st, b, USER_B, "qgis_it_b1");
            st.execute("SET SESSION AUTHORIZATION " + roleB);
            // Schema de A: a role de B nem enxerga a view de A sem USAGE,
            // e com a view do proprio schema nao ve a feicao de A.
            assertThat(count(st, "SELECT count(*) FROM " + b.schema() + ".qgis_node"))
                    .isZero();
            // Savepoints entre as recusas esperadas: sem eles, o
            // primeiro erro abortaria a transacao e a segunda recusa
            // passaria pelo motivo errado (transacao abortada).
            var spView = c.setSavepoint();
            assertThatThrownBy(() -> st.executeQuery(
                    "SELECT count(*) FROM " + a.schema() + ".qgis_node"))
                    .isInstanceOf(SQLException.class);
            c.rollback(spView);
            st.execute("SET SESSION AUTHORIZATION " + roleB);
            // Tabela base de A: a role de B nao tem USAGE nem grant no
            // schema de A (o SELECT minimo da correcao A1 e so no
            // PROPRIO schema) — leitura e escrita recusadas.
            var spBase = c.setSavepoint();
            assertThatThrownBy(() -> st.executeQuery(
                    "SELECT count(*) FROM " + a.schema() + ".gis_node"))
                    .isInstanceOf(SQLException.class);
            c.rollback(spBase);
            st.execute("SET SESSION AUTHORIZATION " + roleB);
            var spDel = c.setSavepoint();
            assertThatThrownBy(() -> st.execute(
                    "DELETE FROM " + a.schema() + ".gis_node"))
                    .isInstanceOf(SQLException.class);
            c.rollback(spDel);
            st.execute("RESET SESSION AUTHORIZATION");
            st.execute("DROP ROLE IF EXISTS " + roleB);
            c.rollback();
        }
    }

    @Test
    @DisplayName("escrita pela qgis_edit_* cai no pacote QGIS DRAFT do usuario; a rede nao muda")
    void editViewWritesIntoDraftPackage() throws Exception {
        try (Connection c = ProvisionedTenant.connect(); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            Scratch a = scratch(st);
            UUID node = seedNode(st, a, "N-A1");
            String role = createQgisRole(st, a, USER_A, "qgis_it_a2");
            st.execute("SET SESSION AUTHORIZATION " + role);
            st.execute("UPDATE " + a.schema() + ".qgis_edit_node"
                    + " SET attributes = '{\"note\":\"qgis\"}'::jsonb"
                    + " WHERE id = '" + node + "'");
            st.execute("RESET SESSION AUTHORIZATION");
            // A rede em operacao nao foi tocada...
            assertThat(queryString(st, "SELECT attributes::text FROM gis_node"
                    + " WHERE id = '" + node + "'")).doesNotContain("qgis");
            // ...e existe 1 item UPDATE no pacote QGIS DRAFT do usuario.
            assertThat(count(st, "SELECT count(*) FROM gis_change_set_item i"
                    + " JOIN gis_change_set s ON s.id = i.change_set_id"
                    + " WHERE s.type = 'QGIS' AND s.channel = 'QGIS'"
                    + "   AND s.status = 'DRAFT' AND s.author_id = '" + USER_A + "'"
                    + "   AND i.operation = 'UPDATE' AND i.feature_id = '" + node + "'"))
                    .isEqualTo(1);
            st.execute("DROP ROLE IF EXISTS " + role);
            c.rollback();
        }
    }
}
