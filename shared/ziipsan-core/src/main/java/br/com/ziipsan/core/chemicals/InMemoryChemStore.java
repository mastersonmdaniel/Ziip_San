package br.com.ziipsan.core.chemicals;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Implementacao em memoria do {@link ChemStore} para o nucleo puro/testes. */
public final class InMemoryChemStore implements ChemStore {
    private final Map<String, ChemProduct> products = new LinkedHashMap<>();
    private final List<ChemReceipt.LotReceipt> lots = new ArrayList<>();
    private final java.util.Set<String> processedReceipts = new java.util.HashSet<>();
    private final List<ChemReceipt.Receipt> receipts = new ArrayList<>();
    private final List<DailyConsumption> consumptions = new ArrayList<>();
    private final Map<UUID, PfControl.PreEnableCheck> pfChecks = new LinkedHashMap<>();
    private final List<PfControl.PreEnableCheck> pfCheckHistory = new ArrayList<>();
    private final List<PfControl.PfRegistration> pfRegistrations = new ArrayList<>();
    private final List<PfControl.PfLicence> pfLicences = new ArrayList<>();
    private final Map<UUID, PfControl.PfState> pfStates = new LinkedHashMap<>();
    private final List<PfControl.PeriodicReport> periodicReports = new ArrayList<>();
    private final Map<String, ReservoirCleaning.Reservoir> reservoirs = new LinkedHashMap<>();
    private final Map<String, ReservoirCleaning.CleaningPlan> plans = new LinkedHashMap<>();
    private final Map<String, UUID> generatedOrders = new LinkedHashMap<>();
    private final List<ReservoirCleaning.CleaningExecution> executions = new ArrayList<>();
    private final List<ChemPorts.AuditEntry> audit = new ArrayList<>();
    private final List<ChemPorts.Alert> alerts = new ArrayList<>();

    private static String key(UUID t, String a) { return t + "|" + a; }
    private static String key(UUID t, String a, String b) { return t + "|" + a + "|" + b; }

    public void putProduct(ChemProduct p) { products.put(key(p.tenantId(), p.inventoryItemCode()), p); }
    public Optional<ChemProduct> product(UUID t, String code) { return Optional.ofNullable(products.get(key(t, code))); }
    public List<ChemProduct> products(UUID t) { return products.values().stream().filter(p -> p.tenantId().equals(t)).toList(); }

    public void saveLotReceipt(ChemReceipt.LotReceipt l) { lots.removeIf(x -> x.tenantId().equals(l.tenantId()) && x.itemCode().equals(l.itemCode()) && x.lotCode().equals(l.lotCode())); lots.add(l); }
    public List<ChemReceipt.LotReceipt> lots(UUID t, String item) { return lots.stream().filter(l -> l.tenantId().equals(t) && l.itemCode().equals(item)).toList(); }
    public Optional<ChemReceipt.LotReceipt> lot(UUID t, String item, String lot) { return lots.stream().filter(l -> l.tenantId().equals(t) && l.itemCode().equals(item) && l.lotCode().equals(lot)).findFirst(); }

    public boolean receiptProcessed(UUID t, String k) { return processedReceipts.contains(key(t, k)); }
    public void markReceiptProcessed(UUID t, String k) { processedReceipts.add(key(t, k)); }
    public void saveReceipt(ChemReceipt.Receipt r) { receipts.add(r); }
    public List<ChemReceipt.Receipt> receipts(UUID t) { return receipts.stream().filter(r -> r.tenantId().equals(t)).toList(); }

    public void saveConsumption(DailyConsumption c) { consumptions.add(c); }
    public List<DailyConsumption> consumptions(UUID t, String item) { return consumptions.stream().filter(c -> c.tenantId().equals(t) && c.itemCode().equals(item)).toList(); }

