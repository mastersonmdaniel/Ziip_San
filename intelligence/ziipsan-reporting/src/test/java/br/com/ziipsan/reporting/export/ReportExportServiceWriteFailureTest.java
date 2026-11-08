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

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regressao N2 (recuperacao M3): uma falha DEPOIS que a execucao nasce
 * RUNNING - escrita (IOException ou RuntimeException do escritor),
 * RuntimeException do executor ou falha do enfileiramento - tem de
 * fechar a execucao em FAILED com a causa registrada. Antes da
 * correcao, finish(FAILED) so cobria o SQLException da leitura e a
 * execucao ficava RUNNING para sempre nestes caminhos.
 *
 * Recorder honesto, nos moldes de AsyncExportEndToEndTest /
 * ExportPolicyTest: materializa o status corrente como o JPA faria -
 * start grava o status inicial e finish SUBSTITUI status/erro da
 * mesma linha, de modo que o estado final observavel e o real.
 */
class ReportExportServiceWriteFailureTest {

    /** Recorder que materializa a linha de execucao (status + erro finais). */
    private static final class HonestRecorder implements ReportExecutionRecorder {
        private final Map<UUID, ReportExecution.Status> currentStatus = new HashMap<>();
        private final Map<UUID, String> currentError = new HashMap<>();
        private final List<ReportExecution.Status> finishes = new ArrayList<>();
        private UUID lastId;

        @Override
        public UUID start(Start start) {
            lastId = UUID.randomUUID();
            currentStatus.put(lastId, start.status());
            currentError.put(lastId, start.error());
            return lastId;
        }

        @Override
        public void finish(UUID id, ReportExecution.Status status, Long rowCount,
                           UUID generatedFileId, String error) {
            finishes.add(status);
            currentStatus.put(id, status);
            currentError.put(id, error);
        }

        ReportExecution.Status finalStatus() {
            return currentStatus.get(lastId);
        }

        String finalError() {
            return currentError.get(lastId);
        }
    }

    private final ExportAuditRecorder audit = (tenant, user, code, result, purpose) -> {
    };

    private final GeneratedFileDelivery deliveryOk = new GeneratedFileDelivery() {
        @Override
        public UUID enqueue(UUID tenantId, UUID userId, String resource, String fileName) {
            return UUID.randomUUID();
        }

        @Override
        public void complete(UUID id, long rowCount, byte[] content) {
        }

        @Override
        public void fail(UUID id, String error) {
        }
    };

    private ReportUserContext user() {
        return new ReportUserContext(UUID.randomUUID(), UUID.randomUUID(),
                Set.of("reporting:export"), ReportUserContext.MaskCeiling.UNMASKED, Set.of());
    }

    private Map<String, Object> oneRow() {
        return Map.of("data", "2026-10-05", "canal", "PIX", "pagamentos", 1L,
                "valor_bruto", BigDecimal.ONE, "valor_liquido", BigDecimal.ONE,
                "valor_retido", BigDecimal.ZERO, "tributo_segregado", BigDecimal.ZERO);
    }

    private ReportQueryExecutorPort executorReturning(List<Map<String, Object>> rows) {
        return (definition, query, u, timeout) ->
                new JdbcReportQueryExecutor.ExecutionResult(rows, false,
                        ReportDataSourceRouter.Source.PRIMARY);
    }

    private ReportDefinition definition() {
        return new ReportRegistry().find("RPT-001").orElseThrow();
    }

