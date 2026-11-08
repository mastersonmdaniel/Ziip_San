package br.com.ziipsan.core.inventory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Implementacao em memoria do {@link InventoryStore} para o nucleo puro/testes. */
public final class InMemoryInventoryStore implements InventoryStore {
    private final Map<String, InventoryItem.StockLocation> locations = new LinkedHashMap<>();
    private final Map<String, InventoryItem.Item> items = new LinkedHashMap<>();
    private final Map<String, StockBalance> balances = new LinkedHashMap<>();
    private final List<StockMovement> movements = new ArrayList<>();
    private final Map<UUID, Requisition> requisitions = new LinkedHashMap<>();
    private final Map<String, Instant> negativeSince = new LinkedHashMap<>();
    private final List<ShortfallPendency> pendencies = new ArrayList<>();
    private final Map<UUID, Transfer> transfers = new LinkedHashMap<>();
    private final List<LotControl.Lot> lots = new ArrayList<>();
    private final Map<String, MaterialKit.Kit> kits = new LinkedHashMap<>();
    private final java.util.Set<String> processedTasks = new java.util.HashSet<>();
    private final List<PosteriorApproval> posterior = new ArrayList<>();
    private final List<TaskConsumption> consumptions = new ArrayList<>();
    private final List<InventoryPorts.AuditEntry> audit = new ArrayList<>();
    private final List<InventoryPorts.Alert> alerts = new ArrayList<>();
    private final List<InventoryCount> counts = new ArrayList<>();

    private static String key(UUID t, String a, String b) { return t + "|" + a + "|" + b; }
    private static String key(UUID t, String a) { return t + "|" + a; }

    public void putLocation(UUID t, InventoryItem.StockLocation l) { locations.put(key(t, l.code()), l); }
    public Optional<InventoryItem.StockLocation> location(UUID t, String c) { return Optional.ofNullable(locations.get(key(t, c))); }
    public void putItem(UUID t, InventoryItem.Item i) { items.put(key(t, i.code()), i); }
    public Optional<InventoryItem.Item> item(UUID t, String c) { return Optional.ofNullable(items.get(key(t, c))); }
    public List<InventoryItem.Item> items(UUID t) { return items.entrySet().stream().filter(e -> e.getKey().startsWith(t + "|")).map(Map.Entry::getValue).toList(); }
    public StockBalance balance(UUID t, String item, String loc) { return balances.computeIfAbsent(key(t, item, loc), k -> new StockBalance(item, loc)); }
    public List<StockBalance> balances(UUID t) { return balances.entrySet().stream().filter(e -> e.getKey().startsWith(t + "|")).map(Map.Entry::getValue).toList(); }
    public void appendMovement(StockMovement m) { movements.add(m); }
    public List<StockMovement> movements(UUID t) { return movements.stream().filter(m -> m.tenantId().equals(t)).toList(); }
    public void saveRequisition(Requisition r) { requisitions.put(r.id(), r); }
    public Optional<Requisition> requisition(UUID t, UUID id) { Requisition r = requisitions.get(id); return r != null && r.tenantId().equals(t) ? Optional.of(r) : Optional.empty(); }
    public List<Requisition> requisitionsByOrder(UUID t, UUID o) { return requisitions.values().stream().filter(r -> r.tenantId().equals(t) && r.orderId().equals(o)).toList(); }
    public void markNegativeSince(UUID t, String i, String l, Instant s) { negativeSince.putIfAbsent(key(t, i, l), s); }
    public void clearNegativeSince(UUID t, String i, String l) { negativeSince.remove(key(t, i, l)); }
    public Optional<Instant> negativeSince(UUID t, String i, String l) { return Optional.ofNullable(negativeSince.get(key(t, i, l))); }
    public void addPendency(ShortfallPendency p) { pendencies.add(p); }
    public List<ShortfallPendency> pendencies(UUID t) { return pendencies.stream().filter(p -> p.tenantId().equals(t)).toList(); }
    public void saveTransfer(Transfer tr) { transfers.put(tr.id(), tr); }
    public Optional<Transfer> transfer(UUID t, UUID id) { Transfer tr = transfers.get(id); return tr != null && tr.tenantId().equals(t) ? Optional.of(tr) : Optional.empty(); }
    public List<Transfer> transfers(UUID t) { return transfers.values().stream().filter(tr -> tr.tenantId().equals(t)).toList(); }
    public void saveLot(LotControl.Lot l) { lots.add(l); }
    public List<LotControl.Lot> lots(UUID t) { return lots.stream().filter(l -> l.tenantId().equals(t)).toList(); }
    public void saveKit(MaterialKit.Kit k) { kits.put(key(k.tenantId(), k.orderTypeCode()), k); }
    public Optional<MaterialKit.Kit> kit(UUID t, String type) { return Optional.ofNullable(kits.get(key(t, type))); }
    private final java.util.Set<String> processedNfeKeys = new java.util.HashSet<>();
    public boolean nfeEntryProcessed(UUID t, String accessKey) { return processedNfeKeys.contains(key(t, accessKey)); }
    public void markNfeEntryProcessed(UUID t, String accessKey) { processedNfeKeys.add(key(t, accessKey)); }
    public boolean fieldTaskProcessed(UUID t, String task) { return processedTasks.contains(key(t, task)); }
    public void markFieldTaskProcessed(UUID t, String task) { processedTasks.add(key(t, task)); }
    public void savePosteriorApproval(PosteriorApproval a) { posterior.removeIf(x -> x.id().equals(a.id())); posterior.add(a); }
    public List<PosteriorApproval> posteriorApprovals(UUID t) { return posterior.stream().filter(a -> a.tenantId().equals(t)).toList(); }
    public void addTaskConsumption(TaskConsumption c) { consumptions.add(c); }
    public List<TaskConsumption> taskConsumptions(UUID t) { return consumptions.stream().filter(c -> c.tenantId().equals(t)).toList(); }
    public void appendAudit(InventoryPorts.AuditEntry e) { audit.add(e); }
    public List<InventoryPorts.AuditEntry> audit(UUID t) { return audit.stream().filter(e -> e.tenantId().equals(t)).toList(); }
    public void appendAlert(InventoryPorts.Alert a) { alerts.add(a); }
    public List<InventoryPorts.Alert> alerts(UUID t) { return alerts.stream().filter(a -> a.tenantId().equals(t)).toList(); }
    public void saveCount(InventoryCount c) { counts.add(c); }
    public List<InventoryCount> counts(UUID t) { return counts.stream().filter(c -> c.tenantId().equals(t)).toList(); }
}