    public void savePfCheck(PfControl.PreEnableCheck c) {
        pfChecks.put(c.tenantId(), c);
        // historico por versao (R2): reconferir a mesma versao substitui
        // a conferencia daquela versao; versoes distintas coexistem.
        pfCheckHistory.removeIf(x -> x.tenantId().equals(c.tenantId()) && x.listVersion().equals(c.listVersion()));
        pfCheckHistory.add(c);
    }
    public Optional<PfControl.PreEnableCheck> latestPfCheck(UUID t) { return Optional.ofNullable(pfChecks.get(t)); }
    public Optional<PfControl.PreEnableCheck> pfCheckForVersion(UUID t, String version) {
        if (version == null) return Optional.empty();
        return pfCheckHistory.stream().filter(c -> c.tenantId().equals(t) && c.listVersion().equals(version))
                .reduce((a, b) -> b);
    }
    public void savePfRegistration(PfControl.PfRegistration r) { pfRegistrations.add(r); }
    public List<PfControl.PfRegistration> pfRegistrations(UUID t) { return pfRegistrations.stream().filter(r -> r.tenantId().equals(t)).toList(); }
    public void savePfLicence(PfControl.PfLicence l) { pfLicences.add(l); }
    public List<PfControl.PfLicence> pfLicences(UUID t) { return pfLicences.stream().filter(l -> l.tenantId().equals(t)).toList(); }
    public void savePfState(PfControl.PfState s) { pfStates.put(s.tenantId(), s); }
    public Optional<PfControl.PfState> pfState(UUID t) { return Optional.ofNullable(pfStates.get(t)); }
    public void savePeriodicReport(PfControl.PeriodicReport r) { periodicReports.removeIf(x -> x.tenantId().equals(r.tenantId()) && x.periodLabel().equals(r.periodLabel())); periodicReports.add(r); }
    public List<PfControl.PeriodicReport> periodicReports(UUID t) { return periodicReports.stream().filter(r -> r.tenantId().equals(t)).toList(); }

    public void putReservoir(ReservoirCleaning.Reservoir r) { reservoirs.put(key(r.tenantId(), r.code()), r); }
    public Optional<ReservoirCleaning.Reservoir> reservoir(UUID t, String code) { return Optional.ofNullable(reservoirs.get(key(t, code))); }
    public List<ReservoirCleaning.Reservoir> reservoirs(UUID t) { return reservoirs.values().stream().filter(r -> r.tenantId().equals(t)).toList(); }
    public void saveCleaningPlan(ReservoirCleaning.CleaningPlan p) { plans.put(key(p.tenantId(), p.reservoirCode()), p); }
    public Optional<ReservoirCleaning.CleaningPlan> cleaningPlan(UUID t, String code) { return Optional.ofNullable(plans.get(key(t, code))); }
    public List<ReservoirCleaning.CleaningPlan> cleaningPlans(UUID t) { return plans.values().stream().filter(p -> p.tenantId().equals(t)).toList(); }
    public boolean cleaningOrderGenerated(UUID t, String code, LocalDate due) { return generatedOrders.containsKey(key(t, code, due.toString())); }
    public void markCleaningOrderGenerated(UUID t, String code, LocalDate due, UUID orderId) { generatedOrders.put(key(t, code, due.toString()), orderId); }
    public Optional<UUID> cleaningOrderId(UUID t, String code, LocalDate due) { return Optional.ofNullable(generatedOrders.get(key(t, code, due.toString()))); }
    public void saveCleaningExecution(ReservoirCleaning.CleaningExecution e) { executions.add(e); }
    public List<ReservoirCleaning.CleaningExecution> cleaningExecutions(UUID t) { return executions.stream().filter(e -> e.tenantId().equals(t)).toList(); }

    public void appendAudit(ChemPorts.AuditEntry e) { audit.add(e); }
    public List<ChemPorts.AuditEntry> audit(UUID t) { return audit.stream().filter(e -> e.tenantId().equals(t)).toList(); }
    public void appendAlert(ChemPorts.Alert a) { alerts.add(a); }
    public List<ChemPorts.Alert> alerts(UUID t) { return alerts.stream().filter(a -> a.tenantId().equals(t)).toList(); }
}
