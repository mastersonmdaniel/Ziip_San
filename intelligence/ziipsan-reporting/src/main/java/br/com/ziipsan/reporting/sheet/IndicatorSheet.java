package br.com.ziipsan.reporting.sheet;

import br.com.ziipsan.reporting.indicator.ConfidenceGrade;
import br.com.ziipsan.reporting.indicator.IndicatorDefinition;
import br.com.ziipsan.reporting.indicator.IndicatorValidationException;
import br.com.ziipsan.reporting.indicator.IndicatorVariable;

import java.util.List;

/**
 * A FICHA de indicador da ED-77: uma VISAO sobre a definicao do registro
 * unico da P3.41 (JOR-19) - a ED-77 NAO tem registro proprio (aceite:
 * "motor de fichas sobre o registro unico de indicadores da P3.41, sem
 * registro proprio"). Variaveis, contexto e grau de confianca sao lidos da
 * definicao da P3.41; a ficha acrescenta so o que o aceite da ED-77 pede:
 * arcabouco, nivel, rateio por municipio e separacao urbano/rural.
 */
public record IndicatorSheet(IndicatorDefinition definition, SheetFramework framework,
                             SheetLevel level, List<ZoneSplit> splits) {
    public IndicatorSheet {
        if (definition == null) {
            throw new IndicatorValidationException("SHEET_DEFINITION_REQUIRED",
                    "ficha sem definicao do registro da P3.41: a ED-77 nao cria registro proprio");
        }
        if (framework == null) {
            throw new IndicatorValidationException("SHEET_FRAMEWORK_REQUIRED",
                    "ficha de " + definition.code() + " sem arcabouco (ISO 24510/24512, NR 9 ou agencia)");
        }
        if (level == null) {
            throw new IndicatorValidationException("SHEET_LEVEL_REQUIRED",
                    "ficha de " + definition.code() + " sem nivel (I, II ou NA declarado)");
        }
        splits = splits == null ? List.of() : List.copyOf(splits);
    }

    public String code() { return definition.code(); }
    public List<IndicatorVariable> variables() { return definition.variables(); }
    public String context() { return definition.context(); }
    public ConfidenceGrade confidenceGrade() { return definition.confidenceGrade(); }
}
