package br.com.ziipsan.reporting.export;

import br.com.ziipsan.reporting.domain.ReportExecution;
import br.com.ziipsan.reporting.domain.ReportExecutionRecorder;
import br.com.ziipsan.reporting.query.JdbcReportQueryExecutor;
import br.com.ziipsan.reporting.query.ReportQuery;
import br.com.ziipsan.reporting.query.ReportQueryJson;
import br.com.ziipsan.reporting.registry.ReportDefinition;
import br.com.ziipsan.reporting.spi.ReportUserContext;

import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orquestra TODA geracao de relatorio pela regra unica da P1.12 (JOR-29):
 * <ol>
 *   <li>permissao {@code *:export} do recurso - sem ela, nega e registra DENIED;</li>
 *   <li>registro no personal_data_access_log (sucesso, negacao e enfileiramento);</li>
 *   <li>leitura na replica ou no primario com statement_timeout (router);</li>
 *   <li>mascara pelo teto do usuario em toda coluna pessoal - antes de escrever;</li>
 *   <li>limite de linhas: ate SYNC_ROW_LIMIT, sincrono; acima, enfileira em
 *       generated_files E em report_executions (QUEUED, com os params) - o
 *       ReportWorker consome a fila ad-hoc, gera o conteudo mascarado e
 *       chama complete/fail da entrega (correcao B5 pos-revisao: antes a
 *       exportacao enfileirada nunca era entregue por ninguem);</li>
 *   <li>trilha em report_executions: uma linha por geracao - negada,
 *       sincrona, enfileirada e a conclusao assincrona pelo worker
 *       (correcao G4 pos-revisao).</li>
 * </ol>
 */
public final class ReportExportService {

    private final br.com.ziipsan.reporting.query.ReportQueryExecutorPort executor;
    private final ExportAuditRecorder audit;
    private final GeneratedFileDelivery delivery;
    private final ReportExecutionRecorder executions;
    private final Map<ExportFormat, ReportWriter> writers;

    public ReportExportService(br.com.ziipsan.reporting.query.ReportQueryExecutorPort executor,
                               ExportAuditRecorder audit,
                               GeneratedFileDelivery delivery,
                               ReportExecutionRecorder executions,
                               List<ReportWriter> writers) {
        this.executor = executor;
        this.audit = audit;
        this.delivery = delivery;
        this.executions = executions;
        this.writers = new java.util.EnumMap<>(ExportFormat.class);
        writers.forEach(w -> this.writers.put(w.format(), w));
    }

    public ExportOutcome export(ReportDefinition definition, ReportQuery query, ExportFormat format,
                                ReportUserContext user, boolean scheduled) {
        int timeout = br.com.ziipsan.reporting.query.ReportDataSourceRouter.DEFAULT_STATEMENT_TIMEOUT_MS;
        if (!user.canExport(definition.requiredPermission())) {
            audit.recordExport(user.tenantId(), user.userId(), definition.code(), "DENIED",
                    "formato=" + format + "; agendado=" + scheduled);
            executions.start(new ReportExecutionRecorder.Start(user.tenantId(), user.userId(),
                    definition.code(), format.name(), ReportQueryJson.toJson(query), scheduled,
                    ReportExecution.Status.DENIED, "PRIMARY", timeout, null, null,
                    "sem a permissao " + definition.requiredPermission()));
            throw new ExportDeniedException(definition.code(), definition.requiredPermission());
        }
        UUID executionId = executions.start(new ReportExecutionRecorder.Start(user.tenantId(),
                user.userId(), definition.code(), format.name(), ReportQueryJson.toJson(query),
                scheduled, ReportExecution.Status.RUNNING, "PRIMARY", timeout, null, null, null));
        // CORRECAO N2 (recuperacao M3): a linha acima nasce RUNNING e TODA
        // falha posterior - leitura, mascara, enfileiramento ou escrita -
        // tem de fecha-la num estado terminal. Antes, finish(FAILED) so
        // cobria o SQLException da leitura; uma falha de escrita
        // (IOException do escritor -> IllegalStateException em
        // writeContent, RuntimeException do escritor/executor ou falha
        // de delivery.enqueue) propagava sem nenhum finish e a execucao
        // ficava RUNNING para sempre. A guarda abaixo fecha FAILED uma
        // unica vez, com a causa, e relanca a excecao original intacta.
        // Desfechos ja registrados (READY/QUEUED/FAILED da leitura) NAO
        // sao sobrescritos: outcomeRecorded bloqueia a dupla escrita.
        boolean outcomeRecorded = false;
        try {
            JdbcReportQueryExecutor.ExecutionResult result;
            try {
                result = executor.execute(definition, query, user, timeout);
            } catch (SQLException e) {
                // CORRECAO R4 (pos-revisao): falha de leitura NAO e NOT_FOUND.
                // Os desfechos aceitos de personal_data_access_log (entidade
                // real do core, PersonalDataAccessLog.Result) sao apenas
                // FOUND, NOT_FOUND, DENIED e QUOTA_EXCEEDED - NAO existe valor
                // de erro, e inventar um quebraria o CHECK/leitura do core.
                // A falha fica registrada onde ha vocabulario para ela:
                // report_executions com status FAILED. Declarado no relatorio.
                executions.finish(executionId, ReportExecution.Status.FAILED, null, null,
                        "falha de leitura: " + e.getClass().getSimpleName());
                outcomeRecorded = true;
                throw new IllegalStateException("Falha ao gerar " + definition.code(), e);
            }
            List<Map<String, Object>> masked = maskRows(definition, result.rows(), user);
            if (masked.size() > ReportDefinition.SYNC_ROW_LIMIT && !scheduled) {
                UUID fileId = delivery.enqueue(user.tenantId(), user.userId(),
                        "report:" + definition.code(),
                        definition.code().toLowerCase() + "." + format.extension());
                executions.finish(executionId, ReportExecution.Status.QUEUED,
                        (long) masked.size(), fileId, null);
                outcomeRecorded = true;
                audit.recordExport(user.tenantId(), user.userId(), definition.code(), "FOUND",
                        "assincrono; linhas=" + masked.size() + "; arquivo=" + fileId);
                return ExportOutcome.queued(fileId, masked.size(), result.source().name());
            }
            byte[] content = writeContent(definition, format, masked);
            executions.finish(executionId, ReportExecution.Status.READY, (long) masked.size(), null, null);
            outcomeRecorded = true;
            audit.recordExport(user.tenantId(), user.userId(), definition.code(), "FOUND",
                    "formato=" + format + "; linhas=" + masked.size() + "; fonte=" + result.source()
                            + "; truncado=" + result.truncated() + "; agendado=" + scheduled);
            return ExportOutcome.ready(content, format, masked.size(), result.truncated(), result.source().name());
        } catch (RuntimeException e) {
            if (!outcomeRecorded) {
                try {
                    executions.finish(executionId, ReportExecution.Status.FAILED, null, null,
                            failureDetail(e));
                } catch (RuntimeException finishFailure) {
                    // A tentativa de fechar FAILED nunca mascara a causa
                    // original: ela segue como suprimida da excecao real.
                    e.addSuppressed(finishFailure);
                }
            }
            throw e;
        }
    }

