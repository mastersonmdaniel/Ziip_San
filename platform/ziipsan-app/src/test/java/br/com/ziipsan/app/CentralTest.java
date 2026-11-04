package br.com.ziipsan.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Connection;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Central Ziip v0 (P1.17, core V9_1) na cadeia real: gate de MFA e de
 * token de tenant, matriz central:read/central:manage, coletor com trava
 * e watchdog, alerta que abre e fecha sem duplicar, fronteira da role
 * `ziipsan_central` e contrato da API de metricas v1.
 *
 * <p>Chaves de regra com sufixo aleatorio: regras sao globais e o CI
 * roda surefire e failsafe no mesmo banco. A limpeza apaga regra,
 * eventos e pontos de teste no finally; pontos de coleta do tenant
 * compartilhado nao sao apagados (serie temporal, sem dono).
 */
class CentralTest extends AppIntegrationTestBase {

    private static String platformToken(String role) {
        return TestTokens.token(null, List.of(role), b -> b.claim("amr", List.of("otp")));
    }

    private static String platformTokenNoMfa(String role) {
        return TestTokens.token(null, List.of(role), b -> {
        });
    }

    private static String tenantToken() {
        // Com amr: sem ele, o gate de MFA responderia mfa-required antes
        // do PlatformAccessFilter (ordem provada em MfaGateIntegrationTest).
        return TestTokens.token(ProvisionedTenant.TENANT_ID, List.of("ADMIN"),
                b -> b.claim("amr", List.of("otp")));
    }

