package br.com.ziipsan.platform.domain;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.platform.domain.TenantDomainStore.DomainView;
import br.com.ziipsan.platform.domain.TenantDomainStore.ResolvedDomain;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementação JDBC de {@link TenantDomainStore} (P2.27).
 *
 * <p>Transação própria por operação com {@code app.current_tenant}
 * local, no padrão do {@code JdbcTenantBrandStore}: a tabela tem RLS
 * ativada e a GUC morre com a transação, nunca vaza para outra conexão
 * do pool. A leitura pré-login é a exceção: um único SELECT na função
 * SECURITY DEFINER da V10_2_1, em autocommit — a função só devolve
 * tenant_id/hostname/status.
 *
 * <p>A unicidade parcial do hostname (índice da V10_2_1) vira 409
 * {@code data-conflict} (código já explicado no catálogo de erros), não
 * 500: o cliente disputa um hostname público com outro tenant.
 */
public final class JdbcTenantDomainStore implements TenantDomainStore {

    private static final String COLUMNS = "id, tenant_id, hostname, status, cname_target, cname_ok,"
            + " last_cname_check_at, cert_status, cert_provider, cert_issued_at, cert_not_after,"
            + " cert_renew_after, last_renewal_at, renewal_failures, verified_at, revoked_at,"
            + " revoked_reason, removed_at, created_at, updated_at";

    private final DataSource dataSource;

