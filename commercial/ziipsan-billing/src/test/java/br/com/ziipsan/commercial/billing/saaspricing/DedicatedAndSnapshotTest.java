package br.com.ziipsan.commercial.billing.saaspricing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DedicatedAndSnapshotTest {

    private List<CostComponent> stack() {
        return List.of(
                new CostComponent("api", new BigDecimal("3000.00"), CostComponent.Allocation.FIXED_PER_TENANT),
                new CostComponent("banco", new BigDecimal("2000.00"), CostComponent.Allocation.FIXED_PER_TENANT));
    }

    @Test void dedicatedPriceCoversOwnStackWithMargin() {
        var params = SaasPricingParameters.fromValues(Map.of(
                "saas.margin_percent", "20", "saas.pass_through_mode", "FRANCHISE"));
        var quote = DedicatedInstancePricing.coveringPrice(stack(), params);
        assertEquals(new BigDecimal("5000.00"), quote.ownStackMonthlyCost());
        assertEquals(new BigDecimal("6000.00"), quote.monthlyPrice());
    }

    @Test void quoteBelowStackCostRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> DedicatedInstancePricing.assertCoversStack(new BigDecimal("4999.99"), stack()));
        assertDoesNotThrow(() -> DedicatedInstancePricing.assertCoversStack(new BigDecimal("5000.00"), stack()));
    }

    @Test void perConnectionComponentRefusedInOwnStackCost() {
        // Sonda exata da revisao (D-02): pilha com um unico componente
        // PER_CONNECTION de 2,00 NAO pode virar "custo da pilha" de 2,00
        // nem fazer assertCoversStack aceitar cotacao de 2,00.
        var perConnectionOnly = List.of(
                new CostComponent("ligacao", new BigDecimal("2.00"), CostComponent.Allocation.PER_CONNECTION));
        var ex = assertThrows(IllegalArgumentException.class,
                () -> DedicatedInstancePricing.ownStackCost(perConnectionOnly));
        assertTrue(ex.getMessage().contains("PER_CONNECTION"));
        assertTrue(ex.getMessage().contains("FIXED_PER_TENANT"));
        assertThrows(IllegalArgumentException.class,
                () -> DedicatedInstancePricing.assertCoversStack(new BigDecimal("2.00"), perConnectionOnly));
        var params = SaasPricingParameters.fromValues(Map.of(
                "saas.margin_percent", "20", "saas.pass_through_mode", "FRANCHISE"));
        assertThrows(IllegalArgumentException.class,
                () -> DedicatedInstancePricing.coveringPrice(perConnectionOnly, params));
        // SHARED_POOL tambem nao e custo fixo da pilha propria.
        var sharedPoolOnly = List.of(
                new CostComponent("pool", new BigDecimal("9000.00"), CostComponent.Allocation.SHARED_POOL));
        assertThrows(IllegalArgumentException.class,
                () -> DedicatedInstancePricing.ownStackCost(sharedPoolOnly));
        // Pilha mista (fixo legitimo + nao-fixo) tambem e recusada.
        var mixed = List.of(
                new CostComponent("api", new BigDecimal("3000.00"), CostComponent.Allocation.FIXED_PER_TENANT),
                new CostComponent("ligacao", new BigDecimal("2.00"), CostComponent.Allocation.PER_CONNECTION));
        assertThrows(IllegalArgumentException.class,
                () -> DedicatedInstancePricing.ownStackCost(mixed));
    }

    @Test void fixedPerTenantStackStillAccepted() {
        // Caminho legitimo preservado apos a guarda da D-02: pilha so
        // FIXED_PER_TENANT soma, cobre com margem e aceita cotacao no custo.
        assertEquals(new BigDecimal("5000.00"), DedicatedInstancePricing.ownStackCost(stack()));
        assertDoesNotThrow(() -> DedicatedInstancePricing.assertCoversStack(new BigDecimal("5000.00"), stack()));
        var params = SaasPricingParameters.fromValues(Map.of(
                "saas.margin_percent", "20", "saas.pass_through_mode", "FRANCHISE"));
        var quote = DedicatedInstancePricing.coveringPrice(stack(), params);
        assertEquals(new BigDecimal("5000.00"), quote.ownStackMonthlyCost());
        assertEquals(new BigDecimal("6000.00"), quote.monthlyPrice());
    }

    @Test void emptyStackRefusedAsADefinir() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> DedicatedInstancePricing.ownStackCost(List.of()));
        assertTrue(ex.getMessage().contains("[A DEFINIR]"));
    }

    @Test void sealedSnapshotVerifies() {
        UUID tenant = UUID.randomUUID();
        var snap = ActiveConnectionSnapshot.sealed(tenant, YearMonth.of(2026, 6), 500);
        assertTrue(snap.verifyHash());
        assertEquals(500, snap.verifiedConnections());
    }

    @Test void tamperedSnapshotDetected() {
        UUID tenant = UUID.randomUUID();
        var snap = ActiveConnectionSnapshot.sealed(tenant, YearMonth.of(2026, 6), 500);
        var tampered = new ActiveConnectionSnapshot(tenant, YearMonth.of(2026, 6), 501, snap.hash());
        assertFalse(tampered.verifyHash());
        assertThrows(IllegalStateException.class, tampered::verifiedConnections);
    }

    @Test void hashDependsOnCompetenceAndTenant() {
        UUID tenant = UUID.randomUUID();
        String h1 = ActiveConnectionSnapshot.computeHash(tenant, YearMonth.of(2026, 6), 500);
        assertNotEquals(h1, ActiveConnectionSnapshot.computeHash(tenant, YearMonth.of(2026, 7), 500));
        assertNotEquals(h1, ActiveConnectionSnapshot.computeHash(UUID.randomUUID(), YearMonth.of(2026, 6), 500));
    }
}