    /** Causa registrada no FAILED da correcao N2: tipo, mensagem e causa raiz. */
    private static String failureDetail(RuntimeException e) {
        StringBuilder detail = new StringBuilder("falha na execucao/escrita: ")
                .append(e.getClass().getSimpleName());
        if (e.getMessage() != null) {
            detail.append(": ").append(e.getMessage());
        }
        if (e.getCause() != null) {
            detail.append("; causa: ").append(e.getCause().getClass().getSimpleName());
            if (e.getCause().getMessage() != null) {
                detail.append(": ").append(e.getCause().getMessage());
            }
        }
        return detail.toString();
    }

    /**
     * Geracao SEM o desvio de limite e SEM abrir nova execucao: e o passo
     * que o worker usa para concluir uma execucao ja registrada (ad-hoc
     * enfileirada ou agendada). Permissao e mascara sao reaplicadas com o
     * contexto resolvido no momento da entrega - quem perdeu a permissao
     * nao recebe o arquivo.
     */
    public ExportOutcome generate(ReportDefinition definition, ReportQuery query, ExportFormat format,
                                  ReportUserContext user, boolean scheduled) {
        if (!user.canExport(definition.requiredPermission())) {
            audit.recordExport(user.tenantId(), user.userId(), definition.code(), "DENIED",
                    "formato=" + format + "; entrega assincrona/agendada");
            throw new ExportDeniedException(definition.code(), definition.requiredPermission());
        }
        JdbcReportQueryExecutor.ExecutionResult result;
        try {
            result = executor.execute(definition, query, user,
                    br.com.ziipsan.reporting.query.ReportDataSourceRouter.DEFAULT_STATEMENT_TIMEOUT_MS);
        } catch (SQLException e) {
            // Mesmo desfecho da exportacao sincrona (correcao R4): sem
            // NOT_FOUND falso; quem registra o FAILED e o worker, na
            // execucao que ele esta concluindo.
            throw new IllegalStateException("Falha ao gerar " + definition.code(), e);
        }
        List<Map<String, Object>> masked = maskRows(definition, result.rows(), user);
        byte[] content = writeContent(definition, format, masked);
        audit.recordExport(user.tenantId(), user.userId(), definition.code(), "FOUND",
                "formato=" + format + "; linhas=" + masked.size() + "; fonte=" + result.source()
                        + "; truncado=" + result.truncated() + "; agendado=" + scheduled);
        return ExportOutcome.ready(content, format, masked.size(), result.truncated(), result.source().name());
    }

    private byte[] writeContent(ReportDefinition definition, ExportFormat format,
                                List<Map<String, Object>> masked) {
        try {
            return writers.get(format).write(definition, masked);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao escrever " + format + " de " + definition.code(), e);
        }
    }

    /** Mascara aplicada linha a linha, coluna a coluna - ponto unico. */
    static List<Map<String, Object>> maskRows(ReportDefinition definition,
                                              List<Map<String, Object>> rows, ReportUserContext user) {
        return rows.stream().map(row -> {
            Map<String, Object> masked = new LinkedHashMap<>();
            for (var column : definition.columns()) {
                masked.put(column.name(),
                        PersonalDataMasker.mask(column, row.get(column.name()), user.maskCeiling()));
            }
            return masked;
        }).toList();
    }

    public record ExportOutcome(byte[] content, ExportFormat format, long rowCount, boolean truncated,
                                UUID generatedFileId, String dataSource) {
        static ExportOutcome ready(byte[] content, ExportFormat format, long rows, boolean truncated, String source) {
            return new ExportOutcome(content, format, rows, truncated, null, source);
        }

        static ExportOutcome queued(UUID fileId, long rows, String source) {
            return new ExportOutcome(null, null, rows, false, fileId, source);
        }

        public boolean isQueued() {
            return generatedFileId != null;
        }
    }
}
