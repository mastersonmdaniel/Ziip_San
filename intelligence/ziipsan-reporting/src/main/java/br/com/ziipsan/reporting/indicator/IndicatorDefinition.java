package br.com.ziipsan.reporting.indicator;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Definicao de um indicador no registro unico do tenant (P3.41): codigo,
 * nome, definicao, unidade, periodicidade, grau de confianca, contexto e a
 * ficha (campo {@code frameworkTag}, ex.: {@code ISO_24510},
 * {@code ISO_24512}, {@code NR_9} da ED-77 — dado declarado por quem define,
 * nenhum valor e presumido). {@code profileTags} e a marcacao por perfil
 * lida pela pagina inicial da P3.39. As variaveis sao as da formula; todas
 * tem fonte declarada pela construcao de {@link IndicatorVariable}.
 */
public record IndicatorDefinition(UUID tenantId, String code, String name, String definitionText,
                                  String unit, IndicatorPeriodicity periodicity,
                                  ConfidenceGrade confidenceGrade, String context,
                                  String frameworkTag, Set<String> profileTags,
                                  List<IndicatorVariable> variables) {

    public IndicatorDefinition {
        if (tenantId == null) {
            throw new IndicatorValidationException("TENANT_REQUIRED", "tenant e obrigatorio");
        }
        if (code == null || code.isBlank()) {
            throw new IndicatorValidationException("INDICATOR_CODE_REQUIRED", "codigo e obrigatorio");
        }
        if (name == null || name.isBlank()) {
            throw new IndicatorValidationException("INDICATOR_NAME_REQUIRED",
                    "nome e obrigatorio para " + code);
        }
        if (definitionText == null || definitionText.isBlank()) {
            throw new IndicatorValidationException("INDICATOR_DEFINITION_REQUIRED",
                    "definicao e obrigatoria para " + code);
        }
        if (unit == null || unit.isBlank()) {
            throw new IndicatorValidationException("INDICATOR_UNIT_REQUIRED",
                    "unidade e obrigatoria para " + code);
        }
        if (periodicity == null) {
            throw new IndicatorValidationException("INDICATOR_PERIODICITY_REQUIRED",
                    "periodicidade e obrigatoria para " + code);
        }
        if (confidenceGrade == null) {
            throw new IndicatorValidationException("CONFIDENCE_GRADE_REQUIRED",
                    "grau de confianca e obrigatorio para " + code + " (ficha da ED-77)");
        }
        if (context == null || context.isBlank()) {
            throw new IndicatorValidationException("INDICATOR_CONTEXT_REQUIRED",
                    "contexto e obrigatorio para " + code + " (ficha da ED-77)");
        }
        if (variables == null || variables.isEmpty()) {
            throw new IndicatorValidationException("INDICATOR_VARIABLES_REQUIRED",
                    "indicador " + code + " sem variaveis: a formula nao teria sobre o que operar");
        }
        profileTags = profileTags == null ? Set.of() : Set.copyOf(profileTags);
        variables = List.copyOf(variables);
        long distinct = variables.stream().map(IndicatorVariable::name).distinct().count();
        if (distinct != variables.size()) {
            throw new IndicatorValidationException("VARIABLE_DUPLICATED",
                    "indicador " + code + " com variavel repetida na definicao");
        }
    }
}
