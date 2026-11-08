package br.com.ziipsan.platform.portal.consumer;

import br.com.ziipsan.core.api.ApiException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Documentos da conta (P3.20): conta PAGA sai com a QUITACAO; o DANFAG sai
 * em PDF com a marca do tenant e estrutura marcada (tagged); o XML e o
 * ORIGINAL, com hash conferido contra o gravado — divergencia recusa a
 * entrega. TODO download e registrado no {@code personal_data_access_log}
 * ANTES de os bytes sairem.
 */
public final class BillDocumentService {

    public record BillDocuments(boolean paid, String settlementProof,
                                byte[] danfagPdf, boolean pdfTenantBranded, boolean pdfTagged,
                                byte[] xml, String xmlStoredSha256) { }

    public interface DocumentPort {
        boolean isUnitLinkedToConsumer(UUID tenantId, String consumerSubject, UUID unitId);
        Optional<UUID> unitOfBill(UUID tenantId, UUID billId);
        Optional<BillDocuments> documents(UUID tenantId, UUID billId);
    }

    /** Rascunho do registro em personal_data_access_log (a persistencia e da integracao). */
    public interface AccessLogPort {
        void recordDownload(UUID tenantId, String consumerSubject, UUID billId, String documentKind);
    }

    public record Download(String kind, byte[] bytes) { }

    private final DocumentPort port;
    private final AccessLogPort accessLog;

    public BillDocumentService(DocumentPort port, AccessLogPort accessLog) {
        if (port == null || accessLog == null) {
            throw new IllegalArgumentException("ports sao obrigatorias");
        }
        this.port = port;
        this.accessLog = accessLog;
    }

    public Download danfagPdf(UUID tenantId, String consumerSubject, UUID billId) {
        BillDocuments docs = requireLinkedDocuments(tenantId, consumerSubject, billId);
        if (docs.danfagPdf() == null || docs.danfagPdf().length == 0) {
            throw new IllegalStateException("DANFAG sem PDF gerado para a conta " + billId);
        }
        if (!docs.pdfTenantBranded()) {
            throw new IllegalStateException("DANFAG sem a marca do tenant nao e entregue: conta " + billId);
        }
        if (!docs.pdfTagged()) {
            throw new IllegalStateException("DANFAG sem estrutura marcada (tagged) nao e entregue: conta " + billId);
        }
        accessLog.recordDownload(tenantId, consumerSubject, billId, "DANFAG_PDF");
        return new Download("DANFAG_PDF", docs.danfagPdf());
    }

    public Download fiscalXml(UUID tenantId, String consumerSubject, UUID billId) {
        BillDocuments docs = requireLinkedDocuments(tenantId, consumerSubject, billId);
        if (docs.xml() == null || docs.xml().length == 0 || docs.xmlStoredSha256() == null) {
            throw new IllegalStateException("XML original ou hash gravado ausente para a conta " + billId);
        }
        String actual = sha256Hex(docs.xml());
        if (!MessageDigest.isEqual(actual.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                docs.xmlStoredSha256().toLowerCase(java.util.Locale.ROOT)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new IllegalStateException("hash do XML difere do gravado para a conta " + billId);
        }
        accessLog.recordDownload(tenantId, consumerSubject, billId, "FISCAL_XML");
        return new Download("FISCAL_XML", docs.xml());
    }

    /** Quitacao da conta paga: sem comprovante de quitacao, conta paga nao se apresenta como quitada. */
    public String settlement(UUID tenantId, String consumerSubject, UUID billId) {
        BillDocuments docs = requireLinkedDocuments(tenantId, consumerSubject, billId);
        if (!docs.paid()) {
            throw new ApiException.BadRequest("bill-not-paid", "A conta nao esta paga; nao ha quitacao.");
        }
        if (docs.settlementProof() == null || docs.settlementProof().isBlank()) {
            throw new IllegalStateException("conta paga sem quitacao registrada: " + billId);
        }
        accessLog.recordDownload(tenantId, consumerSubject, billId, "SETTLEMENT");
        return docs.settlementProof();
    }

    private BillDocuments requireLinkedDocuments(UUID tenantId, String consumerSubject, UUID billId) {
        if (tenantId == null || consumerSubject == null || consumerSubject.isBlank() || billId == null) {
            throw new IllegalArgumentException("tenant, consumidor e conta sao obrigatorios");
        }
        // D7: conta inexistente e conta de unidade nao vinculada recebem a
        // MESMA resposta (403, mesma mensagem) — enumerar UUIDs de conta
        // nao pode virar oraculo de existencia.
        UUID unitId = port.unitOfBill(tenantId, billId)
                .orElseThrow(() -> new ApiException.Forbidden(
                        "Conta de unidade nao vinculada a esta conta do portal."));
        if (!port.isUnitLinkedToConsumer(tenantId, consumerSubject, unitId)) {
            throw new ApiException.Forbidden("Conta de unidade nao vinculada a esta conta do portal.");
        }
        return port.documents(tenantId, billId)
                .orElseThrow(() -> new ApiException.NotFound("Documentos nao encontrados para a conta."));
    }

    static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }
}
