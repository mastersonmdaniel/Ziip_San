package br.com.sigsan.core.document;

import java.util.Locale;

/**
 * CPF e CNPJ: normalização, validação do dígito verificador e máscara de exibição.
 *
 * <h3>CNPJ alfanumérico</h3>
 * A Receita Federal passou a emitir CNPJ com letras (14 posições: 12 alfanuméricas + 2 dígitos
 * verificadores numéricos) a partir de julho de 2026, e os CNPJ numéricos existentes continuam
 * válidos. O cálculo do dígito é o módulo 11 de sempre, mas cada caractere vale o seu código ASCII
 * menos 48 ({@code '0'}=0 ... {@code '9'}=9, {@code 'A'}=17, {@code 'B'}=18 ...). Para um CNPJ
 * numérico isso reduz ao cálculo antigo. Conferido com o exemplo da documentação técnica
 * ({@code 12.ABC.345/01DE-35}) e com CNPJ numéricos conhecidos nos testes; a fonte normativa
 * (instrução normativa da Receita) deve ser confirmada pelo jurídico antes de produção.
 *
 * <p>O documento não é cifrado aqui: esta classe só valida e formata. Quem grava é responsável por
 * cifrar e por calcular o índice cego sobre a forma normalizada.
 */
public final class DocumentNumber {

    private static final int[] CPF_WEIGHTS_1 = {10, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] CPF_WEIGHTS_2 = {11, 10, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] CNPJ_WEIGHTS_1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] CNPJ_WEIGHTS_2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    public enum Kind { CPF, CNPJ, INVALID }

    private DocumentNumber() {
    }

    /** Remove pontuação e espaços e põe em maiúsculas: {@code "12.abc.345/01de-35"} vira {@code "12ABC34501DE35"}. */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (char c : raw.toUpperCase(Locale.ROOT).toCharArray()) {
            if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z')) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static Kind kind(String raw) {
        String d = normalize(raw);
        if (isValidCpf(d)) return Kind.CPF;
        if (isValidCnpj(d)) return Kind.CNPJ;
        return Kind.INVALID;
    }

    public static boolean isValidCpf(String raw) {
        String d = normalize(raw);
        if (d.length() != 11 || !d.chars().allMatch(c -> c >= '0' && c <= '9')) return false;
        if (d.chars().distinct().count() == 1) return false; // 111.111.111-11 passa no cálculo e não existe
        int dv1 = cpfDigit(d, CPF_WEIGHTS_1);
        int dv2 = cpfDigit(d, CPF_WEIGHTS_2);
        return d.charAt(9) - '0' == dv1 && d.charAt(10) - '0' == dv2;
    }

    public static boolean isValidCnpj(String raw) {
        String d = normalize(raw);
        if (d.length() != 14) return false;
        // 12 primeiras alfanuméricas, 2 últimas (dígitos verificadores) numéricas
        for (int i = 0; i < 12; i++) {
            if (!isAlnum(d.charAt(i))) return false;
        }
        if (!isDigit(d.charAt(12)) || !isDigit(d.charAt(13))) return false;
        if (d.chars().distinct().count() == 1) return false; // 00000000000000
        int dv1 = cnpjDigit(d, 12, CNPJ_WEIGHTS_1);
        int dv2 = cnpjDigit(d, 13, CNPJ_WEIGHTS_2);
        return d.charAt(12) - '0' == dv1 && d.charAt(13) - '0' == dv2;
    }

    /** Calcula os 2 dígitos verificadores de um CNPJ com 12 posições (útil para gerar e para testes). */
    public static String cnpjCheckDigits(String first12) {
        String d = normalize(first12);
        if (d.length() != 12 || !d.chars().allMatch(c -> isAlnum((char) c))) {
            throw new IllegalArgumentException("São necessárias 12 posições alfanuméricas");
        }
        int dv1 = cnpjDigit(d, 12, CNPJ_WEIGHTS_1);
        int dv2 = cnpjDigit(d + dv1, 13, CNPJ_WEIGHTS_2);
        return "" + dv1 + dv2;
    }

    /**
     * Máscara para listas e telas: mostra só o miolo. O CPF mostra os 6 dígitos do meio; o CNPJ
     * mostra apenas a ordem do estabelecimento (as 4 posições depois da barra). Documento inválido
     * não é exibido. Exemplos nos testes (um asterisco seguido de barra fecharia este comentário).
     */
    public static String mask(String raw) {
        String d = normalize(raw);
        if (isValidCpf(d)) {
            return "***." + d.substring(3, 6) + "." + d.substring(6, 9) + "-**";
        }
        if (isValidCnpj(d)) {
            return "**.***.***/" + d.substring(8, 12) + "-**";
        }
        return "documento inválido";
    }

    /** Forma com pontuação, para exibição quando a permissão de revelar foi concedida. */
    public static String format(String raw) {
        String d = normalize(raw);
        if (isValidCpf(d)) {
            return d.substring(0, 3) + "." + d.substring(3, 6) + "." + d.substring(6, 9) + "-" + d.substring(9);
        }
        if (isValidCnpj(d)) {
            return d.substring(0, 2) + "." + d.substring(2, 5) + "." + d.substring(5, 8) + "/"
                    + d.substring(8, 12) + "-" + d.substring(12);
        }
        return raw == null ? "" : raw;
    }

    // ---------- cálculo ----------

    private static int cpfDigit(String d, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (d.charAt(i) - '0') * weights[i];
        }
        int r = (sum * 10) % 11;
        return r == 10 ? 0 : r;
    }

    private static int cnpjDigit(String d, int length, int[] weights) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += (d.charAt(i) - 48) * weights[i];
        }
        int rest = sum % 11;
        return rest < 2 ? 0 : 11 - rest;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isAlnum(char c) {
        return isDigit(c) || (c >= 'A' && c <= 'Z');
    }
}
