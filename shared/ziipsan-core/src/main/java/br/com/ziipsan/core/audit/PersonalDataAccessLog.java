package br.com.ziipsan.core.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro de acesso a dado pessoal (P0.11, ADR-0008, tabela {@code personal_data_access_log}
 * da V1_6, um registro por tenant).
 *
 * <p>É a base da detecção de anomalia de acesso (ADR-0008, RNF-41), cuja detecção completa é
 * da P3.10: aqui se entrega o registro e os ganchos de consulta
 * ({@link PersonalDataAccessLogRepository#countQuotaExceeded}), não a detecção.
 *
 * <p>Append-only e sem trigger de auditoria: o log não se audita a si mesmo (evita recursão
 * e duplicação na trilha). Nunca guarda o documento em claro: no máximo o índice cego
 * (hash), o mesmo usado para buscar sem decifrar. Não herda {@code BaseEntity}: o log não
 * tem versão otimista nem {@code tenant_schema} — só {@code accessed_at}, o instante do
 * acesso, que define a janela da cota.
 */
@Entity
@Table(name = "personal_data_access_log", indexes = {
        @Index(name = "idx_pdal_tenant_user_time", columnList = "tenant_id, user_id, accessed_at"),
        @Index(name = "idx_pdal_tenant_result_time", columnList = "tenant_id, result, accessed_at")
})
@Getter
@Setter
public class PersonalDataAccessLog {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "accessed_at", nullable = false, updatable = false)
    private Instant accessedAt;

    /** DOCUMENT_SEARCH, PERSONAL_READ, DOCUMENT_REVEAL ou EXPORT (P1.12). */
    @Column(name = "access_type", nullable = false, length = 20, updatable = false)
    private String accessType;

    @Column(name = "entity_type", length = 100, updatable = false)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private UUID entityId;

    /** Índice cego do documento buscado/revelado; nunca o valor em claro. */
    @Column(name = "document_hash", length = 64, updatable = false)
    private String documentHash;

    /** Finalidade informada por quem acessou (obrigatória na revelação; nula nos demais). */
    @Column(name = "purpose", length = 500, updatable = false)
    private String purpose;

    /** FOUND, NOT_FOUND, DENIED ou QUOTA_EXCEEDED. */
    @Column(name = "result", nullable = false, length = 20, updatable = false)
    private String result;

    public enum AccessType {
        DOCUMENT_SEARCH, PERSONAL_READ, DOCUMENT_REVEAL,
        /** P1.12: exportação de lista (síncrona ou enfileirada); o recurso e as linhas vão em purpose. */
        EXPORT
    }

    public enum Result {
        FOUND, NOT_FOUND, DENIED, QUOTA_EXCEEDED
    }
}
