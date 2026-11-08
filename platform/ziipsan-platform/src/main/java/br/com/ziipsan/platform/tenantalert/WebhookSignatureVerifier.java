package br.com.ziipsan.platform.tenantalert;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * ED-07/P2.26 (correção D3 pós-reprovação): a verificação do webhook NO
 * FLUXO REAL — o lado receptor da assinatura. Até a correção,
 * {@link WebhookSignature#isTimestampFresh} era só biblioteca testada, sem
 * nenhum chamador produtivo: o carimbo anti-repetição existia no papel, mas
 * nada o aplicava. Este verificador é a aplicação: uma entrega só é aceita
 * se a assinatura HMAC confere (tempo constante, via
 * {@link WebhookSignature#verify}) E o carimbo está dentro da tolerância
 * (via {@link WebhookSignature#isTimestampFresh}) — assinatura válida com
 * carimbo velho é REPETIÇÃO e é recusada.
 *
 * <p>Quem o usa de verdade nesta entrega:
 * <ul>
 *   <li>o RECEPTOR do webhook (contrato da P2.26 e passo 1 do ensaio do
 *       SAMAE 2.5): verifica cada {@link TenantAlertPorts.SignedWebhookRequest}
 *       recebida antes de processá-la;</li>
 *   <li>a própria {@link ReliableWebhookDelivery}, como autochecagem antes
 *       do transporte: ela só despacha uma requisição que um receptor
 *       conforme aceitaria, usando a tolerância da política
 *       ({@link WebhookDeliveryPolicy#replayTolerance()}) — a tolerância,
 *       que também nunca era consumida, passa a ter chamador produtivo.</li>
 * </ul>
 */
public final class WebhookSignatureVerifier {

    private WebhookSignatureVerifier() {
    }

    /** Aceita SÓ com assinatura válida E carimbo fresco; qualquer falha = recusa (sem exceção, sem detalhe vazado). */
    public static boolean accept(String secret, long timestampEpochSeconds, String payloadJson,
                                 String signature, Instant now, Duration tolerance) {
        Objects.requireNonNull(now, "o instante de verificação é obrigatório");
        Objects.requireNonNull(tolerance, "a tolerância de repetição é obrigatória");
        if (!WebhookSignature.isTimestampFresh(timestampEpochSeconds, now, tolerance)) {
            return false;
        }
        return WebhookSignature.verify(secret, timestampEpochSeconds, payloadJson, signature);
    }

    /** A mesma verificação sobre a requisição assinada como a porta de transporte a carrega. */
    public static boolean accept(TenantAlertPorts.SignedWebhookRequest request, String secret,
                                 Instant now, Duration tolerance) {
        Objects.requireNonNull(request, "a requisição assinada é obrigatória");
        return accept(secret, request.timestampEpochSeconds(), request.payloadJson(),
                request.signature(), now, tolerance);
    }
}
