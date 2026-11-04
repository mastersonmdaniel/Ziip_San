package br.com.ziipsan.app;

import br.com.ziipsan.core.document.DocumentNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NFS-e da venda SaaS pelo motor fiscal portado (P2.13, ADR-0003, ADR-0019, core V10_1_1)
 * na cadeia real: contexto fiscal da plataforma registrado, fatura do contrato gera NFS-e
 * pelo motor (serie fiscal, DPS, tributos por vigencia, XML autorizado com hash), a
 * referencia da fatura e resolvida com o Id da DPS, a segunda emissao devolve a existente
 * sem consumir outro numero, o tomador sai de {@code tenant_profile} (inclusive CNPJ
 * alfanumerico, P1.39) e HOMOLOGATION/PRODUCTION recusam antes de qualquer escrita.
 *
 * <p>Isolamento: cada teste usa um tenant so de catalogo (sem schema), como o SaaSContractIT
 * da P2.12. O contexto fiscal e as aliquotas sao globais da plataforma, por isso cada
 * teste registra os seus e a limpeza apaga contexto, series, documentos e aliquotas — um
 * teste nunca herda o contexto de outro. Valores de teste, nunca reais: o CNPJ do
 * prestador e sintetico (gerado como no SaaSContractIT), as aliquotas sao os vetores de
 * 2026 publicados no documento de porte (ISS 2%, IRRF 1,5%, CSRF 4,65%, CBS 0,9% e
 * IBS 0,1% informativos) e o ambiente e sempre SIMULATED, salvo o teste de recusa.
 */
class SaaSNfseIT extends AppIntegrationTestBase {

