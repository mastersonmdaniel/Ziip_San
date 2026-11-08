package br.com.ziipsan.reporting.sinisa;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Uma celula do painel da ED-48: a leitura de um indicador em um periodo,
 * fatiada pelas dimensoes do aceite — setor, categoria e tipo de ligacao —
 * com os valores das variaveis da formula. As dimensoes sao atributos
 * DECLARADOS da leitura (quem alimenta o painel as informa a partir da
 * fonte: setor/area do registro, categoria e tipo de ligacao do cadastro);
 * dimensao ausente e {@code null} declarado, nunca um rotulo presumido.
 * O valor e o semaforo NAO vem na celula: sao apurados pelo registro da
 * P3.41 no {@link SinisaPanelService} (fechamento congelado incluso).
 */
public record PanelCell(String indicatorCode, LocalDate periodStart, LocalDate periodEnd,
                        String sector, String category, String connectionType,
                        Map<String, BigDecimal> variableValues) {

    public PanelCell {
        if (indicatorCode == null || indicatorCode.isBlank()) {
            throw new IndicatorValidationException("PANEL_CELL_INDICATOR_REQUIRED",
                    "celula do painel sem indicador");
        }
        if (periodStart == null || periodEnd == null || periodEnd.isBefore(periodStart)) {
            throw new IndicatorValidationException("PANEL_CELL_PERIOD_INVALID",
                    "celula do painel do indicador " + indicatorCode + " com periodo invalido");
        }
        variableValues = variableValues == null ? Map.of() : Map.copyOf(variableValues);
    }
}
