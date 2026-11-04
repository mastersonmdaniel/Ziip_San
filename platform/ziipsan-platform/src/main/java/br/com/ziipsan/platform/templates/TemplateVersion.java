package br.com.ziipsan.platform.templates;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Uma versão de um modelo (P2.30, tabela {@code template_versions} da V1_12).
 * Versão nunca é editada: correção gera versão nova, e a vigência sem
 * sobreposição (EXCLUDE da V1_12) impede duas versões vigentes ao mesmo
 * tempo para o mesmo modelo.
 *
 * <p>{@code blocks} e {@code variables} são JSON cru (o serviço valida a
 * forma e o motor valida o conteúdo): o JPA os mapeia como {@code jsonb}
 * sem desserializar, como os filtros de {@code SavedView}. Vigência com
 * os dois extremos inclusivos, como {@code parameter_versions} da V1_5:
 * uma versão até 31/01 e outra a partir de 31/01 se sobrepõem.
 */
@Entity
@Table(name = "template_versions")
@Getter
@Setter
public class TemplateVersion extends TenantAwareEntity {

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    /** Blocos de conteúdo, como array JSON de {@link TemplateBlock}. */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "blocks", nullable = false, columnDefinition = "jsonb")
    private String blocks = "[]";

    /** Variáveis declaradas, como array JSON de nome + tipo. */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "variables", nullable = false, columnDefinition = "jsonb")
    private String variables = "[]";

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    /** Nulo = sem fim de vigência. */
    @Column(name = "valid_to")
    private LocalDate validTo;

    /** {@code DRAFT}, {@code PROPOSED}, {@code APPROVED} ou {@code REJECTED}. */
    @Column(name = "status", nullable = false)
    private String status = "DRAFT";

    @Column(name = "proposed_by")
    private UUID proposedBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    /** Prévia com massa sintética; obrigatória antes da aprovação (CHECK). */
    @Column(name = "previewed_at")
    private OffsetDateTime previewedAt;

    /** Texto renderizado na prévia, para auditoria do que foi aprovado. */
    @Column(name = "preview_rendered")
    private String previewRendered;

    @Column(name = "rejection_reason")
    private String rejectionReason;
}
