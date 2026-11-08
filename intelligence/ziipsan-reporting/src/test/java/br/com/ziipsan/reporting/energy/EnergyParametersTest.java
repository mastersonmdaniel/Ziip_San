package br.com.ziipsan.reporting.energy;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Parametros nomeados no aceite: nenhum tem valor de fabrica. */
class EnergyParametersTest {

    @Test
    void parametrosAusentesSaoVazios() {
        assertThat(EnergyParameters.demandAlertPct("{}")).isEmpty();
        assertThat(EnergyParameters.pfMin("{}")).isEmpty();
        assertThat(EnergyParameters.emissionFactorsVersion("{}")).isEmpty();
        assertThat(EnergyParameters.reportProfile("{}")).isEmpty();
    }

    @Test
    void parametrosDeclaradosSaoLidos() {
        String json = "{\"energy.demand_alert_pct\": 10, \"energy.pf_min\": 0.92,"
                + " \"esg.emission_factors_version\": \"V-TESTE\", \"esg.report_profile\": \"PERFIL-TESTE\"}";
        assertThat(EnergyParameters.demandAlertPct(json)).hasValueSatisfying(
                v -> assertThat(v).isEqualByComparingTo("10"));
        assertThat(EnergyParameters.pfMin(json)).hasValueSatisfying(
                v -> assertThat(v).isEqualByComparingTo("0.92"));
        assertThat(EnergyParameters.emissionFactorsVersion(json)).contains("V-TESTE");
        assertThat(EnergyParameters.reportProfile(json)).contains("PERFIL-TESTE");
    }

    @Test
    void pfMinForaDaFaixaRecusa() {
        assertThatThrownBy(() -> EnergyParameters.pfMin("{\"energy.pf_min\": 1.5}"))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("ENERGY_PARAMETER_INVALID");
    }

    @Test
    void demandAlertNegativoRecusa() {
        assertThatThrownBy(() -> EnergyParameters.demandAlertPct("{\"energy.demand_alert_pct\": -1}"))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("ENERGY_PARAMETER_INVALID");
    }
}
