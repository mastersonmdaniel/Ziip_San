package br.com.ziipsan.app.web;

import br.com.ziipsan.core.tenant.TenantContextHolder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Usuário autenticado e tenant em uso, para o frontend montar a sessão. Contrato em
 * docs/frontend-cadastro.md §3.1: {id, username, email, fullName, roles, tenantId} (+ tenantSchema).
 */
@RestController
public class MeController {

    public record Me(String id, String username, String fullName, String email, UUID tenantId, String tenantSchema,
                     List<String> roles) {
    }

    @GetMapping("/api/v1/me")
    public Me me(JwtAuthenticationToken auth) {
        var jwt = auth.getToken();
        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .sorted()
                .toList();
        return new Me(jwt.getSubject(), jwt.getClaimAsString("preferred_username"), jwt.getClaimAsString("name"),
                jwt.getClaimAsString("email"), TenantContextHolder.getTenantId(), TenantContextHolder.getTenant(), roles);
    }
}
