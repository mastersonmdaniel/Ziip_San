package br.com.ziipsan.platform.omnichannel;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookSecurityTest {

    // Vetor público conhecido de HMAC-SHA-256 (chave "key", mensagem clássica):
    // conferível à mão/fora deste código.
    @Test
    void hmacKnownVector() {
        String hex = WebhookSecurity.hmacSha256Hex("key",
                "The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8));
        assertThat(hex).isEqualTo("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
    }

    @Test
    void metaSignatureAcceptedAndTamperRejected() {
        byte[] payload = "{\"object\":\"whatsapp_business_account\"}".getBytes(StandardCharsets.UTF_8);
        String header = "sha256=" + WebhookSecurity.hmacSha256Hex(OmnichannelTestSupport.META_SECRET, payload);
        assertThat(WebhookSecurity.verifyHmacSignature(OmnichannelTestSupport.META_SECRET, payload, header)).isTrue();
        byte[] tampered = "{\"object\":\"whatsapp_business_account\",\"x\":1}".getBytes(StandardCharsets.UTF_8);
        assertThat(WebhookSecurity.verifyHmacSignature(OmnichannelTestSupport.META_SECRET, tampered, header)).isFalse();
        assertThat(WebhookSecurity.verifyHmacSignature("outro-segredo", payload, header)).isFalse();
    }

    @Test
    void missingPiecesFailClosed() {
        byte[] payload = "{}".getBytes(StandardCharsets.UTF_8);
        assertThat(WebhookSecurity.verifyHmacSignature(null, payload, "sha256=abc")).isFalse();
        assertThat(WebhookSecurity.verifyHmacSignature("segredo", payload, null)).isFalse();
        assertThat(WebhookSecurity.verifyHmacSignature("segredo", payload, "sha256=")).isFalse();
        assertThat(WebhookSecurity.verifySecretToken(null, "x")).isFalse();
        assertThat(WebhookSecurity.verifySecretToken("segredo", null)).isFalse();
        assertThatThrownBy(() -> WebhookSecurity.hmacSha256Hex(" ", payload))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void telegramSecretToken() {
        assertThat(WebhookSecurity.verifySecretToken(
                OmnichannelTestSupport.TELEGRAM_SECRET, OmnichannelTestSupport.TELEGRAM_SECRET)).isTrue();
        assertThat(WebhookSecurity.verifySecretToken(
                OmnichannelTestSupport.TELEGRAM_SECRET, "segredo-errado")).isFalse();
    }

    @Test
    void hmacWithoutSha256PrefixRejected() {
        // D-03: o hex correto SEM o prefixo sha256= não passa — a Meta
        // sempre envia o prefixo; formato fora da especificação é recusado.
        byte[] payload = "{}".getBytes(StandardCharsets.UTF_8);
        String bareHex = WebhookSecurity.hmacSha256Hex(OmnichannelTestSupport.META_SECRET, payload);
        assertThat(WebhookSecurity.verifyHmacSignature(
                OmnichannelTestSupport.META_SECRET, payload, bareHex)).isFalse();
        assertThat(WebhookSecurity.verifyHmacSignature(
                OmnichannelTestSupport.META_SECRET, payload, "sha256=" + bareHex)).isTrue();
    }

    @Test
    void telegramTokenWithSurroundingSpacesRejected() {
        // D-03: token comparado como enviado, sem trim — espaços finais
        // (ou iniciais) no cabeçalho recusam, mesmo com o segredo certo.
        assertThat(WebhookSecurity.verifySecretToken(
                OmnichannelTestSupport.TELEGRAM_SECRET, OmnichannelTestSupport.TELEGRAM_SECRET + "  "))
                .isFalse();
        assertThat(WebhookSecurity.verifySecretToken(
                OmnichannelTestSupport.TELEGRAM_SECRET, " " + OmnichannelTestSupport.TELEGRAM_SECRET))
                .isFalse();
    }

    @Test
    void channelWebhookModesMatchAcceptance() {
        assertThat(OmnichannelChannel.WHATSAPP.webhookMode())
                .isEqualTo(OmnichannelChannel.WebhookMode.META_HMAC_SHA256);
        assertThat(OmnichannelChannel.MESSENGER.family()).isEqualTo(OmnichannelChannel.Family.META);
        assertThat(OmnichannelChannel.INSTAGRAM.family()).isEqualTo(OmnichannelChannel.Family.META);
        assertThat(OmnichannelChannel.TELEGRAM.family())
                .isEqualTo(OmnichannelChannel.Family.TELEGRAM_BOT_API);
        assertThat(OmnichannelChannel.TELEGRAM.webhookMode())
                .isEqualTo(OmnichannelChannel.WebhookMode.TELEGRAM_SECRET_TOKEN);
        assertThat(OmnichannelChannel.PORTAL_CHAT.webhookMode())
                .isEqualTo(OmnichannelChannel.WebhookMode.AUTHENTICATED_SESSION);
        assertThat(OmnichannelChannel.PORTAL_CHAT.requiresOptInForReply()).isFalse();
        assertThat(OmnichannelChannel.WHATSAPP.requiresOptInForReply()).isTrue();
    }
}
