package br.com.ziipsan.integration.hub;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * P3.37: o despacho do hub — a superfície de operação que aplica, numa
 * chamada só, as regras do aceite nesta ordem:
 * <ol>
 *   <li><b>idempotência</b>: mesma (tenant, integração, chave) já
 *       concluída devolve a resposta guardada SEM chamar o parceiro de
 *       novo;</li>
 *   <li><b>modo</b>: {@code SIMULATED} não chama o parceiro — devolve
 *       desfecho simulado explícito, com log; {@code SANDBOX} e
 *       {@code PRODUCTION} chamam (produção só chega aqui com o aceite
 *       conferido em {@code IntegrationParameters.operableMode});</li>
 *   <li><b>limite de taxa</b>: sem ficha, recusa local
 *       ({@code RATE_LIMITED}) — não é falha do parceiro, não abre
 *       circuito e não enfileira;</li>
 *   <li><b>circuito</b>: aberto, recusa local ({@code CIRCUIT_OPEN}),
 *       idem;</li>
 *   <li><b>repetição</b>: desfecho {@code UNAVAILABLE} (ou exceção do
 *       adaptador) repete pela {@link RetryPolicy}, esperando pelo
 *       {@link Sleeper} da fiação; {@code REJECTED} não repete;</li>
 *   <li><b>falha final</b>: esgotadas as tentativas, a mensagem entra na
 *       fila de falhas ({@link FailureQueueService}) para a tela de
 *       reprocessamento, e o circuito conta a falha UMA vez por
 *       despacho (não por tentativa);</li>
 *   <li><b>log</b>: cada despacho produz o {@link IntegrationCallLog}
 *       com a carga mascarada e o {@code correlation_id} da requisição.</li>
 * </ol>
 *
 * <p>O tempo limite por tentativa ({@code RetryPolicy.timeout}) é
 * aplicado pelo ADAPTADOR/HTTP da fiação (é lá que a chamada bloqueia);
 * o hub declara a política e a fiação a impõe — declarado no relatório.
 */
public final class HubDispatchService {

    public enum DispatchStatus {
        SUCCESS, REJECTED, SIMULATED, RATE_LIMITED, CIRCUIT_OPEN, FAILED_QUEUED, IDEMPOTENT_REPLAY
    }

    /** Resposta guardada de um despacho concluído (sucesso ou recusa de negócio). */
    public record CompletedDispatch(ExternalConnector.ConnectorResponse response) {
        public CompletedDispatch {
            Objects.requireNonNull(response, "resposta é obrigatória no despacho concluído");
        }
    }

    /** Porta da loja de idempotência do hub. */
    public interface IdempotencyStore {
        Optional<CompletedDispatch> find(UUID tenantId, String integrationName, String idempotencyKey);

        void save(UUID tenantId, String integrationName, String idempotencyKey, CompletedDispatch dispatch);
    }

    /** Implementação em memória (ambiente local e testes; a fiação troca pela persistente). */
    public static final class InMemoryIdempotencyStore implements IdempotencyStore {
        private final Map<String, CompletedDispatch> byKey = new LinkedHashMap<>();

        private static String key(UUID tenantId, String integrationName, String idempotencyKey) {
            return tenantId + "|" + integrationName + "|" + idempotencyKey;
        }

        @Override
        public synchronized Optional<CompletedDispatch> find(UUID tenantId, String integrationName, String idempotencyKey) {
            return Optional.ofNullable(byKey.get(key(tenantId, integrationName, idempotencyKey)));
        }

        @Override
        public synchronized void save(UUID tenantId, String integrationName, String idempotencyKey, CompletedDispatch dispatch) {
            byKey.putIfAbsent(key(tenantId, integrationName, idempotencyKey), dispatch);
        }
    }

    /** Espera entre repetições: a fiação dorme de verdade; o teste registra. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration);
    }

    /** Resultado de um despacho, com o log mascarado correspondente. */
    public record DispatchResult(DispatchStatus status, ExternalConnector.ConnectorResponse response,
                                 int attempts, IntegrationCallLog log) {
        public DispatchResult {
            Objects.requireNonNull(status, "situação do despacho é obrigatória");
            Objects.requireNonNull(log, "log do despacho é obrigatório");
            if (attempts < 0) {
                throw new IllegalArgumentException("tentativas do despacho não podem ser negativas: " + attempts);
            }
        }
    }

    private final ContractTestService.ConnectorRegistry registry;
    private final IdempotencyStore idempotencyStore;
    private final FailureQueueService failureQueue;
    private final Sleeper sleeper;

    public HubDispatchService(ContractTestService.ConnectorRegistry registry, IdempotencyStore idempotencyStore,
                              FailureQueueService failureQueue, Sleeper sleeper) {
        this.registry = Objects.requireNonNull(registry, "registro de conectores é obrigatório no despacho");
        this.idempotencyStore = Objects.requireNonNull(idempotencyStore, "loja de idempotência é obrigatória no despacho");
        this.failureQueue = Objects.requireNonNull(failureQueue, "fila de falhas é obrigatória no despacho");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper é obrigatório no despacho");
    }

