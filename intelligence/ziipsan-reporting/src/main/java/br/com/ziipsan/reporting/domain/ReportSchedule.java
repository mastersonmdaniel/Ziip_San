package br.com.ziipsan.reporting.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** Agendamento de relatorio (V15_1), executado pelo worker da P3.22. */
@Entity
@Table(name = "report_schedules")
@Getter
@Setter
public class ReportSchedule extends TenantAwareEntity {

    @Column(name = "definition_code", nullable = false, length = 60)
    private String definitionCode;

    @Column(name = "cron_expression", nullable = false, length = 60)
    private String cronExpression;

    @Column(name = "format", nullable = false, length = 10)
    private String format;

    @Column(name = "params", columnDefinition = "jsonb")
    private String paramsJson;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "next_run_at", nullable = false)
    private OffsetDateTime nextRunAt;

    @Column(name = "last_run_at")
    private OffsetDateTime lastRunAt;
}
