package br.com.ziipsan.app;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1.11 sobre o recurso de usuários (a única escrita com PUT da app; o POST de
 * criação é a superfície idempotente):
 *
 * <ul>
 *   <li>POST repetido com a mesma {@code Idempotency-Key} devolve a mesma resposta
 *       (201, mesmo id) e cria um único usuário;</li>
 *   <li>PUT sem {@code If-Match} recebe 428;</li>
 *   <li>PUT com versão desatualizada recebe 412;</li>
 *   <li>GET devolve o ETag e o PUT com ele passa, avançando o ETag.</li>
 * </ul>
 */
class UserIdempotencyConditionalTest extends AppIntegrationTestBase {

    private static final String ADMIN = bearer(TestTokens.token(ProvisionedTenant.TENANT_ID, "ADMIN"));

    private MvcResult createUser(String sub, String key) throws Exception {
        var req = post("/api/v1/users").header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"keycloakSub\":\"" + sub + "\",\"displayName\":\"Idem\"}");
        if (key != null) {
            req.header("Idempotency-Key", key);
        }
        return mvc.perform(req).andExpect(status().isCreated()).andReturn();
    }

    @Test
    @DisplayName("POST repetido com a mesma Idempotency-Key devolve a mesma resposta e cria um só usuário")
    void repeatedPostWithSameKeyReplays() throws Exception {
        String sub = "sub-idem-" + UUID.randomUUID();
        String key = "key-" + UUID.randomUUID();
        MvcResult first = createUser(sub, key);
        String firstBody = first.getResponse().getContentAsString();
        String id = JsonPath.read(firstBody, "$.id");

        MvcResult second = mvc.perform(post("/api/v1/users").header("Authorization", ADMIN)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keycloakSub\":\"" + sub + "\",\"displayName\":\"Idem\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id))
                .andReturn();
        assertThat(second.getResponse().getContentAsString()).isEqualTo(firstBody);

        mvc.perform(get("/api/v1/users").header("Authorization", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.keycloakSub == '" + sub + "')]").isArray())
                .andExpect(jsonPath("$[?(@.keycloakSub == '" + sub + "')]", hasSize(1)));
    }

    @Test
    @DisplayName("PUT sem If-Match é 428; com ETag desatualizado é 412; com ETag atual passa e o ETag avança")
    void conditionalPutContract() throws Exception {
        String sub = "sub-cond-" + UUID.randomUUID();
        String id = JsonPath.read(createUser(sub, null).getResponse().getContentAsString(), "$.id");
        String body = "{\"displayName\":\"Novo Nome\",\"status\":\"ACTIVE\",\"profiles\":[]}";

        mvc.perform(put("/api/v1/users/" + id).header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:precondition-required"));

        String etag = mvc.perform(get("/api/v1/users/" + id).header("Authorization", ADMIN))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andReturn().getResponse().getHeader("ETag");
        assertThat(etag).isNotBlank();

        mvc.perform(put("/api/v1/users/" + id).header("Authorization", ADMIN)
                        .header("If-Match", "\"v999999\"")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:precondition-failed"));

        mvc.perform(put("/api/v1/users/" + id).header("Authorization", ADMIN)
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Novo Nome"));

        String newEtag = mvc.perform(get("/api/v1/users/" + id).header("Authorization", ADMIN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");
        assertThat(newEtag).isNotEqualTo(etag);
        // O ETag antigo, agora desatualizado, é 412.
        mvc.perform(put("/api/v1/users/" + id).header("Authorization", ADMIN)
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionFailed());
    }
}