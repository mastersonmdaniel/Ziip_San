package br.com.ziipsan.app;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0.14: as duas cadeias lado a lado. A cadeia BFF casa /bff/** e /api/v1/** COM
 * cookie de sessão; sem cookie, vale a cadeia resource server (Bearer, inalterada).
 */
class BffChainIntegrationTest extends AppIntegrationTestBase {

    private static final String PROBLEM = "application/problem+json";
    /** Nome do cookie de sessão no perfil de teste (padrão do application.yml; o perfil local muda). */
    private static final String SESSION_COOKIE = "__Host-ziipsan-session";

    @Test
    @DisplayName("GET /bff/login redireciona para o endpoint de autorização do oauth2Login")
    void bffLoginRedirectsToAuthorization() throws Exception {
        mvc.perform(get("/bff/login"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/oauth2/authorization/ziipsan-bff"));
    }

    @Test
    @DisplayName("GET /bff/csrf sem sessão redireciona para o login (navegação, não 401)")
    void bffCsrfWithoutSessionRedirectsToLogin() throws Exception {
        mvc.perform(get("/bff/csrf"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/bff/login"));
    }

    @Test
    @DisplayName("XHR /api/v1 com cookie de sessão e sem autenticação: 401 em problem+json (não redirect)")
    void apiWithSessionCookieWithoutAuthIs401Json() throws Exception {
        mvc.perform(get("/api/v1/me").cookie(new Cookie(SESSION_COOKIE, "sessao-qualquer")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:unauthenticated"));
    }

    @Test
    @DisplayName("sem cookie e sem token: 401 da cadeia resource server (comportamento antigo, intacto)")
    void apiWithoutCookieWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    @DisplayName("Bearer válido continua funcionando (a cadeia resource server não mudou)")
    void bearerStillWorks() throws Exception {
        String token = TestTokens.token(ProvisionedTenant.TENANT_ID, "VIEWER");
        mvc.perform(get("/api/v1/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(ProvisionedTenant.TENANT_ID.toString()));
    }

    @Test
    @DisplayName("POST /api/v1 com cookie de sessão e sem CSRF: 403 (CSRF ligado só na cadeia BFF)")
    void postWithSessionCookieWithoutCsrfIs403() throws Exception {
        mvc.perform(post("/api/v1/registry/customers/search")
                        .cookie(new Cookie(SESSION_COOKIE, "sessao-qualquer"))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1 sem cookie e sem token: 401 (a cadeia Bearer não tem CSRF)")
    void postWithoutCookieWithoutTokenIs401Not403() throws Exception {
        mvc.perform(post("/api/v1/registry/customers/search")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Bearer com tenant desconhecido continua 403 tenant-inactive (filtro de tenant intacto)")
    void bearerUnknownTenantStill403() throws Exception {
        String token = TestTokens.token(UUID.randomUUID(), "VIEWER");
        mvc.perform(get("/api/v1/me").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:tenant-inactive"));
    }
}
