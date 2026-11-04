package br.com.ziipsan.app;

import br.com.ziipsan.core.document.DocumentNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ponte P2-A (P2.13 ↔ P2.14): a referencia da NFS-e que a P2.13
 * grava na fatura e o Id da DPS (42 posicoes), nao a forma
 * {@code object:}. Este IT prova, na cadeia real, que o download
 * do tenant (P2.14) resolve essa referencia lendo o XML autorizado
 * (gzip) e o hash de {@code platform.saas_nfse}: 200 com o XML e o
 * cabecalho {@code X-Content-Sha256} igual ao hash registrado,
 * 404 para Id da DPS inexistente e 500 (fail-closed, com auditoria)
 * quando o hash registrado foi adulterado.
 *
 * <p>Os helpers de emissao sao os mesmos da SaaSNfseIT (tenant so
 * de catalogo, contexto SIMULATED, aliquotas de teste de 2026),
 * copiados aqui para nao tocar em arquivo de frente.
 */
class SaaSNfseDownloadBridgeIT extends AppIntegrationTestBase {

    private static String platformToken(String role) {
        return TestTokens.token(null, List.of(role), b -> b.claim("amr", List.of("otp")));
    }

    private static String tenantToken(UUID tenantId, String role) {
        return TestTokens.token(tenantId, List.of(role), b -> b.claim("amr", List.of("otp")));
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
        String schema = "bridge_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        new JdbcTemplate(ProvisionedTenant.migratorDs).update(
                "INSERT INTO public.tenant_catalog (id, schema_name, name, cnpj, email, status, plan, settings)"
                        + " VALUES (?, ?, ?, ?, ?, 'ACTIVE', 'PRO', '{}')",
                id, schema, "Tenant Ponte IT", syntheticCnpj(), "ponte@teste.com.br");
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

