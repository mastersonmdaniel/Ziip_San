package br.com.ziipsan.reporting.forecast;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Pedido de projecao (ED-100). Todas as entradas sao explicitas:
 * receita base mensal (a receita da base real — ex.: a receita vigente
 * da simulacao da P2.29), saldo de caixa inicial, estoque de recebiveis
 * por faixa, curva de recebimento e distribuicao de lag estimadas da
 * janela observada dos eventos da ED-32, e os parametros
 * {@code fc.*} do tenant. Ausencia de qualquer entrada e erro nomeado.
 */
public record ForecastRequest(UUID tenantId, YearMonth baseMonth, BigDecimal baseMonthlyBilled,
                              BigDecimal openingCashBalance, List<BracketExposure> openingReceivables,
                              CollectionCurve collectionCurve, LagDistribution lagDistribution,
                              ForecastParameters parameters, ForecastTechnique technique,
                              boolean mlApprovedByAdr0042) {

    public ForecastRequest {
        if (tenantId == null) {
            throw new ForecastValidationException("TENANT_REQUIRED", "tenant e obrigatorio");
        }
        if (baseMonth == null) {
            throw new ForecastValidationException("BASE_MONTH_REQUIRED", "mes-base da projecao e obrigatorio");
        }
        if (baseMonthlyBilled == null) {
            throw new ForecastValidationException("BASE_BILLING_REQUIRED",
                    "receita base mensal e obrigatoria (premissa sem fonte nao entra na projecao)");
        }
        if (baseMonthlyBilled.signum() < 0) {
            throw new ForecastValidationException("BASE_BILLING_NEGATIVE",
                    "receita base mensal negativa: " + baseMonthlyBilled);
        }
        ForecastMath.requireScale("receita base mensal", baseMonthlyBilled, 2);
        if (openingCashBalance == null) {
            throw new ForecastValidationException("OPENING_CASH_REQUIRED",
                    "saldo de caixa inicial e obrigatorio (pode ser negativo, nao pode ser ausente)");
        }
        ForecastMath.requireScale("saldo de caixa inicial", openingCashBalance, 2);
        if (openingReceivables == null) {
            throw new ForecastValidationException("OPENING_RECEIVABLES_REQUIRED",
                    "estoque de recebiveis por faixa e obrigatorio (lista vazia explicita vale)");
        }
        openingReceivables = List.copyOf(openingReceivables);
        if (collectionCurve == null) {
            throw new ForecastValidationException("CURVE_REQUIRED", "curva de recebimento e obrigatoria");
        }
        if (lagDistribution == null) {
            throw new ForecastValidationException("LAG_DISTRIBUTION_REQUIRED",
                    "distribuicao de lag e obrigatoria");
        }
        if (parameters == null) {
            throw new ForecastValidationException("PARAMETERS_REQUIRED",
                    "parametros fc.horizon_months/fc.scenarios sao obrigatorios");
        }
        if (technique == null) {
            throw new ForecastValidationException("TECHNIQUE_REQUIRED",
                    "tecnica da projecao e obrigatoria (declarada, nunca presumida)");
        }
    }
}
