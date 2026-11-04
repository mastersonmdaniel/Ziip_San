package br.com.ziipsan.app;

import br.com.ziipsan.core.document.DocumentNumber;
import br.com.ziipsan.platform.infra.storage.ObjectStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
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
 * Area do tenant: contrato com o Ziip (P2.14, ADR-0022) na cadeia
 * real: o tenant ve contratos, faturas, pagamentos e relatorio do
 * PROPRIO contrato e baixa a fatura paga e a NFS-e; id de outro
 * tenant responde 404; XML baixado e identico ao autorizado, com o
 * hash conferido (adulteracao recusa o download); cada acesso servido
 * e auditado em {@code platform.audit_event}.
 *
 * <p>Isolamento: cada teste usa tenants so de catalogo (sem schema),
 * criados e apagados no proprio teste, como o SaaSContractIT da
 * P2.12. Os documentos sao semeados no armazenamento local de
 * documentos SaaS (bucket proprio) com a convencao de referencia da
 * P2.14; a forma da P2.13 sera confirmada na integracao.
 */
class TenantSaaSContractIT extends AppIntegrationTestBase {

    private static final String BUCKET = "ziipsan-saas-docs";

    @Autowired
    @Qualifier("saasContractObjectStorage")
    private ObjectStorage saasDocs;

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
        String schema = "p214_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        new JdbcTemplate(ProvisionedTenant.migratorDs).update(
                "INSERT INTO public.tenant_catalog (id, schema_name, name, cnpj, email, status, plan, settings)"
                        + " VALUES (?, ?, ?, ?, ?, 'ACTIVE', 'PRO', '{}')",
                id, schema, "Tenant P2.14 IT", syntheticCnpj(), "p214@teste.com.br");
        return id;
    }

    private static void cleanupTenant(UUID tenantId) {
        JdbcTemplate migrator = new JdbcTemplate(ProvisionedTenant.migratorDs);
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

    private static String idOf(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        int start = body.indexOf("\"id\":\"") + 6;
        int end = body.indexOf("\"", start);
        return body.substring(start, end);
    }

    private String createContract(UUID tenantId) throws Exception {
        String number = "CT-P214-" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult result = mvc.perform(post("/api/v1/platform/contracts?tenantId=" + tenantId)
                        .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contractNumber\":\"" + number + "\",\"biddingProcess\":\"PROC-2026-001\","
                                + "\"biddingModality\":\"PREGAO\",\"contractorCnpj\":\"" + syntheticCnpj() + "\","
                                + "\"startsAt\":\"" + today().minusDays(30) + "\",\"endsAt\":\"" + today().plusDays(20) + "\","
                                + "\"monthlyValue\":100.00,\"indexKey\":\"IPCA\","
                                + "\"indexBaseDate\":\"" + today().minusDays(30) + "\",\"slaPolicyKey\":null,"
                                + "\"quotasJson\":\"{}\"}"))
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

    private void payAndReconcile(UUID tenantId, String invoiceId, String amount) throws Exception {
        mvc.perform(post("/api/v1/platform/contracts/payments")
                        .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"" + tenantId + "\",\"invoiceId\":\"" + invoiceId
                                + "\",\"amount\":" + amount + ",\"paidAt\":\"" + today()
                                + "\",\"bankOrderRef\":\"ORDEM-1\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/platform/contracts/invoices/" + invoiceId + "/reconcile")
                        .header("Authorization", bearer(platformToken("PLATFORM_FINANCE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    private static String sha256Hex(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private void seedDocument(String key, byte[] content, String contentType) {
        saasDocs.put(BUCKET, key, new ByteArrayInputStream(content), contentType);
    }

    private void seedRefs(String invoiceId, String invoiceRef, String nfseRef) {
        new JdbcTemplate(ProvisionedTenant.migratorDs).update(
                "UPDATE platform.saas_invoice SET invoice_document_ref = ?, nfse_document_ref = ? WHERE id = ?",
                invoiceRef, nfseRef, UUID.fromString(invoiceId));
    }

    private int auditCount(UUID tenantId, String action) {
        Integer n = new JdbcTemplate(ProvisionedTenant.migratorDs).queryForObject(
                "SELECT count(*) FROM platform.audit_event WHERE tenant_id = ? AND action = ?",
                Integer.class, tenantId, action);
        return n == null ? 0 : n;
    }

    @Test
    @DisplayName("tenant ve contratos, faturas, pagamentos e relatorio do proprio contrato")
    void tenantSeesOwnContractInvoicesPaymentsAndStatement() throws Exception {
        UUID tenantId = newCatalogTenant();
        try {
            String contractId = createContract(tenantId);
            activateContract(contractId);
            String paidInvoice = issueInvoice(contractId, "2026-10", "100.00");
            payAndReconcile(tenantId, paidInvoice, "100.00");
            String openInvoice = issueInvoice(contractId, "2026-11", "100.00");
            // Pagamento sem fatura: aparece em unmatched.
            mvc.perform(post("/api/v1/platform/contracts/payments")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"tenantId\":\"" + tenantId + "\",\"invoiceId\":null,"
                                    + "\"amount\":50.00,\"paidAt\":\"" + today() + "\",\"bankOrderRef\":\"ORDEM-2\"}"))
                    .andExpect(status().isOk());
            // Relatorio de uso do mes, pela plataforma.
            LocalDate month = today().withDayOfMonth(1);
            mvc.perform(post("/api/v1/platform/usage/snapshots")
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"tenantId\":\"" + tenantId + "\",\"periodMonth\":\"" + month + "\","
                                    + "\"activeConnections\":100}"))
                    .andExpect(status().isOk());
            mvc.perform(post("/api/v1/platform/usage/statements/deliver?tenantId="
                                    + tenantId + "&periodMonth=" + month)
                            .header("Authorization", bearer(platformToken("PLATFORM_FINANCE"))))
                    .andExpect(status().isOk());

            String token = tenantToken(tenantId, "ADMIN");
            mvc.perform(get("/api/v1/tenant/my-contract/contracts")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(contractId));
            mvc.perform(get("/api/v1/tenant/my-contract/contracts/" + contractId)
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
            mvc.perform(get("/api/v1/tenant/my-contract/contracts/" + contractId + "/invoices")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
            mvc.perform(get("/api/v1/tenant/my-contract/invoices/" + paidInvoice)
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PAID"));
            mvc.perform(get("/api/v1/tenant/my-contract/contracts/" + contractId + "/payments")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
            mvc.perform(get("/api/v1/tenant/my-contract/invoices/" + paidInvoice + "/payments")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
            mvc.perform(get("/api/v1/tenant/my-contract/payments/unmatched")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
            mvc.perform(get("/api/v1/tenant/my-contract/usage/statements?periodMonth=" + month)
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DELIVERED"));
            assertThat(auditCount(tenantId, "SAAS_MYCONTRACT_VIEWED")).isGreaterThanOrEqualTo(8);
            // A fatura aberta existe, mas nao e o objeto deste teste.
            assertThat(openInvoice).isNotBlank();
        } finally {
            cleanupTenant(tenantId);
        }
    }

    @Test
    @DisplayName("contrato, fatura e documento de outro tenant respondem 404, como inexistente")
    void crossTenantIdsAnswer404() throws Exception {
        UUID tenantA = newCatalogTenant();
        UUID tenantB = newCatalogTenant();
        try {
            String contractId = createContract(tenantA);
            activateContract(contractId);
            String invoiceId = issueInvoice(contractId, "2026-10", "100.00");
            String tokenB = tenantToken(tenantB, "ADMIN");
            mvc.perform(get("/api/v1/tenant/my-contract/contracts/" + contractId)
                            .header("Authorization", bearer(tokenB)))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/tenant/my-contract/contracts/" + contractId + "/invoices")
                            .header("Authorization", bearer(tokenB)))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/tenant/my-contract/invoices/" + invoiceId)
                            .header("Authorization", bearer(tokenB)))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/tenant/my-contract/invoices/" + invoiceId + "/documents/invoice.pdf")
                            .header("Authorization", bearer(tokenB)))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/tenant/my-contract/invoices/" + invoiceId + "/documents/nfse.xml")
                            .header("Authorization", bearer(tokenB)))
                    .andExpect(status().isNotFound());
            // Inexistente tambem e 404: a resposta nao distingue os dois casos.
            mvc.perform(get("/api/v1/tenant/my-contract/contracts/" + UUID.randomUUID())
                            .header("Authorization", bearer(tokenB)))
                    .andExpect(status().isNotFound());
            // O outro tenant nao ve nada na propria lista.
            mvc.perform(get("/api/v1/tenant/my-contract/contracts")
                            .header("Authorization", bearer(tokenB)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        } finally {
            cleanupTenant(tenantA);
            cleanupTenant(tenantB);
        }
    }

    @Test
    @DisplayName("fatura paga e NFS-e baixadas em PDF e XML, com hash conferido e acesso auditado")
    void paidInvoiceAndNfseDownloadWithHashVerification() throws Exception {
        UUID tenantId = newCatalogTenant();
        String prefix = "p214/" + UUID.randomUUID() + "/";
        try {
            String contractId = createContract(tenantId);
            activateContract(contractId);
            String invoiceId = issueInvoice(contractId, "2026-10", "100.00");
            payAndReconcile(tenantId, invoiceId, "100.00");
            byte[] invoicePdf = "%PDF-fatura-paga".getBytes();
            byte[] nfseXml = "<nfse><numero>1</numero></nfse>".getBytes();
            seedDocument(prefix + "invoice.pdf", invoicePdf, "application/pdf");
            seedDocument(prefix + "nfse.xml", nfseXml, "application/xml");
            seedRefs(invoiceId,
                    "object:" + prefix + "invoice.pdf#sha256=" + sha256Hex(invoicePdf),
                    "object:" + prefix + "nfse.xml#sha256=" + sha256Hex(nfseXml));
            String token = tenantToken(tenantId, "CONTADOR");

            MvcResult pdf = mvc.perform(get("/api/v1/tenant/my-contract/invoices/" + invoiceId + "/documents/invoice.pdf")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Content-Sha256", sha256Hex(invoicePdf)))
                    .andReturn();
            assertThat(pdf.getResponse().getContentAsByteArray()).isEqualTo(invoicePdf);

            MvcResult xml = mvc.perform(get("/api/v1/tenant/my-contract/invoices/" + invoiceId + "/documents/nfse.xml")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Content-Sha256", sha256Hex(nfseXml)))
                    .andReturn();
            assertThat(xml.getResponse().getContentAsByteArray()).isEqualTo(nfseXml);

            // Formato nao gravado para o documento: 404, nao outro formato.
            mvc.perform(get("/api/v1/tenant/my-contract/invoices/" + invoiceId + "/documents/invoice.xml")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/tenant/my-contract/invoices/" + invoiceId + "/documents/nfse.pdf")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isNotFound());

            assertThat(auditCount(tenantId, "SAAS_MYCONTRACT_DOCUMENT_DOWNLOADED")).isEqualTo(2);
            String details = new JdbcTemplate(ProvisionedTenant.migratorDs).queryForObject(
                    "SELECT details::text FROM platform.audit_event WHERE tenant_id = ?"
                            + " AND action = 'SAAS_MYCONTRACT_DOCUMENT_DOWNLOADED' ORDER BY created_at DESC LIMIT 1",
                    String.class, tenantId);
            assertThat(details).contains(sha256Hex(nfseXml));
        } finally {
            saasDocs.delete(BUCKET, prefix + "invoice.pdf");
            saasDocs.delete(BUCKET, prefix + "nfse.xml");
            cleanupTenant(tenantId);
        }
    }

    @Test
    @DisplayName("fatura aberta nao baixa (409) e fatura paga sem documento responde 404")
    void unpaidDownloadIsConflictAndMissingDocumentIs404() throws Exception {
        UUID tenantId = newCatalogTenant();
        try {
            String contractId = createContract(tenantId);
            activateContract(contractId);
            String openInvoice = issueInvoice(contractId, "2026-10", "100.00");
            String paidNoDoc = issueInvoice(contractId, "2026-11", "100.00");
            payAndReconcile(tenantId, paidNoDoc, "100.00");
            String token = tenantToken(tenantId, "ADMIN");
            mvc.perform(get("/api/v1/tenant/my-contract/invoices/" + openInvoice + "/documents/invoice.pdf")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isConflict());
            mvc.perform(get("/api/v1/tenant/my-contract/invoices/" + paidNoDoc + "/documents/invoice.pdf")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/tenant/my-contract/invoices/" + paidNoDoc + "/documents/nfse.xml")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isNotFound());
        } finally {
            cleanupTenant(tenantId);
        }
    }

    @Test
    @DisplayName("documento adulterado apos o hash: download recusado, sem servir bytes, com auditoria da recusa")
    void tamperedDocumentIsRefusedAndAudited() throws Exception {
        UUID tenantId = newCatalogTenant();
        String prefix = "p214/" + UUID.randomUUID() + "/";
        try {
            String contractId = createContract(tenantId);
            activateContract(contractId);
            String invoiceId = issueInvoice(contractId, "2026-10", "100.00");
            payAndReconcile(tenantId, invoiceId, "100.00");
            byte[] original = "<nfse><numero>1</numero></nfse>".getBytes();
            seedDocument(prefix + "nfse.xml", original, "application/xml");
            seedRefs(invoiceId, null,
                    "object:" + prefix + "nfse.xml#sha256=" + sha256Hex(original));
            // Adulteracao: mesmo objeto, conteudo trocado, hash da referencia intacto.
            seedDocument(prefix + "nfse.xml", "<nfse><numero>2</numero></nfse>".getBytes(), "application/xml");
            String token = tenantToken(tenantId, "ADMIN");
            MvcResult refused = mvc.perform(get("/api/v1/tenant/my-contract/invoices/" + invoiceId + "/documents/nfse.xml")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().is5xxServerError())
                    .andReturn();
            // Comparacao como texto, nao como byte[]: o doesNotContain de
            // byte[] do AssertJ e por subsequencia (letras em ordem, com
            // saltos) e casa com as letras do JSON de erro, que nao contem
            // nenhum byte do documento. O que se verifica aqui e que o
            // documento adulterado nao foi servido como texto contiguo.
            assertThat(refused.getResponse().getContentAsString())
                    .doesNotContain("<nfse><numero>2</numero></nfse>");
            assertThat(auditCount(tenantId, "SAAS_MYCONTRACT_DOCUMENT_REFUSED")).isEqualTo(1);
            assertThat(auditCount(tenantId, "SAAS_MYCONTRACT_DOCUMENT_DOWNLOADED")).isEqualTo(0);
        } finally {
            saasDocs.delete(BUCKET, prefix + "nfse.xml");
            cleanupTenant(tenantId);
        }
    }

    @Test
    @DisplayName("perfil sem contract:read-own nao abre a area do contrato (403)")
    void withoutReadOwnPermissionIsForbidden() throws Exception {
        UUID tenantId = newCatalogTenant();
        try {
            String contractId = createContract(tenantId);
            mvc.perform(get("/api/v1/tenant/my-contract/contracts")
                            .header("Authorization", bearer(tenantToken(tenantId, "VIEWER"))))
                    .andExpect(status().isForbidden());
            assertThat(contractId).isNotBlank();
        } finally {
            cleanupTenant(tenantId);
        }
    }
}
