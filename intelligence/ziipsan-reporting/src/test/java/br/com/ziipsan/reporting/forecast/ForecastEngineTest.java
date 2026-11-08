package br.com.ziipsan.reporting.forecast;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static br.com.ziipsan.reporting.forecast.SyntheticForecastBase.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Recalculo a mao do cenario BASE (horizonte 3, base 10.000,00/mes,
 * caixa inicial 1.000,00, saida 500,00/mes, estoque ATE_30 2.000,00 +
 * D31_60 1.000,00):
 * <pre>
 *   estoque esperado = 2.000x0,90 + 1.000x0,70 = 2.500,00
 *   pesos do estoque (lags 1..3 = 0,20/0,10/0,05; soma 0,35):
 *     mes 1: 2.500 x 0,571429 = 1.428,57
 *     mes 2: 2.500 x 0,285714 =   714,29
 *     mes 3: 2.500 x 0,142857 =   357,14     (soma = 2.500,00)
 *   arrecadacao do faturamento novo (liquida 10.000,00/mes):
 *     mes 1: 10.000x0,40 = 4.000,00
 *     mes 2: 4.000 + 10.000x0,20 = 6.000,00
 *     mes 3: 4.000 + 2.000 + 10.000x0,10 = 7.000,00
 *   caixa: 1.000 -> 5.928,57 -> 12.142,86 -> 19.000,00
 * </pre>
 */
class ForecastEngineTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final YearMonth BASE_MONTH = YearMonth.of(2027, 1);

    private static ForecastScenario scenario(String name, String adjustment, String growth,
                                             String social, String cashback, String shift, String outflow) {
        return new ForecastScenario(name, bd(adjustment), bd(growth), bd(social), bd(cashback), bd(shift), bd(outflow));
    }

    private static ForecastRequest request(List<ForecastScenario> scenarios) {
        return new ForecastRequest(TENANT, BASE_MONTH, bd("10000.00"), bd("1000.00"),
                List.of(new BracketExposure("ATE_30", bd("2000.00")),
                        new BracketExposure("D31_60", bd("1000.00"))),
                SyntheticForecastBase.curve(), SyntheticForecastBase.lagDistribution(),
                new ForecastParameters(3, scenarios),
                ForecastTechnique.WEIGHTED_AVERAGE, false);
    }

    @Test
    void projetaReceitaArrecadacaoECaixaDoCenarioBase() {
        ForecastEngine.ForecastResult result = ForecastEngine.project(request(List.of(
                scenario("BASE", "0", "0", "0.00", "0.00", "0", "500.00"))));

        ForecastEngine.ScenarioForecast base = result.scenarios().get(0);
        assertThat(base.months()).hasSize(3);
        assertThat(base.months().get(0).month()).isEqualTo(YearMonth.of(2027, 1));
        assertThat(base.months().get(0).grossBilled()).isEqualByComparingTo("10000.00");
        assertThat(base.months().get(0).netBilled()).isEqualByComparingTo("10000.00");
        assertThat(base.months().get(0).collectionFromNewBilling()).isEqualByComparingTo("4000.00");
        assertThat(base.months().get(0).collectionFromOpeningStock()).isEqualByComparingTo("1428.57");
        assertThat(base.months().get(0).totalCollection()).isEqualByComparingTo("5428.57");
        assertThat(base.months().get(0).cashBalance()).isEqualByComparingTo("5928.57");

        assertThat(base.months().get(1).collectionFromNewBilling()).isEqualByComparingTo("6000.00");
        assertThat(base.months().get(1).collectionFromOpeningStock()).isEqualByComparingTo("714.29");
        assertThat(base.months().get(1).totalCollection()).isEqualByComparingTo("6714.29");
        assertThat(base.months().get(1).cashBalance()).isEqualByComparingTo("12142.86");

        assertThat(base.months().get(2).collectionFromNewBilling()).isEqualByComparingTo("7000.00");
        assertThat(base.months().get(2).collectionFromOpeningStock()).isEqualByComparingTo("357.14");
        assertThat(base.months().get(2).totalCollection()).isEqualByComparingTo("7357.14");
        assertThat(base.months().get(2).cashBalance()).isEqualByComparingTo("19000.00");

        assertThat(base.totalGrossBilled()).isEqualByComparingTo("30000.00");
        assertThat(base.totalCollection()).isEqualByComparingTo("19500.00");
        assertThat(base.finalCashBalance()).isEqualByComparingTo("19000.00");
        // Curva de perda do resultado: 2.000x0,10 + 1.000x0,30 = 500,00
        assertThat(result.lossCurve().provisionAmount()).isEqualByComparingTo("500.00");
    }

    @Test
    void cenarioDeReajusteComTarifaSocialECashback() {
        ForecastEngine.ForecastResult result = ForecastEngine.project(request(List.of(
                scenario("REAJUSTE", "10", "0", "500.00", "300.00", "0.05", "500.00"))));

        ForecastEngine.MonthlyForecast m1 = result.scenarios().get(0).months().get(0);
        // bruta: 10.000 x 1,10 = 11.000; liquida: 11.000 - 500 - 300 = 10.200
        assertThat(m1.grossBilled()).isEqualByComparingTo("11000.00");
        assertThat(m1.socialTariffDiscount()).isEqualByComparingTo("500.00");
        assertThat(m1.cashbackAmount()).isEqualByComparingTo("300.00");
        assertThat(m1.netBilled()).isEqualByComparingTo("10200.00");
        assertThat(result.scenarios().get(0).totalGrossBilled()).isEqualByComparingTo("33000.00");
        // Deslocamento +0,05 ponta a ponta (correcao pos-revisao):
        // fracao ajustada do lag 0 = 0,426667 -> 10.200 x 0,426667 = 4.352,00
        assertThat(m1.collectionFromNewBilling()).isEqualByComparingTo("4352.00");
        // Estoque ajustado: 2.000x0,95 + 1.000x0,75 = 2.650,00, distribuido
        // pelos lags ajustados 1..3 (0,213333/0,106667/0,053333; soma 0,373333)
        // com residuo reconciliado no ultimo mes: soma exata de 2.650,00.
        var months = result.scenarios().get(0).months();
        assertThat(months.get(0).collectionFromOpeningStock()).isEqualByComparingTo("1514.28");
        assertThat(months.get(1).collectionFromOpeningStock()).isEqualByComparingTo("757.14");
        assertThat(months.get(2).collectionFromOpeningStock()).isEqualByComparingTo("378.58");
        assertThat(months.stream().map(ForecastEngine.MonthlyForecast::collectionFromOpeningStock)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add))
                .isEqualByComparingTo("2650.00");
    }

    @Test
    void cenarioDeCrescimentoCompoeAReceita() {
        ForecastEngine.ForecastResult result = ForecastEngine.project(request(List.of(
                scenario("CRESCIMENTO", "0", "10", "0.00", "0.00", "0", "0.00"))));

        List<ForecastEngine.MonthlyForecast> months = result.scenarios().get(0).months();
        assertThat(months.get(0).grossBilled()).isEqualByComparingTo("10000.00");
        assertThat(months.get(1).grossBilled()).isEqualByComparingTo("11000.00");
        assertThat(months.get(2).grossBilled()).isEqualByComparingTo("12100.00");
    }

    @Test
    void deducoesMaioresQueAReceitaSaoRecusadas() {
        assertThatThrownBy(() -> ForecastEngine.project(request(List.of(
                scenario("IMPOSSIVEL", "0", "0", "9000.00", "2000.00", "0", "0.00")))))
                .isInstanceOf(ForecastValidationException.class)
                .hasMessageContaining("SCENARIO_DEDUCTIONS_EXCEED_BILLING");
    }

    @Test
    void aprendizadoDeMaquinaExigeAdr0042ENaoEstaImplementado() {
        ForecastRequest mlSemAdr = new ForecastRequest(TENANT, BASE_MONTH, bd("10000.00"), bd("1000.00"),
                List.of(), SyntheticForecastBase.curve(), SyntheticForecastBase.lagDistribution(),
                new ForecastParameters(3, List.of(scenario("BASE", "0", "0", "0.00", "0.00", "0", "0.00"))),
                ForecastTechnique.MACHINE_LEARNING, false);
        assertThatThrownBy(() -> ForecastEngine.project(mlSemAdr))
                .isInstanceOf(ForecastValidationException.class)
                .hasMessageContaining("ML_REQUIRES_ADR0042");

        ForecastRequest mlComAdr = new ForecastRequest(TENANT, BASE_MONTH, bd("10000.00"), bd("1000.00"),
                List.of(), SyntheticForecastBase.curve(), SyntheticForecastBase.lagDistribution(),
                new ForecastParameters(3, List.of(scenario("BASE", "0", "0", "0.00", "0.00", "0", "0.00"))),
                ForecastTechnique.MACHINE_LEARNING, true);
        assertThatThrownBy(() -> ForecastEngine.project(mlComAdr))
                .isInstanceOf(ForecastValidationException.class)
                .hasMessageContaining("ML_NOT_IMPLEMENTED");
    }

    @Test
    void premissaMonetariaComTresCasasERecusada() {
        // Correcao pos-revisao: escala monetaria e no maximo 2 (NUMERIC(16,2)).
        assertThatThrownBy(() -> scenario("X", "0", "0", "1.234", "0.00", "0", "0.00"))
                .isInstanceOf(ForecastValidationException.class)
                .hasMessageContaining("VALUE_SCALE");
    }

    @Test
    void premissasAusentesSaoErrosNomeados() {
        assertThatThrownBy(() -> new ForecastRequest(TENANT, BASE_MONTH, null, bd("1000.00"),
                List.of(), SyntheticForecastBase.curve(), SyntheticForecastBase.lagDistribution(),
                new ForecastParameters(3, List.of(scenario("BASE", "0", "0", "0.00", "0.00", "0", "0.00"))),
                ForecastTechnique.WEIGHTED_AVERAGE, false))
                .isInstanceOf(ForecastValidationException.class)
                .hasMessageContaining("BASE_BILLING_REQUIRED");
    }
}
