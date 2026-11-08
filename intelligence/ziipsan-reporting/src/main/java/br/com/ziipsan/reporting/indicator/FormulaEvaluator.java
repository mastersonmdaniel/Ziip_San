package br.com.ziipsan.reporting.indicator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Avaliador da formula versionada do indicador (P3.41). Gramatica fechada e
 * deterministica: numeros decimais, nomes de variavel, {@code + - * /},
 * parenteses e menos unario. Nada alem disso e aceito — funcao, potencia ou
 * qualquer outro operador e erro de sintaxe nomeado, nunca ignorado.
 *
 * <p>Regras de exatidao (declaradas, sem perda silenciosa):
 * <ul>
 *   <li>soma, subtracao e multiplicacao sao exatas ({@link BigDecimal});</li>
 *   <li>a divisao usa {@code MathContext(20, HALF_UP)}: 20 digitos
 *       significativos. E o unico arredondamento do avaliador, explicito
 *       aqui e travado em teste com dizima periodica;</li>
 *   <li>variavel da formula sem valor informado e recusa nomeada
 *       ({@code MISSING_VARIABLE_VALUE}) — nunca zero presumido;</li>
 *   <li>divisao por zero e recusa nomeada ({@code DIVISION_BY_ZERO}).</li>
 * </ul>
 */
public final class FormulaEvaluator {

    private static final MathContext DIVISION_CONTEXT = new MathContext(20, RoundingMode.HALF_UP);

    private FormulaEvaluator() {
    }

    public static BigDecimal evaluate(String expression, Map<String, BigDecimal> values) {
        if (expression == null || expression.isBlank()) {
            throw new IndicatorValidationException("FORMULA_EXPRESSION_REQUIRED",
                    "expressao da formula e obrigatoria");
        }
        Parser parser = new Parser(expression, values == null ? Map.of() : values);
        BigDecimal result = parser.parseExpression().value();
        parser.expectEnd();
        return result;
    }

