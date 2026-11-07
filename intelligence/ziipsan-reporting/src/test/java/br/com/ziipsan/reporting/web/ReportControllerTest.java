package br.com.ziipsan.reporting.web;

import br.com.ziipsan.reporting.domain.ReportExecutionRecorder;
import br.com.ziipsan.reporting.export.CsvReportWriter;
import br.com.ziipsan.reporting.export.ExportAuditRecorder;
import br.com.ziipsan.reporting.export.ExportFormat;
import br.com.ziipsan.reporting.export.GeneratedFileDelivery;
import br.com.ziipsan.reporting.export.ReportExportService;
import br.com.ziipsan.reporting.query.JdbcReportQueryExecutor;
import br.com.ziipsan.reporting.query.ReportDataSourceRouter;
import br.com.ziipsan.reporting.query.ReportQuery;
import br.com.ziipsan.reporting.query.ReportQueryExecutorPort;
import br.com.ziipsan.reporting.registry.ReportRegistry;
import br.com.ziipsan.reporting.spi.ReportUserContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Correcao G2: o controller monta o ReportQuery dos parametros HTTP e
 * RECUSA exportacao sem o atributo de usuario - nunca fabrica contexto
 * (a versao anterior criava tenantId aleatorio e userId nulo).
 */
class ReportControllerTest {

    private final AtomicReference<ReportQuery> captured = new AtomicReference<>();
    private final ReportQueryExecutorPort executor = (definition, query, user, timeout) -> {
        captured.set(query);
        return new JdbcReportQueryExecutor.ExecutionResult(List.of(), false,
                ReportDataSourceRouter.Source.PRIMARY);
    };
    private final ExportAuditRecorder audit = (tenant, user, code, result, purpose) -> {
    };
    private final GeneratedFileDelivery delivery = new GeneratedFileDelivery() {
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
    private final ReportExecutionRecorder executions = new ReportExecutionRecorder() {
        @Override
        public UUID start(Start start) {
            return UUID.randomUUID();
        }

        @Override
        public void finish(UUID id, br.com.ziipsan.reporting.domain.ReportExecution.Status status,
                           Long rows, UUID fileId, String error) {
        }
    };
    private final ReportController controller = new ReportController(new ReportRegistry(),
            new ReportExportService(executor, audit, delivery, executions, List.of(new CsvReportWriter())));

    private MockHttpServletRequest requestWithUser() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("ziipsan.reportUser", new ReportUserContext(UUID.randomUUID(),
                UUID.randomUUID(), Set.of("reporting:export"),
                ReportUserContext.MaskCeiling.FULL, Set.of()));
        return request;
    }

    @Test
    void exportacaoMontaQueryDosParametrosHttp() {
        MockHttpServletRequest request = requestWithUser();
        request.addParameter("format", "CSV");
        request.addParameter("data_from", "2026-10-01");
        request.addParameter("data_to", "2026-10-31");
        request.addParameter("sort", "valor_liquido");
        request.addParameter("dir", "desc");
        request.addParameter("groupBy", "canal");
        var response = controller.export("RPT-001", ExportFormat.CSV, request);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        ReportQuery query = captured.get();
        assertThat(query.filters()).containsKey("data");
        assertThat(query.filters().get("data").from()).isEqualTo("2026-10-01");
        assertThat(query.filters().get("data").to()).isEqualTo("2026-10-31");
        assertThat(query.sortColumn()).isEqualTo("valor_liquido");
        assertThat(query.ascending()).isFalse();
        assertThat(query.groupBy()).containsExactly("canal");
    }

    @Test
    void semContextoDeUsuarioRecusaSemFabricar() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThatThrownBy(() -> controller.export("RPT-001", ExportFormat.CSV, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ziipsan.reportUser");
        assertThat(captured.get()).isNull();
    }
}
