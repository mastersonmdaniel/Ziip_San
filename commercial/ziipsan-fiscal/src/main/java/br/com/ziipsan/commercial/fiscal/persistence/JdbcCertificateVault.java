package br.com.ziipsan.commercial.fiscal.persistence;

import br.com.ziipsan.commercial.fiscal.port.CertificateVault;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Implementação JDBC do cofre de certificado — só referências, nunca segredo
 * (ver {@link CertificateVault}). Reutiliza o mesmo padrão transacional do
 * {@link JdbcFiscalDocumentRepository}: {@code SET LOCAL} de schema e tenant por operação.
 */
public final class JdbcCertificateVault implements CertificateVault {

    private final DataSource dataSource;
    private final String schema;

    public JdbcCertificateVault(DataSource dataSource, String schema) {
        if (schema == null || !schema.matches("[a-z][a-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("O schema do tenant e invalido: " + schema);
        }
        this.dataSource = dataSource;
        this.schema = schema;
    }

    @Override
    public Optional<CertificateRef> certificateFor(UUID tenantId, UUID issuerId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (var statement = connection.createStatement()) {
                    statement.execute("SET LOCAL search_path TO " + schema + ", public");
                }
                try (PreparedStatement setTenant = connection.prepareStatement(
                        "SELECT set_config('app.current_tenant', ?, true)")) {
                    setTenant.setString(1, tenantId.toString());
                    setTenant.executeQuery();
                }
                try (PreparedStatement query = connection.prepareStatement(
                        "SELECT id, issuer_id, thumbprint, vault_ref, key_type, not_after "
                                + "FROM fiscal_certificates WHERE tenant_id = ? AND issuer_id = ? "
                                + "AND revoked_at IS NULL ORDER BY not_after DESC LIMIT 1")) {
                    query.setObject(1, tenantId);
                    query.setObject(2, issuerId);
                    try (ResultSet rows = query.executeQuery()) {
                        Optional<CertificateRef> result = rows.next()
                                ? Optional.of(new CertificateRef(
                                        (UUID) rows.getObject("id"), (UUID) rows.getObject("issuer_id"),
                                        rows.getString("thumbprint"), rows.getString("vault_ref"),
                                        rows.getString("key_type"),
                                        rows.getTimestamp("not_after").toInstant().atOffset(java.time.ZoneOffset.UTC)))
                                : Optional.empty();
                        connection.commit();
                        return result;
                    }
                }
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                    // O erro original e o que importa.
                }
                throw new IllegalStateException("Falha de acesso ao cofre de certificado", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha de acesso ao cofre de certificado", e);
        }
    }

    @Override
    public void recordAccess(UUID tenantId, UUID certificateId, UUID accessedBy, String purpose) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (var statement = connection.createStatement()) {
                    statement.execute("SET LOCAL search_path TO " + schema + ", public");
                }
                try (PreparedStatement setTenant = connection.prepareStatement(
                        "SELECT set_config('app.current_tenant', ?, true)")) {
                    setTenant.setString(1, tenantId.toString());
                    setTenant.executeQuery();
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO fiscal_certificate_access_log (tenant_id, certificate_id, accessed_by, purpose) "
                                + "VALUES (?, ?, ?, ?)")) {
                    insert.setObject(1, tenantId);
                    insert.setObject(2, certificateId);
                    insert.setObject(3, accessedBy);
                    insert.setString(4, purpose);
                    insert.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                    // O erro original e o que importa.
                }
                throw new IllegalStateException("Falha ao registrar acesso ao certificado", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao registrar acesso ao certificado", e);
        }
    }
}
