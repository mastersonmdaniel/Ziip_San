package br.com.ziipsan.platform.engagement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

/**
 * Acompanhamento da meta do PROPRIO usuario (ED-101): consumo realizado x
 * meta, com a fonte declarada. {@code ratio} = realizado/meta (escala 4,
 * HALF_UP — convencao unica desta entrega); {@code WITHIN_GOAL} so e
 * definitivo com o periodo fechado (consumo faturado conhecido); em periodo
 * aberto, o mesmo estado e provisorio e a gamificacao nao o consome.
 */
public record GoalProgress(UUID tenantId, UUID unitId, YearMonth period,
                           BigDecimal targetM3, BigDecimal actualM3, BigDecimal ratio,
                           Source source, Status status, boolean periodClosed) {

    public enum Source {BILLED, TELEMETRY, NONE}

    public enum Status {WITHIN_GOAL, EXCEEDED, NO_DATA}

    public GoalProgress {
        Objects.requireNonNull(tenantId, "tenantId e obrigatorio");
        Objects.requireNonNull(unitId, "unitId e obrigatorio");
        Objects.requireNonNull(period, "period e obrigatorio");
        Objects.requireNonNull(targetM3, "targetM3 e obrigatorio");
        Objects.requireNonNull(source, "source e obrigatoria");
        Objects.requireNonNull(status, "status e obrigatorio");
        if (status == Status.NO_DATA) {
            if (actualM3 != null || ratio != null || source != Source.NONE) {
                throw new EngagementValidationException("PROGRESS_INCONSISTENT",
                        "NO_DATA exige actual/ratio nulos e fonte NONE");
            }
        } else {
            if (actualM3 == null || ratio == null || source == Source.NONE) {
                throw new EngagementValidationException("PROGRESS_INCONSISTENT",
                        "progresso com dado exige actual, ratio e fonte");
            }
        }
    }

    static GoalProgress noData(ConsumptionGoal goal, boolean periodClosed) {
        return new GoalProgress(goal.tenantId(), goal.unitId(), goal.period(),
                goal.targetM3(), null, null, Source.NONE, Status.NO_DATA, periodClosed);
    }

    static GoalProgress of(ConsumptionGoal goal, BigDecimal actualM3, Source source, boolean periodClosed) {
        BigDecimal ratio = actualM3.divide(goal.targetM3(), 4, RoundingMode.HALF_UP);
        Status status = actualM3.compareTo(goal.targetM3()) <= 0 ? Status.WITHIN_GOAL : Status.EXCEEDED;
        return new GoalProgress(goal.tenantId(), goal.unitId(), goal.period(),
                goal.targetM3(), actualM3, ratio, source, status, periodClosed);
    }
}
