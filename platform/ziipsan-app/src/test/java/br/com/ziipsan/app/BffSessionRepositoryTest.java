package br.com.ziipsan.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0.14: a sessão vive no PostgreSQL (core V5), não na memória do processo. É
 * isto que permite duas réplicas sem afinidade: a prova aqui é ida-e-volta pelo
 * banco (cria, lê por outro acesso ao repositório e confere a linha na tabela);
 * o ensaio com dois processos e uma réplica derrubada no meio da sessão é o item
 * (8) do E2E Playwright (frontend/e2e/bff.spec.ts), fora do alcance do MockMvc.
 */
class BffSessionRepositoryTest extends AppIntegrationTestBase {

    // Tipo raw de propósito: JdbcIndexedSessionRepository é um
    // SessionRepository<JdbcSession> (e JdbcSession é package-private, então nem
    // var nem o tipo concreto compilam aqui) — e generics são invariantes, de
    // modo que injetar SessionRepository<Session> nunca casa (o bean existe,
    // mas o Spring recusa com "No qualifying bean"). No tipo raw, o apagamento
    // casa com Session e o teste prova o ponto: a sessão wired é a JDBC, não
    // um repositório em memória.
    @Autowired
    @SuppressWarnings("rawtypes")
    private SessionRepository sessions;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("sessão criada é lida de volta e some ao ser apagada (persistência, não memória)")
    void sessionRoundTripsThroughPostgres() {
        Session session = sessions.createSession();
        session.setAttribute("probe", "valor-de-teste");
        sessions.save(session);

        Session loaded = sessions.findById(session.getId());
        assertThat(loaded).isNotNull();
        // getAttribute() é genérico (<T> T): sem variável tipada, o javac infere T
        // a partir das sobrecargas de assertThat e a chamada fica ambígua
        // (IntPredicate vs Predicate). Com Object, assertThat(Object) vence.
        Object probe = loaded.getAttribute("probe");
        assertThat(probe).isEqualTo("valor-de-teste");
        assertThat(Integer.valueOf(countAttributeRows(session.getId()))).isEqualTo(1);

        sessions.deleteById(session.getId());
        assertThat(sessions.findById(session.getId())).isNull();
    }

    @Test
    @DisplayName("tabelas de sessão existem em public e NÃO têm RLS de tenant (sessão é do servidor)")
    void sessionTablesHaveNoTenantRls() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT relname, relrowsecurity FROM pg_class "
                             + "WHERE relname IN ('spring_session', 'spring_session_attributes') ORDER BY relname");
             ResultSet rows = statement.executeQuery()) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("spring_session");
            assertThat(rows.getBoolean(2)).isFalse();
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("spring_session_attributes");
            assertThat(rows.getBoolean(2)).isFalse();
        }
    }

    private int countAttributeRows(String sessionId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM spring_session_attributes "
                             + "WHERE session_primary_id = (SELECT primary_id FROM spring_session WHERE session_id = ?)")) {
            statement.setString(1, sessionId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
