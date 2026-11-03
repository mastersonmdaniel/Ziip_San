package br.com.ziipsan.platform.infra.crypto;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * P0.28: {@link TenantKeyStore} sobre {@code public.tenant_key} (core V6), JDBC puro
 * (sem JPA: o provedor de chaves nao pode depender do EntityManager do tenant que
 * ele mesmo ajuda a cifrar). Quem chama roda na transacao do chamador; o provedor
 * usa esta store fora de transacao de negocio. A KEK NAO passa por aqui: ela fica
 * no cofre (SecretStore) e so o provedor a conhece.
 */
public final class JdbcTenantKeyStore implements TenantKeyStore {

    private final DataSource dataSource;

    public JdbcTenantKeyStore(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("DataSource obrigatorio");
        }
        this.dataSource = dataSource;
    }

    @Override
    public Optional<KeyMaterial> loadActive(UUID tenantId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT key_version, wrapped_encryption_key, wrapped_blind_index_key, status"
                             + " FROM public.tenant_key WHERE tenant_id = ? AND status = 'ACTIVE'"
                             + " ORDER BY key_version DESC LIMIT 1")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new KeyMaterial(rs.getInt(1), rs.getBytes(2), rs.getBytes(3), rs.getString(4)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("falha ao ler tenant_key", e);
        }
    }

    @Override
    public int createVersion(UUID tenantId, byte[] wrappedEncryptionKey, byte[] wrappedBlindIndexKey) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                int next;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT COALESCE(MAX(key_version), 0) FROM public.tenant_key WHERE tenant_id = ?")) {
                    ps.setObject(1, tenantId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        next = rs.getInt(1) + 1;
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE public.tenant_key SET status = 'ROTATED', rotated_at = NOW()"
                                + " WHERE tenant_id = ? AND status = 'ACTIVE'")) {
                    ps.setObject(1, tenantId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO public.tenant_key"
                                + " (tenant_id, key_version, wrapped_encryption_key, wrapped_blind_index_key, status)"
                                + " VALUES (?, ?, ?, ?, 'ACTIVE')")) {
                    ps.setObject(1, tenantId);
                    ps.setInt(2, next);
                    ps.setBytes(3, wrappedEncryptionKey);
                    ps.setBytes(4, wrappedBlindIndexKey);
                    ps.executeUpdate();
                }
                c.commit();
                return next;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("falha ao criar versao de tenant_key", e);
        }
    }

    @Override
    public void destroyAll(UUID tenantId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE public.tenant_key SET wrapped_encryption_key = NULL,"
                             + " wrapped_blind_index_key = NULL, status = 'DESTROYED', destroyed_at = NOW()"
                             + " WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("falha ao destruir tenant_key", e);
        }
    }

    @Override
    public boolean destroyed(UUID tenantId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM public.tenant_key WHERE tenant_id = ? AND status = 'DESTROYED' LIMIT 1")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("falha ao verificar tenant_key destruida", e);
        }
    }
}
