package br.com.ziipsan.app;

import br.com.ziipsan.core.document.DocumentNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobranca do SaaS por contrato (P2.12, ADR-0019, core V10_1) na cadeia
 * real: contrato com aditivo, empenho, reajuste so com apostilamento,
 * faturas e pagamentos conciliados, alertas do item 7, restricao manual
 * com fatura vencida e aviso (e recusada sem eles), prova de que
 * nenhum caminho automatico bloqueia o tenant, uso mensal sem dado
 * pessoal, fotografia com hash, relatorio com janela de contestacao e
 * custo do provedor com margem na aba Contratos.
 *
 * <p>Isolamento: cada teste usa um tenant so de catalogo (sem schema),
 * criado e apagado no proprio teste, com numeros de contrato com
 * sufixo aleatorio (numero de contrato e globalmente unico). Datas
 * relativas ao dia real (UTC): fatura vencida vence ontem, contrato a
 * vencer vence em 20 dias (janela CRITICAL de 30 dias).
 */
class SaaSContractIT extends AppIntegrationTestBase {

    private static String platformToken(String role) {
        return TestTokens.token(null, List.of(role), b -> b.claim("amr", List.of("otp")));
    }

    private static String tenantToken() {
        return TestTokens.token(ProvisionedTenant.TENANT_ID, List.of("ADMIN"),
                b -> b.claim("amr", List.of("otp")));
    }

    private static String syntheticCnpj() {
        String base;
        do {
            base = String.valueOf(100000000000L + (long) (Math.random() * 899999999999L));
        } while (base.chars().distinct().count() == 1);
        return base + DocumentNumber.cnpjCheckDigits(base);
    }

