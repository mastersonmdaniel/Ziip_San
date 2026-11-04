package br.com.ziipsan.integration.service;

import br.com.ziipsan.integration.PublicApiScopes;
import br.com.ziipsan.integration.security.ApiClientAuthentication;
import br.com.ziipsan.integration.security.CidrCheck;
import br.com.ziipsan.platform.tenant.TenantCatalog;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Token endpoint OAuth 2.0 da API pública (P2.26).
 *
 * <p>Grant suportado: {@code client_credentials}. Autenticação do cliente
 * por {@code client_secret} (corpo ou Basic) ou por {@code private_key_jwt}
 * (RFC 7523, com {@code jti} de uso único). O token emitido é opaco:
 * {@code ziip_at_<tenant(16B) + aleatório(32B)>} em base64url — o tenant
 * embutido é só a chave de busca do token (o segredo/as asserção é que
 * autentica), e o filtro o resolve pelo catálogo antes de qualquer
 * consulta ao schema do tenant.
 *
 * <p>Erros do token endpoint seguem o formato OAuth ({@code error} /
 * {@code error_description}), não problem+json: é o contrato do RFC 6749
 * para este endpoint. As rotas de recurso usam problem+json (RFC 9457).
 * O motivo exato nunca é distinguido entre cliente inexistente, segredo
 * errado, asserção inválida e tenant inativo — tudo é
 * {@code invalid_client}, sem oráculo de enumeração.
 *
 * <p>Portões, nesta ordem: tenant existe e está ativo; API ligada
 * ({@code api.enabled}); cliente existe e está ativo; credencial válida
 * (segredo vigente ou anterior na sobreposição, ou asserção válida de
 * uso único); IP na allowlist (do cliente, ou do parâmetro do tenant);
 * escopo pedido subconjunto dos escopos do cliente.
 */
@Service
public class ApiTokenService {

    /** Erro OAuth do token endpoint (código + status HTTP). */
    public static class OAuthException extends RuntimeException {
        private final String error;
        private final int status;

        public OAuthException(String error, int status, String message) {
            super(message);
            this.error = error;
            this.status = status;
        }

        public String error() {
            return error;
        }

        public int status() {
            return status;
        }
    }

    public record TokenRequest(UUID tenantId, String clientId, String grantType, String scope,
                               String clientSecret, String assertionType, String assertion) {
    }

    public record TokenResponse(String accessToken, String tokenType, int expiresIn, String scope) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final DataSource dataSource;
    private final TenantCatalog catalog;
    private final TenantParameterReader parameters;

    public ApiTokenService(DataSource dataSource, TenantCatalog catalog, TenantParameterReader parameters) {
        this.dataSource = dataSource;
        this.catalog = catalog;
        this.parameters = parameters;
    }

