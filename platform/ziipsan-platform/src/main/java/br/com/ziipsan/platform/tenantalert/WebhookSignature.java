package br.com.ziipsan.platform.tenantalert;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/**
 * ED-07/P2.26: a assinatura do webhook de saída — HMAC-SHA-256 em hexadecimal
 * minúsculo sobre a forma canônica {@code <epochSegundos>.<cargaJson>}, com o
 * carimbo de tempo DENTRO da assinatura contra repetição (aceite da P2.26:
 * "assinatura HMAC-SHA-256 com carimbo de tempo contra repetição"). É o mesmo
 * mecanismo da assinatura do envelope do supervisório da ED-17.
 *
 * <p>O segredo é por endpoint/tenant e vive no cofre da fiação (porta
 * {@link TenantAlertPorts.WebhookEndpoints}); esta classe nunca o guarda,
 * nunca o registra em log e compara em tempo constante.
 */
public final class WebhookSignature {

    private WebhookSignature() {
    }

    public static String canonical(long timestampEpochSeconds, String payloadJson) {
        Objects.requireNonNull(payloadJson, "a carga do webhook é obrigatória");
        return timestampEpochSeconds + "." + payloadJson;
    }

    public static String sign(String secret, long timestampEpochSeconds, String payloadJson) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("o segredo do endpoint é obrigatório para assinar");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(
                    canonical(timestampEpochSeconds, payloadJson).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA-256 indisponível para assinar o webhook", e);
        }
    }

    public static boolean verify(String secret, long timestampEpochSeconds, String payloadJson, String signature) {
        if (secret == null || signature == null) {
            return false;
        }
        String expected = sign(secret, timestampEpochSeconds, payloadJson);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    /** Carimbo dentro da tolerância (nos dois sentidos) = não é repetição velha nem relógio adulterado. */
    public static boolean isTimestampFresh(long timestampEpochSeconds, Instant now, Duration tolerance) {
        Objects.requireNonNull(now, "o instante de referência é obrigatório");
        Objects.requireNonNull(tolerance, "a tolerância de repetição é obrigatória");
        if (tolerance.isNegative() || tolerance.isZero()) {
            throw new IllegalArgumentException("a tolerância de repetição deve ser positiva: " + tolerance);
        }
        long delta = Math.abs(now.getEpochSecond() - timestampEpochSeconds);
        return delta <= tolerance.getSeconds();
    }
}
