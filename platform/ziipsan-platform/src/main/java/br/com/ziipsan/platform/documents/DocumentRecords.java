package br.com.ziipsan.platform.documents;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * P1.20: registros do pipeline de documentos. Sem anotacoes de framework: o
 * servico e o repositorio sao classes simples, construidas explicitamente
 * (mesmo padrao do modulo fiscal), para nao acoplar o pipeline ao contexto
 * Spring da aplicacao antes das frentes consumidoras (P1.19, P1.23, P1.24).
 */
public final class DocumentRecords {

    private DocumentRecords() {
    }

    /** Tipo de documento por catalogo (nunca texto livre). Codigo obrigatorio. */
    public record DocumentType(UUID id, UUID tenantId, String code, String label,
                               boolean active, Instant createdAt, Instant updatedAt) {
    }

    /** Classe de retencao do grupo G6 (ADR-0002): o tenant so amplia o piso. */
    public record RetentionClass(UUID id, UUID tenantId, String code, String label,
                                 int retentionMonths, boolean active,
                                 Instant createdAt, Instant updatedAt) {
    }

    /** Situacao da varredura. So CLEAN libera o download. */
    public enum ScanStatus {
        QUARANTINED,
        CLEAN,
        INFECTED,
        NOT_SCANNED
    }

    /** Classificacao do conteudo do documento (codigos do P0.11). */
    public enum DataClassification {
        ID, CT, LOC, FIN, SOC, SENS
    }

    /** Linha de documents. */
    public record Document(UUID id, UUID tenantId, UUID documentTypeId, UUID retentionClassId,
                           DataClassification dataClassification, ScanStatus scanStatus,
                           String originalFilename, String declaredContentType,
                           String detectedContentType, long sizeBytes, String sha256,
                           String storageBucket, String storageKey, boolean immutable,
                           Instant scannedAt, String scanner, Instant avDbUpdatedAt,
                           String threat, Instant createdAt, Instant updatedAt) {
        /** Liberado para leitura somente quando varrido e limpo. */
        public boolean released() {
            return scanStatus == ScanStatus.CLEAN;
        }
    }

    /** Tipo de evento da trilha de varredura (document_scan_events). */
    public enum ScanEventType {
        SCAN_QUARANTINED,
        SCAN_CLEAN,
        SCAN_INFECTED,
        SCAN_UNAVAILABLE,
        SIGNATURE_STALE
    }

    /** Um evento da trilha. documentId vazio = alerta do varredor, sem documento. */
    public record ScanEvent(UUID id, UUID tenantId, Optional<UUID> documentId,
                            ScanEventType eventType, String threat, String detail,
                            Instant createdAt) {
    }

    /** Resultado do monitor de idade das assinaturas. */
    public enum SignatureStatus {
        /** Idade conhecida e dentro do limite. */
        FRESH,
        /** Idade conhecida e acima do limite: gera alerta SIGNATURE_STALE. */
        STALE,
        /** Idade desconhecida (varredor nao informa): nunca alegada como atual. */
        UNKNOWN
    }

    /** Idade das assinaturas num instante: estado + idade quando conhecida. */
    public record SignatureFreshness(SignatureStatus status, Optional<Instant> updatedAt,
                                     Optional<java.time.Duration> age,
                                     java.time.Duration threshold) {
    }
}
