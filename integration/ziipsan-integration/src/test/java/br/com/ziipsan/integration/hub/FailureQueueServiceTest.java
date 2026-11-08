package br.com.ziipsan.integration.hub;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3.37: fila de falhas e tela de reprocessamento. Teto de 3 tentativas
 * da mensagem (1 do despacho + 2 reprocessos): números conferidos à mão
 * em cada transição.
 */
class FailureQueueServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-07T12:00:00Z");
    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private static class FakeConnector implements ExternalConnector {
        private final List<Outcome> script = new ArrayList<>();
        private final List<ConnectorRequest> seen = new ArrayList<>();

        FakeConnector(Outcome... outcomes) {
            script.addAll(List.of(outcomes));
        }

        @Override public String name() { return "psp-pix"; }
        @Override public Kind kind() { return Kind.PSP; }
        @Override public String partnerVersion() { return "2.1"; }

        @Override
        public ConnectionTestResult testConnection(ConnectorContext context) {
            return new ConnectionTestResult(true, 1, "ok", T0);
        }

        @Override
        public ConnectorResponse send(ConnectorContext context, ConnectorRequest request) {
            seen.add(request);
            Outcome outcome = script.isEmpty() ? Outcome.SUCCESS : script.remove(0);
            return new ConnectorResponse(outcome, "ext-9", "{}", outcome == Outcome.SUCCESS ? null : "falhou");
        }

        @Override
        public String homologationLayoutFingerprint(ConnectorContext homologationContext) {
            return "hash";
        }
    }

    private static ExternalConnector.ConnectorContext ctx() {
        return new ExternalConnector.ConnectorContext(TENANT, IntegrationMode.SANDBOX, "vault:ref", "corr-1");
    }

    private FailureQueueService service() {
        return new FailureQueueService(new FailureQueueService.InMemoryFailureStore());
    }

    @Test
    @DisplayName("enfileira PENDING com 1 tentativa contada e a visão da tela mascara a carga")
    void enqueueAndMaskedView() {
        FailureQueueService queue = service();
        FailureQueueService.FailedMessage message = queue.enqueue(TENANT, "psp-pix", "send",
                "corr-1", "idem-1", "{\"api_key\":\"segredo\",\"valor\":5}", 3, "timeout", T0);
        assertThat(message.status()).isEqualTo(FailureQueueService.FailureStatus.PENDING);
        assertThat(message.attempts()).isEqualTo(1);
        assertThat(queue.pendingCount(TENANT, "psp-pix")).isEqualTo(1);
        List<FailureQueueService.FailedMessageView> view = queue.queueView(TENANT);
        assertThat(view).hasSize(1);
        assertThat(view.get(0).maskedPayloadJson()).isEqualTo("{\"api_key\":\"***\",\"valor\":5}");
        assertThat(view.get(0).maskedPayloadJson()).doesNotContain("segredo");
    }

    @Test
    @DisplayName("reprocesso com sucesso resolve, repetindo a MESMA chave de idempotência e correlação")
    void reprocessSuccess() {
        FailureQueueService queue = service();
        FailureQueueService.FailedMessage message = queue.enqueue(TENANT, "psp-pix", "send",
                "corr-1", "idem-1", "{}", 3, "timeout", T0);
        FakeConnector fake = new FakeConnector(ExternalConnector.Outcome.SUCCESS);
        FailureQueueService.FailedMessage updated = queue.reprocess(message.id(), fake, ctx(), T0.plusSeconds(60));
        assertThat(updated.status()).isEqualTo(FailureQueueService.FailureStatus.RESOLVED);
        assertThat(updated.attempts()).isEqualTo(2);
        assertThat(fake.seen).hasSize(1);
        assertThat(fake.seen.get(0).idempotencyKey()).isEqualTo("idem-1");
        assertThat(fake.seen.get(0).correlationId()).isEqualTo("corr-1");
        assertThat(queue.pendingCount(TENANT, "psp-pix")).isZero();
    }

    @Test
    @DisplayName("falhas contam tentativa e a mensagem morre ao atingir o teto (3): reprocessar morta é erro")
    void reprocessDiesAtCap() {
        FailureQueueService queue = service();
        FailureQueueService.FailedMessage message = queue.enqueue(TENANT, "psp-pix", "send",
                "corr-1", "idem-1", "{}", 3, "timeout", T0);
        FakeConnector fake = new FakeConnector(
                ExternalConnector.Outcome.UNAVAILABLE, ExternalConnector.Outcome.UNAVAILABLE);
        FailureQueueService.FailedMessage first = queue.reprocess(message.id(), fake, ctx(), T0.plusSeconds(60));
        assertThat(first.status()).isEqualTo(FailureQueueService.FailureStatus.PENDING);
        assertThat(first.attempts()).isEqualTo(2);
        FailureQueueService.FailedMessage second = queue.reprocess(message.id(), fake, ctx(), T0.plusSeconds(120));
        assertThat(second.status()).isEqualTo(FailureQueueService.FailureStatus.DEAD);
        assertThat(second.attempts()).isEqualTo(3);
        assertThatThrownBy(() -> queue.reprocess(message.id(), fake, ctx(), T0.plusSeconds(180)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("DEAD");
    }

    @Test
    @DisplayName("teto 1 já nasce DEAD; conector de outra integração é recusado no reprocesso")
    void capOneDiesAtEnqueueAndWrongConnectorRefused() {
        FailureQueueService queue = service();
        FailureQueueService.FailedMessage dead = queue.enqueue(TENANT, "psp-pix", "send",
                "corr-1", "idem-1", "{}", 1, "timeout", T0);
        assertThat(dead.status()).isEqualTo(FailureQueueService.FailureStatus.DEAD);
        FailureQueueService.FailedMessage pending = queue.enqueue(TENANT, "psp-pix", "send",
                "corr-2", "idem-2", "{}", 3, "timeout", T0);
        ExternalConnector other = new FakeConnector(ExternalConnector.Outcome.SUCCESS) {
            @Override public String name() { return "sefaz-nfe"; }
        };
        assertThatThrownBy(() -> queue.reprocess(pending.id(), other, ctx(), T0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("não é o da mensagem");
        var otherTenantCtx = new ExternalConnector.ConnectorContext(
                UUID.fromString("00000000-0000-0000-0000-0000000000bb"),
                IntegrationMode.SANDBOX, "vault:ref", "corr-1");
        assertThatThrownBy(() -> queue.reprocess(pending.id(),
                new FakeConnector(ExternalConnector.Outcome.SUCCESS), otherTenantCtx, T0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("não cruza tenant");
    }
}
