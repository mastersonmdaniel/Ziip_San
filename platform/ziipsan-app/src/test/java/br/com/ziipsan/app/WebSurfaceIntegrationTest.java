package br.com.ziipsan.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0.16: superfície web na cadeia real (CSP, HSTS, 413, validação das buscas,
 * teto de página). O rate limit fica DESLIGADO nesta base
 * ({@link AppIntegrationTestBase}) — o teste dedicado é
 * {@link RateLimitIntegrationTest}.
 */
class WebSurfaceIntegrationTest extends AppIntegrationTestBase {

    private static final String PROBLEM = "application/problem+json";
    private static final String CSP = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'";

    private String auth() {
        return bearer(TestTokens.token(ProvisionedTenant.TENANT_ID, "OPERATOR"));
    }

    @Test
    @DisplayName("CSP estrita presente em 200")
    void cspOn200() throws Exception {
        mvc.perform(get("/api/v1/me").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", CSP));
    }

    @Test
    @DisplayName("CSP presente também em 401 (o header sai antes da cadeia de autorização)")
    void cspOn401() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Security-Policy", CSP));
    }

    @Test
    @DisplayName("HSTS presente em requisição segura e ausente sem TLS")
    void hstsOnlyWhenSecure() throws Exception {
        mvc.perform(get("/api/v1/me").header("Authorization", auth()).secure(true))
                .andExpect(status().isOk())
                .andExpect(header().exists("Strict-Transport-Security"));
        mvc.perform(get("/api/v1/me").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    @Test
    @DisplayName("corpo acima de 1 MiB: 413 antes da autenticação (sem token)")
    void bodyOverLimitIs413() throws Exception {
        byte[] big = new byte[1048577];
        mvc.perform(post("/api/v1/registry/customers/search")
                        .contentType("application/json")
                        .content(big))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:content-too-large"));
    }

    @Test
    @DisplayName("POST /search com q acima de 200 caracteres: 422 (o @Size é avaliado com @Valid)")
    void searchWithLongQueryIs422() throws Exception {
        String q = "x".repeat(201);
        mvc.perform(post("/api/v1/registry/customers/search")
                        .header("Authorization", auth())
                        .contentType("application/json")
                        .content("{\"q\": \"" + q + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:validation"));
    }

    @Test
    @DisplayName("GET de lista com q acima de 200 caracteres: 422 (validação antes da recusa de busca-na-URL)")
    void listWithLongQueryIs422() throws Exception {
        mvc.perform(get("/api/v1/registry/customers")
                        .header("Authorization", auth())
                        .param("q", "x".repeat(201)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:validation"));
    }

    @Test
    @DisplayName("GET de lista com q curto: continua 400 search-in-url (regra de domínio intacta)")
    void listWithShortQueryStill400() throws Exception {
        mvc.perform(get("/api/v1/registry/customers")
                        .header("Authorization", auth())
                        .param("q", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:search-in-url"));
    }

    @Test
    @DisplayName("GET de lista com página acima do teto: 400 invalid-page, nunca 500")
    void pageOverCeilingIs400() throws Exception {
        mvc.perform(get("/api/v1/registry/customers")
                        .header("Authorization", auth())
                        .param("page", "30000000")
                        .param("size", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:invalid-page"));
    }

    @Test
    @DisplayName("P0.16 fase 2: propriedade desconhecida no corpo: 422 validation com o campo em errors[]")
    void unknownPropertyIs422() throws Exception {
        mvc.perform(post("/api/v1/registry/localities")
                        .header("Authorization", auth())
                        .contentType("application/json")
                        .content("{\"code\": 991, \"name\": \"Localidade campo desconhecido\", "
                                + "\"municipalityIbge\": \"3550308\", \"campoDesconhecido\": \"X\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:validation"))
                .andExpect(jsonPath("$.errors[0].field").value("campoDesconhecido"))
                .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_PROPERTY"));
    }

    @Test
    @DisplayName("P0.16 fase 2: corpo malformado: 422 validation, nunca 500")
    void malformedBodyIs422() throws Exception {
        mvc.perform(post("/api/v1/registry/localities")
                        .header("Authorization", auth())
                        .contentType("application/json")
                        .content("{\"code\": 992, \"name\": "))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:validation"))
                .andExpect(jsonPath("$.errors[0].field").value("body"))
                .andExpect(jsonPath("$.errors[0].code").value("MALFORMED_BODY"));
    }

    @Test
    @DisplayName("P0.16 fase 2: payload válido continua passando (201)")
    void validPayloadStillPasses() throws Exception {
        mvc.perform(post("/api/v1/registry/localities")
                        .header("Authorization", auth())
                        .contentType("application/json")
                        .content("{\"code\": 993, \"name\": \"Localidade payload válido\", "
                                + "\"municipalityIbge\": \"3550308\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(993));
    }
}
