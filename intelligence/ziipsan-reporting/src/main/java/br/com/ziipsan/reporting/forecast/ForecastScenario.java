package br.com.ziipsan.reporting.forecast;

import java.math.BigDecimal;

/**
 * Cenario de projecao (ED-100), como definido em {@code fc.scenarios}:
 * TODAS as premissas sao parametros explicitos do cenario — campo nulo e
 * erro nomeado, nunca constante escondida nem percentual inventado.
 *
 * <ul>
 *   <li>{@code tariffAdjustmentPercent}: reajuste tarifario em % sobre a
 *       receita base — o valor que a simulacao de impacto da P2.29 produz
 *       (receita proposta x vigente) traduzido a percentual por quem
 *       define o cenario;</li>
 *   <li>{@code monthlyGrowthPercent}: crescimento mensal da base em %
 *       (ligacoes/consumo); zero explicito = base constante;</li>
 *   <li>{@code socialTariffMonthlyDiscount}: desconto mensal da tarifa
 *       social em R$ (efeito medido na P2.29: desconto social da massa);</li>
 *   <li>{@code cashbackMonthlyAmount}: cashback mensal de CBS/IBS em R$
 *       (total por competencia da P2.34), que reduz a receita faturada
 *       como credito na fatura;</li>
 *   <li>{@code recoveryShift}: deslocamento da recuperacao em pontos de
 *       fracao (ex.: 0.05 = +5 p.p. na taxa de recebimento), o cenario de
 *       inadimplencia;</li>
 *   <li>{@code monthlyCashOutflow}: saida mensal de caixa em R$ para o
 *       saldo projetado; zero explicito vale.</li>
 * </ul>
 */
public record ForecastScenario(String name, BigDecimal tariffAdjustmentPercent, BigDecimal monthlyGrowthPercent,
                               BigDecimal socialTariffMonthlyDiscount, BigDecimal cashbackMonthlyAmount,
                               BigDecimal recoveryShift, BigDecimal monthlyCashOutflow) {

    public ForecastScenario {
        if (name == null || name.isBlank()) {
            throw new ForecastValidationException("SCENARIO_NAME_REQUIRED", "nome do cenario e obrigatorio");
        }
        requireParam(name, "tariffAdjustmentPercent", tariffAdjustmentPercent);
        requireParam(name, "monthlyGrowthPercent", monthlyGrowthPercent);
        requireParam(name, "socialTariffMonthlyDiscount", socialTariffMonthlyDiscount);
        requireParam(name, "cashbackMonthlyAmount", cashbackMonthlyAmount);
        requireParam(name, "recoveryShift", recoveryShift);
        requireParam(name, "monthlyCashOutflow", monthlyCashOutflow);
        if (tariffAdjustmentPercent.compareTo(new BigDecimal("-100")) <= 0) {
            throw new ForecastValidationException("SCENARIO_PARAM_INVALID",
                    "cenario " + name + ": tariffAdjustmentPercent deve ser maior que -100: "
                            + tariffAdjustmentPercent);
        }
        if (monthlyGrowthPercent.compareTo(new BigDecimal("-100")) <= 0) {
            throw new ForecastValidationException("SCENARIO_PARAM_INVALID",
                    "cenario " + name + ": monthlyGrowthPercent deve ser maior que -100: " + monthlyGrowthPercent);
        }
        if (socialTariffMonthlyDiscount.signum() < 0 || cashbackMonthlyAmount.signum() < 0
                || monthlyCashOutflow.signum() < 0) {
            throw new ForecastValidationException("SCENARIO_PARAM_INVALID",
                    "cenario " + name + ": desconto social, cashback e saida de caixa nao podem ser negativos");
        }
        ForecastMath.requireScale("tariffAdjustmentPercent do cenario " + name, tariffAdjustmentPercent, 4);
        ForecastMath.requireScale("monthlyGrowthPercent do cenario " + name, monthlyGrowthPercent, 4);
        ForecastMath.requireScale("socialTariffMonthlyDiscount do cenario " + name, socialTariffMonthlyDiscount, 2);
        ForecastMath.requireScale("cashbackMonthlyAmount do cenario " + name, cashbackMonthlyAmount, 2);
        ForecastMath.requireScale("monthlyCashOutflow do cenario " + name, monthlyCashOutflow, 2);
        ForecastMath.requireScale("recoveryShift do cenario " + name, recoveryShift, 6);
        if (recoveryShift.compareTo(new BigDecimal("-1")) < 0 || recoveryShift.compareTo(BigDecimal.ONE) > 0) {
            throw new ForecastValidationException("SCENARIO_PARAM_INVALID",
                    "cenario " + name + ": recoveryShift fora de [-1, 1]: " + recoveryShift);
        }
    }

    private static void requireParam(String scenario, String field, BigDecimal value) {
        if (value == null) {
            throw new ForecastValidationException("SCENARIO_PARAM_REQUIRED",
                    "cenario " + scenario + ": parametro " + field + " de fc.scenarios ausente"
                            + " (premissa sem valor declarado e erro, nunca presumida)");
        }
    }

    /** Deducoes mensais sobre a receita bruta: tarifa social + cashback. */
    public BigDecimal monthlyDeductions() {
        return socialTariffMonthlyDiscount.add(cashbackMonthlyAmount);
    }
}
