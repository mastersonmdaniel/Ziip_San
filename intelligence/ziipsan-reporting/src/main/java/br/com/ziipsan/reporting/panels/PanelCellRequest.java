package br.com.ziipsan.reporting.panels;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * ED-49: um pedido de celula de painel — a leitura de UM indicador do
 * registro da P3.41 em um periodo, para um painel. Como na celula da
 * ED-48, o VALOR e o SEMAFORO nao vem no pedido: sao apurados pelo
 * registro no {@link StandardPanelService}. {@code variableValues} sao
 * os valores das variaveis declaradas da formula DO REGISTRO (a formula
 * em si nunca aparece aqui — sem formula repetida no painel, JOR-19).
 * {@code areaCode} e a area da apuracao no registro (meta e fechamento
 * por area da P3.41 usam a mesma chave); {@code null} = apuracao geral.
 */
public record PanelCellRequest(PanelKind panel, String indicatorCode, LocalDate periodStart,
                               LocalDate periodEnd, String areaCode,
                               Map<String, BigDecimal> variableValues) {

    public PanelCellRequest {
        if (panel == null) {
            throw new IndicatorValidationException("PANEL_REQUEST_PANEL_REQUIRED",
                    "celula de painel sem o painel");
        }
        if (indicatorCode == null || indicatorCode.isBlank()) {
            throw new IndicatorValidationException("PANEL_REQUEST_INDICATOR_REQUIRED",
                    "celula do painel " + panel + " sem o indicador do registro");
        }
        if (periodStart == null || periodEnd == null || periodEnd.isBefore(periodStart)) {
            throw new IndicatorValidationException("PANEL_REQUEST_PERIOD_INVALID",
                    "celula do painel " + panel + " do indicador " + indicatorCode
                            + " com periodo invalido");
        }
        variableValues = variableValues == null ? Map.of() : Map.copyOf(variableValues);
    }
}
