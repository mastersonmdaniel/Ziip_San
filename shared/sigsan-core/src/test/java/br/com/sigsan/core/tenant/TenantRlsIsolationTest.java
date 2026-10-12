package br.com.sigsan.core.tenant;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prova de isolamento entre tenants contra PostgreSQL real.
 *
 * Este é o teste mais importante do projeto. Se a RLS estiver mal configurada, um tenant lê os
 * dados de outro e todo o modelo multi-tenant cai. Não há mock envolvido: PostgreSQL de verdade,
 * schema de verdade, policies de verdade.
 *
 * Usa o PostgreSQL descartável de integração (scripts/reset-it-db.sh, porta 55433), nunca o banco de
 * desenvolvimento: dois builds no mesmo banco colidiam ("tuple concurrently updated"). Para apontar
 * outro banco, defina SIGSAN_IT_JDBC_URL, SIGSAN_IT_DB_USER/PASSWORD e
 * SIGSAN_IT_MIGRATOR_USER/PASSWORD (as mesmas variáveis do sigsan-app).
 */
class TenantRlsIsolationTest {

    private static DataSource dataSource;
    private static DataSource migratorDataSource;

    /**
     * Sufixo único por execução da suíte.
     *
     * Os testes fazem DROP SCHEMA CASCADE. Com nomes fixos como "tenant_alpha", rodar
     * `mvn test` numa máquina de desenvolvimento apagaria o schema de um cliente real chamado
     * Alpha. O prefixo itest_ e o sufixo aleatório tornam a colisão implausível, e o método
     * assertTestSchema verifica a guarda antes de qualquer DROP.
     */
    private static final String RUN_ID =
            Long.toHexString(System.nanoTime() & 0xFFFFFFL);

    private static final String SCHEMA_A = "itest_rls_a_" + RUN_ID;
    private static final String SCHEMA_B = "itest_rls_b_" + RUN_ID;

