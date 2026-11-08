package br.com.ziipsan.platform.tenantalert;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Aceite da ED-07, itens com "(teste)": evento escolhido gera alerta; fora do catálogo é recusado; falha de lote notifica. */
class TenantAlertServiceTest {

    @Test
    void chosenCatalogEventGeneratesAlertToResponsibleInNotificationCenter() {
        var h = new TenantAlertFixtures.Harness();
        var service = h.service();
        service.configureRule(TenantAlertFixtures.TENANT, TenantAlertFixtures.KNOWN_EVENT,
                TenantAlertFixtures.KNOWN_VERSION, TenantAlertFixtures.RESPONSIBLE, false, TenantAlertFixtures.ADMIN);

        var result = service.onBusinessEvent(TenantAlertFixtures.TENANT, UUID.randomUUID(),
                TenantAlertFixtures.KNOWN_EVENT, TenantAlertFixtures.KNOWN_VERSION, TenantAlertFixtures.NOW);

        assertThat(result.outcome()).isEqualTo(TenantAlertService.EventOutcome.ALERTED);
        assertThat(result.deliveries()).hasSize(1);
        assertThat(h.center.delivered).hasSize(1);
        var request = h.center.delivered.get(0);
        assertThat(request.recipientUserId()).isEqualTo(TenantAlertFixtures.RESPONSIBLE);
        assertThat(request.eventName()).isEqualTo(TenantAlertFixtures.KNOWN_EVENT);
        assertThat(request.kind()).isEqualTo(TenantAlert.Kind.CATALOG_EVENT);
        // Sem webhook na regra: nenhuma chamada de transporte.
        assertThat(h.transport.sent).isEmpty();
    }

    @Test
    void catalogEventWithoutTenantChoiceGeneratesNoAlert() {
        var h = new TenantAlertFixtures.Harness();
        var result = h.service().onBusinessEvent(TenantAlertFixtures.TENANT, UUID.randomUUID(),
                TenantAlertFixtures.KNOWN_EVENT, TenantAlertFixtures.KNOWN_VERSION, TenantAlertFixtures.NOW);
        assertThat(result.outcome()).isEqualTo(TenantAlertService.EventOutcome.IGNORED_NO_RULE);
        assertThat(h.center.delivered).isEmpty();
        assertThat(h.alerts.listByTenant(TenantAlertFixtures.TENANT, 10)).isEmpty();
    }

    @Test
    void configuringRuleForEventOutsideCatalogIsRefusedAndNothingIsSaved() {
        var h = new TenantAlertFixtures.Harness();
        var service = h.service();
        assertThatThrownBy(() -> service.configureRule(TenantAlertFixtures.TENANT, "evento.inexistente", 1,
                TenantAlertFixtures.RESPONSIBLE, false, TenantAlertFixtures.ADMIN))
                .isInstanceOf(AlertRejectedException.class)
                .hasMessageContaining("fora do catálogo");
        assertThat(h.rules.listByTenant(TenantAlertFixtures.TENANT)).isEmpty();
    }

    @Test
    void configuringRuleForUndeclaredVersionIsRefused() {
        var h = new TenantAlertFixtures.Harness();
        assertThatThrownBy(() -> h.service().configureRule(TenantAlertFixtures.TENANT,
                TenantAlertFixtures.KNOWN_EVENT, 99, TenantAlertFixtures.RESPONSIBLE, false, TenantAlertFixtures.ADMIN))
                .isInstanceOf(AlertRejectedException.class);
        assertThat(h.rules.listByTenant(TenantAlertFixtures.TENANT)).isEmpty();
    }

    @Test
    void eventOutsideCatalogIsRefusedOnIngestionToo() {
        var h = new TenantAlertFixtures.Harness();
        assertThatThrownBy(() -> h.service().onBusinessEvent(TenantAlertFixtures.TENANT, UUID.randomUUID(),
                "evento.inexistente", 1, TenantAlertFixtures.NOW))
                .isInstanceOf(AlertRejectedException.class);
        assertThat(h.center.delivered).isEmpty();
    }

