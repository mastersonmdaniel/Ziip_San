package br.com.ziipsan.platform.about;

import br.com.ziipsan.platform.about.AboutRecords.DocumentAcceptance;
import br.com.ziipsan.platform.about.AboutRecords.DocumentCatalog;
import br.com.ziipsan.platform.about.AboutRecords.ReleaseNoteView;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * P1.24: implementação JDBC de {@link AboutStore}.
 *
 * <p>Bean Spring na aplicação (como DeploymentRepository): o DataSource vem
 * por injeção; fora do Spring, o construtor direto serve igual.
 *
 * <p>Padrão de isolamento igual ao de JdbcDocumentRepository (P1.20): cada
 * operação com dado de tenant roda numa transação explícita que fixa
 * {@code app.current_tenant} (para a policy com FORCE) E filtra por
 * {@code tenant_id} em cada query — nenhuma das duas camadas anda sozinha.
 * O catálogo ({@code platform.document}) é global e não passa por tenant.
 *
 * <p>Sem anotações Spring de propósito (mesmo padrão da P1.20): a aplicação
 * constrói esta classe com o DataSource dela. O bean Spring, quando
 * existir, é só um construtor com {@code @Autowired} na aplicação.
 */
@Component
public final class JdbcAboutStore implements AboutStore {

    private final DataSource dataSource;

