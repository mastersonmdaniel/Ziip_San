package br.com.ziipsan.platform.templates;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Modelo de documento ou mensagem de um tenant (P2.30, tabela
 * {@code document_templates} da V1_12). O modelo é só o cabeçalho: o
 * conteúdo versionado vive em {@link TemplateVersion}.
 *
 * <p>Sem {@code @Builder} (o Lombok descarta os campos herdados de
 * {@code BaseEntity}, como em {@code SavedView}). Sem FK para a fábrica:
 * {@code factory_key} é a chave do catálogo em código
 * ({@link FactoryTemplates}), e nulo quando o modelo nasceu do zero.
 */
@Entity
@Table(name = "document_templates")
@Getter
@Setter
public class DocumentTemplate extends TenantAwareEntity {

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "title", nullable = false)
    private String title;

    /** Tipo de documento, como o código do enum (CHECK da V1_12). */
    @Column(name = "document_type", nullable = false)
    private String documentType;

    /** Canal, como o código do enum (CHECK da V1_12). */
    @Column(name = "channel", nullable = false)
    private String channel;

    @Column(name = "factory_key")
    private String factoryKey;

    /** {@code ACTIVE} ou {@code ARCHIVED}. */
    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";
}
