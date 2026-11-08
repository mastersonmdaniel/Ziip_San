package br.com.ziipsan.integration.hub;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P3.37: despacho do hub — ordem das regras conferida caso a caso com
 * conector falso roteirizado. Recuos esperados à mão pela política
 * padrão (500 ms, depois 1000 ms).
 */
class HubDispatchServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-07T12:00:00Z");
    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private static class FakeConnector implements ExternalConnector {
        private final List<Outcome> script = new ArrayList<>();
        private int calls;

        FakeConnector(Outcome... outcomes) {
            script.addAll(List.of(outcomes));
        }

        @Override public String name() { return "psp-pix"; }
        @Override public Kind kind() { return Kind.PSP; }
        @Override public String partnerVersion() { return "2.1"; }

        @Override
        public ConnectionTestResult testConnection(ConnectorContext context) {
            return new ConnectionTestResult(true, 3, "ok", T0);
        }

        @Override
        public ConnectorResponse send(ConnectorContext context, ConnectorRequest request) {
            calls++;
            Outcome outcome = script.isEmpty() ? Outcome.SUCCESS : script.remove(0);
            return new ConnectorResponse(outcome, "ext-1", "{}", outcome == Outcome.SUCCESS ? null : "detalhe");
        }

        @Override
        public String homologationLayoutFingerprint(ConnectorContext homologationContext) {
            return "hash";
        }
    }

    private static final class Fixture {
        final FakeConnector connector;
        final FailureQueueService queue = new FailureQueueService(new FailureQueueService.InMemoryFailureStore());
        final List<Duration> sleeps = new ArrayList<>();
        final HubDispatchService hub;
        final CircuitBreaker circuit = new CircuitBreaker(2, Duration.ofSeconds(60));
        final IntegrationRateLimiter limiter = new IntegrationRateLimiter(100, 6000.0);

        Fixture(FakeConnector connector) {
            this.connector = connector;
            Map<String, ExternalConnector> byName = Map.of("psp-pix", connector);
            this.hub = new HubDispatchService(name -> Optional.ofNullable(byName.get(name)),
                    new HubDispatchService.InMemoryIdempotencyStore(), queue, sleeps::add);
        }

        HubDispatchService.DispatchResult dispatch(IntegrationMode mode, String idemKey, RetryPolicy policy) {
            return hub.dispatch(TENANT, "psp-pix", mode, "vault:ref",
                    new ExternalConnector.ConnectorRequest("corr-1", idemKey, "send", "{\"token\":\"abc\"}"),
                    policy, circuit, limiter, 3, T0);
        }
    }

    @Test
    @DisplayName("sucesso de primeira: 1 tentativa, sem espera, log mascarado com o correlation_id")
    void successFirstTry() {
        Fixture f = new Fixture(new FakeConnector(ExternalConnector.Outcome.SUCCESS));
        HubDispatchService.DispatchResult result = f.dispatch(IntegrationMode.SANDBOX, "idem-1", RetryPolicy.DEFAULT);
        assertThat(result.status()).isEqualTo(HubDispatchService.DispatchStatus.SUCCESS);
        assertThat(result.attempts()).isEqualTo(1);
        assertThat(f.connector.calls).isEqualTo(1);
        assertThat(f.sleeps).isEmpty();
        assertThat(result.log().correlationId()).isEqualTo("corr-1");
        assertThat(result.log().maskedPayloadJson()).isEqualTo("{\"token\":\"***\"}");
        assertThat(f.queue.pendingCount(TENANT, "psp-pix")).isZero();
    }

    @Test
    @DisplayName("replay idempotente: mesma chave devolve o guardado SEM nova chamada ao parceiro")
    void idempotentReplay() {
        Fixture f = new Fixture(new FakeConnector(ExternalConnector.Outcome.SUCCESS));
        f.dispatch(IntegrationMode.SANDBOX, "idem-1", RetryPolicy.DEFAULT);
        HubDispatchService.DispatchResult replay = f.dispatch(IntegrationMode.SANDBOX, "idem-1", RetryPolicy.DEFAULT);
        assertThat(replay.status()).isEqualTo(HubDispatchService.DispatchStatus.IDEMPOTENT_REPLAY);
        assertThat(replay.attempts()).isZero();
        assertThat(f.connector.calls).isEqualTo(1);
        // Chave diferente é outra operação: chama de novo.
        HubDispatchService.DispatchResult other = f.dispatch(IntegrationMode.SANDBOX, "idem-2", RetryPolicy.DEFAULT);
        assertThat(other.status()).isEqualTo(HubDispatchService.DispatchStatus.SUCCESS);
        assertThat(f.connector.calls).isEqualTo(2);
    }

    @Test
    @DisplayName("SIMULATED não chama o parceiro e o desfecho é explícito")
    void simulatedNeverCalls() {
        Fixture f = new Fixture(new FakeConnector(ExternalConnector.Outcome.SUCCESS));
        HubDispatchService.DispatchResult result = f.dispatch(IntegrationMode.SIMULATED, "idem-1", RetryPolicy.DEFAULT);
        assertThat(result.status()).isEqualTo(HubDispatchService.DispatchStatus.SIMULATED);
        assertThat(f.connector.calls).isZero();
        assertThat(result.response()).isNull();
    }

    @Test
    @DisplayName("UNAVAILABLE repete pela política: 2 falhas + sucesso = 3 tentativas e recuos 500/1000 ms")
    void retriesUnavailableThenSuccess() {
        Fixture f = new Fixture(new FakeConnector(ExternalConnector.Outcome.UNAVAILABLE,
                ExternalConnector.Outcome.UNAVAILABLE, ExternalConnector.Outcome.SUCCESS));
        HubDispatchService.DispatchResult result = f.dispatch(IntegrationMode.SANDBOX, "idem-1", RetryPolicy.DEFAULT);
        assertThat(result.status()).isEqualTo(HubDispatchService.DispatchStatus.SUCCESS);
        assertThat(result.attempts()).isEqualTo(3);
        assertThat(f.sleeps).containsExactly(Duration.ofMillis(500), Duration.ofMillis(1000));
        assertThat(f.circuit.consecutiveFailures()).isZero();
    }

    @Test
    @DisplayName("UNAVAILABLE até esgotar: FAILED_QUEUED, fila com 1 pendente e circuito conta UMA falha")
    void exhaustedGoesToFailureQueue() {
        Fixture f = new Fixture(new FakeConnector(ExternalConnector.Outcome.UNAVAILABLE,
                ExternalConnector.Outcome.UNAVAILABLE, ExternalConnector.Outcome.UNAVAILABLE));
        HubDispatchService.DispatchResult result = f.dispatch(IntegrationMode.SANDBOX, "idem-1", RetryPolicy.DEFAULT);
        assertThat(result.status()).isEqualTo(HubDispatchService.DispatchStatus.FAILED_QUEUED);
        assertThat(result.attempts()).isEqualTo(3);
        assertThat(f.queue.pendingCount(TENANT, "psp-pix")).isEqualTo(1);
        assertThat(f.circuit.consecutiveFailures()).isEqualTo(1);
    }

    @Test
    @DisplayName("REJECTED não repete, não enfileira, não abre circuito — e o replay devolve a mesma recusa")
    void rejectedIsFinal() {
        Fixture f = new Fixture(new FakeConnector(ExternalConnector.Outcome.REJECTED));
        HubDispatchService.DispatchResult result = f.dispatch(IntegrationMode.SANDBOX, "idem-1", RetryPolicy.DEFAULT);
        assertThat(result.status()).isEqualTo(HubDispatchService.DispatchStatus.REJECTED);
        assertThat(result.attempts()).isEqualTo(1);
        assertThat(f.sleeps).isEmpty();
        assertThat(f.queue.pendingCount(TENANT, "psp-pix")).isZero();
        assertThat(f.circuit.consecutiveFailures()).isZero();
        HubDispatchService.DispatchResult replay = f.dispatch(IntegrationMode.SANDBOX, "idem-1", RetryPolicy.DEFAULT);
        assertThat(replay.status()).isEqualTo(HubDispatchService.DispatchStatus.REJECTED);
        assertThat(f.connector.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("limite de taxa e circuito aberto recusam LOCALMENTE, sem chamada e sem fila")
    void localRefusals() {
        Fixture f = new Fixture(new FakeConnector(ExternalConnector.Outcome.SUCCESS));
        IntegrationRateLimiter tiny = new IntegrationRateLimiter(1, 1.0);
        assertThat(tiny.tryAcquire(T0)).isTrue();
        HubDispatchService.DispatchResult limited = f.hub.dispatch(TENANT, "psp-pix", IntegrationMode.SANDBOX,
                "vault:ref", new ExternalConnector.ConnectorRequest("corr-1", "idem-rl", "send", "{}"),
                RetryPolicy.DEFAULT, f.circuit, tiny, 3, T0);
        assertThat(limited.status()).isEqualTo(HubDispatchService.DispatchStatus.RATE_LIMITED);
        assertThat(f.connector.calls).isZero();

        CircuitBreaker open = new CircuitBreaker(1, Duration.ofSeconds(60));
        open.recordFailure(T0);
        HubDispatchService.DispatchResult circuitOpen = f.hub.dispatch(TENANT, "psp-pix", IntegrationMode.SANDBOX,
                "vault:ref", new ExternalConnector.ConnectorRequest("corr-1", "idem-co", "send", "{}"),
                RetryPolicy.DEFAULT, open, f.limiter, 3, T0);
        assertThat(circuitOpen.status()).isEqualTo(HubDispatchService.DispatchStatus.CIRCUIT_OPEN);
        assertThat(f.connector.calls).isZero();
        assertThat(f.queue.pendingCount(TENANT, "psp-pix")).isZero();
    }

    @Test
    @DisplayName("teste de conexão passa pela porta do conector")
    void connectionTestThroughPort() {
        Fixture f = new Fixture(new FakeConnector());
        ExternalConnector.ConnectionTestResult result =
                f.hub.testConnection(TENANT, "psp-pix", IntegrationMode.SANDBOX, "vault:ref", "corr-1");
        assertThat(result.reachable()).isTrue();
        assertThat(result.latencyMillis()).isEqualTo(3);
    }
}
