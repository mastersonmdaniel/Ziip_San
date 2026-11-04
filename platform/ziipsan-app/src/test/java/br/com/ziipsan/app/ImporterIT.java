package br.com.ziipsan.app;

import br.com.ziipsan.app.importer.ImportJobHandler;
import br.com.ziipsan.commercial.registry.importer.ImportService;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.platform.infra.queue.OutboxMessage;
import br.com.ziipsan.platform.tenant.TenantCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1.40: importador genérico de planilha de ponta a ponta, sobre o tenant
 * compartilhado de {@link ProvisionedTenant}.
 *
 * <p>O worker da P1.9 ainda não tem runner no app, então o IT chama o handler
 * diretamente (mesma entrada que o worker usaria, com o tenant explícito na
 * mensagem); o controlador só enfileira (202). Códigos únicos por teste para
 * não poluir os demais testes do tenant compartilhado.
 */
class ImporterIT extends AppIntegrationTestBase {

    private static final UUID TENANT_A = ProvisionedTenant.TENANT_ID;
    private static final String SCHEMA = ProvisionedTenant.SCHEMA;

    private final String operator = bearer(TestTokens.token(TENANT_A, "OPERATOR"));
    private final String viewer = bearer(TestTokens.token(TENANT_A, "VIEWER"));

    @Autowired
    private TenantCatalog catalog;

    @Autowired
    private ImportService imports;

