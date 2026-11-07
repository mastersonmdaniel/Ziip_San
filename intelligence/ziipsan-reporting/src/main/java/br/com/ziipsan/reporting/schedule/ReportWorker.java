package br.com.ziipsan.reporting.schedule;

import br.com.ziipsan.reporting.domain.ReportExecution;
import br.com.ziipsan.reporting.domain.ReportExecutionRecorder;
import br.com.ziipsan.reporting.domain.ReportExecutionRepository;
import br.com.ziipsan.reporting.domain.ReportSchedule;
import br.com.ziipsan.reporting.domain.ReportScheduleRepository;
import br.com.ziipsan.reporting.export.ExportDeniedException;
import br.com.ziipsan.reporting.export.ExportFormat;
import br.com.ziipsan.reporting.export.GeneratedFileDelivery;
import br.com.ziipsan.reporting.export.ReportExportService;
import br.com.ziipsan.reporting.query.ReportQuery;
import br.com.ziipsan.reporting.query.ReportQueryJson;
import br.com.ziipsan.reporting.registry.ReportRegistry;
import br.com.ziipsan.reporting.spi.ReportUserContext;
import br.com.ziipsan.reporting.spi.ReportUserContextResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Worker dos relatorios: acorda por polling (mesmo padrao de fila do
 * projeto) e fecha os DOIS ciclos de entrega em Arquivos gerados
 * (generated_files):
 * <ol>
 *   <li><b>agendados</b> (report_schedules vencidos): gera pela regra unica
 *       da P1.12 com a query RECONSTRUIDA dos params gravados no
 *       agendamento (correcao G1 - antes executava ReportQuery.empty() e
 *       ignorava filtros/agrupamento) e com o contexto do CRIADOR;</li>
 *   <li><b>ad-hoc enfileirados</b> (report_executions QUEUED nao
 *       agendadas, da exportacao acima do limite sincrono): gera o
 *       conteudo com mascara e chama complete/fail da entrega (correcao
 *       B5 - antes ninguem consumia esta fila e o arquivo nunca saia de
 *       QUEUED).</li>
 * </ol>
 * Toda conclusao grava o desfecho na propria linha de report_executions
 * (correcao G4). Instanciado pela ReportingConfiguration; o polling so
 * roda com o @EnableScheduling do app (declarado no relatorio).
 */
public class ReportWorker {

    private static final Logger LOG = LoggerFactory.getLogger(ReportWorker.class);

    private final ReportScheduleRepository schedules;
    private final ReportExecutionRepository executions;
    private final ReportExecutionRecorder recorder;
    private final ReportRegistry registry;
    private final ReportExportService exportService;
    private final GeneratedFileDelivery delivery;
    private final ScheduleOwnerResolver ownerResolver;
    private final ReportUserContextResolver userContextResolver;

    public ReportWorker(ReportScheduleRepository schedules, ReportExecutionRepository executions,
                        ReportExecutionRecorder recorder, ReportRegistry registry,
                        ReportExportService exportService, GeneratedFileDelivery delivery,
                        ScheduleOwnerResolver ownerResolver,
                        ReportUserContextResolver userContextResolver) {
        this.schedules = schedules;
        this.executions = executions;
        this.recorder = recorder;
        this.registry = registry;
        this.exportService = exportService;
        this.delivery = delivery;
        this.ownerResolver = ownerResolver;
        this.userContextResolver = userContextResolver;
    }

