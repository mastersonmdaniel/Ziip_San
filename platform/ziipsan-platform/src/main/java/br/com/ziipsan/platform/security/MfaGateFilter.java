package br.com.ziipsan.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Gate de segundo fator para {@code /api/v1/platform/*} (P0.15, antes da P0.43).
 *
 * <p>Posição: DEPOIS da autenticação (Bearer ou BFF reconstruído) e ANTES do
 * {@link JwtTenantFilter} — que barra token sem {@code tenant_id} com 403
 * tenant-missing. Sem esta ordem, um token de plataforma sem segundo fator
 * receberia tenant-missing em vez de mfa-required, e o cliente não saberia que
 * o problema é o segundo fator.
 *
 * <p>Regra: em rota de plataforma, o JWT precisa trazer {@code amr} com um valor
 * de segundo fator ({@link MfaPolicy#MFA_AMR_VALUES}). Sem autenticação JWT, o
 * filtro deixa passar: quem dá 401 é a cadeia (resource server ou BFF). Fora de
 * rota de plataforma, deixa passar sempre. O filtro NÃO decide quem é usuário
 * de plataforma nem recusa token com tenant (isso é P0.43); ele só exige o
 * segundo fator onde a rota é de plataforma.
 *
 * <p>Interação documentada com o JwtTenantFilter: token de plataforma (sem
 * tenant_id) COM segundo fator passa no gate e depois cai em tenant-missing —
 * até a P0.43 isentar as rotas de plataforma do filtro de tenant. O gate não
 * está errado por isso; a rota de plataforma ainda não existe (só o gate, mais
 * rota de teste nos testes).
 */
public class MfaGateFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        String path = context != null && !context.isEmpty() && uri.startsWith(context)
                ? uri.substring(context.length()) : uri;
        if (!path.startsWith("/api/v1/platform/")) {
            chain.doFilter(request, response);
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            chain.doFilter(request, response);
            return;
        }
        List<String> amr = jwtAuth.getToken().getClaimAsStringList("amr");
        if (amr == null || amr.stream().noneMatch(MfaPolicy.MFA_AMR_VALUES::contains)) {
            ProblemResponses.write(response, 403, "mfa-required", "Segundo fator exigido",
                    "Esta rota exige segundo fator (OTP ou WebAuthn). Entre novamente com o segundo fator.");
            return;
        }
        chain.doFilter(request, response);
    }
}
