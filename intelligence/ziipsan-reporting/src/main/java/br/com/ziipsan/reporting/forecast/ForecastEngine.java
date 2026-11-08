package br.com.ziipsan.reporting.forecast;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Motor da projecao de receita, arrecadacao e caixa por cenario (ED-100,
 * GES-10). Classe pura: nao le banco, nao grava nada — entradas sao os
 * valores de {@link ForecastRequest} e a saida e o {@link ForecastResult}.
 * Persistir a execucao e alimentar os paineis do BI da ED-47 e fiacao de
 * integracao sobre as tabelas/views do SQL pendente desta entrega.
 *
 * <p><b>Modelo (tecnica simples, recalculavel a mao):</b>
 * <ol>
 *   <li>Receita bruta do mes h: {@code base x (1 + reajuste/100) x
 *       (1 + crescimento/100)^(h-1)} — cadeia sobre o valor nao
 *       arredondado; o mes e publicado arredondado a centavos.</li>
 *   <li>Receita liquida: bruta {@code -} desconto da tarifa social
 *       {@code -} cashback de CBS/IBS (deducoes mensais do cenario;
 *       deducao maior que a bruta e erro nomeado).</li>
 *   <li>Arrecadacao do faturamento novo no mes h:
 *       {@code soma_{lag=0..h-1} liquida(h-lag) x fracaoAjustada(lag)},
 *       com a distribuicao de lag ajustada pelo deslocamento de
 *       inadimplencia do cenario ({@link LagDistribution#adjusted}).</li>
 *   <li>Arrecadacao do estoque inicial: o total esperado
 *       {@code soma(exposicao_faixa x recuperacaoAjustada_faixa)} e
 *       distribuido pelos meses 1..horizonte na proporcao das fracoes
 *       ajustadas de lag 1..horizonte (normalizadas), com o residuo de
 *       arredondamento reconciliado no ultimo mes — a soma dos meses e
 *       exatamente o total esperado.</li>
 *   <li>Caixa: saldo inicial {@code +} soma(arrecadacao {@code -} saida
 *       mensal do cenario), acumulado mes a mes.</li>
 * </ol>
 * Dinheiro e arredondado por mes (escala 2, HALF_UP); taxas tem escala 6
 * ({@link ForecastMath}). Aprendizado de maquina: recusado — sem o
 * ADR-0042 por {@code ML_REQUIRES_ADR0042}; com ele, por nao implementado
 * nesta entrega ({@code ML_NOT_IMPLEMENTED}).
 */
public final class ForecastEngine {

    /** Linha mensal de um cenario: receita, arrecadacao e caixa do mes. */
    public record MonthlyForecast(YearMonth month, BigDecimal grossBilled, BigDecimal socialTariffDiscount,
                                  BigDecimal cashbackAmount, BigDecimal netBilled,
                                  BigDecimal collectionFromNewBilling, BigDecimal collectionFromOpeningStock,
                                  BigDecimal totalCollection, BigDecimal cashOutflow, BigDecimal cashBalance) {
    }

    /** Projecao completa de um cenario no horizonte. */
    public record ScenarioForecast(String scenarioName, List<MonthlyForecast> months,
                                   BigDecimal totalGrossBilled, BigDecimal totalNetBilled,
                                   BigDecimal totalCollection, BigDecimal finalCashBalance) {
        public ScenarioForecast {
            months = List.copyOf(months);
        }
    }

    /** Resultado: curva de perda (para a ED-32) + projecao por cenario. */
    public record ForecastResult(java.util.UUID tenantId, YearMonth baseMonth, int horizonMonths,
                                 ForecastTechnique technique, LossCurve lossCurve,
                                 List<ScenarioForecast> scenarios) {
        public ForecastResult {
            scenarios = List.copyOf(scenarios);
        }
    }

    private ForecastEngine() {
    }

    public static ForecastResult project(ForecastRequest request) {
        if (request == null) {
            throw new ForecastValidationException("REQUEST_REQUIRED", "pedido de projecao e obrigatorio");
        }
        if (request.technique() == ForecastTechnique.MACHINE_LEARNING) {
            if (!request.mlApprovedByAdr0042()) {
                throw new ForecastValidationException("ML_REQUIRES_ADR0042",
                        "aprendizado de maquina so sob o ADR-0042: tecnica recusada nesta projecao");
            }
            throw new ForecastValidationException("ML_NOT_IMPLEMENTED",
                    "nenhum modelo de aprendizado de maquina foi entregue nesta tarefa (ED-100);"
                            + " a tecnica implementada e WEIGHTED_AVERAGE");
        }
        LossCurve lossCurve = LossCurve.fromCurve(request.collectionCurve(), request.openingReceivables());
        List<ScenarioForecast> scenarios = new ArrayList<>();
        for (ForecastScenario scenario : request.parameters().scenarios()) {
            scenarios.add(projectScenario(request, scenario));
        }
        return new ForecastResult(request.tenantId(), request.baseMonth(),
                request.parameters().horizonMonths(), request.technique(), lossCurve, scenarios);
    }

    private static ScenarioForecast projectScenario(ForecastRequest request, ForecastScenario scenario) {
        int horizon = request.parameters().horizonMonths();
        LagDistribution adjustedLag = request.lagDistribution().adjusted(scenario.recoveryShift());

        // Estoque inicial: total esperado e sua distribuicao no tempo.
        BigDecimal stockExpected = BigDecimal.ZERO;
        for (BracketExposure exposure : request.openingReceivables()) {
            BigDecimal rate = request.collectionCurve()
                    .adjustedRecoveryRate(exposure.bracketCode(), scenario.recoveryShift());
            stockExpected = stockExpected.add(exposure.exposure().multiply(rate));
        }
        // Total do estoque em dinheiro (escala 2): a distribuicao mensal
        // reconcilia o residuo no ultimo mes (correcao pos-revisao), para
        // a soma dos meses ser exatamente este total, sem deriva de
        // arredondamento.
        BigDecimal stockTotal = ForecastMath.money(stockExpected);
        BigDecimal lagDenominator = BigDecimal.ZERO;
        for (int h = 1; h <= horizon; h++) {
            lagDenominator = lagDenominator.add(adjustedLag.fractionAt(h));
        }
        if (stockExpected.signum() > 0 && lagDenominator.signum() == 0) {
            throw new ForecastValidationException("STOCK_TIMING_UNDEFINED",
                    "cenario " + scenario.name() + ": estoque esperado de " + stockExpected
                            + " sem fracao de lag em 1.." + horizon + " para distribui-lo no tempo");
        }

        BigDecimal readjustFactor = BigDecimal.ONE.add(scenario.tariffAdjustmentPercent().movePointLeft(2));
        BigDecimal growthFactor = BigDecimal.ONE.add(scenario.monthlyGrowthPercent().movePointLeft(2));
        BigDecimal deductions = scenario.monthlyDeductions();

        List<BigDecimal> netByMonth = new ArrayList<>();
        List<MonthlyForecast> months = new ArrayList<>();
        BigDecimal rawGross = null;
        BigDecimal cash = request.openingCashBalance();
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        BigDecimal totalCollection = BigDecimal.ZERO;
        BigDecimal stockDistributed = BigDecimal.ZERO;
        for (int h = 1; h <= horizon; h++) {
            rawGross = (h == 1)
                    ? request.baseMonthlyBilled().multiply(readjustFactor)
                    : rawGross.multiply(growthFactor);
            BigDecimal gross = ForecastMath.money(rawGross);
            if (deductions.compareTo(gross) > 0) {
                throw new ForecastValidationException("SCENARIO_DEDUCTIONS_EXCEED_BILLING",
                        "cenario " + scenario.name() + ", mes " + h + ": deducoes " + deductions
                                + " maiores que a receita bruta " + gross);
            }
            BigDecimal net = gross.subtract(deductions);
            netByMonth.add(net);

            BigDecimal fromNew = BigDecimal.ZERO;
            for (int lag = 0; lag <= h - 1; lag++) {
                fromNew = fromNew.add(netByMonth.get(h - 1 - lag).multiply(adjustedLag.fractionAt(lag)));
            }
            fromNew = ForecastMath.money(fromNew);

            BigDecimal fromStock = BigDecimal.ZERO;
            if (stockTotal.signum() > 0) {
                if (h < horizon) {
                    BigDecimal ratio = ForecastMath.rate(adjustedLag.fractionAt(h), lagDenominator);
                    fromStock = ForecastMath.money(stockTotal.multiply(ratio));
                    stockDistributed = stockDistributed.add(fromStock);
                } else {
                    // Ultimo mes: residuo da reconciliacao (ver acima).
                    fromStock = stockTotal.subtract(stockDistributed);
                }
            }
            BigDecimal collection = fromNew.add(fromStock);
            cash = cash.add(collection).subtract(scenario.monthlyCashOutflow());
            totalGross = totalGross.add(gross);
            totalNet = totalNet.add(net);
            totalCollection = totalCollection.add(collection);
            months.add(new MonthlyForecast(request.baseMonth().plusMonths(h - 1L), gross,
                    scenario.socialTariffMonthlyDiscount(), scenario.cashbackMonthlyAmount(), net,
                    fromNew, fromStock, collection, scenario.monthlyCashOutflow(), cash));
        }
        return new ScenarioForecast(scenario.name(), months, totalGross, totalNet, totalCollection, cash);
    }
}
