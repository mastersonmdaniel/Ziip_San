package br.com.ziipsan.reporting.export;

import br.com.ziipsan.reporting.domain.ReportExecution;
import br.com.ziipsan.reporting.domain.ReportExecutionRecorder;
import br.com.ziipsan.reporting.domain.ReportExecutionRepository;
import br.com.ziipsan.reporting.domain.ReportScheduleRepository;
import br.com.ziipsan.reporting.query.JdbcReportQueryExecutor;
import br.com.ziipsan.reporting.query.ReportDataSourceRouter;
import br.com.ziipsan.reporting.query.ReportQuery;
import br.com.ziipsan.reporting.query.ReportQueryExecutorPort;
import br.com.ziipsan.reporting.registry.ReportDefinition;
import br.com.ziipsan.reporting.registry.ReportRegistry;
import br.com.ziipsan.reporting.schedule.ReportWorker;
import br.com.ziipsan.reporting.spi.ReportUserContext;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Correcao B5, ponta a ponta no nivel servico+worker com fakes honestos:
 * exportacao acima do limite sincrono enfileira (generated_files QUEUED +
 * report_executions QUEUED com os params) e o WORKER entrega - arquivo
 * READY com o conteudo MASCARADO e execucao READY. Antes do conserto o
 * arquivo ficava QUEUED para sempre (ninguem chamava complete).
 */
class AsyncExportEndToEndTest {

    @Test
    void exportacaoAcimaDoLimiteTerminaEmArquivoCompletoMascarado() {
        UUID tenant = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ReportUserContext user = new ReportUserContext(tenant, userId,
                Set.of("reporting.rpt-002:export"), ReportUserContext.MaskCeiling.FULL, Set.of());

        // 1001 linhas de RPT-002 com documento pessoal em claro na fonte.
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < ReportDefinition.SYNC_ROW_LIMIT + 1; i++) {
            rows.add(Map.of("payment_id", "p" + i, "data_pagamento", "2026-10-05", "canal", "PIX",
                    "valor", new java.math.BigDecimal("10.00"), "documento_pagador", "12345678900",
                    "mes_arrecadacao", "2026-10-01", "recebido_em", "2026-10-05T10:00:00"));
        }
        ReportQueryExecutorPort executor = (definition, query, u, timeout) ->
                new JdbcReportQueryExecutor.ExecutionResult(rows, false,
                        ReportDataSourceRouter.Source.PRIMARY);
        List<String> auditResults = new ArrayList<>();
        ExportAuditRecorder audit = (t, u, code, result, purpose) -> auditResults.add(result);

        // Entrega honesta: guarda status e conteudo de verdade.
        Map<UUID, String> status = new HashMap<>();
        Map<UUID, byte[]> content = new HashMap<>();
        GeneratedFileDelivery delivery = new GeneratedFileDelivery() {
            @Override
            public UUID enqueue(UUID tenantId, UUID uid, String resource, String fileName) {
                UUID id = UUID.randomUUID();
                status.put(id, "QUEUED");
                return id;
            }

            @Override
            public void complete(UUID id, long rowCount, byte[] bytes) {
                status.put(id, "READY");
                content.put(id, bytes);
            }

            @Override
            public void fail(UUID id, String error) {
                status.put(id, "FAILED");
            }
        };
        // Recorder honesto: materializa a execucao como o JPA faria, para o
        // worker a encontrar na fila QUEUED.
        List<ReportExecution> executionRows = new ArrayList<>();
        ReportExecutionRecorder recorder = new ReportExecutionRecorder() {
            @Override
            public UUID start(Start start) {
                ReportExecution entity = new ReportExecution();
                entity.setId(UUID.randomUUID());
                entity.setTenantId(start.tenantId());
                entity.setDefinitionCode(start.definitionCode());
                entity.setRequestedBy(start.userId());
                entity.setFormat(start.format());
                entity.setParamsJson(start.paramsJson());
                entity.setScheduled(start.scheduled());
                entity.setStatus(start.status());
                entity.setDataSource(start.dataSource());
                entity.setStatementTimeoutMs(start.statementTimeoutMs());
                executionRows.add(entity);
                return entity.getId();
            }

            @Override
            public void finish(UUID id, ReportExecution.Status newStatus, Long rowCount,
                               UUID fileId, String error) {
                executionRows.stream().filter(e -> e.getId().equals(id)).findFirst().ifPresent(e -> {
                    e.setStatus(newStatus);
                    if (rowCount != null) {
                        e.setRowCount(rowCount);
                    }
                    if (fileId != null) {
                        e.setGeneratedFileId(fileId);
                    }
                    e.setError(error);
                });
            }
        };
        ReportExportService service = new ReportExportService(executor, audit, delivery, recorder,
                List.of(new CsvReportWriter(), new XlsxReportWriter(), new PdfReportWriter()));
        ReportDefinition definition = new ReportRegistry().find("RPT-002").orElseThrow();

        var outcome = service.export(definition, ReportQuery.empty(), ExportFormat.CSV, user, false);
        assertThat(outcome.isQueued()).isTrue();
        UUID fileId = outcome.generatedFileId();
        assertThat(status.get(fileId)).isEqualTo("QUEUED");
        assertThat(executionRows).hasSize(1);
        assertThat(executionRows.get(0).getStatus()).isEqualTo(ReportExecution.Status.QUEUED);
        assertThat(executionRows.get(0).getGeneratedFileId()).isEqualTo(fileId);

        // O worker consome a fila ad-hoc e fecha o ciclo.
        ReportExecutionRepository executionsRepo = mock(ReportExecutionRepository.class);
        when(executionsRepo.findByStatusAndScheduledFalseOrderByCreatedAtAsc(ReportExecution.Status.QUEUED))
                .thenAnswer(invocation -> executionRows.stream()
                        .filter(e -> e.getStatus() == ReportExecution.Status.QUEUED
                                && !e.isScheduled())
                        .toList());
        ReportWorker worker = new ReportWorker(mock(ReportScheduleRepository.class), executionsRepo,
                recorder, new ReportRegistry(), service, delivery,
                ReportWorker::conservativeContext,
                (tenantId, uid) -> user);
        worker.runQueuedAdHoc();

        assertThat(status.get(fileId)).isEqualTo("READY");
        String csv = new String(content.get(fileId), StandardCharsets.UTF_8);
        assertThat(csv).doesNotContain("12345678900");
        assertThat(csv).contains("***");
        assertThat(csv.lines().count()).isEqualTo(ReportDefinition.SYNC_ROW_LIMIT + 2L);
        assertThat(executionRows.get(0).getStatus()).isEqualTo(ReportExecution.Status.READY);
        assertThat(executionRows.get(0).getRowCount())
                .isEqualTo(ReportDefinition.SYNC_ROW_LIMIT + 1L);
    }
}
