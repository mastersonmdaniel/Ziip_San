package br.com.ziipsan.integration.service;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.integration.PublicApiScopes;
import org.springframework.stereotype.Service;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Clientes OAuth 2.0 por tenant (P2.26) — a tela de credenciais do tenant.
 *
 * <p>Criar, girar, revogar e ver o último uso. O segredo em claro só volta
 * na criação e na rotação; a listagem nunca o inclui. Regras:
 * <ul>
 *   <li>escopos só do conjunto público da v1 (fora dele, 422);</li>
 *   <li>limite de clientes ativos do parâmetro {@code api.client_max}
 *       (padrão de fábrica 10);</li>
 *   <li>limite de taxa do cliente limitado pelo parâmetro
 *       {@code api.rate_limit_per_minute} e pelo teto da plataforma;</li>
 *   <li>{@code PRIVATE_KEY_JWT} exige chave pública RSA em PEM, validada
 *       na criação (parse da chave, não só formato);</li>
 *   <li>rotação: o segredo anterior vai para {@code previous_*} com 24 h
 *       de sobreposição, e o novo segredo volta uma única vez.</li>
 * </ul>
 * mTLS e DPoP NÃO estão nesta entrega (ver o relatório: exigem terminação
 * TLS na borda e decisão do dono sobre o emissor de certificados).
 */
@Service
public class ApiClientService {

    public record ClientView(UUID id, String clientId, String name, String authMethod, List<String> scopes,
                             Integer rateLimitPerMinute, List<String> ipAllowlist, String environment,
                             String status, Instant lastUsedAt, Instant createdAt) {
    }

    public record CreatedClient(ClientView client, String secret) {
    }

    public record CreateInput(String clientId, String name, String authMethod, List<String> scopes,
                              Integer rateLimitPerMinute, List<String> ipAllowlist, String environment,
                              String publicKeyPem) {
    }

    private final DataSource dataSource;
    private final TenantParameterReader parameters;

    public ApiClientService(DataSource dataSource, TenantParameterReader parameters) {
        this.dataSource = dataSource;
        this.parameters = parameters;
    }

