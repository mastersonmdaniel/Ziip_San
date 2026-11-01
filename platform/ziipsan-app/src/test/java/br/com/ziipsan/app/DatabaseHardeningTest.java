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

    private static String single(String sql) throws SQLException {
        try (Connection c = ProvisionedTenant.appDs.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            assertThat(rs.next()).as("sem linha para: %s", sql).isTrue();
            return rs.getString(1);
        }
    }
}
