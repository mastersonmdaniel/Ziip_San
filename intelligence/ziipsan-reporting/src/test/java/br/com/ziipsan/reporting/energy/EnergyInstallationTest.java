package br.com.ziipsan.reporting.energy;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cadastro de instalacoes de energia por unidade operacional (OPE-08). */
class EnergyInstallationTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID UNIT = UUID.randomUUID();

    @Test
    void cadastroValidoComNumeroDaDistribuidora() {
        EnergyInstallation installation = new EnergyInstallation(TENANT, UUID.randomUUID(), UNIT,
                OperationalUnitKind.ETA, "Distribuidora Sintetica", "UC-12345",
                new BigDecimal("150"));
        assertThat(installation.installationNumber()).isEqualTo("UC-12345");
        assertThat(installation.unitKind()).isEqualTo(OperationalUnitKind.ETA);
    }

    @Test
    void semNumeroDaInstalacaoRecusa() {
        assertThatThrownBy(() -> new EnergyInstallation(TENANT, UUID.randomUUID(), UNIT,
                OperationalUnitKind.PUMPING_STATION, "Distribuidora Sintetica", " ", null))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("ENERGY_INSTALLATION_NUMBER_REQUIRED");
    }

    @Test
    void semDistribuidoraRecusa() {
        assertThatThrownBy(() -> new EnergyInstallation(TENANT, UUID.randomUUID(), UNIT,
                OperationalUnitKind.WATER_SYSTEM, null, "UC-1", null))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("ENERGY_DISTRIBUTOR_REQUIRED");
    }

    @Test
    void semUnidadeOperacionalRecusa() {
        assertThatThrownBy(() -> new EnergyInstallation(TENANT, UUID.randomUUID(), null,
                OperationalUnitKind.ETE, "Distribuidora Sintetica", "UC-1", null))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("ENERGY_OPERATIONAL_UNIT_REQUIRED");
    }

    @Test
    void demandaContratadaNaoPositivaRecusa() {
        assertThatThrownBy(() -> new EnergyInstallation(TENANT, UUID.randomUUID(), UNIT,
                OperationalUnitKind.ETA, "Distribuidora Sintetica", "UC-1", BigDecimal.ZERO))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("ENERGY_CONTRACTED_DEMAND_INVALID");
    }
}