    @Autowired
    public JdbcAboutStore(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("DataSource é obrigatório");
        }
        this.dataSource = dataSource;
    }

    @FunctionalInterface
    private interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    private <T> T inTenantTransaction(UUID tenantId, Work<T> work) {
        requireTenant(tenantId);
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT set_config('app.current_tenant', ?, true)")) {
                    ps.setString(1, tenantId.toString());
                    ps.executeQuery().close();
                }
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new AboutStoreException("Falha de acesso ao Sobre", e);
        }
    }

    @Override
    public List<DocumentCatalog> listDocuments() {
        String sql = "SELECT code, title, current_version, current_content_hash, published_at, active"
                + " FROM platform.document WHERE active ORDER BY code";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<DocumentCatalog> out = new ArrayList<>();
            while (rs.next()) {
                out.add(mapDocument(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new AboutStoreException("Falha ao listar documentos do Sobre", e);
        }
    }

    @Override
    public Optional<DocumentCatalog> findDocument(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT code, title, current_version, current_content_hash, published_at, active"
                + " FROM platform.document WHERE code = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, code.strip());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapDocument(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new AboutStoreException("Falha ao buscar documento do Sobre", e);
        }
    }

    @Override
    public DocumentAcceptance accept(UUID tenantId, UUID userId, String code,
                                     String ipAddress, String authMethod) {
        requireTenant(tenantId);
        if (userId == null) {
            throw new IllegalArgumentException("userId é obrigatório");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code do documento é obrigatório");
        }
        if (authMethod == null || authMethod.isBlank()) {
            throw new IllegalArgumentException("authMethod é obrigatório");
        }
        return inTenantTransaction(tenantId, connection -> {
            DocumentCatalog doc;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT code, title, current_version, current_content_hash, published_at, active"
                            + " FROM platform.document WHERE code = ?")) {
                ps.setString(1, code.strip());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new DocumentNotPublishedException(code);
                    }
                    doc = mapDocument(rs);
                }
            }
            if (!doc.active()) {
                throw new DocumentNotPublishedException(code);
            }
            String insert = "INSERT INTO platform.document_acceptance"
                    + " (tenant_id, user_id, document_code, document_version, content_hash, ip_address, auth_method)"
                    + " VALUES (?, ?, ?, ?, ?, ?::inet, ?)"
                    + " ON CONFLICT (tenant_id, user_id, document_code, document_version) DO NOTHING"
                    + " RETURNING id, tenant_id, user_id, document_code, document_version,"
                    + " content_hash, accepted_at, ip_address, auth_method";
            try (PreparedStatement ps = connection.prepareStatement(insert)) {
                ps.setObject(1, tenantId);
                ps.setObject(2, userId);
                ps.setString(3, doc.code());
                ps.setString(4, doc.currentVersion());
                ps.setString(5, doc.currentContentHash());
                if (ipAddress == null || ipAddress.isBlank()) {
                    ps.setNull(6, Types.VARCHAR);
                } else {
                    ps.setString(6, ipAddress.strip());
                }
                ps.setString(7, authMethod.strip());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapAcceptance(rs);
                    }
                }
            }
            // Conflito: o aceite já existia — devolve o original, sem tocar a data.
            String existing = "SELECT id, tenant_id, user_id, document_code, document_version,"
                    + " content_hash, accepted_at, ip_address, auth_method"
                    + " FROM platform.document_acceptance"
                    + " WHERE tenant_id = ? AND user_id = ? AND document_code = ? AND document_version = ?";
            try (PreparedStatement ps = connection.prepareStatement(existing)) {
                ps.setObject(1, tenantId);
                ps.setObject(2, userId);
                ps.setString(3, doc.code());
                ps.setString(4, doc.currentVersion());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapAcceptance(rs);
                    }
                }
            }
            throw new AboutStoreException("Aceite não encontrado após conflito de inserção", null);
        });
    }

    @Override
    public List<DocumentAcceptance> acceptancesFor(UUID tenantId, UUID userId) {
        requireTenant(tenantId);
        if (userId == null) {
            throw new IllegalArgumentException("userId é obrigatório");
        }
        return inTenantTransaction(tenantId, connection -> {
            String sql = "SELECT id, tenant_id, user_id, document_code, document_version,"
                    + " content_hash, accepted_at, ip_address, auth_method"
                    + " FROM platform.document_acceptance"
                    + " WHERE tenant_id = ? AND user_id = ? ORDER BY document_code";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, tenantId);
                ps.setObject(2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<DocumentAcceptance> out = new ArrayList<>();
                    while (rs.next()) {
                        out.add(mapAcceptance(rs));
                    }
                    return out;
                }
            }
        });
    }

    @Override
    public ReleaseNoteView markViewed(UUID tenantId, UUID userId, String version) {
        requireTenant(tenantId);
        if (userId == null) {
            throw new IllegalArgumentException("userId é obrigatório");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version é obrigatória");
        }
        return inTenantTransaction(tenantId, connection -> {
            String insert = "INSERT INTO support.release_note_view (tenant_id, user_id, version)"
                    + " VALUES (?, ?, ?)"
                    + " ON CONFLICT (tenant_id, user_id, version) DO NOTHING"
                    + " RETURNING id, tenant_id, user_id, version, viewed_at";
            try (PreparedStatement ps = connection.prepareStatement(insert)) {
                ps.setObject(1, tenantId);
                ps.setObject(2, userId);
                ps.setString(3, version.strip());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapView(rs);
                    }
                }
            }
            // Conflito: a primeira visualização é a que vale — devolve-a.
            String existing = "SELECT id, tenant_id, user_id, version, viewed_at"
                    + " FROM support.release_note_view"
                    + " WHERE tenant_id = ? AND user_id = ? AND version = ?";
            try (PreparedStatement ps = connection.prepareStatement(existing)) {
                ps.setObject(1, tenantId);
                ps.setObject(2, userId);
                ps.setString(3, version.strip());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapView(rs);
                    }
                }
            }
            throw new AboutStoreException("Visualização não encontrada após conflito de inserção", null);
        });
    }

    @Override
    public List<String> viewedVersions(UUID tenantId, UUID userId) {
        requireTenant(tenantId);
        if (userId == null) {
            throw new IllegalArgumentException("userId é obrigatório");
        }
        return inTenantTransaction(tenantId, connection -> {
            String sql = "SELECT version FROM support.release_note_view"
                    + " WHERE tenant_id = ? AND user_id = ? ORDER BY viewed_at";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, tenantId);
                ps.setObject(2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<String> out = new ArrayList<>();
                    while (rs.next()) {
                        out.add(rs.getString(1));
                    }
                    return out;
                }
            }
        });
    }

    private static void requireTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId é obrigatório");
        }
    }

    private static DocumentCatalog mapDocument(ResultSet rs) throws SQLException {
        return new DocumentCatalog(
                rs.getString("code"),
                rs.getString("title"),
                rs.getString("current_version"),
                rs.getString("current_content_hash"),
                toOffset(rs.getTimestamp("published_at")),
                rs.getBoolean("active"));
    }

    private static DocumentAcceptance mapAcceptance(ResultSet rs) throws SQLException {
        return new DocumentAcceptance(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("document_code"),
                rs.getString("document_version"),
                rs.getString("content_hash"),
                toOffset(rs.getTimestamp("accepted_at")),
                rs.getString("ip_address"),
                rs.getString("auth_method"));
    }

    private static ReleaseNoteView mapView(ResultSet rs) throws SQLException {
        return new ReleaseNoteView(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("version"),
                toOffset(rs.getTimestamp("viewed_at")));
    }

    private static OffsetDateTime toOffset(Timestamp ts) {
        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
    }

    /** Falha de infra do repositório; nunca vaza SQL para a API (o handler global dá 500). */
    public static final class AboutStoreException extends RuntimeException {
        AboutStoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
