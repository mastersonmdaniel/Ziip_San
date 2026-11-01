package br.com.ziipsan.compliance.lgpd.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import br.com.ziipsan.core.domain.TenantAwareEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Retention schedule entry (LGPD Art. 15, II / Art. 16).
 *
 * <p>Declares how long a given data category may be kept and what happens when the term
 * ends. The engine reads these rows; it never invents retention terms.</p>
 */
@Entity
@Table(name = "lgpd_retention_schedule", indexes = {
        @Index(name = "idx_retention_tenant_entity", columnList = "tenant_id, entity_type"),
        @Index(name = "idx_retention_next_run", columnList = "next_run_at")
})
@Getter
@Setter
public class RetentionSchedule extends TenantAwareEntity {

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType; // ConsentRecord, DataSubjectRequest, DpiaRecord, ...

    @Column(name = "category", nullable = false, length = 200)
    private String category; // What is being retained (e.g. CONTACT_DATA, BILLING_DATA)

    @Column(name = "retention_days", nullable = false)
    private Integer retentionDays; // Term measured from retentionAnchorAt

    @Column(name = "retention_anchor", length = 50)
    private String retentionAnchor; // Field the term is counted from (GRANTED_AT, COMPLETED_AT)

    /**
     * Legal or contractual reason to keep the data past the term (Art. 16, I).
     * When set, disposal is blocked and the record goes to legal review instead.
     */
    @Column(name = "legal_hold_reason", columnDefinition = "text")
    private String legalHoldReason;

    @Column(name = "disposal_action", length = 30, nullable = false)
    private String disposalAction; // ANONYMIZE, DELETE, ARCHIVE

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "last_run_at")
    private OffsetDateTime lastRunAt;

    @Column(name = "next_run_at")
    private OffsetDateTime nextRunAt;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;
}