package br.com.sigsan.commercial.collection.barcode;

import java.math.BigDecimal;

/** Utilitários numéricos compartilhados pelo código de barras de arrecadação e de cobrança. */
final class Digits {

    private Digits() {
    }

    /**
     * Remove os separadores usuais de uma linha digitável impressa (espaço, ponto, hífen) e exige
     * que o resto seja só algarismos.
     */
    static String normalize(String raw) {
        if (raw == null) {
            throw new InvalidBarcodeException(InvalidBarcodeException.Reason.NOT_NUMERIC, "Código ausente");
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ' ' || c == '.' || c == '-' || c == '\t') {
                continue;
            }
            if (c < '0' || c > '9') {
                throw new InvalidBarcodeException(InvalidBarcodeException.Reason.NOT_NUMERIC,
                        "Caractere não numérico no código: '" + c + "'");
            }
            sb.append(c);
        }
        return sb.toString();
    }

    static String leftPad(long value, int width) {
        String s = Long.toString(value);
        if (s.length() > width) {
            throw new InvalidBarcodeException(InvalidBarcodeException.Reason.FIELD_OUT_OF_RANGE,
                    "Valor " + value + " não cabe em " + width + " posições");
        }
        return "0".repeat(width - s.length()) + s;
    }

    /** Reais com no máximo 2 casas para centavos; nunca arredonda em silêncio. */
    static long toCents(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new InvalidBarcodeException(InvalidBarcodeException.Reason.FIELD_OUT_OF_RANGE,
                    "Valor ausente ou negativo");
        }
        BigDecimal cents = amount.movePointRight(2);
        if (cents.stripTrailingZeros().scale() > 0) {
            throw new InvalidBarcodeException(InvalidBarcodeException.Reason.FIELD_OUT_OF_RANGE,
                    "Valor com mais de 2 casas decimais: " + amount.toPlainString());
        }
        try {
            return cents.longValueExact();
        } catch (ArithmeticException e) {
            throw new InvalidBarcodeException(InvalidBarcodeException.Reason.FIELD_OUT_OF_RANGE,
                    "Valor grande demais: " + amount.toPlainString());
        }
    }

    static BigDecimal fromCents(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }

    static boolean isAllDigits(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