    public JdbcTenantDomainStore(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource é obrigatório");
        }
        this.dataSource = dataSource;
    }

    @Override
    public Optional<ResolvedDomain> resolveByHost(String hostname) {
        if (hostname == null || hostname.isBlank()) {
            return Optional.empty();
        }
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT tenant_id, hostname, status"
                     + " FROM public.tenant_domain_by_host(?)")) {
            ps.setString(1, hostname);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new ResolvedDomain(
                        (UUID) rs.getObject(1), rs.getString(2), rs.getString(3)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao resolver o tenant pelo host.", e);
        }
    }

    @Override
    public Optional<DomainView> findOwn(UUID tenantId, UUID domainId) {
        return readTx(tenantId, c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT " + COLUMNS + " FROM platform.tenant_domain WHERE id = ? AND tenant_id = ?")) {
                ps.setObject(1, domainId);
                ps.setObject(2, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(viewOf(rs)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public List<DomainView> listOwn(UUID tenantId) {
        return readTx(tenantId, c -> {
            List<DomainView> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT " + COLUMNS + " FROM platform.tenant_domain"
                            + " WHERE tenant_id = ? ORDER BY created_at DESC, id DESC")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(viewOf(rs));
                    }
                }
            }
            return out;
        });
    }

    @Override
    public Optional<String> verificationTokenHash(UUID tenantId, UUID domainId) {
        return readTx(tenantId, c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT verification_token_hash FROM platform.tenant_domain"
                            + " WHERE id = ? AND tenant_id = ?")) {
                ps.setObject(1, domainId);
                ps.setObject(2, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public DomainView insert(UUID tenantId, UUID actorId, String hostname, String tokenHash, String cnameTarget) {
        try {
            return writeTx(tenantId, c -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO platform.tenant_domain"
                                + " (id, tenant_id, hostname, status, verification_token_hash, cname_target,"
                                + " cname_ok, cert_status, created_by, updated_by)"
                                + " VALUES (uuid_generate_v4(), ?, ?, 'PENDING_VERIFICATION', ?, ?, false, 'NONE', ?, ?)"
                                + " RETURNING " + COLUMNS)) {
                    ps.setObject(1, tenantId);
                    ps.setString(2, hostname);
                    ps.setString(3, tokenHash);
                    ps.setString(4, cnameTarget);
                    setActor(ps, 5, actorId);
                    setActor(ps, 6, actorId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalStateException("A criação do domínio não devolveu a linha.");
                        }
                        DomainView created = viewOf(rs);
                        audit(c, tenantId, actorId, created, "CREATE");
                        return created;
                    }
                }
            });
        } catch (IllegalStateException e) {
            for (Throwable t = e; t != null; t = t.getCause()) {
                if (t instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                    throw new ApiException.Conflict("data-conflict",
                            "O domínio já está em uso por outro tenant.");
                }
            }
            throw e;
        }
    }

    @Override
    public DomainView update(UUID tenantId, UUID actorId, DomainView view, String auditAction) {
        return writeTx(tenantId, c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE platform.tenant_domain SET status = ?, cname_ok = ?, last_cname_check_at = ?,"
                            + " cert_status = ?, cert_provider = ?, cert_issued_at = ?, cert_not_after = ?,"
                            + " cert_renew_after = ?, last_renewal_at = ?, renewal_failures = ?,"
                            + " verified_at = ?, revoked_at = ?, revoked_reason = ?, removed_at = ?,"
                            + " updated_at = NOW(), updated_by = ?"
                            + " WHERE id = ? AND tenant_id = ? RETURNING " + COLUMNS)) {
                int i = 0;
                ps.setString(++i, view.status());
                ps.setBoolean(++i, view.cnameOk());
                setInstant(ps, ++i, view.lastCnameCheckAt());
                ps.setString(++i, view.certStatus());
                setText(ps, ++i, view.certProvider());
                setInstant(ps, ++i, view.certIssuedAt());
                setInstant(ps, ++i, view.certNotAfter());
                setInstant(ps, ++i, view.certRenewAfter());
                setInstant(ps, ++i, view.lastRenewalAt());
                ps.setInt(++i, view.renewalFailures());
                setInstant(ps, ++i, view.verifiedAt());
                setInstant(ps, ++i, view.revokedAt());
                setText(ps, ++i, view.revokedReason());
                setInstant(ps, ++i, view.removedAt());
                setActor(ps, ++i, actorId);
                ps.setObject(++i, view.id());
                ps.setObject(++i, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new ApiException.NotFound("Domínio não encontrado.");
                    }
                    DomainView saved = viewOf(rs);
                    audit(c, tenantId, actorId, saved, auditAction);
                    return saved;
                }
            }
        });
    }

    @Override
    public void syncBrandParameter(UUID tenantId, UUID actorId, String hostname) {
        writeTx(tenantId, c -> {
            if (hostname == null) {
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM public.tenant_settings WHERE tenant_id = ? AND setting_key = 'brand.custom_domain'")) {
                    ps.setObject(1, tenantId);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO public.tenant_settings"
                                + " (id, tenant_id, setting_key, setting_value, is_sensitive)"
                                + " VALUES (uuid_generate_v4(), ?, 'brand.custom_domain', ?::jsonb, false)"
                                + " ON CONFLICT (tenant_id, setting_key) DO UPDATE"
                                + " SET setting_value = EXCLUDED.setting_value, is_sensitive = false,"
                                + " updated_at = NOW()")) {
                    ps.setObject(1, tenantId);
                    ps.setString(2, "\"" + hostname + "\"");
                    ps.executeUpdate();
                }
            }
            return null;
        });
    }

    @Override
    public Optional<String> brandCustomDomain(UUID tenantId) {
        return readTx(tenantId, c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT setting_value FROM public.tenant_settings"
                            + " WHERE tenant_id = ? AND setting_key = 'brand.custom_domain'")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    String json = rs.getString(1);
                    if (json == null || json.length() < 2) {
                        return Optional.empty();
                    }
                    // setting_value é uma string JSON ("host"): remove as aspas.
                    return Optional.of(json.substring(1, json.length() - 1));
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    private static DomainView viewOf(ResultSet rs) throws SQLException {
        return new DomainView(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("tenant_id"),
                rs.getString("hostname"),
                rs.getString("status"),
                rs.getString("cname_target"),
                rs.getBoolean("cname_ok"),
                instantOrNull(rs, "last_cname_check_at"),
                rs.getString("cert_status"),
                rs.getString("cert_provider"),
                instantOrNull(rs, "cert_issued_at"),
                instantOrNull(rs, "cert_not_after"),
                instantOrNull(rs, "cert_renew_after"),
                instantOrNull(rs, "last_renewal_at"),
                rs.getInt("renewal_failures"),
                instantOrNull(rs, "verified_at"),
                instantOrNull(rs, "revoked_at"),
                rs.getString("revoked_reason"),
                instantOrNull(rs, "removed_at"),
                instantOrNull(rs, "created_at"),
                instantOrNull(rs, "updated_at"));
    }

    private static Instant instantOrNull(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }

    private static void setInstant(PreparedStatement ps, int index, Instant value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.TIMESTAMP);
        } else {
            ps.setTimestamp(index, Timestamp.from(value));
        }
    }

    private static void setText(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    private static void setActor(PreparedStatement ps, int index, UUID actorId) throws SQLException {
        if (actorId == null) {
            ps.setNull(index, Types.OTHER);
        } else {
            ps.setObject(index, actorId);
        }
    }

    /** Auditoria: só hostname, estados e motivo — nunca o hash do token. */
    private void audit(Connection c, UUID tenantId, UUID actorId, DomainView view, String action)
            throws SQLException {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("hostname", view.hostname());
        detail.put("status", view.status());
        detail.put("certStatus", view.certStatus());
        if (view.revokedReason() != null) {
            detail.put("revokedReason", view.revokedReason());
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO public.audit_log"
                        + " (tenant_id, entity_type, entity_id, action, new_values, success, error_message,"
                        + " created_by, created_at)"
                        + " VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, NOW())")) {
            ps.setObject(1, tenantId);
            ps.setString(2, "tenant_domain");
            ps.setObject(3, view.id());
            ps.setString(4, action);
            ps.setString(5, Json.write(detail));
            ps.setBoolean(6, true);
            ps.setNull(7, Types.VARCHAR);
            setActor(ps, 8, actorId);
            ps.executeUpdate();
        }
    }

    @FunctionalInterface
    private interface Tx<T> {
        T run(Connection connection) throws Exception;
    }

    private <T> T writeTx(UUID tenantId, Tx<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT set_config('app.current_tenant', ?, true)")) {
                ps.setString(1, tenantId.toString());
                ps.executeQuery().close();
            }
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (RuntimeException e) {
                connection.rollback();
                throw e;
            } catch (Exception e) {
                connection.rollback();
                throw new IllegalStateException("Operação de domínio falhou: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Operação de domínio falhou: " + e.getMessage(), e);
        }
    }

    private <T> T readTx(UUID tenantId, Tx<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT set_config('app.current_tenant', ?, true)")) {
                ps.setString(1, tenantId.toString());
                ps.executeQuery().close();
            }
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (RuntimeException e) {
                connection.rollback();
                throw e;
            } catch (Exception e) {
                connection.rollback();
                throw new IllegalStateException("Leitura de domínio falhou: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Leitura de domínio falhou: " + e.getMessage(), e);
        }
    }
}
