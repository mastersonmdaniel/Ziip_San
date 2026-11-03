package br.com.ziipsan.app;

import br.com.ziipsan.core.document.DocumentNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Console da plataforma v0 (P1.6) na cadeia real: matriz de suspensão e de
 * eliminação, carência com hold e certificado, auditoria append-only, fonte
 * única do perfil, checklist com percentual e tela de mudança de parâmetro.
 *
 * <p>Isolamento: cada teste de plataforma usa um tenant de catálogo próprio
 * (linha direta no catálogo, sem schema — as tabelas do console não tocam
 * o schema do tenant), criado e apagado no próprio teste. Os testes de
 * parâmetro usam o tenant compartilhado (precisam de um schema real) com
 * vigências em 2031+ para não colidir com nada vigente, e apagam as
 * próprias versões no finally. Códigos de plano e chaves de flag levam
 * sufixo aleatório porque planos e flags são globais e o CI roda surefire
 * e failsafe no mesmo banco.
 */
class PlatformConsoleTest extends AppIntegrationTestBase {

    private static String platformToken(String role) {
        return TestTokens.token(null, List.of(role), b -> b.claim("amr", List.of("otp")));
    }

    private static String tenantToken() {
        // ADMIN + a concessão direta parameters:manage (integracao P1-B):
        // nenhum perfil de fabrica a inclui, como registry:export.
        return TestTokens.token(ProvisionedTenant.TENANT_ID, "ADMIN", "parameters:manage");
    }

    private static String base(UUID tenantId) {
        return "/api/v1/platform/tenants/" + tenantId + "/console";
    }

    /** CNPJ sintético válido (nunca dado real): base aleatória + dígitos do DocumentNumber. */
    private static String syntheticCnpj() {
        String base;
        do {
            base = String.valueOf(100000000000L + (long) (Math.random() * 899999999999L));
        } while (base.chars().distinct().count() == 1);
        return base + DocumentNumber.cnpjCheckDigits(base);
    }

