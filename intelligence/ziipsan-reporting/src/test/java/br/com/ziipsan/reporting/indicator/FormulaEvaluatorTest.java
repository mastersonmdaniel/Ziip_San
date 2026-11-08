package br.com.ziipsan.reporting.indicator;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormulaEvaluatorTest {

    private static BigDecimal eval(String expression, Map<String, BigDecimal> values) {
        return FormulaEvaluator.evaluate(expression, values);
    }

    @Test
    void arithmeticWithPrecedenceAndParenthesesIsExact() {
        Map<String, BigDecimal> values = Map.of(
                "produzido", new BigDecimal("1000"),
                "distribuido", new BigDecimal("900"));
        // (1000 - 900) / 1000 * 100 = 10 exato
        assertThat(eval("(produzido - distribuido) / produzido * 100", values))
                .isEqualByComparingTo("10");
        assertThat(eval("2 + 3 * 4", Map.of())).isEqualByComparingTo("14");
        assertThat(eval("-5 + 8", Map.of())).isEqualByComparingTo("3");
    }

    @Test
    void divisionUsesDeclaredTwentyDigitContext() {
        // 1/3 em dizima: o unico arredondamento do avaliador, declarado na
        // classe (MathContext 20 digitos, HALF_UP).
        assertThat(eval("1 / 3", Map.of())).isEqualByComparingTo("0.33333333333333333333");
    }

    @Test
    void missingVariableValueIsNamedRefusalNeverZero() {
        assertThatThrownBy(() -> eval("a + b", Map.of("a", BigDecimal.ONE)))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("MISSING_VARIABLE_VALUE")
                .hasMessageContaining("b");
    }

    @Test
    void divisionByZeroIsNamedRefusal() {
        assertThatThrownBy(() -> eval("a / b", Map.of("a", BigDecimal.ONE, "b", BigDecimal.ZERO)))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("DIVISION_BY_ZERO");
    }

    @Test
    void syntaxOutsideTheClosedGrammarIsNamedRefusal() {
        assertThatThrownBy(() -> eval("a ^ 2", Map.of("a", BigDecimal.ONE)))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("FORMULA_SYNTAX");
        assertThatThrownBy(() -> eval("(a + 1", Map.of("a", BigDecimal.ONE)))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("FORMULA_SYNTAX");
    }
}
