package br.com.ziipsan.platform.tenantalert;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * ED-07/P2.26: a entrega confiável do webhook de um alerta — pelo menos uma
 * vez, com {@code event_id} único (o id do alerta), assinatura HMAC com
 * carimbo ({@link WebhookSignature}), tentativas com recuo exponencial e
 * jitter ({@link WebhookDeliveryPolicy}) e desativação automática do
 * endpoint depois das falhas consecutivas da política, com o desfecho
 * registrado tentativa a tentativa.
 *
 * <p>A carga é montada pelo chamador SÓ com identificadores e códigos
 * (regra da P1.38/P2.26: carga útil sem dado pessoal). A ordem por chave de
 * agregado da P2.26 é do transporte/fila da P2.26: esta entrega processa um
 * alerta por vez, na ordem em que o serviço o gera — declarado no relatório.
 *
 * <p>Antes de cada tentativa, a requisição recém-assinada passa pela
 * verificação REAL do receptor ({@link WebhookSignatureVerifier}) com a
 * tolerância da política (correção D3 pós-reprovação: o anti-replay
 * {@code isTimestampFresh} e o {@code replayTolerance}, antes sem chamador
 * produtivo, agora são aplicados no fluxo): uma requisição que um receptor
 * conforme recusaria nunca é despachada — a falha é de programação/relógio
 * e aborta a entrega com exceção, em vez de queimar tentativas de rede.
 */
public final class ReliableWebhookDelivery {

    public enum Outcome { DELIVERED, FAILED, ENDPOINT_DISABLED, NOT_CONFIGURED }

    public record Attempt(int number, Instant at, boolean delivered, Integer httpStatus, String error) {
    }

    public record Result(Outcome outcome, List<Attempt> attempts, boolean endpointDisabledNow) {
        public Result {
            attempts = List.copyOf(attempts);
        }
    }

    private final TenantAlertPorts.WebhookEndpoints endpoints;
    private final TenantAlertPorts.WebhookTransport transport;
    private final TenantAlertPorts.WebhookStateStore states;
    private final TenantAlertPorts.Sleeper sleeper;
    private final LongSupplier jitterSource;
    private final Clock clock;

    public ReliableWebhookDelivery(TenantAlertPorts.WebhookEndpoints endpoints,
                                   TenantAlertPorts.WebhookTransport transport,
                                   TenantAlertPorts.WebhookStateStore states,
                                   TenantAlertPorts.Sleeper sleeper,
                                   LongSupplier jitterSource, Clock clock) {
        this.endpoints = Objects.requireNonNull(endpoints, "a porta de endpoints é obrigatória");
        this.transport = Objects.requireNonNull(transport, "a porta de transporte é obrigatória");
        this.states = Objects.requireNonNull(states, "a porta de estado do webhook é obrigatória");
        this.sleeper = Objects.requireNonNull(sleeper, "a porta de espera é obrigatória");
        this.jitterSource = Objects.requireNonNull(jitterSource, "a fonte de jitter é obrigatória");
        this.clock = Objects.requireNonNull(clock, "o relógio é obrigatório");
    }

    public Result deliver(UUID tenantId, UUID eventId, String eventName, String payloadJson,
                          WebhookDeliveryPolicy policy) {
        Objects.requireNonNull(policy, "a política de entrega é obrigatória");
        var endpointOpt = endpoints.endpointFor(tenantId, eventName);
        if (endpointOpt.isEmpty()) {
            return new Result(Outcome.NOT_CONFIGURED, List.of(), false);
        }
        var endpoint = endpointOpt.get();
        var state = states.stateOf(tenantId, eventName);
        if (!endpoint.enabled() || state.disabled()) {
            return new Result(Outcome.ENDPOINT_DISABLED, List.of(), false);
        }
        List<Attempt> attempts = new ArrayList<>();
        for (int n = 1; n <= policy.maxAttempts(); n++) {
            long ts = clock.instant().getEpochSecond();
            String signature = WebhookSignature.sign(endpoint.secret(), ts, payloadJson);
            var request = new TenantAlertPorts.SignedWebhookRequest(
                    tenantId, eventId, endpoint.url(), payloadJson, ts, signature);
            // Correção D3: autochecagem anti-replay/assinatura no fluxo real,
            // com a tolerância da política, ANTES do transporte.
            if (!WebhookSignatureVerifier.accept(request, endpoint.secret(), clock.instant(),
                    policy.replayTolerance())) {
                throw new IllegalStateException(
                        "webhook recém-assinado reprovado na verificação (assinatura ou carimbo fora "
                                + "da tolerância da política) — entrega abortada antes do transporte");
            }
            TenantAlertPorts.TransportResult sent;
            try {
                sent = transport.send(request);
            } catch (RuntimeException e) {
                sent = TenantAlertPorts.TransportResult.failed(e.getClass().getSimpleName());
            }
            attempts.add(new Attempt(n, clock.instant(), sent.delivered(), sent.httpStatus(), sent.error()));
            if (sent.delivered()) {
                states.save(new TenantAlertPorts.WebhookEndpointState(tenantId, eventName, 0, false));
                return new Result(Outcome.DELIVERED, attempts, false);
            }
            if (policy.hasAnotherAttempt(n)) {
                sleeper.sleep(policy.backoffForRetry(n, jitterSource));
            }
        }
        int failures = state.consecutiveFailures() + 1;
        boolean disableNow = failures >= policy.consecutiveFailuresToDisable();
        states.save(new TenantAlertPorts.WebhookEndpointState(tenantId, eventName, failures, disableNow));
        return new Result(Outcome.FAILED, attempts, disableNow);
    }
}
