package br.com.ziipsan.platform.templates;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Um documento gerado pelo motor (P2.30, tabela {@code generated_documents}
 * da V1_12). O ponto do aceite: o registro grava o modelo e a versão usados
 * E o texto renderizado no instante da geração. Mudança posterior do modelo
 * (versão nova, outra versão vigente, modelo arquivado) não altera esta
 * linha — o documento já emitido é imutável por construção, não por
 * promessa de não re-renderizar.
 *
 * <p>{@code document_id} aponta para a linha de {@code documents} do
 * pipeline da P1.20 quando o arquivo foi guardado por ele (P1.24); nulo
 * quando só o instantâneo de texto foi gerado. O motor nunca grava arquivo
 * fora do pipeline.
 */
@Entity
@Table(name = "generated_documents")
@Getter
@Setter
public class GeneratedDocument extends TenantAwareEntity {

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "template_version_id", nullable = false)
    private UUID templateVersionId;

    /** Instantâneo do texto renderizado na geração. */
    @Column(name = "rendered_body", nullable = false)
    private String renderedBody;

    /**
     * Nomes das variáveis referenciadas na geração, como array JSON. Os
     * valores NÃO são persistidos (minimização LGPD): o valor impresso já
     * está no instantâneo do texto, que é o documento emitido.
     */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "variables_used", nullable = false, columnDefinition = "jsonb")
    private String variablesUsed = "[]";

    /** Linha de {@code documents} do pipeline, quando o arquivo foi guardado. */
    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "generated_by")
    private UUID generatedBy;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;
}
