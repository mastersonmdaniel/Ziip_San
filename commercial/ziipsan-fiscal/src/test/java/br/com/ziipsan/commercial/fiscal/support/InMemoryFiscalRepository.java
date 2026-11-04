package br.com.ziipsan.commercial.fiscal.support;

import br.com.ziipsan.commercial.fiscal.port.FiscalDocumentRepository;
import br.com.ziipsan.commercial.fiscal.rules.TaxRateValidity.TaxRateRow;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório em memória com a MESMA semântica contratual do JDBC: reserva atômica
 * (synchronized), unicidade de origem, número nunca reutilizado. Não é um mock leniente:
 * duplicata de origem e série inexistente falham aqui como no banco.
 */
public final class InMemoryFiscalRepository implements FiscalDocumentRepository {

    private final Map<UUID, IssuerRecord> issuers = new HashMap<>();
    private final Map<String, Long> seriesNext = new HashMap<>();
    private final Map<UUID, DocumentRecord> documents = new HashMap<>();
    private final Map<String, UUID> bySource = new HashMap<>();
    private final Map<UUID, List<TaxLine>> taxes = new HashMap<>();
    private final Map<UUID, List<EventRecord>> events = new HashMap<>();
    private final Map<UUID, StoredXml> xml = new HashMap<>();
    private final List<TaxRateRow> rates = new ArrayList<>();

    public record StoredXml(byte[] gzip, String sha256, String source) {
    }

    public synchronized void addIssuer(IssuerRecord issuer) {
        issuers.put(issuer.id(), issuer);
    }

    public synchronized void addTaxRate(TaxRateRow rate) {
        rates.add(rate);
    }

    @Override
    public synchronized UUID insertTaxRate(UUID tenantId, TaxRateRow row, String legalBasis) {
        rates.add(row);
        return UUID.randomUUID();
    }

    /** Próximo número da série (o que será reservado na próxima emissão; 1 = nunca usada). */
    public synchronized long nextNumber(UUID issuerId, String documentType, String series) {
        return seriesNext.getOrDefault(seriesKey(issuerId, documentType, series), 0L);
    }

    public synchronized int documentCount() {
        return documents.size();
    }

    public synchronized Optional<StoredXml> storedXml(UUID documentId) {
        return Optional.ofNullable(xml.get(documentId));
    }

    public synchronized List<TaxLine> taxesOf(UUID documentId) {
        return List.copyOf(taxes.getOrDefault(documentId, List.of()));
    }

    @Override
    public synchronized Optional<IssuerRecord> findIssuer(UUID tenantId, UUID issuerId) {
        return Optional.ofNullable(issuers.get(issuerId));
    }

    @Override
    public synchronized void ensureSeries(UUID tenantId, UUID issuerId, String documentType, String series) {
        seriesNext.putIfAbsent(seriesKey(issuerId, documentType, series), 1L);
    }

    @Override
    public synchronized ReservedSlot reserveSlot(UUID tenantId, UUID issuerId, String documentType, String series,
            String sourceType, UUID sourceId, String environment, Integer tpAmb, BigDecimal gross) {
        String key = seriesKey(issuerId, documentType, series);
        if (!seriesNext.containsKey(key)) {
            throw new IllegalStateException("Serie fiscal inexistente: crie-a com ensureSeries antes de emitir");
        }
        String sourceKey = tenantId + "|" + sourceType + "|" + sourceId + "|" + documentType;
        if (bySource.containsKey(sourceKey)) {
            throw new DuplicateSourceException("A origem " + sourceType + "/" + sourceId + " ja gerou um documento " + documentType);
        }
        long number = seriesNext.get(key);
        seriesNext.put(key, number + 1);
        UUID documentId = UUID.randomUUID();
        documents.put(documentId, new DocumentRecord(documentId, issuerId, documentType, series, number,
                null, null, sourceType, sourceId, "RESERVED", environment, tpAmb, gross, null, null, null, null, null, null));
        bySource.put(sourceKey, documentId);
        return new ReservedSlot(documentId, number);
    }

