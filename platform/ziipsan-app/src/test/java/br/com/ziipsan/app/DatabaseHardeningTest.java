package br.com.ziipsan.app;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Donos e privilégios do banco depois do init de segurança (infra/postgres/init).
 *
 * <p>Achado C1 do PDCA de 12/10/2026: ziipsan_app era dona de current_tenant_id() e do banco, e um
 * CREATE OR REPLACE FUNCTION como ziipsan_app redefinia a função que as policies de RLS consultam.
 * Estes testes provam que nem a aplicação nem o migrador conseguem mais isso.
 */
class DatabaseHardeningTest {

    /** insufficient_privilege: "permission denied" e "must be owner of". */
    private static final String INSUFFICIENT_PRIVILEGE = "42501";

    private static final String REDEFINE_TENANT_FUNCTION =
            "CREATE OR REPLACE FUNCTION public.current_tenant_id() RETURNS uuid "
                    + "LANGUAGE sql STABLE AS $$ SELECT NULL::uuid $$";

    @BeforeAll
    static void setUp() {
        ProvisionedTenant.ensure();
    }

    @Test
    @DisplayName("a aplicação não consegue redefinir current_tenant_id()")
    void appCannotRedefineTenantFunction() {
        assertThatThrownBy(() -> exec(ProvisionedTenant.appDs.getConnection(), REDEFINE_TENANT_FUNCTION))
                .isInstanceOf(SQLException.class)
                .extracting(e -> ((SQLException) e).getSQLState())
                .isEqualTo(INSUFFICIENT_PRIVILEGE);
    }

    @Test
    @DisplayName("o migrador também não consegue redefinir current_tenant_id()")
    void migratorCannotRedefineTenantFunction() {
        assertThatThrownBy(() -> exec(ProvisionedTenant.migratorDs.getConnection(), REDEFINE_TENANT_FUNCTION))
                .isInstanceOf(SQLException.class)
                .extracting(e -> ((SQLException) e).getSQLState())
                .isEqualTo(INSUFFICIENT_PRIVILEGE);
    }

    @Test
    @DisplayName("P0.17: ziipsan_app só tem SELECT em public.tenant_catalog; a escrita é só da migração")
    void appHasOnlySelectOnTenantCatalog() throws SQLException {
        // Prova pelo catálogo de privilégios, não só pela tentativa (que também é feita
        // abaixo): o estado concedido é exatamente SELECT para a aplicação.
        assertThat(single("SELECT has_table_privilege('ziipsan_app', 'public.tenant_catalog', 'SELECT')::text"))
                .isEqualTo("true");
        for (String privilege : new String[]{"INSERT", "UPDATE", "DELETE"}) {
            assertThat(single("SELECT has_table_privilege('ziipsan_app', 'public.tenant_catalog', '" + privilege + "')::text"))
                    .as("a aplicação não pode ter %s no catálogo", privilege)
                    .isEqualTo("false");
        }
        assertThat(single("SELECT has_table_privilege('ziipsan_migrator', 'public.tenant_catalog', 'INSERT')::text"))
                .as("a role de migração (dona da tabela) mantém a escrita do catálogo")
                .isEqualTo("true");

        // E a tentativa confirma: INSERT/UPDATE/DELETE como app falham com 42501 antes de
        // qualquer checagem de constraint (o WHERE false não salva: o privilégio é exigido
        // mesmo sem linha afetada).
        String probe = "itest_probe_" + Long.toHexString(System.nanoTime() & 0xFFFFF);
        assertThatThrownBy(() -> exec(ProvisionedTenant.appDs.getConnection(),
                "INSERT INTO public.tenant_catalog (schema_name, name) VALUES ('" + probe + "', 'Probe')"))
                .isInstanceOf(SQLException.class)
                .extracting(e -> ((SQLException) e).getSQLState())
                .isEqualTo(INSUFFICIENT_PRIVILEGE);
        assertThatThrownBy(() -> exec(ProvisionedTenant.appDs.getConnection(),
                "UPDATE public.tenant_catalog SET name = 'Probe' WHERE false"))
                .isInstanceOf(SQLException.class)
                .extracting(e -> ((SQLException) e).getSQLState())
                .isEqualTo(INSUFFICIENT_PRIVILEGE);
        assertThatThrownBy(() -> exec(ProvisionedTenant.appDs.getConnection(),
                "DELETE FROM public.tenant_catalog WHERE false"))
                .isInstanceOf(SQLException.class)
                .extracting(e -> ((SQLException) e).getSQLState())
                .isEqualTo(INSUFFICIENT_PRIVILEGE);
    }

    @Test
    @DisplayName("a aplicação não cria tabela em public")
    void appCannotCreateInPublic() {
        assertThatThrownBy(() -> exec(ProvisionedTenant.appDs.getConnection(), "CREATE TABLE public.intruso (i int)"))
                .isInstanceOf(SQLException.class)
                .extracting(e -> ((SQLException) e).getSQLState())
                .isEqualTo(INSUFFICIENT_PRIVILEGE);
    }

