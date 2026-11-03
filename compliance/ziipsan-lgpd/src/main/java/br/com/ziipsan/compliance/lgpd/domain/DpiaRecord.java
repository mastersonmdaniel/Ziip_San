package br.com.ziipsan.compliance.lgpd.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "lgpd_dpia", indexes = {
        @Index(name = "idx_dpia_tenant_status", columnList = "tenant_id, status"),
        @Index(name = "idx_dpia_processing", columnList = "processing_activity"),
        @Index(name = "idx_dpia_risk", columnList = "risk_level")
})
@Getter
@Setter
public class DpiaRecord extends TenantAwareEntity {

    @Column(name = "processing_activity", nullable = false, length = 200)
    private String processingActivity; // Name/description of processing

    @Column(name = "processing_description", columnDefinition = "text")
    private String processingDescription;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "data_categories", columnDefinition = "jsonb")
    private String dataCategories; // Array of data categories processed

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "special_categories", columnDefinition = "jsonb")
    private String specialCategories; // Special category data (Art. 11 LGPD)

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "data_subjects", columnDefinition = "jsonb")
    private String dataSubjects; // Categories of data subjects

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "recipients", columnDefinition = "jsonb")
    private String recipients; // Categories of recipients

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "third_country_transfers", columnDefinition = "jsonb")
    private String thirdCountryTransfers; // International transfers

    @Column(name = "retention_period", length = 100)
    private String retentionPeriod;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "security_measures", columnDefinition = "jsonb")
    private String securityMeasures; // Technical and organizational measures

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 20)
    private RiskLevel riskLevel;

    @Column(name = "risk_score", precision = 5, scale = 2)
    private java.math.BigDecimal riskScore; // 0-100

    @Column(name = "likelihood", length = 20)
    private String likelihood; // HIGH, MEDIUM, LOW

    @Column(name = "severity", length = 20)
    private String severity; // HIGH, MEDIUM, LOW

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "mitigation_measures", columnDefinition = "jsonb")
    private String mitigationMeasures; // Implemented/planned measures

    @Column(name = "residual_risk", length = 20)
    private String residualRisk; // HIGH, MEDIUM, LOW after mitigation

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private DpiaStatus status = DpiaStatus.DRAFT;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "review_date")
    private OffsetDateTime reviewDate;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "dpo_signature", columnDefinition = "jsonb")
    private String dpoSignature; // DPO approval

    @Column(name = "report_pdf", columnDefinition = "bytea")
    private byte[] reportPdf;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    /** P1.15: exclusão lógica (V2_11) — linha excluída não aparece nas leituras do módulo. */
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public enum RiskLevel {
        NEGLIGIBLE, LOW, MEDIUM, HIGH, CRITICAL;
    }

    public enum DpiaStatus {
        DRAFT, IN_REVIEW, APPROVED, REJECTED, EXPIRED, ARCHIVED;
    }
}