    @Test
    void falhaDeEscritaIoFechaExecucaoComoFailedComCausa() {
        HonestRecorder recorder = new HonestRecorder();
        ReportWriter failingWriter = new ReportWriter() {
            @Override
            public ExportFormat format() {
                return ExportFormat.CSV;
            }

            @Override
            public byte[] write(ReportDefinition def, List<Map<String, Object>> rows) throws IOException {
                throw new IOException("disco cheio simulado");
            }
        };
        ReportExportService service = new ReportExportService(
                executorReturning(List.of(oneRow())), audit, deliveryOk, recorder,
                List.of(failingWriter));

        assertThatThrownBy(() -> service.export(definition(), ReportQuery.empty(),
                ExportFormat.CSV, user(), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Falha ao escrever");

        assertThat(recorder.finalStatus())
                .isNotEqualTo(ReportExecution.Status.RUNNING)
                .isEqualTo(ReportExecution.Status.FAILED);
        assertThat(recorder.finishes).containsExactly(ReportExecution.Status.FAILED);
        assertThat(recorder.finalError())
                .contains("IOException")
                .contains("disco cheio simulado");
    }

    @Test
    void falhaDeEscritaRuntimeFechaExecucaoComoFailedComCausa() {
        HonestRecorder recorder = new HonestRecorder();
        ReportWriter failingWriter = new ReportWriter() {
            @Override
            public ExportFormat format() {
                return ExportFormat.CSV;
            }

            @Override
            public byte[] write(ReportDefinition def, List<Map<String, Object>> rows) {
                throw new IllegalArgumentException("estado invalido simulado");
            }
        };
        ReportExportService service = new ReportExportService(
                executorReturning(List.of(oneRow())), audit, deliveryOk, recorder,
                List.of(failingWriter));

        assertThatThrownBy(() -> service.export(definition(), ReportQuery.empty(),
                ExportFormat.CSV, user(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("estado invalido simulado");

        assertThat(recorder.finalStatus())
                .isNotEqualTo(ReportExecution.Status.RUNNING)
                .isEqualTo(ReportExecution.Status.FAILED);
        assertThat(recorder.finishes).containsExactly(ReportExecution.Status.FAILED);
        assertThat(recorder.finalError())
                .contains("IllegalArgumentException")
                .contains("estado invalido simulado");
    }

    @Test
    void falhaRuntimeDoExecutorFechaExecucaoComoFailedComCausa() {
        HonestRecorder recorder = new HonestRecorder();
        ReportQueryExecutorPort failingExecutor = (def, query, u, timeout) -> {
            throw new IllegalStateException("replica explodiu simulada");
        };
        ReportExportService service = new ReportExportService(
                failingExecutor, audit, deliveryOk, recorder, List.of(new CsvReportWriter()));

        assertThatThrownBy(() -> service.export(definition(), ReportQuery.empty(),
                ExportFormat.CSV, user(), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("replica explodiu simulada");

        assertThat(recorder.finalStatus())
                .isNotEqualTo(ReportExecution.Status.RUNNING)
                .isEqualTo(ReportExecution.Status.FAILED);
        assertThat(recorder.finishes).containsExactly(ReportExecution.Status.FAILED);
        assertThat(recorder.finalError())
                .contains("IllegalStateException")
                .contains("replica explodiu simulada");
    }

    @Test
    void falhaNoEnfileiramentoFechaExecucaoComoFailedComCausa() {
        HonestRecorder recorder = new HonestRecorder();
        GeneratedFileDelivery failingDelivery = new GeneratedFileDelivery() {
            @Override
            public UUID enqueue(UUID tenantId, UUID userId, String resource, String fileName) {
                throw new IllegalStateException("fila indisponivel simulada");
            }

            @Override
            public void complete(UUID id, long rowCount, byte[] content) {
            }

            @Override
            public void fail(UUID id, String error) {
            }
        };
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = oneRow();
        for (int i = 0; i < ReportDefinition.SYNC_ROW_LIMIT + 1; i++) {
            rows.add(row);
        }
        ReportExportService service = new ReportExportService(
                executorReturning(rows), audit, failingDelivery, recorder,
                List.of(new CsvReportWriter()));

        assertThatThrownBy(() -> service.export(definition(), ReportQuery.empty(),
                ExportFormat.CSV, user(), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fila indisponivel simulada");

        assertThat(recorder.finalStatus())
                .isNotEqualTo(ReportExecution.Status.RUNNING)
                .isEqualTo(ReportExecution.Status.FAILED);
        assertThat(recorder.finishes).containsExactly(ReportExecution.Status.FAILED);
        assertThat(recorder.finalError())
                .contains("IllegalStateException")
                .contains("fila indisponivel simulada");
    }
}
