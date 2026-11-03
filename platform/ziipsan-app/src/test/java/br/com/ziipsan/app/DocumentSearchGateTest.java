package br.com.ziipsan.app;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0.11, ADR-0008, contra o PostgreSQL real: busca por documento com papel próprio,
 * registro e cota; leituras e revelações registradas em {@code personal_data_access_log}.
 *
 * <p>Cota baixa (3/hora) só neste contexto, para provar o 429 sem 60 buscas. Cada token
 * tem subject próprio, então cada teste tem seu balde de cota.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DocumentSearchGateTest extends AppIntegrationTestBase {

    private static final UUID TENANT = ProvisionedTenant.TENANT_ID;

    /** CPF válido de exemplo (dígitos calculados, não é de ninguém), só desta classe. */
    private static final String CPF = "987.654.321-00";
    /** CPF válido sem cliente, para o caso NOT_FOUND. */
    private static final String UNKNOWN_CPF = "123.456.789-09";

    private static String customerId;

    @DynamicPropertySource
    static void quota(DynamicPropertyRegistry registry) {
        registry.add("ziipsan.document-search.quota-per-hour", () -> "3");
    }

    private final String operator = bearer(TestTokens.token(TENANT, "OPERATOR"));
    private final String viewer = bearer(TestTokens.token(TENANT, "VIEWER"));
    private final String revealer = bearer(TestTokens.token(TENANT, "VIEWER", "registry:reveal-document"));

    private ResultActions send(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        return mvc.perform(request.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Contagem no log por tipo e resultado (com a GUC do tenant: a RLS é FORCE). */
    private long countLog(String accessType, String result) throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SELECT set_config('app.current_tenant', '" + TENANT + "', true)");
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT count(*) FROM " + ProvisionedTenant.SCHEMA + ".personal_data_access_log"
                                + " WHERE tenant_id = ? AND access_type = ? AND result = ?")) {
                    ps.setObject(1, TENANT);
                    ps.setString(2, accessType);
                    ps.setString(3, result);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        return rs.getLong(1);
                    }
                }
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @Order(1)
    @DisplayName("cliente do gate: criado com a máscara do teto vigente")
    void customer() throws Exception {
        MvcResult created = send(post("/api/v1/registry/customers"), operator,
                "{\"personType\": \"PF\", \"name\": \"Gate Busca Documento\", \"document\": \"" + CPF + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentMasked").value("***.***.321-**"))
                .andReturn();
        customerId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    }

    @Test
    @Order(2)
    @DisplayName("sem registry:search-document, a busca por documento é 403 e fica registrada como DENIED")
    void documentSearchWithoutPermissionIsForbiddenAndLogged() throws Exception {
        long deniedBefore = countLog("DOCUMENT_SEARCH", "DENIED");
        send(post("/api/v1/registry/customers/search"), viewer, "{\"q\": \"" + CPF + "\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:access-denied"));
        assertThat(countLog("DOCUMENT_SEARCH", "DENIED")).isEqualTo(deniedBefore + 1);
    }

    @Test
    @Order(3)
    @DisplayName("com a permissão, a busca por documento acha (FOUND) e a sem cliente registra NOT_FOUND")
    void documentSearchWithPermissionIsLogged() throws Exception {
        String searcher = bearer(TestTokens.token(TENANT, "VIEWER", "registry:search-document"));
        long foundBefore = countLog("DOCUMENT_SEARCH", "FOUND");
        long notFoundBefore = countLog("DOCUMENT_SEARCH", "NOT_FOUND");
        send(post("/api/v1/registry/customers/search"), searcher, "{\"q\": \"" + CPF + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(customerId));
        assertThat(countLog("DOCUMENT_SEARCH", "FOUND")).isEqualTo(foundBefore + 1);
        send(post("/api/v1/registry/customers/search"), searcher, "{\"q\": \"" + UNKNOWN_CPF + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        assertThat(countLog("DOCUMENT_SEARCH", "NOT_FOUND")).isEqualTo(notFoundBefore + 1);
    }

    @Test
    @Order(4)
    @DisplayName("busca por nome continua com o papel de leitura, sem a permissão própria")
    void nameSearchNeedsNoSearchPermission() throws Exception {
        send(post("/api/v1/registry/customers/search"), viewer, "{\"q\": \"Gate Busca\", \"nameMatch\": \"contains\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.hasItem(customerId)));
    }

    @Test
    @Order(5)
    @DisplayName("cota excedida: 429 com QUOTA_EXCEEDED registrado")
    void quotaExceededIsTooManyRequestsAndLogged() throws Exception {
        String searcher = bearer(TestTokens.token(TENANT, "VIEWER", "registry:search-document"));
        long exceededBefore = countLog("DOCUMENT_SEARCH", "QUOTA_EXCEEDED");
        for (int i = 0; i < 3; i++) {
            send(post("/api/v1/registry/customers/search"), searcher, "{\"q\": \"" + CPF + "\"}")
                    .andExpect(status().isOk());
        }
        send(post("/api/v1/registry/customers/search"), searcher, "{\"q\": \"" + CPF + "\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:too-many-requests"));
        assertThat(countLog("DOCUMENT_SEARCH", "QUOTA_EXCEEDED")).isEqualTo(exceededBefore + 1);
    }

    @Test
    @Order(6)
    @DisplayName("leitura de cadastro e revelação ficam registradas no log de acesso")
    void personalReadAndRevealAreLogged() throws Exception {
        long readsBefore = countLog("PERSONAL_READ", "FOUND");
        long revealsBefore = countLog("DOCUMENT_REVEAL", "FOUND");
        mvc.perform(get("/api/v1/registry/customers/" + customerId).header("Authorization", viewer))
                .andExpect(status().isOk());
        assertThat(countLog("PERSONAL_READ", "FOUND")).isEqualTo(readsBefore + 1);
        send(post("/api/v1/registry/customers/" + customerId + "/reveal-document"), revealer,
                "{\"purpose\": \"Conferência com o titular\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document").value(CPF));
        assertThat(countLog("DOCUMENT_REVEAL", "FOUND")).isEqualTo(revealsBefore + 1);
    }
}
