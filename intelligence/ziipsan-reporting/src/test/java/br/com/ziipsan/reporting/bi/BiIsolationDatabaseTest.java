package br.com.ziipsan.reporting.bi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ED-47 — TESTE DE ISOLAMENTO do aceite, contra PostgreSQL real, no
 * padrão de integração do projeto (banco descartável, variáveis
 * {@code ZIIPSAN_IT_*}; ver {@code ReportViewsDatabaseTest} da P3.22):
 *
 * <p>"consulta livre e {@code SET} da GUC não leem outro tenant, em
 * schema próprio e compartilhado".
 *
 * <p>Massa: dois tenants no schema COMPARTILHADO {@code tenant_shared}
 * e um terceiro em schema PRÓPRIO {@code tenant_proprio}, todos sobre a
 * mesma view de origem {@code rpt_contas} (com {@code tenant_id}). O
 * provisionamento executado é o SQL GERADO por
 * {@link BiRoleProvisioningSql} — o teste prova o artefato entregue,
 * não uma cópia escrita à mão. As conexões de leitura usam
 * {@code SET ROLE} para a role do tenant: no Superset a conexão faz
 * LOGIN como a role, caso em que {@code current_user} é a própria role
 * — o mesmo valor que a função de contexto consulta.
 */
class BiIsolationDatabaseTest {

    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TENANT_C = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final List<String> VIEWS = List.of("rpt_contas");
    private static DataSource dataSource;

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Variável " + name + " não definida: exporte-a antes de rodar "
                    + "o teste (o ci.sh gera a senha efêmera)");
        }
        return value;
    }

    @BeforeAll
    static void setUp() throws Exception {
        String url = System.getenv().getOrDefault("ZIIPSAN_IT_JDBC_URL",
                "jdbc:postgresql://localhost:55433/ziipsan_it");
        org.postgresql.ds.PGSimpleDataSource ds = new org.postgresql.ds.PGSimpleDataSource();
        ds.setUrl(url);
        ds.setUser(requireEnv("ZIIPSAN_IT_DB_USER"));
        ds.setPassword(requireEnv("ZIIPSAN_IT_DB_PASSWORD"));
        dataSource = ds;
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            // Massa minima: a "view da P3.22" vira tabela-fonte aqui; o que
            // se prova e a camada do BI sobre ela, nao o relatorio.
            st.execute("CREATE SCHEMA IF NOT EXISTS tenant_shared");
            st.execute("CREATE SCHEMA IF NOT EXISTS tenant_proprio");
            st.execute("DROP TABLE IF EXISTS tenant_shared.rpt_contas");
            st.execute("DROP TABLE IF EXISTS tenant_proprio.rpt_contas");
            st.execute("CREATE TABLE tenant_shared.rpt_contas (tenant_id UUID, valor INT)");
            st.execute("CREATE TABLE tenant_proprio.rpt_contas (tenant_id UUID, valor INT)");
            st.execute("INSERT INTO tenant_shared.rpt_contas VALUES ('" + TENANT_A + "', 10), ('" + TENANT_B + "', 20)");
            st.execute("INSERT INTO tenant_proprio.rpt_contas VALUES ('" + TENANT_C + "', 30)");
            for (String sql : BiRoleProvisioningSql.sharedObjectsSql()) {
                st.execute(sql);
            }
            provision(st, TenantBiRole.of(TENANT_A, TenantBiRole.IsolationMode.SHARED_SCHEMA, "tenant_shared"));
            provision(st, TenantBiRole.of(TENANT_B, TenantBiRole.IsolationMode.SHARED_SCHEMA, "tenant_shared"));
            provision(st, TenantBiRole.of(TENANT_C, TenantBiRole.IsolationMode.DEDICATED_SCHEMA, "tenant_proprio"));
        }
    }

    private static void provision(Statement st, TenantBiRole mapping) throws Exception {
        for (String sql : BiRoleProvisioningSql.provisionSql(mapping, VIEWS)) {
            st.execute(sql);
        }
        // O dono da sessao de teste precisa poder assumir a role (no
        // ambiente real a conexao faz LOGIN como a role do tenant).
        st.execute("GRANT " + mapping.roleName() + " TO CURRENT_USER");
    }

    /** Soma que a role do tenant enxerga na sua camada, com ataque opcional de GUC. */
    private static long visibleSum(UUID tenant, String gucAttackTenant) throws Exception {
        TenantBiRole mapping = TenantBiRole.of(tenant,
                tenant.equals(TENANT_C)
                        ? TenantBiRole.IsolationMode.DEDICATED_SCHEMA
                        : TenantBiRole.IsolationMode.SHARED_SCHEMA,
                tenant.equals(TENANT_C) ? "tenant_proprio" : "tenant_shared");
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute("SET ROLE " + mapping.roleName());
            if (gucAttackTenant != null) {
                // O ataque do aceite: a sessao tenta se passar por outro
                // tenant trocando a GUC que a aplicacao usa.
                st.execute("SET app.current_tenant = '" + gucAttackTenant + "'");
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT COALESCE(SUM(valor), 0) FROM " + mapping.accessSchema() + ".bi_rpt_contas")) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    @Test
    void schemaCompartilhadoCadaRoleLeSoOProprioTenant() throws Exception {
        assertThat(visibleSum(TENANT_A, null)).isEqualTo(10);
        assertThat(visibleSum(TENANT_B, null)).isEqualTo(20);
    }

    @Test
    void schemaProprioLeSoOProprioTenant() throws Exception {
        assertThat(visibleSum(TENANT_C, null)).isEqualTo(30);
    }

    @Test
    void setDaGucNaoLeOutroTenant() throws Exception {
        // A tenta ler como B (compartilhado), B como A, C como A (proprio):
        // em todos, o resultado continua sendo o do tenant da ROLE.
        assertThat(visibleSum(TENANT_A, TENANT_B.toString())).isEqualTo(10);
        assertThat(visibleSum(TENANT_B, TENANT_A.toString())).isEqualTo(20);
        assertThat(visibleSum(TENANT_C, TENANT_A.toString())).isEqualTo(30);
    }

    @Test
    void consultaLivreForaDaCamadaEhNegadaPeloBanco() throws Exception {
        TenantBiRole a = TenantBiRole.of(TENANT_A, TenantBiRole.IsolationMode.SHARED_SCHEMA, "tenant_shared");
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute("SET ROLE " + a.roleName());
            // Direto na view de origem (que enxerga os dois tenants):
            assertThatThrownBy(() -> st.executeQuery("SELECT * FROM tenant_shared.rpt_contas"))
                    .isInstanceOf(SQLException.class);
            // E no schema de acesso do OUTRO tenant:
            TenantBiRole b = TenantBiRole.of(TENANT_B, TenantBiRole.IsolationMode.SHARED_SCHEMA, "tenant_shared");
            assertThatThrownBy(() -> st.executeQuery("SELECT * FROM " + b.accessSchema() + ".bi_rpt_contas"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void desprovisionadoNaoLeNada() throws Exception {
        UUID efemero = UUID.fromString("44444444-4444-4444-4444-444444444444");
        TenantBiRole mapping = TenantBiRole.of(efemero, TenantBiRole.IsolationMode.SHARED_SCHEMA, "tenant_shared");
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO tenant_shared.rpt_contas VALUES ('" + efemero + "', 40)");
            provision(st, mapping);
            for (String sql : BiRoleProvisioningSql.deprovisionSql(mapping, VIEWS)) {
                st.execute(sql);
            }
        }
        // Sem mapeamento, bi_current_tenant_id() e nulo e a view devolve
        // vazio — mesmo que um grant residual existisse (defesa dupla).
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute("GRANT SELECT ON " + mapping.accessSchema() + ".bi_rpt_contas TO " + mapping.roleName());
            st.execute("SET ROLE " + mapping.roleName());
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM " + mapping.accessSchema() + ".bi_rpt_contas")) {
                rs.next();
                assertThat(rs.getLong(1)).isZero();
            }
        }
    }
}
