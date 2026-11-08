package br.com.ziipsan.platform.signing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 em hexadecimal minusculo — o formato de {@code content_hash} da P1.24 e da V1_14. */
public final class SignatureHashes {

    private SignatureHashes() {
    }

    public static String sha256Hex(byte[] content) {
        if (content == null) {
            throw new IllegalArgumentException("conteudo e obrigatorio para o hash");
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel na JVM", e);
        }
    }

    public static String sha256Hex(String text) {
        if (text == null) {
            throw new IllegalArgumentException("texto e obrigatorio para o hash");
        }
        return sha256Hex(text.getBytes(StandardCharsets.UTF_8));
    }

    /** Formato exigido pela coluna: 64 hex minusculos. */
    public static boolean isSha256Hex(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }
}
