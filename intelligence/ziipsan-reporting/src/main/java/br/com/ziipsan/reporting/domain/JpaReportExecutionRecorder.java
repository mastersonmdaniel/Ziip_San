package br.com.ziipsan.reporting.domain;

import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Gravacao da trilha de execucoes via o repositorio JPA do modulo. */
public class JpaReportExecutionRecorder implements ReportExecutionRecorder {

    private final ReportExecutionRepository repository;

    public JpaReportExecutionRecorder(ReportExecutionRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public UUID start(Start start) {
        ReportExecution entity = new ReportExecution();
        entity.setTenantId(start.tenantId());
        entity.setDefinitionCode(start.definitionCode());
        entity.setRequestedBy(start.userId());
        entity.setFormat(start.format());
        entity.setParamsJson(start.paramsJson());
        entity.setScheduled(start.scheduled());
        entity.setStatus(start.status());
        entity.setDataSource(start.dataSource());
        entity.setStatementTimeoutMs(start.statementTimeoutMs());
        entity.setRowCount(start.rowCount());
        entity.setGeneratedFileId(start.generatedFileId());
        entity.setError(start.error());
        if (isTerminal(start.status())) {
            entity.setFinishedAt(OffsetDateTime.now(ZoneOffset.UTC));
        }
        return repository.save(entity).getId();
    }

    @Override
    @Transactional
    public void finish(UUID executionId, ReportExecution.Status status, Long rowCount,
                       UUID generatedFileId, String error) {
        repository.findById(executionId).ifPresent(entity -> {
            entity.setStatus(status);
            if (rowCount != null) {
                entity.setRowCount(rowCount);
            }
            if (generatedFileId != null) {
                entity.setGeneratedFileId(generatedFileId);
            }
            entity.setError(error == null ? null : error.substring(0, Math.min(500, error.length())));
            if (isTerminal(status)) {
                entity.setFinishedAt(OffsetDateTime.now(ZoneOffset.UTC));
            }
            repository.save(entity);
        });
    }

    private static boolean isTerminal(ReportExecution.Status status) {
        return status == ReportExecution.Status.READY
                || status == ReportExecution.Status.FAILED
                || status == ReportExecution.Status.DENIED;
    }
}
