package br.com.ziipsan.core.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Massa SINTETICA declarada dos testes da ED-58 (nenhum dado real). */
final class InventoryFixtures {
    static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000005801");
    static final UUID OTHER_TENANT = UUID.fromString("00000000-0000-0000-0000-000000005802");
    static final UUID REQUESTER = UUID.fromString("00000000-0000-0000-0000-000000005811");
    static final UUID APPROVER = UUID.fromString("00000000-0000-0000-0000-000000005812");
    static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000005813");
    static final UUID ORDER = UUID.fromString("00000000-0000-0000-0000-000000005820");
    static final Instant T0 = Instant.parse("2026-11-09T10:00:00Z");
    static final String LOC = "ALMOX-CENTRAL";
    static final String LOC2 = "ALMOX-BAIRRO";

    static BigDecimal bd(String s) { return new BigDecimal(s); }

    /** Parametros sinteticos do tenant de teste: auto ate 100,00; faixas 500/ALMOXARIFE, acima/GESTOR; regularizacao 5 dias; validade 30 dias. */
    static InventoryParameters params() {
        return new InventoryParameters() {
            public Optional<BigDecimal> autoApprovalLimit(UUID t) { return Optional.of(bd("100.00")); }
            public Optional<List<ApprovalBand>> approvalBands(UUID t) {
                return Optional.of(List.of(new ApprovalBand(bd("500.00"), "ALMOXARIFE"), new ApprovalBand(null, "GESTOR")));
            }
            public Optional<Integer> negativeRegularizationDays(UUID t) { return Optional.of(5); }
            public Optional<Integer> lotExpiryAlertDays(UUID t) { return Optional.of(30); }
        };
    }
    static InventoryParameters noParams() {
        return new InventoryParameters() {
            public Optional<BigDecimal> autoApprovalLimit(UUID t) { return Optional.empty(); }
            public Optional<List<ApprovalBand>> approvalBands(UUID t) { return Optional.empty(); }
            public Optional<Integer> negativeRegularizationDays(UUID t) { return Optional.empty(); }
            public Optional<Integer> lotExpiryAlertDays(UUID t) { return Optional.empty(); }
        };
    }

    static WarehouseService service(InMemoryInventoryStore store) {
        return new WarehouseService(store, params(), null);
    }

    /** Loja pronta: 2 locais, CANO (comum, reposicao 5), VALVULA (CONTROLADO), LUVA (EPI lote), HIDRO (serializado). */
    static WarehouseService stocked(InMemoryInventoryStore store) {
        WarehouseService w = service(store);
        w.registerLocation(TENANT, new InventoryItem.StockLocation(LOC, "Almoxarifado central", true), ACTOR, T0);
        w.registerLocation(TENANT, new InventoryItem.StockLocation(LOC2, "Almoxarifado bairro", true), ACTOR, T0);
        w.registerItem(TENANT, new InventoryItem.Item("CANO", "Cano PVC 100mm", InventoryItem.Kind.NETWORK_MATERIAL, "m", false, false, false, bd("5"), true), ACTOR, T0);
        w.registerItem(TENANT, new InventoryItem.Item("VALVULA", "Valvula controlada", InventoryItem.Kind.NETWORK_MATERIAL, "un", true, false, false, null, true), ACTOR, T0);
        w.registerItem(TENANT, new InventoryItem.Item("LUVA", "Luva de protecao", InventoryItem.Kind.PPE, "par", false, false, true, null, true), ACTOR, T0);
        w.registerItem(TENANT, new InventoryItem.Item("HIDRO", "Hidrometro DN20", InventoryItem.Kind.METER, "un", false, true, false, null, true), ACTOR, T0);
        return w;
    }

    static Set<String> roles(String... r) { return Set.of(r); }
    private InventoryFixtures() {}
}
