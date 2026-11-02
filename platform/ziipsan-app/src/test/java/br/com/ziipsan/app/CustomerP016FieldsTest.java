package br.com.ziipsan.app;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0.16 opcao 1, dominio cliente: os campos que o frontend ja enviava e eram descartados
 * em silencio agora sao aceitos no POST/PUT e devolvidos no GET, e payloads antigos sem
 * eles continuam funcionando. Nao toca nos casos campoDesconhecido do RegistryApiE2ETest.
 *
 * <p>Documentos sinteticos, gerados com digitos verificadores validos: nenhum CPF/CNPJ real.
 */
class CustomerP016FieldsTest extends AppIntegrationTestBase {

    private final String operator = bearer(TestTokens.token(ProvisionedTenant.TENANT_ID, "OPERATOR"));
    private final String viewer = bearer(TestTokens.token(ProvisionedTenant.TENANT_ID, "VIEWER"));

    @Test
    @DisplayName("cliente: campos P0.16 vao e voltam; payload antigo sem eles continua valido")
    void p016FieldsRoundTrip() throws Exception {
        String customerTypeId = UUID.randomUUID().toString();
        String activityBranchId = UUID.randomUUID().toString();

        // Pai (PJ com CNPJ), para o parentCustomerId do filho.
        String parentId = id(send(post("/api/v1/registry/customers"), operator, """
                {"personType": "PJ", "name": "Empresa Mae P016", "document": "%s"}"""
                .formatted(validCnpj())).andExpect(status().isCreated()));

        // Filho (PJ sem documento, com motivo): todos os campos novos de uma vez.
        MvcResult created = send(post("/api/v1/registry/customers"), operator, """
                {"personType": "PJ", "name": "Empresa Filha P016", "tradeName": "Filha P016",
                 "customerTypeId": "%s", "activityBranchId": "%s", "parentCustomerId": "%s",
                 "documentExemptionReason": "Sem documento: cadastro provisorio autorizado",
                 "billing": {"dueDay": 5, "nextMonthDue": true, "advanceInvoice": true, "blockNegativation": true},
                 "emails": [{"address": "filha.p016@exemplo.com.br"}],
                 "phones": [{"number": "+554833234567", "type": "COMMERCIAL", "primary": true,
                             "extension": "123", "contactName": "Contato Teste"}],
                 "addresses": [
                   {"address": {"cep": "88000000", "streetName": "Rua das Aguas", "number": "100",
                                "complement": "Sala 1", "neighborhood": "Centro", "city": "Florianopolis",
                                "state": "SC", "coordinates": {"lat": -27.59, "lng": -48.54}},
                    "correspondence": true},
                   {"address": {"cep": "88010000", "streetName": "Rua Segunda", "number": "200",
                                "city": "Sao Jose", "state": "SC"},
                    "correspondence": false}]}"""
                .formatted(customerTypeId, activityBranchId, parentId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerType.id").value(customerTypeId))
                .andExpect(jsonPath("$.activityBranch.id").value(activityBranchId))
                .andExpect(jsonPath("$.parentCustomer.id").value(parentId))
                .andExpect(jsonPath("$.parentCustomer.name").value("Empresa Mae P016"))
                .andExpect(jsonPath("$.documentExemptionReason").value("Sem documento: cadastro provisorio autorizado"))
                .andExpect(jsonPath("$.billing.dueDay").value(5))
                .andExpect(jsonPath("$.billing.nextMonthDue").value(true))
                .andExpect(jsonPath("$.billing.advanceInvoice").value(true))
                .andExpect(jsonPath("$.billing.blockNegativation").value(true))
                .andExpect(jsonPath("$.phones[0].extension").value("123"))
                .andExpect(jsonPath("$.phones[0].contactName").value("Contato Teste"))
                .andExpect(jsonPath("$.addresses.length()").value(2))
                .andExpect(jsonPath("$.addresses[0].address.city").value("Florianopolis"))
                .andExpect(jsonPath("$.addresses[0].address.coordinates.lat").value(-27.59))
                .andExpect(jsonPath("$.addresses[0].address.coordinates.lng").value(-48.54))
                .andExpect(jsonPath("$.addresses[0].correspondence").value(true))
                .andExpect(jsonPath("$.addresses[1].address.city").value("Sao Jose"))
                .andExpect(jsonPath("$.addresses[1].correspondence").value(false))
                .andReturn();
        String childId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        int version = JsonPath.read(created.getResponse().getContentAsString(), "$.version");

        // GET devolve os mesmos valores.
        send(get("/api/v1/registry/customers/" + childId), viewer, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerType.id").value(customerTypeId))
                .andExpect(jsonPath("$.parentCustomer.name").value("Empresa Mae P016"))
                .andExpect(jsonPath("$.billing.blockNegativation").value(true))
                .andExpect(jsonPath("$.addresses[0].address.streetName").value("Rua das Aguas"));

        // PUT troca os campos novos; a versao protege o cadastro inteiro.
        send(put("/api/v1/registry/customers/" + childId), operator, """
                {"personType": "PJ", "name": "Empresa Filha P016", "version": %d,
                 "customerTypeId": "%s", "activityBranchId": "%s", "parentCustomerId": "%s",
                 "documentExemptionReason": "Sem documento: cadastro provisorio autorizado",
                 "billing": {"dueDay": 5, "nextMonthDue": false, "advanceInvoice": true, "blockNegativation": false},
                 "emails": [{"address": "filha.p016@exemplo.com.br"}],
                 "phones": [{"number": "+554833234567", "type": "COMMERCIAL", "primary": true,
                             "extension": "999", "contactName": "Outro Contato"}],
                 "addresses": [
                   {"address": {"cep": "88000000", "streetName": "Rua das Aguas", "number": "100",
                                "city": "Florianopolis", "state": "SC"},
                    "correspondence": true}]}"""
                .formatted(version, customerTypeId, activityBranchId, parentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billing.nextMonthDue").value(false))
                .andExpect(jsonPath("$.billing.blockNegativation").value(false))
                .andExpect(jsonPath("$.phones[0].extension").value("999"))
                .andExpect(jsonPath("$.phones[0].contactName").value("Outro Contato"))
                .andExpect(jsonPath("$.addresses.length()").value(1));

        // O filtro customerTypeId da busca passa a funcionar (antes, filter-unavailable).
        send(post("/api/v1/registry/customers/search"), viewer,
                "{\"customerTypeId\": \"%s\"}".formatted(customerTypeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(childId)));

        // Coordenada pela metade e recusada, sem derrubar o resto da validacao.
        send(post("/api/v1/registry/customers"), operator, """
                {"personType": "PF", "name": "Coordenada Pela Metade", "document": "%s",
                 "addresses": [{"address": {"streetName": "Rua X", "number": "1",
                                           "coordinates": {"lat": -27.59}},
                                "correspondence": true}]}""".formatted(validCpf()))
                .andExpect(status().isUnprocessableContent());

        // Payload antigo, sem nenhum campo novo: continua valido e le os novos como vazios.
        MvcResult legacy = send(post("/api/v1/registry/customers"), operator, """
                {"personType": "PF", "name": "Cliente Legado P016", "document": "%s",
                 "birthDate": "1980-05-17", "billing": {"dueDay": 10},
                 "emails": [{"address": "legado.p016@exemplo.com.br"}],
                 "phones": [{"number": "+5511987654321", "type": "MOBILE", "primary": true}]}"""
                .formatted(validCpf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerType").value(nullValue()))
                .andExpect(jsonPath("$.activityBranch").value(nullValue()))
                .andExpect(jsonPath("$.parentCustomer").value(nullValue()))
                .andExpect(jsonPath("$.billing.nextMonthDue").value(false))
                .andExpect(jsonPath("$.billing.advanceInvoice").value(false))
                .andExpect(jsonPath("$.billing.blockNegativation").value(false))
                .andExpect(jsonPath("$.addresses.length()").value(0))
                .andExpect(jsonPath("$.phones[0].extension").value(nullValue()))
                .andExpect(jsonPath("$.phones[0].contactName").value(nullValue()))
                .andReturn();
        String legacyId = JsonPath.read(legacy.getResponse().getContentAsString(), "$.id");
        send(get("/api/v1/registry/customers/" + legacyId), viewer, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addresses.length()").value(0));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        MockHttpServletRequestBuilder r = request.header("Authorization", token);
        if (json != null) {
            r.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(r);
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    /** CPF sintetico valido: 9 digitos aleatorios + 2 digitos verificadores (mod 11). */
    private static String validCpf() {
        String base = String.format("%09d", ThreadLocalRandom.current().nextInt(100_000_000, 999_999_999));
        int d1 = checkDigit(base, 10);
        int d2 = checkDigit(base + d1, 11);
        return base + d1 + d2;
    }

    /** CNPJ sintetico valido: 12 digitos aleatorios + 2 digitos verificadores (mod 11). */
    private static String validCnpj() {
        String base = String.format("%012d", ThreadLocalRandom.current().nextLong(100_000_000_000L, 999_999_999_999L));
        int d1 = checkDigitCnpj(base, new int[]{5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
        int d2 = checkDigitCnpj(base + d1, new int[]{6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
        return base + d1 + d2;
    }

    private static int checkDigit(String digits, int startWeight) {
        int sum = 0;
        for (int i = 0; i < digits.length(); i++) {
            sum += (digits.charAt(i) - '0') * (startWeight - i);
        }
        int rest = sum % 11;
        return rest < 2 ? 0 : 11 - rest;
    }

    private static int checkDigitCnpj(String digits, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (digits.charAt(i) - '0') * weights[i];
        }
        int rest = sum % 11;
        return rest < 2 ? 0 : 11 - rest;
    }
}
