package br.com.ziipsan.reporting.schedule;

import br.com.ziipsan.reporting.domain.ReportSchedule;
import br.com.ziipsan.reporting.domain.ReportScheduleRepository;
import br.com.ziipsan.reporting.export.ExportFormat;
import br.com.ziipsan.reporting.query.ReportQuery;
import br.com.ziipsan.reporting.registry.ReportRegistry;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Cria agendamentos validando definicao, cron e formato antes de gravar.
 *  Instanciado pela ReportingConfiguration (correcao R1). */
public class ReportScheduleService {

    private final ReportScheduleRepository repository;
    private final ReportRegistry registry;

    public ReportScheduleService(ReportScheduleRepository repository, ReportRegistry registry) {
        this.repository = repository;
        this.registry = registry;
    }

    @Transactional
    public ReportSchedule create(UUID tenantId, String definitionCode, String cron, ExportFormat format,
                                 ReportQuery query, UUID createdBy) {
        registry.find(definitionCode, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Relatorio desconhecido: " + definitionCode));
        CronSchedule schedule = CronSchedule.parse(cron);
        ReportSchedule entity = new ReportSchedule();
        entity.setTenantId(tenantId);
        entity.setDefinitionCode(definitionCode);
        entity.setCronExpression(cron);
        entity.setFormat(format.name());
        // Params completos (filtros, agrupamento E ordenacao) no formato
        // do ReportQueryJson - o worker reconstrói a query deles (G1).
        entity.setParamsJson(br.com.ziipsan.reporting.query.ReportQueryJson.toJson(query));
        entity.setActive(true);
        entity.setNextRunAt(OffsetDateTime.ofInstant(
                schedule.nextAfter(java.time.ZonedDateTime.now(ZoneOffset.UTC)).toInstant(), ZoneOffset.UTC));
        entity.setCreatedBy(createdBy);
        return repository.save(entity);
    }
}
