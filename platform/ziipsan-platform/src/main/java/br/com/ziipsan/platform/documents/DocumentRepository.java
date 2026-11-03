package br.com.ziipsan.platform.documents;

import br.com.ziipsan.platform.documents.DocumentRecords.Document;
import br.com.ziipsan.platform.documents.DocumentRecords.DocumentType;
import br.com.ziipsan.platform.documents.DocumentRecords.RetentionClass;
import br.com.ziipsan.platform.documents.DocumentRecords.ScanEvent;
import br.com.ziipsan.platform.documents.DocumentRecords.ScanEventType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * P1.20: porta de persistencia do pipeline de documentos. A implementacao JDBC
 * e {@link JdbcDocumentRepository}; testes usam um repositorio em memoria
 * sobre esta interface.
 */
public interface DocumentRepository {

    DocumentType createDocumentType(UUID tenantId, String code, String label);

    Optional<DocumentType> findDocumentType(UUID tenantId, UUID documentTypeId);

    RetentionClass createRetentionClass(UUID tenantId, String code, String label, int retentionMonths);

    Optional<RetentionClass> findRetentionClass(UUID tenantId, UUID retentionClassId);

    Document insertDocument(Document document);

    Optional<Document> findDocument(UUID tenantId, UUID documentId);

    Document updateDocument(Document document);

    ScanEvent insertEvent(UUID tenantId, Optional<UUID> documentId, ScanEventType eventType,
                          String threat, String detail);

    List<ScanEvent> listEvents(UUID tenantId, UUID documentId);

    /** Evento mais recente de um tipo no tenant (para deduplicar alertas). */
    Optional<ScanEvent> latestEventByType(UUID tenantId, ScanEventType eventType);
}
