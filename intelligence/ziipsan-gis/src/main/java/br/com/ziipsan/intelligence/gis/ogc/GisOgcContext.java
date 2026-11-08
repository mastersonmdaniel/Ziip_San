package br.com.ziipsan.intelligence.gis.ogc;

import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.intelligence.gis.publication.GisPermissions;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Contexto da requisição OGC/exportação, no mesmo desenho do
 * GisPublicationContext da GIS-06: tenant e usuário SEMPRE do
 * contexto (token) — nunca da URL, do workspace pedido ou do corpo.
 * Credencial de terceiro é a que NÃO carrega gis:view do backoffice
 * completo? Não: terceiro é marcado pela authority explícita
 * {@code gis:third-party} quando a integração a emitir; sem ela, a
 * audiência é BACKOFFICE. (A emissão da credencial OGC é da
 * integração — pendência declarada.)
 */
public final class GisOgcContext {

    public static final String THIRD_PARTY_AUTHORITY = "gis:third-party";

    private GisOgcContext(){}

    public static UUID tenantId() {
        UUID t = TenantContextHolder.getTenantId();
        if (t == null) throw new IllegalStateException("sem tenant no contexto da requisição");
        return t;
    }

    public static UUID userId() {
        UUID u = TenantContextHolder.getUserId();
        if (u == null) throw new IllegalStateException("sem usuário no contexto da requisição");
        return u;
    }

    public static Set<String> authorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return Set.of();
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static boolean canViewPersonal(){ return authorities().contains(GisPermissions.VIEW_PERSONAL); }
    public static boolean thirdParty(){ return authorities().contains(THIRD_PARTY_AUTHORITY); }
}