    public TokenResponse issue(TokenRequest req, String remoteIp) {
        if (req.tenantId() == null || req.clientId() == null || req.clientId().isBlank()) {
            throw new OAuthException("invalid_client", 401, "Credencial de cliente inválida.");
        }
        if (!"client_credentials".equals(req.grantType())) {
            throw new OAuthException("unsupported_grant_type", 400, "Só client_credentials é suportado.");
        }
        var entry = catalog.find(req.tenantId());
        if (entry.isEmpty() || !entry.get().active()) {
            throw new OAuthException("invalid_client", 401, "Credencial de cliente inválida.");
        }
        // O contexto do tenant é montado aqui (o token endpoint não passa
        // pelo filtro de API pública: ele é permitAll na cadeia de segurança).
        br.com.ziipsan.core.tenant.TenantContextHolder.setTenant(entry.get().schema());
        br.com.ziipsan.core.tenant.TenantContextHolder.setTenantId(req.tenantId());
        try {
            if (!parameters.booleanValue(req.tenantId(), entry.get().schema(), "api.enabled", false)) {
                throw new OAuthException("access_denied", 403,
                        "A API pública está desligada para este tenant.");
            }
            return TenantJdbc.inTenantTransaction(dataSource, c -> {
                ClientAuthRow client;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT id, auth_method, secret_hash, previous_secret_hash, previous_valid_until,"
                                + " public_key_pem, scopes, ip_allowlist, environment, status"
                                + " FROM api_clients WHERE tenant_id = ? AND client_id = ?")) {
                    ps.setObject(1, req.tenantId());
                    ps.setString(2, req.clientId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new OAuthException("invalid_client", 401, "Credencial de cliente inválida.");
                        }
                        client = new ClientAuthRow((UUID) rs.getObject(1), rs.getString(2), rs.getString(3),
                                rs.getString(4), rs.getTimestamp(5), rs.getString(6),
                                arrayStrings(rs.getArray(7)), ipStrings(rs.getArray(8)),
                                rs.getString(9), rs.getString(10));
                    }
                }
                if (!"ACTIVE".equals(client.status())) {
                    throw new OAuthException("invalid_client", 401, "Credencial de cliente inválida.");
                }
                authenticate(c, req, client);
                List<String> allowedIps = client.ipAllowlist().isEmpty()
                        ? parameters.stringListValue(req.tenantId(), entry.get().schema(), "api.ip_allowlist")
                        : client.ipAllowlist();
                if (!CidrCheck.allowed(remoteIp, allowedIps)) {
                    throw new OAuthException("invalid_client", 401, "Credencial de cliente inválida.");
                }
                List<String> granted = grantedScopes(req.scope(), client.scopes());
                String token = generateToken(req.tenantId());
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO api_access_tokens (id, tenant_id, client_id, token_hash, scopes, expires_at)"
                                + " VALUES (?, ?, ?, ?, ?, ?)")) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, req.tenantId());
                    ps.setObject(3, client.id());
                    ps.setString(4, SecretHash.hash(token));
                    ps.setArray(5, c.createArrayOf("text", granted.toArray()));
                    ps.setTimestamp(6, Timestamp.from(Instant.now().plusSeconds(
                            PublicApiScopes.ACCESS_TOKEN_TTL_SECONDS)));
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE api_clients SET last_used_at = NOW() WHERE tenant_id = ? AND id = ?")) {
                    ps.setObject(1, req.tenantId());
                    ps.setObject(2, client.id());
                    ps.executeUpdate();
                }
                return new TokenResponse(token, "Bearer", PublicApiScopes.ACCESS_TOKEN_TTL_SECONDS,
                        String.join(" ", granted));
            });
        } finally {
            br.com.ziipsan.core.tenant.TenantContextHolder.clear();
        }
    }

    /**
     * Valida um token de acesso (chamado pelo filtro, com o contexto do
     * tenant já montado a partir do tenant embutido no token). Devolve a
     * autenticação ou nulo se o token não vale. Também atualiza o último
     * uso do cliente e recusa token de tenant com a API desligada.
     */
    public ApiClientAuthentication validate(String token, String schema, UUID tenantId) {
        if (!parameters.booleanValue(tenantId, schema, "api.enabled", false)) {
            return null;
        }
        return TenantJdbc.inTenantTransaction(dataSource, c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT t.client_id, t.scopes, t.expires_at, t.revoked,"
                            + " cl.client_id, cl.status, cl.ip_allowlist, cl.environment, cl.rate_limit_per_minute"
                            + " FROM api_access_tokens t JOIN api_clients cl"
                            + " ON cl.tenant_id = t.tenant_id AND cl.id = t.client_id"
                            + " WHERE t.tenant_id = ? AND t.token_hash = ?")) {
                ps.setObject(1, tenantId);
                ps.setString(2, SecretHash.hash(token));
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    if (rs.getBoolean(4) || !"ACTIVE".equals(rs.getString(6))) {
                        return null;
                    }
                    Timestamp expires = rs.getTimestamp(3);
                    if (expires == null || expires.toInstant().isBefore(Instant.now())) {
                        return null;
                    }
                    List<String> scopes = arrayStrings(rs.getArray(2));
                    List<String> ips = ipStrings(rs.getArray(7));
                    UUID clientUuid = (UUID) rs.getObject(1);
                    String clientId = rs.getString(5);
                    String environment = rs.getString(8);
                    try (PreparedStatement upd = c.prepareStatement(
                            "UPDATE api_clients SET last_used_at = NOW() WHERE tenant_id = ? AND id = ?")) {
                        upd.setObject(1, tenantId);
                        upd.setObject(2, clientUuid);
                        upd.executeUpdate();
                    }
                    int clientLimit = rs.getInt(9);
                    var auth = new ApiClientAuthentication(tenantId, clientUuid, clientId, scopes, environment,
                            rs.wasNull() ? null : clientLimit);
                    auth.setDetails(ips);
                    return auth;
                }
            }
        });
    }

    private void authenticate(Connection c, TokenRequest req, ClientAuthRow client) throws java.sql.SQLException {
        Instant now = Instant.now();
        if ("CLIENT_SECRET".equals(client.authMethod())) {
            boolean current = SecretHash.matches(req.clientSecret(), client.secretHash());
            boolean previous = SecretHash.previousStillValid(client.previousSecretHash(),
                    client.previousValidUntil() == null ? null : client.previousValidUntil().toInstant(), now)
                    && SecretHash.matches(req.clientSecret(), client.previousSecretHash());
            if (!current && !previous) {
                throw new OAuthException("invalid_client", 401, "Credencial de cliente inválida.");
            }
            return;
        }
        if ("PRIVATE_KEY_JWT".equals(client.authMethod())) {
            if (!PrivateKeyJwtValidator.ASSERTION_TYPE.equals(req.assertionType())
                    || req.assertion() == null) {
                throw new OAuthException("invalid_client", 401, "Credencial de cliente inválida.");
            }
            try {
                var key = PrivateKeyJwtValidator.parsePublicKey(client.publicKeyPem());
                var claims = PrivateKeyJwtValidator.validate(req.assertion(), req.clientId(), key, now);
                String jtiHash = SecretHash.hash(claims.getJWTID());
                try (PreparedStatement check = c.prepareStatement(
                        "SELECT 1 FROM api_assertion_jtis WHERE tenant_id = ? AND jti_hash = ?")) {
                    check.setObject(1, IntegrationContext.tenantId());
                    check.setString(2, jtiHash);
                    try (ResultSet rs = check.executeQuery()) {
                        if (rs.next()) {
                            throw new OAuthException("invalid_client", 401, "Credencial de cliente inválida.");
                        }
                    }
                }
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT INTO api_assertion_jtis (tenant_id, jti_hash, expires_at) VALUES (?, ?, ?)")) {
                    ins.setObject(1, IntegrationContext.tenantId());
                    ins.setString(2, jtiHash);
                    ins.setTimestamp(3, Timestamp.from(claims.getExpirationTime().toInstant()));
                    ins.executeUpdate();
                }
            } catch (OAuthException e) {
                throw e;
            } catch (Exception e) {
                throw new OAuthException("invalid_client", 401, "Credencial de cliente inválida.");
            }
            return;
        }
        throw new OAuthException("invalid_client", 401, "Credencial de cliente inválida.");
    }

    private static List<String> grantedScopes(String requested, List<String> clientScopes) {
        if (requested == null || requested.isBlank()) {
            return List.copyOf(clientScopes);
        }
        Set<String> allowed = new HashSet<>(clientScopes);
        List<String> out = new ArrayList<>();
        for (String scope : requested.trim().split("\\s+")) {
            if (!allowed.contains(scope)) {
                throw new OAuthException("invalid_scope", 400, "Escopo pedido fora dos escopos do cliente.");
            }
            out.add(scope);
        }
        return out;
    }

    /** Token opaco: prefixo + tenant (16B) + aleatório (32B), em base64url. */
    static String generateToken(UUID tenantId) {
        ByteBuffer buffer = ByteBuffer.allocate(48);
        buffer.putLong(tenantId.getMostSignificantBits());
        buffer.putLong(tenantId.getLeastSignificantBits());
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        buffer.put(random);
        return "ziip_at_" + Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
    }

    /** Tenant embutido no token, ou nulo se o formato não vale. */
    public static UUID tokenTenant(String token) {
        if (token == null || !token.startsWith("ziip_at_")) {
            return null;
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(token.substring("ziip_at_".length()));
            if (bytes.length != 48) {
                return null;
            }
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            return new UUID(buffer.getLong(), buffer.getLong());
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> arrayStrings(Array array) throws java.sql.SQLException {
        if (array == null) {
            return List.of();
        }
        return List.of((String[]) array.getArray());
    }

    private static List<String> ipStrings(Array array) throws java.sql.SQLException {
        List<String> out = new ArrayList<>();
        if (array != null) {
            for (Object o : (Object[]) array.getArray()) {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }

    private record ClientAuthRow(UUID id, String authMethod, String secretHash, String previousSecretHash,
                                 Timestamp previousValidUntil, String publicKeyPem, List<String> scopes,
                                 List<String> ipAllowlist, String environment, String status) {
    }
}
