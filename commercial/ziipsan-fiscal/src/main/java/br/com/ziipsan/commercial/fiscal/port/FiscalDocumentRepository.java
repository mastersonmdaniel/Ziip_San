package br.com.ziipsan.commercial.fiscal.port;

import br.com.ziipsan.commercial.fiscal.rules.TaxRateValidity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de persistência do motor fiscal (V4_5). O serviço de emissão só fala com esta
 * porta: a implementação JDBC vive em {@code persistence} e a de teste, em memória, nos
 * testes. A reserva de número é atômica por contrato: travar a série e gravar o documento
 * em RESERVED acontecem na mesma transação, ou nada acontece — é o que garante "sem número
 * repetido nem perdido" (P1.8) mesmo com emissões simultâneas.
 */
public interface FiscalDocumentRepository {

    /** Emitente, como gravado em {@code fiscal_issuers} (só hash/máscara, nunca em claro). */
    record IssuerRecord(UUID id, String ibge, String documentKind, String documentHash) {
    }

    /** Número reservado e o id do documento RESERVED criado na mesma transação. */
    record ReservedSlot(UUID documentId, long number) {
    }

    /** Linha de {@code fiscal_documents}, com os campos que o serviço lê. */
    record DocumentRecord(
            UUID id, UUID issuerId, String documentType, String series, long number,
            String dpsId, String accessKey, String sourceType, UUID sourceId, String status,
            String environment, Integer tpAmb, BigDecimal gross, BigDecimal net,
            String unsignedXml, String protocol, String rejectionCode, String rejectionMessage,
            UUID replacedById) {
    }

    /** Linha de {@code fiscal_document_taxes}. */
    record TaxLine(String taxCode, BigDecimal rate, BigDecimal amount, boolean informative) {
    }

    /** Linha de {@code fiscal_document_events}. */
    record EventRecord(
            UUID id, UUID documentId, String eventId, String eventType, String eventKind,
            String status, String reason, UUID requestedBy, UUID approvedBy, UUID approvedBySecond) {
    }

    /** Violação da unicidade de origem: a mesma origem já gerou este tipo de documento. */
    class DuplicateSourceException extends IllegalStateException {
        public DuplicateSourceException(String message) {
            super(message);
        }
    }

    Optional<IssuerRecord> findIssuer(UUID tenantId, UUID issuerId);

    /** Cria a série se ela ainda não existir (ato administrativo; idempotente). */
    void ensureSeries(UUID tenantId, UUID issuerId, String documentType, String series);

    /**
     * Reserva o próximo número da série e grava o documento em RESERVED, na mesma transação.
     * O número nunca volta ao estoque: se a emissão falhar depois, o documento fica com um
     * estado terminal ou reconciliável (REJECTED / PENDING_RECONCILIATION), nunca apagado.
     *
     * @throws DuplicateSourceException se a origem já gerou este tipo de documento
     * @throws IllegalStateException se a série não existir
     */
    ReservedSlot reserveSlot(UUID tenantId, UUID issuerId, String documentType, String series,
            String sourceType, UUID sourceId, String environment, Integer tpAmb, BigDecimal gross);

    /** Preenche o Id da DPS, o XML não assinado e as linhas de tributo; estado passa a SIGNED. */
    void signDocument(UUID tenantId, UUID documentId, String dpsId, String unsignedXml, List<TaxLine> taxes);

    void markAuthorized(UUID tenantId, UUID documentId, String protocol, String accessKey);

    void markRejected(UUID tenantId, UUID documentId, String code, String message);

    void markPendingReconciliation(UUID tenantId, UUID documentId);

    void markCancelled(UUID tenantId, UUID documentId);

    /** Original passa a REPLACED e aponta para o substituto. */
    void markReplaced(UUID tenantId, UUID originalId, UUID replacementId);

    void registerEvent(UUID tenantId, EventRecord event);

    void storeAuthorizedXml(UUID tenantId, UUID documentId, byte[] xmlGzip, String sha256, String source);

    Optional<DocumentRecord> findDocument(UUID tenantId, UUID documentId);

    Optional<DocumentRecord> findBySource(UUID tenantId, String sourceType, UUID sourceId, String documentType);

    Optional<EventRecord> findEventByDocumentAndKind(UUID tenantId, UUID documentId, String eventKind);

    List<TaxRateValidity.TaxRateRow> taxRates(UUID tenantId);
}
