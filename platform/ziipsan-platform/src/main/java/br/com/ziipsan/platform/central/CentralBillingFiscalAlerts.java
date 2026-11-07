package br.com.ziipsan.platform.central;

import br.com.ziipsan.platform.observability.AlertRule;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Regras de alerta das abas Faturamento e Fiscal da Central (P2.11,
 * aceite literal): "ciclo de faturamento atrasado e fila fiscal acima
 * do RNF-08 disparam alerta". Regras puras, sem banco:
 *
 * <ul>
 *   <li><b>Ciclo atrasado</b> — existe item OBRIGATÓRIO do cronograma
 *       (P2.5/V4_3) com {@code planned_on} estritamente anterior a hoje
 *       e situação não liquidada (PENDING ou COMMANDED). Item com
 *       {@code planned_on} hoje ainda está no prazo; item DONE ou
 *       CANCELLED nunca atrasa; item não obrigatório vencido aparece na
 *       aba como contagem, mas não dispara o alerta (o fechamento da
 *       referência, na P2.5, depende dos obrigatórios).</li>
 *   <li><b>Fila fiscal acima do RNF-08</b> — {@code queueSize()} do
 *       instantâneo fiscal ESTRITAMENTE maior que o limiar configurado
 *       ({@link CentralAlertThresholds}). Fila igual ao limiar está no
 *       limite, não acima dele: não dispara.</li>
 * </ul>
 *
 * <p>As {@link AlertRule} correspondentes seguem a P3.11: owner e
 * runbook obrigatórios na construção — sem runbook a regra nem nasce.
 */
public final class CentralBillingFiscalAlerts {

    public static final String BILLING_CYCLE_OVERDUE_RULE = "central.billing.cycle_overdue";
    public static final String FISCAL_QUEUE_OVER_LIMIT_RULE = "central.fiscal.queue_over_limit";

    public static final String BILLING_RUNBOOK = "docs/operacao/runbooks/RB-07-ciclo-faturamento-atrasado.md";
    public static final String FISCAL_RUNBOOK = "docs/operacao/runbooks/RB-08-fila-fiscal-acima-do-limite.md";

    private CentralBillingFiscalAlerts() {
    }

    /** Itens obrigatórios vencidos e não liquidados (a contagem que a métrica publica). */
    public static List<BillingCycleSnapshot.ScheduleItemView> overdueMandatoryItems(
            BillingCycleSnapshot snapshot, LocalDate today) {
        Objects.requireNonNull(snapshot, "instantâneo do ciclo é obrigatório");
        Objects.requireNonNull(today, "a data de hoje é obrigatória (fuso do tenant, P0.19)");
        return snapshot.items().stream()
                .filter(item -> item.mandatory() && !item.isSettled() && item.plannedOn().isBefore(today))
                .toList();
    }

    /**
     * Teste do alerta 1 (aceite): ciclo de faturamento atrasado dispara.
     * Instantâneo vazio (tenant sem cronograma, ver
     * {@link BillingCycleSnapshot}) devolve vazio — sem atraso — sem
     * exceção, para não abortar a coleta dos demais tenants (m-3).
     * O sinal carrega o grupo do item vencido MAIS ANTIGO (menor
     * {@code planned_on}), que é o que o RB-07 manda identificar (M-2).
     */
    public static Optional<CentralAlertSignal> evaluateBillingCycle(
            BillingCycleSnapshot snapshot, LocalDate today) {
        List<BillingCycleSnapshot.ScheduleItemView> overdue = overdueMandatoryItems(snapshot, today);
        if (overdue.isEmpty()) {
            return Optional.empty();
        }
        BillingCycleSnapshot.ScheduleItemView oldest = overdue.stream()
                .min(Comparator.comparing(BillingCycleSnapshot.ScheduleItemView::plannedOn))
                .orElseThrow();
        return Optional.of(new CentralAlertSignal(
                BILLING_CYCLE_OVERDUE_RULE, CentralMetrics.BILLING_CYCLE_OVERDUE_ITEMS,
                snapshot.tenantId(), oldest.groupId(), overdue.size(), 0,
                "Ciclo " + snapshot.referenceMonth() + " atrasado: " + overdue.size()
                        + " item(ns) obrigatório(s) vencido(s) e não realizado(s); mais antigo: "
                        + oldest.activityCode() + " do grupo " + oldest.groupId()
                        + " previsto para " + oldest.plannedOn()));
    }

    /** Teste do alerta 2 (aceite): fila fiscal acima do RNF-08 dispara. */
    public static Optional<CentralAlertSignal> evaluateFiscalQueue(
            FiscalQueueSnapshot snapshot, CentralAlertThresholds thresholds) {
        Objects.requireNonNull(snapshot, "instantâneo fiscal é obrigatório");
        Objects.requireNonNull(thresholds,
                "limiares são obrigatórios: sem o limiar do RNF-08 o alerta fiscal não é avaliado");
        long queue = snapshot.queueSize();
        if (queue <= thresholds.fiscalQueueLimitRnf08()) {
            return Optional.empty();
        }
        return Optional.of(new CentralAlertSignal(
                FISCAL_QUEUE_OVER_LIMIT_RULE, CentralMetrics.FISCAL_QUEUE_PENDING,
                snapshot.tenantId(), null, queue, thresholds.fiscalQueueLimitRnf08(),
                "Fila fiscal com " + queue + " documento(s) aguardando desfecho, acima do limiar "
                        + "do RNF-08 (" + thresholds.fiscalQueueLimitRnf08() + "); mais antigo há "
                        + snapshot.oldestPendingAgeSeconds() + "s"));
    }

    /** Regra de alerta do ciclo (P3.11): qualquer item obrigatório vencido (limiar 0). */
    public static AlertRule billingCycleRule(String owner) {
        return new AlertRule(BILLING_CYCLE_OVERDUE_RULE, CentralMetrics.BILLING_CYCLE_OVERDUE_ITEMS,
                ">", 0, 1, "WARNING", "TENANT", owner, BILLING_RUNBOOK);
    }

    /** Regra de alerta da fila: o limiar vem da configuração do RNF-08, nunca de constante. */
    public static AlertRule fiscalQueueRule(String owner, CentralAlertThresholds thresholds) {
        Objects.requireNonNull(thresholds, "limiares são obrigatórios para montar a regra da fila fiscal");
        return new AlertRule(FISCAL_QUEUE_OVER_LIMIT_RULE, CentralMetrics.FISCAL_QUEUE_PENDING,
                ">", thresholds.fiscalQueueLimitRnf08(), 1, "WARNING", "TENANT", owner, FISCAL_RUNBOOK);
    }
}