    @Test
    void duplicateActiveRuleForSameEventAndResponsibleIsRefused() {
        var h = new TenantAlertFixtures.Harness();
        var service = h.service();
        service.configureRule(TenantAlertFixtures.TENANT, TenantAlertFixtures.KNOWN_EVENT,
                TenantAlertFixtures.KNOWN_VERSION, TenantAlertFixtures.RESPONSIBLE, false, TenantAlertFixtures.ADMIN);
        assertThatThrownBy(() -> service.configureRule(TenantAlertFixtures.TENANT, TenantAlertFixtures.KNOWN_EVENT,
                TenantAlertFixtures.KNOWN_VERSION, TenantAlertFixtures.RESPONSIBLE, false, TenantAlertFixtures.ADMIN))
                .isInstanceOf(AlertRejectedException.class)
                .hasMessageContaining("Já existe regra ativa");
    }

    @Test
    void disabledRuleGeneratesNoAlert() {
        var h = new TenantAlertFixtures.Harness();
        var service = h.service();
        var rule = service.configureRule(TenantAlertFixtures.TENANT, TenantAlertFixtures.KNOWN_EVENT,
                TenantAlertFixtures.KNOWN_VERSION, TenantAlertFixtures.RESPONSIBLE, false, TenantAlertFixtures.ADMIN);
        service.setRuleEnabled(TenantAlertFixtures.TENANT, rule.id(), false);
        var result = service.onBusinessEvent(TenantAlertFixtures.TENANT, UUID.randomUUID(),
                TenantAlertFixtures.KNOWN_EVENT, TenantAlertFixtures.KNOWN_VERSION, TenantAlertFixtures.NOW);
        assertThat(result.outcome()).isEqualTo(TenantAlertService.EventOutcome.IGNORED_NO_RULE);
        assertThat(h.center.delivered).isEmpty();
    }

    @Test
    void redeliveryOfSameOutboxMessageDoesNotDuplicateAlertOrDeliveries() {
        var h = new TenantAlertFixtures.Harness();
        var service = h.service();
        service.configureRule(TenantAlertFixtures.TENANT, TenantAlertFixtures.KNOWN_EVENT,
                TenantAlertFixtures.KNOWN_VERSION, TenantAlertFixtures.RESPONSIBLE, true, TenantAlertFixtures.ADMIN);
        UUID eventId = UUID.randomUUID();
        service.onBusinessEvent(TenantAlertFixtures.TENANT, eventId,
                TenantAlertFixtures.KNOWN_EVENT, TenantAlertFixtures.KNOWN_VERSION, TenantAlertFixtures.NOW);
        var again = service.onBusinessEvent(TenantAlertFixtures.TENANT, eventId,
                TenantAlertFixtures.KNOWN_EVENT, TenantAlertFixtures.KNOWN_VERSION, TenantAlertFixtures.NOW);
        assertThat(again.deliveries()).hasSize(1);
        assertThat(again.deliveries().get(0).duplicate()).isTrue();
        assertThat(h.center.delivered).hasSize(1);
        assertThat(h.transport.sent).hasSize(1);
        assertThat(h.alerts.listByTenant(TenantAlertFixtures.TENANT, 10)).hasSize(1);
    }

    @Test
    void batchFailureNotifiesTenantResponsible() {
        var h = new TenantAlertFixtures.Harness();
        var failure = new TenantAlertService.BatchFailure(TenantAlertFixtures.TENANT, UUID.randomUUID(),
                "2026-11", List.of("rota-01"), "route-error");
        var result = h.service().onBatchFailure(failure);
        assertThat(result.outcome()).isEqualTo(TenantAlertService.BatchOutcome.NOTIFIED);
        // Correção D1: a falha de lote sai pela porta TIPADA própria — e NÃO
        // como pedido sem evento à central (a central real não o ingeriria).
        assertThat(h.batchNotifier.delivered).hasSize(1);
        assertThat(h.center.delivered).isEmpty();
        var notice = h.batchNotifier.delivered.get(0);
        assertThat(notice.recipientUserId()).isEqualTo(TenantAlertFixtures.RESPONSIBLE);
        assertThat(notice.runRef()).isEqualTo(failure.runId().toString());
        assertThat(notice.titleCode()).isEqualTo(TenantAlertService.BATCH_FAILURE_TITLE_CODE);
        var stored = h.alerts.listByTenant(TenantAlertFixtures.TENANT, 10);
        assertThat(stored).hasSize(1);
        // O alerta gravado continua sem fingir nome de evento do catálogo.
        assertThat(stored.get(0).eventName()).isNull();
        assertThat(stored.get(0).kind()).isEqualTo(TenantAlert.Kind.BATCH_FAILURE);
    }

