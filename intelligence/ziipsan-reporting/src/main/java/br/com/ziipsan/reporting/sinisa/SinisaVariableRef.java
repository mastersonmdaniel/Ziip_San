package br.com.ziipsan.reporting.sinisa;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import java.util.regex.Pattern;

/**
 * Referencia de uma variavel do mapa SINISA da P4.2 (GES-16) usada como
 * entrada de um indicador do catalogo da ED-48: o nome local da variavel na
 * formula do registro da P3.41 e a CHAVE da variavel no mapa da P4.2.
 *
 * <p><b>Nenhum codigo oficial aqui.</b> Como na P4.2, o codigo unico do
 * Glossario de Informacoes do SINISA nao e presumido: a referencia carrega
 * so a chave interna estavel do mapa (ex.: {@code AGUA.VOLUME_FATURADO}),
 * nunca um {@code officialCode} inventado. A natureza da origem
 * ({@link SourceKind}) espelha, por nome, o {@code SourceKind} da
 * {@code SinisaVariable} da P4.2 e decide, na materializacao, como a fonte
 * da P3.41 e declarada (view do nucleo ou manual com responsavel).
 */
public record SinisaVariableRef(String variableName, String sinisaVariableKey,
                                SinisaPanelForm form, SourceKind sourceKind) {

    /** Natureza da origem no mapa da P4.2 (espelho por nome do SourceKind dela). */
    public enum SourceKind {
        METERING,
        BILLING,
        REGISTRY,
        DERIVED,
        MANUAL
    }

    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public SinisaVariableRef {
        if (variableName == null || !NAME_PATTERN.matcher(variableName).matches()) {
            throw new IndicatorValidationException("SINISA_VARIABLE_NAME_INVALID",
                    "nome local de variavel invalido: " + variableName);
        }
        if (sinisaVariableKey == null || sinisaVariableKey.isBlank()) {
            throw new IndicatorValidationException("SINISA_VARIABLE_KEY_REQUIRED",
                    "variavel " + variableName + " sem a chave do mapa SINISA da P4.2: "
                            + "indicador SINISA sem ligacao ao mapa e recusado (GES-16)");
        }
        SinisaMapCatalog.requireKnown(sinisaVariableKey);
        if (form == null) {
            throw new IndicatorValidationException("SINISA_VARIABLE_FORM_REQUIRED",
                    "variavel " + sinisaVariableKey + " sem formulario SINISA");
        }
        if (sourceKind == null) {
            throw new IndicatorValidationException("SINISA_VARIABLE_SOURCE_KIND_REQUIRED",
                    "variavel " + sinisaVariableKey + " sem a natureza da origem do mapa da P4.2");
        }
    }
}