    /**
     * Despacha uma requisição. {@code circuit} e {@code rateLimiter} são
     * os do par (tenant, integração), mantidos pela fiação;
     * {@code maxFailureAttempts} é o teto de tentativas da MENSAGEM na
     * fila (despacho + reprocessamentos).
     */
    public DispatchResult dispatch(UUID tenantId, String integrationName, IntegrationMode mode,
                                   String credentialRef, ExternalConnector.ConnectorRequest request,
                                   RetryPolicy retryPolicy, CircuitBreaker circuit,
                                   IntegrationRateLimiter rateLimiter, int maxFailureAttempts, Instant now) {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório no despacho");
        IntegrationParameters.requireValidName(integrationName);
        Objects.requireNonNull(mode, "modo é obrigatório no despacho");
        Objects.requireNonNull(request, "requisição é obrigatória no despacho");
        Objects.requireNonNull(retryPolicy, "política de repetição é obrigatória no despacho");
        Objects.requireNonNull(circuit, "circuito é obrigatório no despacho");
        Objects.requireNonNull(rateLimiter, "limite de taxa é obrigatório no despacho");
        Objects.requireNonNull(now, "instante é obrigatório no despacho");

        // 1. Idempotência: replay devolve o guardado sem tocar o parceiro.
        Optional<CompletedDispatch> prior = idempotencyStore.find(tenantId, integrationName, request.idempotencyKey());
        if (prior.isPresent()) {
            ExternalConnector.ConnectorResponse response = prior.get().response();
            // Replay de sucesso é IDEMPOTENT_REPLAY; de recusa, a recusa se
            // repete como REJECTED (o chamador lê o mesmo desfecho).
            DispatchStatus status = response.isSuccess() ? DispatchStatus.IDEMPOTENT_REPLAY : DispatchStatus.REJECTED;
            return new DispatchResult(status, response, 0,
                    log(tenantId, integrationName, mode, request, status.name(), now));
        }

        // 2. Modo simulado: nenhum parceiro é chamado.
        if (!mode.callsPartner()) {
            return new DispatchResult(DispatchStatus.SIMULATED, null, 0,
                    log(tenantId, integrationName, mode, request, DispatchStatus.SIMULATED.name(), now));
        }

        ExternalConnector connector = registry.connector(integrationName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "nenhum conector ligado à integração " + integrationName));

        // 3. Limite de taxa (recusa local).
        if (!rateLimiter.tryAcquire(now)) {
            return new DispatchResult(DispatchStatus.RATE_LIMITED, null, 0,
                    log(tenantId, integrationName, mode, request, DispatchStatus.RATE_LIMITED.name(), now));
        }

        // 4. Circuito aberto (recusa local).
        if (!circuit.allowRequest(now)) {
            return new DispatchResult(DispatchStatus.CIRCUIT_OPEN, null, 0,
                    log(tenantId, integrationName, mode, request, DispatchStatus.CIRCUIT_OPEN.name(), now));
        }

        // 5. Tentativas com repetição só para UNAVAILABLE/exceção.
        ExternalConnector.ConnectorContext context = new ExternalConnector.ConnectorContext(
                tenantId, mode, credentialRef, request.correlationId());
        int attempts = 0;
        ExternalConnector.ConnectorResponse response = null;
        while (true) {
            attempts++;
            try {
                response = connector.send(context, request);
            } catch (RuntimeException e) {
                response = new ExternalConnector.ConnectorResponse(
                        ExternalConnector.Outcome.UNAVAILABLE, null, null, e.getMessage());
            }
            if (response.outcome() != ExternalConnector.Outcome.UNAVAILABLE) {
                break;
            }
            if (!retryPolicy.hasAnotherAttempt(attempts)) {
                break;
            }
            sleeper.sleep(retryPolicy.backoffForRetry(attempts));
        }

        if (response.isSuccess()) {
            circuit.recordSuccess();
            idempotencyStore.save(tenantId, integrationName, request.idempotencyKey(), new CompletedDispatch(response));
            return new DispatchResult(DispatchStatus.SUCCESS, response, attempts,
                    log(tenantId, integrationName, mode, request, DispatchStatus.SUCCESS.name(), now));
        }
        if (response.outcome() == ExternalConnector.Outcome.REJECTED) {
            // Recusa de negócio: conclusão (não repete, não enfileira, não
            // abre circuito) e fica guardada para o replay idempotente.
            idempotencyStore.save(tenantId, integrationName, request.idempotencyKey(), new CompletedDispatch(response));
            return new DispatchResult(DispatchStatus.REJECTED, response, attempts,
                    log(tenantId, integrationName, mode, request, DispatchStatus.REJECTED.name(), now));
        }
        // 6. Falha final: circuito conta uma vez; mensagem vai para a fila.
        circuit.recordFailure(now);
        failureQueue.enqueue(tenantId, integrationName, request.operation(), request.correlationId(),
                request.idempotencyKey(), request.payloadJson(), maxFailureAttempts, response.detail(), now);
        return new DispatchResult(DispatchStatus.FAILED_QUEUED, response, attempts,
                log(tenantId, integrationName, mode, request, DispatchStatus.FAILED_QUEUED.name(), now));
    }

    /** Teste de conexão pela porta do conector (tela): não passa por taxa/circuito/repetição. */
    public ExternalConnector.ConnectionTestResult testConnection(UUID tenantId, String integrationName,
                                                                 IntegrationMode mode, String credentialRef,
                                                                 String correlationId) {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório no teste de conexão");
        IntegrationParameters.requireValidName(integrationName);
        ExternalConnector connector = registry.connector(integrationName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "nenhum conector ligado à integração " + integrationName));
        return connector.testConnection(new ExternalConnector.ConnectorContext(
                tenantId, mode, credentialRef, correlationId));
    }

    private static IntegrationCallLog log(UUID tenantId, String integrationName, IntegrationMode mode,
                                          ExternalConnector.ConnectorRequest request, String outcome, Instant at) {
        return IntegrationCallLog.of(tenantId, integrationName, request.operation(), request.correlationId(),
                mode, request.payloadJson(), outcome, at);
    }
}
