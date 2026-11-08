package br.com.ziipsan.core.chemicals;

import br.com.ziipsan.core.inventory.LotControl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Relatorios e saidas da ED-89 — funcoes puras sobre o store e as
 * portas, nenhum numero inventado:
 * <ul>
 *   <li>consumo por dia e por m3 tratado, com a conciliacao do periodo;</li>
 *   <li>alerta de ESTOQUE MINIMO por cobertura em dias
 *       ({@code chem.min_stock_days}): cobertura = disponivel da ED-58
 *       dividido pelo consumo medio diario REAL lancado; sem historico
 *       de consumo, a cobertura e INDETERMINADA (nenhum alerta, nenhuma
 *       presuncao);</li>
 *   <li>alertas de validade de lote, reusando {@link LotControl} REAL
 *       da ED-58 e a janela {@code inv.lot_expiry_alert_days} dela;</li>
 *   <li>REGISTRO PUBLICO de limpeza de reservatorios (OPE-07): so
 *       execucoes encerradas com laudo, sem dado pessoal — fonte do
 *       portal, do relatorio anual da ED-79 e, pelo volume descartado,
 *       do balanco operacional da ED-85.</li>
 * </ul>
 */
public final class ChemReports {
    private ChemReports() {}

    public record DailyRow(LocalDate date, String treatmentUnitCode, BigDecimal treatedVolumeM3,
                           BigDecimal actualQty, BigDecimal theoreticalQty, BigDecimal actualPerM3,
                           DailyConsumption.Status status) {}

    /** Consumo diario do item no periodo [from, to], com conciliacao pela tolerancia informada (null = REPORTED). */
    public static List<DailyRow> dailyConsumption(ChemStore store, UUID tenant, String itemCode,
                                                  LocalDate from, LocalDate to, BigDecimal tolerancePct) {
        return store.consumptions(tenant, itemCode).stream()
                .filter(c -> !c.consumptionDate().isBefore(from) && !c.consumptionDate().isAfter(to))
                .sorted(java.util.Comparator.comparing(DailyConsumption::consumptionDate))
                .map(c -> { DailyConsumption.Reconciliation r = c.reconcile(tolerancePct);
                    return new DailyRow(c.consumptionDate(), c.treatmentUnitCode(), c.treatedVolumeM3(),
                            c.actualQty(), r.theoreticalQty(), c.actualPerM3(), r.status()); })
                .toList();
    }

    public record Coverage(String itemCode, String locationCode, BigDecimal available,
                           BigDecimal averageDailyUse, BigDecimal coverageDays, boolean belowMinimum) {}

    /**
     * Cobertura de estoque em dias, por item x local de armazenamento.
     * Media diaria = total consumido / dias DISTINTOS com consumo
     * lancado ate a data de referencia (declarado: sem janela presumida).
     * Sem consumo lancado ou sem saldo lido da ED-58: cobertura null e
     * belowMinimum=false — indeterminado NUNCA vira alerta nem folga.
     */
    public static Coverage coverage(ChemStore store, ChemPorts.StockQueryPort stock, ChemParameters parameters,
                                    UUID tenant, String itemCode, LocalDate referenceDate) {
        ChemProduct product = store.product(tenant, itemCode).orElseThrow(() ->
                new ChemExceptions.NotFoundException("CHEM_PRODUCT_NOT_FOUND", "produto quimico nao cadastrado: " + itemCode));
        int minDays = ChemParameters.requireMinStockDays(parameters.minStockDays(tenant));
        var view = stock.stock(tenant, itemCode, product.storageLocationCode()).orElse(null);
        List<DailyConsumption> history = store.consumptions(tenant, itemCode).stream()
                .filter(c -> !c.consumptionDate().isAfter(referenceDate)).toList();
        long distinctDays = history.stream().map(DailyConsumption::consumptionDate).distinct().count();
        if (view == null || distinctDays == 0)
            return new Coverage(itemCode, product.storageLocationCode(), view == null ? null : view.available(),
                    null, null, false);
        BigDecimal total = history.stream().map(DailyConsumption::actualQty).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgDaily = total.divide(BigDecimal.valueOf(distinctDays), 6, RoundingMode.HALF_UP);
        if (avgDaily.signum() == 0)
            return new Coverage(itemCode, product.storageLocationCode(), view.available(), avgDaily, null, false);
        BigDecimal days = view.available().divide(avgDaily, 2, RoundingMode.HALF_UP);
        return new Coverage(itemCode, product.storageLocationCode(), view.available(), avgDaily, days,
                days.compareTo(BigDecimal.valueOf(minDays)) < 0);
    }