    @Test
    void sameBatchFailureTwiceNotifiesOnlyOnce() {
        var h = new TenantAlertFixtures.Harness();
        var failure = new TenantAlertService.BatchFailure(TenantAlertFixtures.TENANT, UUID.randomUUID(),
                "2026-11", List.of("rota-01"), "route-error");
        h.service().onBatchFailure(failure);
        var again = h.service().onBatchFailure(failure);
        assertThat(again.outcome()).isEqualTo(TenantAlertService.BatchOutcome.DUPLICATE);
        assertThat(h.batchNotifier.delivered).hasSize(1);
    }

    @Test
    void batchFailureWithoutConfiguredResponsibleIsDeclaredNotDeliveredToInventedUser() {
        var h = new TenantAlertFixtures.Harness();
        h.batchResponsible = null;
        var failure = new TenantAlertService.BatchFailure(TenantAlertFixtures.TENANT, UUID.randomUUID(),
                "2026-11", List.of("rota-01"), "route-error");
        var result = h.service().onBatchFailure(failure);
        assertThat(result.outcome()).isEqualTo(TenantAlertService.BatchOutcome.NO_RESPONSIBLE);
        assertThat(result.alertId()).isNull();
        assertThat(h.batchNotifier.delivered).isEmpty();
        assertThat(h.center.delivered).isEmpty();
    }

    @Test
    void centerDeliveryFailureDoesNotBurnTheAlertRedeliveryDelivers() {
        // Correção D4: entrega ANTES de gravar — a falha da central não deixa
        // alerta gravado, e a reentrega da outbox entrega de verdade.
        var h = new TenantAlertFixtures.Harness();
        h.center.failuresBeforeSuccess = 1;
        var service = h.service();
        service.configureRule(TenantAlertFixtures.TENANT, TenantAlertFixtures.KNOWN_EVENT,
                TenantAlertFixtures.KNOWN_VERSION, TenantAlertFixtures.RESPONSIBLE, false, TenantAlertFixtures.ADMIN);
        UUID eventId = UUID.randomUUID();
        assertThatThrownBy(() -> service.onBusinessEvent(TenantAlertFixtures.TENANT, eventId,
                TenantAlertFixtures.KNOWN_EVENT, TenantAlertFixtures.KNOWN_VERSION, TenantAlertFixtures.NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("central-de-ensaio-indisponivel");
        // Nada gravado: a reentrega NÃO pode virar "duplicada".
        assertThat(h.alerts.listByTenant(TenantAlertFixtures.TENANT, 10)).isEmpty();
        assertThat(h.center.delivered).isEmpty();

        var retried = service.onBusinessEvent(TenantAlertFixtures.TENANT, eventId,
                TenantAlertFixtures.KNOWN_EVENT, TenantAlertFixtures.KNOWN_VERSION, TenantAlertFixtures.NOW);
        assertThat(retried.outcome()).isEqualTo(TenantAlertService.EventOutcome.ALERTED);
        assertThat(retried.deliveries()).hasSize(1);
        assertThat(retried.deliveries().get(0).duplicate()).isFalse();
        assertThat(h.center.attempts).isEqualTo(2);
        assertThat(h.center.delivered).hasSize(1);
        assertThat(h.alerts.listByTenant(TenantAlertFixtures.TENANT, 10)).hasSize(1);
    }

    @Test
    void batchNotificationFailureAllowsSuccessfulRetry() {
        // Correção D4 no caminho de lote: falha da notificação, depois
        // reentrega bem-sucedida — sem alerta queimado como "duplicado".
        var h = new TenantAlertFixtures.Harness();
        h.batchNotifier.failuresBeforeSuccess = 1;
        var failure = new TenantAlertService.BatchFailure(TenantAlertFixtures.TENANT, UUID.randomUUID(),
                "2026-11", List.of("rota-01"), "route-error");
        assertThatThrownBy(() -> h.service().onBatchFailure(failure))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("canal-de-lote-de-ensaio-indisponivel");
        assertThat(h.alerts.listByTenant(TenantAlertFixtures.TENANT, 10)).isEmpty();

        var retried = h.service().onBatchFailure(failure);
        assertThat(retried.outcome()).isEqualTo(TenantAlertService.BatchOutcome.NOTIFIED);
        assertThat(h.batchNotifier.attempts).isEqualTo(2);
        assertThat(h.batchNotifier.delivered).hasSize(1);
        assertThat(h.alerts.listByTenant(TenantAlertFixtures.TENANT, 10)).hasSize(1);
    }
}
