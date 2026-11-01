package br.com.ziipsan.app;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P0.9: pools dimensionados por processo e timeouts na role da aplicação.
 *
 * <p>Valores decididos pelo coordenador no turno 5 (registrados na ARQUITETURA-ALVO):
 * app maximumPoolSize 20 / minimumIdle 2, migrador 2 / 0 (já existente),
 * statement_timeout 5s e idle_in_transaction_session_timeout 30s (valores-alvo da ARQUITETURA-ALVO §4), só em
 * ziipsan_app — nunca no migrador. Os testes de cancelamento e de transação
 * ociosa usam timeouts de sessão curtos (SET em conexão dedicada), nunca
 * alteram a role, para não envenenar o ProvisionedTenant compartilhado.
 */
class PoolTimeoutTest {

    @BeforeAll
    static void setUp() {
        ProvisionedTenant.ensure();
    }

    @Test
    @DisplayName("a role ziipsan_app carrega os dois timeouts da P0.9")
    void appRoleCarriesTimeouts() throws SQLException {
        String config = single("SELECT array_to_string(rolconfig, ';') FROM pg_roles "
                + "WHERE rolname = 'ziipsan_app'");
        assertThat(config).contains("statement_timeout=5s");
        assertThat(config).contains("idle_in_transaction_session_timeout=30s");
    }

    @Test
    @DisplayName("o migrador não carrega timeout de aplicação (migrations são longas)")
    void migratorHasNoAppTimeouts() throws SQLException {
        String config = single("SELECT COALESCE(array_to_string(rolconfig, ';'), '') FROM pg_roles "
                + "WHERE rolname = 'ziipsan_migrator'");
        assertThat(config).doesNotContain("statement_timeout=");
        assertThat(config).doesNotContain("idle_in_transaction_session_timeout=");
    }

    @Test
    @DisplayName("consulta longa é cancelada pelo statement_timeout (57014)")
    void longQueryIsCancelled() throws SQLException {
        try (Connection c = appConnection(); Statement st = c.createStatement()) {
            st.execute("SET statement_timeout = '100ms'");
            assertThatThrownBy(() -> st.executeQuery("SELECT pg_sleep(2)"))
                    .isInstanceOf(SQLException.class)
                    .extracting(e -> ((SQLException) e).getSQLState())
                    .isEqualTo("57014");
        }
    }

    @Test
    @DisplayName("transação ociosa é encerrada pelo idle_in_transaction_session_timeout")
    void idleTransactionIsTerminated() throws Exception {
        Connection c = appConnection();
        try (Statement st = c.createStatement()) {
            st.execute("SET idle_in_transaction_session_timeout = '100ms'");
            st.execute("BEGIN");
            st.execute("SELECT 1");
            Thread.sleep(500);
            // A sessão foi encerrada pelo timeout: a próxima instrução falha
            // (conexão fechada) ou a conexão já não é válida.
            boolean failed;
            try {
                st.execute("SELECT 1");
                failed = !c.isValid(2);
            } catch (SQLException ignored) {
                failed = true;
            }
            assertThat(failed).as("transação ociosa acima do timeout deve ser encerrada").isTrue();
        } finally {
            try {
                c.close();
            } catch (SQLException ignored) {
                // sessão já encerrada pelo timeout
            }
        }
    }

    @Test
    @DisplayName("prepared statement reutilizado troca de tenant sem vazar contexto")
    void preparedStatementSwitchesTenant() throws SQLException {
        UUID other = UUID.randomUUID();
        try (Connection c = appConnection();
             PreparedStatement ps = c.prepareStatement("SELECT current_tenant_id()");
             Statement st = c.createStatement()) {
            st.execute("SET app.current_tenant = '" + ProvisionedTenant.TENANT_ID + "'");
            assertThat(queryOne(ps)).isEqualTo(ProvisionedTenant.TENANT_ID.toString());
            st.execute("SET app.current_tenant = '" + other + "'");
            assertThat(queryOne(ps)).isEqualTo(other.toString());
            st.execute("SET app.current_tenant = ''");
            assertThat(queryOne(ps)).isNull();
        }
    }

    @Test
    @DisplayName("tabela do tenant provisionado é legível sob o próprio tenant (schema por tenant)")
    void tenantTableReadableUnderOwnTenant() throws SQLException {
        try (Connection c = appConnection(); Statement st = c.createStatement()) {
            st.execute("SET app.current_tenant = '" + ProvisionedTenant.TENANT_ID + "'");
            try (ResultSet rs = st.executeQuery("SELECT count(*) FROM "
                    + ProvisionedTenant.SCHEMA + ".consumer_units")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong(1)).isGreaterThanOrEqualTo(0);
            }
        }
    }

    private static Connection appConnection() throws SQLException {
        return new DriverManagerDataSource(ProvisionedTenant.url,
                ProvisionedTenant.appUser, ProvisionedTenant.appPassword).getConnection();
    }

    private static String single(String sql) throws SQLException {
        try (Connection c = ProvisionedTenant.appDs.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            assertThat(rs.next()).as("sem linha para: %s", sql).isTrue();
            return rs.getString(1);
        }
    }

    private static String queryOne(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            assertThat(rs.next()).isTrue();
            return rs.getString(1);
        }
    }
}
