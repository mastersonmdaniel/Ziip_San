package br.com.ziipsan.integration.webhook;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Envio HTTP das entregas (P2.26).
 *
 * <p>Cliente sem follow-redirect (3xx é falha da tentativa, nunca um
 * novo destino), timeouts de 10 s, e validação SSRF da URL
 * imediatamente antes do envio. A interface existe para o teste injetar
 * um remetente fixo e provar assinatura, ordem e reenvio sem rede.
 */
public interface WebhookHttpSender {

    record Result(int status, String locationHeader) {
    }

    Result send(URI url, String body, String eventId, long timestampSeconds, String signature) throws Exception;

    /** Implementação real, sobre {@link HttpClient} do JDK. */
    class Default implements WebhookHttpSender {

        private final HttpClient client;
        private final SsrfUrlValidator.DnsResolver resolver;

        public Default() {
            this(HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build(), SsrfUrlValidator.SYSTEM);
        }

        public Default(HttpClient client, SsrfUrlValidator.DnsResolver resolver) {
            this.client = client;
            this.resolver = resolver;
        }

        @Override
        public Result send(URI url, String body, String eventId, long timestampSeconds, String signature)
                throws Exception {
            // Validação SSRF no envio (a URL já foi validada na criação do
            // endpoint; aqui vale a resolução do momento do envio).
            SsrfUrlValidator.validate(url.toString(), resolver);
            HttpRequest request = HttpRequest.newBuilder(url)
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("X-Ziipsan-Event-Id", eventId)
                    .header("X-Ziipsan-Timestamp", String.valueOf(timestampSeconds))
                    .header("X-Ziipsan-Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return new Result(response.statusCode(),
                    response.headers().firstValue("Location").orElse(null));
        }
    }
}
