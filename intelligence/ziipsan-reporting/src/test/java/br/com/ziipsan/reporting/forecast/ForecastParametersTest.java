package br.com.ziipsan.reporting.forecast;

import org.junit.jupiter.api.Test;

import static br.com.ziipsan.reporting.forecast.SyntheticForecastBase.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ForecastParametersTest {

    private static final String SCENARIOS_JSON = """
            {
              "BASE": {
                "tariffAdjustmentPercent": "0",
                "monthlyGrowthPercent": "0",
                "socialTariffMonthlyDiscount": "0.00",
                "cashbackMonthlyAmount": "0.00",
                "recoveryShift": "0",
                "monthlyCashOutflow": "500.00"
              },
              "REAJUSTE": {
                "tariffAdjustmentPercent": "10",
                "monthlyGrowthPercent": "0",
                "socialTariffMonthlyDiscount": "500.00",
                "cashbackMonthlyAmount": "300.00",
                "recoveryShift": "0.05",
                "monthlyCashOutflow": "500.00"
              }
            }
            """;

    @Test
    void leHorizonteECenariosDeFc() {
        ForecastParameters params = ForecastParameters.parse(3, SCENARIOS_JSON);

        assertThat(params.horizonMonths()).isEqualTo(3);
        assertThat(params.scenarios()).hasSize(2);
        ForecastScenario reajuste = params.scenarios().stream()
                .filter(s -> s.name().equals("REAJUSTE")).findFirst().orElseThrow();
        assertThat(reajuste.tariffAdjustmentPercent()).isEqualByComparingTo("10");
        assertThat(reajuste.socialTariffMonthlyDiscount()).isEqualByComparingTo("500.00");
        assertThat(reajuste.cashbackMonthlyAmount()).isEqualByComparingTo("300.00");
        assertThat(reajuste.recoveryShift()).isEqualByComparingTo("0.05");
        // Deducoes do cenario: 500 + 300
        assertThat(reajuste.monthlyDeductions()).isEqualByComparingTo("800.00");
    }

    @Test
    void campoAusenteNoCenarioEErroNomeado() {
        String json = """
                { "BASE": { "tariffAdjustmentPercent": "0" } }
                """;
        assertThatThrownBy(() -> ForecastParameters.parse(3, json))
                .isInstanceOf(ForecastValidationException.class)
                .hasMessageContaining("SCENARIO_FIELD_REQUIRED")
                .hasMessageContaining("monthlyGrowthPercent");
    }

    @Test
    void horizonteAusenteOuInvalidoEErro() {
        assertThatThrownBy(() -> ForecastParameters.parse(null, SCENARIOS_JSON))
                .isInstanceOf(ForecastValidationException.class)
                .hasMessageContaining("HORIZON_REQUIRED");
        assertThatThrownBy(() -> ForecastParameters.parse(0, SCENARIOS_JSON))
                .isInstanceOf(ForecastValidationException.class)
                .hasMessageContaining("HORIZON_INVALID");
        assertThatThrownBy(() -> ForecastParameters.parse(3, " "))
                .isInstanceOf(ForecastValidationException.class)
                .hasMessageContaining("SCENARIOS_REQUIRED");
    }

    @Test
    void jsonInvalidoViraErroNomeado() {
        assertThatThrownBy(() -> ForecastParameters.parse(3, "{invalido"))
                .isInstanceOf(ForecastValidationException.class)
                .hasMessageContaining("SCENARIOS_JSON_INVALID");
    }

    @Test
    void parametroNuloNoCenarioEErroNomeado() {
        assertThatThrownBy(() -> new ForecastScenario("X", bd("0"), bd("0"), bd("0.00"),
                null, bd("0"), bd("0.00")))
                .isInstanceOf(ForecastValidationException.class)
                .hasMessageContaining("SCENARIO_PARAM_REQUIRED")
                .hasMessageContaining("cashbackMonthlyAmount");
    }
}
