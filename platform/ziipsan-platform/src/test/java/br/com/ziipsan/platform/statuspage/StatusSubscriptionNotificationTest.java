package br.com.ziipsan.platform.statuspage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P4.8: assinatura por e-mail das atualizações da página de status da
 * P3.18 — só confirmado recebe, só no componente assinado, cancelado
 * nunca.
 */
class StatusSubscriptionNotificationTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    private static StatusSubscription active(String email, List<String> components) {
        return StatusSubscription.subscribe(email, components, "tok-" + email, NOW).confirm("tok-" + email, NOW);
    }

    @Test
    @DisplayName("P4.8: assinatura nasce PENDING (não recebe), confirma com o token certo e cancela para sempre")
    void subscriptionLifecycle() {
        StatusSubscription pending = StatusSubscription.subscribe("ops@tenant.example", List.of(), "tok-1", NOW);
        assertThat(pending.receivesNotifications()).isFalse();
        assertThatThrownBy(() -> pending.confirm("token-errado", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        StatusSubscription activeSub = pending.confirm("tok-1", NOW);
        assertThat(activeSub.receivesNotifications()).isTrue();
        StatusSubscription cancelled = activeSub.unsubscribe();
        assertThat(cancelled.receivesNotifications()).isFalse();
        assertThatThrownBy(() -> cancelled.confirm("tok-1", NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> StatusSubscription.subscribe("sem-arroba", List.of(), "t", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("P4.8: notificação vai só a ACTIVE interessado no componente afetado; corpo é o fato público em JSON")
    void notifiesOnlyInterestedActiveSubscribers() {
        List<String[]> sent = new ArrayList<>();
        StatusNotificationService service = new StatusNotificationService(
                (to, subject, body) -> sent.add(new String[]{to, subject, body}));
        List<StatusSubscription> subs = List.of(
                active("todos@tenant.example", List.of()),
                active("so-api@tenant.example", List.of("api")),
                active("so-portal@tenant.example", List.of("portal")),
                StatusSubscription.subscribe("pendente@tenant.example", List.of(), "tok-p", NOW),
                active("cancelado@tenant.example", List.of()).unsubscribe());
        StatusNotificationService.StatusUpdateNotice notice =
                new StatusNotificationService.StatusUpdateNotice(
                        "API indisponível", "INVESTIGATING", List.of("api"),
                        "Identificamos a falha e estamos investigando.");

        List<StatusNotificationService.Notification> notifications =
                service.notifySubscribers(subs, notice);
        assertThat(notifications).extracting(StatusNotificationService.Notification::toEmail)
                .containsExactlyInAnyOrder("todos@tenant.example", "so-api@tenant.example");
        assertThat(sent).hasSize(2);
        assertThat(notifications.get(0).bodyJson())
                .contains("API indisponível").contains("\"api\"").contains("investigando");
        assertThat(notifications.get(0).subject()).contains("API indisponível");
    }
}
