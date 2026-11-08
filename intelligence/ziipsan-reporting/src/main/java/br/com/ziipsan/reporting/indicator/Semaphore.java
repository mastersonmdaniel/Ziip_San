package br.com.ziipsan.reporting.indicator;

import java.math.BigDecimal;

/** Semaforo do indicador frente a meta (P3.41). NO_TARGET nao e verde: e a ausencia declarada de meta. */
public enum Semaphore {
    GREEN,
    YELLOW,
    RED,
    NO_TARGET;

    /**
     * Faixa do semaforo: valor na meta ou alem dela (na direcao da meta) =
     * GREEN; dentro da tolerancia amarela declarada na meta = YELLOW; fora
     * = RED. A tolerancia e percentual declarado da meta
     * ({@code yellowTolerancePercent}), com vigencia propria da meta —
     * nenhum percentual e fixo no codigo.
     */
    public static Semaphore evaluate(BigDecimal value, IndicatorTarget target) {
        if (target == null) {
            return NO_TARGET;
        }
        BigDecimal goal = target.targetValue();
        BigDecimal tolerance = goal.abs()
                .multiply(target.yellowTolerancePercent())
                .movePointLeft(2);
        return switch (target.direction()) {
            case HIGHER_IS_BETTER -> {
                if (value.compareTo(goal) >= 0) {
                    yield GREEN;
                }
                yield value.compareTo(goal.subtract(tolerance)) >= 0 ? YELLOW : RED;
            }
            case LOWER_IS_BETTER -> {
                if (value.compareTo(goal) <= 0) {
                    yield GREEN;
                }
                yield value.compareTo(goal.add(tolerance)) <= 0 ? YELLOW : RED;
            }
        };
    }
}
