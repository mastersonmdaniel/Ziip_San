package br.com.ziipsan.integration.webhook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Assinatura HMAC-SHA-256 das entregas de webhook (P2.26).
 *
 * <p>Mensagem assinada: {@code <timestamp>.<event_id>.<corpo>} — o carimbo
 * de tempo (segundos desde a época) viaja no cabeçalho
 * {@code X-Ziipsan-Timestamp} e o receptor o rejeita fora de uma janela
 * de 5 minutos (contra repetição). Cabeçalhos enviados:
 * {@code X-Ziipsan-Event-Id}, {@code X-Ziipsan-Timestamp} e
 * {@code X-Ziipsan-Signature: v1=<hex>}.
 *
 * <p>Verificação com sobreposição: na rotação, o segredo anterior continua
 * válido até {@code previous_valid_until}; o receptor (e o teste de
 * verificação) aceita a assinatura feita com qualquer um dos dois.
 */
public final class WebhookSigner {

    /** Janela de aceitação do carimbo de tempo, em segundos (contra repetição). */
    public static final long TIMESTAMP_TOLERANCE_SECONDS = 300;

    private WebhookSigner() {
    }

    /** Assina a entrega com o segredo dado. */
    public static String sign(String secret, long timestampSeconds, String eventId, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String message = timestampSeconds + "." + eventId + "." + body;
            return "v1=" + HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao assinar a entrega de webhook", e);
        }
    }

    /**
     * Verifica a assinatura contra o segredo vigente e, se houver, o
     * anterior (sobreposição da rotação). O carimbo precisa estar dentro
     * da janela de tolerância em relação a {@code nowSeconds}.
     */
    public static boolean verify(String signatureHeader, String currentSecret, String previousSecret,
                                 long timestampSeconds, long nowSeconds, String eventId, String body) {
        if (signatureHeader == null || currentSecret == null) {
            return false;
        }
        if (Math.abs(nowSeconds - timestampSeconds) > TIMESTAMP_TOLERANCE_SECONDS) {
            return false;
        }
        if (constantTimeEquals(signatureHeader, sign(currentSecret, timestampSeconds, eventId, body))) {
            return true;
        }
        return previousSecret != null
                && constantTimeEquals(signatureHeader, sign(previousSecret, timestampSeconds, eventId, body));
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