    private static String ruleKey() {
        return "central.it." + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static void cleanupRule(String key) {
        JdbcTemplate migrator = new JdbcTemplate(ProvisionedTenant.migratorDs);
        migrator.update("DELETE FROM platform.alert_event WHERE rule_id IN"
                + " (SELECT id FROM platform.alert_rule WHERE rule_key = ?)", key);
        migrator.update("DELETE FROM platform.alert_rule WHERE rule_key = ?", key);
    }

    private int openCountForRule(String key) throws Exception {
        String body = getBody("/api/v1/platform/metrics/alerts?state=open", "PLATFORM_OPS");
        int count = 0;
        int idx = 0;
        while ((idx = body.indexOf("\"ruleKey\":\"" + key + "\"", idx)) != -1) {
            count++;
            idx++;
        }
        return count;
    }

    private String getBody(String path, String role) throws Exception {
        MvcResult result = mvc.perform(get(path)
                        .header("Authorization", "Bearer " + platformToken(role)))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    @Test
    @DisplayName("rota de plataforma sem amr -> 403 mfa-required; token de tenant -> 403 tenant-not-allowed")
    void platformGates() throws Exception {
        mvc.perform(get("/api/v1/platform/metrics/catalog")
                        .header("Authorization", "Bearer " + platformTokenNoMfa("PLATFORM_OPS")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:mfa-required"));
        mvc.perform(get("/api/v1/platform/metrics/catalog")
                        .header("Authorization", "Bearer " + tenantToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:tenant-not-allowed"));
    }

    @Test
    @DisplayName("SUPPORT le a Central, mas nao coleta: central:manage e so OWNER/OPS")
    void centralReadManageMatrix() throws Exception {
        mvc.perform(get("/api/v1/platform/metrics/catalog")
                        .header("Authorization", "Bearer " + platformToken("PLATFORM_SUPPORT")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/platform/central/collect")
                        .header("Authorization", "Bearer " + platformToken("PLATFORM_SUPPORT")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/platform/central/overview")
                        .header("Authorization", "Bearer " + platformToken("PLATFORM_FINANCE")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("coleta escreve pontos; catalogo, snapshot, series e health-scores seguem o contrato v1")
    void collectAndMetricsContract() throws Exception {
        mvc.perform(post("/api/v1/platform/central/collect")
                        .header("Authorization", "Bearer " + platformToken("PLATFORM_OPS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("COLLECTED"));
        mvc.perform(get("/api/v1/platform/metrics/catalog")
                        .header("Authorization", "Bearer " + platformToken("PLATFORM_OPS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value("1"))
                .andExpect(jsonPath("$.product").value("ziip-san"))
                .andExpect(jsonPath("$.definitions").isArray());
        assertThat(getBody("/api/v1/platform/metrics/catalog", "PLATFORM_OPS"))
                .contains("\"key\":\"platform.outbox.pending\",\"unit\":\"count\"");
        assertThat(getBody("/api/v1/platform/metrics/snapshot", "PLATFORM_OPS"))
                .contains("\"key\":\"platform.tenants.total\"");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        mvc.perform(get("/api/v1/platform/metrics/series")
                        .param("key", "platform.tenants.total")
                        .param("from", now.minusHours(1).toString())
                        .param("to", now.plusHours(1).toString())
                        .param("step", "5m")
                        .header("Authorization", "Bearer " + platformToken("PLATFORM_OPS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points[0].key").value("platform.tenants.total"));
        mvc.perform(get("/api/v1/platform/metrics/series")
                        .param("key", "metrica.inexistente")
                        .param("from", now.minusHours(1).toString())
                        .param("to", now.plusHours(1).toString())
                        .header("Authorization", "Bearer " + platformToken("PLATFORM_OPS")))
                .andExpect(status().isNotFound());
        assertThat(getBody("/api/v1/platform/metrics/health-scores", "PLATFORM_OPS"))
                .as("health-scores incluem a entrada global (tenantRef nulo) com fatores nao medidos")
                .contains("\"tenantRef\":null")
                .contains("\"unmeasured\"");
        mvc.perform(get("/api/v1/platform/central/overview")
                        .header("Authorization", "Bearer " + platformToken("PLATFORM_OPS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayFocus").exists())
                .andExpect(jsonPath("$.watchdog.stale").value(false));
        mvc.perform(get("/api/v1/platform/central/platform")
                        .header("Authorization", "Bearer " + platformToken("PLATFORM_OPS")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/platform/central/security")
                        .header("Authorization", "Bearer " + platformToken("PLATFORM_OPS")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/platform/central/alerts")
                        .header("Authorization", "Bearer " + platformToken("PLATFORM_OPS")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("trava: com o advisory lock retido, a coleta devolve SKIPPED_LOCK sem escrever")
    void collectorLock() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            st.execute("SELECT pg_advisory_xact_lock(8100170017)");
            mvc.perform(post("/api/v1/platform/central/collect")
                            .header("Authorization", "Bearer " + platformToken("PLATFORM_OPS")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.outcome").value("SKIPPED_LOCK"));
            c.rollback();
        }
        mvc.perform(post("/api/v1/platform/central/collect")
                        .header("Authorization", "Bearer " + platformToken("PLATFORM_OPS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("COLLECTED"));
    }

    @Test
    @DisplayName("watchdog: marcador velho -> stale; coleta com gap abre o alerta, a proxima saudavel o fecha")
    void watchdogGapLifecycle() throws Exception {
        JdbcTemplate migrator = new JdbcTemplate(ProvisionedTenant.migratorDs);
        OffsetDateTime old = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        migrator.update("UPDATE platform.config_item SET item_value = ?::JSONB WHERE item_key = 'central.collector.last_run_at'",
                "{\"collected\": true, \"at\": \"" + old + "\"}");
        try {
            mvc.perform(get("/api/v1/platform/central/watchdog")
                            .header("Authorization", "Bearer " + platformToken("PLATFORM_OPS")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stale").value(true));
            mvc.perform(post("/api/v1/platform/central/collect")
                            .header("Authorization", "Bearer " + platformToken("PLATFORM_OPS")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.staleGapDetected").value(true));
            assertThat(getBody("/api/v1/platform/metrics/alerts?state=open", "PLATFORM_OPS"))
                    .contains("\"ruleKey\":\"central.collector.stale\"");
            mvc.perform(post("/api/v1/platform/central/collect")
                            .header("Authorization", "Bearer " + platformToken("PLATFORM_OPS")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.staleGapDetected").value(false));
            assertThat(getBody("/api/v1/platform/metrics/alerts?state=open", "PLATFORM_OPS"))
                    .doesNotContain("\"ruleKey\":\"central.collector.stale\"");
        } finally {
            migrator.update("DELETE FROM platform.alert_event WHERE rule_id IN"
                    + " (SELECT id FROM platform.alert_rule WHERE rule_key = 'central.collector.stale')");
        }
    }

    @Test
    @DisplayName("alerta abre, nao duplica na segunda coleta e fecha quando a condicao limpa")
    void alertOpensOnceAndCloses() throws Exception {
        String key = ruleKey();
        String ruleJson = "{\"ruleKey\":\"" + key + "\",\"metricKey\":\"platform.outbox.dead\","
                + "\"comparator\":\">\",\"threshold\":-1,\"consecutiveReadings\":1,"
                + "\"severity\":\"WARNING\",\"scope\":\"GLOBAL\",\"owner\":\"platform\"}";
        try {
            mvc.perform(post("/api/v1/platform/central/rules")
                            .contentType(MediaType.APPLICATION_JSON).content(ruleJson)
                            .header("Authorization", "Bearer " + platformToken("PLATFORM_OPS")))
                    .andExpect(status().isOk());
            mvc.perform(post("/api/v1/platform/central/collect")
                            .header("Authorization", "Bearer " + platformToken("PLATFORM_OPS")))
                    .andExpect(status().isOk());
            assertThat(openCountForRule(key)).isEqualTo(1);
            mvc.perform(post("/api/v1/platform/central/collect")
                            .header("Authorization", "Bearer " + platformToken("PLATFORM_OPS")))
                    .andExpect(status().isOk());
            assertThat(openCountForRule(key)).as("segunda coleta nao duplica o alerta aberto").isEqualTo(1);
            // Condicao limpa: limiar impossivel; a proxima coleta resolve.
            new JdbcTemplate(ProvisionedTenant.migratorDs).update(
                    "UPDATE platform.alert_rule SET threshold = 999999 WHERE rule_key = ?", key);
            mvc.perform(post("/api/v1/platform/central/collect")
                            .header("Authorization", "Bearer " + platformToken("PLATFORM_OPS")))
                    .andExpect(status().isOk());
            assertThat(openCountForRule(key)).isEqualTo(0);
            assertThat(getBody("/api/v1/platform/metrics/alerts?state=resolved", "PLATFORM_OPS"))
                    .contains("\"ruleKey\":\"" + key + "\"");
        } finally {
            cleanupRule(key);
        }
    }

    @Test
    @DisplayName("regra com corpo invalido -> 422 (@NotBlank); reconhecer alerta fechado -> 404")
    void ruleValidationAndAcknowledge() throws Exception {
        String badJson = "{\"ruleKey\":\"\",\"metricKey\":\"platform.outbox.dead\","
                + "\"comparator\":\">\",\"threshold\":1,\"consecutiveReadings\":1,"
                + "\"severity\":\"WARNING\",\"scope\":\"GLOBAL\",\"owner\":\"platform\"}";
        mvc.perform(post("/api/v1/platform/central/rules")
                        .contentType(MediaType.APPLICATION_JSON).content(badJson)
                        .header("Authorization", "Bearer " + platformToken("PLATFORM_OPS")))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/platform/central/alerts/" + UUID.randomUUID() + "/acknowledge")
                        .header("Authorization", "Bearer " + platformToken("PLATFORM_OPS")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("fronteira: ziipsan_central nao le tabela de tenant nem perfil do console; so executa platform_stats.*")
    void centralRoleBoundary() {
        JdbcTemplate migrator = new JdbcTemplate(ProvisionedTenant.migratorDs);
        String tenantTable = "\"" + ProvisionedTenant.SCHEMA + "\".users";
        Boolean tenantSelect = migrator.queryForObject(
                "SELECT has_table_privilege('ziipsan_central', ?, 'SELECT')", Boolean.class, tenantTable);
        assertThat(tenantSelect).as("central nao pode ler tabela de tenant").isFalse();
        Boolean profileSelect = migrator.queryForObject(
                "SELECT has_table_privilege('ziipsan_central', 'platform.tenant_profile', 'SELECT')", Boolean.class);
        assertThat(profileSelect).as("central nao pode ler perfil do console (tem CNPJ)").isFalse();
        Boolean pointSelect = migrator.queryForObject(
                "SELECT has_table_privilege('ziipsan_central', 'platform.metric_point', 'SELECT')", Boolean.class);
        assertThat(pointSelect).as("central le os pontos da Central").isTrue();
        for (String fn : List.of("platform_stats.tenant_outbox_counts()",
                "platform_stats.tenant_counts()", "platform_stats.open_alert_counts()",
                "platform_stats.audit_action_counts(TIMESTAMPTZ)", "platform_stats.onboarding_counts()")) {
            Boolean exec = migrator.queryForObject(
                    "SELECT has_function_privilege('ziipsan_central', ?, 'EXECUTE')", Boolean.class, fn);
            assertThat(exec).as("central executa " + fn).isTrue();
        }
        // Colunas de retorno das funcoes: so tenant_id, contagens e idades.
        Set<String> personal = Set.of("name", "cnpj", "email", "phone", "document", "cpf", "address", "payload");
        List<Map<String, Object>> rows = migrator.queryForList("SELECT * FROM platform_stats.tenant_outbox_counts()");
        if (!rows.isEmpty()) {
            assertThat(rows.get(0).keySet()).doesNotContainAnyElementsOf(personal);
        }
        List<String> argNames = migrator.queryForList(
                "SELECT unnest(proargnames) FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace"
                        + " WHERE n.nspname = 'platform_stats'", String.class);
        assertThat(argNames).doesNotContainAnyElementsOf(personal);
        // O corpo das funcoes nao referencia coluna pessoal de tenant.
        String bodies = String.join(" ", migrator.queryForList(
                "SELECT prosrc FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace"
                        + " WHERE n.nspname = 'platform_stats'", String.class));
        assertThat(bodies).doesNotContain("cnpj").doesNotContain("payload");
    }
}
