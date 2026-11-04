package br.com.ziipsan.commercial.registry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Rascunho do assistente de cadastro no servidor (P1.3, V3_12). O payload é a
 * cópia de trabalho do formulário, em JSONB, e PODE conter o documento em
 * claro antes da cifra — por isso a tabela nasce SEM trigger de auditoria
 * (register_tenant_table com p_audit = false, como generated_files): auditar
 * cada versão copiaria dado pessoal para o audit_log. O ciclo de vida é
 * rastreável pela própria linha (status, updated_at, updated_by).
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "registry_drafts")
@Getter
@Setter
public class RegistryDraft extends TenantAwareEntity {

    @Column(name = "resource", nullable = false, length = 20)
    private String resource;

    @Column(name = "target_id")
    private UUID targetId;

    /** Etapa 0-based, como o cliente do frontend (drafts.ts): 0 é a primeira etapa. */
    @Column(name = "step", nullable = false)
    private int step = 0;

    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload = "{}";

    @Column(name = "status", nullable = false, length = 20)
    private String status = "OPEN";

}