    /** Alertas de validade reusando a regra REAL da ED-58 sobre os lotes recebidos aqui. */
    public static List<LotControl.Lot> lotExpiryAlerts(ChemStore store, UUID tenant, String itemCode,
                                                       LocalDate today, int alertDays) {
        List<LotControl.Lot> lots = store.lots(tenant, itemCode).stream()
                .map(l -> new LotControl.Lot(tenant, l.itemCode(), l.locationCode(), l.lotCode(),
                        l.expiryDate(), null, l.qty()))
                .toList();
        return LotControl.expiryAlerts(lots, today, alertDays);
    }

    // ---------- OPE-07: registro publico / ED-79 / ED-85 ----------

    public record PublicCleaningRecord(String reservoirCode, String reservoirName, String supplySystem,
                                       LocalDate executedOn, String reportRef,
                                       String productItemCode, BigDecimal productQty,
                                       BigDecimal discardedVolumeM3) {}

    /**
     * Registro publico consultavel no portal (Decreto 5.440/2005,
     * Anexo, arts. 16 e 17; Portaria GM/MS 888/2021, art. 14, XVII):
     * TODAS as execucoes gravadas ja exigem laudo no construtor — logo,
     * tudo que esta no store e publicavel; nenhum dado pessoal entra
     * (a execucao nao carrega pessoa).
     */
    public static List<PublicCleaningRecord> publicCleaningRecords(ChemStore store, UUID tenant) {
        return store.cleaningExecutions(tenant).stream()
                .sorted(java.util.Comparator.comparing(ReservoirCleaning.CleaningExecution::executedOn))
                .map(e -> { ReservoirCleaning.Reservoir r = store.reservoir(tenant, e.reservoirCode()).orElse(null);
                    return new PublicCleaningRecord(e.reservoirCode(), r == null ? null : r.name(),
                            r == null ? null : r.supplySystem(), e.executedOn(), e.reportRef(),
                            e.productItemCode(), e.productQty(), e.discardedVolumeM3()); })
                .toList();
    }

    /** Fonte do relatorio anual de qualidade da ED-79: limpezas do ano, por reservatorio. */
    public static List<PublicCleaningRecord> annualReportSource(ChemStore store, UUID tenant, int year) {
        return publicCleaningRecords(store, tenant).stream()
                .filter(r -> r.executedOn().getYear() == year).toList();
    }

    public record DiscardedVolume(String reservoirCode, BigDecimal totalDiscardedM3) {}

    /**
     * Uso operacional da ED-85 (balanco/DMC): volume descartado na
     * limpeza de reservatorio no periodo — volume autorizado nao
     * faturado por limpeza de reservatorio (ficha de perdas). Soma
     * EXATA dos lancamentos; periodo sem execucao devolve lista vazia,
     * nunca zero presumido por reservatorio.
     */
    public static List<DiscardedVolume> discardedVolumes(ChemStore store, UUID tenant, LocalDate from, LocalDate to) {
        java.util.Map<String, BigDecimal> byReservoir = new java.util.LinkedHashMap<>();
        for (ReservoirCleaning.CleaningExecution e : store.cleaningExecutions(tenant)) {
            if (e.executedOn().isBefore(from) || e.executedOn().isAfter(to)) continue;
            byReservoir.merge(e.reservoirCode(), e.discardedVolumeM3(), BigDecimal::add);
        }
        return byReservoir.entrySet().stream()
                .map(e -> new DiscardedVolume(e.getKey(), e.getValue().setScale(2, RoundingMode.HALF_UP))).toList();
    }

    /** Planos vencidos (para alerta de limpeza atrasada). */
    public static List<ReservoirCleaning.CleaningPlan> overdueCleaningPlans(ChemStore store, UUID tenant, LocalDate today) {
        return store.cleaningPlans(tenant).stream().filter(p -> p.overdueOn(today)).toList();
    }
}
