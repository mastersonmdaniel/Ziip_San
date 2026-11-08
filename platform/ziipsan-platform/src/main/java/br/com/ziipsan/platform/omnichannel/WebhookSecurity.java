package br.com.ziipsan.platform.omnichannel;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Conferência de webhook (aceite da ED-42: "webhook com assinatura ou
 * segredo conferido"), FAIL-CLOSED: segredo ausente, assinatura ausente
 * ou divergente = webhook RECUSADO, sem exceção silenciosa e sem
 * "aceitar em desenvolvimento". O segredo vem do cofre pela porta
 * ({@code OmnichannelPorts.CredentialVaultPort}) no instante da
 * conferência — nunca de constante, arquivo ou log deste código.
 *
 * <ul>
 *   <li><b>Meta (WhatsApp/Messenger/Instagram) e provedor de e-mail:</b>
 *       cabeçalho de assinatura no formato {@code sha256=<hex>} do
 *       HMAC-SHA-256 da carga bruta com o segredo do aplicativo/provedor
 *       do tenant;</li>
 *   <li><b>Telegram (Bot API):</b> cabeçalho do segredo definido no
 *       registro do webhook, comparado em tempo constante;</li>
 *   <li><b>chat do portal:</b> sem webhook — ver
 *       {@link OmnichannelChannel.WebhookMode#AUTHENTICATED_SESSION}.</li>
 * </ul>
 *
 * As comparações usam {@link MessageDigest#isEqual} (tempo constante
 * sobre o conteúdo) para não vazar o segredo por temporização.
 */
public final class WebhookSecurity {

    private WebhookSecurity() {
    }

    /** HMAC-SHA-256 em hex minúsculo da carga com o segredo — primitiva conferível à mão com vetor de teste. */
    public static String hmacSha256Hex(String secret, byte[] payload) {
        if (secret == null || secret.isBlank() || payload == null) {
            throw new IllegalArgumentException("segredo do cofre e carga bruta são obrigatórios na conferência");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 indisponível nesta JVM", e);
        }
    }

    /**
     * Verdade se o cabeçalho {@code sha256=<hex>} confere com a carga e o
     * segredo do cofre. O prefixo {@code sha256=} é EXIGIDO (a Meta sempre
     * o envia): hex correto sem o prefixo é recusado — formato fora da
     * especificação do provedor não passa, mesmo conhecendo o HMAC.
     */
    public static boolean verifyHmacSignature(String secret, byte[] payload, String signatureHeader) {
        if (secret == null || secret.isBlank() || payload == null
                || signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        if (!signatureHeader.startsWith("sha256=")) {
            return false;
        }
        String provided = signatureHeader.substring("sha256=".length());
        if (provided.isBlank()) {
            return false;
        }
        String expected = hmacSha256Hex(secret, payload);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                provided.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Verdade se o segredo do cabeçalho (Bot API do Telegram) é exatamente
     * o segredo do cofre. A comparação é sobre o cabeçalho COMO ENVIADO,
     * sem {@code trim}: token com espaços à volta é recusado.
     */
    public static boolean verifySecretToken(String expectedSecret, String providedHeader) {
        if (expectedSecret == null || expectedSecret.isBlank()
                || providedHeader == null || providedHeader.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(expectedSecret.getBytes(StandardCharsets.UTF_8),
                providedHeader.getBytes(StandardCharsets.UTF_8));
    }
}
