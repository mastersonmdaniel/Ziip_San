package br.com.ziipsan.app.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;

/**
 * Volta do login BFF (P0.14). O frontend pede {@code GET /bff/login?returnTo=/caminho}
 * antes de ir ao Keycloak; o caminho fica guardado na sessão (nunca em storage do
 * navegador) e, no sucesso, o navegador volta para ele. Só aceita caminho interno
 * (mesma regra do frontend: começa com {@code /}, sem {@code //}, sem {@code /bff}
 * e sem {@code /login}); qualquer outra coisa vira {@code /}. Sem returnTo, {@code /}.
 */
public class BffLoginSuccessHandler implements AuthenticationSuccessHandler {

    public static final String RETURN_TO_SESSION_ATTRIBUTE = "ziipsan-bff-return-to";

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        String returnTo = "/";
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object stored = session.getAttribute(RETURN_TO_SESSION_ATTRIBUTE);
            if (stored instanceof String value && isSafeReturnPath(value)) {
                returnTo = value;
            }
            session.removeAttribute(RETURN_TO_SESSION_ATTRIBUTE);
        }
        response.sendRedirect(returnTo);
    }

    public static boolean isSafeReturnPath(String value) {
        return value.startsWith("/") && !value.startsWith("//") && !value.startsWith("/\\")
                && !value.startsWith("/bff") && !value.startsWith("/login")
                && !value.startsWith("/auth/callback");
    }
}
