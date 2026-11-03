package br.com.ziipsan.commercial.registry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * P1.12: arquivo gerado — ponto único de entrega das exportações de lista acima
 * do limite síncrono (e, no futuro, dos relatórios agendados da P3.22, que
 * escrevem nesta mesma tabela; esta frente constrói só o item de entrega).
 *
 * <p>O conteúdo é o CSV já mascarado (a mesma máscara da tela, ADR-0008):
 * nenhum documento em claro passa por aqui. Os filtros que geraram o arquivo
 * NÃO ficam nesta tabela — vão só na mensagem da outbox que o worker consome,
 * para a busca digitada (que pode ser um documento) não virar um novo
 * armazenamento de dado pessoal. Sem trigger de auditoria, pelo mesmo motivo
 * dos XMLs fiscais: copiar o arquivo integral para o audit_log duplicaria o
 * artefato; o ciclo de vida (pedido, conclusão, falha) fica no registro de
 * exportação em personal_data_access_log e no estado desta linha.
 */
@Entity
@Table(name = "generated_files")
@Getter
@Setter
public class GeneratedFile extends TenantAwareEntity {

    public enum Status {
        QUEUED, READY, FAILED
    }

    /** Quem pediu a exportação (subject do token, quando é UUID). */
    @Column(name = "user_id")
    private UUID userId;

    /** Recurso exportado: "customers" ou "consumer-units". */
    @Column(name = "resource", nullable = false, length = 50)
    private String resource;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "file_name", nullable = false, length = 200)
    private String fileName;

    /** Linhas do arquivo; nulo enquanto enfileirado. */
    @Column(name = "row_count")
    private Long rowCount;

    /** CSV pronto; nulo enquanto enfileirado ou com falha. */
    @Column(name = "content", columnDefinition = "bytea")
    private byte[] content;

    /** Motivo da falha, quando FAILED. */
    @Column(name = "error", length = 500)
    private String error;

    @Column(name = "ready_at")
    private OffsetDateTime readyAt;
}
