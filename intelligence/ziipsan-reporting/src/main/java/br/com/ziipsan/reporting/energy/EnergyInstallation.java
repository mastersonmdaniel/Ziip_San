package br.com.ziipsan.reporting.energy;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Instalacao de energia de uma unidade operacional (aceite da ED-90,
 * OPE-08: "cadastro das instalacoes de energia por unidade operacional,
 * com o numero da instalacao na distribuidora").
 *
 * <p>O numero da instalacao e o identificador que a distribuidora usa
 * para a unidade consumidora: sem ele nao ha fatura para importar nem
 * telemetria para ligar, por isso e obrigatorio e nao presumido.
 * {@code contractedDemandKw} e opcional no cadastro (instalacoes do
 * grupo B podem nao ter demanda contratada); quando presente, e o
 * valor contratado declarado — a demanda de referencia de cada fatura
 * viaja na propria fatura ({@link EnergyBill}).
 */
public record EnergyInstallation(UUID tenantId, UUID id, UUID operationalUnitId,
                                 OperationalUnitKind unitKind, String distributorName,
                                 String installationNumber, BigDecimal contractedDemandKw) {

    public EnergyInstallation {
        if (tenantId == null) {
            throw new IndicatorValidationException("ENERGY_TENANT_REQUIRED",
                    "instalacao de energia sem tenant");
        }
        if (id == null) {
            throw new IndicatorValidationException("ENERGY_INSTALLATION_ID_REQUIRED",
                    "instalacao de energia sem id");
        }
        if (operationalUnitId == null) {
            throw new IndicatorValidationException("ENERGY_OPERATIONAL_UNIT_REQUIRED",
                    "instalacao de energia sem a unidade operacional dona (aceite: "
                            + "cadastro por unidade operacional)");
        }
        if (unitKind == null) {
            throw new IndicatorValidationException("ENERGY_OPERATIONAL_UNIT_KIND_REQUIRED",
                    "instalacao de energia sem o tipo da unidade (sistema, ETA, elevatoria, ETE)");
        }
        if (distributorName == null || distributorName.isBlank()) {
            throw new IndicatorValidationException("ENERGY_DISTRIBUTOR_REQUIRED",
                    "instalacao de energia sem a distribuidora");
        }
        if (installationNumber == null || installationNumber.isBlank()) {
            throw new IndicatorValidationException("ENERGY_INSTALLATION_NUMBER_REQUIRED",
                    "instalacao de energia sem o numero da instalacao na distribuidora: "
                            + "sem ele nao ha fatura nem telemetria para ligar (OPE-08)");
        }
        if (contractedDemandKw != null && contractedDemandKw.signum() <= 0) {
            throw new IndicatorValidationException("ENERGY_CONTRACTED_DEMAND_INVALID",
                    "demanda contratada da instalacao deve ser positiva quando declarada: "
                            + contractedDemandKw);
        }
    }
}
