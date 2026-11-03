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
 * docs/frontend-cadastro.md §3.1: {id, username, email, fullName, roles, tenantId}.
 *
 * P0.17: a resposta NÃO inclui o nome do schema do tenant. O schema é detalhe interno de
 * infraestrutura (e o frontend nunca precisou dele para montar a sessão); expô-lo em toda
 * resposta de /me entregava o layout do banco a qualquer token válido.
 */
@RestController
public class MeController {

    public record Me(String id, String username, String fullName, String email, UUID tenantId,
                     List<String> roles, List<String> permissions) {
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
        // P1.12: as permissões `recurso:ação` expandidas pelo conversor JWT
        // (tudo que não é ROLE_*), para o frontend exibir ações por permissão.
        // A exibição nunca é a segurança: cada rota continua conferindo a
        // permissão no servidor.
        List<String> permissions = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> !a.startsWith("ROLE_"))
                .sorted()
                .toList();
        return new Me(jwt.getSubject(), jwt.getClaimAsString("preferred_username"), jwt.getClaimAsString("name"),
                jwt.getClaimAsString("email"), TenantContextHolder.getTenantId(), roles, permissions);
    }
}