    public List<ClientView> list() {
        return TenantJdbc.inTenantTransaction(dataSource, c -> {
            List<ClientView> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, client_id, name, auth_method, scopes, rate_limit_per_minute,"
                            + " ip_allowlist, environment, status, last_used_at, created_at"
                            + " FROM api_clients WHERE tenant_id = ? ORDER BY created_at")) {
                ps.setObject(1, IntegrationContext.tenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(view(rs));
                    }
                }
            }
            return out;
        });
    }

    public ClientView get(UUID id) {
        return TenantJdbc.inTenantTransaction(dataSource, c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, client_id, name, auth_method, scopes, rate_limit_per_minute,"
                            + " ip_allowlist, environment, status, last_used_at, created_at"
                            + " FROM api_clients WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, IntegrationContext.tenantId());
                ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new ApiException.NotFound("O cliente de API não existe neste tenant.");
                    }
                    return view(rs);
                }
            }
        });
    }

    public CreatedClient create(CreateInput in) {
        validateCreate(in);
        UUID tenantId = IntegrationContext.tenantId();
        String schema = IntegrationContext.schema();
        Integer clientMax = parameters.intValue(tenantId, schema, "api.client_max");
        int max = clientMax == null ? 10 : clientMax;
        // O limite do cliente é gravado já limitado ao teto da plataforma; o
        // parâmetro do tenant (só reduz) aplica-se em runtime, no filtro.
        Integer storedLimit = in.rateLimitPerMinute() == null ? null
                : Math.min(in.rateLimitPerMinute(), PublicApiScopes.PLATFORM_RATE_LIMIT_PER_MINUTE);
        return TenantJdbc.inTenantTransaction(dataSource, c -> {
            try (PreparedStatement count = c.prepareStatement(
                    "SELECT count(*) FROM api_clients WHERE tenant_id = ? AND status = 'ACTIVE'")) {
                count.setObject(1, tenantId);
                try (ResultSet rs = count.executeQuery()) {
                    rs.next();
                    if (rs.getLong(1) >= max) {
                        throw new ApiException.Validation("Limite de clientes ativos do tenant atingido.",
                                List.of(new ApiException.FieldError("clientId", "limit-reached",
                                        "O tenant já tem " + max + " clientes ativos.")), List.of());
                    }
                }
            }
            String secret = null;
            String secretHash = null;
            if ("CLIENT_SECRET".equals(in.authMethod())) {
                secret = SecretHash.generate("ziip_sk_");
                secretHash = SecretHash.hash(secret);
            }
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO api_clients (id, tenant_id, client_id, name, auth_method, secret_hash,"
                            + " public_key_pem, scopes, rate_limit_per_minute, ip_allowlist, environment)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, id);
                ps.setObject(2, tenantId);
                ps.setString(3, in.clientId());
                ps.setString(4, in.name());
                ps.setString(5, in.authMethod());
                if (secretHash == null) {
                    ps.setNull(6, Types.VARCHAR);
                } else {
                    ps.setString(6, secretHash);
                }
                if (in.publicKeyPem() == null) {
                    ps.setNull(7, Types.VARCHAR);
                } else {
                    ps.setString(7, in.publicKeyPem());
                }
                ps.setArray(8, c.createArrayOf("text", in.scopes().toArray()));
                if (storedLimit == null) {
                    ps.setNull(9, Types.INTEGER);
                } else {
                    ps.setInt(9, storedLimit);
                }
                if (in.ipAllowlist() == null || in.ipAllowlist().isEmpty()) {
                    ps.setNull(10, Types.ARRAY);
                } else {
                    ps.setArray(10, c.createArrayOf("inet", in.ipAllowlist().toArray()));
                }
                ps.setString(11, in.environment() == null ? "PRODUCTION" : in.environment());
                ps.executeUpdate();
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    throw new ApiException.Validation("Já existe um cliente com este client_id no tenant.",
                            List.of(new ApiException.FieldError("clientId", "duplicate",
                                    "Escolha outro client_id.")), List.of());
                }
                throw new IllegalStateException("Falha ao criar o cliente de API", e);
            }
            return new CreatedClient(getInTx(c, id), secret);
        });
    }

    /** Rotação: o segredo anterior fica válido por 24 h; o novo volta uma única vez. */
    public CreatedClient rotateSecret(UUID id) {
        return TenantJdbc.inTenantTransaction(dataSource, c -> {
            ClientRow row = rowInTx(c, id);
            if (!"CLIENT_SECRET".equals(row.authMethod())) {
                throw new ApiException.Validation("Só cliente com segredo pode girar o segredo.",
                        List.of(new ApiException.FieldError("authMethod", "invalid",
                                "Cliente private_key_jwt gira a chave no emissor, não aqui.")), List.of());
            }
            String secret = SecretHash.generate("ziip_sk_");
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE api_clients SET secret_hash = ?, previous_secret_hash = ?,"
                            + " previous_valid_until = ?, updated_at = NOW()"
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setString(1, SecretHash.hash(secret));
                ps.setString(2, row.secretHash());
                ps.setTimestamp(3, Timestamp.from(Instant.now().plusSeconds(
                        PublicApiScopes.SECRET_OVERLAP_HOURS * 3600L)));
                ps.setObject(4, IntegrationContext.tenantId());
                ps.setObject(5, id);
                ps.executeUpdate();
            }
            return new CreatedClient(getInTx(c, id), secret);
        });
    }

    public void revoke(UUID id) {
        TenantJdbc.inTenantTransaction(dataSource, c -> {
            rowInTx(c, id);
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE api_clients SET status = 'REVOKED', updated_at = NOW()"
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, IntegrationContext.tenantId());
                ps.setObject(2, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE api_access_tokens SET revoked = true WHERE tenant_id = ? AND client_id = ?")) {
                ps.setObject(1, IntegrationContext.tenantId());
                ps.setObject(2, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** Limite efetivo por cliente: o menor entre cliente, parâmetro do tenant e teto. */
    public static int effectiveRateLimit(Integer clientLimit, Integer tenantLimit) {
        int effective = PublicApiScopes.PLATFORM_RATE_LIMIT_PER_MINUTE;
        if (clientLimit != null) {
            effective = Math.min(effective, clientLimit);
        }
        if (tenantLimit != null) {
            effective = Math.min(effective, tenantLimit);
        }
        return effective;
    }

    private void validateCreate(CreateInput in) {
        List<ApiException.FieldError> errors = new ArrayList<>();
        if (in.clientId() == null || !in.clientId().matches("[a-z0-9][a-z0-9_-]{2,79}")) {
            errors.add(new ApiException.FieldError("clientId", "invalid",
                    "O client_id usa só minúsculas, dígitos, hífen e sublinhado, de 3 a 80 caracteres."));
        }
        if (in.name() == null || in.name().isBlank()) {
            errors.add(new ApiException.FieldError("name", "required", "O nome do cliente é obrigatório."));
        }
        if (!"CLIENT_SECRET".equals(in.authMethod()) && !"PRIVATE_KEY_JWT".equals(in.authMethod())) {
            errors.add(new ApiException.FieldError("authMethod", "invalid",
                    "O método é CLIENT_SECRET ou PRIVATE_KEY_JWT."));
        }
        if (in.scopes() == null || in.scopes().isEmpty()) {
            errors.add(new ApiException.FieldError("scopes", "required",
                    "O cliente precisa de pelo menos um escopo."));
        } else {
            Set<String> unknown = new HashSet<>(in.scopes());
            unknown.removeAll(PublicApiScopes.ALL);
            if (!unknown.isEmpty()) {
                errors.add(new ApiException.FieldError("scopes", "unknown-scope",
                        "Escopos desconhecidos: " + String.join(", ", unknown) + "."));
            }
        }
        if (in.environment() != null
                && !"PRODUCTION".equals(in.environment()) && !"SANDBOX".equals(in.environment())) {
            errors.add(new ApiException.FieldError("environment", "invalid",
                    "O ambiente é PRODUCTION ou SANDBOX."));
        }
        if ("PRIVATE_KEY_JWT".equals(in.authMethod())) {
            if (in.publicKeyPem() == null || in.publicKeyPem().isBlank()) {
                errors.add(new ApiException.FieldError("publicKeyPem", "required",
                        "Cliente private_key_jwt exige a chave pública RSA em PEM."));
            } else {
                try {
                    PrivateKeyJwtValidator.parsePublicKey(in.publicKeyPem());
                } catch (Exception e) {
                    errors.add(new ApiException.FieldError("publicKeyPem", "invalid",
                            "A chave pública não é uma chave RSA em PEM válida."));
                }
            }
        }
        if (in.ipAllowlist() != null) {
            for (String entry : in.ipAllowlist()) {
                if (!isCidrOrIp(entry)) {
                    errors.add(new ApiException.FieldError("ipAllowlist", "invalid",
                            "Allowlist inválida: " + entry + ". Use IP ou CIDR."));
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new ApiException.Validation("Há campos inválidos.", errors, List.of());
        }
    }

    static boolean isCidrOrIp(String entry) {
        if (entry == null) {
            return false;
        }
        String host = entry.contains("/") ? entry.substring(0, entry.indexOf('/')) : entry;
        byte[] address;
        try {
            address = parseLiteralIp(host);
        } catch (Exception e) {
            return false;
        }
        if (entry.contains("/")) {
            try {
                int prefix = Integer.parseInt(entry.substring(entry.indexOf('/') + 1));
                int max = address.length == 4 ? 32 : 128;
                return prefix >= 0 && prefix <= max;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    /**
     * IP literal, sem DNS: um nome resolvível não é entrada válida de
     * allowlist. IPv4 em quádrupla pontuada; IPv6 literal (contém ':').
     */
    private static byte[] parseLiteralIp(String host) throws Exception {
        if (host.contains(":")) {
            java.net.InetAddress parsed = java.net.InetAddress.getByName(host);
            if (parsed.getAddress().length != 16) {
                throw new IllegalArgumentException("não é IPv6 literal");
            }
            return parsed.getAddress();
        }
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException("não é IPv4 literal");
        }
        byte[] out = new byte[4];
        for (int i = 0; i < 4; i++) {
            if (parts[i].isEmpty() || !parts[i].chars().allMatch(Character::isDigit)) {
                throw new IllegalArgumentException("octeto inválido");
            }
            int value = Integer.parseInt(parts[i]);
            if (value < 0 || value > 255) {
                throw new IllegalArgumentException("octeto fora de 0-255");
            }
            out[i] = (byte) value;
        }
        return out;
    }

    record ClientRow(UUID id, String authMethod, String secretHash) {
    }

    private ClientRow rowInTx(Connection c, UUID id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, auth_method, secret_hash FROM api_clients WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, IntegrationContext.tenantId());
            ps.setObject(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException.NotFound("O cliente de API não existe neste tenant.");
                }
                return new ClientRow((UUID) rs.getObject(1), rs.getString(2), rs.getString(3));
            }
        }
    }

    private ClientView getInTx(Connection c, UUID id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, client_id, name, auth_method, scopes, rate_limit_per_minute,"
                        + " ip_allowlist, environment, status, last_used_at, created_at"
                        + " FROM api_clients WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, IntegrationContext.tenantId());
            ps.setObject(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException.NotFound("O cliente de API não existe neste tenant.");
                }
                return view(rs);
            }
        }
    }

    private static ClientView view(ResultSet rs) throws SQLException {
        Array scopesArray = rs.getArray(5);
        List<String> scopes = scopesArray == null ? List.of()
                : List.of((String[]) scopesArray.getArray());
        Array ipArray = rs.getArray(7);
        List<String> ips = new ArrayList<>();
        if (ipArray != null) {
            for (Object o : (Object[]) ipArray.getArray()) {
                ips.add(String.valueOf(o));
            }
        }
        Timestamp lastUsed = rs.getTimestamp(10);
        Timestamp created = rs.getTimestamp(11);
        int limit = rs.getInt(6);
        return new ClientView((UUID) rs.getObject(1), rs.getString(2), rs.getString(3), rs.getString(4),
                scopes, rs.wasNull() ? null : limit, ips, rs.getString(8), rs.getString(9),
                lastUsed == null ? null : lastUsed.toInstant(),
                created == null ? null : created.toInstant());
    }
}