    private void registerContext(String providerCnpj) throws Exception {
        mvc.perform(post("/api/v1/platform/fiscal-contexts")
                        .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legalName\":\"Ziip SAN Plataforma Teste\",\"cnpj\":\"" + providerCnpj + "\","
                                + "\"municipalityIbge\":\"4205407\",\"series\":\"00001\","
                                + "\"environment\":\"SIMULATED\",\"taxRegime\":\"LUCRO_PRESUMIDO\"}"))
                .andExpect(status().isOk())
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
                                    + "\"rate\":" + rate[1] + ",\"legalBasis\":\"Vetor de teste 2026 (ponte)\"}"))
                    .andExpect(status().isOk());
        }
    }

    private String competence() {
        return String.format("%04d-%02d", today().getYear(), today().getMonthValue());
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    /** Emite a NFS-e de uma fatura nova e devolve [tenantId, invoiceId, dpsId]. */
    private String[] emitNfse() throws Exception {
        UUID tenantId = newCatalogTenant();
        try {
            String takerCnpj = syntheticCnpj();
            insertProfile(tenantId, "Prestadora Ponte Teste", takerCnpj);
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            String contractId = createContract(tenantId, "CTR-PONTE-" + suffix, takerCnpj);
            String invoiceId = issueInvoice(contractId, competence(), "10000");
            registerContext(syntheticCnpj());
            registerTaxRates();
            MvcResult issued = mvc.perform(post("/api/v1/platform/contracts/invoices/" + invoiceId + "/nfse")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                    .andReturn();
            String body = issued.getResponse().getContentAsString();
            String dpsId = body.substring(body.indexOf("\"dpsId\":\"") + 9, body.indexOf("\"dpsId\":\"") + 51);
            assertThat(dpsId).hasSize(42);
            return new String[]{tenantId.toString(), invoiceId, dpsId};
        } catch (Exception e) {
            cleanupTenant(tenantId);
            throw e;
        }
    }

    @Test
    @DisplayName("download da NFS-e resolve a referencia Id-da-DPS: 200, XML autorizado e hash registrado")
    void nfseDownloadResolvesDpsRef() throws Exception {
        String[] ctx = emitNfse();
        UUID tenantId = UUID.fromString(ctx[0]);
        String invoiceId = ctx[1];
        try {
            JdbcTemplate migrator = new JdbcTemplate(ProvisionedTenant.migratorDs);
            assertThat(migrator.queryForObject(
                    "SELECT nfse_document_ref FROM platform.saas_invoice WHERE id = ?",
                    String.class, UUID.fromString(invoiceId))).isEqualTo(ctx[2]);
            String registeredHash = migrator.queryForObject(
                    "SELECT xml_sha256 FROM platform.saas_nfse WHERE invoice_id = ?",
                    String.class, UUID.fromString(invoiceId));

            // A NFS-e nao exige fatura PAID, so referencia resolvida.
            MvcResult xml = mvc.perform(get("/api/v1/tenant/my-contract/invoices/" + invoiceId + "/documents/nfse.xml")
                            .header("Authorization", bearer(tenantToken(tenantId, "CONTADOR"))))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Content-Sha256", registeredHash))
                    .andReturn();
            byte[] bytes = xml.getResponse().getContentAsByteArray();
            // A igualdade com o hash registrado prova que os bytes sao
            // exatamente o XML autorizado; nao se assume conteudo interno.
            assertThat(bytes.length).isGreaterThan(0);
            assertThat(sha256Hex(bytes)).isEqualTo(registeredHash);
        } finally {
            cleanupTenant(tenantId);
        }
    }

    @Test
    @DisplayName("Id da DPS inexistente resolve para vazio: download 404, sem vazar bytes")
    void unknownDpsRefIs404() throws Exception {
        UUID tenantId = newCatalogTenant();
        try {
            String takerCnpj = syntheticCnpj();
            insertProfile(tenantId, "Prestadora Ponte Teste", takerCnpj);
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            String contractId = createContract(tenantId, "CTR-PONTE-" + suffix, takerCnpj);
            String invoiceId = issueInvoice(contractId, competence(), "10000");
            new JdbcTemplate(ProvisionedTenant.migratorDs).update(
                    "UPDATE platform.saas_invoice SET nfse_document_ref = ? WHERE id = ?",
                    "9".repeat(42), UUID.fromString(invoiceId));
            mvc.perform(get("/api/v1/tenant/my-contract/invoices/" + invoiceId + "/documents/nfse.xml")
                            .header("Authorization", bearer(tenantToken(tenantId, "CONTADOR"))))
                    .andExpect(status().isNotFound());
        } finally {
            cleanupTenant(tenantId);
        }
    }

    @Test
    @DisplayName("hash da NFS-e adulterado no banco: download recusado fail-closed (500) com auditoria")
    void tamperedNfseHashIsRefused() throws Exception {
        String[] ctx = emitNfse();
        UUID tenantId = UUID.fromString(ctx[0]);
        String invoiceId = ctx[1];
        try {
            JdbcTemplate migrator = new JdbcTemplate(ProvisionedTenant.migratorDs);
            migrator.update("UPDATE platform.saas_nfse SET xml_sha256 = ? WHERE invoice_id = ?",
                    "0".repeat(64), UUID.fromString(invoiceId));
            mvc.perform(get("/api/v1/tenant/my-contract/invoices/" + invoiceId + "/documents/nfse.xml")
                            .header("Authorization", bearer(tenantToken(tenantId, "CONTADOR"))))
                    .andExpect(status().is5xxServerError());
            // Mesmo padrao da P2.14 (auditCount): a recusa e auditada e
            // nenhum download foi servido para este tenant.
            assertThat(migrator.queryForObject(
                    "SELECT count(*) FROM platform.audit_event WHERE tenant_id = ? AND action = ?",
                    Integer.class, tenantId, "SAAS_MYCONTRACT_DOCUMENT_REFUSED")).isEqualTo(1);
            assertThat(migrator.queryForObject(
                    "SELECT count(*) FROM platform.audit_event WHERE tenant_id = ? AND action = ?",
                    Integer.class, tenantId, "SAAS_MYCONTRACT_DOCUMENT_DOWNLOADED")).isEqualTo(0);
        } finally {
            cleanupTenant(tenantId);
        }
    }
}
