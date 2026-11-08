package br.com.ziipsan.platform.home;

import br.com.ziipsan.platform.notification.NotificationCategory;
import br.com.ziipsan.platform.notification.NotificationCenterService;
import br.com.ziipsan.platform.work.WorkItem;
import br.com.ziipsan.platform.work.WorkItemStatus;
import br.com.ziipsan.platform.work.WorkItemType;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Massa sintetica declarada dos testes da P3.39 (nenhum dado real). */
final class HomeFixtures {

    static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID USER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final Instant NOW = Instant.parse("2026-03-10T13:00:00Z");

    private HomeFixtures() {
    }

    static WorkItem item(Instant dueAt, WorkItemStatus status) {
        return new WorkItem(UUID.randomUUID(), TENANT, WorkItemType.HELD_BILL,
                "BILL", UUID.randomUUID(), "billing", UUID.randomUUID(), USER, Set.of(), null,
                dueAt, status, "/billing/analysis-queue", "billing.held-bill.registered", 1, null, null,
                Instant.parse("2026-03-09T13:00:00Z"), status.isTerminal() ? NOW : null,
                status.isTerminal() ? "DONE" : null, null, 0, null);
    }

    static NotificationCenterService.NotificationView notice(boolean read) {
        return new NotificationCenterService.NotificationView(UUID.randomUUID(),
                "billing.bill.issued", NotificationCategory.BILLING, "bill.issued.title",
                "bill.issued.body", NOW, read ? NOW : null);
    }

    static HomeIndicator indicator(String code) {
        return new HomeIndicator(code, "Indicador " + code, "%",
                new java.math.BigDecimal("12.5"), "GREEN");
    }

    /** Portas falsas com contadores: provam o que a composicao consultou (e o que nao consultou). */
    static final class FakePorts {
        List<WorkItem> inbox = List.of();
        List<NotificationCenterService.NotificationView> notices = List.of();
        long unread = 0;
        List<HomeIndicator> indicators = List.of();
        HomePorts.AlertsResult alerts = HomePorts.AlertsResult.unavailable();
        int inboxCalls = 0;
        int indicatorCalls = 0;
        String lastProfileAsked = null;

        HomePageService service() {
            HomePorts.WorkItems w = (userId, limit) -> {
                inboxCalls++;
                return inbox;
            };
            HomePorts.Notifications n = new HomePorts.Notifications() {
                @Override
                public List<NotificationCenterService.NotificationView> recentOf(UUID userId, int limit) {
                    return notices;
                }

                @Override
                public long unreadCountOf(UUID userId) {
                    return unread;
                }
            };
            HomePorts.Indicators i = (tenantId, profileCode) -> {
                indicatorCalls++;
                lastProfileAsked = profileCode;
                return indicators;
            };
            HomePorts.TenantAlerts a = tenantId -> alerts;
            return new HomePageService(w, n, i, a, java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));
        }
    }
}