    /**
     * Validacao sintatica da formula SEM valores de negocio (usada na
     * definicao do indicador, correcao D2 da revisao independente): percorre
     * a gramatica INTEIRA, de modo que erro de sintaxe em qualquer posicao e
     * detectado — na avaliacao comum, uma divisao por zero dependente de
     * dado aborta o parse e mascarava o restante da expressao (ex.:
     * {@code a / (a - b) + ^} era aceito na definicao). Denominador
     * CONSTANTE zero (literal ou subexpressao sem variaveis, ex.:
     * {@code a / 0}, {@code a / (2 - 2)}) e recusa aqui
     * ({@code DIVISION_BY_ZERO}), porque nao depende de dado nenhum;
     * denominador que zera so com certos valores (ex.: {@code a / (a - b)})
     * NAO e recusa de definicao — a divisao por zero real continua recusada
     * no calculo, com os valores do periodo. Identificador nenhum e
     * conferido aqui: quem cruza os nomes com as variaveis declaradas e o
     * servico, sintaticamente.
     */
    public static void validateSyntax(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IndicatorValidationException("FORMULA_EXPRESSION_REQUIRED",
                    "expressao da formula e obrigatoria");
        }
        Parser parser = new Parser(expression, Map.of(), true);
        parser.parseExpression();
        parser.expectEnd();
    }

    /** Valor parcial do parse: o numero e se a subexpressao e constante (sem variaveis). */
    private record PVal(BigDecimal value, boolean constant) {
    }

    private static final class Parser {
        private final String text;
        private final Map<String, BigDecimal> values;
        private final boolean syntaxOnly;
        private int pos;

        Parser(String text, Map<String, BigDecimal> values) {
            this(text, values, false);
        }

        Parser(String text, Map<String, BigDecimal> values, boolean syntaxOnly) {
            this.text = text;
            this.values = values;
            this.syntaxOnly = syntaxOnly;
        }

        PVal parseExpression() {
            PVal value = parseTerm();
            while (true) {
                skipSpaces();
                if (consume('+')) {
                    PVal rhs = parseTerm();
                    value = new PVal(value.value().add(rhs.value()),
                            value.constant() && rhs.constant());
                } else if (consume('-')) {
                    PVal rhs = parseTerm();
                    value = new PVal(value.value().subtract(rhs.value()),
                            value.constant() && rhs.constant());
                } else {
                    return value;
                }
            }
        }

        private PVal parseTerm() {
            PVal value = parseFactor();
            while (true) {
                skipSpaces();
                if (consume('*')) {
                    PVal rhs = parseFactor();
                    value = new PVal(value.value().multiply(rhs.value()),
                            value.constant() && rhs.constant());
                } else if (consume('/')) {
                    PVal divisor = parseFactor();
                    if (divisor.value().compareTo(BigDecimal.ZERO) == 0) {
                        if (!syntaxOnly || divisor.constant()) {
                            throw new IndicatorValidationException("DIVISION_BY_ZERO",
                                    "divisao por zero na formula: " + text);
                        }
                        // So na validacao sintatica: denominador nao
                        // constante que zerou com os valores ficticios —
                        // segue o parse com valor ficticio para alcancar o
                        // restante da expressao; a recusa real e no calculo
                        // do periodo, com os valores dele.
                        value = new PVal(BigDecimal.ONE, false);
                    } else {
                        value = new PVal(value.value().divide(divisor.value(), DIVISION_CONTEXT),
                                value.constant() && divisor.constant());
                    }
                } else {
                    return value;
                }
            }
        }

        private PVal parseFactor() {
            skipSpaces();
            if (consume('-')) {
                PVal inner = parseFactor();
                return new PVal(inner.value().negate(), inner.constant());
            }
            if (consume('+')) {
                return parseFactor();
            }
            if (consume('(')) {
                PVal value = parseExpression();
                skipSpaces();
                if (!consume(')')) {
                    throw syntax("parentese nao fechado");
                }
                return value;
            }
            if (pos < text.length() && (Character.isDigit(text.charAt(pos)) || text.charAt(pos) == '.')) {
                int start = pos;
                while (pos < text.length()
                        && (Character.isDigit(text.charAt(pos)) || text.charAt(pos) == '.')) {
                    pos++;
                }
                try {
                    return new PVal(new BigDecimal(text.substring(start, pos)), true);
                } catch (NumberFormatException e) {
                    throw syntax("numero invalido em " + text.substring(start, pos));
                }
            }
            if (pos < text.length()
                    && (Character.isLetter(text.charAt(pos)) || text.charAt(pos) == '_')) {
                int start = pos;
                while (pos < text.length()
                        && (Character.isLetterOrDigit(text.charAt(pos)) || text.charAt(pos) == '_')) {
                    pos++;
                }
                String name = text.substring(start, pos);
                BigDecimal value = values.get(name);
                if (value == null) {
                    if (syntaxOnly) {
                        // Sem valores de negocio: qualquer identificador e
                        // um valor ficticio nao constante; o cruzamento com
                        // as variaveis declaradas e do servico.
                        return new PVal(BigDecimal.ONE, false);
                    }
                    throw new IndicatorValidationException("MISSING_VARIABLE_VALUE",
                            "variavel " + name + " sem valor informado para o calculo");
                }
                return new PVal(value, false);
            }
            throw syntax("caractere inesperado na posicao " + pos);
        }

        void expectEnd() {
            skipSpaces();
            if (pos != text.length()) {
                throw syntax("conteudo extra apos o fim da expressao, na posicao " + pos);
            }
        }

        private void skipSpaces() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        private boolean consume(char c) {
            if (pos < text.length() && text.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        private IndicatorValidationException syntax(String detail) {
            return new IndicatorValidationException("FORMULA_SYNTAX",
                    detail + " na formula: " + text);
        }
    }
}
