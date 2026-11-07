package br.com.ziipsan.reporting.query;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Fonte de leitura dos relatorios (ADR-0025/RNF-71): replica de leitura
 * quando configurada (local: replica local; RDS so a partir da LC.3); sem
 * replica, o primario com {@code statement_timeout} obrigatorio - relatorio
 * nunca roda sem teto de tempo no primario. O teto padrao e o do RNF-71
 * (geracao em ate 2 min na massa da P2.9).
 *
 * <p>CORRECAO B4 (pos-revisao): {@code SET LOCAL} so vale DENTRO de uma
 * transacao - fora dela o PostgreSQL emite aviso e o timeout nao se
 * aplica (a versao anterior chamava SET LOCAL com auto-commit ligado:
 * o teto era ficticio). Agora {@link #open(int)} abre transacao
 * explicita na conexao (auto-commit desligado) ANTES do SET LOCAL, e a
 * {@link RoutedConnection} encerra a transacao na devolucao: commit
 * explicito via {@link RoutedConnection#commit()} no sucesso; rollback
 * no {@link RoutedConnection#close()} se ninguem confirmou. O estado
 * original de auto-commit da conexao e restaurado antes de fecha-la.
 */
public final class ReportDataSourceRouter {

    public static final int DEFAULT_STATEMENT_TIMEOUT_MS = 120_000;

    private final DataSource primary;
    private final DataSource readReplica;

    public ReportDataSourceRouter(DataSource primary, DataSource readReplica) {
        this.primary = primary;
        this.readReplica = readReplica;
    }

    public boolean replicaConfigured() {
        return readReplica != null;
    }

    public RoutedConnection open(int statementTimeoutMs) throws SQLException {
        boolean replica = readReplica != null;
        Connection connection = (replica ? readReplica : primary).getConnection();
        boolean originalAutoCommit = connection.getAutoCommit();
        try {
            connection.setReadOnly(true);
            // Transacao explicita PRIMEIRO: sem ela o SET LOCAL e ignorado.
            connection.setAutoCommit(false);
            try (Statement st = connection.createStatement()) {
                st.execute("SET LOCAL statement_timeout = " + Math.max(1, statementTimeoutMs));
            }
        } catch (SQLException | RuntimeException e) {
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
        return new RoutedConnection(connection, replica ? Source.READ_REPLICA : Source.PRIMARY,
                originalAutoCommit);
    }

    public enum Source {
        PRIMARY, READ_REPLICA
    }

    /**
     * Conexao roteada com a transacao do timeout aberta. O chamador deve
     * chamar {@link #commit()} ao concluir a leitura com sucesso; o
     * {@link #close()} faz rollback do que nao foi confirmado, restaura
     * o auto-commit original e fecha a conexao.
     */
    public static final class RoutedConnection implements AutoCloseable {

        private final Connection connection;
        private final Source source;
        private final boolean originalAutoCommit;
        private boolean ended;

        RoutedConnection(Connection connection, Source source, boolean originalAutoCommit) {
            this.connection = connection;
            this.source = source;
            this.originalAutoCommit = originalAutoCommit;
        }

        public Connection connection() {
            return connection;
        }

        public Source source() {
            return source;
        }

        /** Confirma a transacao de leitura (encerra o SET LOCAL com ela). */
        public void commit() throws SQLException {
            connection.commit();
            ended = true;
        }

        @Override
        public void close() throws SQLException {
            try {
                if (!ended) {
                    connection.rollback();
                }
            } finally {
                try {
                    connection.setAutoCommit(originalAutoCommit);
                } finally {
                    connection.close();
                }
            }
        }
    }
}
