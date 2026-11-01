package br.com.ziipsan.commercial.collection.barcode;

/**
 * Dígitos verificadores (DAC) dos códigos de barras de arrecadação e de cobrança bancária.
 *
 * <p>Há <b>três</b> algoritmos, e confundi-los é o erro clássico:
 * <ul>
 *   <li>{@link #mod10}: pesos 2,1,2,1... da direita para a esquerda; somam-se os <i>algarismos</i>
 *       de cada produto; DAC = 10 - (soma mod 10), e 0 se o resto for 0. Usado no DV geral dos
 *       valores {@code 6} e {@code 7} da arrecadação, em cada bloco da linha digitável com
 *       esses valores e nos 3 primeiros campos da linha digitável de cobrança.</li>
 *   <li>{@link #mod11Collection}: pesos 2..9 (reinicia em 2) da direita para a esquerda;
 *       DAC = 11 - (soma mod 11); resto 0 ou 1 dá DAC 0; resto 10 dá DAC 1. Usado na
 *       arrecadação com valores {@code 8} e {@code 9}.</li>
 *   <li>{@link #mod11Billing}: mesmos pesos, mas DAC = 11 - (soma mod 11) com 0, 10 e 11
 *       trocados por 1 (nunca devolve 0). Usado no DV geral do boleto de cobrança bancária.</li>
 * </ul>
 *
 * <p>Fontes: FEBRABAN, "Layout Padrão de Arrecadação/Recebimento com Utilização do Código de
 * Barras", versão 08, seções 07 a 10 (módulo 10 e módulo 11 da arrecadação, com exemplos
 * numéricos); Banco Santander, "Código de Barras - Cobrança", v36 abril/2025 (módulo 11 da
 * cobrança, com exemplo numérico). Ver README do módulo.
 */
public final class CheckDigits {

    private CheckDigits() {
    }

    /** DAC módulo 10 (FEBRABAN arrecadação, seção 07). Só aceita algarismos. */
    public static int mod10(String digits) {
        requireDigits(digits);
        int sum = 0;
        int weight = 2;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int product = (digits.charAt(i) - '0') * weight;
            sum += product / 10 + product % 10; // soma os algarismos do produto
            weight = weight == 2 ? 1 : 2;
        }
        int rest = sum % 10;
        return rest == 0 ? 0 : 10 - rest;
    }

    /** DAC módulo 11 da arrecadação (FEBRABAN arrecadação, seção 09): resto 0 ou 1 dá 0. */
    public static int mod11Collection(String digits) {
        int rest = mod11Rest(digits);
        if (rest == 0 || rest == 1) {
            return 0;
        }
        return 11 - rest; // resto 10 dá 1
    }

    /** DAC módulo 11 do boleto de cobrança bancária: 0, 10 e 11 viram 1. */
    public static int mod11Billing(String digits) {
        int dac = 11 - mod11Rest(digits);
        return (dac == 0 || dac == 10 || dac == 11) ? 1 : dac;
    }

    private static int mod11Rest(String digits) {
        requireDigits(digits);
        int sum = 0;
        int weight = 2;
        for (int i = digits.length() - 1; i >= 0; i--) {
            sum += (digits.charAt(i) - '0') * weight;
            weight = weight == 9 ? 2 : weight + 1;
        }
        return sum % 11;
    }

    static void requireDigits(String digits) {
        if (digits == null || digits.isEmpty()) {
            throw new IllegalArgumentException("Sequência numérica vazia");
        }
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("Só algarismos são aceitos: '" + c + "' na posição " + (i + 1));
            }
        }
    }
}