    private static String platformToken(String role) {
        return TestTokens.token(null, List.of(role), b -> b.claim("amr", List.of("otp")));
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
        String schema = "nfse_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        new JdbcTemplate(ProvisionedTenant.migratorDs).update(
                "INSERT INTO public.tenant_catalog (id, schema_name, name, cnpj, email, status, plan, settings)"
                        + " VALUES (?, ?, ?, ?, ?, 'ACTIVE', 'PRO', '{}')",
                id, schema, "Tenant NFS-e IT", syntheticCnpj(), "nfse@teste.com.br");
        return id;
    }

    private static void insertProfile(UUID tenantId, String legalName, String cnpj) {
        new JdbcTemplate(ProvisionedTenant.migratorDs).update(
                "INSERT INTO platform.tenant_profile (tenant_id, legal_name, cnpj, legal_nature)"
                        + " VALUES (?, ?, ?, 'PRIVATE')",
                tenantId, legalName, cnpj);
    }

    private static void cleanupTenant(UUID tenantId) {
        JdbcTemplate migrator = new JdbcTemplate(ProvisionedTenant.migratorDs);
        // A NFS-e referencia a fatura (saas_nfse.invoice_id) e o contexto
        // (saas_nfse.context_id): as linhas da NFS-e do tenant vao ANTES
        // das faturas e do contexto global, senao a limpeza viola a FK e
        // vaza contexto/aliquotas para o teste seguinte.
        migrator.update("DELETE FROM platform.saas_nfse_event WHERE nfse_id IN"
                + " (SELECT id FROM platform.saas_nfse WHERE tenant_id = ?)", tenantId);
        migrator.update("DELETE FROM platform.saas_nfse_tax WHERE nfse_id IN"
                + " (SELECT id FROM platform.saas_nfse WHERE tenant_id = ?)", tenantId);
        migrator.update("DELETE FROM platform.saas_nfse WHERE tenant_id = ?", tenantId);
        List<UUID> contractIds = migrator.query(
                "SELECT id FROM platform.saas_contract WHERE tenant_id = ?",
                (rs, i) -> rs.getObject(1, UUID.class), tenantId);
        for (UUID contractId : contractIds) {
            migrator.update("DELETE FROM platform.saas_restriction_notice WHERE contract_id = ?", contractId);
            migrator.update("DELETE FROM platform.saas_payment WHERE invoice_id IN"
                    + " (SELECT id FROM platform.saas_invoice WHERE contract_id = ?)", contractId);
            migrator.update("DELETE FROM platform.saas_invoice WHERE contract_id = ?", contractId);
            migrator.update("DELETE FROM platform.saas_readjustment WHERE contract_id = ?", contractId);
            migrator.update("DELETE FROM platform.saas_contract_addendum WHERE contract_id = ?", contractId);
            migrator.update("DELETE FROM platform.saas_commitment WHERE contract_id = ?", contractId);
        }
        migrator.update("DELETE FROM platform.saas_nfse_series");
        migrator.update("DELETE FROM platform.platform_fiscal_context");
        migrator.update("DELETE FROM platform.saas_fiscal_tax_rate");
        migrator.update("DELETE FROM platform.saas_contract WHERE tenant_id = ?", tenantId);
        migrator.update("DELETE FROM platform.audit_event WHERE tenant_id = ?", tenantId);
        migrator.update("DELETE FROM platform.tenant_profile WHERE tenant_id = ?", tenantId);
        migrator.update("DELETE FROM public.tenant_catalog WHERE id = ?", tenantId);
    }

    private static void clearFiscalContext() {
        JdbcTemplate migrator = new JdbcTemplate(ProvisionedTenant.migratorDs);
        // Mesma ordem de cleanupTenant: a NFS-e referencia o contexto, por
        // isso as linhas da NFS-e vao antes do contexto global.
        migrator.update("DELETE FROM platform.saas_nfse_event");
        migrator.update("DELETE FROM platform.saas_nfse_tax");
        migrator.update("DELETE FROM platform.saas_nfse");
        migrator.update("DELETE FROM platform.saas_nfse_series");
        migrator.update("DELETE FROM platform.platform_fiscal_context");
        migrator.update("DELETE FROM platform.saas_fiscal_tax_rate");
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private static String contractJson(String number, String contractorCnpj) {
        return "{\"contractNumber\":\"" + number + "\",\"biddingProcess\":\"PROC-2026-001\","
                + "\"biddingModality\":\"PREGAO\",\"contractorCnpj\":\"" + contractorCnpj + "\","
                + "\"startsAt\":\"" + today().minusDays(30) + "\",\"endsAt\":\"" + today().plusDays(20) + "\","
                + "\"monthlyValue\":10000,\"indexKey\":\"IPCA\","
                + "\"indexBaseDate\":\"" + today().minusDays(30) + "\",\"slaPolicyKey\":null,"
                + "\"quotasJson\":\"{}\"}";
    }

    private static String idOf(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        int start = body.indexOf("\"id\":\"") + 6;
        int end = body.indexOf("\"", start);
        return body.substring(start, end);
    }

    private String createContract(UUID tenantId, String number, String contractorCnpj) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/platform/contracts?tenantId=" + tenantId)
                        .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contractJson(number, contractorCnpj)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        return idOf(result);
    }

    private String issueInvoice(String contractId, String competence, String total) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/platform/contracts/" + contractId + "/invoices")
                        .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commitmentId\":null,\"competence\":\"" + competence
                                + "\",\"total\":" + total + ",\"dueDate\":\"" + today().plusDays(10) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn();
        return idOf(result);
    }

    private void registerContext(String providerCnpj, String environment) throws Exception {
        mvc.perform(post("/api/v1/platform/fiscal-contexts")
                        .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legalName\":\"Ziip SAN Plataforma Teste\",\"cnpj\":\"" + providerCnpj + "\","
                                + "\"municipalityIbge\":\"4205407\",\"series\":\"00001\","
                                + "\"environment\":\"" + environment + "\",\"taxRegime\":\"LUCRO_PRESUMIDO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environment").value(environment))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    private void registerTaxRates() throws Exception {
        String[][] rates = {{"ISS", "0.02"}, {"IRRF", "0.015"}, {"CSRF", "0.0465"},
                {"CBS", "0.009"}, {"IBS", "0.001"}};
        for (String[] rate : rates) {
            mvc.perform(post("/api/v1/platform/fiscal-contexts/tax-rates")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"taxCode\":\"" + rate[0] + "\",\"validFrom\":\"2026-01-01\","
                                    + "\"rate\":" + rate[1] + ",\"legalBasis\":\"Vetor de teste 2026 (porte)\"}"))
                    .andExpect(status().isOk());
        }
    }

    private String competence() {
        return String.format("%04d-%02d", today().getYear(), today().getMonthValue());
    }

    @Test
    @DisplayName("fatura gera NFS-e pelo motor: serie, DPS, protocolo do simulador e ref resolvida")
    void invoiceGeneratesNfseViaPortedEngine() throws Exception {
        UUID tenantId = newCatalogTenant();
        try {
            String takerCnpj = syntheticCnpj();
            insertProfile(tenantId, "Prestadora Teste Saneamento", takerCnpj);
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            String contractId = createContract(tenantId, "CTR-NFSE-" + suffix, takerCnpj);
            String invoiceId = issueInvoice(contractId, competence(), "10000");
            String providerCnpj = syntheticCnpj();
            registerContext(providerCnpj, "SIMULATED");
            registerTaxRates();

            MvcResult issued = mvc.perform(post("/api/v1/platform/contracts/invoices/" + invoiceId + "/nfse")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                    .andExpect(jsonPath("$.environment").value("SIMULATED"))
                    .andExpect(jsonPath("$.series").value("00001"))
                    .andExpect(jsonPath("$.number").value(1))
                    .andExpect(jsonPath("$.takerCnpj").value(takerCnpj))
                    .andReturn();
            String body = issued.getResponse().getContentAsString();
            String dpsId = body.substring(body.indexOf("\"dpsId\":\"") + 9, body.indexOf("\"dpsId\":\"") + 51);
            // Id da DPS (42): IBGE + tipo CNPJ (2) + CNPJ do prestador + serie + numero.
            assertThat(dpsId).hasSize(42);
            assertThat(dpsId).startsWith("42054072" + providerCnpj + "00001");
            assertThat(dpsId).endsWith("000000000000001");
            assertThat(body).contains("\"protocol\":\"SIM-" + dpsId + "\"");

            JdbcTemplate migrator = new JdbcTemplate(ProvisionedTenant.migratorDs);
            assertThat(migrator.queryForObject(
                    "SELECT nfse_document_ref FROM platform.saas_invoice WHERE id = ?",
                    String.class, UUID.fromString(invoiceId))).isEqualTo(dpsId);
            assertThat(migrator.queryForObject(
                    "SELECT xml_sha256 FROM platform.saas_nfse WHERE invoice_id = ?",
                    String.class, UUID.fromString(invoiceId))).matches("[0-9a-f]{64}");
            assertThat(migrator.queryForObject(
                    "SELECT xml_gzip IS NOT NULL FROM platform.saas_nfse WHERE invoice_id = ?",
                    Boolean.class, UUID.fromString(invoiceId))).isTrue();
            assertThat(migrator.queryForObject(
                    "SELECT amount FROM platform.saas_nfse_tax t JOIN platform.saas_nfse n ON n.id = t.nfse_id"
                            + " WHERE n.invoice_id = ? AND t.tax_code = 'ISS'",
                    java.math.BigDecimal.class, UUID.fromString(invoiceId))).isEqualByComparingTo("200");
            assertThat(migrator.queryForObject(
                    "SELECT amount FROM platform.saas_nfse_tax t JOIN platform.saas_nfse n ON n.id = t.nfse_id"
                            + " WHERE n.invoice_id = ? AND t.tax_code = 'CBS'",
                    java.math.BigDecimal.class, UUID.fromString(invoiceId))).isEqualByComparingTo("90");

            // Idempotencia: a segunda emissao devolve a mesma NFS-e, sem novo numero.
            mvc.perform(post("/api/v1/platform/contracts/invoices/" + invoiceId + "/nfse")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dpsId").value(dpsId))
                    .andExpect(jsonPath("$.number").value(1));
            assertThat(migrator.queryForObject(
                    "SELECT next_number FROM platform.saas_nfse_series", Long.class)).isEqualTo(2L);
            assertThat(migrator.queryForObject(
                    "SELECT count(*) FROM platform.saas_nfse", Integer.class)).isEqualTo(1);

            mvc.perform(get("/api/v1/platform/contracts/invoices/" + invoiceId + "/nfse")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dpsId").value(dpsId));
        } finally {
            cleanupTenant(tenantId);
        }
    }

    @Test
    @DisplayName("tomador com CNPJ alfanumerico (P1.39) emite: validado, nao recusado")
    void alphanumericTakerCnpjAccepted() throws Exception {
        // Vetor da documentacao da Receita (IN RFB 2.229/2024), conferido pelo calculo.
        String alphanumeric = "12ABC34501DE" + DocumentNumber.cnpjCheckDigits("12ABC34501DE");
        assertThat(DocumentNumber.isValidCnpj(alphanumeric)).isTrue();
        UUID tenantId = newCatalogTenant();
        try {
            insertProfile(tenantId, "Prestadora Alfanumerica Teste", alphanumeric);
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            String contractId = createContract(tenantId, "CTR-NFSE-ALFA-" + suffix, alphanumeric);
            String invoiceId = issueInvoice(contractId, competence(), "5000");
            registerContext(syntheticCnpj(), "SIMULATED");
            registerTaxRates();
            mvc.perform(post("/api/v1/platform/contracts/invoices/" + invoiceId + "/nfse")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                    .andExpect(jsonPath("$.takerCnpj").value(alphanumeric));
            JdbcTemplate migrator = new JdbcTemplate(ProvisionedTenant.migratorDs);
            assertThat(migrator.queryForObject(
                    "SELECT taker_cnpj FROM platform.saas_nfse WHERE invoice_id = ?",
                    String.class, UUID.fromString(invoiceId))).isEqualTo(alphanumeric);
        } finally {
            cleanupTenant(tenantId);
        }
    }

    @Test
    @DisplayName("HOMOLOGATION recusa antes de escrever: sem numero, sem documento, sem ref")
    void homologationRefusesBeforeWriting() throws Exception {
        UUID tenantId = newCatalogTenant();
        try {
            String takerCnpj = syntheticCnpj();
            insertProfile(tenantId, "Prestadora Teste Saneamento", takerCnpj);
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            String contractId = createContract(tenantId, "CTR-NFSE-HOM-" + suffix, takerCnpj);
            String invoiceId = issueInvoice(contractId, competence(), "10000");
            registerContext(syntheticCnpj(), "HOMOLOGATION");
            registerTaxRates();
            mvc.perform(post("/api/v1/platform/contracts/invoices/" + invoiceId + "/nfse")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE"))))
                    .andExpect(status().isConflict());
            JdbcTemplate migrator = new JdbcTemplate(ProvisionedTenant.migratorDs);
            assertThat(migrator.queryForObject(
                    "SELECT count(*) FROM platform.saas_nfse", Integer.class)).isEqualTo(0);
            assertThat(migrator.queryForObject(
                    "SELECT nfse_document_ref FROM platform.saas_invoice WHERE id = ?",
                    String.class, UUID.fromString(invoiceId))).isNull();
            assertThat(migrator.queryForObject(
                    "SELECT count(*) FROM platform.saas_nfse_series", Integer.class)).isEqualTo(0);
        } finally {
            cleanupTenant(tenantId);
        }
    }

    @Test
    @DisplayName("sem contexto fiscal registrado, a emissao e recusada (422) sem tocar na fatura")
    void missingContextRefuses() throws Exception {
        clearFiscalContext();
        UUID tenantId = newCatalogTenant();
        try {
            String takerCnpj = syntheticCnpj();
            insertProfile(tenantId, "Prestadora Teste Saneamento", takerCnpj);
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            String contractId = createContract(tenantId, "CTR-NFSE-NCTX-" + suffix, takerCnpj);
            String invoiceId = issueInvoice(contractId, competence(), "10000");
            mvc.perform(post("/api/v1/platform/contracts/invoices/" + invoiceId + "/nfse")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE"))))
                    .andExpect(status().isUnprocessableEntity());
            JdbcTemplate migrator = new JdbcTemplate(ProvisionedTenant.migratorDs);
            assertThat(migrator.queryForObject(
                    "SELECT nfse_document_ref FROM platform.saas_invoice WHERE id = ?",
                    String.class, UUID.fromString(invoiceId))).isNull();
        } finally {
            cleanupTenant(tenantId);
        }
    }

    @Test
    @DisplayName("sem aliquota vigente, o motor nao emite (422) e nenhum numero e consumido")
    void missingTaxRateRefuses() throws Exception {
        UUID tenantId = newCatalogTenant();
        try {
            String takerCnpj = syntheticCnpj();
            insertProfile(tenantId, "Prestadora Teste Saneamento", takerCnpj);
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            String contractId = createContract(tenantId, "CTR-NFSE-NRATE-" + suffix, takerCnpj);
            String invoiceId = issueInvoice(contractId, competence(), "10000");
            registerContext(syntheticCnpj(), "SIMULATED");
            mvc.perform(post("/api/v1/platform/contracts/invoices/" + invoiceId + "/nfse")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE"))))
                    .andExpect(status().isUnprocessableEntity());
            JdbcTemplate migrator = new JdbcTemplate(ProvisionedTenant.migratorDs);
            assertThat(migrator.queryForObject(
                    "SELECT count(*) FROM platform.saas_nfse", Integer.class)).isEqualTo(0);
        } finally {
            cleanupTenant(tenantId);
        }
    }

    @Test
    @DisplayName("contexto de producao marcado como teste e recusado no registro")
    void productionTestOnlyContextRefused() throws Exception {
        mvc.perform(post("/api/v1/platform/fiscal-contexts")
                        .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legalName\":\"Ziip SAN Plataforma Teste\",\"cnpj\":\"" + syntheticCnpj() + "\","
                                + "\"municipalityIbge\":\"4205407\",\"series\":\"00001\","
                                + "\"environment\":\"PRODUCTION\",\"testOnly\":true}"))
                .andExpect(status().isUnprocessableEntity());
        JdbcTemplate migrator = new JdbcTemplate(ProvisionedTenant.migratorDs);
        assertThat(migrator.queryForObject(
                "SELECT count(*) FROM platform.platform_fiscal_context WHERE environment = 'PRODUCTION'",
                Integer.class)).isEqualTo(0);
    }
}
