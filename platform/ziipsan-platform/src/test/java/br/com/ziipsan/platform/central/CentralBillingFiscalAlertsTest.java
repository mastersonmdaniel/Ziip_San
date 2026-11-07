package br.com.ziipsan.platform.central;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Aceite da P2.11, um teste para cada alerta: (1) ciclo de faturamento
 * atrasado dispara; (2) fila fiscal acima do RNF-08 dispara. Vetores
 * calculados a mao: fila = RESERVED+SIGNED+TRANSMITTED+
 * PENDING_RECONCILIATION; com o mapa abaixo, 4+3+2+2 = 11.
 */
class CentralBillingFiscalAlertsTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID GROUP_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID GROUP_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private static BillingCycleSnapshot cycle(BillingCycleSnapshot.ScheduleItemView... items) {
        return new BillingCycleSnapshot(TENANT, YearMonth.of(2026, 11), List.of(items));
    }

    private static BillingCycleSnapshot.ScheduleItemView item(
            String activity, boolean mandatory, String status, String planned) {
        return item(GROUP_A, activity, mandatory, status, planned);
    }

    private static BillingCycleSnapshot.ScheduleItemView item(
            UUID group, String activity, boolean mandatory, String status, String planned) {
        return new BillingCycleSnapshot.ScheduleItemView(
                group, activity, mandatory, status, LocalDate.parse(planned));
    }

    private static FiscalQueueSnapshot fiscal(long reserved, long signed, long transmitted, long pendingRec) {
        return new FiscalQueueSnapshot(TENANT, Map.of(
                "RESERVED", reserved, "SIGNED", signed, "TRANSMITTED", transmitted,
                "AUTHORIZED", 40L, "REJECTED", 5L, "PENDING_RECONCILIATION", pendingRec,
                "CANCELLED", 1L, "REPLACED", 1L, "VOIDED", 2L), 3600);
    }

    // ---- Alerta 1: ciclo atrasado ----

    @Test
    @DisplayName("P2.11 alerta 1: item obrigatorio vencido e nao realizado dispara o alerta do ciclo")
    void billingCycleOverdueFires() {
        var snapshot = cycle(item("BILLING", true, "PENDING", "2026-11-09"));
        var signal = CentralBillingFiscalAlerts.evaluateBillingCycle(snapshot, TODAY);
        assertThat(signal).isPresent();
        assertThat(signal.get().ruleKey()).isEqualTo("central.billing.cycle_overdue");
        assertThat(signal.get().metricKey()).isEqualTo("billing.cycle.overdue_items");
        assertThat(signal.get().observedValue()).isEqualTo(1);
        assertThat(signal.get().tenantId()).isEqualTo(TENANT);
        assertThat(signal.get().groupId()).isEqualTo(GROUP_A);
    }

    @Test
    @DisplayName("P2.11 alerta 1 (M-2): o sinal carrega o grupo do item vencido mais antigo (passo 1 do RB-07)")
    void billingCycleSignalCarriesOldestOverdueGroup() {
        var snapshot = cycle(
                item(GROUP_A, "BILLING", true, "PENDING", "2026-11-09"),
                item(GROUP_B, "READING", true, "COMMANDED", "2026-11-05"));
        var signal = CentralBillingFiscalAlerts.evaluateBillingCycle(snapshot, TODAY);
        assertThat(signal).isPresent();
        assertThat(signal.get().observedValue()).isEqualTo(2);
        assertThat(signal.get().groupId()).isEqualTo(GROUP_B);
        assertThat(signal.get().detail()).contains("READING").contains(GROUP_B.toString()).contains("2026-11-05");
    }

    @Test
    @DisplayName("P2.11 alerta 1: previsto para hoje, realizado, cancelado ou nao obrigatorio nao dispara")
    void billingCycleNotOverdueDoesNotFire() {
        assertThat(CentralBillingFiscalAlerts.evaluateBillingCycle(
                cycle(item("BILLING", true, "PENDING", "2026-11-10")), TODAY)).isEmpty();
        assertThat(CentralBillingFiscalAlerts.evaluateBillingCycle(
                cycle(item("BILLING", true, "DONE", "2026-11-01")), TODAY)).isEmpty();
        assertThat(CentralBillingFiscalAlerts.evaluateBillingCycle(
                cycle(item("BILLING", true, "CANCELLED", "2026-11-01")), TODAY)).isEmpty();
        assertThat(CentralBillingFiscalAlerts.evaluateBillingCycle(
                cycle(item("CLOSING", false, "COMMANDED", "2026-11-01")), TODAY)).isEmpty();
    }

    @Test
    @DisplayName("P2.11 alerta 1: comandado tambem atrasa; contagem so dos obrigatorios vencidos")
    void billingCycleCountsOnlyOverdueMandatory() {
        var snapshot = cycle(
                item("READING", true, "COMMANDED", "2026-11-08"),
                item("BILLING", true, "PENDING", "2026-11-09"),
                item("CLOSING", false, "PENDING", "2026-11-01"),
                item("TRANSMISSION", true, "DONE", "2026-11-05"));
        var signal = CentralBillingFiscalAlerts.evaluateBillingCycle(snapshot, TODAY);
        assertThat(signal).isPresent();
        assertThat(signal.get().observedValue()).isEqualTo(2);
    }

    @Test
    @DisplayName("P2.11 (m-3): instantaneo vazio = tenant sem cronograma; nao dispara e nao lanca excecao")
    void emptyCycleSnapshotMeansNoScheduleAndDoesNotFire() {
        var snapshot = new BillingCycleSnapshot(TENANT, YearMonth.of(2026, 11), List.of());
        assertThat(snapshot.items()).isEmpty();
        assertThat(CentralBillingFiscalAlerts.overdueMandatoryItems(snapshot, TODAY)).isEmpty();
        assertThat(CentralBillingFiscalAlerts.evaluateBillingCycle(snapshot, TODAY)).isEmpty();
    }

    // ---- Alerta 2: fila fiscal acima do RNF-08 ----

    @Test
    @DisplayName("P2.11 alerta 2: fila acima do limiar do RNF-08 dispara (fila calculada a mao = 11 > 10)")
    void fiscalQueueOverLimitFires() {
        var snapshot = fiscal(4, 3, 2, 2);
        assertThat(snapshot.queueSize()).isEqualTo(11);
        var signal = CentralBillingFiscalAlerts.evaluateFiscalQueue(snapshot, new CentralAlertThresholds(10));
        assertThat(signal).isPresent();
        assertThat(signal.get().ruleKey()).isEqualTo("central.fiscal.queue_over_limit");
        assertThat(signal.get().observedValue()).isEqualTo(11);
        assertThat(signal.get().threshold()).isEqualTo(10);
        assertThat(signal.get().groupId()).isNull();
    }

    @Test
    @DisplayName("P2.11 alerta 2: fila igual ao limiar esta no limite (nao acima) e nao dispara")
    void fiscalQueueAtLimitDoesNotFire() {
        var snapshot = fiscal(4, 3, 2, 1); // fila = 10
        assertThat(snapshot.queueSize()).isEqualTo(10);
        assertThat(CentralBillingFiscalAlerts.evaluateFiscalQueue(snapshot, new CentralAlertThresholds(10)))
                .isEmpty();
    }

    @Test
    @DisplayName("P2.11 alerta 2: AUTHORIZED e REJECTED nao sao fila, mesmo em volume")
    void fiscalQueueExcludesSettledAndRejected() {
        var snapshot = new FiscalQueueSnapshot(TENANT, Map.of(
                "RESERVED", 0L, "SIGNED", 0L, "TRANSMITTED", 0L, "AUTHORIZED", 9999L,
                "REJECTED", 9999L, "PENDING_RECONCILIATION", 0L, "CANCELLED", 9L,
                "REPLACED", 9L, "VOIDED", 9L), 0);
        assertThat(snapshot.queueSize()).isZero();
        assertThat(CentralBillingFiscalAlerts.evaluateFiscalQueue(snapshot, new CentralAlertThresholds(1)))
                .isEmpty();
    }

    @Test
    @DisplayName("P2.11 alerta 2: sem limiar configurado a avaliacao e recusada, nunca assumida")
    void fiscalQueueWithoutThresholdsIsRefused() {
        assertThatThrownBy(() -> CentralBillingFiscalAlerts.evaluateFiscalQueue(fiscal(1, 0, 0, 0), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CentralAlertThresholds.fromJson("{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fiscal_queue_limit_rnf08");
        assertThatThrownBy(() -> CentralAlertThresholds.fromJson(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new CentralAlertThresholds(25).fiscalQueueLimitRnf08()).isEqualTo(25);
        assertThat(CentralAlertThresholds.fromJson("{\"fiscal_queue_limit_rnf08\": 25}")
                .fiscalQueueLimitRnf08()).isEqualTo(25);
        assertThatThrownBy(() -> new CentralAlertThresholds(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("P2.11 (m-1): limiar fracionario no JSON e recusado, nunca truncado; JSON nao-objeto e recusado")
    void thresholdsRefuseFractionAndNonObject() {
        assertThatThrownBy(() -> CentralAlertThresholds.fromJson("{\"fiscal_queue_limit_rnf08\": 10.5}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fiscal_queue_limit_rnf08");
        assertThatThrownBy(() -> CentralAlertThresholds.fromJson("[1, 2]"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CentralAlertThresholds.fromJson("42"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CentralAlertThresholds.fromJson("\"texto\""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- Regras no padrao P3.11 (owner + runbook obrigatorios) ----

    @Test
    @DisplayName("P2.11: regras de alerta nascem com runbook; sem owner a P3.11 recusa")
    void alertRulesRequireOwnerAndCarryRunbook() {
        var billing = CentralBillingFiscalAlerts.billingCycleRule("platform");
        assertThat(billing.ruleKey()).isEqualTo("central.billing.cycle_overdue");
        assertThat(billing.runbookUrl()).isEqualTo("docs/operacao/runbooks/RB-07-ciclo-faturamento-atrasado.md");
        var fiscal = CentralBillingFiscalAlerts.fiscalQueueRule("platform", new CentralAlertThresholds(10));
        assertThat(fiscal.threshold()).isEqualTo(10.0);
        assertThat(fiscal.runbookUrl()).isEqualTo("docs/operacao/runbooks/RB-08-fila-fiscal-acima-do-limite.md");
        assertThatThrownBy(() -> CentralBillingFiscalAlerts.billingCycleRule(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("responsável");
    }

    // ---- Forma dos instantaneos: ausencia e erro explicito ----

    @Test
    @DisplayName("P2.11: instantaneo com estado desconhecido ou contagem ausente e recusado")
    void snapshotsRefuseUnknownOrMissingData() {
        assertThatThrownBy(() -> item("BILLING", true, "LATE", "2026-11-09"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("V4_3");
        assertThatThrownBy(() -> new FiscalQueueSnapshot(TENANT, Map.of("RESERVED", 1L), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ausente");
    }

    @Test
    @DisplayName("P2.11 (m-3/M-2): lista de itens nula e recusada (vazio explicito nao); item sem grupo e recusado")
    void snapshotsRefuseNullItemsAndGrouplessItem() {
        assertThatThrownBy(() -> new BillingCycleSnapshot(TENANT, YearMonth.of(2026, 11), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("items");
        assertThatThrownBy(() -> new BillingCycleSnapshot.ScheduleItemView(
                null, "BILLING", true, "PENDING", LocalDate.of(2026, 11, 9)))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("group_id");
    }

    @Test
    @DisplayName("P2.11 (m-4): contagem negativa no instantaneo fiscal e recusada")
    void fiscalSnapshotRefusesNegativeCount() {
        assertThatThrownBy(() -> new FiscalQueueSnapshot(TENANT, Map.of(
                "RESERVED", -1L, "SIGNED", 0L, "TRANSMITTED", 0L, "AUTHORIZED", 0L,
                "REJECTED", 0L, "PENDING_RECONCILIATION", 0L, "CANCELLED", 0L,
                "REPLACED", 0L, "VOIDED", 0L), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negativa");
    }
}
