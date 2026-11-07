package br.com.ziipsan.reporting.schedule;

import br.com.ziipsan.reporting.domain.ReportExecutionRecorder;
import br.com.ziipsan.reporting.domain.ReportExecutionRepository;
import br.com.ziipsan.reporting.domain.ReportSchedule;
import br.com.ziipsan.reporting.domain.ReportScheduleRepository;
import br.com.ziipsan.reporting.export.CsvReportWriter;
import br.com.ziipsan.reporting.export.ExportAuditRecorder;
import br.com.ziipsan.reporting.export.GeneratedFileDelivery;
import br.com.ziipsan.reporting.export.ReportExportService;
import br.com.ziipsan.reporting.query.JdbcReportQueryExecutor;
import br.com.ziipsan.reporting.query.ReportDataSourceRouter;
import br.com.ziipsan.reporting.query.ReportQuery;
import br.com.ziipsan.reporting.query.ReportQueryJson;
import br.com.ziipsan.reporting.registry.ReportRegistry;
import br.com.ziipsan.reporting.spi.ReportUserContext;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Correcao G1: o worker agendado executa a query dos PARAMS GRAVADOS no
 * agendamento (filtros/agrupamento/ordenacao), nao ReportQuery.empty().
 */
class ReportWorkerTest {

    @Test
    void agendadoReconstroiQueryDosParamsGravados() {
        AtomicReference<ReportQuery> captured = new AtomicReference<>();
        var executor = new br.com.ziipsan.reporting.query.ReportQueryExecutorPort() {
            @Override
            public JdbcReportQueryExecutor.ExecutionResult execute(
                    br.com.ziipsan.reporting.registry.ReportDefinition definition, ReportQuery query,
                    ReportUserContext user, int timeout) {
                captured.set(query);
                return new JdbcReportQueryExecutor.ExecutionResult(List.of(), false,
                        ReportDataSourceRouter.Source.PRIMARY);
            }
        };
        ExportAuditRecorder audit = (tenant, user, code, result, purpose) -> {
        };
        GeneratedFileDelivery delivery = new GeneratedFileDelivery() {
            @Override
            public UUID enqueue(UUID tenantId, UUID userId, String resource, String fileName) {
                return UUID.randomUUID();
            }

            @Override
            public void complete(UUID id, long rows, byte[] content) {
            }

            @Override
            public void fail(UUID id, String error) {
            }
        };
        ReportExecutionRecorder executions = new ReportExecutionRecorder() {
            @Override
            public UUID start(Start start) {
                return UUID.randomUUID();
            }

            @Override
            public void finish(UUID id, br.com.ziipsan.reporting.domain.ReportExecution.Status status,
                               Long rows, UUID fileId, String error) {
            }
        };
        ReportExportService service = new ReportExportService(executor, audit, delivery, executions,
                List.of(new CsvReportWriter()));
        ReportRegistry registry = new ReportRegistry();
        UUID tenant = UUID.randomUUID();
        UUID creator = UUID.randomUUID();
        ReportWorker worker = new ReportWorker(mock(ReportScheduleRepository.class),
                mock(ReportExecutionRepository.class), executions, registry, service, delivery,
                schedule -> new ReportUserContext(schedule.getTenantId(), schedule.getCreatedBy(),
                        Set.of("reporting:export"), ReportUserContext.MaskCeiling.FULL, Set.of()),
                (tenantId, userId) -> {
                    throw new UnsupportedOperationException("nao usado neste teste");
                });

        ReportQuery expected = new ReportQuery(
                Map.of("data", ReportQuery.FilterValue.range("2026-10-01", "2026-10-31")),
                "valor_liquido", false, List.of("canal"));
        ReportSchedule schedule = new ReportSchedule();
        schedule.setId(UUID.randomUUID());
        schedule.setTenantId(tenant);
        schedule.setDefinitionCode("RPT-001");
        schedule.setCronExpression("0 6 * * *");
        schedule.setFormat("CSV");
        schedule.setParamsJson(ReportQueryJson.toJson(expected));
        schedule.setCreatedBy(creator);
        schedule.setNextRunAt(OffsetDateTime.now(ZoneOffset.UTC));

        worker.runOne(schedule, OffsetDateTime.now(ZoneOffset.UTC));

        assertThat(captured.get()).isEqualTo(expected);
        // E o agendamento avancou para a proxima execucao do cron.
        assertThat(schedule.getLastRunAt()).isNotNull();
        assertThat(schedule.getNextRunAt()).isAfter(schedule.getLastRunAt());
    }
}
