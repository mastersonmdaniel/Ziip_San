package br.com.ziipsan.platform.security;

import br.com.ziipsan.security.auth.PermissionCatalog;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Permissões de um token a partir dos papéis do realm (P0.13, ADR-0040).
 *
 * <p>Usado pelo conversor JWT da aplicação: cada papel do token vira {@code ROLE_<papel>} (para
 * compatibilidade, ex.: {@code /api/v1/me}) e, quando o papel é um perfil de fábrica ou uma
 * permissão do catálogo, vira também as permissões {@code recurso:ação} correspondentes, que
 * são o que as rotas verificam com {@code hasAuthority(...)}. Papel desconhecido não concede
 * permissão nenhuma (negação por padrão).
 */
public final class PermissionResolver {

    private PermissionResolver() {
    }

    /** Permissões distintas, na ordem dos papéis, para os papéis dados. */
    public static Set<String> permissionsForRoles(Collection<String> roles) {
        Set<String> out = new LinkedHashSet<>();
        if (roles == null) {
            return out;
        }
        for (String role : roles) {
            out.addAll(PermissionCatalog.authoritiesForRole(role));
        }
        return out;
    }
}
