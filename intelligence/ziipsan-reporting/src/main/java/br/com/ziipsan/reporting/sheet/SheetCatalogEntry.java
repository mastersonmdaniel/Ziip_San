package br.com.ziipsan.reporting.sheet;

import br.com.ziipsan.reporting.indicator.IndicatorPeriodicity;
import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import java.util.List;

/**
 * Uma entrada do catalogo de fabrica de fichas da ED-77 (RAS-07): codigo,
 * variaveis, formula, ORIGEM DO DADO NO PRODUTO e arcabouco/nivel. Entrada
 * sem qualquer um desses elementos e recusada - e o teste de calculo sobre
 * massa sintetica roda sobre a formula declarada aqui, materializada no
 * registro da P3.41 pelo {@link SheetService}.
 *
 * <p>Textos literais das ISO 24510/24512 e das resolucoes de agencia NAO
 * sao reproduzidos: a entrada guarda so titulo proprio, edicao/referencia
 * e a formula operacional - conteudo normativo verbatim e [A DEFINIR]
 * ate a licenca/carga conferida (padrao da ED-73/ED-76; pendencia
 * P-ED77-01).
 */
public record SheetCatalogEntry(String code, String title, SheetFramework framework, SheetLevel level,
                                String definitionText, String unit, IndicatorPeriodicity periodicity,
                                String formulaExpression, List<SheetVariable> variables,
                                String context) {

    /** Variavel da ficha com a origem do dado no produto declarada (RAS-07). */
    public record SheetVariable(String name, DataOrigin origin) {
        public SheetVariable {
            if (name == null || name.isBlank()) {
                throw new IndicatorValidationException("SHEET_VARIABLE_NAME_REQUIRED",
                        "variavel de ficha sem nome");
            }
            if (origin == null) {
                throw new IndicatorValidationException("SHEET_VARIABLE_ORIGIN_REQUIRED",
                        "variavel " + name + " sem a origem do dado no produto (RAS-07)");
            }
        }
    }

    /** Origens de dado que o produto de fato tem nesta entrega. */
    public enum DataOrigin {
        IBGE_BASE,
        NETWORK_INCIDENT_ED92,
        REPORT_VIEW_P322,
        MANUAL_LOW_CONFIDENCE
    }

    public SheetCatalogEntry {
        if (code == null || code.isBlank()) {
            throw new IndicatorValidationException("SHEET_ENTRY_CODE_REQUIRED", "ficha de fabrica sem codigo");
        }
        if (title == null || title.isBlank()) {
            throw new IndicatorValidationException("SHEET_ENTRY_TITLE_REQUIRED",
                    "ficha " + code + " sem titulo proprio (texto da norma nao e reproduzido)");
        }
        if (framework == null || level == null) {
            throw new IndicatorValidationException("SHEET_ENTRY_FRAMEWORK_REQUIRED",
                    "ficha " + code + " sem arcabouco ou nivel");
        }
        if (definitionText == null || definitionText.isBlank()) {
            throw new IndicatorValidationException("SHEET_ENTRY_DEFINITION_REQUIRED",
                    "ficha " + code + " sem definicao em palavras proprias");
        }
        if (unit == null || unit.isBlank()) {
            throw new IndicatorValidationException("SHEET_ENTRY_UNIT_REQUIRED", "ficha " + code + " sem unidade");
        }
        if (periodicity == null) {
            throw new IndicatorValidationException("SHEET_ENTRY_PERIODICITY_REQUIRED",
                    "ficha " + code + " sem periodicidade");
        }
        if (formulaExpression == null || formulaExpression.isBlank()) {
            throw new IndicatorValidationException("SHEET_ENTRY_FORMULA_REQUIRED",
                    "ficha " + code + " sem formula (RAS-07)");
        }
        if (variables == null || variables.isEmpty()) {
            throw new IndicatorValidationException("SHEET_ENTRY_VARIABLES_REQUIRED",
                    "ficha " + code + " sem variaveis com origem (RAS-07)");
        }
        if (context == null || context.isBlank()) {
            throw new IndicatorValidationException("SHEET_ENTRY_CONTEXT_REQUIRED",
                    "ficha " + code + " sem contexto");
        }
        variables = List.copyOf(variables);
    }
}
