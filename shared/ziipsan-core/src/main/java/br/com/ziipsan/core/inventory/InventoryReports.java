package br.com.ziipsan.core.inventory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Relatorios do almoxarifado (ED-58): materiais e insumos, relatorio
 * DIARIO de negativos com alerta alem do prazo de regularizacao, ponto
 * de reposicao, evidencias por tarefa (ISO 45001/ED-71) e a visao da aba
 * Estoque da Central Ziip (saldos, negativos e alertas POR TENANT).
 * Funcoes puras sobre o store — nenhum numero e inventado aqui.
 */
public final class InventoryReports {
    private InventoryReports() {}

    public record BalanceRow(String itemCode, String locationCode, BigDecimal physical,
                             BigDecimal reserved, BigDecimal available, BigDecimal averageCost, BigDecimal stockValue) {}
    public record NegativeRow(String itemCode, String locationCode, BigDecimal physical, BigDecimal reserved,
                              BigDecimal available, Instant negativeSince, long daysNegative, boolean overdue) {}
    public record CentralStockView(java.util.UUID tenantId, List<BalanceRow> balances, List<NegativeRow> negatives,
                                   List<InventoryPorts.Alert> alerts, List<InventoryStore.ShortfallPendency> pendencies) {}
    public record ItemTotals(String itemCode, BigDecimal physical, BigDecimal reserved, BigDecimal available, BigDecimal stockValue) {}

    public static List<BalanceRow> balances(InventoryStore store, java.util.UUID tenant) {
        return store.balances(tenant).stream().map(b -> new BalanceRow(b.itemCode(), b.locationCode(), b.physical(),
                b.reserved(), b.available(), b.averageCost(), b.stockValue())).toList();
    }

    /** Relatorio diario de negativos: disponivel ou fisico < 0, com dias e estouro do prazo do tenant. */
    public static List<NegativeRow> dailyNegatives(InventoryStore store, java.util.UUID tenant, Instant today, int regularizationDays) {
        return store.balances(tenant).stream()
                .filter(b -> b.available().signum() < 0 || b.physical().signum() < 0)
                .map(b -> {
                    Instant since = store.negativeSince(tenant, b.itemCode(), b.locationCode()).orElse(today);
                    long days = Math.max(0, Duration.between(since, today).toDays());
                    return new NegativeRow(b.itemCode(), b.locationCode(), b.physical(), b.reserved(), b.available(),
                            since, days, days > regularizationDays);
                }).toList();
    }

    /** Itens no/abaixo do ponto de reposicao declarado no item (ausencia de ponto = fora do relatorio). */
    public static List<BalanceRow> atOrBelowReorderPoint(InventoryStore store, java.util.UUID tenant) {
        return store.balances(tenant).stream()
                .filter(b -> store.item(tenant, b.itemCode()).map(i -> i.reorderPoint() != null
                        && b.available().compareTo(i.reorderPoint()) <= 0).orElse(false))
                .map(b -> new BalanceRow(b.itemCode(), b.locationCode(), b.physical(), b.reserved(), b.available(), b.averageCost(), b.stockValue()))
                .toList();
    }

    /** Relatorio de materiais e insumos: totais por item (todos os locais) com valor em estoque pelo custo medio. */
    public static List<ItemTotals> materialsReport(InventoryStore store, java.util.UUID tenant) {
        java.util.Map<String, List<StockBalance>> byItem = new java.util.LinkedHashMap<>();
        for (StockBalance b : store.balances(tenant)) byItem.computeIfAbsent(b.itemCode(), k -> new java.util.ArrayList<>()).add(b);
        return byItem.entrySet().stream().map(e -> new ItemTotals(e.getKey(),
                sum(e.getValue(), StockBalance::physical), sum(e.getValue(), StockBalance::reserved),
                sum(e.getValue(), StockBalance::physical).subtract(sum(e.getValue(), StockBalance::reserved)),
                e.getValue().stream().map(StockBalance::stockValue).reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(2, RoundingMode.HALF_UP))).toList();
    }
    private static BigDecimal sum(List<StockBalance> bs, java.util.function.Function<StockBalance, BigDecimal> f) {
        return bs.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Aba Estoque da Central Ziip (CENTRAL-ZIIP §3): saldos, negativos e alertas do tenant. */
    public static CentralStockView centralView(InventoryStore store, InventoryParameters parameters, java.util.UUID tenant, Instant today) {
        int days = InventoryParameters.requireRegularizationDays(parameters.negativeRegularizationDays(tenant));
        return new CentralStockView(tenant, balances(store, tenant), dailyNegatives(store, tenant, today, days),
                store.alerts(tenant), store.pendencies(tenant));
    }

    /** Evidencia de consumo/EPI por tarefa para a ISO 45001 (consumida pela ED-71). */
    public static List<InventoryStore.TaskConsumption> taskEvidence(InventoryStore store, java.util.UUID tenant, String taskId) {
        Objects.requireNonNull(taskId, "task_id e obrigatorio");
        return store.taskConsumptions(tenant).stream().filter(c -> taskId.equals(c.taskId())).toList();
    }

    /** Alertas de validade de lote na janela do tenant (inv.lot_expiry_alert_days). */
    public static List<LotControl.Lot> lotExpiryAlerts(InventoryStore store, InventoryParameters parameters,
                                                       java.util.UUID tenant, LocalDate today) {
        int days = InventoryParameters.requireLotExpiryAlertDays(parameters.lotExpiryAlertDays(tenant));
        return LotControl.expiryAlerts(store.lots(tenant), today, days);
    }
}
