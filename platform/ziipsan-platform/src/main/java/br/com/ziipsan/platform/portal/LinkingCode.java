package br.com.ziipsan.platform.portal;

import br.com.ziipsan.security.crypto.FieldProtector;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * Codigo de vinculacao impresso na fatura (ADR-0043): alta entropia
 * (CSPRNG, 20 simbolos de alfabeto de 64 = 120 bits), NUNCA a matricula
 * (numerica e sequencial). No banco vive so o indice cego do codigo
 * (contexto proprio); a conferencia e em tempo constante sobre os indices.
 */
public final class LinkingCode {

    public static final String CONTEXT = "portal.linking_code";
    private static final String ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    private static final int LENGTH = 20;
    private static final SecureRandom RANDOM = new SecureRandom();

    private LinkingCode() { }

    public static String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Formato aceitavel: comprimento minimo e nao puramente numerico (matricula nao e codigo). */
    public static boolean hasCodeShape(String candidate) {
        if (candidate == null || candidate.length() < 16) {
            return false;
        }
        boolean hasLetter = false;
        for (char c : candidate.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '-' && c != '_') {
                return false;
            }
            if (Character.isLetter(c)) {
                hasLetter = true;
            }
        }
        return hasLetter;
    }

    public static String storageHash(FieldProtector protector, UUID tenantId, String code) {
        if (!hasCodeShape(code)) {
            throw new IllegalArgumentException("codigo de vinculacao fora do formato de alta entropia");
        }
        return protector.indexOf(tenantId, CONTEXT, code);
    }

    public static boolean matches(FieldProtector protector, UUID tenantId, String storedHash, String candidate) {
        if (storedHash == null || !hasCodeShape(candidate)) {
            return false;
        }
        String candidateHash = protector.indexOf(tenantId, CONTEXT, candidate);
        return MessageDigest.isEqual(
                storedHash.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                candidateHash.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
