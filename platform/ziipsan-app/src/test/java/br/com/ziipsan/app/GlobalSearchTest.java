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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1.33 contra o PostgreSQL real: busca global por teclado sobre cliente, unidade, OS,
 * conta e documento, com permissão por seção, escopo pendente (P3.30) e protocolo
 * pendente (P1.36); e visões salvas (V1_17) sem compartilhamento fingido.
 *
 * <p>O gate de CPF é o da P0.11/ADR-0008, exercido aqui pela busca global: sem
 * {@code registry:search-document}, a busca por documento completo é 403 e fica
 * DENIED; com ela, é um único registro FOUND/NOT_FOUND por busca global e uma
 * única cota (a busca global NÃO autoriza duas vezes, uma por seção de cadastro).
 * Documento parcial nunca casa: "372.851" volta 200 sem cliente e sem
 * {@code documentSearch}. Cota baixa (3/hora) só neste contexto, com token
 * dedicado — cada token tem subject próprio, então cada teste tem seu balde.
 *
 * <p>Dados sintéticos, sem pessoa real: o CPF 372.851.904-97 é sintético com DV calculado, sem uso em nenhum outro
 * teste do módulo (529.982.247-25 colidiria com o RegistryApiE2ETest), e a matrícula
 * 12345 é inventada para o teste.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GlobalSearchTest extends AppIntegrationTestBase {

    private static final UUID TENANT = ProvisionedTenant.TENANT_ID;
    private static final String SCHEMA = ProvisionedTenant.SCHEMA;

    /** CPF sintético válido (exemplo clássico de DV), sem cliente fora deste teste. */
    private static final String CPF = "372.851.904-97";
    private static final String MATRICULA = "12345";

    private static String customerId;
    private static String unitId;
    private static String billId;
    private static String orderId;

    private static final String FULL =
            bearer(TestTokens.token(TENANT, "VIEWER", "search:read", "service-order:read", "billing:read", "document:read"));
    private static final String SEARCH_ONLY = bearer(TestTokens.token(TENANT, "search:read"));
    private static final String NO_SEARCH = bearer(TestTokens.token(TENANT, "VIEWER"));
    private static final String DOC_SEARCHER =
            bearer(TestTokens.token(TENANT, "VIEWER", "search:read", "registry:search-document"));
    private static final String QUOTA_TOKEN =
            bearer(TestTokens.token(TENANT, "VIEWER", "search:read", "registry:search-document"));
    private static final String OPERATOR = bearer(TestTokens.token(TENANT, "OPERATOR", "search:read"));

    @DynamicPropertySource
    static void quota(DynamicPropertyRegistry registry) {
        registry.add("ziipsan.document-search.quota-per-hour", () -> "3");
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        return mvc.perform(request.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", token));
    }

    private ResultActions global(String token, String json) throws Exception {
        return send(post("/api/v1/search/global"), token, json);
    }

    private long countLog(String accessType, String result) throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SELECT set_config('app.current_tenant', '" + TENANT + "', true)");
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT count(*) FROM " + SCHEMA + ".personal_data_access_log"
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

    /** INSERT com RLS forçada: GUC do tenant + search_path do schema, na transação. */
    private void sql(String... statements) throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SELECT set_config('app.current_tenant', '" + TENANT + "', true)");
                st.execute("SET LOCAL search_path TO " + SCHEMA + ", public");
                for (String sql : statements) {
                    st.execute(sql);
                }
            }
            c.commit();
        }
    }

    @Test
    @Order(1)
    @DisplayName("cliente do gate global: criado com a máscara do teto vigente")
    void customer() throws Exception {
        MvcResult created = send(post("/api/v1/registry/customers"), OPERATOR,
                "{\"personType\": \"PF\", \"name\": \"Global Busca Cliente\", \"document\": \"" + CPF + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentMasked").value("***.***.904-**"))
                .andReturn();
        customerId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    }

    @Test
    @Order(2)
    @DisplayName("fixtures: unidade + vínculo, tipo/OS, conta e documentos (só CLEAN aparece)")
    void fixtures() throws Exception {
        unitId = UUID.randomUUID().toString();
        billId = UUID.randomUUID().toString();
        String typeId = UUID.randomUUID().toString();
        orderId = UUID.randomUUID().toString();
        String docTypeId = UUID.randomUUID().toString();
        String retentionId = UUID.randomUUID().toString();
        String cleanDocId = UUID.randomUUID().toString();
        String quarantinedDocId = UUID.randomUUID().toString();
        sql(
                "INSERT INTO " + SCHEMA + ".consumer_units (id, tenant_id, matricula, dv) VALUES ('"
                        + unitId + "', '" + TENANT + "', '" + MATRICULA + "', '0')",
                "INSERT INTO " + SCHEMA + ".unit_customers (id, tenant_id, unit_id, customer_id, role, start_date, show_on_bill)"
                        + " VALUES ('" + UUID.randomUUID() + "', '" + TENANT + "', '" + unitId + "', '" + customerId
                        + "', 'OWNER', CURRENT_DATE, true)",
                "INSERT INTO " + SCHEMA + ".service_types (id, tenant_id, code, name, operation, effect_code, indicator, target_classes)"
                        + " VALUES ('" + typeId + "', '" + TENANT + "', 'GLOBALTEST', 'Serviço Global', 'GLOBAL_OP', 'GLOBAL_EFFECT', 'INTERNAL', ARRAY['CONSUMER_UNIT'])",
                "INSERT INTO " + SCHEMA + ".service_orders (id, tenant_id, service_type_id, origin, execution_mode, priority, status, created_by)"
                        + " VALUES ('" + orderId + "', '" + TENANT + "', '" + typeId + "', 'INTERNAL', 'INTERNAL', 'NORMAL', 'DRAFT', '" + TENANT + "')",
                "INSERT INTO " + SCHEMA + ".bills (id, tenant_id, unit_id, reference_month, status, water_value, sewer_value, debits_value, credits_value, taxes_value, total_value)"
                        + " VALUES ('" + billId + "', '" + TENANT + "', '" + unitId + "', DATE '2026-10-01', 'NORMAL', 100, 0, 0, 0, 0, 100)",
                "INSERT INTO " + SCHEMA + ".document_types (id, tenant_id, code, label) VALUES ('"
                        + docTypeId + "', '" + TENANT + "', 'CONTRATO', 'Contrato')",
                "INSERT INTO " + SCHEMA + ".retention_classes (id, tenant_id, code, label, retention_months) VALUES ('"
                        + retentionId + "', '" + TENANT + "', 'G6', 'Grupo G6', 60)",
                "INSERT INTO " + SCHEMA + ".documents (id, tenant_id, document_type_id, retention_class_id, data_classification, scan_status, original_filename, detected_content_type, size_bytes, sha256, storage_bucket, storage_key)"
                        + " VALUES ('" + cleanDocId + "', '" + TENANT + "', '" + docTypeId + "', '" + retentionId
                        + "', 'CT', 'CLEAN', 'contrato-global.pdf', 'application/pdf', 10, '"
                        + "a".repeat(64) + "', 'documents', 'contrato-global.pdf')",
                "INSERT INTO " + SCHEMA + ".documents (id, tenant_id, document_type_id, retention_class_id, data_classification, scan_status, original_filename, detected_content_type, size_bytes, sha256, storage_bucket, storage_key)"
                        + " VALUES ('" + quarantinedDocId + "', '" + TENANT + "', '" + docTypeId + "', '" + retentionId
                        + "', 'CT', 'QUARANTINED', 'quarentena-global.pdf', 'application/pdf', 10, '"
                        + "b".repeat(64) + "', 'documents', 'quarentena-global.pdf')");
    }

    @Test
    @Order(3)
    @DisplayName("busca por texto: cliente, unidade, OS e documento CLEAN; protocolo pendente, nada negado")
    void textSearchFindsSections() throws Exception {
        global(FULL, "{\"q\": \"Global\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentSearch").value(false))
                .andExpect(jsonPath("$.pendingTypes[0]").value("PROTOCOL"))
                .andExpect(jsonPath("$.deniedTypes").isEmpty())
                .andExpect(jsonPath("$.results[?(@.type == 'CUSTOMER')].id").value(org.hamcrest.Matchers.hasItem(customerId)))
                .andExpect(jsonPath("$.results[?(@.type == 'CONSUMER_UNIT')].id").value(org.hamcrest.Matchers.hasItem(unitId)))
                .andExpect(jsonPath("$.results[?(@.type == 'SERVICE_ORDER')].id").value(org.hamcrest.Matchers.hasItem(orderId)))
                .andExpect(jsonPath("$.results[?(@.type == 'DOCUMENT')].title").value(org.hamcrest.Matchers.hasItem("contrato-global.pdf")));
    }

    @Test
    @Order(4)
    @DisplayName("busca por matrícula: unidade e conta; documento em quarentena nunca aparece")
    void matriculaAndQuarantine() throws Exception {
        global(FULL, "{\"q\": \"" + MATRICULA + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[?(@.type == 'CONSUMER_UNIT')].id").value(org.hamcrest.Matchers.hasItem(unitId)))
                .andExpect(jsonPath("$.results[?(@.type == 'BILL')].id").value(org.hamcrest.Matchers.hasItem(billId)));
        global(FULL, "{\"q\": \"quarentena\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[?(@.type == 'DOCUMENT')]").isEmpty());
    }

    @Test
    @Order(5)
    @DisplayName("sem registry:search-document, a busca global por documento é 403 com DENIED — não contorna o CPF")
    void documentSearchWithoutPermissionIsForbidden() throws Exception {
        long deniedBefore = countLog("DOCUMENT_SEARCH", "DENIED");
        global(FULL, "{\"q\": \"" + CPF + "\"}").andExpect(status().isForbidden());
        assertThat(countLog("DOCUMENT_SEARCH", "DENIED")).isEqualTo(deniedBefore + 1);
    }

    @Test
    @Order(6)
    @DisplayName("com a permissão, a busca global por documento acha cliente e unidade; parcial não casa")
    void documentSearchWithPermission() throws Exception {
        long foundBefore = countLog("DOCUMENT_SEARCH", "FOUND");
        global(DOC_SEARCHER, "{\"q\": \"" + CPF + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentSearch").value(true))
                .andExpect(jsonPath("$.results[?(@.type == 'CUSTOMER')].id").value(org.hamcrest.Matchers.hasItem(customerId)))
                .andExpect(jsonPath("$.results[?(@.type == 'CONSUMER_UNIT')].id").value(org.hamcrest.Matchers.hasItem(unitId)));
        assertThat(countLog("DOCUMENT_SEARCH", "FOUND")).isEqualTo(foundBefore + 1);
        // Documento parcial: 200, sem casa, sem flag de busca por documento.
        global(DOC_SEARCHER, "{\"q\": \"372.851\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentSearch").value(false))
                .andExpect(jsonPath("$.results[?(@.type == 'CUSTOMER')]").isEmpty());
    }

    @Test
    @Order(7)
    @DisplayName("cota da busca global: 3 buscas por documento OK, a 4ª é 429 com QUOTA_EXCEEDED")
    void quotaIsPerGlobalSearch() throws Exception {
        long exceededBefore = countLog("DOCUMENT_SEARCH", "QUOTA_EXCEEDED");
        for (int i = 0; i < 3; i++) {
            global(QUOTA_TOKEN, "{\"q\": \"" + CPF + "\"}").andExpect(status().isOk());
        }
        global(QUOTA_TOKEN, "{\"q\": \"" + CPF + "\"}").andExpect(status().isTooManyRequests());
        assertThat(countLog("DOCUMENT_SEARCH", "QUOTA_EXCEEDED")).isEqualTo(exceededBefore + 1);
    }

    @Test
    @Order(8)
    @DisplayName("sem search:read, a rota é 403; só com ele, as seções sem permissão voltam negadas, sem dado")
    void permissionGating() throws Exception {
        global(NO_SEARCH, "{\"q\": \"Global\"}").andExpect(status().isForbidden());
        global(SEARCH_ONLY, "{\"q\": \"Global\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isEmpty())
                .andExpect(jsonPath("$.deniedTypes").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "CUSTOMER", "CONSUMER_UNIT", "SERVICE_ORDER", "BILL", "DOCUMENT")));
    }

    @Test
    @Order(9)
    @DisplayName("validação: texto vazio e tipo desconhecido são 422")
    void validation() throws Exception {
        global(FULL, "{\"q\": \"\"}").andExpect(status().isUnprocessableEntity());
        global(FULL, "{\"q\": \"Global\", \"types\": [\"NOPE\"]}").andExpect(status().isUnprocessableEntity());
    }

    @Test
    @Order(10)
    @DisplayName("visões salvas: CRUD do dono, 404 para outro dono, sem compartilhamento fingido")
    void savedViews() throws Exception {
        String other = bearer(TestTokens.token(TENANT, "search:read"));
        MvcResult created = send(post("/api/v1/search/saved-views"), FULL,
                "{\"title\": \"Pendências\", \"resource\": \"customers\", \"filters\": \"{\\\"q\\\": \\\"Global\\\"}\", \"density\": \"compact\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Pendências"))
                .andExpect(jsonPath("$.density").value("compact"))
                .andReturn();
        String viewId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        // Título duplicado do mesmo dono: 409.
        send(post("/api/v1/search/saved-views"), FULL,
                "{\"title\": \"Pendências\", \"resource\": \"customers\"}")
                .andExpect(status().isConflict());
        // Filtros inválidos e ORG_UNIT sem unidade: 422.
        send(post("/api/v1/search/saved-views"), FULL,
                "{\"title\": \"Quebrada\", \"resource\": \"customers\", \"filters\": \"{x\"}")
                .andExpect(status().isUnprocessableEntity());
        send(post("/api/v1/search/saved-views"), FULL,
                "{\"title\": \"Compartilhada\", \"resource\": \"customers\", \"visibility\": \"ORG_UNIT\"}")
                .andExpect(status().isUnprocessableEntity());
        // ORG_UNIT com unidade: grava, mas o outro dono NÃO a lista nem a lê (P3.30 pendente).
        MvcResult shared = send(post("/api/v1/search/saved-views"), FULL,
                "{\"title\": \"Compartilhada\", \"resource\": \"customers\", \"visibility\": \"ORG_UNIT\", \"sharedOrgUnitId\": \""
                        + UUID.randomUUID() + "\"}")
                .andExpect(status().isCreated())
                .andReturn();
        String sharedId = JsonPath.read(shared.getResponse().getContentAsString(), "$.id");
        send(get("/api/v1/search/saved-views"), other)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem(viewId))))
                .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem(sharedId))));
        send(get("/api/v1/search/saved-views/" + viewId), other).andExpect(status().isNotFound());
        send(get("/api/v1/search/saved-views/" + viewId), FULL).andExpect(status().isOk());
        send(delete("/api/v1/search/saved-views/" + viewId), FULL).andExpect(status().isNoContent());
        send(get("/api/v1/search/saved-views/" + viewId), FULL).andExpect(status().isNotFound());
        send(delete("/api/v1/search/saved-views/" + sharedId), FULL).andExpect(status().isNoContent());
    }
}
