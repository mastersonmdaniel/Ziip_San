package br.com.ziipsan.reporting.energy;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Fatura/leitura de energia de uma instalacao em um mes de referencia
 * (aceite da ED-90, OPE-08): consumo total e na ponta, demanda medida e
 * contratada e fator de potencia.
 *
 * <p>Regras de fonte (declaradas):
 * <ul>
 *   <li>fatura vinda de arquivo ({@code IMPORTED_BILL}) EXIGE a versao
 *       do leiaute da distribuidora que a leu — o leiaute e conferido
 *       na implantacao e a versao e a prova de qual leiaute foi usado;</li>
 *   <li>telemetria e entrada manual NAO carregam versao de leiaute
 *       (nao passaram por arquivo de distribuidora);</li>
 *   <li>demanda e fator de potencia sao nullable: ha fatura sem demanda
 *       contratada/medida e sem fator de potencia; a avaliacao de
 *       alerta ({@link EnergyEfficiency}) declara o que nao pode
 *       avaliar em vez de presumir numero.</li>
 * </ul>
 * Nenhum valor monetario e calculado aqui: o alerta e de MULTA
 * (risco), sem tarifa de ultrapassagem em nenhuma fonte desta tarefa.
 */
public record EnergyBill(UUID tenantId, UUID installationId, YearMonth referenceMonth,
                         BigDecimal totalKwh, BigDecimal peakKwh,
                         BigDecimal measuredDemandKw, BigDecimal contractedDemandKw,
                         BigDecimal powerFactor, EnergyDataSource source,
                         String layoutVersion) {

    public EnergyBill {
        if (tenantId == null) {
            throw new IndicatorValidationException("ENERGY_TENANT_REQUIRED", "fatura sem tenant");
        }
        if (installationId == null) {
            throw new IndicatorValidationException("ENERGY_BILL_INSTALLATION_REQUIRED",
                    "fatura sem a instalacao dona");
        }
        if (referenceMonth == null) {
            throw new IndicatorValidationException("ENERGY_BILL_MONTH_REQUIRED",
                    "fatura sem o mes de referencia");
        }
        if (totalKwh == null || totalKwh.signum() < 0) {
            throw new IndicatorValidationException("ENERGY_BILL_KWH_INVALID",
                    "fatura exige consumo total (kWh) nao negativo");
        }
        if (peakKwh != null) {
            if (peakKwh.signum() < 0 || peakKwh.compareTo(totalKwh) > 0) {
                throw new IndicatorValidationException("ENERGY_BILL_PEAK_KWH_INVALID",
                        "consumo na ponta deve estar entre 0 e o consumo total: ponta="
                                + peakKwh + " total=" + totalKwh);
            }
        }
        if (measuredDemandKw != null && measuredDemandKw.signum() < 0) {
            throw new IndicatorValidationException("ENERGY_BILL_DEMAND_INVALID",
                    "demanda medida nao pode ser negativa: " + measuredDemandKw);
        }
        if (contractedDemandKw != null && contractedDemandKw.signum() <= 0) {
            throw new IndicatorValidationException("ENERGY_BILL_DEMAND_INVALID",
                    "demanda contratada deve ser positiva quando declarada: " + contractedDemandKw);
        }
        if (powerFactor != null
                && (powerFactor.signum() < 0 || powerFactor.compareTo(BigDecimal.ONE) > 0)) {
            throw new IndicatorValidationException("ENERGY_BILL_POWER_FACTOR_INVALID",
                    "fator de potencia fora da faixa [0, 1]: " + powerFactor);
        }
        if (source == null) {
            throw new IndicatorValidationException("ENERGY_BILL_SOURCE_REQUIRED",
                    "fatura sem a origem declarada (arquivo importado, telemetria ou manual)");
        }
        if (source == EnergyDataSource.FLEET_ED59) {
            throw new IndicatorValidationException("ENERGY_BILL_SOURCE_INVALID",
                    "fatura de energia nao tem origem FLEET_ED59 (essa origem e do combustivel)");
        }
        if (source == EnergyDataSource.IMPORTED_BILL) {
            if (layoutVersion == null || layoutVersion.isBlank()) {
                throw new IndicatorValidationException("ENERGY_BILL_LAYOUT_VERSION_REQUIRED",
                        "fatura importada por arquivo exige a versao do leiaute da "
                                + "distribuidora conferido na implantacao [SEM FONTE ate a conferencia]");
            }
        } else if (layoutVersion != null && !layoutVersion.isBlank()) {
            throw new IndicatorValidationException("ENERGY_BILL_LAYOUT_VERSION_UNEXPECTED",
                    "versao de leiaute so existe em fatura importada por arquivo; "
                            + "origem recebida: " + source);
        }
    }
}
