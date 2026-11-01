package br.com.ziipsan.commercial.collection.barcode;

/**
 * Identificador de valor efetivo ou de referência (posição 3), que também fixa o algoritmo do
 * dígito verificador geral (posição 4) e dos dígitos de cada bloco da linha digitável.
 *
 * <ul>
 *   <li>{@code 6}: valor efetivo em reais, módulo 10;</li>
 *   <li>{@code 7}: valor de referência (quantidade de moeda, zeros ou valor a reajustar), módulo 10;</li>
 *   <li>{@code 8}: valor efetivo em reais, módulo 11;</li>
 *   <li>{@code 9}: valor de referência, módulo 11.</li>
 * </ul>
 */
public enum ValueIdentifier {
    EFFECTIVE_MOD10('6', true, false),
    REFERENCE_MOD10('7', false, false),
    EFFECTIVE_MOD11('8', true, true),
    REFERENCE_MOD11('9', false, true);

    private final char code;
    private final boolean effectiveAmount;
    private final boolean mod11;

    ValueIdentifier(char code, boolean effectiveAmount, boolean mod11) {
        this.code = code;
        this.effectiveAmount = effectiveAmount;
        this.mod11 = mod11;
    }

    public char code() {
        return code;
    }

    /** {@code true} se o campo de valor (11 posições) é o valor a cobrar, em centavos de real. */
    public boolean isEffectiveAmount() {
        return effectiveAmount;
    }

    public boolean usesMod11() {
        return mod11;
    }

    /** DAC deste identificador sobre a sequência dada (módulo 10 ou módulo 11 da arrecadação). */
    public int checkDigit(String digits) {
        return mod11 ? CheckDigits.mod11Collection(digits) : CheckDigits.mod10(digits);
    }

    public static ValueIdentifier fromCode(char code) {
        for (ValueIdentifier v : values()) {
            if (v.code == code) {
                return v;
            }
        }
        throw new InvalidBarcodeException(InvalidBarcodeException.Reason.UNKNOWN_VALUE_IDENTIFIER,
                "Identificador de valor inexistente (esperado 6, 7, 8 ou 9): '" + code + "'");
    }
}
