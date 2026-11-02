package br.com.ziipsan.app;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0.16 opção 1, frente catálogos: os campos que o frontend já envia em catalogDefinitions.ts
 * passam a ser persistidos e devolvidos pela API, nos 4 catálogos com tabela/rota. Cada recurso é
 * criado com os novos campos, lido (GET devolve os mesmos valores), editado (PUT troca os valores)
 * e criado de novo sem os novos campos (payload mínimo continua funcionando: escalares null,
 * flags false). Nada aqui toca os casos campoDesconhecido de RegistryApiE2ETest nem a flag
 * fail-on-unknown-properties.
 *
 * <p>Não cobre o problema pré-existente do `code` ausente no formulário do frontend: os payloads
 * aqui incluem `code` porque ele continua obrigatório no backend (ver relatório da frente).
 */
class CatalogP016E2ETest extends AppIntegrationTestBase {

    private final String operator = bearer(TestTokens.token(ProvisionedTenant.TENANT_ID, "OPERATOR"));
    private final String viewer = bearer(TestTokens.token(ProvisionedTenant.TENANT_ID, "VIEWER"));

    private static String code(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    @Test
    @DisplayName("categoria: novos campos persistem, editam e são opcionais")
    void category() throws Exception {
        String catCode = code("P16C");
        String id = id(send(post("/api/v1/registry/categories"), operator, """
                {"code": "%s", "name": "Categoria P016", "abbreviation": "RES", "minimumConsumption": 10,
                 "economiesSoftLimit": 5, "requiresSpecialPermission": true}""".formatted(catCode))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.abbreviation").value("RES"))
                .andExpect(jsonPath("$.minimumConsumption").value(10))
                .andExpect(jsonPath("$.economiesSoftLimit").value(5))
                .andExpect(jsonPath("$.requiresSpecialPermission").value(true)));
        mvc.perform(get("/api/v1/registry/categories/" + id).header("Authorization", viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abbreviation").value("RES"))
                .andExpect(jsonPath("$.minimumConsumption").value(10))
                .andExpect(jsonPath("$.economiesSoftLimit").value(5))
                .andExpect(jsonPath("$.requiresSpecialPermission").value(true));
        send(put("/api/v1/registry/categories/" + id), operator, """
                {"code": "%s", "name": "Categoria P016 editada", "abbreviation": "COM", "minimumConsumption": 20,
                 "economiesSoftLimit": null, "requiresSpecialPermission": false, "version": 0}""".formatted(catCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abbreviation").value("COM"))
                .andExpect(jsonPath("$.minimumConsumption").value(20))
                .andExpect(jsonPath("$.economiesSoftLimit").isEmpty())
                .andExpect(jsonPath("$.requiresSpecialPermission").value(false));
        // payload sem os novos campos continua funcionando
        send(post("/api/v1/registry/categories"), operator, """
                {"code": "%s", "name": "Categoria minima P016"}""".formatted(code("P16C")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.abbreviation").isEmpty())
                .andExpect(jsonPath("$.minimumConsumption").isEmpty())
                .andExpect(jsonPath("$.economiesSoftLimit").isEmpty())
                .andExpect(jsonPath("$.requiresSpecialPermission").value(false));
    }

    @Test
    @DisplayName("subcategoria: novos campos persistem, editam e são opcionais")
    void subcategory() throws Exception {
        String categoryId = id(send(post("/api/v1/registry/categories"), operator, """
                {"code": "%s", "name": "Categoria mae P016"}""".formatted(code("P16C")))
                .andExpect(status().isCreated()));
        String subCode = code("P16S");
        String id = id(send(post("/api/v1/registry/subcategories"), operator, """
                {"categoryId": "%s", "code": "%s", "name": "Sub P016", "abbreviation": "SUB-ABREV",
                 "inspectionFactor": 3, "seasonalSupply": true}""".formatted(categoryId, subCode))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.abbreviation").value("SUB-ABREV"))
                .andExpect(jsonPath("$.inspectionFactor").value(3))
                .andExpect(jsonPath("$.seasonalSupply").value(true))
                .andExpect(jsonPath("$.category.id").value(categoryId)));
        mvc.perform(get("/api/v1/registry/subcategories/" + id).header("Authorization", viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abbreviation").value("SUB-ABREV"))
                .andExpect(jsonPath("$.inspectionFactor").value(3))
                .andExpect(jsonPath("$.seasonalSupply").value(true));
        send(put("/api/v1/registry/subcategories/" + id), operator, """
                {"categoryId": "%s", "code": "%s", "name": "Sub P016 editada", "abbreviation": null,
                 "inspectionFactor": 7, "seasonalSupply": false, "version": 0}""".formatted(categoryId, subCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abbreviation").isEmpty())
                .andExpect(jsonPath("$.inspectionFactor").value(7))
                .andExpect(jsonPath("$.seasonalSupply").value(false));
        send(post("/api/v1/registry/subcategories"), operator, """
                {"categoryId": "%s", "code": "%s", "name": "Sub minima P016"}""".formatted(categoryId, code("P16S")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.abbreviation").isEmpty())
                .andExpect(jsonPath("$.inspectionFactor").isEmpty())
                .andExpect(jsonPath("$.seasonalSupply").value(false));
    }

    @Test
    @DisplayName("situação de água: novos campos persistem, editam e são opcionais; flags antigas intactas")
    void waterStatus() throws Exception {
        String waterCode = code("P16W");
        String id = id(send(post("/api/v1/registry/water-statuses"), operator, """
                {"code": "%s", "name": "Agua P016", "abbreviation": "LIG", "minimumBillingConsumption": 15,
                 "hasNetwork": true, "hasConnection": true,
                 "billable": true, "requiresNetwork": true, "connected": false, "supplies": true,
                 "minimumConsumption": true}""".formatted(waterCode))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.abbreviation").value("LIG"))
                .andExpect(jsonPath("$.minimumBillingConsumption").value(15))
                .andExpect(jsonPath("$.hasNetwork").value(true))
                .andExpect(jsonPath("$.hasConnection").value(true))
                .andExpect(jsonPath("$.requiresNetwork").value(true))
                .andExpect(jsonPath("$.connected").value(false))
                .andExpect(jsonPath("$.minimumConsumption").value(true)));
        mvc.perform(get("/api/v1/registry/water-statuses/" + id).header("Authorization", viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abbreviation").value("LIG"))
                .andExpect(jsonPath("$.minimumBillingConsumption").value(15))
                .andExpect(jsonPath("$.hasNetwork").value(true))
                .andExpect(jsonPath("$.hasConnection").value(true));
        send(put("/api/v1/registry/water-statuses/" + id), operator, """
                {"code": "%s", "name": "Agua P016 editada", "abbreviation": "COR", "minimumBillingConsumption": 0,
                 "hasNetwork": false, "hasConnection": false, "version": 0}""".formatted(waterCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abbreviation").value("COR"))
                .andExpect(jsonPath("$.minimumBillingConsumption").value(0))
                .andExpect(jsonPath("$.hasNetwork").value(false))
                .andExpect(jsonPath("$.hasConnection").value(false));
        send(post("/api/v1/registry/water-statuses"), operator, """
                {"code": "%s", "name": "Agua minima P016"}""".formatted(code("P16W")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.abbreviation").isEmpty())
                .andExpect(jsonPath("$.minimumBillingConsumption").isEmpty())
                .andExpect(jsonPath("$.hasNetwork").value(false))
                .andExpect(jsonPath("$.hasConnection").value(false));
    }

    @Test
    @DisplayName("situação de esgoto: novos campos persistem, editam e são opcionais; flags antigas intactas")
    void sewerStatus() throws Exception {
        String sewerCode = code("P16E");
        String id = id(send(post("/api/v1/registry/sewer-statuses"), operator, """
                {"code": "%s", "name": "Esgoto P016", "abbreviation": "ESG", "minimumBillingVolume": 12.5,
                 "hasNetwork": true, "hasConnection": false,
                 "billable": true, "requiresNetwork": false, "connected": true,
                 "minimumConsumption": false}""".formatted(sewerCode))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.abbreviation").value("ESG"))
                .andExpect(jsonPath("$.minimumBillingVolume").value(12.5))
                .andExpect(jsonPath("$.hasNetwork").value(true))
                .andExpect(jsonPath("$.hasConnection").value(false))
                .andExpect(jsonPath("$.connected").value(true)));
        mvc.perform(get("/api/v1/registry/sewer-statuses/" + id).header("Authorization", viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abbreviation").value("ESG"))
                .andExpect(jsonPath("$.minimumBillingVolume").value(12.5))
                .andExpect(jsonPath("$.hasNetwork").value(true))
                .andExpect(jsonPath("$.hasConnection").value(false));
        send(put("/api/v1/registry/sewer-statuses/" + id), operator, """
                {"code": "%s", "name": "Esgoto P016 editado", "abbreviation": "ESG2", "minimumBillingVolume": 0,
                 "hasNetwork": false, "hasConnection": true, "version": 0}""".formatted(sewerCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abbreviation").value("ESG2"))
                .andExpect(jsonPath("$.minimumBillingVolume").value(0))
                .andExpect(jsonPath("$.hasNetwork").value(false))
                .andExpect(jsonPath("$.hasConnection").value(true));
        send(post("/api/v1/registry/sewer-statuses"), operator, """
                {"code": "%s", "name": "Esgoto minimo P016"}""".formatted(code("P16E")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.abbreviation").isEmpty())
                .andExpect(jsonPath("$.minimumBillingVolume").isEmpty())
                .andExpect(jsonPath("$.hasNetwork").value(false))
                .andExpect(jsonPath("$.hasConnection").value(false));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        return mvc.perform(request.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
