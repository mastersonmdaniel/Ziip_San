package br.com.sigsan.compliance.lgpd.domain;

import br.com.sigsan.core.domain.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "lgpd_data_subject_request", indexes = {
        @Index(name = "idx_dsr_tenant_subject", columnList = "tenant_id, subject_id"),
        @Index(name = "idx_dsr_type_status", columnList = "request_type, status"),
        @Index(name = "idx_dsr_deadline", columnList = "deadline_at")
})
@Getter
@Setter
public class DataSubjectRequest extends TenantAwareEntity {

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "subject_type", length = 30)
    private String subjectType;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", length = 30, nullable = false)
    private RequestType requestType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private RequestStatus status = RequestStatus.RECEIVED;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "requested_data_categories", columnDefinition = "jsonb")
    private String requestedDataCategories;

    @Column(name = "verification_method", length = 50)
    private String verificationMethod; // EMAIL, SMS, DOCUMENT, IN_PERSON, BIOMETRIC

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "deadline_at", nullable = false)
    private OffsetDateTime deadlineAt; // 15 dias (LGPD art. 19, II: declaracao completa); a confirmacao/acesso simplificado e imediata (art. 19, I). O art. 18 lista os DIREITOS; o prazo e do art. 19. Prazo parametrizavel por tenant/regulador: confirmar com o juridico.

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "completed_by")
    private UUID completedBy;

    @Column(name = "response_summary", columnDefinition = "text")
    private String responseSummary;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "response_data", columnDefinition = "jsonb")
    private String responseData; // Data provided in response

    @Column(name = "denial_reason", columnDefinition = "text")
    private String denialReason;

    @Column(name = "extension_reason", columnDefinition = "text")
    private String extensionReason; // If extended beyond 15 days

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    public enum RequestType {
        CONFIRMATION, // Confirmação de tratamento (Art. 18, I)
        ACCESS, // Acesso aos dados (Art. 18, II)
        CORRECTION, // Correção de dados (Art. 18, III)
        ANONYMIZATION, // Anonimização (Art. 18, IV)
        BLOCKING, // Bloqueio (Art. 18, V)
        DELETION, // Eliminação (Art. 18, VI)
        PORTABILITY, // Portabilidade (Art. 18, VII)
        INFORMATION, // Informação sobre compartilhamento (Art. 18, VIII)
        REVOCATION, // Revogação do consentimento (Art. 18, IX)
        OPPOSITION; // Oposição ao tratamento (Art. 18, X)
    }

    public enum RequestStatus {
        RECEIVED,
        VERIFICATION_PENDING,
        VERIFIED,
        IN_PROGRESS,
        COMPLETED,
        DENIED,
        EXTENDED,
        EXPIRED,
        CANCELLED;
    }
}