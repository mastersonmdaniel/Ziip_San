package br.com.ziipsan.platform.portal.verify;

import br.com.ziipsan.security.crypto.FieldProtector;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * Codigo publico de verificacao da fatura (P3.35, PLT-11): o codigo curto
 * impresso na conta (e codificado no QR que aponta para
 * {@code GET /api/v1/public/documents/verify}). DIFERENTE do codigo de
 * vinculacao da P3.3/P3.20: aquele vincula a unidade a conta do portal;
 * este so prova que um papel corresponde a uma fatura emitida.
 *
 * <p>Desenho anti-enumeracao: 16 simbolos de alfabeto de 32 (Crockford
 * sem I/L/O/U) = 80 bits de entropia de CSPRNG, exibidos agrupados
 * ({@code XXXX-XXXX-XXXX-XXXX}) para digitacao. Codigo sequencial ou
 * derivado da matricula seria um oraculo de faturas: por isso a geracao
 * e aleatoria e no banco vive SO o indice cego do codigo (contexto
 * proprio {@link #CONTEXT}), nunca o codigo em claro. A conferencia e
 * em tempo constante sobre os indices, como no {@code LinkingCode}.
 */
public final class VerificationCode {

    public static final String CONTEXT = "portal.bill_verification_code";
    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final int SYMBOLS = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private VerificationCode() { }

    /** Gera um codigo novo, ja agrupado para impressao ({@code XXXX-XXXX-XXXX-XXXX}). */
    public static String generate() {
        StringBuilder raw = new StringBuilder(SYMBOLS);
        for (int i = 0; i < SYMBOLS; i++) {
            raw.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return group(raw.toString());
    }

    /** Remove agrupamento e espacos e sobe para maiusculas: a forma canonica de conferencia. */
    public static String normalize(String candidate) {
        if (candidate == null) {
            return null;
        }
        return candidate.replace("-", "").replace(" ", "").strip().toUpperCase(java.util.Locale.ROOT);
    }

    /** Formato aceitavel: exatamente 16 simbolos do alfabeto (apos normalizar). */
    public static boolean hasCodeShape(String candidate) {
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

    public static String storageHash(FieldProtector protector, UUID tenantId, String code) {
        if (!hasCodeShape(code)) {
            throw new IllegalArgumentException("codigo de verificacao fora do formato de alta entropia");
        }
        return protector.indexOf(tenantId, CONTEXT, normalize(code));
    }

    public static boolean matches(FieldProtector protector, UUID tenantId, String storedHash, String candidate) {
        if (storedHash == null || !hasCodeShape(candidate)) {
            return false;
        }
        String candidateHash = protector.indexOf(tenantId, CONTEXT, normalize(candidate));
        return MessageDigest.isEqual(
                storedHash.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                candidateHash.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