    @Test
    @DisplayName("banco, public e current_tenant_id() pertencem a ziipsan_owner, que não tem login")
    void ownershipIsSeparated() throws SQLException {
        assertThat(single("SELECT pg_get_userbyid(proowner) FROM pg_proc "
                + "WHERE oid = 'public.current_tenant_id()'::regprocedure")).isEqualTo("ziipsan_owner");
        assertThat(single("SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname = current_database()"))
                .isEqualTo("ziipsan_owner");
        assertThat(single("SELECT pg_get_userbyid(nspowner) FROM pg_namespace WHERE nspname = 'public'"))
                .isEqualTo("ziipsan_owner");
        assertThat(single("SELECT rolcanlogin::text FROM pg_roles WHERE rolname = 'ziipsan_owner'")).isEqualTo("false");
        assertThat(single("SELECT (pg_has_role('ziipsan_app', 'ziipsan_owner', 'MEMBER') "
                + "OR pg_has_role('ziipsan_migrator', 'ziipsan_owner', 'MEMBER'))::text")).isEqualTo("false");
    }

    @Test
    @DisplayName("as roles do ZIIPSAN não são superusuário, e o cluster mantém um superusuário de operação")
    void superuserIsOnlyTheBootstrapRole() throws SQLException {
        assertThat(single("SELECT count(*)::text FROM pg_roles WHERE rolname LIKE 'ziipsan%' "
                + "AND (rolsuper OR rolbypassrls OR rolcreaterole OR rolcreatedb)")).isEqualTo("0");
        assertThat(Integer.parseInt(single("SELECT count(*)::text FROM pg_roles WHERE rolsuper"))).isPositive();
    }

    @Test
    @DisplayName("P0.18: current_tenant_id() vigente é SQL STABLE, não plpgsql")
    void tenantFunctionIsSqlStable() throws SQLException {
        assertThat(single("SELECT l.lanname FROM pg_proc p JOIN pg_language l ON l.oid = p.prolang "
                + "WHERE p.oid = 'public.current_tenant_id()'::regprocedure")).isEqualTo("sql");
        assertThat(single("SELECT provolatile FROM pg_proc "
                + "WHERE oid = 'public.current_tenant_id()'::regprocedure")).isEqualTo("s");
    }

    @Test
    @DisplayName("P0.18: current_tenant_id() devolve NULL com GUC ausente, vazia ou inválida, sem erro")
    void tenantFunctionReturnsNullForMissingOrInvalidGuc() throws SQLException {
        try (Connection c = ProvisionedTenant.appDs.getConnection();
             Statement st = c.createStatement()) {
            assertThat(queryOne(st, "SELECT current_tenant_id()")).isNull();
            st.execute("SET app.current_tenant = ''");
            assertThat(queryOne(st, "SELECT current_tenant_id()")).isNull();
            st.execute("SET app.current_tenant = 'nao-um-uuid'");
            assertThat(queryOne(st, "SELECT current_tenant_id()")).isNull();
            st.execute("SET app.current_tenant = '" + ProvisionedTenant.TENANT_ID + "'");
            assertThat(queryOne(st, "SELECT current_tenant_id()"))
                    .isEqualTo(ProvisionedTenant.TENANT_ID.toString());
        }
    }

    @Test
    @DisplayName("P0.18: plano de execução de consulta RLS multi-linha executa sem erro (corroboração)")
    void rlsMultiRowPlanRuns() throws SQLException {
        // A prova dura é a asserção de catálogo acima (language=sql, sem EXCEPTION
        // possível em SQL); o plano é a corroboração pedida pelo plano.
        try (Connection c = ProvisionedTenant.appDs.getConnection();
             Statement st = c.createStatement()) {
            st.execute("SET app.current_tenant = '" + ProvisionedTenant.TENANT_ID + "'");
            try (ResultSet rs = st.executeQuery("EXPLAIN SELECT * FROM "
                    + ProvisionedTenant.SCHEMA + ".consumer_units")) {
                assertThat(rs.next()).isTrue();
            }
        }
    }

    @Test
    @DisplayName("a função de auditoria (SECURITY DEFINER) tem search_path fixo")
    void auditFunctionHasFixedSearchPath() throws SQLException {
        String config = single("SELECT array_to_string(p.proconfig, ';') FROM pg_proc p "
                + "JOIN pg_namespace n ON n.oid = p.pronamespace "
                + "WHERE n.nspname = '" + ProvisionedTenant.SCHEMA + "' AND p.proname = 'audit_trigger_function'");
        assertThat(config).startsWith("search_path=pg_catalog, public, pg_temp");
    }

    private static void exec(Connection connection, String sql) throws SQLException {
        try (Connection c = connection; Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    private static String queryOne(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            assertThat(rs.next()).as("sem linha para: %s", sql).isTrue();
            return rs.getString(1);
        }
    }

    private static String single(String sql) throws SQLException {
        try (Connection c = ProvisionedTenant.appDs.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            assertThat(rs.next()).as("sem linha para: %s", sql).isTrue();
            return rs.getString(1);
        }
    }
}
