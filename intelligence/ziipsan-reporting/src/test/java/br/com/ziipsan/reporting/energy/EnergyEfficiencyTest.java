package br.com.ziipsan.reporting.energy;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * kWh/m3, ponta e alerta de multa (OPE-08). Limiares sao parametros do
 * TENANT declarados no JSON de cada teste — nenhum valor e de fabrica.
 */
class EnergyEfficiencyTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID INSTALLATION = UUID.randomUUID();

    private static EnergyBill bill(String measuredDemand, String pf) {
        return new EnergyBill(TENANT, INSTALLATION, YearMonth.of(2026, 10),
                new BigDecimal("12000"), new BigDecimal("3000"),
                measuredDemand == null ? null : new BigDecimal(measuredDemand),
                new BigDecimal("100"),
                pf == null ? null : new BigDecimal(pf),
                EnergyDataSource.IMPORTED_BILL, "L1");
    }

    @Test
    void consumoEspecificoConhecido() {
        // 12000 kWh / 3000 m3 = 4 kWh/m3
        assertThat(EnergyEfficiency.specificConsumptionKwhPerM3(
                new BigDecimal("12000"), new BigDecimal("3000")))
                .isEqualByComparingTo("4");
    }

    @Test
    void consumoEspecificoSemVolumeRecusa() {
        assertThatThrownBy(() -> EnergyEfficiency.specificConsumptionKwhPerM3(
                new BigDecimal("12000"), BigDecimal.ZERO))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("ENERGY_VOLUME_REQUIRED");
    }

    @Test
    void participacaoNaPontaConhecida() {
        // 3000 de 12000 = 25%
        assertThat(EnergyEfficiency.peakSharePercent(bill("100", "0.95")))
                .isEqualByComparingTo("25");
    }

    @Test
    void demandaAcimaDaToleranciaAlertaMulta() {
        // contratada 100, tolerancia do tenant 10% -> limite 110; medida 120 alerta
        String params = "{\"energy.demand_alert_pct\": 10}";
        EnergyEfficiency.DemandAssessment assessment =
                EnergyEfficiency.assessDemand(bill("120", null), params);
        assertThat(assessment.demandEvaluated()).isTrue();
        assertThat(assessment.demandLimitKw()).isEqualByComparingTo("110");
        assertThat(assessment.demandAlert()).isTrue();
        assertThat(assessment.anyAlert()).isTrue();
    }

    @Test
    void demandaDentroDaToleranciaNaoAlerta() {
        String params = "{\"energy.demand_alert_pct\": 10}";
        EnergyEfficiency.DemandAssessment assessment =
                EnergyEfficiency.assessDemand(bill("105", null), params);
        assertThat(assessment.demandEvaluated()).isTrue();
        assertThat(assessment.demandAlert()).isFalse();
    }

    @Test
    void semParametroDeDemandaNaoAvalia() {
        EnergyEfficiency.DemandAssessment assessment =
                EnergyEfficiency.assessDemand(bill("999", null), "{}");
        assertThat(assessment.demandEvaluated()).isFalse();
        assertThat(assessment.demandAlert()).isFalse();
    }

    @Test
    void fatorDePotenciaAbaixoDoMinimoDoTenantAlerta() {
        // minimo declarado pelo tenant no teste: 0.90; fatura: 0.85
        String params = "{\"energy.pf_min\": 0.90}";
        EnergyEfficiency.DemandAssessment assessment =
                EnergyEfficiency.assessDemand(bill("100", "0.85"), params);
        assertThat(assessment.powerFactorEvaluated()).isTrue();
        assertThat(assessment.powerFactorAlert()).isTrue();
    }

    @Test
    void fatorDePotenciaSemMinimoNaoAvalia() {
        EnergyEfficiency.DemandAssessment assessment =
                EnergyEfficiency.assessDemand(bill("100", "0.50"), "{}");
        assertThat(assessment.powerFactorEvaluated()).isFalse();
        assertThat(assessment.powerFactorAlert()).isFalse();
    }
}
