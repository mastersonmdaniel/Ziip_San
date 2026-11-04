package br.com.ziipsan.integration.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Segredos da API pública (P2.26): gerados com {@link SecureRandom},
 * guardados só como hash SHA-256 hexadecimal e comparados em tempo
 * constante. O valor em claro só existe na resposta de criação/rotação
 * — nunca é gravado, nunca é lido de volta, nunca vai para log.
 */
public final class SecretHash {

    private static final SecureRandom RANDOM = new SecureRandom();

    private SecretHash() {
    }

    /** Novo segredo em claro (cliente ou webhook), com prefixo por tipo. */
    public static String generate(String prefix) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String secret) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao calcular o hash do segredo", e);
        }
    }

    /** Comparação em tempo constante entre o segredo apresentado e o hash guardado. */
    public static boolean matches(String presented, String storedHash) {
        if (presented == null || storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(hash(presented).getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }

    /** Verdadeiro se o segredo anterior ainda está na janela de sobreposição. */
    public static boolean previousStillValid(String previousHash, java.time.Instant previousValidUntil,
                                             java.time.Instant now) {
        return previousHash != null && previousValidUntil != null && now.isBefore(previousValidUntil);
    }
}
