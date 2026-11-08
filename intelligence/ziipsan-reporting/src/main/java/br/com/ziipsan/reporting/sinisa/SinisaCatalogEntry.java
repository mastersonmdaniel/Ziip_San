package br.com.ziipsan.reporting.sinisa;

import br.com.ziipsan.reporting.indicator.IndicatorPeriodicity;
import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import java.util.List;

/**
 * Uma entrada do catalogo SINISA pre-configurado da ED-48: o modelo de um
 * indicador SINISA (codigo, nome, definicao, unidade, periodicidade, formula
 * e entradas) pronto para ser materializado no registro unico da P3.41 de
 * um tenant pelo {@link SinisaCatalogService}.
 *
 * <p>O que a entrada NAO carrega, de proposito:
 * <ul>
 *   <li><b>codigo oficial do Glossario</b> — nao inventado (precedente da
 *       P4.2: {@code officialCode} fica nulo ate o glossario vigente ser
 *       carregado na integracao);</li>
 *   <li><b>grau de confianca</b> — a ficha da P3.41 exige o grau declarado;
 *       quem materializa o catalogo no tenant o informa (parametro da
 *       materializacao), o catalogo nao presume um grau;</li>
 *   <li><b>meta</b> — meta e do tenant, por periodo e por area, no registro
 *       da P3.41; sem meta o semaforo e {@code NO_TARGET}, nunca verde.</li>
 * </ul>
 * {@code sinisaVariableKey} e a variavel do mapa da P4.2 que este indicador
 * REPRESENTA (a ligacao do aceite); {@code variables} sao as ENTRADAS da
 * formula, cada uma tambem ligada a uma variavel do mapa.
 */
public record SinisaCatalogEntry(String code, String sinisaVariableKey, SinisaPanelForm form,
                                 String name, String definitionText, String unit,
                                 IndicatorPeriodicity periodicity, String formulaExpression,
                                 List<SinisaVariableRef> variables, String context) {

    public SinisaCatalogEntry {
        if (code == null || code.isBlank()) {
            throw new IndicatorValidationException("SINISA_ENTRY_CODE_REQUIRED",
                    "entrada do catalogo SINISA sem codigo");
        }
        if (sinisaVariableKey == null || sinisaVariableKey.isBlank()) {
            throw new IndicatorValidationException("SINISA_ENTRY_WITHOUT_MAP_VARIABLE",
                    "entrada " + code + " sem a variavel do mapa SINISA da P4.2 (GES-16): recusada");
        }
        SinisaMapCatalog.requireKnown(sinisaVariableKey);
        if (form == null) {
            throw new IndicatorValidationException("SINISA_ENTRY_FORM_REQUIRED",
                    "entrada " + code + " sem formulario SINISA");
        }
        if (name == null || name.isBlank()) {
            throw new IndicatorValidationException("SINISA_ENTRY_NAME_REQUIRED",
                    "entrada " + code + " sem nome");
        }
        if (definitionText == null || definitionText.isBlank()) {
            throw new IndicatorValidationException("SINISA_ENTRY_DEFINITION_REQUIRED",
                    "entrada " + code + " sem definicao");
        }
        if (unit == null || unit.isBlank()) {
            throw new IndicatorValidationException("SINISA_ENTRY_UNIT_REQUIRED",
                    "entrada " + code + " sem unidade");
        }
        if (periodicity == null) {
            throw new IndicatorValidationException("SINISA_ENTRY_PERIODICITY_REQUIRED",
                    "entrada " + code + " sem periodicidade");
        }
        if (formulaExpression == null || formulaExpression.isBlank()) {
            throw new IndicatorValidationException("SINISA_ENTRY_FORMULA_REQUIRED",
                    "entrada " + code + " sem formula sobre as variaveis do mapa");
        }
        if (variables == null || variables.isEmpty()) {
            throw new IndicatorValidationException("SINISA_ENTRY_VARIABLES_REQUIRED",
                    "entrada " + code + " sem variaveis de entrada ligadas ao mapa da P4.2");
        }
        if (context == null || context.isBlank()) {
            throw new IndicatorValidationException("SINISA_ENTRY_CONTEXT_REQUIRED",
                    "entrada " + code + " sem contexto (ficha da P3.41)");
        }
        variables = List.copyOf(variables);
    }
}
