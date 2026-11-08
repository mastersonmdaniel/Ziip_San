package br.com.ziipsan.platform.tenantalert;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Aceite da ED-07 + P3.39: alertas do tenant acrescentados à página inicial por perfil (porta real HomePorts.TenantAlerts). */
class TenantAlertsHomeAdapterTest {

    @Test
    void homePortReturnsTenantAlertsAsAvailable() {
        var h = new TenantAlertFixtures.Harness();
        var service = h.service();
        service.configureRule(TenantAlertFixtures.TENANT, TenantAlertFixtures.KNOWN_EVENT,
                TenantAlertFixtures.KNOWN_VERSION, TenantAlertFixtures.RESPONSIBLE, false, TenantAlertFixtures.ADMIN);
        service.onBusinessEvent(TenantAlertFixtures.TENANT, UUID.randomUUID(),
                TenantAlertFixtures.KNOWN_EVENT, TenantAlertFixtures.KNOWN_VERSION, TenantAlertFixtures.NOW);
        service.onBatchFailure(new TenantAlertService.BatchFailure(TenantAlertFixtures.TENANT, UUID.randomUUID(),
                "2026-11", List.of("rota-01"), "route-error"));

        var adapter = new TenantAlertsHomeAdapter(service);
        var result = adapter.alertsOf(TenantAlertFixtures.TENANT);
        assertThat(result.available()).isTrue();
        assertThat(result.alerts()).hasSize(2);
        assertThat(result.alerts()).anySatisfy(a -> {
            assertThat(a.code()).isEqualTo(TenantAlertFixtures.KNOWN_EVENT);
            assertThat(a.recordAddress()).startsWith("/inicio/alertas/");
        });
        assertThat(result.alerts()).anySatisfy(a ->
                assertThat(a.code()).isEqualTo(TenantAlertsHomeAdapter.BATCH_FAILURE_CODE));
    }

    @Test
    void homePortWithNoAlertsIsAvailableAndEmpty() {
        var h = new TenantAlertFixtures.Harness();
        var result = new TenantAlertsHomeAdapter(h.service()).alertsOf(TenantAlertFixtures.TENANT);
        assertThat(result.available()).isTrue();
        assertThat(result.alerts()).isEmpty();
    }
}