    /** Impede que um DROP CASCADE atinja um schema que não é deste teste. */
    private static void assertTestSchema(String schema) {
        if (!schema.startsWith("itest_")) {
            throw new IllegalStateException(
                    "Recusando destruir o schema " + schema + ": nao pertence a este teste");
        }
    }
    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeAll
    static void connect() {
        String url = System.getenv().getOrDefault(
                "SIGSAN_IT_JDBC_URL", "jdbc:postgresql://host.docker.internal:55433/sigsan");
        String user = System.getenv().getOrDefault("SIGSAN_IT_DB_USER", "sigsan_app");
        String password = System.getenv().getOrDefault(
                "SIGSAN_IT_DB_PASSWORD", "sigsan_dev_password");

        // A aplicacao conecta como sigsan_app, que e NOSUPERUSER e NOBYPASSRLS. E assim que
        // a RLS precisa valer, porque e a role real em producao.
        dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(url, user, password);

        // O DDL de teste (criar schema, tabela, policy, funcao) exige privilégios de migracao.
        // Rodar o DDL com um role diferente e usar sigsan_app para o SELECT e o que prova que
        // a RLS funciona para a aplicacao, e nao para um superusuario.
        migratorDataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                url,
                System.getenv().getOrDefault("SIGSAN_IT_MIGRATOR_USER", "sigsan_migrator"),
                System.getenv().getOrDefault("SIGSAN_IT_MIGRATOR_PASSWORD", "sigsan_migrator_password"));
    }

    @BeforeEach
    void resetData() throws SQLException {
        try (Connection conn = migratorDataSource.getConnection(); Statement st = conn.createStatement()) {
            assertTestSchema(SCHEMA_A);
            st.execute("DROP SCHEMA IF EXISTS " + SCHEMA_A + " CASCADE");
            assertTestSchema(SCHEMA_B);
            st.execute("DROP SCHEMA IF EXISTS " + SCHEMA_B + " CASCADE");
        }
        provision(SCHEMA_A);
        provision(SCHEMA_B);
        seed(SCHEMA_A, TENANT_A, "ordem-alpha-1");
        seed(SCHEMA_B, TENANT_B, "ordem-beta-1");
    }

    /**
     * Cria schema de tenant, aplica RLS e registra a linha em tenant_metadata. Reproduz o que o
     * TenantProvisioningService faz em produção, mas reduzido ao essencial para o teste.
     */
    private static void provision(String schema) throws SQLException {
        try (Connection conn = migratorDataSource.getConnection(); Statement st = conn.createStatement()) {
            // As extensões são criadas pelo init do container. O role de migração não tem
            // privilégio para criá-las, e não precisa: só de usá-las. As migrações de produção
            // (V1__create_core_schema.sql) declaram as mesmas extensões.
            st.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
            // public entra no search_path porque é lá que as extensões moram.
            st.execute("SET search_path TO " + schema + ", public");

            // USAGE em public e EXECUTE em current_tenant_id() vem do init de seguranca: a funcao
            // pertence a sigsan_owner e o migrador nao pode conceder nem redefini-la.

            st.execute("SET search_path TO " + schema + ", public");

            st.execute("""
                    CREATE TABLE service_orders (
                        id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                        tenant_id UUID NOT NULL,
                        order_number VARCHAR(50) NOT NULL,
                        status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
                        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    )
                    """);

            st.execute("ALTER TABLE service_orders ENABLE ROW LEVEL SECURITY");
            st.execute("""
                    CREATE POLICY service_orders_isolation ON service_orders
                        USING (tenant_id = current_tenant_id())
                    """);

            st.execute("""
                    CREATE TABLE tenant_metadata (
                        id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                        tenant_id UUID NOT NULL UNIQUE,
                        schema_name VARCHAR(100) NOT NULL
                    )
                    """);
            st.execute("ALTER TABLE tenant_metadata ENABLE ROW LEVEL SECURITY");
            st.execute("""
                    CREATE POLICY tenant_metadata_isolation ON tenant_metadata
                        USING (tenant_id = current_tenant_id())
                    """);

            // Grants depois da criacao das tabelas, como faz grantTenantTablePrivileges em
            // producao. ALTER DEFAULT PRIVILEGES sozinho nao bastaria.
            st.execute("SET search_path TO public");
            st.execute("GRANT USAGE ON SCHEMA " + schema + " TO sigsan_app");
            st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA "
                    + schema + " TO sigsan_app");
            st.execute("GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA " + schema + " TO sigsan_app");
        }
    }

    private static void seed(String schema, UUID tenantId, String orderNumber) throws SQLException {
        try (Connection conn = migratorDataSource.getConnection(); Statement st = conn.createStatement()) {
            st.execute("SET search_path TO " + schema + ", public");
            st.execute("INSERT INTO tenant_metadata (tenant_id, schema_name) VALUES ('"
                    + tenantId + "', '" + schema + "')");
        }
        seedOrder(schema, tenantId, orderNumber);
    }

    private static void seedOrder(String schema, UUID tenantId, String orderNumber) throws SQLException {
        try (Connection conn = migratorDataSource.getConnection(); Statement st = conn.createStatement()) {
            st.execute("SET search_path TO " + schema + ", public");
            st.execute("INSERT INTO service_orders (tenant_id, order_number) VALUES ('"
                    + tenantId + "', '" + orderNumber + "')");
        }
    }

    /**
     * Executa uma leitura no contexto de um tenant.
     *
     * A GUC app.current_tenant é o que current_tenant_id() lê. Precisa ser setada dentro da
     * mesma transação da leitura: set_config com is_local=true morre no commit, e setar fora
     * de transação vazaria o tenant para a próxima conexão reaproveitada do pool.
     *
     * O try-with-resources faz rollback no fim, descartando a GUC local.
     */
    private int countAs(UUID tenantId, String sql) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.execute("SELECT set_config('app.current_tenant', '" + tenantId + "', true)");
                try (ResultSet rs = st.executeQuery(sql)) {
                    rs.next();
                    return rs.getInt(1);
                }
            } finally {
                conn.rollback();
            }
        }
    }

    private static String ordersIn(String schema) {
        return "SELECT count(*) FROM " + schema + ".service_orders";
    }

    private static String metadataIn(String schema) {
        return "SELECT count(*) FROM " + schema + ".tenant_metadata";
    }

    @Test
    @DisplayName("tenant enxerga as próprias ordens de serviço")
    void tenantSeesOwnOrders() throws SQLException {
        assertThat(countAs(TENANT_A, ordersIn(SCHEMA_A)))
                .as("A enxerga a própria ordem")
                .isEqualTo(1);
        assertThat(countAs(TENANT_B, ordersIn(SCHEMA_B)))
                .as("B enxerga a própria ordem")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("sem contexto de tenant, a RLS nega tudo em vez de liberar tudo")
    void withoutTenantContextNothingIsVisible() throws SQLException {
        assertThat(countAs(null, ordersIn(SCHEMA_A)))
                .as("contexto ausente deve resultar em zero linhas visíveis, nunca em tudo")
                .isZero();
    }

    @Test
    @DisplayName("tenant_metadata também é isolada por RLS")
    void tenantMetadataIsAlsoIsolated() throws SQLException {
        assertThat(countAs(TENANT_A, metadataIn(SCHEMA_A)))
                .as("A tem uma linha de metadata própria")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("INSERT com tenant_id de outro tenant é barrado pela policy")
    void crossTenantInsertIsRejected() {
        // A policy USING (tenant_id = current_tenant_id()) vale para INSERT tambem: uma linha
        // com tenant_id diferente do contexto viola a policy e o banco recusa. Este e o
        // comportamento correto. Se a policy so cobrisse SELECT, o INSERT passaria e o dado
        // ficaria invisivel mas presente.
        assertThatThrownBy(() -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try (Statement st = conn.createStatement()) {
                    st.execute("SELECT set_config('app.current_tenant', '" + TENANT_A + "', true)");
                    st.execute("INSERT INTO " + SCHEMA_B
                            + ".service_orders (tenant_id, order_number) VALUES ('" + TENANT_B
                            + "', 'ordem-invasora')");
                } finally {
                    conn.rollback();
                }
            }
        }).isInstanceOf(SQLException.class)
                .hasMessageContaining("row-level security");
    }

    @Test
    @DisplayName("tenant A não enxerga ordens do tenant B mesmo no mesmo schema")
    void crossTenantReadIsBlocked() throws SQLException {
        // Mesmo schema, IDs diferentes. E exatamente o cenario de RLS: a tabela e a mesma,
        // quem filtra e a policy.
        // Insere só a ordem extra: a linha de metadata de B ja existe e tem chave unica.
        seedOrder(SCHEMA_B, TENANT_B, "ordem-beta-2");

        assertThat(countAs(TENANT_A, ordersIn(SCHEMA_B)))
                .as("A não pode enxergar nada do schema de B")
                .isZero();
        assertThat(countAs(TENANT_B, ordersIn(SCHEMA_B)))
                .as("B enxerga as duas ordens próprias")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("UUID de tenant inexistente não vaza dados")
    void unknownTenantSeesNothing() throws SQLException {
        UUID stranger = UUID.fromString("99999999-9999-9999-9999-999999999999");

        assertThat(countAs(stranger, ordersIn(SCHEMA_A))).isZero();
        assertThat(countAs(stranger, ordersIn(SCHEMA_B))).isZero();
        assertThat(countAs(stranger, metadataIn(SCHEMA_A))).isZero();
    }

    @Test
    @DisplayName("contexto de tenant não vaza entre conexões do pool")
    void tenantContextDoesNotLeakAcrossConnections() throws SQLException {
        // O helper usa SET LOCAL e faz rollback. Se a GUC vazasse, a segunda leitura sem
        // contexto enxergaria a ordem da primeira.
        assertThat(countAs(TENANT_A, ordersIn(SCHEMA_A))).isEqualTo(1);
        assertThat(countAs(null, ordersIn(SCHEMA_A)))
                .as("a GUC local do primeiro uso não pode sobreviver")
                .isZero();
    }

    @Test
    @DisplayName("o contexto Java de tenant é independente do contexto de banco")
    void javaContextIsIndependentPerThread() throws Exception {
        TenantContextHolder.setTenant(SCHEMA_A);
        TenantContextHolder.setTenantId(TENANT_A);

        final String[] seenSchema = new String[1];
        final UUID[] seenId = new UUID[1];
        Thread worker = new Thread(() -> {
            seenSchema[0] = TenantContextHolder.getTenant();
            seenId[0] = TenantContextHolder.getTenantId();
        });
        worker.start();
        worker.join();

        assertThat(seenSchema[0]).isNull();
        assertThat(seenId[0]).isNull();
        assertThat(TenantContextHolder.getTenant()).isEqualTo(SCHEMA_A);
        assertThat(TenantContextHolder.getTenantId()).isEqualTo(TENANT_A);

        TenantContextHolder.clear();
    }
}
