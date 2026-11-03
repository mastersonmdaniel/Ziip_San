package br.com.ziipsan.compliance.lgpd.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "lgpd_consent", indexes = {
        @Index(name = "idx_consent_tenant_subject", columnList = "tenant_id, subject_id"),
        @Index(name = "idx_consent_purpose", columnList = "purpose"),
        @Index(name = "idx_consent_status", columnList = "status"),
        @Index(name = "idx_consent_expires", columnList = "expires_at")
})
@Getter
@Setter
public class ConsentRecord extends TenantAwareEntity {

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId; // Data subject (user, customer, employee)

    @Column(name = "subject_type", length = 30, nullable = false)
    private String subjectType; // CUSTOMER, EMPLOYEE, USER, VISITOR

    @Column(name = "purpose", nullable = false, length = 200)
    private String purpose; // Specific purpose for processing

    @Column(name = "purpose_category", length = 50)
    private String purposeCategory; // MARKETING, OPERATIONAL, LEGAL, ANALYTICS, etc.

    @Enumerated(EnumType.STRING)
    @Column(name = "legal_basis", length = 30, nullable = false)
    private LegalBasis legalBasis; // CONSENT, CONTRACT, LEGAL_OBLIGATION, VITAL_INTEREST, PUBLIC_TASK, LEGITIMATE_INTEREST

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ConsentStatus status = ConsentStatus.GRANTED;

    @Column(name = "granted_at", nullable = false)
    private OffsetDateTime grantedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "consent_text", columnDefinition = "text")
    private String consentText; // Exact text presented to subject

    @Column(name = "consent_hash", length = 64)
    private String consentHash; // Hash of consent text for integrity

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "geolocation", columnDefinition = "jsonb")
    private String geolocation;

    @Column(name = "withdrawal_method", length = 50)
    private String withdrawalMethod; // WEB, EMAIL, PHONE, IN_PERSON, API

    @Column(name = "withdrawal_reason", columnDefinition = "text")
    private String withdrawalReason;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    /**
     * P1.15: exclusão lógica (migration V2_11). Linha excluída não aparece em
     * nenhuma leitura do módulo (repositórios filtram deletedAt IS NULL); a linha
     * sobrevive como prova, no mesmo padrão do cadastro (deletedAt em Customer).
     * O fluxo que preenche esta coluna (pedido do titular) é da P3.40.
     */
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public enum LegalBasis {
        CONSENT, // Consentimento (Art. 7º, I)
        CONTRACT, // Execução de contrato (Art. 7º, II)
        LEGAL_OBLIGATION, // Cumprimento de obrigação legal (Art. 7º, III)
        VITAL_INTEREST, // Proteção da vida (Art. 7º, IV)
        PUBLIC_TASK, // Execução de política pública (Art. 7º, V)
        LEGITIMATE_INTEREST, // Interesse legítimo (Art. 7º, IX)
        CREDIT_PROTECTION; // Proteção do crédito (Art. 7º, X)
    }

    public enum ConsentStatus {
        GRANTED, // Concedido
        REVOKED, // Revogado
        EXPIRED, // Expirado
        PENDING, // Pendente (double opt-in)
        WITHDRAWN; // Retirado (parcial)
    }
}