package br.com.ziipsan.platform.signing;

import java.security.SecureRandom;

/**
 * Codigo publico de verificacao da assinatura (correcao D7 da revisao
 * cruzada), no MESMO desenho do {@code VerificationCode} da P3.35 neste
 * modulo: 16 simbolos de alfabeto de 32 (Crockford sem I/L/O/U) = <b>80 bits
 * de entropia de CSPRNG</b>, exibidos agrupados ({@code XXXX-XXXX-XXXX-XXXX})
 * para digitacao.
 *
 * <p>Codigo sequencial ou previsivel transformaria o endpoint publico de
 * validacao em oraculo enumeravel (ele distingue VALID / RECORD_TAMPERED /
 * NOT_FOUND). Por isso:
 * <ul>
 *   <li>a geracao de producao e {@link #generate()} (CSPRNG) — a fiacao dos
 *       signers DEVE injetar {@code VerificationCodes::generate} como o
 *       {@code Supplier<String>} de codigos;</li>
 *   <li>os tres signers CONFEREM o formato de cada codigo recebido do
 *       fornecedor injetado ({@link #requireShape}) e recusam codigo fora
 *       dele: um fornecedor sequencial/previsivel nao entra em silencio.</li>
 * </ul>
 * A entropia nao dispensa o limite de tentativas do endpoint publico
 * ({@code SignatureService.VerificationLimits}) — sao camadas distintas.
 */
public final class VerificationCodes {

    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final int SYMBOLS = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private VerificationCodes() {
    }

    /** Gera um codigo novo de 80 bits (CSPRNG), ja agrupado para impressao. */
    public static String generate() {
        StringBuilder raw = new StringBuilder(SYMBOLS);
        for (int i = 0; i < SYMBOLS; i++) {
            raw.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return group(raw.toString());
    }

    /** Remove agrupamento/espacos e sobe para maiusculas: a forma canonica. */
    public static String normalize(String candidate) {
        if (candidate == null) {
            return null;
        }
        return candidate.replace("-", "").replace(" ", "").strip().toUpperCase(java.util.Locale.ROOT);
    }

    /** Formato aceitavel: exatamente 16 simbolos do alfabeto (apos normalizar). */
    public static boolean hasShape(String candidate) {
        String normalized = normalize(candidate);
        if (normalized == null || normalized.length() != SYMBOLS) {
            return false;
        }
        for (char c : normalized.toCharArray()) {
            if (ALPHABET.indexOf(c) < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Guarda dos signers: codigo fora do formato de alta entropia e erro de
     * fiacao (fornecedor errado), nao dado do usuario — por isso
     * {@code IllegalStateException}, como nos demais erros de configuracao.
     */
    public static String requireShape(String candidate) {
        if (!hasShape(candidate)) {
            throw new IllegalStateException("codigo de verificacao fora do formato de alta entropia "
                    + "(16 simbolos, 80 bits): a fiacao deve injetar VerificationCodes::generate "
                    + "como fornecedor de codigos dos signers.");
        }
        return candidate;
    }

    private static String group(String raw) {
        StringBuilder sb = new StringBuilder(raw.length() + 3);
        for (int i = 0; i < raw.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                sb.append('-');
            }
            sb.append(raw.charAt(i));
        }
        return sb.toString();
    }
}
