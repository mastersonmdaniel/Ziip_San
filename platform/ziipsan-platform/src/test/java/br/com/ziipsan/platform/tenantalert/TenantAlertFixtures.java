package br.com.ziipsan.platform.tenantalert;

import br.com.ziipsan.platform.notification.BusinessEventCatalog;
import br.com.ziipsan.platform.notification.CatalogEvent;
import br.com.ziipsan.platform.notification.NotificationCategory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Dublês das portas donas para os testes da ED-07 — nenhum comportamento de dona é reimplementado aqui além do contrato da porta. */
final class TenantAlertFixtures {

    static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID RESPONSIBLE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final UUID ADMIN = UUID.fromString("33333333-3333-3333-3333-333333333333");
    static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    /** Evento de catálogo USADO NOS TESTES como membro declarado do catálogo dublê: nome ilustrativo do contrato da porta, não é afirmação sobre o conteúdo real do catálogo da P1.38 (ver RELATORIO-ED-07). */
    static final String KNOWN_EVENT = "billing.cycle.closed";
    static final int KNOWN_VERSION = 1;

    static BusinessEventCatalog catalog() {
        return (name, version) -> KNOWN_EVENT.equals(name) && version == KNOWN_VERSION
                ? Optional.of(new CatalogEvent(name, version, NotificationCategory.BILLING))
                : Optional.empty();
    }

    static final class RecordingNotificationCenter implements TenantAlertPorts.NotificationCenterPort {
        final List<TenantAlertPorts.NotificationRequest> delivered = new ArrayList<>();
        /** Falhas injetadas antes do sucesso (teste da correção D4): cada chamada consome uma e lança. */
        int failuresBeforeSuccess;
        int attempts;
        @Override public void deliver(TenantAlertPorts.NotificationRequest request) {
            attempts++;
            if (failuresBeforeSuccess > 0) {
                failuresBeforeSuccess--;
                throw new IllegalStateException("central-de-ensaio-indisponivel");
            }
            delivered.add(request);
        }
    }

    static final class RecordingBatchFailureNotifier implements TenantAlertPorts.BatchFailureNotificationPort {
        final List<TenantAlertPorts.BatchFailureNotice> delivered = new ArrayList<>();
        /** Falhas injetadas antes do sucesso (teste da correção D4): cada chamada consome uma e lança. */
        int failuresBeforeSuccess;
        int attempts;
        @Override public void notify(TenantAlertPorts.BatchFailureNotice notice) {
            attempts++;
            if (failuresBeforeSuccess > 0) {
                failuresBeforeSuccess--;
                throw new IllegalStateException("canal-de-lote-de-ensaio-indisponivel");
            }
            delivered.add(notice);
        }
    }

    static final class RecordingTransport implements TenantAlertPorts.WebhookTransport {
        final List<TenantAlertPorts.SignedWebhookRequest> sent = new ArrayList<>();
        boolean fail;
        int failFirst;
        @Override public TenantAlertPorts.TransportResult send(TenantAlertPorts.SignedWebhookRequest request) {
            sent.add(request);
            if (fail || sent.size() <= failFirst) {
                return TenantAlertPorts.TransportResult.failed("endpoint-de-ensaio-indisponivel");
            }
            return TenantAlertPorts.TransportResult.ok(200);
        }
    }

    static final class RecordingSleeper implements TenantAlertPorts.Sleeper {
        final List<Duration> sleeps = new ArrayList<>();
        @Override public void sleep(Duration duration) { sleeps.add(duration); }
    }

    static final class Harness {
        final TenantAlertPorts.InMemoryRuleStore rules = new TenantAlertPorts.InMemoryRuleStore();
        final TenantAlertPorts.InMemoryAlertStore alerts = new TenantAlertPorts.InMemoryAlertStore();
        final RecordingNotificationCenter center = new RecordingNotificationCenter();
        final RecordingBatchFailureNotifier batchNotifier = new RecordingBatchFailureNotifier();
        final RecordingTransport transport = new RecordingTransport();
        final RecordingSleeper sleeper = new RecordingSleeper();
        final TenantAlertPorts.InMemoryWebhookStateStore states = new TenantAlertPorts.InMemoryWebhookStateStore();
        UUID batchResponsible = RESPONSIBLE;
        String endpointUrl = "https://cliente.exemplo.invalid/webhooks/ziipsan";
        String endpointSecret = "segredo-de-ensaio";
        boolean endpointConfigured = true;
        final WebhookDeliveryPolicy policy = new WebhookDeliveryPolicy(
                3, Duration.ofMillis(100), Duration.ofSeconds(1), 2, Duration.ofMinutes(5));

        TenantAlertPorts.WebhookEndpoints endpoints() {
            return (tenantId, eventName) -> endpointConfigured
                    ? Optional.of(new TenantAlertPorts.WebhookEndpoint(tenantId, endpointUrl, endpointSecret, true))
                    : Optional.empty();
        }

        ReliableWebhookDelivery webhookDelivery() {
            return new ReliableWebhookDelivery(endpoints(), transport, states, sleeper, () -> 0L, CLOCK);
        }

        TenantAlertService service() {
            return new TenantAlertService(catalog(), rules, alerts, center, batchNotifier, webhookDelivery(), policy,
                    tenantId -> Optional.ofNullable(batchResponsible), CLOCK);
        }
    }

    private TenantAlertFixtures() {
    }
}
