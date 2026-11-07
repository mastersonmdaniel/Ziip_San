package br.com.ziipsan.reporting.export;

import br.com.ziipsan.reporting.domain.ReportExecution;
import br.com.ziipsan.reporting.domain.ReportExecutionRecorder;
import br.com.ziipsan.reporting.query.JdbcReportQueryExecutor;
import br.com.ziipsan.reporting.query.ReportDataSourceRouter;
import br.com.ziipsan.reporting.query.ReportQuery;
import br.com.ziipsan.reporting.query.ReportQueryExecutorPort;
import br.com.ziipsan.reporting.registry.ReportDefinition;
import br.com.ziipsan.reporting.registry.ReportRegistry;
import br.com.ziipsan.reporting.spi.ReportUserContext;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regra unica da P1.12, ponta a ponta no servico: permissao, registro,
 * mascara e limite de linhas com desvio assincrono.
 */
class ExportPolicyTest {

    private final List<String> auditResults = new ArrayList<>();
    private final ExportAuditRecorder audit = (tenant, user, code, result, purpose) -> auditResults.add(result);
    private UUID enqueuedFile;
    // Fake HONESTO (correcao B5): guarda estado de verdade - enqueue cria
    // QUEUED, complete grava conteudo e marca READY, fail marca FAILED.
    // O fake anterior tinha complete() no-op e escondia que ninguem
    // entregava a exportacao enfileirada.
    private final Map<UUID, String> fileStatus = new java.util.HashMap<>();
    private final Map<UUID, byte[]> fileContent = new java.util.HashMap<>();
    private final GeneratedFileDelivery delivery = new GeneratedFileDelivery() {
        @Override
        public UUID enqueue(UUID tenantId, UUID userId, String resource, String fileName) {
            enqueuedFile = UUID.randomUUID();
            fileStatus.put(enqueuedFile, "QUEUED");
            return enqueuedFile;
        }

        @Override
        public void complete(UUID id, long rows, byte[] content) {
            fileStatus.put(id, "READY");
            fileContent.put(id, content);
        }

        @Override
        public void fail(UUID id, String error) {
            fileStatus.put(id, "FAILED");
        }
    };
    // Trilha de execucoes (G4) observavel: status inicial e desfechos.
    private final List<ReportExecution.Status> executionStarts = new ArrayList<>();
    private final List<ReportExecution.Status> executionFinishes = new ArrayList<>();
    private final ReportExecutionRecorder executions = new ReportExecutionRecorder() {
        @Override
        public UUID start(Start start) {
            executionStarts.add(start.status());
            return UUID.randomUUID();
        }

        @Override
        public void finish(UUID id, ReportExecution.Status status, Long rows, UUID fileId, String error) {
            executionFinishes.add(status);
        }
    };

    private ReportQueryExecutorPort executorReturning(List<Map<String, Object>> rows) {
        return (definition, query, user, timeout) ->
                new JdbcReportQueryExecutor.ExecutionResult(rows, false, ReportDataSourceRouter.Source.PRIMARY);
    }

    private ReportExportService service(List<Map<String, Object>> rows) {
        return new ReportExportService(executorReturning(rows), audit, delivery, executions,
                List.of(new CsvReportWriter(), new XlsxReportWriter(), new PdfReportWriter()));
    }

    private ReportUserContext user(Set<String> permissions, ReportUserContext.MaskCeiling ceiling) {
        return new ReportUserContext(UUID.randomUUID(), UUID.randomUUID(), permissions, ceiling, Set.of("FINANCEIRO"));
    }

    @Test
    void semPermissaoNegaERegistraDenied() {
        ReportDefinition definition = new ReportRegistry().find("RPT-001").orElseThrow();
        assertThatThrownBy(() -> service(List.of()).export(definition, ReportQuery.empty(),
                ExportFormat.CSV, user(Set.of(), ReportUserContext.MaskCeiling.FULL), false))
                .isInstanceOf(ExportDeniedException.class);
        assertThat(auditResults).containsExactly("DENIED");
    }

    @Test
    void exportacaoMascaraDocumentoPeloTetoERegistraFound() {
        ReportDefinition definition = new ReportRegistry().find("RPT-002").orElseThrow();
        List<Map<String, Object>> rows = List.of(Map.of(
                "payment_id", "p1", "data_pagamento", "2026-10-05", "canal", "PIX",
                "valor", new java.math.BigDecimal("75.25"), "documento_pagador", "12345678900",
                "mes_arrecadacao", "2026-10-01", "recebido_em", "2026-10-05T10:00:00"));
        var outcome = service(rows).export(definition, ReportQuery.empty(), ExportFormat.CSV,
                user(Set.of("reporting.rpt-002:export"), ReportUserContext.MaskCeiling.FULL), false);
        String csv = new String(outcome.content(), StandardCharsets.UTF_8);
        assertThat(csv).doesNotContain("12345678900");
        assertThat(csv).contains("***");
        assertThat(csv).contains("75.25");
        assertThat(auditResults).containsExactly("FOUND");
    }

    @Test
    void falhaDeLeituraNaoViraNotFoundERegistraExecucaoFalha() {
        // Correcao R4: PersonalDataAccessLog.Result (core) so aceita
        // FOUND/NOT_FOUND/DENIED/QUOTA_EXCEEDED - nao ha valor de erro.
        // SQLException NAO pode ser registrada como NOT_FOUND (mentira de
        // auditoria); o desfecho vai para report_executions como FAILED.
        ReportDefinition definition = new ReportRegistry().find("RPT-001").orElseThrow();
        ReportQueryExecutorPort failing = (def, query, user, timeout) -> {
            throw new java.sql.SQLException("falha simulada");
        };
        ReportExportService svc = new ReportExportService(failing, audit, delivery, executions,
                List.of(new CsvReportWriter()));
        assertThatThrownBy(() -> svc.export(definition, ReportQuery.empty(), ExportFormat.CSV,
                user(Set.of("reporting:export"), ReportUserContext.MaskCeiling.FULL), false))
                .isInstanceOf(IllegalStateException.class);
        assertThat(auditResults).doesNotContain("NOT_FOUND");
        assertThat(executionFinishes).containsExactly(ReportExecution.Status.FAILED);
    }

    @Test
    void acimaDoLimiteSincronoEnfileiraEmArquivosGerados() {
        ReportDefinition definition = new ReportRegistry().find("RPT-001").orElseThrow();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < ReportDefinition.SYNC_ROW_LIMIT + 1; i++) {
            rows.add(Map.of("data", "2026-10-05", "canal", "PIX", "pagamentos", 1L,
                    "valor_bruto", java.math.BigDecimal.ONE, "valor_liquido", java.math.BigDecimal.ONE,
                    "valor_retido", java.math.BigDecimal.ZERO, "tributo_segregado", java.math.BigDecimal.ZERO));
        }
        var outcome = service(rows).export(definition, ReportQuery.empty(), ExportFormat.CSV,
                user(Set.of("reporting:export"), ReportUserContext.MaskCeiling.UNMASKED), false);
        assertThat(outcome.isQueued()).isTrue();
        assertThat(outcome.generatedFileId()).isEqualTo(enqueuedFile);
        assertThat(auditResults).containsExactly("FOUND");
        // Estado honesto: o arquivo nasce QUEUED e a execucao fica QUEUED
        // em aberto - quem conclui e o worker (prova em
        // AsyncExportEndToEndTest). Nada de READY falso aqui.
        assertThat(fileStatus.get(enqueuedFile)).isEqualTo("QUEUED");
        assertThat(executionFinishes).containsExactly(ReportExecution.Status.QUEUED);
    }
}