    @Override
    public synchronized void signDocument(UUID tenantId, UUID documentId, String dpsId, String unsignedXml, List<TaxLine> taxLines) {
        DocumentRecord current = required(documentId);
        documents.put(documentId, with(current, dpsId, null, "SIGNED", unsignedXml, null, null, null, null));
        taxes.put(documentId, List.copyOf(taxLines));
    }

    @Override
    public synchronized void markAuthorized(UUID tenantId, UUID documentId, String protocol, String accessKey) {
        DocumentRecord current = required(documentId);
        documents.put(documentId, with(current, current.dpsId(), accessKey, "AUTHORIZED", current.unsignedXml(), protocol, null, null, current.replacedById()));
    }

    @Override
    public synchronized void markRejected(UUID tenantId, UUID documentId, String code, String message) {
        DocumentRecord current = required(documentId);
        documents.put(documentId, with(current, current.dpsId(), current.accessKey(), "REJECTED", current.unsignedXml(), null, code, message, current.replacedById()));
    }

    @Override
    public synchronized void markPendingReconciliation(UUID tenantId, UUID documentId) {
        DocumentRecord current = required(documentId);
        documents.put(documentId, with(current, current.dpsId(), current.accessKey(), "PENDING_RECONCILIATION", current.unsignedXml(), current.protocol(), null, null, current.replacedById()));
    }

    @Override
    public synchronized void markCancelled(UUID tenantId, UUID documentId) {
        DocumentRecord current = required(documentId);
        documents.put(documentId, with(current, current.dpsId(), current.accessKey(), "CANCELLED", current.unsignedXml(), current.protocol(), null, null, current.replacedById()));
    }

    @Override
    public synchronized void markReplaced(UUID tenantId, UUID originalId, UUID replacementId) {
        DocumentRecord current = required(originalId);
        documents.put(originalId, with(current, current.dpsId(), current.accessKey(), "REPLACED", current.unsignedXml(), current.protocol(), null, null, replacementId));
    }

    @Override
    public synchronized void registerEvent(UUID tenantId, EventRecord event) {
        events.computeIfAbsent(event.documentId(), k -> new ArrayList<>()).add(event);
    }

    @Override
    public synchronized void storeAuthorizedXml(UUID tenantId, UUID documentId, byte[] xmlGzip, String sha256, String source) {
        xml.put(documentId, new StoredXml(xmlGzip, sha256, source));
    }

    @Override
    public synchronized Optional<DocumentRecord> findDocument(UUID tenantId, UUID documentId) {
        return Optional.ofNullable(documents.get(documentId));
    }

    @Override
    public synchronized Optional<DocumentRecord> findBySource(UUID tenantId, String sourceType, UUID sourceId, String documentType) {
        UUID id = bySource.get(tenantId + "|" + sourceType + "|" + sourceId + "|" + documentType);
        return id == null ? Optional.empty() : Optional.ofNullable(documents.get(id));
    }

    @Override
    public synchronized Optional<EventRecord> findEventByDocumentAndKind(UUID tenantId, UUID documentId, String eventKind) {
        List<EventRecord> list = events.getOrDefault(documentId, List.of());
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).eventKind().equals(eventKind)) {
                return Optional.of(list.get(i));
            }
        }
        return Optional.empty();
    }

    @Override
    public synchronized List<TaxRateRow> taxRates(UUID tenantId) {
        return List.copyOf(rates);
    }

    private DocumentRecord required(UUID documentId) {
        DocumentRecord current = documents.get(documentId);
        if (current == null) {
            throw new IllegalStateException("Documento inexistente: " + documentId);
        }
        return current;
    }

    private static String seriesKey(UUID issuerId, String documentType, String series) {
        return issuerId + "|" + documentType + "|" + series;
    }

    private static DocumentRecord with(DocumentRecord current, String dpsId, String accessKey, String status,
            String unsignedXml, String protocol, String rejectionCode, String rejectionMessage, UUID replacedById) {
        return new DocumentRecord(current.id(), current.issuerId(), current.documentType(), current.series(),
                current.number(), dpsId, accessKey, current.sourceType(), current.sourceId(), status,
                current.environment(), current.tpAmb(), current.gross(), current.net(), unsignedXml,
                protocol, rejectionCode, rejectionMessage, replacedById);
    }
}
