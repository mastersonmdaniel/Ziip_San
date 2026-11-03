package br.com.ziipsan.core.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import br.com.ziipsan.core.domain.BaseEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_log", indexes = {
    @Index(name = "idx_audit_tenant_entity", columnList = "tenant_id, entity_type, entity_id"),
    @Index(name = "idx_audit_user_time", columnList = "user_id, created_at"),
    @Index(name = "idx_audit_correlation", columnList = "correlation_id")
})
@Getter
@Setter
public class AuditLog extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private AuditAction action;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "old_values", columnDefinition = "jsonb")
    private String oldValues;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "new_values", columnDefinition = "jsonb")
    private String newValues;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "changed_fields", columnDefinition = "jsonb")
    private String changedFields;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "success", nullable = false)
    private Boolean success = true;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    public enum AuditAction {
        CREATE, READ, UPDATE, DELETE, LOGIN, LOGOUT, EXPORT, IMPORT, SIGN, VERIFY, ANONYMIZE;
    }

    /**
     * P1.7: o id do audit_log é sempre UUIDv7 (core V14, PK (created_at, id)).
     * Sobrescreve o onCreate de BaseEntity (UUIDv4) de propósito, e sem
     * checar o valor prévio: a ordem de disparo dos callbacks da hierarquia
     * (superclasse antes ou depois da subclasse) não pode decidir a versão
     * do id — nos dois casos o resultado é v7. Nenhum chamador atual fixa o
     * id de um AuditLog antes de salvar, então nada depende de um id
     * pré-atribuído aqui; o caminho do trigger usa o default uuidv7() do
     * banco, a mesma regra.
     */
    @Override
    @PrePersist
    protected void onCreate() {
        setId(UuidV7.generate());
    }
}