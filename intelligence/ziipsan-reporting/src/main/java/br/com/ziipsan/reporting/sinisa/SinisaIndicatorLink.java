package br.com.ziipsan.reporting.sinisa;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import java.util.UUID;

/**
 * Ligacao de UM indicador do registro unico da P3.41 a UMA variavel do mapa
 * SINISA da P4.2 (aceite da ED-48: "cada indicador ligado a variavel do mapa
 * SINISA da P4.2 (GES-16)"). Ligacao sem chave do mapa nao existe: a
 * construcao recusa. E a linha da tabela {@code sinisa_indicator_links} do
 * SQL pendente da ED-48.
 */
public record SinisaIndicatorLink(UUID tenantId, String indicatorCode, String sinisaVariableKey,
                                  SinisaPanelForm form) {

    public SinisaIndicatorLink {
        if (tenantId == null) {
            throw new IndicatorValidationException("TENANT_REQUIRED", "tenant e obrigatorio");
        }
        if (indicatorCode == null || indicatorCode.isBlank()) {
            throw new IndicatorValidationException("SINISA_LINK_INDICATOR_REQUIRED",
                    "ligacao SINISA sem o codigo do indicador do registro da P3.41");
        }
        if (sinisaVariableKey == null || sinisaVariableKey.isBlank()) {
            throw new IndicatorValidationException("SINISA_LINK_VARIABLE_REQUIRED",
                    "indicador " + indicatorCode + " sem a variavel do mapa SINISA da P4.2 (GES-16)");
        }
        SinisaMapCatalog.requireKnown(sinisaVariableKey);
        if (form == null) {
            throw new IndicatorValidationException("SINISA_LINK_FORM_REQUIRED",
                    "ligacao do indicador " + indicatorCode + " sem formulario SINISA");
        }
    }
}
