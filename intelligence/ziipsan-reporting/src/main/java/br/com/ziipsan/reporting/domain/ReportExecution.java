package br.com.ziipsan.reporting.domain;

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

/** Execucao de relatorio (V15_1): sincrona, assincrona ou agendada. */
@Entity
@Table(name = "report_executions")
@Getter
@Setter
public class ReportExecution extends TenantAwareEntity {

    public enum Status {
        QUEUED, RUNNING, READY, FAILED, DENIED
    }

    @Column(name = "definition_code", nullable = false, length = 60)
    private String definitionCode;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "format", nullable = false, length = 10)
    private String format;

    @Column(name = "params", columnDefinition = "jsonb")
    private String paramsJson;

    @Column(name = "scheduled", nullable = false)
    private boolean scheduled;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "data_source", nullable = false, length = 20)
    private String dataSource;

    @Column(name = "statement_timeout_ms", nullable = false)
    private int statementTimeoutMs;

    @Column(name = "row_count")
    private Long rowCount;

    @Column(name = "generated_file_id")
    private UUID generatedFileId;

    @Column(name = "error", length = 500)
    private String error;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;
}