    private static UUID newCatalogTenant() {
        UUID id = UUID.randomUUID();
        String schema = "saas_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        new JdbcTemplate(ProvisionedTenant.migratorDs).update(
                "INSERT INTO public.tenant_catalog (id, schema_name, name, cnpj, email, status, plan, settings)"
                        + " VALUES (?, ?, ?, ?, ?, 'ACTIVE', 'PRO', '{}')",
                id, schema, "Tenant SaaS IT", syntheticCnpj(), "saas@teste.com.br");
        return id;
    }

    private static void cleanupTenant(UUID tenantId) {
        JdbcTemplate migrator = new JdbcTemplate(ProvisionedTenant.migratorDs);
        List<UUID> contractIds = migrator.query(
                "SELECT id FROM platform.saas_contract WHERE tenant_id = ?",
                (rs, i) -> rs.getObject(1, UUID.class), tenantId);
        for (UUID contractId : contractIds) {
            // Notices primeiro: referenciam fatura e contrato.
            migrator.update("DELETE FROM platform.saas_restriction_notice WHERE contract_id = ?", contractId);
            migrator.update("DELETE FROM platform.saas_payment WHERE invoice_id IN"
                    + " (SELECT id FROM platform.saas_invoice WHERE contract_id = ?)", contractId);
            migrator.update("DELETE FROM platform.saas_invoice WHERE contract_id = ?", contractId);
            migrator.update("DELETE FROM platform.saas_readjustment WHERE contract_id = ?", contractId);
            migrator.update("DELETE FROM platform.saas_contract_addendum WHERE contract_id = ?", contractId);
            migrator.update("DELETE FROM platform.saas_commitment WHERE contract_id = ?", contractId);
        }
        migrator.update("DELETE FROM platform.saas_payment WHERE tenant_id = ?", tenantId);
        migrator.update("DELETE FROM platform.usage_statement WHERE tenant_id = ?", tenantId);
        migrator.update("DELETE FROM platform.usage_connection_snapshot WHERE tenant_id = ?", tenantId);
        migrator.update("DELETE FROM platform.usage_ledger WHERE tenant_id = ?", tenantId);
        migrator.update("DELETE FROM platform.provider_cost_allocation WHERE tenant_id = ?", tenantId);
        migrator.update("DELETE FROM platform.saas_contract WHERE tenant_id = ?", tenantId);
        migrator.update("DELETE FROM platform.alert_event WHERE tenant_id = ?", tenantId);
        migrator.update("DELETE FROM platform.audit_event WHERE tenant_id = ?", tenantId);
        migrator.update("DELETE FROM platform.tenant_lifecycle WHERE tenant_id = ?", tenantId);
        migrator.update("DELETE FROM public.tenant_catalog WHERE id = ?", tenantId);
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private static String contractJson(String number, LocalDate startsAt, LocalDate endsAt,
                                       String monthlyValue, String quotasJson) {
        // quotasJson e uma String no DTO: vai como string JSON escapada,
        // nao como objeto cru (objeto cru falha na desserializacao com
        // MALFORMED_BODY/422 no tratador global).
        String escaped = quotasJson.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"contractNumber\":\"" + number + "\",\"biddingProcess\":\"PROC-2026-001\","
                + "\"biddingModality\":\"PREGAO\",\"contractorCnpj\":\"" + syntheticCnpj() + "\","
                + "\"startsAt\":\"" + startsAt + "\",\"endsAt\":\"" + endsAt + "\","
                + "\"monthlyValue\":" + monthlyValue + ",\"indexKey\":\"IPCA\","
                + "\"indexBaseDate\":\"" + startsAt + "\",\"slaPolicyKey\":null,"
                + "\"quotasJson\":\"" + escaped + "\"}";
    }

    private static String idOf(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        int start = body.indexOf("\"id\":\"") + 6;
        int end = body.indexOf("\"", start);
        return body.substring(start, end);
    }

    private String createContract(UUID tenantId, String number, String monthlyValue,
                                  String quotasJson) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/platform/contracts?tenantId=" + tenantId)
                        .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contractJson(number, today().minusDays(30), today().plusDays(20),
                                monthlyValue, quotasJson)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        return idOf(result);
    }

    private void activateContract(String contractId) throws Exception {
        mvc.perform(post("/api/v1/platform/contracts/" + contractId + "/activate")
                        .header("Authorization", bearer(platformToken("PLATFORM_FINANCE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    private String issueInvoice(String contractId, String commitmentId, String competence,
                                String total, LocalDate dueDate) throws Exception {
        String commitment = commitmentId == null ? "null" : "\"" + commitmentId + "\"";
        MvcResult result = mvc.perform(post("/api/v1/platform/contracts/" + contractId + "/invoices")
                        .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commitmentId\":" + commitment + ",\"competence\":\"" + competence
                                + "\",\"total\":" + total + ",\"dueDate\":\"" + dueDate + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn();
        return idOf(result);
    }

    @Test
    @DisplayName("parametro usage.statement_contest_days: classe d, padrao de fabrica 30")
    void contestDaysParameterIsRegistered() {
        JdbcTemplate app = new JdbcTemplate(ProvisionedTenant.appDs);
        assertThat(app.queryForObject(
                "SELECT param_class FROM public.parameter_definitions"
                        + " WHERE definition_key = 'usage.statement_contest_days'",
                String.class)).isEqualTo("d");
        assertThat(app.queryForObject(
                "SELECT factory_default::text FROM public.parameter_definitions"
                        + " WHERE definition_key = 'usage.statement_contest_days'",
                String.class)).isEqualTo("30");
    }

    @Test
    @DisplayName("regras de alerta do item 7 da ADR-0019 semeadas na V10_1")
    void alertRulesSeeded() {
        JdbcTemplate app = new JdbcTemplate(ProvisionedTenant.appDs);
        assertThat(app.queryForObject(
                "SELECT count(*) FROM platform.alert_rule WHERE rule_key LIKE 'saas.%'",
                Integer.class)).isEqualTo(6);
        assertThat(app.queryForObject(
                "SELECT count(*) FROM platform.metric_definition WHERE metric_key LIKE 'saas.%'",
                Integer.class)).isEqualTo(5);
    }

    @Test
    @DisplayName("contrato, aditivo, empenho, fatura com abatimento e conciliacao parcial e total")
    void contractInvoiceAndReconciliation() throws Exception {
        UUID tenantId = newCatalogTenant();
        try {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            String contractId = createContract(tenantId, "CTR-" + suffix, "10000", "{}");
            activateContract(contractId);
            MvcResult commitment = mvc.perform(post("/api/v1/platform/contracts/" + contractId + "/commitments")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"commitmentNumber\":\"EMP-" + suffix + "\",\"amount\":50000,"
                                    + "\"issuedAt\":\"" + today().minusDays(30) + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(50000))
                    .andReturn();
            String commitmentId = idOf(commitment);
            mvc.perform(post("/api/v1/platform/contracts/" + contractId + "/addenda")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"addendumNumber\":\"ADT-" + suffix + "\",\"kind\":\"TERM\","
                                    + "\"description\":\"Prorrogacao de prazo\",\"effectiveFrom\":\""
                                    + today().plusDays(20) + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.kind").value("TERM"));
            String competence = String.format("%04d-%02d", today().getYear(), today().getMonthValue());
            String invoiceId = issueInvoice(contractId, commitmentId, competence, "10000", today().plusDays(10));
            JdbcTemplate migrator = new JdbcTemplate(ProvisionedTenant.migratorDs);
            assertThat(migrator.queryForObject(
                    "SELECT balance FROM platform.saas_commitment WHERE id = ?",
                    java.math.BigDecimal.class, UUID.fromString(commitmentId)))
                    .isEqualByComparingTo("40000");
            mvc.perform(post("/api/v1/platform/contracts/payments")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"tenantId\":\"" + tenantId + "\",\"invoiceId\":\"" + invoiceId
                                    + "\",\"amount\":4000,\"paidAt\":\"" + today() + "\","
                                    + "\"bankOrderRef\":\"OB-1\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.matched").value(true));
            mvc.perform(get("/api/v1/platform/contracts/invoices/" + invoiceId)
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
                    .andExpect(jsonPath("$.paidAmount").value(4000));
            mvc.perform(post("/api/v1/platform/contracts/payments")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"tenantId\":\"" + tenantId + "\",\"invoiceId\":\"" + invoiceId
                                    + "\",\"amount\":6000,\"paidAt\":\"" + today() + "\","
                                    + "\"bankOrderRef\":\"OB-2\"}"))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/v1/platform/contracts/invoices/" + invoiceId)
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PAID"))
                    .andExpect(jsonPath("$.overdue").value(false));
        } finally {
            cleanupTenant(tenantId);
        }
    }

    @Test
    @DisplayName("reajuste fica proposta sem apostilamento e aplica com aditivo, atualizando o mensal")
    void readjustmentNeedsAddendum() throws Exception {
        UUID tenantId = newCatalogTenant();
        try {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            String contractId = createContract(tenantId, "CTR-" + suffix, "10000", "{}");
            activateContract(contractId);
            MvcResult proposed = mvc.perform(post("/api/v1/platform/contracts/" + contractId + "/readjustments")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"indexKey\":\"IPCA\",\"indexValuePct\":4.5,"
                                    + "\"proposedMonthlyValue\":10450}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PROPOSED"))
                    .andExpect(jsonPath("$.baseMonthlyValue").value(10000))
                    .andReturn();
            String readjustmentId = idOf(proposed);
            mvc.perform(post("/api/v1/platform/contracts/readjustments/" + readjustmentId + "/apply")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"addendumId\":null}"))
                    .andExpect(status().is4xxClientError());
            MvcResult addendum = mvc.perform(post("/api/v1/platform/contracts/" + contractId + "/addenda")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"addendumNumber\":\"ADT-" + suffix + "\",\"kind\":\"READJUSTMENT\","
                                    + "\"description\":\"Apostilamento de reajuste\",\"effectiveFrom\":\""
                                    + today() + "\"}"))
                    .andExpect(status().isOk())
                    .andReturn();
            mvc.perform(post("/api/v1/platform/contracts/readjustments/" + readjustmentId + "/apply")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"addendumId\":\"" + idOf(addendum) + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APPLIED"));
            mvc.perform(get("/api/v1/platform/contracts/" + contractId)
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.monthlyValue").value(10450));
        } finally {
            cleanupTenant(tenantId);
        }
    }

    @Test
    @DisplayName("nenhum caminho automatico bloqueia o tenant: alertas abrem e o ciclo de vida fica intacto")
    void noAutomaticBlocking() throws Exception {
        UUID tenantId = newCatalogTenant();
        try {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            String contractId = createContract(tenantId, "CTR-" + suffix, "10000",
                    "{\"quota.active_connections\":100}");
            activateContract(contractId);
            String competence = String.format("%04d-%02d", today().getYear(), today().getMonthValue());
            issueInvoice(contractId, null, competence, "10000", today().minusDays(1));
            mvc.perform(post("/api/v1/platform/usage/snapshots")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"tenantId\":\"" + tenantId + "\",\"periodMonth\":\""
                                    + today().withDayOfMonth(1) + "\",\"activeConnections\":100}"))
                    .andExpect(status().isOk());
            mvc.perform(post("/api/v1/platform/contracts/payments")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"tenantId\":\"" + tenantId + "\",\"invoiceId\":null,"
                                    + "\"amount\":500,\"paidAt\":\"" + today() + "\",\"bankOrderRef\":\"OB-X\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.matched").value(false));
            mvc.perform(post("/api/v1/platform/contracts/alerts/evaluate")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tenantsWithAlerts").value(1));
            JdbcTemplate migrator = new JdbcTemplate(ProvisionedTenant.migratorDs);
            assertThat(migrator.queryForObject(
                    "SELECT count(*) FROM platform.alert_event e JOIN platform.alert_rule r ON r.id = e.rule_id"
                            + " WHERE e.tenant_id = ? AND e.resolved_at IS NULL AND r.rule_key LIKE 'saas.%'",
                    Integer.class, tenantId)).isGreaterThanOrEqualTo(4);
            for (String ruleKey : List.of("saas.contract.expiring", "saas.contract.quota_80",
                    "saas.contract.quota_100", "saas.invoice.overdue", "saas.payment.unmatched")) {
                assertThat(migrator.queryForObject(
                        "SELECT count(*) FROM platform.alert_event e JOIN platform.alert_rule r"
                                + " ON r.id = e.rule_id WHERE e.tenant_id = ? AND r.rule_key = ?"
                                + " AND e.resolved_at IS NULL",
                        Integer.class, tenantId, ruleKey)).as("alerta %s aberto", ruleKey).isEqualTo(1);
            }
            assertThat(migrator.queryForObject(
                    "SELECT count(*) FROM platform.tenant_lifecycle WHERE tenant_id = ?",
                    Integer.class, tenantId)).as("nenhuma escrita no ciclo de vida").isZero();
        } finally {
            cleanupTenant(tenantId);
        }
    }

    @Test
    @DisplayName("restricao recusada sem fatura vencida, sem step-up e por papel sem permissao; com elos, ativa apos o aviso")
    void restrictionNeedsOverdueInvoiceNoticeAndStepUp() throws Exception {
        UUID tenantId = newCatalogTenant();
        try {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            String contractId = createContract(tenantId, "CTR-" + suffix, "10000", "{}");
            activateContract(contractId);
            String competence = String.format("%04d-%02d", today().getYear(), today().getMonthValue());
            String openInvoiceId = issueInvoice(contractId, null, competence, "10000", today().plusDays(10));
            String overdueInvoiceId = issueInvoice(contractId, null, competence, "5000", today().minusDays(1));
            String futureEffective = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1).toString();
            String noticeBody = "{\"tenantId\":\"" + tenantId + "\",\"contractId\":\"" + contractId
                    + "\",\"invoiceId\":\"" + overdueInvoiceId + "\",\"justification\":\"Inadimplencia\","
                    + "\"stepUpConfirmed\":true,\"effectiveAt\":\"" + futureEffective + "\"}";
            // Fatura ainda nao vencida: recusado.
            mvc.perform(post("/api/v1/platform/contracts/restriction-notices")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(noticeBody.replace(overdueInvoiceId, openInvoiceId)))
                    .andExpect(status().is4xxClientError());
            // Sem step-up: recusado.
            mvc.perform(post("/api/v1/platform/contracts/restriction-notices")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(noticeBody.replace("\"stepUpConfirmed\":true", "\"stepUpConfirmed\":false")))
                    .andExpect(status().isForbidden());
            // Papel sem contract:restrict: recusado pelo gate de permissao.
            mvc.perform(post("/api/v1/platform/contracts/restriction-notices")
                            .header("Authorization", bearer(platformToken("PLATFORM_SUPPORT")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(noticeBody))
                    .andExpect(status().isForbidden());
            // Com os elos: aviso aceito, ciclo em SUSPENSION_NOTICE (backoffice ainda opera).
            MvcResult notice = mvc.perform(post("/api/v1/platform/contracts/restriction-notices")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(noticeBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andReturn();
            String noticeId = idOf(notice);
            JdbcTemplate migrator = new JdbcTemplate(ProvisionedTenant.migratorDs);
            assertThat(migrator.queryForObject(
                    "SELECT status FROM platform.tenant_lifecycle WHERE tenant_id = ?",
                    String.class, tenantId)).isEqualTo("SUSPENSION_NOTICE");
            // Antes do prazo: ativacao recusada.
            mvc.perform(post("/api/v1/platform/contracts/restriction-notices/" + noticeId + "/activate")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"stepUpConfirmed\":true}"))
                    .andExpect(status().isConflict());
            // Prazo vencido (ajuste de relogio no banco, como viagem no tempo do teste): ativa.
            migrator.update("UPDATE platform.saas_restriction_notice SET effective_at = NOW() - INTERVAL '1 hour'"
                    + " WHERE id = ?", UUID.fromString(noticeId));
            mvc.perform(post("/api/v1/platform/contracts/restriction-notices/" + noticeId + "/activate")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"stepUpConfirmed\":true}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("EFFECTIVE"));
            assertThat(migrator.queryForObject(
                    "SELECT status FROM platform.tenant_lifecycle WHERE tenant_id = ?",
                    String.class, tenantId)).isEqualTo("SUSPENDED");
        } finally {
            cleanupTenant(tenantId);
        }
    }

    @Test
    @DisplayName("uso, fotografia com hash, relatorio com janela de 30 dias e contestacao dentro da janela")
    void usageSnapshotStatementAndContest() throws Exception {
        UUID tenantId = newCatalogTenant();
        try {
            LocalDate month = today().withDayOfMonth(1);
            mvc.perform(post("/api/v1/platform/usage")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"tenantId\":\"" + tenantId + "\",\"periodMonth\":\"" + month + "\","
                                    + "\"metricKey\":\"messages.whatsapp.utility\",\"quantity\":1250,"
                                    + "\"unit\":\"messages\"}"))
                    .andExpect(status().isOk());
            mvc.perform(post("/api/v1/platform/usage")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"tenantId\":\"" + tenantId + "\",\"periodMonth\":\"" + month + "\","
                                    + "\"metricKey\":\"customer_name\",\"quantity\":1,\"unit\":\"count\"}"))
                    .andExpect(status().is4xxClientError());
            MvcResult snapshot = mvc.perform(post("/api/v1/platform/usage/snapshots")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"tenantId\":\"" + tenantId + "\",\"periodMonth\":\"" + month + "\","
                                    + "\"activeConnections\":5000}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(true))
                    .andExpect(jsonPath("$.activeConnections").value(5000))
                    .andReturn();
            String snapshotId = idOf(snapshot);
            mvc.perform(get("/api/v1/platform/usage/snapshots/" + snapshotId + "/verify")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(true));
            // Fotografia alterada depois do hash: detectada.
            new JdbcTemplate(ProvisionedTenant.migratorDs).update(
                    "UPDATE platform.usage_connection_snapshot SET active_connections = 5001 WHERE id = ?",
                    UUID.fromString(snapshotId));
            mvc.perform(get("/api/v1/platform/usage/snapshots/" + snapshotId + "/verify")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(false));
            MvcResult statement = mvc.perform(post("/api/v1/platform/usage/statements/deliver?tenantId="
                                    + tenantId + "&periodMonth=" + month)
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DELIVERED"))
                    .andReturn();
            String statementId = idOf(statement);
            JdbcTemplate migrator = new JdbcTemplate(ProvisionedTenant.migratorDs);
            assertThat(migrator.queryForObject(
                    "SELECT (contest_until - delivered_at) FROM platform.usage_statement WHERE id = ?",
                    String.class, UUID.fromString(statementId))).contains("30 days");
            mvc.perform(post("/api/v1/platform/usage/statements/" + statementId + "/contest")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"note\":\"Quantidade divergente do relatorio interno\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CONTESTED"));
        } finally {
            cleanupTenant(tenantId);
        }
    }

    @Test
    @DisplayName("usage_ledger so tem colunas de contagem: nenhuma coluna de dado pessoal")
    void usageLedgerHasNoPersonalDataColumns() {
        JdbcTemplate app = new JdbcTemplate(ProvisionedTenant.appDs);
        List<String> columns = app.query(
                "SELECT column_name FROM information_schema.columns"
                        + " WHERE table_schema = 'platform' AND table_name = 'usage_ledger' ORDER BY column_name",
                (rs, i) -> rs.getString(1));
        assertThat(columns).containsExactly("created_at", "id", "metric_key", "period_month",
                "quantity", "tenant_id", "unit", "updated_at");
    }

    @Test
    @DisplayName("custo do provedor por etiqueta e por proxy, com margem por contrato na aba Contratos")
    void providerCostAndContractsTabMargin() throws Exception {
        UUID tenantId = newCatalogTenant();
        try {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            String contractNumber = "CTR-" + suffix;
            String contractId = createContract(tenantId, contractNumber, "10000", "{}");
            activateContract(contractId);
            LocalDate month = today().withDayOfMonth(1);
            String competence = String.format("%04d-%02d", today().getYear(), today().getMonthValue());
            issueInvoice(contractId, null, competence, "10000", today().plusDays(10));
            mvc.perform(post("/api/v1/platform/usage/costs")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"tenantId\":\"" + tenantId + "\",\"periodMonth\":\"" + month + "\","
                                    + "\"costKey\":\"provider:llm\",\"basis\":\"TAG\",\"amount\":4000,"
                                    + "\"notes\":\"etiqueta do provedor\"}"))
                    .andExpect(status().isOk());
            mvc.perform(post("/api/v1/platform/usage/costs")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"tenantId\":\"" + tenantId + "\",\"periodMonth\":\"" + month + "\","
                                    + "\"costKey\":\"proxy:tokens\",\"basis\":\"PROXY_TOKENS\",\"amount\":2000,"
                                    + "\"notes\":\"proxy por tokens\"}"))
                    .andExpect(status().isOk());
            MvcResult tab = mvc.perform(get("/api/v1/platform/central/contracts")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE"))))
                    .andExpect(status().isOk())
                    .andReturn();
            String body = tab.getResponse().getContentAsString();
            assertThat(body).contains(contractNumber);
            int at = body.indexOf(contractNumber);
            String row = body.substring(Math.max(0, at - 400), Math.min(body.length(), at + 600));
            assertThat(row).contains("\"providerCostMonth\":6000");
            assertThat(row).contains("\"invoicedMonth\":10000");
            assertThat(row).contains("\"margin\":0.4");
        } finally {
            cleanupTenant(tenantId);
        }
    }

    @Test
    @DisplayName("token de tenant nao abre rota de plataforma; SUPPORT nao gerencia contrato")
    void platformRoutesRefuseTenantAndSupportManage() throws Exception {
        UUID tenantId = newCatalogTenant();
        try {
            mvc.perform(post("/api/v1/platform/contracts?tenantId=" + tenantId)
                            .header("Authorization", bearer(tenantToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(contractJson("CTR-X", today().minusDays(30), today().plusDays(20),
                                    "10000", "{}")))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/platform/contracts?tenantId=" + tenantId)
                            .header("Authorization", bearer(platformToken("PLATFORM_SUPPORT")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(contractJson("CTR-X", today().minusDays(30), today().plusDays(20),
                                    "10000", "{}")))
                    .andExpect(status().isForbidden());
        } finally {
            cleanupTenant(tenantId);
        }
    }
}
