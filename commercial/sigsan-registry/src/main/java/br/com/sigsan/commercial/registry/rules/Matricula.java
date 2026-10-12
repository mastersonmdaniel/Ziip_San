package br.com.sigsan.commercial.registry.rules;

/**
 * Matrícula da unidade consumidora e seu dígito verificador (módulo 11).
 *
 * <p>Algoritmo: percorre os dígitos da direita para a esquerda multiplicando pelos pesos
 * 2, 3, 4, ..., 9 (e recomeça em 2 depois do 9), soma, e toma o resto da divisão por 11. Resto 0 ou
 * 1 dá dígito 0; qualquer outro resto dá {@code 11 - resto} (resto 10 dá 1). É o módulo 11 clássico
 * de arrecadação, e o mesmo que sistemas comerciais legados de saneamento usam para a matrícula; por
 * isso as matrículas existentes de um prestador que migra continuam válidas.
 *
 * <p>O resultado é sempre um dígito de 0 a 9 (nunca "X"), o que mantém a matrícula numérica.
 *
 * <p>Atenção: o dígito protege contra erro de digitação (troca de um dígito, ou de dois adjacentes
 * na maioria dos casos). Não é controle de acesso nem segredo: a matrícula é sequencial e
 * enumerável. A unicidade e o acesso vêm do banco e da autorização, não do dígito.
 */
public final class Matricula {

    private Matricula() {
    }

    /** Dígito verificador de uma matrícula só com dígitos (sem o DV). */
    public static int checkDigit(String digits) {
        if (digits == null || digits.isEmpty() || !digits.chars().allMatch(c -> c >= '0' && c <= '9')) {
            throw new IllegalArgumentException("A matrícula deve ter apenas dígitos: " + digits);
        }
        int weight = 2;
        int sum = 0;
        for (int i = digits.length() - 1; i >= 0; i--) {
            sum += (digits.charAt(i) - '0') * weight;
            weight = weight == 9 ? 2 : weight + 1;
        }
        int rest = sum % 11;
        return rest <= 1 ? 0 : 11 - rest;
    }

    /** Matrícula formatada com o dígito: {@code 12345-5}. */
    public static String format(long sequence) {
        if (sequence < 0) {
            throw new IllegalArgumentException("A sequência da matrícula não pode ser negativa");
        }
        String digits = Long.toString(sequence);
        return digits + "-" + checkDigit(digits);
    }

    /** Aceita {@code 12345-5} ou {@code 123455}. */
    public static boolean isValid(String matriculaWithCheckDigit) {
        if (matriculaWithCheckDigit == null) {
            return false;
        }
        String compact = matriculaWithCheckDigit.replace("-", "").trim();
        if (compact.length() < 2 || !compact.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return false;
        }
        String body = compact.substring(0, compact.length() - 1);
        int given = compact.charAt(compact.length() - 1) - '0';
        return checkDigit(body) == given;
    }
}