    /** Tenant só de catálogo (sem schema): basta para as tabelas do console. */
    private static UUID newCatalogTenant() {
        UUID id = UUID.randomUUID();
        String schema = "console_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        new JdbcTemplate(ProvisionedTenant.migratorDs).update(
                "INSERT INTO public.tenant_catalog (id, schema_name, name, cnpj, email, status, plan, settings)"
                        + " VALUES (?, ?, ?, ?, ?, 'ACTIVE', 'PRO', '{}')",
                id, schema, "Tenant Console IT", syntheticCnpj(), "console@teste.com.br");
        return id;
    }

    private static void cleanupCatalogTenant(UUID tenantId) {
        JdbcTemplate migrator = new JdbcTemplate(ProvisionedTenant.migratorDs);
        for (String table : List.of("platform.audit_event", "platform.tenant_export",
                "platform.onboarding_step", "platform.tenant_feature_flag",
                "platform.tenant_subscription", "platform.tenant_lifecycle", "platform.tenant_profile")) {
            migrator.update("DELETE FROM " + table + " WHERE tenant_id = ?", tenantId);
        }
        migrator.update("DELETE FROM public.tenant_catalog WHERE id = ?", tenantId);
    }

    private static void cleanupSharedParameters(String... keys) throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             Statement st = c.createStatement()) {
            // Nível de sessão (false), não local à transação: em autocommit,
            // cada statement é uma transação própria e o set_config local
            // morreria antes do DELETE, que apagaria zero linhas em silêncio.
            // A conexão é fechada logo após, então a GUC não vaza.
            st.execute("SELECT set_config('app.current_tenant', '" + ProvisionedTenant.TENANT_ID + "', false)");
            for (String key : keys) {
                st.execute("DELETE FROM " + ProvisionedTenant.SCHEMA + ".parameter_acceptances"
                        + " WHERE tenant_id = '" + ProvisionedTenant.TENANT_ID
                        + "' AND version_id IN (SELECT id FROM " + ProvisionedTenant.SCHEMA
                        + ".parameter_versions WHERE tenant_id = '" + ProvisionedTenant.TENANT_ID
                        + "' AND definition_key = '" + key + "')");
                st.execute("DELETE FROM " + ProvisionedTenant.SCHEMA + ".parameter_versions"
                        + " WHERE tenant_id = '" + ProvisionedTenant.TENANT_ID
                        + "' AND definition_key = '" + key + "'");
            }
        }
    }

    private static String profileJson(String cnpj) {
        return "{\"legalName\":\"Concessionaria Console IT\",\"tradeName\":\"Console IT\","
                + "\"cnpj\":\"" + cnpj + "\",\"legalNature\":\"PRIVATE\","
                + "\"addressCity\":\"Cidade Teste\",\"addressState\":\"SC\",\"municipalityIbge\":\"4205407\","
                + "\"stateRegistration\":\"123456\",\"taxRegime\":\"LUCRO_PRESUMIDO\","
                + "\"dpoName\":\"Encarregado Teste\",\"dpoPublicContact\":\"dpo@teste.com.br\"}";
    }

    private static String noticeJson(String reason, String justification, boolean stepUp, String effectiveAt) {
        return "{\"reason\":\"" + reason + "\",\"justification\":\"" + justification + "\","
                + "\"stepUpConfirmed\":" + stepUp + ",\"effectiveAt\":\"" + effectiveAt + "\"}";
    }

    @Test
    @DisplayName("V9_7: as 5 chaves so.*/sst.* da P1.31 estão no catálogo global; as da ED-20, não")
    void serviceOrderParametersAreRegistered() {
        JdbcTemplate app = new JdbcTemplate(ProvisionedTenant.appDs);
        for (String key : List.of("so.operational_types_enabled", "so.deadline_hours_by_type",
                "sst.required_controls_by_service_type", "sst.min_crew_confined_space",
                "sst.training_validity_months")) {
            Integer n = app.queryForObject(
                    "SELECT count(*) FROM public.parameter_definitions WHERE definition_key = ?",
                    Integer.class, key);
            assertThat(n).as("definição %s registrada pela V9_7", key).isEqualTo(1);
        }
        assertThat(app.queryForObject(
                "SELECT param_class FROM public.parameter_definitions WHERE definition_key = 'sst.min_crew_confined_space'",
                String.class)).isEqualTo("a");
        assertThat(app.queryForObject(
                "SELECT factory_default::text FROM public.parameter_definitions WHERE definition_key = 'sst.min_crew_confined_space'",
                String.class)).isEqualTo("2");
        assertThat(app.queryForObject(
                "SELECT factory_default::text FROM public.parameter_definitions WHERE definition_key = 'sst.training_validity_months'",
                String.class)).isEqualTo("12");
        assertThat(app.queryForObject(
                "SELECT count(*) FROM public.parameter_legal_limits WHERE definition_key = 'sst.min_crew_confined_space' AND limit_kind = 'FLOOR' AND limit_value::text = '2'",
                Integer.class)).isEqualTo(1);
        assertThat(app.queryForObject(
                "SELECT count(*) FROM public.parameter_legal_limits WHERE definition_key = 'sst.training_validity_months' AND limit_kind = 'MAX_VALUE' AND limit_value::text = '12'",
                Integer.class)).isEqualTo(1);
        // ED-20 ainda não existe: a chave dela segue não registrada, por decisão declarada.
        assertThat(app.queryForObject(
                "SELECT count(*) FROM public.parameter_definitions WHERE definition_key = 'so.appointment_window_hours'",
                Integer.class)).isZero();
    }

    @Test
    @DisplayName("perfil é fonte única: PUT/GET no console não tocam a linha do catálogo")
    void profileIsSingleSource() throws Exception {
        UUID tenantId = newCatalogTenant();
        try {
            String cnpj = syntheticCnpj();
            mvc.perform(put(base(tenantId) + "/profile")
                            .header("Authorization", bearer(platformToken("PLATFORM_OWNER")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(profileJson(cnpj)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cnpj").value(cnpj))
                    .andExpect(jsonPath("$.legalNature").value("PRIVATE"));
            mvc.perform(get(base(tenantId) + "/profile")
                            .header("Authorization", bearer(platformToken("PLATFORM_SUPPORT"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.municipalityIbge").value("4205407"))
                    .andExpect(jsonPath("$.dpoPublicContact").value("dpo@teste.com.br"));
            String catalogCnpj = new JdbcTemplate(ProvisionedTenant.migratorDs).queryForObject(
                    "SELECT cnpj FROM public.tenant_catalog WHERE id = ?", String.class, tenantId);
            assertThat(catalogCnpj).isNotEqualTo(cnpj);
            mvc.perform(put(base(tenantId) + "/profile")
                            .header("Authorization", bearer(platformToken("PLATFORM_OWNER")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(profileJson("123")))
                    .andExpect(status().isUnprocessableEntity());
        } finally {
            cleanupCatalogTenant(tenantId);
        }
    }

    @Test
    @DisplayName("planos, cotas e flags: assinar troca o plano ativo (um só ACTIVE) e a flag do tenant vence o padrão")
    void plansAndFlags() throws Exception {
        UUID tenantId = newCatalogTenant();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        try {
            MvcResult planA = mvc.perform(post(base(tenantId) + "/plans")
                            .header("Authorization", bearer(platformToken("PLATFORM_OWNER")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"PLANO-A-" + suffix + "\",\"name\":\"Plano A\",\"quotas\":\"{\\\"max_users\\\":10}\"}"))
                    .andExpect(status().isOk()).andReturn();
            String planAId = com.jayway.jsonpath.JsonPath.read(
                    planA.getResponse().getContentAsString(), "$.id");
            MvcResult planB = mvc.perform(post(base(tenantId) + "/plans")
                            .header("Authorization", bearer(platformToken("PLATFORM_OWNER")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"PLANO-B-" + suffix + "\",\"name\":\"Plano B\",\"quotas\":\"{\\\"max_users\\\":50}\"}"))
                    .andExpect(status().isOk()).andReturn();
            String planBId = com.jayway.jsonpath.JsonPath.read(
                    planB.getResponse().getContentAsString(), "$.id");
            mvc.perform(post(base(tenantId) + "/subscriptions")
                            .header("Authorization", bearer(platformToken("PLATFORM_OWNER")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"planId\":\"" + planAId + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.planCode").value("PLANO-A-" + suffix));
            mvc.perform(post(base(tenantId) + "/subscriptions")
                            .header("Authorization", bearer(platformToken("PLATFORM_OWNER")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"planId\":\"" + planBId + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.planCode").value("PLANO-B-" + suffix));
            Integer active = new JdbcTemplate(ProvisionedTenant.migratorDs).queryForObject(
                    "SELECT count(*) FROM platform.tenant_subscription WHERE tenant_id = ? AND status = 'ACTIVE'",
                    Integer.class, tenantId);
            assertThat(active).isEqualTo(1);

            String flag = "console.it.flag." + suffix;
            mvc.perform(post(base(tenantId) + "/flags")
                            .header("Authorization", bearer(platformToken("PLATFORM_OPS")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"flagKey\":\"" + flag + "\",\"description\":\"Flag de IT\",\"defaultEnabled\":false}"))
                    .andExpect(status().isOk());
            mvc.perform(get(base(tenantId) + "/flags/tenant?flagKey=" + flag)
                            .header("Authorization", bearer(platformToken("PLATFORM_OWNER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(false));
            mvc.perform(post(base(tenantId) + "/flags/tenant")
                            .header("Authorization", bearer(platformToken("PLATFORM_OWNER")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"flagKey\":\"" + flag + "\",\"enabled\":true}"))
                    .andExpect(status().isOk());
            mvc.perform(get(base(tenantId) + "/flags/tenant?flagKey=" + flag)
                            .header("Authorization", bearer(platformToken("PLATFORM_OWNER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(true));
        } finally {
            cleanupCatalogTenant(tenantId);
            new JdbcTemplate(ProvisionedTenant.migratorDs).update(
                    "DELETE FROM platform.plan WHERE code LIKE 'PLANO-%-" + suffix + "'");
            new JdbcTemplate(ProvisionedTenant.migratorDs).update(
                    "DELETE FROM platform.tenant_feature_flag WHERE flag_key = ?", "console.it.flag." + suffix);
            new JdbcTemplate(ProvisionedTenant.migratorDs).update(
                    "DELETE FROM platform.feature_flag WHERE flag_key = ?", "console.it.flag." + suffix);
        }
    }

    @Test
    @DisplayName("onboarding: seed cria 9 passos do manifesto; percentual acompanha a conclusão")
    void onboardingPercent() throws Exception {
        UUID tenantId = newCatalogTenant();
        try {
            mvc.perform(post(base(tenantId) + "/onboarding/seed")
                            .header("Authorization", bearer(platformToken("PLATFORM_OWNER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.inserted").value(9));
            mvc.perform(post(base(tenantId) + "/onboarding/seed")
                            .header("Authorization", bearer(platformToken("PLATFORM_OWNER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.inserted").value(0));
            mvc.perform(get(base(tenantId) + "/onboarding")
                            .header("Authorization", bearer(platformToken("PLATFORM_SUPPORT"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(9))
                    .andExpect(jsonPath("$.percent").value(0));
            for (String step : List.of("parameters_accepted", "import_reconciliation", "fiscal_certificate")) {
                mvc.perform(post(base(tenantId) + "/onboarding/steps/" + step + "/complete")
                                .header("Authorization", bearer(platformToken("PLATFORM_OPS"))))
                        .andExpect(status().isOk());
            }
            mvc.perform(get(base(tenantId) + "/onboarding")
                            .header("Authorization", bearer(platformToken("PLATFORM_OWNER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.done").value(3))
                    .andExpect(jsonPath("$.percent").value(33));
            for (String step : List.of("psp_test", "mfa_policy", "restore_rehearsal",
                    "dpo_informed", "dpa_signed", "training")) {
                mvc.perform(post(base(tenantId) + "/onboarding/steps/" + step + "/complete")
                                .header("Authorization", bearer(platformToken("PLATFORM_OPS"))))
                        .andExpect(status().isOk());
            }
            mvc.perform(get(base(tenantId) + "/onboarding")
                            .header("Authorization", bearer(platformToken("PLATFORM_OWNER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.percent").value(100));
        } finally {
            cleanupCatalogTenant(tenantId);
        }
    }

    @Test
    @DisplayName("suspensão: OWNER e FINANCE por inadimplência passam; FINANCE por outro motivo, OPS e sem step-up recebem 403; ativar antes da vigência é 409")
    void suspensionMatrix() throws Exception {
        UUID tenantId = newCatalogTenant();
        UUID financeTenant = newCatalogTenant();
        String future = Instant.now().plusSeconds(3600).toString();
        try {
            mvc.perform(post(base(tenantId) + "/suspension-notice")
                            .header("Authorization", bearer(platformToken("PLATFORM_OWNER")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(noticeJson("OTHER", "Justificativa de IT", true, future)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUSPENSION_NOTICE"));
            mvc.perform(get(base(tenantId) + "/backoffice-read-only")
                            .header("Authorization", bearer(platformToken("PLATFORM_OWNER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.readOnly").value(false));
            mvc.perform(post(base(tenantId) + "/suspension-activate")
                            .header("Authorization", bearer(platformToken("PLATFORM_OWNER"))))
                    .andExpect(status().isConflict());
            mvc.perform(post(base(tenantId) + "/suspension-notice")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(noticeJson("REGULATORY_ORDER", "Ordem", true, future)))
                    .andExpect(status().isForbidden());
            mvc.perform(post(base(tenantId) + "/suspension-notice")
                            .header("Authorization", bearer(platformToken("PLATFORM_OPS")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(noticeJson("CONTRACT_DEFAULT", "Inadimplência", true, future)))
                    .andExpect(status().isForbidden());
            mvc.perform(post(base(tenantId) + "/suspension-notice")
                            .header("Authorization", bearer(platformToken("PLATFORM_OWNER")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(noticeJson("OTHER", "Sem step-up", false, future)))
                    .andExpect(status().isForbidden());
            mvc.perform(post(base(financeTenant) + "/suspension-notice")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(noticeJson("CONTRACT_DEFAULT", "Inadimplência contratual", true, future)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUSPENSION_NOTICE"));
            mvc.perform(get(base(tenantId) + "/audit")
                            .header("Authorization", bearer(platformToken("PLATFORM_OWNER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].action").value("SUSPENSION_NOTICE_REQUESTED"));
        } finally {
            cleanupCatalogTenant(tenantId);
            cleanupCatalogTenant(financeTenant);
        }
    }

    @Test
    @DisplayName("suspensão vigente: após a vigência do aviso, ativar deixa o backoffice só leitura")
    void suspensionActivation() throws Exception {
        UUID tenantId = newCatalogTenant();
        try {
            String effective = Instant.now().plusSeconds(2).toString();
            mvc.perform(post(base(tenantId) + "/suspension-notice")
                            .header("Authorization", bearer(platformToken("PLATFORM_OWNER")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(noticeJson("CONTRACT_DEFAULT", "Inadimplência", true, effective)))
                    .andExpect(status().isOk());
            Thread.sleep(2500);
            mvc.perform(post(base(tenantId) + "/suspension-activate")
                            .header("Authorization", bearer(platformToken("PLATFORM_OWNER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUSPENDED"));
            mvc.perform(get(base(tenantId) + "/backoffice-read-only")
                            .header("Authorization", bearer(platformToken("PLATFORM_OWNER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.readOnly").value(true));
        } finally {
            cleanupCatalogTenant(tenantId);
        }
    }

    @Test
    @DisplayName("eliminação: só OWNER pede e conclui; FINANCE recebe 403; hold suspende, liberação retoma a carência, conclusão emite certificado")
    void deletionGraceHoldCertificate() throws Exception {
        UUID tenantId = newCatalogTenant();
        try {
            mvc.perform(post(base(tenantId) + "/deletion-request")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"justification\":\"Financeiro tenta eliminar\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(post(base(tenantId) + "/deletion-request")
                            .header("Authorization", bearer(platformToken("PLATFORM_OWNER")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"justification\":\"Encerramento contratual\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DELETION_GRACE"))
                    .andExpect(jsonPath("$.deletionGraceUntil").exists());
            mvc.perform(post(base(tenantId) + "/deletion-complete")
                            .header("Authorization", bearer(platformToken("PLATFORM_OWNER"))))
                    .andExpect(status().isConflict());
            mvc.perform(post(base(tenantId) + "/deletion-hold")
                            .header("Authorization", bearer(platformToken("PLATFORM_OPS")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"holdReason\":\"CONTESTATION\",\"reference\":\"Contestação 123\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DELETION_HELD"));
            mvc.perform(post(base(tenantId) + "/deletion-complete")
                            .header("Authorization", bearer(platformToken("PLATFORM_OWNER"))))
                    .andExpect(status().isConflict());
            mvc.perform(post(base(tenantId) + "/deletion-release")
                            .header("Authorization", bearer(platformToken("PLATFORM_OWNER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DELETION_GRACE"));
            new JdbcTemplate(ProvisionedTenant.migratorDs).update(
                    "UPDATE platform.tenant_lifecycle SET deletion_grace_until = NOW() - INTERVAL '1 day'"
                            + " WHERE tenant_id = ?", tenantId);
            MvcResult done = mvc.perform(post(base(tenantId) + "/deletion-complete")
                            .header("Authorization", bearer(platformToken("PLATFORM_OWNER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DELETED"))
                    .andExpect(jsonPath("$.deletionCertificateHash").exists())
                    .andExpect(jsonPath("$.deletionCertificateIssuedAt").exists())
                    .andReturn();
            String cert = com.jayway.jsonpath.JsonPath.read(
                    done.getResponse().getContentAsString(), "$.deletionCertificateHash");
            assertThat(cert).matches("[0-9a-f]{64}");
            mvc.perform(post(base(tenantId) + "/deletion-complete")
                            .header("Authorization", bearer(platformToken("PLATFORM_OWNER"))))
                    .andExpect(status().isConflict());
        } finally {
            cleanupCatalogTenant(tenantId);
        }
    }

    @Test
    @DisplayName("exportação: só OWNER exporta; o pedido e o hash ficam registrados, e FINANCE recebe 403")
    void exportOwnerOnly() throws Exception {
        UUID tenantId = newCatalogTenant();
        try {
            mvc.perform(post(base(tenantId) + "/export")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE"))))
                    .andExpect(status().isForbidden());
            MvcResult exported = mvc.perform(post(base(tenantId) + "/export")
                            .header("Authorization", bearer(platformToken("PLATFORM_OWNER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contentHash").exists())
                    .andReturn();
            String body = exported.getResponse().getContentAsString();
            String hash = com.jayway.jsonpath.JsonPath.read(body, "$.contentHash");
            assertThat(hash).matches("[0-9a-f]{64}");
            assertThat(body).contains(tenantId.toString());
            Integer rows = new JdbcTemplate(ProvisionedTenant.migratorDs).queryForObject(
                    "SELECT count(*) FROM platform.tenant_export WHERE tenant_id = ? AND content_hash = ?",
                    Integer.class, tenantId, hash);
            assertThat(rows).isEqualTo(1);
        } finally {
            cleanupCatalogTenant(tenantId);
        }
    }

    @Test
    @DisplayName("auditoria é append-only para a aplicação: UPDATE e DELETE em audit_event falham")
    void auditIsAppendOnly() throws Exception {
        UUID tenantId = newCatalogTenant();
        try {
            mvc.perform(put(base(tenantId) + "/profile")
                            .header("Authorization", bearer(platformToken("PLATFORM_OWNER")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(profileJson(syntheticCnpj())))
                    .andExpect(status().isOk());
            JdbcTemplate app = new JdbcTemplate(ProvisionedTenant.appDs);
            assertThatThrownBy(() -> app.update(
                    "UPDATE platform.audit_event SET action = 'TAMPERED' WHERE tenant_id = ?", tenantId))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> app.update(
                    "DELETE FROM platform.audit_event WHERE tenant_id = ?", tenantId))
                    .isInstanceOf(DataAccessException.class);
            Integer rows = app.queryForObject(
                    "SELECT count(*) FROM platform.audit_event WHERE tenant_id = ? AND action = 'PROFILE_UPSERTED'",
                    Integer.class, tenantId);
            assertThat(rows).isEqualTo(1);
        } finally {
            cleanupCatalogTenant(tenantId);
        }
    }

    @Test
    @DisplayName("tela de parâmetro: chave fora do catálogo é 404; travada (classe a) é 422; propor/aprovar por outro/aceitar funciona; alerta de risco da classe b exige reconhecimento")
    void parameterChangeScreen() throws Exception {
        String proposer = tenantToken();
        String approver = tenantToken();
        try {
            mvc.perform(post("/api/v1/tenant/parameters/propose")
                            .header("Authorization", bearer(proposer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"definitionKey\":\"inexistente.xyz\",\"value\":1,"
                                    + "\"validFrom\":\"2031-01-01\",\"validTo\":null}"))
                    .andExpect(status().isNotFound());
            mvc.perform(post("/api/v1/tenant/parameters/propose")
                            .header("Authorization", bearer(proposer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"definitionKey\":\"sst.min_crew_confined_space\",\"value\":1,"
                                    + "\"validFrom\":\"2031-01-01\",\"validTo\":null}"))
                    .andExpect(status().isUnprocessableEntity());
            mvc.perform(post("/api/v1/tenant/parameters/propose")
                            .header("Authorization", bearer(proposer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"definitionKey\":\"sst.min_crew_confined_space\",\"value\":3,"
                                    + "\"validFrom\":\"2031-01-01\",\"validTo\":null}"))
                    .andExpect(status().isUnprocessableEntity());
            MvcResult crew = mvc.perform(post("/api/v1/tenant/parameters/propose")
                            .header("Authorization", bearer(proposer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"definitionKey\":\"sst.min_crew_confined_space\",\"value\":2,"
                                    + "\"validFrom\":\"2031-01-01\",\"validTo\":null}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PROPOSED"))
                    .andReturn();
            String crewId = com.jayway.jsonpath.JsonPath.read(
                    crew.getResponse().getContentAsString(), "$.id");
            mvc.perform(post("/api/v1/tenant/parameters/" + crewId + "/approve")
                            .header("Authorization", bearer(proposer)))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/tenant/parameters/" + crewId + "/approve")
                            .header("Authorization", bearer(approver)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APPROVED"));
            mvc.perform(post("/api/v1/tenant/parameters/" + crewId + "/accept")
                            .header("Authorization", bearer(approver))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"accepterName\":\"Gestora\",\"accepterRole\":\"ADMIN\",\"riskAlertAcknowledged\":false}"))
                    .andExpect(status().isOk());

            MvcResult training = mvc.perform(post("/api/v1/tenant/parameters/propose")
                            .header("Authorization", bearer(proposer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"definitionKey\":\"sst.training_validity_months\",\"value\":6,"
                                    + "\"validFrom\":\"2032-01-01\",\"validTo\":null}"))
                    .andExpect(status().isOk()).andReturn();
            String trainingId = com.jayway.jsonpath.JsonPath.read(
                    training.getResponse().getContentAsString(), "$.id");
            mvc.perform(get("/api/v1/tenant/parameters/sst.training_validity_months/change?proposedVersionId=" + trainingId)
                            .header("Authorization", bearer(proposer)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currentValueJson").value("12"))
                    .andExpect(jsonPath("$.proposedValueJson").value("6"))
                    .andExpect(jsonPath("$.riskAlert").value(false));

            MvcResult snap = mvc.perform(post("/api/v1/tenant/parameters/propose")
                            .header("Authorization", bearer(proposer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"definitionKey\":\"gis.snap_tolerance_m\",\"value\":0.20,"
                                    + "\"validFrom\":\"2033-01-01\",\"validTo\":null}"))
                    .andExpect(status().isOk()).andReturn();
            String snapId = com.jayway.jsonpath.JsonPath.read(
                    snap.getResponse().getContentAsString(), "$.id");
            mvc.perform(post("/api/v1/tenant/parameters/" + snapId + "/approve")
                            .header("Authorization", bearer(approver)))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/v1/tenant/parameters/gis.snap_tolerance_m/change?proposedVersionId=" + snapId)
                            .header("Authorization", bearer(proposer)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.riskAlert").value(true));
            mvc.perform(post("/api/v1/tenant/parameters/" + snapId + "/accept")
                            .header("Authorization", bearer(approver))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"accepterName\":\"Gestora\",\"accepterRole\":\"ADMIN\",\"riskAlertAcknowledged\":false}"))
                    .andExpect(status().isUnprocessableEntity());
            mvc.perform(post("/api/v1/tenant/parameters/" + snapId + "/accept")
                            .header("Authorization", bearer(approver))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"accepterName\":\"Gestora\",\"accepterRole\":\"ADMIN\",\"riskAlertAcknowledged\":true}"))
                    .andExpect(status().isOk());

            MvcResult so = mvc.perform(post("/api/v1/tenant/parameters/propose")
                            .header("Authorization", bearer(proposer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"definitionKey\":\"so.operational_types_enabled\",\"value\":true,"
                                    + "\"validFrom\":\"2034-01-01\",\"validTo\":null}"))
                    .andExpect(status().isOk()).andReturn();
            String soId = com.jayway.jsonpath.JsonPath.read(
                    so.getResponse().getContentAsString(), "$.id");
            mvc.perform(post("/api/v1/tenant/parameters/" + soId + "/approve")
                            .header("Authorization", bearer(approver)))
                    .andExpect(status().isOk());
            mvc.perform(post("/api/v1/tenant/parameters/" + soId + "/accept")
                            .header("Authorization", bearer(approver))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"accepterName\":\"Gestora\",\"accepterRole\":\"ADMIN\",\"riskAlertAcknowledged\":false}"))
                    .andExpect(status().isOk());
        } finally {
            cleanupSharedParameters("sst.min_crew_confined_space", "sst.training_validity_months",
                    "gis.snap_tolerance_m", "so.operational_types_enabled");
        }
    }

    @Test
    @DisplayName("gate de produção: checklist 100% + perfil + versão aprovada sem aceite bloqueia; com aceite, libera")
    void productionGate() throws Exception {
        String proposer = tenantToken();
        String approver = tenantToken();
        JdbcTemplate migrator = new JdbcTemplate(ProvisionedTenant.migratorDs);
        try {
            mvc.perform(post(base(ProvisionedTenant.TENANT_ID) + "/onboarding/seed")
                            .header("Authorization", bearer(platformToken("PLATFORM_OWNER"))))
                    .andExpect(status().isOk());
            for (String step : List.of("parameters_accepted", "import_reconciliation", "fiscal_certificate",
                    "psp_test", "mfa_policy", "restore_rehearsal", "dpo_informed", "dpa_signed", "training")) {
                // Concluir só age se o passo ainda está pendente; 409 (já concluído) é tolerado.
                mvc.perform(post(base(ProvisionedTenant.TENANT_ID) + "/onboarding/steps/" + step + "/complete")
                                .header("Authorization", bearer(platformToken("PLATFORM_OPS"))))
                        .andExpect(result -> {
                            int st = result.getResponse().getStatus();
                            assertThat(st).isIn(200, 409);
                        });
            }
            mvc.perform(put(base(ProvisionedTenant.TENANT_ID) + "/profile")
                            .header("Authorization", bearer(platformToken("PLATFORM_OWNER")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(profileJson(syntheticCnpj())))
                    .andExpect(status().isOk());
            MvcResult snap = mvc.perform(post("/api/v1/tenant/parameters/propose")
                            .header("Authorization", bearer(proposer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"definitionKey\":\"gis.import_snap_tolerance_m\",\"value\":0.60,"
                                    + "\"validFrom\":\"2035-01-01\",\"validTo\":null}"))
                    .andExpect(status().isOk()).andReturn();
            String snapId = com.jayway.jsonpath.JsonPath.read(
                    snap.getResponse().getContentAsString(), "$.id");
            mvc.perform(post("/api/v1/tenant/parameters/" + snapId + "/approve")
                            .header("Authorization", bearer(approver)))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/v1/tenant/parameters/onboarding")
                            .header("Authorization", bearer(proposer)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.panel.percent").value(100))
                    .andExpect(jsonPath("$.productionReady").value(false));
            mvc.perform(post("/api/v1/tenant/parameters/" + snapId + "/accept")
                            .header("Authorization", bearer(approver))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"accepterName\":\"Gestora\",\"accepterRole\":\"ADMIN\",\"riskAlertAcknowledged\":true}"))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/v1/tenant/parameters/onboarding")
                            .header("Authorization", bearer(proposer)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.productionReady").value(true));
        } finally {
            cleanupSharedParameters("gis.import_snap_tolerance_m");
            migrator.update("DELETE FROM platform.onboarding_step WHERE tenant_id = ?", ProvisionedTenant.TENANT_ID);
            migrator.update("DELETE FROM platform.tenant_profile WHERE tenant_id = ?", ProvisionedTenant.TENANT_ID);
            migrator.update("DELETE FROM platform.audit_event WHERE tenant_id = ?", ProvisionedTenant.TENANT_ID);
        }
    }
}
