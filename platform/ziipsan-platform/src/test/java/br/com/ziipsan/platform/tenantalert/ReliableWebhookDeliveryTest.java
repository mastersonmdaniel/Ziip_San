package br.com.ziipsan.platform.tenantalert;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Aceite da ED-07/P2.26: webhook com entrega confiável e assinatura. */
class ReliableWebhookDeliveryTest {

    private static final String PAYLOAD = "{\"event_id\":\"x\"}";

    @Test
    void configuredWebhookReceivesSignedDelivery() {
        var h = new TenantAlertFixtures.Harness();
        var service = h.service();
        service.configureRule(TenantAlertFixtures.TENANT, TenantAlertFixtures.KNOWN_EVENT,
                TenantAlertFixtures.KNOWN_VERSION, TenantAlertFixtures.RESPONSIBLE, true, TenantAlertFixtures.ADMIN);

        var result = service.onBusinessEvent(TenantAlertFixtures.TENANT, UUID.randomUUID(),
                TenantAlertFixtures.KNOWN_EVENT, TenantAlertFixtures.KNOWN_VERSION, TenantAlertFixtures.NOW);

        var delivery = result.deliveries().get(0);
        assertThat(delivery.webhook()).isNotNull();
        assertThat(delivery.webhook().outcome()).isEqualTo(ReliableWebhookDelivery.Outcome.DELIVERED);
        assertThat(h.transport.sent).hasSize(1);
        var sent = h.transport.sent.get(0);
        assertThat(sent.url()).isEqualTo(h.endpointUrl);
        // Assinatura confere com o segredo do endpoint e o carimbo enviado.
        assertThat(WebhookSignature.verify(h.endpointSecret, sent.timestampEpochSeconds(),
                sent.payloadJson(), sent.signature())).isTrue();
        // Carga sem dado pessoal: só identificadores e códigos.
        assertThat(sent.payloadJson()).contains("event_name", "tenant_id", "responsible_user_id");
        assertThat(sent.payloadJson()).doesNotContain("@", "cpf", "nome");
    }

    @Test
    void webhookRuleWithoutConfiguredEndpointDeliversOnlyToCenter() {
        var h = new TenantAlertFixtures.Harness();
        h.endpointConfigured = false;
        var service = h.service();
        service.configureRule(TenantAlertFixtures.TENANT, TenantAlertFixtures.KNOWN_EVENT,
                TenantAlertFixtures.KNOWN_VERSION, TenantAlertFixtures.RESPONSIBLE, true, TenantAlertFixtures.ADMIN);
        var result = service.onBusinessEvent(TenantAlertFixtures.TENANT, UUID.randomUUID(),
                TenantAlertFixtures.KNOWN_EVENT, TenantAlertFixtures.KNOWN_VERSION, TenantAlertFixtures.NOW);
        assertThat(result.deliveries().get(0).webhook().outcome())
                .isEqualTo(ReliableWebhookDelivery.Outcome.NOT_CONFIGURED);
        assertThat(h.center.delivered).hasSize(1);
        assertThat(h.transport.sent).isEmpty();
    }

    @Test
    void failingEndpointIsRetriedWithBackoffAndEventuallyDelivered() {
        var h = new TenantAlertFixtures.Harness();
        h.transport.failFirst = 2;
        var delivery = h.webhookDelivery();
        var result = delivery.deliver(TenantAlertFixtures.TENANT, UUID.randomUUID(),
                TenantAlertFixtures.KNOWN_EVENT, PAYLOAD, h.policy);
        assertThat(result.outcome()).isEqualTo(ReliableWebhookDelivery.Outcome.DELIVERED);
        assertThat(result.attempts()).hasSize(3);
        assertThat(h.sleeper.sleeps).hasSize(2);
        // Recuo exponencial (jitter zero no dublê): 100ms, depois 200ms.
        assertThat(h.sleeper.sleeps.get(0).toMillis()).isEqualTo(100);
        assertThat(h.sleeper.sleeps.get(1).toMillis()).isEqualTo(200);
    }

    @Test
    void exhaustedRetriesCountConsecutiveFailureAndDisableAtPolicyThreshold() {
        var h = new TenantAlertFixtures.Harness();
        h.transport.fail = true;
        var delivery = h.webhookDelivery();
        var first = delivery.deliver(TenantAlertFixtures.TENANT, UUID.randomUUID(),
                TenantAlertFixtures.KNOWN_EVENT, PAYLOAD, h.policy);
        assertThat(first.outcome()).isEqualTo(ReliableWebhookDelivery.Outcome.FAILED);
        assertThat(first.attempts()).hasSize(3);
        assertThat(first.endpointDisabledNow()).isFalse();
        var second = delivery.deliver(TenantAlertFixtures.TENANT, UUID.randomUUID(),
                TenantAlertFixtures.KNOWN_EVENT, PAYLOAD, h.policy);
        assertThat(second.endpointDisabledNow()).isTrue();
        // Desativado: a terceira entrega nem tenta.
        var third = delivery.deliver(TenantAlertFixtures.TENANT, UUID.randomUUID(),
                TenantAlertFixtures.KNOWN_EVENT, PAYLOAD, h.policy);
        assertThat(third.outcome()).isEqualTo(ReliableWebhookDelivery.Outcome.ENDPOINT_DISABLED);
        assertThat(third.attempts()).isEmpty();
    }

    @Test
    void successfulDeliveryResetsConsecutiveFailures() {
        var h = new TenantAlertFixtures.Harness();
        h.transport.fail = true;
        var delivery = h.webhookDelivery();
        delivery.deliver(TenantAlertFixtures.TENANT, UUID.randomUUID(),
                TenantAlertFixtures.KNOWN_EVENT, PAYLOAD, h.policy);
        h.transport.fail = false;
        delivery.deliver(TenantAlertFixtures.TENANT, UUID.randomUUID(),
                TenantAlertFixtures.KNOWN_EVENT, PAYLOAD, h.policy);
        var state = h.states.stateOf(TenantAlertFixtures.TENANT, TenantAlertFixtures.KNOWN_EVENT);
        assertThat(state.consecutiveFailures()).isZero();
        assertThat(state.disabled()).isFalse();
    }
}