    @Scheduled(fixedDelayString = "${ziipsan.reporting.worker-delay-ms:60000}")
    public void runDueSchedules() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<ReportSchedule> due = schedules
                .findByActiveTrueAndNextRunAtLessThanEqualOrderByNextRunAtAsc(now);
        for (ReportSchedule schedule : due) {
            runOne(schedule, now);
        }
    }

    @Scheduled(fixedDelayString = "${ziipsan.reporting.worker-delay-ms:60000}")
    public void runQueuedAdHoc() {
        List<ReportExecution> queued = executions
                .findByStatusAndScheduledFalseOrderByCreatedAtAsc(ReportExecution.Status.QUEUED);
        for (ReportExecution execution : queued) {
            runAdHoc(execution);
        }
    }

    void runOne(ReportSchedule schedule, OffsetDateTime now) {
        var definition = registry.find(schedule.getDefinitionCode(), schedule.getTenantId());
        if (definition.isEmpty()) {
            LOG.warn("Agendamento {} aponta para relatorio inexistente {}", schedule.getId(),
                    schedule.getDefinitionCode());
            advance(schedule, now);
            return;
        }
        ReportUserContext owner = ownerResolver.resolve(schedule);
        ExportFormat format = ExportFormat.valueOf(schedule.getFormat());
        UUID fileId = delivery.enqueue(schedule.getTenantId(), owner.userId(),
                "report:" + schedule.getDefinitionCode(),
                schedule.getDefinitionCode().toLowerCase() + "." + format.extension());
        try {
            // G1: a query e a dos params gravados (filtros, agrupamento,
            // ordenacao), nunca uma query vazia.
            ReportQuery query = ReportQueryJson.fromJson(schedule.getParamsJson());
            var outcome = exportService.export(definition.get(), query, format, owner, true);
            delivery.complete(fileId, outcome.rowCount(), outcome.content());
        } catch (RuntimeException e) {
            delivery.fail(fileId, e.getMessage());
            LOG.warn("Relatorio agendado {} falhou: {}", schedule.getDefinitionCode(), e.getMessage());
        }
        advance(schedule, now);
    }

    /** Conclusao de UMA exportacao ad-hoc enfileirada (correcao B5). */
    void runAdHoc(ReportExecution execution) {
        var definition = registry.find(execution.getDefinitionCode(), execution.getTenantId());
        if (definition.isEmpty()) {
            recorder.finish(execution.getId(), ReportExecution.Status.FAILED, null,
                    execution.getGeneratedFileId(), "relatorio inexistente: " + execution.getDefinitionCode());
            if (execution.getGeneratedFileId() != null) {
                delivery.fail(execution.getGeneratedFileId(),
                        "relatorio inexistente: " + execution.getDefinitionCode());
            }
            return;
        }
        ReportUserContext owner = userContextResolver.resolve(
                execution.getTenantId(), execution.getRequestedBy());
        try {
            ReportQuery query = ReportQueryJson.fromJson(execution.getParamsJson());
            var outcome = exportService.generate(definition.get(), query,
                    ExportFormat.valueOf(execution.getFormat()), owner, false);
            delivery.complete(execution.getGeneratedFileId(), outcome.rowCount(), outcome.content());
            recorder.finish(execution.getId(), ReportExecution.Status.READY,
                    outcome.rowCount(), execution.getGeneratedFileId(), null);
        } catch (ExportDeniedException e) {
            delivery.fail(execution.getGeneratedFileId(), e.getMessage());
            recorder.finish(execution.getId(), ReportExecution.Status.DENIED, null,
                    execution.getGeneratedFileId(), e.getMessage());
        } catch (RuntimeException e) {
            delivery.fail(execution.getGeneratedFileId(), e.getMessage());
            recorder.finish(execution.getId(), ReportExecution.Status.FAILED, null,
                    execution.getGeneratedFileId(), e.getMessage());
            LOG.warn("Exportacao ad-hoc {} falhou: {}", execution.getDefinitionCode(), e.getMessage());
        }
    }

    private void advance(ReportSchedule schedule, OffsetDateTime now) {
        CronSchedule cron = CronSchedule.parse(schedule.getCronExpression());
        ZonedDateTime next = cron.nextAfter(now.atZoneSameInstant(ZoneOffset.UTC));
        schedule.setLastRunAt(now);
        schedule.setNextRunAt(OffsetDateTime.ofInstant(next.toInstant(), ZoneOffset.UTC));
        schedules.save(schedule);
    }

    /** Resolve o contexto (permissoes/teto) do criador do agendamento. */
    public interface ScheduleOwnerResolver {
        ReportUserContext resolve(ReportSchedule schedule);
    }

    /** Resolvedor conservador padrao: sem fiacao de identidade, o contexto
     *  tem mascara TOTAL e nenhuma permissao - o agendado e negado e
     *  registrado em vez de vazar. A fiacao real substitui este bean. */
    public static ReportUserContext conservativeContext(ReportSchedule schedule) {
        return new ReportUserContext(schedule.getTenantId(), schedule.getCreatedBy(),
                Set.of(), ReportUserContext.MaskCeiling.FULL, Set.of());
    }
}
