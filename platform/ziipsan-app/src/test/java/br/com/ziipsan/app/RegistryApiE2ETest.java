package br.com.ziipsan.app;

import br.com.ziipsan.commercial.registry.rules.Matricula;
import br.com.ziipsan.core.audit.AuditLogRepository;
import br.com.ziipsan.platform.tenant.TenantProvisioningService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API do cadastro de ponta a ponta: aplicação real (segurança, tenant pelo token, Hibernate com RLS),
 * banco de integração e tokens assinados. Um fluxo só, em ordem: território → catálogos → cliente com
 * CPF → imóvel, e depois concorrência, exclusão lógica, busca por documento, revelação, isolamento entre
 * tenants e a trilha de auditoria sem dado pessoal.
 *
 * <p>O tenant A é o de {@link ProvisionedTenant} (compartilhado com outros testes; por isso os códigos
 * aqui são aleatórios). O tenant B é provisionado só para este teste.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RegistryApiE2ETest extends AppIntegrationTestBase {

    private static final UUID TENANT_A = ProvisionedTenant.TENANT_ID;
    private static final UUID TENANT_B = UUID.randomUUID();
    private static final String SCHEMA_B = "it_b_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    /** CPF válido de exemplo (dígitos calculados, não é de ninguém). */
    private static final String CPF = "529.982.247-25";
    private static final String CPF_DIGITS = "52998224725";
    private static final String CUSTOMER_NAME = "Maria Ponta Aponta Cadastro";
    private static final String PURPOSE = "Conferência com o titular no atendimento";

    private static final int CODE = ThreadLocalRandom.current().nextInt(100, 900);
    private static final String TODAY = LocalDate.now().toString();

    private static Timestamp startedAt;

    private static String localityId;
    private static String sectorId;
    private static String blockId;
    private static String routeId;
    private static String subcategoryId;
    private static String waterId;
    private static String sewerId;
    private static String customerId;
    private static String unitId;
    private static String unit2Id;
    private static String inscription;

    private final String operator = bearer(TestTokens.token(TENANT_A, "OPERATOR"));
    private final String viewer = bearer(TestTokens.token(TENANT_A, "VIEWER"));
    // P0.13: o formato da permissão direta é recurso:ação (antes, registry.reveal-document).
    private final String revealer = bearer(TestTokens.token(TENANT_A, "VIEWER", "registry:reveal-document"));
    // P0.11: busca por documento exige a permissão própria, separada de leitura.
    private final String searcher = bearer(TestTokens.token(TENANT_A, "VIEWER", "registry:search-document"));
    private final String operatorB = bearer(TestTokens.token(TENANT_B, "OPERATOR", "registry:reveal-document",
            "registry:search-document"));

    @BeforeAll
    static void setUp() throws SQLException {
        ProvisionedTenant.ensure();
        var result = new TenantProvisioningService(
                ProvisionedTenant.appDs,
                new JdbcTemplate(ProvisionedTenant.appDs),
                Mockito.mock(AuditLogRepository.class),
                ProvisionedTenant.migratorDs)
                .provisionTenant(TenantProvisioningService.TenantProvisioningRequest.builder()
                        .tenantId(TENANT_B)
                        .schemaName(SCHEMA_B)
                        .displayName("Tenant B da API do cadastro")
                        .cnpj("11222333000181")
                        .email("b-api@teste.com.br")
                        .plan("PRO")
                        .build());
        assertThat(result.success()).as("provisionamento do tenant B: %s", result.error()).isTrue();
        try (Connection c = ProvisionedTenant.appDs.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT now()")) {
            rs.next();
            startedAt = rs.getTimestamp(1);
        }
    }

    @AfterAll
    static void tearDown() throws SQLException {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection(); Statement st = c.createStatement()) {
            if (SCHEMA_B.startsWith("it_b_")) {
                st.execute("DROP SCHEMA IF EXISTS " + SCHEMA_B + " CASCADE");
                // Apagar a linha do catálogo junto: o DatabaseMigrator do perfil
                // migrate percorre todo tenant ACTIVE e roda Flyway no schema dele;
                // deixar a linha sem o schema quebra teste migrate posterior.
                // P1-A: apagar tenant_vertical antes (FK para o catálogo, desde que o
                // provisionamento ativa o pacote sanitation), senao o DELETE viola a FK.
                st.execute("DELETE FROM platform.tenant_vertical WHERE tenant_id = (SELECT id FROM public.tenant_catalog WHERE schema_name = '" + SCHEMA_B + "')");
                st.execute("DELETE FROM public.tenant_catalog WHERE schema_name = '" + SCHEMA_B + "'");
            }
        }
    }

    // ------------------------------------------------------------------ fluxo de cadastro

    @Test
    @Order(1)
    @DisplayName("território: localidade → setor → rota → quadra, com página no formato do frontend")
    void territory() throws Exception {
        // P0.16 fase 2: campo desconhecido é recusado com 422 (fail-on-unknown-properties=true),
        // com o campo em errors[] — a criação válida segue abaixo para alimentar o fluxo.
        send(post("/api/v1/registry/localities"), operator, """
                {"code": %d, "name": "Localidade E2E %d", "municipalityIbge": "3550308", "campoDesconhecido": "X"}"""
                .formatted(CODE, CODE))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("campoDesconhecido"));
        localityId = id(send(post("/api/v1/registry/localities"), operator, """
                {"code": %d, "name": "Localidade E2E %d", "municipalityIbge": "3550308"}"""
                .formatted(CODE, CODE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.blocked").value(false)));

        sectorId = id(send(post("/api/v1/registry/commercial-sectors"), operator, """
                {"localityId": "%s", "code": 1, "name": "Setor E2E"}""".formatted(localityId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.locality.id").value(localityId))
                .andExpect(jsonPath("$.locality.code").value(CODE)));

        routeId = id(send(post("/api/v1/registry/reading-routes"), operator, """
                {"code": %d, "name": "Rota E2E"}""".formatted(CODE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true)));

        blockId = id(send(post("/api/v1/registry/blocks"), operator, """
                {"sectorId": "%s", "code": 3, "readingRouteId": "%s", "hasWaterNetwork": true, "hasSewerNetwork": true}"""
                .formatted(sectorId, routeId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.localityId").value(localityId))
                .andExpect(jsonPath("$.readingRoute.id").value(routeId)));

        mvc.perform(get("/api/v1/registry/localities").param("q", "Localidade E2E " + CODE).header("Authorization", viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(localityId))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(25))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
        mvc.perform(get("/api/v1/registry/blocks").param("sectorId", sectorId).header("Authorization", viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(blockId));

        // código repetido na mesma localidade: 422 no campo
        send(post("/api/v1/registry/commercial-sectors"), operator, """
                {"localityId": "%s", "code": 1, "name": "Outro"}""".formatted(localityId))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("code"))
                .andExpect(jsonPath("$.errors[0].code").value("CODE_IN_USE"));
        // leitura não grava
        send(post("/api/v1/registry/localities"), viewer, """
                {"code": %d, "name": "Sem permissão"}""".formatted(CODE + 1))
                .andExpect(status().isForbidden());
        // sem coluna de exclusão lógica: 405 explícito
        mvc.perform(delete("/api/v1/registry/localities/" + localityId).header("Authorization", operator))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:deletion-unavailable"));
    }

    @Test
    @Order(2)
    @DisplayName("catálogos: categoria, subcategoria e situações de água e de esgoto")
    void catalogs() throws Exception {
        String categoryId = id(send(post("/api/v1/registry/categories"), operator, """
                {"code": "E2E%d", "name": "Residencial E2E"}""".formatted(CODE))
                .andExpect(status().isCreated()));
        subcategoryId = id(send(post("/api/v1/registry/subcategories"), operator, """
                {"categoryId": "%s", "code": "E2E%d.1", "name": "Residencial padrão E2E"}""".formatted(categoryId, CODE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category.id").value(categoryId)));
        waterId = id(send(post("/api/v1/registry/water-statuses"), operator, """
                {"code": "LIG%d", "name": "Ligado E2E", "billable": true, "requiresNetwork": true, "connected": true, "supplies": true}"""
                .formatted(CODE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.billable").value(true))
                .andExpect(jsonPath("$.requiresNetwork").value(true)));
        sewerId = id(send(post("/api/v1/registry/sewer-statuses"), operator, """
                {"code": "LIG%d", "name": "Ligado E2E", "billable": true, "requiresNetwork": true, "connected": true}"""
                .formatted(CODE))
                .andExpect(status().isCreated()));
        mvc.perform(get("/api/v1/registry/subcategories").param("categoryId", categoryId).header("Authorization", viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @Order(3)
    @DisplayName("cliente com CPF: só a máscara volta; documento repetido é recusado")
    void customer() throws Exception {
        // P0.16 fase 2: campo desconhecido é recusado com 422 antes de qualquer
        // gravação (o CPF não é consumido); a criação válida segue abaixo.
        send(post("/api/v1/registry/customers"), operator, """
                {"personType": "PF", "name": "%s", "document": "%s", "birthDate": "1980-05-17",
                 "billing": {"dueDay": 10},
                 "emails": [{"address": "maria.e2e@exemplo.com.br"}],
                 "phones": [{"number": "+5511987654321", "type": "MOBILE", "primary": true}],
                 "campoDesconhecido": "campo que o cadastro não modela"}""".formatted(CUSTOMER_NAME, CPF))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("campoDesconhecido"));
        MvcResult created = send(post("/api/v1/registry/customers"), operator, """
                {"personType": "PF", "name": "%s", "document": "%s", "birthDate": "1980-05-17",
                 "billing": {"dueDay": 10},
                 "emails": [{"address": "maria.e2e@exemplo.com.br"}],
                 "phones": [{"number": "+5511987654321", "type": "MOBILE", "primary": true}]}""".formatted(CUSTOMER_NAME, CPF))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentMasked").value("***.***.247-**"))
                .andExpect(jsonPath("$.emails[0].primary").value(true))
                .andExpect(jsonPath("$.phones[0].primary").value(true))
                .andExpect(jsonPath("$.billing.dueDay").value(10))
                .andExpect(content().string(not(containsString(CPF_DIGITS))))
                .andExpect(content().string(not(containsString(CPF))))
                .andReturn();
        customerId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        // o mesmo CPF sem pontuação é o mesmo documento
        send(post("/api/v1/registry/customers"), operator, """
                {"personType": "PF", "name": "Outra Pessoa", "document": "%s"}""".formatted(CPF_DIGITS))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("document"))
                .andExpect(jsonPath("$.errors[0].code").value("DOCUMENT_IN_USE"));
        send(post("/api/v1/registry/customers"), operator, """
                {"personType": "PF", "name": "Documento ruim", "document": "529.982.247-26"}""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].code").value("DOCUMENT_INVALID"));

        mvc.perform(get("/api/v1/registry/customers/" + customerId).header("Authorization", viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(CUSTOMER_NAME))
                .andExpect(jsonPath("$.documentMasked").value("***.***.247-**"))
                .andExpect(jsonPath("$.unitCount").value(0))
                .andExpect(content().string(not(containsString(CPF_DIGITS))));

        // alteração sem documento mantém o atual; o telefone principal troca de linha sem violar o índice único
        send(put("/api/v1/registry/customers/" + customerId), operator, """
                {"personType": "PF", "name": "%s", "version": 0, "billing": {"dueDay": 15},
                 "emails": [{"address": "maria.e2e@exemplo.com.br"}],
                 "phones": [{"number": "+5511987654321", "type": "MOBILE", "primary": false},
                            {"number": "+5511912345678", "type": "RESIDENTIAL", "primary": true}]}"""
                .formatted(CUSTOMER_NAME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.documentMasked").value("***.***.247-**"))
                .andExpect(jsonPath("$.billing.dueDay").value(15))
                .andExpect(jsonPath("$.phones.length()").value(2))
                .andExpect(jsonPath("$.phones[?(@.number == '+5511912345678')].primary", hasItem(true)))
                .andExpect(jsonPath("$.phones[?(@.number == '+5511987654321')].primary", hasItem(false)));
        // mudar o tipo de pessoa sem reenviar o documento: 422
        send(put("/api/v1/registry/customers/" + customerId), operator, """
                {"personType": "PJ", "name": "%s", "version": 1}""".formatted(CUSTOMER_NAME))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].code").value("DOCUMENT_REQUIRED"));
    }

    @Test
    @Order(4)
    @DisplayName("busca de cliente por documento via POST: com e sem pontuação acha o mesmo; na URL é 400")
    void customerSearch() throws Exception {
        for (String q : List.of(CPF, CPF_DIGITS)) {
            send(post("/api/v1/registry/customers/search"), searcher, "{\"q\": \"" + q + "\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(customerId))
                    .andExpect(jsonPath("$.content[0].documentMasked").value("***.***.247-**"));
        }
        send(post("/api/v1/registry/customers/search"), viewer, "{\"q\": \"ponta aponta\", \"nameMatch\": \"contains\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(customerId)));

        mvc.perform(get("/api/v1/registry/customers").param("q", CPF_DIGITS).header("Authorization", viewer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:search-in-url"));
        mvc.perform(get("/api/v1/registry/customers").param("personType", "PF").header("Authorization", viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @Order(5)
    @DisplayName("imóvel: matrícula com DV, inscrição, categoria derivada, vínculos, histórico e aviso confirmável")
    void consumerUnit() throws Exception {
        MvcResult created = send(post("/api/v1/registry/consumer-units"), operator, unitBody(1, 1, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.locality.id").value(localityId))
                .andExpect(jsonPath("$.block.id").value(blockId))
                .andExpect(jsonPath("$.readingRoute.id").value(routeId))
                .andExpect(jsonPath("$.mainSubcategory.id").value(subcategoryId))
                .andExpect(jsonPath("$.economies").value(1))
                .andExpect(jsonPath("$.address.cep").value("01001000"))
                .andReturn();
        String body = created.getResponse().getContentAsString();
        unitId = JsonPath.read(body, "$.id");
        String registration = JsonPath.read(body, "$.registration");
        String dv = JsonPath.read(body, "$.registrationDv");
        assertThat(Matricula.isValid(registration + "-" + dv)).as("DV módulo 11 da matrícula").isTrue();
        inscription = JsonPath.read(body, "$.inscriptionText");
        assertThat(inscription).isEqualTo("%03d.001.003.0001.000".formatted(CODE));

        // a mesma inscrição em outro imóvel: 422 citando a matrícula que já a usa
        send(post("/api/v1/registry/consumer-units"), operator, unitBody(1, 1, null))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("lot"))
                .andExpect(jsonPath("$.errors[0].code").value("INSCRIPTION_IN_USE"))
                .andExpect(jsonPath("$.errors[0].message", containsString(registration + "-" + dv)));

        // economias acima do limite: só aviso; reenviado com o token, grava
        MvcResult warned = send(post("/api/v1/registry/consumer-units"), operator, unitBody(2, 60, null))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:confirmation-required"))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.warnings[0].code").value("UNIT_ECONOMIES_OVER_LIMIT"))
                .andReturn();
        String token = JsonPath.read(warned.getResponse().getContentAsString(), "$.warnings[0].confirmToken");
        unit2Id = id(send(post("/api/v1/registry/consumer-units"), operator, unitBody(2, 60, token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.economies").value(60)));

        send(post("/api/v1/registry/consumer-units/search"), viewer, "{\"q\": \"" + inscription + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(unitId))
                .andExpect(jsonPath("$.content[0].userName").value(CUSTOMER_NAME))
                .andExpect(jsonPath("$.content[0].userDocumentMasked").value("***.***.247-**"))
                .andExpect(jsonPath("$.content[0].waterStatusName").value("Ligado E2E"));
        send(post("/api/v1/registry/consumer-units/search"), searcher, "{\"q\": \"" + CPF_DIGITS + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/registry/consumer-units").param("q", CPF_DIGITS).header("Authorization", viewer))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/registry/consumer-units/" + unitId + "/customers").header("Authorization", viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerId").value(customerId))
                .andExpect(jsonPath("$[0].relation").value("USER"))
                .andExpect(jsonPath("$[0].showOnBill").value(true))
                .andExpect(jsonPath("$[0].customerDocumentMasked").value("***.***.247-**"));
        mvc.perform(get("/api/v1/registry/consumer-units/" + unitId + "/history").header("Authorization", viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].type", hasItem("STATUS")))
                .andExpect(jsonPath("$[*].title", hasItem("Imóvel cadastrado")));
        mvc.perform(get("/api/v1/registry/customers/" + customerId).header("Authorization", viewer))
                .andExpect(jsonPath("$.unitCount").value(2));
    }

    @Test
    @Order(6)
    @DisplayName("concorrência: PUT com a versão atual grava; com a versão velha é 409 sem errors/warnings")
    void optimisticLocking() throws Exception {
        send(put("/api/v1/registry/localities/" + localityId), operator, """
                {"code": %d, "name": "Localidade E2E %d renomeada", "version": 0}""".formatted(CODE, CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        send(put("/api/v1/registry/localities/" + localityId), operator, """
                {"code": %d, "name": "Outra edição sobre versão velha", "version": 0}""".formatted(CODE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:stale-version"))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.warnings").doesNotExist());

        MvcResult unit = mvc.perform(get("/api/v1/registry/consumer-units/" + unitId).header("Authorization", viewer))
                .andReturn();
        Number version = JsonPath.read(unit.getResponse().getContentAsString(), "$.version");
        String update = unitBody(1, 2, null).replaceFirst("\\{", "{\"version\": " + version + ", \"notes\": \"Portão azul\", ");
        send(put("/api/v1/registry/consumer-units/" + unitId), operator, update)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(version.intValue() + 1))
                .andExpect(jsonPath("$.economies").value(2));
        send(put("/api/v1/registry/consumer-units/" + unitId), operator, update)
                .andExpect(status().isConflict());
    }

    @Test
    @Order(7)
    @DisplayName("exclusão lógica: some da lista, includeDeleted traz; cliente com vínculo vigente não sai")
    void logicalDelete() throws Exception {
        send(delete("/api/v1/registry/consumer-units/" + unit2Id), operator, "{\"reason\": \"Cadastro duplicado no teste\"}")
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/registry/consumer-units").param("blockId", blockId).header("Authorization", viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(unitId));
        mvc.perform(get("/api/v1/registry/consumer-units").param("blockId", blockId).param("includeDeleted", "true")
                        .header("Authorization", viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.id == '" + unit2Id + "')].deleted", hasItem(true)));
        mvc.perform(get("/api/v1/registry/consumer-units/" + unit2Id).header("Authorization", viewer))
                // P1.15: GET por id de registro excluido devolve 404 (VIEWER nao ve
                // excluidos); a lista com includeDeleted acima continua sendo a prova
                // de que o registro existe.
                .andExpect(status().isNotFound());
        send(delete("/api/v1/registry/consumer-units/" + unit2Id), operator, "{\"reason\": \"de novo\"}")
                .andExpect(status().isConflict());
        send(delete("/api/v1/registry/customers/" + customerId), operator, "{\"reason\": \"teste\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:customer-has-active-link"));
        send(delete("/api/v1/registry/customers/" + customerId), operator, "{}")
                .andExpect(status().isUnprocessableContent());

        // cliente sem vínculo: excluído some da busca e volta com includeDeleted
        String other = id(send(post("/api/v1/registry/customers"), operator, """
                {"personType": "PJ", "name": "Empresa Excluída E2E %d"}""".formatted(CODE))
                .andExpect(status().isCreated()));
        send(delete("/api/v1/registry/customers/" + other), operator, "{\"reason\": \"Encerrou atividades\"}")
                .andExpect(status().isNoContent());
        send(post("/api/v1/registry/customers/search"), viewer, "{\"q\": \"Empresa Excluída E2E %d\"}".formatted(CODE))
                .andExpect(jsonPath("$.totalElements").value(0));
        send(post("/api/v1/registry/customers/search"), viewer,
                "{\"q\": \"Empresa Excluída E2E %d\", \"includeDeleted\": true}".formatted(CODE))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].deleted").value(true));
    }

    @Test
    @Order(8)
    @DisplayName("revelar documento: sem o papel 403; com o papel, CPF formatado e acesso registrado sem o valor")
    void revealDocument() throws Exception {
        String body = "{\"purpose\": \"" + PURPOSE + "\"}";
        send(post("/api/v1/registry/customers/" + customerId + "/reveal-document"), operator, body)
                .andExpect(status().isForbidden());
        send(post("/api/v1/registry/customers/" + customerId + "/reveal-document"), revealer, "{}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("purpose"));
        send(post("/api/v1/registry/customers/" + customerId + "/reveal-document"), revealer, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document").value(CPF))
                .andExpect(jsonPath("$.expiresInSeconds").value(15));

        List<String> reads = auditRows("entity_id = '" + customerId + "' AND action = 'READ'");
        assertThat(reads).as("registro do acesso ao documento").hasSize(1);
        assertThat(reads.getFirst()).contains(PURPOSE).contains("customers.document")
                .doesNotContain(CPF_DIGITS).doesNotContain(CPF);
    }

    @Test
    @Order(9)
    @DisplayName("isolamento: o tenant B não enxerga o cliente nem o imóvel do A (404); cabeçalho do A com token do B é 403")
    void tenantIsolation() throws Exception {
        mvc.perform(get("/api/v1/registry/customers/" + customerId).header("Authorization", operatorB))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/registry/consumer-units/" + unitId).header("Authorization", operatorB))
                .andExpect(status().isNotFound());
        send(put("/api/v1/registry/customers/" + customerId), operatorB, """
                {"personType": "PF", "name": "Invasão", "version": 0}""")
                .andExpect(status().isNotFound());
        send(post("/api/v1/registry/customers/" + customerId + "/reveal-document"), operatorB, "{\"purpose\": \"x\"}")
                .andExpect(status().isNotFound());
        send(post("/api/v1/registry/customers/search"), operatorB, "{\"q\": \"" + CPF + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/registry/localities").header("Authorization", operatorB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/registry/customers/" + customerId).header("Authorization", operatorB)
                        .header("X-Tenant-ID", ProvisionedTenant.SCHEMA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:tenant-mismatch"));

        // o mesmo CPF pode existir no tenant B: o índice cego é por tenant
        send(post("/api/v1/registry/customers"), operatorB, """
                {"personType": "PF", "name": "Titular do tenant B", "document": "%s"}""".formatted(CPF))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentMasked").value("***.***.247-**"));
    }

    @Test
    @Order(10)
    @DisplayName("nenhuma linha da auditoria gerada pelo fluxo contém o CPF em claro nem o nome do cliente")
    void auditTrailHasNoPersonalData() throws Exception {
        List<String> rows = auditRows("created_at >= ?");
        assertThat(rows).as("linhas de auditoria do fluxo").hasSizeGreaterThan(10);
        assertThat(rows).allSatisfy(r -> assertThat(r)
                .doesNotContain(CPF_DIGITS)
                .doesNotContain(CPF)
                .doesNotContain(CUSTOMER_NAME)
                .doesNotContain("maria.e2e@exemplo.com.br")
                .doesNotContain("5511987654321")
                .doesNotContain("5511912345678")
                .doesNotContain("Cadastro duplicado no teste")
                .doesNotContain("Portão azul"));
        assertThat(auditRows("entity_id = '" + customerId + "' AND action = 'CREATE'"))
                .as("a criação do cliente está na trilha").hasSize(1);
    }

    // ------------------------------------------------------------------ apoio

    private String unitBody(int lot, int economies, String confirmToken) {
        String tokens = confirmToken == null ? "" : ", \"confirmTokens\": [\"" + confirmToken + "\"]";
        return """
                {"blockId": "%s", "localityId": "%s", "lot": %d, "sublot": 0,
                 "address": {"cep": "01001-000", "streetName": "Rua das Águas E2E", "number": "%d",
                             "neighborhood": "Centro", "city": "São Paulo", "state": "SP"},
                 "customers": [{"customerId": "%s", "relation": "USER", "startDate": "%s", "showOnBill": true}],
                 "subcategories": [{"subcategoryId": "%s", "economies": %d}],
                 "waterStatusId": "%s", "sewerStatusId": "%s", "readingSequence": %d%s}"""
                .formatted(blockId, localityId, lot, 100 + lot, customerId, TODAY, subcategoryId, economies, waterId,
                        sewerId, lot * 10, tokens);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        return mvc.perform(request.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    /** Linhas da trilha do tenant A (valores antigos, novos, alterados e metadados) que casam com o filtro. */
    private static List<String> auditRows(String condition) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (Connection c = ProvisionedTenant.appDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SELECT set_config('app.current_tenant', '" + TENANT_A + "', true)");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT coalesce(old_values::text, '') || coalesce(new_values::text, '') "
                            + "|| coalesce(changed_fields::text, '') || coalesce(metadata::text, '') "
                            + "FROM public.audit_log WHERE " + condition)) {
                if (condition.contains("?")) {
                    ps.setTimestamp(1, startedAt);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(rs.getString(1));
                    }
                }
            }
            c.rollback();
        }
        return rows;
    }
}
