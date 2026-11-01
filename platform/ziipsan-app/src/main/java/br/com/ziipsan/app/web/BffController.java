package br.com.ziipsan.app.web;

import br.com.ziipsan.app.security.BffLoginSuccessHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

/**
 * Endpoints do BFF (P0.14), FORA de {@code /api/v1} de propósito: o
 * {@code RoutePermissionTest} exige permissão do catálogo em toda rota
 * {@code /api/v1}, e estas rotas são do mecanismo de login, não de negócio.
 *
 * <ul>
 *   <li>{@code GET /bff/login?returnTo=/caminho}: guarda o caminho de volta na
 *       sessão e manda o navegador ao endpoint de autorização do oauth2Login
 *       (que leva ao Keycloak). A volta é tratada pelo
 *       {@link BffLoginSuccessHandler};</li>
 *   <li>{@code GET /bff/csrf}: devolve o token anti-CSRF da sessão. O filtro de
 *       CSRF grava o mesmo token no cookie {@code XSRF-TOKEN} (legível pelo JS),
 *       que o axios reenvia em {@code X-XSRF-TOKEN}; este endpoint existe para o
 *       frontend garantir o cookie antes do primeiro POST;</li>
 *   <li>{@code POST /bff/logout}: é o {@code logoutUrl} da cadeia BFF (não um
 *       método aqui): invalida a sessão no PostgreSQL e encerra o SSO no
 *       Keycloak, voltando para {@code /login}.</li>
 * </ul>
 */
@RestController
public class BffController {

    @GetMapping("/bff/login")
    public void login(@RequestParam(required = false) String returnTo,
                      HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (returnTo != null && BffLoginSuccessHandler.isSafeReturnPath(returnTo)) {
            request.getSession(true).setAttribute(BffLoginSuccessHandler.RETURN_TO_SESSION_ATTRIBUTE, returnTo);
        }
        response.sendRedirect("/oauth2/authorization/ziipsan-bff");
    }

    @GetMapping("/bff/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "parameterName", token.getParameterName(),
                "token", token.getToken());
    }
}