    private static String uniq(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String upload(String schemaCode, String fileName, String csv) throws Exception {
        MvcResult result = mvc.perform(multipart("/api/v1/registry/imports")
                        .file(new MockMultipartFile("file", fileName, "text/csv",
                                csv.getBytes(StandardCharsets.UTF_8)))
                        .param("schemaCode", schemaCode)
                        .header("Authorization", operator))
                .andExpect(status().isCreated())
                .andReturn();
        return Json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("id").asText();
    }

    private void runHandler(String type, String jobId) throws Exception {
        new ImportJobHandler(catalog, imports, type).handle(new OutboxMessage(
                UUID.randomUUID(), TENANT_A, "default", type,
                Json.write(Map.of("importId", jobId)), 0));
    }

    private String jobStatus(String jobId) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/registry/imports/" + jobId)
                        .header("Authorization", viewer))
                .andExpect(status().isOk())
                .andReturn();
        return Json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("status").asText();
    }

    private String jobField(String jobId, String field) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/registry/imports/" + jobId)
                        .header("Authorization", viewer))
                .andExpect(status().isOk())
                .andReturn();
        var node = Json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    private void insertCategory(String code) throws Exception {
        try (Connection c = ProvisionedTenant.appDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SELECT set_config('app.current_tenant', '" + TENANT_A + "', true)");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + SCHEMA + ".categories (id, tenant_id, code, name)"
                            + " VALUES (?, ?, ?, 'Preexistente')")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, TENANT_A);
                ps.setString(3, code);
                ps.executeUpdate();
            }
            c.commit();
        }
    }

    private boolean categoryActive(String code) throws Exception {
        try (Connection c = ProvisionedTenant.appDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SELECT set_config('app.current_tenant', '" + TENANT_A + "', true)");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT active FROM " + SCHEMA + ".categories WHERE tenant_id = ? AND code = ?")) {
                ps.setObject(1, TENANT_A);
                ps.setString(2, code);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return false;
                    }
                    boolean active = rs.getBoolean(1);
                    c.rollback();
                    return active;
                }
            }
        }
    }

    private boolean categoryExists(String code) throws Exception {
        try (Connection c = ProvisionedTenant.appDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SELECT set_config('app.current_tenant', '" + TENANT_A + "', true)");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT 1 FROM " + SCHEMA + ".categories WHERE tenant_id = ? AND code = ?")) {
                ps.setObject(1, TENANT_A);
                ps.setString(2, code);
                try (ResultSet rs = ps.executeQuery()) {
                    boolean found = rs.next();
                    c.rollback();
                    return found;
                }
            }
        }
    }

    private String orderStatus(String orderId) throws Exception {
        try (Connection c = ProvisionedTenant.appDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SELECT set_config('app.current_tenant', '" + TENANT_A + "', true)");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT status, reverses_order_id FROM " + SCHEMA + ".service_orders"
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, TENANT_A);
                ps.setObject(2, UUID.fromString(orderId));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    String status = rs.getString(1);
                    c.rollback();
                    return status;
                }
            }
        }
    }

    private String orderReverses(String orderId) throws Exception {
        try (Connection c = ProvisionedTenant.appDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SELECT set_config('app.current_tenant', '" + TENANT_A + "', true)");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT reverses_order_id FROM " + SCHEMA + ".service_orders"
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, TENANT_A);
                ps.setObject(2, UUID.fromString(orderId));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    Object reverses = rs.getObject(1);
                    c.rollback();
                    return reverses == null ? null : reverses.toString();
                }
            }
        }
    }

    @Test
    @DisplayName("esquemas listados e modelo gerado do esquema (cabeçalho + exemplo)")
    void schemasAndTemplate() throws Exception {
        mvc.perform(get("/api/v1/registry/imports/schemas").header("Authorization", viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='registry.categories')]").exists())
                .andExpect(jsonPath("$[?(@.code=='registry.subcategories')]").exists());
        mvc.perform(get("/api/v1/registry/imports/schemas/registry.categories/template")
                        .header("Authorization", viewer))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "code,name,abbreviation,minimum_consumption,economies_soft_limit,"
                                + "requires_special_permission,public_entity,active")))
                .andExpect(content().string(containsString("RESIDENCIAL,Residencial,RES,10,5,nao,nao,sim")));
    }

    @Test
    @DisplayName("fluxo completo: valida, recusa por linha, aplica só aceitas por OS de lote, reaplicar é no-op")
    void fullFlowCategories() throws Exception {
        String goodA = uniq("CA");
        String goodB = uniq("CB");
        String existing = uniq("CX");
        insertCategory(existing);
        String csv = "code,name,abbreviation,minimum_consumption,economies_soft_limit,"
                + "requires_special_permission,public_entity,active\n"
                + goodA + ",Categoria A,CA,10,5,nao,nao,sim\n"
                + goodB + ",Categoria B,CB,20,3,nao,nao,sim\n"
                + uniq("BAD") + ",Ruim,XX,dez,5,nao,nao,sim\n"
                + goodA + ",Duplicada no arquivo,CA,10,5,nao,nao,sim\n"
                + existing + ",Duplicada no banco,CX,10,5,nao,nao,sim\n";
        String jobId = upload("registry.categories", "categorias.csv", csv);
        assertThat(jobStatus(jobId)).isEqualTo("RECEIVED");

        runHandler(ImportService.VALIDATE_JOB_TYPE, jobId);
        assertThat(jobStatus(jobId)).isEqualTo("VALIDATED");
        mvc.perform(get("/api/v1/registry/imports/" + jobId).header("Authorization", viewer))
                .andExpect(jsonPath("$.totalRows").value(5))
                .andExpect(jsonPath("$.acceptedCount").value(2))
                .andExpect(jsonPath("$.rejectedCount").value(3));
        mvc.perform(get("/api/v1/registry/imports/" + jobId + "/rejections").header("Authorization", viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.reasonCode=='INVALID_TYPE')]").exists())
                .andExpect(jsonPath("$[?(@.reasonCode=='DUPLICATE_IN_FILE')]").exists())
                .andExpect(jsonPath("$[?(@.reasonCode=='DUPLICATE_IN_DATABASE')]").exists());
        mvc.perform(get("/api/v1/registry/imports/" + jobId + "/rejections.csv").header("Authorization", viewer))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("linha,campo,motivo,mensagem")))
                .andExpect(content().string(containsString("DUPLICATE_IN_FILE")));

        mvc.perform(post("/api/v1/registry/imports/" + jobId + "/apply").header("Authorization", operator))
                .andExpect(status().isAccepted());
        runHandler(ImportService.APPLY_JOB_TYPE, jobId);
        assertThat(jobStatus(jobId)).isEqualTo("APPLIED");
        String batchOrderId = jobField(jobId, "batchOrderId");
        assertThat(batchOrderId).isNotNull();
        assertThat(orderStatus(batchOrderId)).isEqualTo("CLOSED_EXECUTED");
        assertThat(categoryExists(goodA)).isTrue();
        assertThat(categoryExists(goodB)).isTrue();
        assertThat(categoryActive(goodA)).isTrue();

        // Reaplicar: no-op, sem nova OS e sem duplicar.
        runHandler(ImportService.APPLY_JOB_TYPE, jobId);
        assertThat(jobStatus(jobId)).isEqualTo("APPLIED");
        assertThat(jobField(jobId, "batchOrderId")).isEqualTo(batchOrderId);
        mvc.perform(get("/api/v1/registry/imports/" + jobId).header("Authorization", viewer))
                .andExpect(jsonPath("$.appliedCount").value(2));
    }

    @Test
    @DisplayName("reenvio do mesmo arquivo devolve o job existente, sem duplicar")
    void resubmitSameFileReturnsExistingJob() throws Exception {
        String code = uniq("CR");
        String csv = "code,name,abbreviation,minimum_consumption,economies_soft_limit,"
                + "requires_special_permission,public_entity,active\n"
                + code + ",Reenvio,CR,10,5,nao,nao,sim\n";
        String first = upload("registry.categories", "reenvio.csv", csv);
        MvcResult second = mvc.perform(multipart("/api/v1/registry/imports")
                        .file(new MockMultipartFile("file", "reenvio.csv", "text/csv",
                                csv.getBytes(StandardCharsets.UTF_8)))
                        .param("schemaCode", "registry.categories")
                        .header("Authorization", operator))
                .andExpect(status().isCreated())
                .andReturn();
        var body = Json.readTree(second.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(body.get("id").asText()).isEqualTo(first);
        assertThat(body.get("duplicate").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("subcategoria com FK inexistente é recusada; só a aceita é aplicada")
    void subcategoriesForeignKey() throws Exception {
        String category = uniq("CF");
        String csvCategories = "code,name,abbreviation,minimum_consumption,economies_soft_limit,"
                + "requires_special_permission,public_entity,active\n"
                + category + ",Categoria FK,CF,10,5,nao,nao,sim\n";
        String catJob = upload("registry.categories", "fk-cat.csv", csvCategories);
        runHandler(ImportService.VALIDATE_JOB_TYPE, catJob);
        runHandler(ImportService.APPLY_JOB_TYPE, catJob);
        assertThat(jobStatus(catJob)).isEqualTo("APPLIED");

        String goodSub = uniq("SC");
        String csv = "category_code,code,name,abbreviation,inspection_factor,seasonal_supply,active\n"
                + category + "," + goodSub + ",Sub boa,SB,1,nao,sim\n"
                + "MISSING-CODE," + uniq("SM") + ",Sub sem categoria,SM,1,nao,sim\n";
        String jobId = upload("registry.subcategories", "subs.csv", csv);
        runHandler(ImportService.VALIDATE_JOB_TYPE, jobId);
        mvc.perform(get("/api/v1/registry/imports/" + jobId).header("Authorization", viewer))
                .andExpect(jsonPath("$.acceptedCount").value(1))
                .andExpect(jsonPath("$.rejectedCount").value(1));
        mvc.perform(get("/api/v1/registry/imports/" + jobId + "/rejections").header("Authorization", viewer))
                .andExpect(jsonPath("$[?(@.reasonCode=='FOREIGN_KEY_NOT_FOUND')]").exists());
        runHandler(ImportService.APPLY_JOB_TYPE, jobId);
        assertThat(jobStatus(jobId)).isEqualTo("APPLIED");
        try (Connection c = ProvisionedTenant.appDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SELECT set_config('app.current_tenant', '" + TENANT_A + "', true)");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM " + SCHEMA + ".subcategories WHERE tenant_id = ? AND code = ?")) {
                ps.setObject(1, TENANT_A);
                ps.setString(2, goodSub);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getLong(1)).isEqualTo(1L);
                }
            }
            c.rollback();
        }
    }

    @Test
    @DisplayName("aplicar antes de validar é 409; esquema desconhecido é 422")
    void applyBeforeValidationAndUnknownSchema() throws Exception {
        String code = uniq("CV");
        String csv = "code,name,abbreviation,minimum_consumption,economies_soft_limit,"
                + "requires_special_permission,public_entity,active\n"
                + code + ",Tardia,CV,10,5,nao,nao,sim\n";
        String jobId = upload("registry.categories", "tardia.csv", csv);
        mvc.perform(post("/api/v1/registry/imports/" + jobId + "/apply").header("Authorization", operator))
                .andExpect(status().isConflict());
        mvc.perform(multipart("/api/v1/registry/imports")
                        .file(new MockMultipartFile("file", "x.csv", "text/csv",
                                csv.getBytes(StandardCharsets.UTF_8)))
                        .param("schemaCode", "registry.nao-existe")
                        .header("Authorization", operator))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("cabeçalho divergente é recusa de arquivo (linha 0) e nada é aplicado")
    void headerMismatchIsFileRejection() throws Exception {
        String csv = "codigo,nome\n" + uniq("CH") + ",Errado\n";
        String jobId = upload("registry.categories", "cabecalho.csv", csv);
        runHandler(ImportService.VALIDATE_JOB_TYPE, jobId);
        assertThat(jobStatus(jobId)).isEqualTo("VALIDATED");
        mvc.perform(get("/api/v1/registry/imports/" + jobId).header("Authorization", viewer))
                .andExpect(jsonPath("$.acceptedCount").value(0))
                .andExpect(jsonPath("$.rejectedCount").value(1));
        mvc.perform(get("/api/v1/registry/imports/" + jobId + "/rejections").header("Authorization", viewer))
                .andExpect(jsonPath("$[?(@.reasonCode=='HEADER_MISMATCH')]").exists())
                .andExpect(jsonPath("$[?(@.rowNumber==0)]").exists());
        assertThatThrownBy(() -> runHandler(ImportService.APPLY_JOB_TYPE, jobId))
                .hasMessageContaining("Nenhuma linha aceita");
        assertThat(jobStatus(jobId)).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("reversão desativa o que foi inserido e liga a OS inversa à OS do lote")
    void reverseDeactivatesAndLinksInverseOrder() throws Exception {
        String code = uniq("RV");
        String csv = "code,name,abbreviation,minimum_consumption,economies_soft_limit,"
                + "requires_special_permission,public_entity,active\n"
                + code + ",Reverter,RV,10,5,nao,nao,sim\n";
        String jobId = upload("registry.categories", "reverter.csv", csv);
        runHandler(ImportService.VALIDATE_JOB_TYPE, jobId);
        runHandler(ImportService.APPLY_JOB_TYPE, jobId);
        assertThat(jobStatus(jobId)).isEqualTo("APPLIED");
        assertThat(categoryActive(code)).isTrue();
        String batchOrderId = jobField(jobId, "batchOrderId");

        mvc.perform(post("/api/v1/registry/imports/" + jobId + "/reverse").header("Authorization", operator))
                .andExpect(status().isAccepted());
        runHandler(ImportService.REVERSE_JOB_TYPE, jobId);
        assertThat(jobStatus(jobId)).isEqualTo("REVERSED");
        String reversalOrderId = jobField(jobId, "reversalOrderId");
        assertThat(reversalOrderId).isNotNull();
        assertThat(orderStatus(reversalOrderId)).isEqualTo("CLOSED_EXECUTED");
        assertThat(orderReverses(reversalOrderId)).isEqualTo(batchOrderId);
        assertThat(categoryActive(code)).isFalse();

        // Reverter de novo: no-op.
        runHandler(ImportService.REVERSE_JOB_TYPE, jobId);
        assertThat(jobStatus(jobId)).isEqualTo("REVERSED");
        assertThat(jobField(jobId, "reversalOrderId")).isEqualTo(reversalOrderId);
    }
}
