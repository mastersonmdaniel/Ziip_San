package br.com.ziipsan.app;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GIS-02 (P1.27), ponta a ponta pela API que a tela usa: criar tipo e item
 * do tenant, aprovar por outra pessoa, ativar e desativar — sem código nem
 * migration. O autor e o aprovador são dois tokens distintos (subjects
 * diferentes); reutilizar o token do autor na aprovação é 403.
 *
 * <p>As permissões são concessão direta ({@code gis:catalog-read} /
 * {@code gis:catalog-manage}): nenhum perfil de fábrica as inclui ainda
 * (perfis por função são da P1.37).
 */
class GisCatalogE2ETest extends AppIntegrationTestBase {

    private static final String BASE = "/api/v1/gis/catalog";

    private final String author = bearer(TestTokens.token(ProvisionedTenant.TENANT_ID, "gis:catalog-manage"));
    private final String approver = bearer(TestTokens.token(ProvisionedTenant.TENANT_ID, "gis:catalog-manage"));
    private final String reader = bearer(TestTokens.token(ProvisionedTenant.TENANT_ID, "gis:catalog-read"));
    private final String viewer = bearer(TestTokens.token(ProvisionedTenant.TENANT_ID, "VIEWER"));

    private static String code(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        return mvc.perform(request.header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String pipeClassId() throws Exception {
        String body = mvc.perform(get(BASE + "/classes").header("Authorization", reader))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        java.util.List<java.util.Map<String, Object>> classes = JsonPath.read(body, "$");
        return classes.stream().filter(c -> "PIPE".equals(c.get("code"))).findFirst()
                .orElseThrow().get("id").toString();
    }

    @Test
    @DisplayName("tipo: cria pendente, autor não aprova, outra pessoa aprova e ativa/desativa")
    void typeLifecycle() throws Exception {
        String typeCode = code("E2E");
        String typeId = id(send(post(BASE + "/types"), author, """
                {"classId": "%s", "code": "%s", "label": "Tipo E2E",
                 "attributesSchema": "{\\"type\\": \\"object\\", \\"properties\\": {\\"diametro\\": {\\"type\\": \\"integer\\"}}}"}
                """.formatted(pipeClassId(), typeCode))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.approvalStatus").value("PENDING"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.origin").value("TENANT")));

        send(post(BASE + "/types/" + typeId + "/approve"), author, "")
                .andExpect(status().isForbidden());
        send(post(BASE + "/types/" + typeId + "/approve"), approver, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("APPROVED"))
                .andExpect(jsonPath("$.active").value(true));

        send(patch(BASE + "/types/" + typeId + "/active"), approver, "{\"active\": false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        send(patch(BASE + "/types/" + typeId + "/active"), approver, "{\"active\": true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        // Leitura com gis:catalog-read; sem a permissão, 403.
        mvc.perform(get(BASE + "/types/" + typeId).header("Authorization", reader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(typeCode.toUpperCase()));
        mvc.perform(get(BASE + "/types/" + typeId).header("Authorization", viewer))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("item: cria pendente em tipo aprovado, aprova por outra pessoa, desativa some do usável")
    void itemLifecycle() throws Exception {
        String typeId = id(send(post(BASE + "/types"), author, """
                {"classId": "%s", "code": "%s", "label": "Tipo do item E2E"}
                """.formatted(pipeClassId(), code("E2EI")))
                .andExpect(status().isCreated()));
        send(post(BASE + "/types/" + typeId + "/approve"), approver, "")
                .andExpect(status().isOk());

        String itemId = id(send(post(BASE + "/items"), author, """
                {"featureTypeId": "%s", "dnMm": 123, "pressureClass": "PN16"}
                """.formatted(typeId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.approvalStatus").value("PENDING"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.referenceStatus").value("TENANT")));

        send(post(BASE + "/items/" + itemId + "/approve"), author, "")
                .andExpect(status().isForbidden());
        send(post(BASE + "/items/" + itemId + "/approve"), approver, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("APPROVED"))
                .andExpect(jsonPath("$.active").value(true));

        // Duplicata exata é recusada.
        send(post(BASE + "/items"), author, """
                {"featureTypeId": "%s", "dnMm": 123, "pressureClass": "PN16"}
                """.formatted(typeId))
                .andExpect(status().isConflict());

        // Desativado some do usável (a lista que a criação de feição usa).
        send(patch(BASE + "/items/" + itemId + "/active"), approver, "{\"active\": false}")
                .andExpect(status().isOk());
        mvc.perform(get(BASE + "/items?featureTypeId=" + typeId + "&usable=true")
                        .header("Authorization", reader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(BASE + "/items?featureTypeId=" + typeId).header("Authorization", reader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Item em tipo pendente é recusado.
        String pendingType = id(send(post(BASE + "/types"), author, """
                {"classId": "%s", "code": "%s", "label": "Tipo pendente"}
                """.formatted(pipeClassId(), code("E2EP")))
                .andExpect(status().isCreated()));
        send(post(BASE + "/items"), author, """
                {"featureTypeId": "%s", "dnMm": 100}
                """.formatted(pendingType))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("catálogo de fábrica presente e relatório: nenhum STANDARD, compra P1.28 pendente")
    void factoryCatalogAndReport() throws Exception {
        mvc.perform(get(BASE + "/types?q=UTS").header("Authorization", reader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].origin").value("FACTORY"))
                .andExpect(jsonPath("$[0].approvalStatus").value("APPROVED"));
        mvc.perform(get(BASE + "/types?q=MOBILE_BOOSTER").header("Authorization", reader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].origin").value("FACTORY"));
        mvc.perform(get(BASE + "/report/reference-to-validate").header("Authorization", reader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.factoryItemsStandard").value(0))
                .andExpect(jsonPath("$.factoryItemsToValidate").value(261))
                .andExpect(jsonPath("$.normsPurchasePending").value(true));
    }

    @Test
    @DisplayName("code do tipo é obrigatório: ausente ou em branco é 422 (convenção do GlobalExceptionHandler) e nomeia o campo")
    void typeCodeIsRequired() throws Exception {
        send(post(BASE + "/types"), author, """
                {"classId": "%s", "label": "Sem code"}
                """.formatted(pipeClassId()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("code"));
        send(post(BASE + "/types"), author, """
                {"classId": "%s", "code": "   ", "label": "Code em branco"}
                """.formatted(pipeClassId()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("code"));
    }
}
