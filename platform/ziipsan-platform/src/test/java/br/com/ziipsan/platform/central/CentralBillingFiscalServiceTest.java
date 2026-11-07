package br.com.ziipsan.platform.central;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CentralBillingFiscalServiceTest {

    private static final UUID TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID GROUP = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private final CentralBillingFiscalService service = new CentralBillingFiscalService();

    private static FiscalQueueSnapshot fiscalQueue(long reserved, long signed, long transmitted, long pendingRec) {
        return new FiscalQueueSnapshot(TENANT, Map.of(
                "RESERVED", reserved, "SIGNED", signed, "TRANSMITTED", transmitted,
                "AUTHORIZED", 0L, "REJECTED", 0L, "PENDING_RECONCILIATION", pendingRec,
                "CANCELLED", 0L, "REPLACED", 0L, "VOIDED", 0L), 7200);
    }

    @Test
    @DisplayName("P2.11: abas Faturamento e Fiscal existem na Central, ao lado das da P1.17")
    void tabsDeliveredByP211() {
        assertThat(CentralTab.deliveredByP211()).containsExactly(CentralTab.FATURAMENTO, CentralTab.FISCAL);
        assertThat(CentralTab.FATURAMENTO.route()).isEqualTo("/central/faturamento");
        assertThat(CentralTab.FISCAL.route()).isEqualTo("/central/fiscal");
        assertThat(CentralTab.values()).extracting(CentralTab::key)
                .contains("visao-geral", "plataforma", "seguranca", "alertas", "faturamento", "fiscal");
    }

    @Test
    @DisplayName("P2.11: visao de faturamento conta por situacao e expoe o atraso")
    void billingTabViewCounts() {
        var snapshot = new BillingCycleSnapshot(TENANT, YearMonth.of(2026, 11), List.of(
                new BillingCycleSnapshot.ScheduleItemView(GROUP, "READING", true, "DONE", LocalDate.of(2026, 11, 3)),
                new BillingCycleSnapshot.ScheduleItemView(GROUP, "BILLING", true, "PENDING", LocalDate.of(2026, 11, 9)),
                new BillingCycleSnapshot.ScheduleItemView(GROUP, "CLOSING", true, "PENDING", LocalDate.of(2026, 11, 20))));
        var view = service.billingTab(snapshot, LocalDate.of(2026, 11, 10));
        assertThat(view.tab()).isEqualTo(CentralTab.FATURAMENTO);
        assertThat(view.countByStatus()).containsEntry("DONE", 1L).containsEntry("PENDING", 2L)
                .containsEntry("COMMANDED", 0L).containsEntry("CANCELLED", 0L);
        assertThat(view.overdueMandatoryCount()).isEqualTo(1);
        assertThat(view.alert()).isPresent();
        assertThat(view.alert().get().groupId()).isEqualTo(GROUP);
    }

    @Test
    @DisplayName("P2.11 (m-3): visao de faturamento de tenant sem cronograma sai zerada e sem alerta")
    void billingTabViewForTenantWithoutSchedule() {
        var snapshot = new BillingCycleSnapshot(TENANT, YearMonth.of(2026, 11), List.of());
        var view = service.billingTab(snapshot, LocalDate.of(2026, 11, 10));
        assertThat(view.tab()).isEqualTo(CentralTab.FATURAMENTO);
        assertThat(view.countByStatus()).containsEntry("DONE", 0L).containsEntry("PENDING", 0L)
                .containsEntry("COMMANDED", 0L).containsEntry("CANCELLED", 0L);
        assertThat(view.overdueMandatoryCount()).isZero();
        assertThat(view.alert()).isEmpty();
    }

    @Test
    @DisplayName("P2.11: visao fiscal expoe fila, limiar e estouro sem operar documento (fronteira P2.33)")
    void fiscalTabViewExposesQueueAgainstLimit() {
        var snapshot = new FiscalQueueSnapshot(TENANT, Map.of(
                "RESERVED", 6L, "SIGNED", 6L, "TRANSMITTED", 0L, "AUTHORIZED", 3L,
                "REJECTED", 2L, "PENDING_RECONCILIATION", 0L, "CANCELLED", 0L,
                "REPLACED", 0L, "VOIDED", 0L), 7200);
        var view = service.fiscalTab(snapshot, new CentralAlertThresholds(10));
        assertThat(view.tab()).isEqualTo(CentralTab.FISCAL);
        assertThat(view.queueSize()).isEqualTo(12);
        assertThat(view.queueLimitRnf08()).isEqualTo(10);
        assertThat(view.overLimit()).isTrue();
        assertThat(view.alert()).isPresent();
    }

    @Test
    @DisplayName("P2.11 (m-4): visao fiscal exatamente no limiar nao esta acima dele e nao dispara")
    void fiscalTabAtLimitDoesNotFire() {
        var view = service.fiscalTab(fiscalQueue(4, 3, 2, 1), new CentralAlertThresholds(10));
        assertThat(view.tab()).isEqualTo(CentralTab.FISCAL);
        assertThat(view.queueSize()).isEqualTo(10);
        assertThat(view.queueLimitRnf08()).isEqualTo(10);
        assertThat(view.overLimit()).isFalse();
        assertThat(view.alert()).isEmpty();
    }

    @Test
    @DisplayName("P2.11 (m-4): visao fiscal um acima do limiar dispara pela aba")
    void fiscalTabAboveLimitFires() {
        var view = service.fiscalTab(fiscalQueue(4, 3, 2, 2), new CentralAlertThresholds(10));
        assertThat(view.queueSize()).isEqualTo(11);
        assertThat(view.queueLimitRnf08()).isEqualTo(10);
        assertThat(view.overLimit()).isTrue();
        assertThat(view.alert()).isPresent();
        assertThat(view.alert().get().observedValue()).isEqualTo(11);
    }
}
