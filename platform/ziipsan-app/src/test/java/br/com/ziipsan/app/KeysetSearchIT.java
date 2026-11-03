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
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0.22: paginação keyset de ponta a ponta na API real, no tenant próprio do teste.
 *
 * <p>Cinco clientes com o mesmo filtro, páginas de 2: 2 + 2 + 1, sem repetição nem
 * falta, em ordem alfabética, e hasNext/nextCursor coerentes. Ordenação diferente
 * de nome crescente dá 400 (o keyset só suporta essa ordenação).
 */
class KeysetSearchIT extends AppIntegrationTestBase {

    private static final UUID TENANT = UUID.randomUUID();
    private static final String SCHEMA = "it_p0r22k_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String TAG = "zzzkeyset" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    private static final List<String> NAMES = List.of(
            "ZzzKeyset Alfa " + TAG, "ZzzKeyset Beta " + TAG, "ZzzKeyset Delta " + TAG,
            "ZzzKeyset Epsilon " + TAG, "ZzzKeyset Gama " + TAG);

    private final String operator = bearer(TestTokens.token(TENANT, "OPERATOR"));

    @BeforeAll
    static void setUp() throws Exception {
        ProvisionedTenant.ensure();
        var result = new TenantProvisioningService(
                ProvisionedTenant.appDs,
                new JdbcTemplate(ProvisionedTenant.appDs),
                Mockito.mock(AuditLogRepository.class),
                ProvisionedTenant.migratorDs)
                .provisionTenant(TenantProvisioningService.TenantProvisioningRequest.builder()
                        .tenantId(TENANT)
                        .schemaName(SCHEMA)
                        .displayName("Tenant keyset P0.22")
                        .cnpj("88777666000155")
                        .email("keyset@teste.com.br")
                        .plan("PRO")
                        .build());
        assertThat(result.success()).as("provisionamento: %s", result.error()).isTrue();
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             Statement st = c.createStatement()) {
            st.execute("SET app.current_tenant = '" + TENANT + "'");
            st.execute("SET search_path TO " + SCHEMA + ", public");
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO customers (id, tenant_id, person_type, name) VALUES (?, ?, 'PF', ?)")) {
                for (String name : NAMES) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, TENANT);
                    ps.setString(3, name);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             Statement st = c.createStatement()) {
            st.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
            // P1-A (integracao): tenant_vertical referencia o catalogo (FK) desde que o
            // provisionamento passou a ativar o pacote sanitation; apagar antes, senao o
            // DELETE do catalogo viola a FK e a linha orfa quebra o migrate posterior.
            st.execute("DELETE FROM platform.tenant_vertical WHERE tenant_id = (SELECT id FROM public.tenant_catalog WHERE schema_name = '" + SCHEMA + "')");
            st.execute("DELETE FROM public.tenant_catalog WHERE schema_name = '" + SCHEMA + "'");
        }
    }

    private MvcResult page(String cursor) throws Exception {
        String url = "/api/v1/registry/customers/search/keyset";
        if (cursor != null) {
            url += "?cursor=" + cursor;
        }
        return mvc.perform(post(url)
                        .header("Authorization", operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"q\": \"" + TAG + "\", \"size\": 2}"))
                .andExpect(status().isOk())
                .andReturn();
    }

    @Test
    @DisplayName("2 + 2 + 1, sem repetição, em ordem, com hasNext/nextCursor coerentes")
    void walksAllPages() throws Exception {
        List<String> seen = new ArrayList<>();
        String cursor = null;
        for (int i = 0; i < 3; i++) {
            MvcResult result = page(cursor);
            String body = result.getResponse().getContentAsString();
            List<String> names = JsonPath.read(body, "$.content[*].name");
            seen.addAll(names);
            boolean hasNext = JsonPath.read(body, "$.hasNext");
            String next = JsonPath.read(body, "$.nextCursor");
            if (i < 2) {
                assertThat(names).hasSize(2);
                assertThat(hasNext).isTrue();
                assertThat(next).isNotBlank();
                cursor = next;
            } else {
                assertThat(names).hasSize(1);
                assertThat(hasNext).isFalse();
                assertThat(next).isNull();
            }
        }
        assertThat(seen).containsExactlyElementsOf(NAMES);
    }

    @Test
    @DisplayName("ordenação diferente de nome crescente no keyset: 400")
    void otherSortIs400() throws Exception {
        mvc.perform(post("/api/v1/registry/customers/search/keyset")
                        .header("Authorization", operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"q\": \"" + TAG + "\", \"size\": 2, \"sort\": \"createdAt,desc\"}"))
                .andExpect(status().isBadRequest());
    }
}
