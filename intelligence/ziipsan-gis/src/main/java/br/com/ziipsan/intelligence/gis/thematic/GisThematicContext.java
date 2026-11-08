package br.com.ziipsan.intelligence.gis.thematic;

import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.intelligence.gis.publication.GisPermissions;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tenant, usuário e permissões da requisição para a GIS-08, no
 * mesmo desenho do {@code GisPublicationContext} (GIS-06): o tenant
 * vem SEMPRE do contexto (token) — nunca da URL nem do corpo — e é
 * ele que alimenta todos os filtros de tenant das consultas.
 */
final class GisThematicContext {

    private GisThematicContext() {
    }

    static UUID tenantId() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("sem tenant no contexto da requisição");
        }
        return tenantId;
    }

    static UUID userId() {
        UUID userId = TenantContextHolder.getUserId();
        if (userId == null) {
            throw new IllegalStateException("sem usuário no contexto da requisição");
        }
        return userId;
    }

    static Set<String> authorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return Set.of();
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());
    }

    static boolean canViewPersonal() {
        return authorities().contains(GisPermissions.VIEW_PERSONAL);
    }

    static boolean hasAuthority(String authority) {
        return authorities().contains(authority);
    }
}
