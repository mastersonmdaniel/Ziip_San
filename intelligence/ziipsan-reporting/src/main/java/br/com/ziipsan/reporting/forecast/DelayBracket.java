package br.com.ziipsan.reporting.forecast;

/**
 * Faixa de atraso (ED-100): intervalo de dias em atraso, inclusivo nas duas
 * pontas; {@code maxDays == null} = faixa aberta ("acima de minDays").
 * As faixas sao ENTRADA explicita de quem estima a curva — o conjunto
 * {@link #standard()} e apenas o recorte usual declarado, nao uma
 * constante escondida de calculo: a estimativa aceita qualquer conjunto
 * sem sobreposicao.
 */
public record DelayBracket(String code, int minDays, Integer maxDays) {

    public DelayBracket {
        if (code == null || code.isBlank()) {
            throw new ForecastValidationException("BRACKET_CODE_REQUIRED", "codigo da faixa e obrigatorio");
        }
        if (minDays < 0) {
            throw new ForecastValidationException("BRACKET_RANGE_INVALID",
                    "faixa " + code + " com minDays negativo: " + minDays);
        }
        if (maxDays != null && maxDays < minDays) {
            throw new ForecastValidationException("BRACKET_RANGE_INVALID",
                    "faixa " + code + " com maxDays < minDays: " + maxDays + " < " + minDays);
        }
    }

    /** Sobreposicao de intervalos (faixa aberta cobre tudo a partir do seu minimo). */
    public boolean overlaps(DelayBracket other) {
        int thisMax = maxDays == null ? Integer.MAX_VALUE : maxDays;
        int otherMax = other.maxDays == null ? Integer.MAX_VALUE : other.maxDays;
        return minDays <= otherMax && other.minDays <= thisMax;
    }

    /** Recorte usual declarado: ate 30, 31-60, 61-90 e acima de 90 dias. */
    public static java.util.List<DelayBracket> standard() {
        return java.util.List.of(
                new DelayBracket("ATE_30", 0, 30),
                new DelayBracket("D31_60", 31, 60),
                new DelayBracket("D61_90", 61, 90),
                new DelayBracket("ACIMA_90", 91, null));
    }
}
