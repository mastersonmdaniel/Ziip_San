package br.com.ziipsan.platform.engagement;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Metas de consumo da ED-101: definicao pelo usuario e acompanhamento.
 *
 * <p>Guardas das operacoes do usuario (definicao E acompanhamento),
 * nesta ordem: (1) modulo ligado no tenant
 * ({@code eng.peer_report_enabled} — ver {@link EngagementParameters});
 * (2) adesao voluntaria ativa do usuario para a unidade; (3) meta positiva
 * (no construtor de {@link ConsumptionGoal}). A definicao acontece no app
 * da ED-39 e no portal (P3.3): esta entrega e o BACKEND dessas telas — os
 * endpoints/telas sao fiacao de integracao nomeada no relatorio, nao
 * fingidos aqui.
 *
 * <p>Acompanhamento: telemetria da ED-17 quando houver agregado do
 * periodo; senao, o consumo faturado; nenhuma das duas = {@code NO_DATA}.
 */
public final class GoalService {

    private final EngagementParameters parameters;
    private final EngagementStores.EnrollmentStore enrollments;
    private final EngagementStores.GoalStore goals;
    private final ConsumptionPorts.BilledConsumptionPort billed;
    private final ConsumptionPorts.TelemetryConsumptionPort telemetry;

    public GoalService(EngagementParameters parameters,
                       EngagementStores.EnrollmentStore enrollments,
                       EngagementStores.GoalStore goals,
                       ConsumptionPorts.BilledConsumptionPort billed,
                       ConsumptionPorts.TelemetryConsumptionPort telemetry) {
        this.parameters = Objects.requireNonNull(parameters, "parametros sao obrigatorios");
        this.enrollments = Objects.requireNonNull(enrollments, "adesoes sao obrigatorias");
        this.goals = Objects.requireNonNull(goals, "metas sao obrigatorias");
        this.billed = Objects.requireNonNull(billed, "consumo faturado e obrigatorio");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetria e obrigatoria");
    }

    /** Portao comum: modulo ligado + adesao ativa. Devolve a adesao. */
    public UserEnrollment requireActiveEnrollment(UUID tenantId, UUID customerId, UUID unitId) {
        if (!parameters.peerReportEnabled(tenantId)) {
            throw new EngagementValidationException("FEATURE_DISABLED",
                    "eng.peer_report_enabled desligado para o tenant: modulo de engajamento indisponivel");
        }
        UserEnrollment enrollment = enrollments.find(tenantId, customerId, unitId)
                .orElseThrow(() -> new EngagementValidationException("NOT_ENROLLED",
                        "sem adesao voluntaria do usuario para a unidade"));
        if (!enrollment.active()) {
            throw new EngagementValidationException("ENROLLMENT_REVOKED",
                    "adesao do usuario revogada para a unidade");
        }
        return enrollment;
    }

    /** Define (ou redefine) a meta do usuario para a unidade no periodo. */
    public ConsumptionGoal defineGoal(UUID tenantId, UUID customerId, UUID unitId,
                                      YearMonth period, BigDecimal targetM3, Instant now) {
        requireActiveEnrollment(tenantId, customerId, unitId);
        ConsumptionGoal goal = new ConsumptionGoal(UUID.randomUUID(), tenantId, customerId,
                unitId, period, targetM3, now);
        goals.save(goal);
        return goal;
    }

    /**
     * Acompanhamento da meta gravada. Exige as MESMAS guardas da definicao
     * (modulo ligado + adesao voluntaria ativa do usuario, via
     * {@link #requireActiveEnrollment}) — correcao D1 da revisao da
     * ED-101: antes esta operacao nao recebia {@code customerId} e devolvia
     * o progresso sem guarda alguma. {@code periodClosed} e fato do ciclo
     * de faturamento informado por quem chama (a integracao o deriva do
     * fechamento do ciclo); este servico nao presume fechamento.
     */
    public GoalProgress progress(UUID tenantId, UUID customerId, UUID unitId,
                                 YearMonth period, boolean periodClosed) {
        requireActiveEnrollment(tenantId, customerId, unitId);
        ConsumptionGoal goal = goals.find(tenantId, unitId, period)
                .orElseThrow(() -> new EngagementValidationException("GOAL_NOT_FOUND",
                        "sem meta definida para a unidade no periodo " + period));
        Optional<BigDecimal> fromTelemetry = telemetry.telemetryM3(tenantId, unitId, period);
        if (fromTelemetry.isPresent()) {
            return GoalProgress.of(goal, fromTelemetry.get(), GoalProgress.Source.TELEMETRY, periodClosed);
        }
        Optional<BigDecimal> fromBilling = billed.billedM3(tenantId, unitId, period);
        if (fromBilling.isPresent()) {
            return GoalProgress.of(goal, fromBilling.get(), GoalProgress.Source.BILLED, periodClosed);
        }
        return GoalProgress.noData(goal, periodClosed);
    }
}
