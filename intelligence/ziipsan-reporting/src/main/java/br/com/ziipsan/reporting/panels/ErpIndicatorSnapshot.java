package br.com.ziipsan.reporting.panels;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * ED-49: retrato de um indicador LIDO do ERP do cliente (ED-62) para a
 * secao de compras/RH/orcamento do painel estrategico. Valor, competencia
 * e referencia de origem sao os que o ERP devolveu — transportados, nunca
 * calculados aqui (a ED-62 nao constroi os modulos, ADR-0028 i; esta
 * entrega tambem nao). {@code providerName} e o nome estavel do hub da
 * ED-62 ({@code erp-betha}/{@code erp-ipm}/{@code erp-fiorilli}).
 */
public record ErpIndicatorSnapshot(String providerName, String area, String indicatorCode,
                                   BigDecimal value, LocalDate competence, String sourceReference) {

    public ErpIndicatorSnapshot {
        if (providerName == null || providerName.isBlank()) {
            throw new IndicatorValidationException("ERP_SNAPSHOT_PROVIDER_REQUIRED",
                    "indicador de ERP sem o fornecedor de origem (ED-62)");
        }
        if (area == null || area.isBlank()) {
            throw new IndicatorValidationException("ERP_SNAPSHOT_AREA_REQUIRED",
                    "indicador de ERP sem a area (compras/RH/orcamento, ED-62)");
        }
        if (indicatorCode == null || indicatorCode.isBlank()) {
            throw new IndicatorValidationException("ERP_SNAPSHOT_INDICATOR_REQUIRED",
                    "indicador de ERP sem o codigo lido");
        }
        if (value == null) {
            throw new IndicatorValidationException("ERP_SNAPSHOT_VALUE_REQUIRED",
                    "indicador de ERP " + indicatorCode + " sem o valor devolvido pelo ERP");
        }
        if (competence == null) {
            throw new IndicatorValidationException("ERP_SNAPSHOT_COMPETENCE_REQUIRED",
                    "indicador de ERP " + indicatorCode + " sem a competencia");
        }
        if (sourceReference == null || sourceReference.isBlank()) {
            throw new IndicatorValidationException("ERP_SNAPSHOT_SOURCE_REQUIRED",
                    "indicador de ERP " + indicatorCode + " sem a referencia de origem no ERP");
        }
    }
}
