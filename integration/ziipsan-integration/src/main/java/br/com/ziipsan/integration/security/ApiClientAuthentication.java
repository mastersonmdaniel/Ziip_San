package br.com.ziipsan.integration.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

/**
 * Autenticação da API pública (P2.26): o principal é o cliente OAuth
 * (não um usuário), e as autoridades são os escopos do token — exatamente
 * o que os {@code @PreAuthorize} das rotas públicas verificam. Não há
 * usuário, sessão ou papel de realm aqui: a API pública é distinta da
 * API do frontend, com autenticação própria.
 */
public class ApiClientAuthentication extends AbstractAuthenticationToken {

    private final UUID tenantId;
    private final UUID clientUuid;
    private final String clientId;
    private final List<String> scopes;
    private final String environment;
    private final Integer rateLimitPerMinute;

    public ApiClientAuthentication(UUID tenantId, UUID clientUuid, String clientId,
                                   List<String> scopes, String environment) {
        this(tenantId, clientUuid, clientId, scopes, environment, null);
    }

    public ApiClientAuthentication(UUID tenantId, UUID clientUuid, String clientId,
                                   List<String> scopes, String environment, Integer rateLimitPerMinute) {
        super(scopes.stream().map(SimpleGrantedAuthority::new).toList());
        this.tenantId = tenantId;
        this.clientUuid = clientUuid;
        this.clientId = clientId;
        this.scopes = List.copyOf(scopes);
        this.environment = environment;
        this.rateLimitPerMinute = rateLimitPerMinute;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return clientId;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID clientUuid() {
        return clientUuid;
    }

    public String clientId() {
        return clientId;
    }

    public List<String> scopes() {
        return scopes;
    }

    public String environment() {
        return environment;
    }

    public boolean isSandbox() {
        return "SANDBOX".equals(environment);
    }

    /** Limite de taxa do cliente (nulo = sem limite próprio, vale tenant/teto). */
    public Integer rateLimitPerMinute() {
        return rateLimitPerMinute;
    }

    public static List<GrantedAuthority> authorities(List<String> scopes) {
        return scopes.stream().<GrantedAuthority>map(SimpleGrantedAuthority::new).toList();
    }
}
