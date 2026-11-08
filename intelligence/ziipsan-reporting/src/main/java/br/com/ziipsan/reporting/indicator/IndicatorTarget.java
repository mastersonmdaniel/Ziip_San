package br.com.ziipsan.reporting.indicator;

import br.com.ziipsan.core.parameters.ParameterVersionRule;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Meta do indicador por periodo e por area (aceite da P3.41).
 * {@code areaCode == null} e a meta geral do tenant no periodo; uma meta com
 * area declarada prevalece sobre a geral para aquela area. A vigencia da
 * meta e o proprio periodo, validada pelo motor da P0.19
 * ({@link ParameterVersionRule.Validity}): periodo invertido e recusado na
 * construcao, e metas sobrepostas para o mesmo indicador+area sao recusadas
 * pelo servico com {@code ParameterVersionRule.validateNoOverlap}.
 */
public record IndicatorTarget(String indicatorCode, LocalDate periodStart, LocalDate periodEnd,
                              String areaCode, BigDecimal targetValue,
                              BigDecimal yellowTolerancePercent, Direction direction) {

    public enum Direction {
        HIGHER_IS_BETTER,
        LOWER_IS_BETTER
    }

    public IndicatorTarget {
        if (indicatorCode == null || indicatorCode.isBlank()) {
            throw new IndicatorValidationException("TARGET_INDICATOR_REQUIRED",
                    "meta sem indicador");
        }
        if (targetValue == null) {
            throw new IndicatorValidationException("TARGET_VALUE_REQUIRED",
                    "valor da meta e obrigatorio para " + indicatorCode);
        }
        if (yellowTolerancePercent == null
                || yellowTolerancePercent.compareTo(BigDecimal.ZERO) < 0
                || yellowTolerancePercent.compareTo(new BigDecimal("100")) > 0) {
            throw new IndicatorValidationException("TARGET_TOLERANCE_INVALID",
                    "tolerancia amarela deve estar entre 0 e 100 (%), recebida: "
                            + yellowTolerancePercent);
        }
        if (direction == null) {
            throw new IndicatorValidationException("TARGET_DIRECTION_REQUIRED",
                    "direcao da meta e obrigatoria para " + indicatorCode);
        }
        // Valida o periodo pela regra de vigencia da P0.19 (from obrigatorio,
        // to nao anterior a from) a partir dos PARAMETROS do construtor: num
        // record, os campos so sao atribuidos ao fim do construtor compacto,
        // logo chamar validity() aqui leria periodStart/periodEnd ainda
        // nulos e toda meta seria recusada (correcao D1 da revisao
        // independente).
        new ParameterVersionRule.Validity(periodStart, periodEnd);
        // D5: a V15_2 declara indicator_targets.period_end NOT NULL. A
        // vigencia aberta da P0.19 (to nulo) vale para as formulas
        // (valid_to anulavel na V15_2), nao para metas — o dominio espelha
        // o NOT NULL da migration.
        if (periodEnd == null) {
            throw new IndicatorValidationException("TARGET_PERIOD_END_REQUIRED",
                    "fim do periodo da meta e obrigatorio para " + indicatorCode
                            + " (period_end NOT NULL na V15_2)");
        }
    }

    /** Vigencia desta meta como intervalo da P0.19. */
    public ParameterVersionRule.Validity validity() {
        return new ParameterVersionRule.Validity(periodStart, periodEnd);
    }

    /** A meta cobre esta data (inicio do periodo de apuracao)? */
    public boolean covers(LocalDate date) {
        return validity().covers(date);
    }
}
