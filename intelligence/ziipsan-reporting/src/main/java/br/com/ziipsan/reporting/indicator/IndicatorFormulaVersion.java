package br.com.ziipsan.reporting.indicator;

import br.com.ziipsan.core.parameters.ParameterVersionRule;

import java.util.Objects;

/**
 * Uma versao da formula de um indicador (aceite da P3.41): mudar a formula
 * gera uma NOVA versao, com vigencia propria pelo motor de vigencia da
 * P0.19 ({@link ParameterVersionRule.Validity}); a versao anterior nunca e
 * reescrita, de modo que o historico e os fechamentos antigos continuam
 * apontando para a formula que os produziu.
 */
public record IndicatorFormulaVersion(int version, String expression,
                                      ParameterVersionRule.Validity validity) {

    public IndicatorFormulaVersion {
        if (version < 1) {
            throw new IndicatorValidationException("FORMULA_VERSION_INVALID",
                    "versao de formula deve ser >= 1, recebida: " + version);
        }
        if (expression == null || expression.isBlank()) {
            throw new IndicatorValidationException("FORMULA_EXPRESSION_REQUIRED",
                    "expressao da formula e obrigatoria");
        }
        Objects.requireNonNull(validity, "vigencia da formula e obrigatoria");
    }
}
