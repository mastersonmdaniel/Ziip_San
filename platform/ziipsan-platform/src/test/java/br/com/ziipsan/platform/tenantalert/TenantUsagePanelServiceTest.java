package br.com.ziipsan.platform.tenantalert;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Aceite da ED-07: painel de uso e desempenho do tenant — com honestidade de fonte. */
class TenantUsagePanelServiceTest {

    private static final Instant FROM = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-12-01T00:00:00Z");

    @Test
    void panelAggregatesBatchAlertsAndWebhooksWithRates() {
        var alerts = new TenantAlertPorts.InMemoryAlertStore();
        alerts.save(new TenantAlert(java.util.UUID.randomUUID(), TenantAlertFixtures.TENANT,
                java.util.UUID.randomUUID(), TenantAlert.Kind.CATALOG_EVENT,
                TenantAlertFixtures.KNOWN_EVENT, 1, "evt-1", TenantAlertFixtures.RESPONSIBLE,
                "t", "b", Instant.parse("2026-11-05T00:00:00Z")));
        var panel = new TenantUsagePanelService(
                (t, f, to) -> Optional.of(new TenantAlertPorts.BatchStats(10, 8, 1, 1, Duration.ofMinutes(30))),
                (t, f, to) -> Optional.of(new TenantAlertPorts.WebhookStats(20, 15, 5, 1)),
                alerts);
        var result = panel.panelOf(TenantAlertFixtures.TENANT, FROM, TO);
        assertThat(result.batch().available()).isTrue();
        assertThat(result.batch().runsTotal()).isEqualTo(10);
        assertThat(result.batch().successRate().value()).isEqualByComparingTo(new BigDecimal("0.8000"));
        assertThat(result.alerts().alertsGenerated()).isEqualTo(1);
        assertThat(result.webhooks().successRate().value()).isEqualByComparingTo(new BigDecimal("0.7500"));
        assertThat(result.webhooks().endpointsDisabled()).isEqualTo(1);
    }

    @Test
    void missingSourcesAreDeclaredUnavailableNeverZero() {
        var panel = new TenantUsagePanelService(TenantUsagePanelService.noBatchSource(),
                TenantUsagePanelService.noWebhookSource(), new TenantAlertPorts.InMemoryAlertStore());
        var result = panel.panelOf(TenantAlertFixtures.TENANT, FROM, TO);
        assertThat(result.batch().available()).isFalse();
        assertThat(result.batch().runsTotal()).isNull();
        assertThat(result.webhooks().available()).isFalse();
        assertThat(result.webhooks().deliveriesTotal()).isNull();
        // Alertas são da própria ED-07: fonte presente, contagem real (zero de verdade).
        assertThat(result.alerts().available()).isTrue();
        assertThat(result.alerts().alertsGenerated()).isZero();
    }

    @Test
    void rateWithoutDenominatorIsNullNotInvented() {
        var panel = new TenantUsagePanelService(
                (t, f, to) -> Optional.of(new TenantAlertPorts.BatchStats(0, 0, 0, 0, null)),
                (t, f, to) -> Optional.of(new TenantAlertPorts.WebhookStats(0, 0, 0, 0)),
                new TenantAlertPorts.InMemoryAlertStore());
        var result = panel.panelOf(TenantAlertFixtures.TENANT, FROM, TO);
        assertThat(result.batch().successRate()).isNull();
        assertThat(result.webhooks().successRate()).isNull();
        assertThat(result.batch().averageDurationMillis()).isNull();
    }

    @Test
    void panelDoesNotCountAlertsAfterPeriodEnd() {
        // Sonda do revisor (correção D2): alertas em 2026-11-05 e 2027-06-01;
        // o painel de novembro/2026 tem de dar 1 — o de 2027 é FORA do período.
        var alerts = new TenantAlertPorts.InMemoryAlertStore();
        alerts.save(new TenantAlert(java.util.UUID.randomUUID(), TenantAlertFixtures.TENANT,
                java.util.UUID.randomUUID(), TenantAlert.Kind.CATALOG_EVENT,
                TenantAlertFixtures.KNOWN_EVENT, 1, "evt-dentro", TenantAlertFixtures.RESPONSIBLE,
                "t", "b", Instant.parse("2026-11-05T00:00:00Z")));
        alerts.save(new TenantAlert(java.util.UUID.randomUUID(), TenantAlertFixtures.TENANT,
                java.util.UUID.randomUUID(), TenantAlert.Kind.CATALOG_EVENT,
                TenantAlertFixtures.KNOWN_EVENT, 1, "evt-fora", TenantAlertFixtures.RESPONSIBLE,
                "t", "b", Instant.parse("2027-06-01T00:00:00Z")));
        var panel = new TenantUsagePanelService(TenantUsagePanelService.noBatchSource(),
                TenantUsagePanelService.noWebhookSource(), alerts);
        var result = panel.panelOf(TenantAlertFixtures.TENANT, FROM, TO);
        assertThat(result.alerts().alertsGenerated()).isEqualTo(1);
    }

    @Test
    void panelPeriodIsHalfOpenFromInclusiveToExclusive() {
        // Fronteiras do [from, to): alerta EXATAMENTE em FROM conta; em TO, não.
        var alerts = new TenantAlertPorts.InMemoryAlertStore();
        alerts.save(new TenantAlert(java.util.UUID.randomUUID(), TenantAlertFixtures.TENANT,
                java.util.UUID.randomUUID(), TenantAlert.Kind.CATALOG_EVENT,
                TenantAlertFixtures.KNOWN_EVENT, 1, "evt-from", TenantAlertFixtures.RESPONSIBLE,
                "t", "b", FROM));
        alerts.save(new TenantAlert(java.util.UUID.randomUUID(), TenantAlertFixtures.TENANT,
                java.util.UUID.randomUUID(), TenantAlert.Kind.CATALOG_EVENT,
                TenantAlertFixtures.KNOWN_EVENT, 1, "evt-to", TenantAlertFixtures.RESPONSIBLE,
                "t", "b", TO));
        var panel = new TenantUsagePanelService(TenantUsagePanelService.noBatchSource(),
                TenantUsagePanelService.noWebhookSource(), alerts);
        var result = panel.panelOf(TenantAlertFixtures.TENANT, FROM, TO);
        assertThat(result.alerts().alertsGenerated()).isEqualTo(1);
    }

    @Test
    void invertedPeriodIsRefused() {
        var panel = new TenantUsagePanelService(TenantUsagePanelService.noBatchSource(),
                TenantUsagePanelService.noWebhookSource(), new TenantAlertPorts.InMemoryAlertStore());
        assertThatThrownBy(() -> panel.panelOf(TenantAlertFixtures.TENANT, TO, FROM))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
