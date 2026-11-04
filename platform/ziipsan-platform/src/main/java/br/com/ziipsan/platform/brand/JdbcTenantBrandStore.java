package br.com.ziipsan.platform.brand;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import tools.jackson.databind.JsonNode;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementação JDBC de {@link TenantBrandStore} (P1.19).
 *
 * <p>Transação própria por operação com {@code app.current_tenant} local,
 * no padrão do {@code TenantParameterService}: as tabelas têm RLS forçada
 * e a GUC morre com a transação, nunca vaza para outra conexão do pool.
 * As leituras pré-login são a exceção: um único SELECT na função SECURITY
 * DEFINER da V9_2, em autocommit — a função fixa a GUC dentro da
 * transação daquele statement.
 *
 * <p>A unicidade do slug (índice UNIQUE da V9_2) vira 409
 * {@code data-conflict} (código já explicado no catálogo de erros), não
 * 500: o cliente disputa um slug público com outro tenant.
 */
public final class JdbcTenantBrandStore implements TenantBrandStore {

    private final DataSource dataSource;

    public JdbcTenantBrandStore(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource é obrigatório");
        }
        this.dataSource = dataSource;
    }

    @Override
    public Optional<TenantBrand> resolveBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT public.tenant_brand_by_slug(?)")) {
            ps.setString(1, slug);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                String json = rs.getString(1);
                if (json == null) {
                    return Optional.empty();
                }
                JsonNode node = Json.readTree(json);
                return Optional.of(new TenantBrand(
                        UUID.fromString(node.get("tenantId").asString()),
                        node.get("slug").asString(),
                        textOrNull(node, "displayName"),
                        textOrNull(node, "shortName"),
                        textOrNull(node, "color"),
                        node.get("contact"),
                        logoRefOrNull(node, "logo"),
                        logoRefOrNull(node, "logoDark")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao resolver a marca pelo slug.", e);
        }
    }

    @Override
    public Optional<LogoBytes> logoBytes(String slug, UUID assetId) {
        if (slug == null || slug.isBlank() || assetId == null) {
            return Optional.empty();
        }
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT content_type, width, height, sha256, content"
                             + " FROM public.tenant_brand_logo(?, ?)")) {
            ps.setString(1, slug);
            ps.setObject(2, assetId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new LogoBytes(rs.getString(1), rs.getInt(2), rs.getInt(3),
                        rs.getString(4), rs.getBytes(5)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao ler o logo.", e);
        }
    }

    @Override
    public CurrentBrand currentBrand(UUID tenantId) {
        requireTenant(tenantId);
        return inTx(tenantId, c -> {
            String slug = null;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT subdomain_slug FROM public.tenant_catalog WHERE id = ?")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        slug = rs.getString(1);
                    }
                }
            }
            Map<String, JsonNode> settings = new LinkedHashMap<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT setting_key, setting_value::text FROM public.tenant_settings"
                            + " WHERE tenant_id = ? AND starts_with(setting_key, 'branding.')")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        settings.put(rs.getString(1), Json.readTree(rs.getString(2)));
                    }
                }
            }
            AssetView logo = assetByKind(c, tenantId, "LOGO", linkedAssetId(settings, "branding.logo"));
            AssetView logoDark = assetByKind(c, tenantId, "LOGO_DARK",
                    linkedAssetId(settings, "branding.logo_dark"));
            return new CurrentBrand(slug, settings, logo, logoDark);
        });
    }

    @Override
    public void saveSettings(UUID tenantId, UUID actorId, Map<String, String> upserts,
                             List<String> deleteKeys, SlugUpdate slugUpdate) {
        requireTenant(tenantId);
        try {
            inTx(tenantId, c -> {
                for (Map.Entry<String, String> entry : upserts.entrySet()) {
                    upsertSetting(c, tenantId, entry.getKey(), entry.getValue());
                }
                for (String key : deleteKeys) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "DELETE FROM public.tenant_settings WHERE tenant_id = ? AND setting_key = ?")) {
                        ps.setObject(1, tenantId);
                        ps.setString(2, key);
                        ps.executeUpdate();
                    }
                }
                if (slugUpdate.change()) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE public.tenant_catalog SET subdomain_slug = ? WHERE id = ?")) {
                        if (slugUpdate.slug() == null) {
                            ps.setNull(1, Types.VARCHAR);
                        } else {
                            ps.setString(1, slugUpdate.slug());
                        }
                        ps.setObject(2, tenantId);
                        ps.executeUpdate();
                    }
                }
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("keys", upserts.keySet().stream().sorted().toList());
                detail.put("deletedKeys", deleteKeys.stream().sorted().toList());
                detail.put("slugChanged", slugUpdate.change());
                audit(c, tenantId, actorId, "tenant_brand", tenantId, "UPDATE",
                        Json.write(detail), true, null);
                return null;
            });
        } catch (IllegalStateException e) {
            for (Throwable t = e; t != null; t = t.getCause()) {
                if (t instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                    throw new ApiException.Conflict("data-conflict",
                            "O subdomínio já está em uso por outro tenant.");
                }
            }
            throw e;
        }
    }

    @Override
    public AssetView insertQuarantinedAsset(UUID tenantId, String kind, String contentType, int width, int height,
                                            long sizeBytes, String sha256, byte[] content) {
        requireTenant(tenantId);
        return inTx(tenantId, c -> {
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO public.tenant_assets"
                            + " (id, tenant_id, kind, content_type, width, height, size_bytes, sha256,"
                            + " scan_status, content, created_by)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'QUARANTINED', ?, ?)")) {
                ps.setObject(1, id);
                ps.setObject(2, tenantId);
                ps.setString(3, kind);
                ps.setString(4, contentType);
                ps.setInt(5, width);
                ps.setInt(6, height);
                ps.setLong(7, sizeBytes);
                ps.setString(8, sha256);
                ps.setBytes(9, content);
                setActor(ps, 10, null);
                ps.executeUpdate();
            }
            return new AssetView(id, kind, contentType, width, height, sha256,
                    "QUARANTINED", null, null, null);
        });
    }

    @Override
    public void activateCleanAsset(UUID tenantId, UUID actorId, AssetView asset, String scanner, Instant scannedAt) {
        requireTenant(tenantId);
        inTx(tenantId, c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE public.tenant_assets SET scan_status = 'CLEAN', scanned_at = ?, scanner = ?,"
                            + " threat = NULL, updated_at = NOW(), updated_by = ?"
                            + " WHERE id = ? AND tenant_id = ?")) {
                ps.setTimestamp(1, Timestamp.from(scannedAt));
                ps.setString(2, scanner);
                setActor(ps, 3, actorId);
                ps.setObject(4, asset.id());
                ps.setObject(5, tenantId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM public.tenant_assets"
                            + " WHERE tenant_id = ? AND kind = ? AND id <> ?")) {
                ps.setObject(1, tenantId);
                ps.setString(2, asset.kind());
                ps.setObject(3, asset.id());
                ps.executeUpdate();
            }
            String key = "LOGO_DARK".equals(asset.kind()) ? "branding.logo_dark" : "branding.logo";
            upsertSetting(c, tenantId, key, "\"" + asset.id() + "\"");
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("kind", asset.kind());
            detail.put("sha256", asset.sha256());
            detail.put("scanner", scanner);
            audit(c, tenantId, actorId, "tenant_asset", asset.id(), "UPDATE",
                    Json.write(detail), true, null);
            return null;
        });
    }

    @Override
    public void discardInfectedAsset(UUID tenantId, UUID actorId, AssetView asset, String threat) {
        requireTenant(tenantId);
        inTx(tenantId, c -> {
            deleteAssetRow(c, tenantId, asset.id());
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("kind", asset.kind());
            detail.put("sha256", asset.sha256());
            detail.put("threat", threat);
            audit(c, tenantId, actorId, "tenant_asset", asset.id(), "CREATE",
                    Json.write(detail), false, "varredura recusou o logo: " + threat);
            return null;
        });
    }

    @Override
    public void markAssetNotScanned(UUID tenantId, UUID assetId) {
        requireTenant(tenantId);
        inTx(tenantId, c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE public.tenant_assets SET scan_status = 'NOT_SCANNED', updated_at = NOW()"
                            + " WHERE id = ? AND tenant_id = ?")) {
                ps.setObject(1, assetId);
                ps.setObject(2, tenantId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public boolean deleteLogo(UUID tenantId, UUID actorId, String kind) {
        requireTenant(tenantId);
        return inTx(tenantId, c -> {
            String key = "LOGO_DARK".equals(kind) ? "branding.logo_dark" : "branding.logo";
            boolean hadKey = false;
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM public.tenant_settings WHERE tenant_id = ? AND setting_key = ?")) {
                ps.setObject(1, tenantId);
                ps.setString(2, key);
                hadKey = ps.executeUpdate() > 0;
            }
            // Sem unicidade por (tenant, kind) (V9_2, staging do upload):
            // pode haver o logo efetivo + uploads em quarentena do kind.
            // A remoção apaga todos, para não ressuscitar um pendente.
            UUID firstDeleted = null;
            int deleted = 0;
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM public.tenant_assets WHERE tenant_id = ? AND kind = ? RETURNING id")) {
                ps.setObject(1, tenantId);
                ps.setString(2, kind);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        if (firstDeleted == null) {
                            firstDeleted = rs.getObject(1, UUID.class);
                        }
                        deleted++;
                    }
                }
            }
            boolean found = hadKey || deleted > 0;
            if (found) {
                Map<String, Object> detail = new HashMap<>();
                detail.put("kind", kind);
                audit(c, tenantId, actorId, "tenant_asset",
                        firstDeleted != null ? firstDeleted : tenantId, "DELETE",
                        Json.write(detail), true, null);
            }
            return found;
        });
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    /**
     * Ativo do kind como o admin o vê: o apontado pela chave
     * {@code branding.*} (o logo efetivo) quando existe; sem vínculo, o
     * mais recente (um upload em quarentena aguardando varredura). A
     * preferência pelo vínculo é o que mantém a marca anterior valendo
     * durante a quarentena: o pendente só aparece na resposta do upload,
     * não como marca efetiva.
     */
    private AssetView assetByKind(Connection c, UUID tenantId, String kind, UUID linkedId) throws SQLException {
        if (linkedId != null) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, kind, content_type, width, height, sha256, scan_status, scanned_at, scanner, threat"
                            + " FROM public.tenant_assets WHERE tenant_id = ? AND kind = ? AND id = ?")) {
                ps.setObject(1, tenantId);
                ps.setString(2, kind);
                ps.setObject(3, linkedId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return assetViewOf(rs);
                    }
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, kind, content_type, width, height, sha256, scan_status, scanned_at, scanner, threat"
                        + " FROM public.tenant_assets WHERE tenant_id = ? AND kind = ?"
                        + " ORDER BY created_at DESC LIMIT 1")) {
            ps.setObject(1, tenantId);
            ps.setString(2, kind);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return assetViewOf(rs);
            }
        }
    }

    private static AssetView assetViewOf(ResultSet rs) throws SQLException {
        Timestamp scannedAt = rs.getTimestamp(8);
        return new AssetView(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                rs.getInt(4), rs.getInt(5), rs.getString(6), rs.getString(7),
                scannedAt == null ? null : scannedAt.toInstant(),
                rs.getString(9), rs.getString(10));
    }

    /** Id do ativo apontado por uma chave {@code branding.*}, ou null quando a chave não aponta. */
    private static UUID linkedAssetId(Map<String, JsonNode> settings, String key) {
        JsonNode node = settings.get(key);
        if (node == null || !node.isTextual()) {
            return null;
        }
        try {
            return UUID.fromString(node.asString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void upsertSetting(Connection c, UUID tenantId, String key, String valueJson) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO public.tenant_settings"
                        + " (id, tenant_id, setting_key, setting_value, is_sensitive)"
                        + " VALUES (uuid_generate_v4(), ?, ?, ?::jsonb, false)"
                        + " ON CONFLICT (tenant_id, setting_key) DO UPDATE"
                        + " SET setting_value = EXCLUDED.setting_value, is_sensitive = false,"
                        + " updated_at = NOW()")) {
            ps.setObject(1, tenantId);
            ps.setString(2, key);
            ps.setString(3, valueJson);
            ps.executeUpdate();
        }
    }

    private void deleteAssetRow(Connection c, UUID tenantId, UUID assetId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM public.tenant_assets WHERE id = ? AND tenant_id = ?")) {
            ps.setObject(1, assetId);
            ps.setObject(2, tenantId);
            ps.executeUpdate();
        }
    }

    private void audit(Connection c, UUID tenantId, UUID actorId, String entityType, UUID entityId,
                       String action, String detailJson, boolean success, String errorMessage) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO public.audit_log"
                        + " (tenant_id, entity_type, entity_id, action, new_values, success, error_message,"
                        + " created_by, created_at)"
                        + " VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, NOW())")) {
            ps.setObject(1, tenantId);
            ps.setString(2, entityType);
            ps.setObject(3, entityId);
            ps.setString(4, action);
            ps.setString(5, detailJson);
            ps.setBoolean(6, success);
            if (errorMessage == null) {
                ps.setNull(7, Types.VARCHAR);
            } else {
                ps.setString(7, errorMessage);
            }
            setActor(ps, 8, actorId);
            ps.executeUpdate();
        }
    }

    private static void setActor(PreparedStatement ps, int index, UUID actorId) throws SQLException {
        if (actorId == null) {
            ps.setNull(index, Types.OTHER);
        } else {
            ps.setObject(index, actorId);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asString() : null;
    }

    private static PublicLogoRef logoRefOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isObject()) {
            return null;
        }
        return new PublicLogoRef(UUID.fromString(value.get("id").asString()),
                value.get("contentType").asString(), value.get("width").asInt(),
                value.get("height").asInt(), value.get("sha256").asString());
    }

    private static void requireTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new ApiException.Forbidden("Esta operação exige um token de tenant.");
        }
    }

    @FunctionalInterface
    private interface Tx<T> {
        T run(Connection connection) throws Exception;
    }

    private <T> T inTx(UUID tenantId, Tx<T> work) {
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
                throw new IllegalStateException("Operação de marca falhou: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Operação de marca falhou: " + e.getMessage(), e);
        }
    }
}
