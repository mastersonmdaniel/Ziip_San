package br.com.ziipsan.app;

import br.com.ziipsan.core.audit.AuditLogRepository;
import br.com.ziipsan.platform.tenant.TenantProvisioningService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0.13: API própria de gestão de usuários por tenant. O tenant vem do token, nunca do
 * corpo; ADMIN do tenant A não altera usuário do tenant B (404, sem vazar existência).
 */
class UserApiTest extends AppIntegrationTestBase {

    private static final UUID TENANT_A = ProvisionedTenant.TENANT_ID;
    private static final UUID TENANT_B = UUID.randomUUID();
    private static final String SCHEMA_B = "it_u_"
            + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    private static final String ADMIN_A = bearer(TestTokens.token(TENANT_A, "ADMIN"));
    private static final String MANAGER_A = bearer(TestTokens.token(TENANT_A, "MANAGER"));
    private static final String OPERATOR_A = bearer(TestTokens.token(TENANT_A, "OPERATOR"));
    private static final String VIEWER_A = bearer(TestTokens.token(TENANT_A, "VIEWER"));
    private static final String ADMIN_B = bearer(TestTokens.token(TENANT_B, "ADMIN"));

    @BeforeAll
    static void setUp() {
        ProvisionedTenant.ensure();
        var result = new TenantProvisioningService(
                ProvisionedTenant.appDs,
                new JdbcTemplate(ProvisionedTenant.appDs),
                Mockito.mock(AuditLogRepository.class),
                ProvisionedTenant.migratorDs)
                .provisionTenant(TenantProvisioningService.TenantProvisioningRequest.builder()
                        .tenantId(TENANT_B)
                        .schemaName(SCHEMA_B)
                        .displayName("Tenant B da API de usuarios")
                        // CNPJ distinto do tenant B do RegistryApiE2ETest (11222333000181):
                        // tenant_catalog.cnpj e UNIQUE e os testes dividem uma JVM/banco no CI.
                        .cnpj("33444555000181")
                        .email("b-users@teste.com.br")
                        .plan("PRO")
                        .build());
        assertThat(result.success()).as("provisionamento do tenant B: %s", result.error()).isTrue();
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection(); Statement st = c.createStatement()) {
            if (SCHEMA_B.startsWith("it_u_")) {
                st.execute("DROP SCHEMA IF EXISTS " + SCHEMA_B + " CASCADE");
                // Apagar a linha do catálogo junto: o DatabaseMigrator do perfil
                // migrate percorre todo tenant ACTIVE e roda Flyway no schema dele;
                // deixar a linha sem o schema quebra teste migrate posterior.
                // P1-A: apagar tenant_vertical antes (FK para o catálogo, desde que o
                // provisionamento ativa o pacote sanitation), senao o DELETE viola a FK.
                st.execute("DELETE FROM public.tenant_vertical WHERE tenant_id = (SELECT id FROM public.tenant_catalog WHERE schema_name = '" + SCHEMA_B + "')");
                st.execute("DELETE FROM public.tenant_catalog WHERE schema_name = '" + SCHEMA_B + "'");
            }
        }
    }

    @Test
    @DisplayName("só ADMIN cria usuário; VIEWER não lista, OPERATOR e MANAGER não criam")
    void onlyAdminWritesUsers() throws Exception {
        mvc.perform(get("/api/v1/users").header("Authorization", VIEWER_A))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/users").header("Authorization", OPERATOR_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keycloakSub\":\"sub-op\",\"displayName\":\"Op\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/users").header("Authorization", MANAGER_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keycloakSub\":\"sub-mg\",\"displayName\":\"Mg\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users").header("Authorization", MANAGER_A))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN cria, lista e lê; sub duplicado é 409; perfil desconhecido é 422")
    void adminCreatesAndReads() throws Exception {
        String sub = "sub-" + UUID.randomUUID();
        MvcResult created = mvc.perform(post("/api/v1/users").header("Authorization", ADMIN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keycloakSub\":\"" + sub + "\",\"displayName\":\"Ana Gestora\","
                                + "\"email\":\"ana@teste.com.br\",\"profiles\":[\"OPERATOR\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.keycloakSub").value(sub))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.profiles[0]").value("OPERATOR"))
                .andReturn();
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mvc.perform(post("/api/v1/users").header("Authorization", ADMIN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keycloakSub\":\"" + sub + "\",\"displayName\":\"Outra\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/users").header("Authorization", ADMIN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keycloakSub\":\"sub-x\",\"displayName\":\"X\",\"profiles\":[\"SUPERUSER\"]}"))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(get("/api/v1/users/" + id).header("Authorization", ADMIN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Ana Gestora"));
        mvc.perform(get("/api/v1/users").header("Authorization", ADMIN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.keycloakSub == '" + sub + "')]").exists());
    }

    @Test
    @DisplayName("ADMIN atualiza status e perfis; ADMIN de outro tenant recebe 404, sem vazar")
    void adminUpdatesAndCrossTenantIs404() throws Exception {
        String sub = "sub-" + UUID.randomUUID();
        MvcResult created = mvc.perform(post("/api/v1/users").header("Authorization", ADMIN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keycloakSub\":\"" + sub + "\",\"displayName\":\"Beto\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        // P1.11: PUT exige If-Match com o ETag do GET.
        String etag = mvc.perform(get("/api/v1/users/" + id).header("Authorization", ADMIN_A))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");
        mvc.perform(put("/api/v1/users/" + id).header("Authorization", ADMIN_A)
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Beto Suspenso\",\"status\":\"SUSPENDED\",\"profiles\":[\"VIEWER\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.profiles[0]").value("VIEWER"));

        mvc.perform(get("/api/v1/users/" + id).header("Authorization", ADMIN_B))
                .andExpect(status().isNotFound());
        // P1.11: com If-Match: * a pré-condição passa e o 404 de outro tenant se mantém.
        mvc.perform(put("/api/v1/users/" + id).header("Authorization", ADMIN_B)
                        .header("If-Match", "*")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"X\",\"status\":\"ACTIVE\",\"profiles\":[]}"))
                .andExpect(status().isNotFound());
    }
}
