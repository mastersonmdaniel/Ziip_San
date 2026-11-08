package br.com.ziipsan.integration.erp;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ErpIndicatorsTest {

    private static ErpIndicators.ErpIndicator ind(java.util.UUID tenant, ErpIndicators.Area area, String code) {
        return new ErpIndicators.ErpIndicator(tenant, ErpProvider.BETHA, area, code,
                new BigDecimal("42.5"), LocalDate.of(2026, 11, 1), "erp:report-7");
    }

    @Test
    void panelFiltersByTenantAndCompetence() {
        var read = List.of(
                ind(ErpFixtures.TENANT, ErpIndicators.Area.PURCHASES, "COMPRAS_MES"),
                ind(ErpFixtures.TENANT_B, ErpIndicators.Area.HR, "HEADCOUNT"),
                ind(ErpFixtures.TENANT, ErpIndicators.Area.BUDGET, "ORC_EXEC"));
        var panel = ErpIndicators.strategicPanel(read, ErpFixtures.TENANT, LocalDate.of(2026, 11, 1));
        assertEquals(2, panel.size());
        assertTrue(panel.stream().allMatch(i -> i.tenantId().equals(ErpFixtures.TENANT)));
    }

    @Test
    void visanItemTExposesTheSameReadIndicators() {
        var read = List.of(ind(ErpFixtures.TENANT, ErpIndicators.Area.HR, "HEADCOUNT"));
        assertEquals(ErpIndicators.strategicPanel(read, ErpFixtures.TENANT, LocalDate.of(2026, 11, 1)),
                ErpIndicators.visanItemT(read, ErpFixtures.TENANT, LocalDate.of(2026, 11, 1)));
    }

    @Test
    void indicatorRequiresErpOriginReference() {
        assertThrows(IllegalArgumentException.class, () -> new ErpIndicators.ErpIndicator(
                ErpFixtures.TENANT, ErpProvider.IPM, ErpIndicators.Area.BUDGET, "X",
                BigDecimal.ONE, LocalDate.of(2026, 11, 1), " "));
    }

    @Test
    void coverageDeclaresEd95OutAndSupplierProofPending() {
        var items = ErpCoverage.items();
        assertTrue(items.stream().anyMatch(i -> i.requirement().contains("ED-95") || i.implementation().contains("ED-95")));
        assertTrue(items.stream().anyMatch(i -> i.implementation().contains("PENDENCIA EXTERNA B1")));
        assertTrue(items.stream().anyMatch(i -> i.requirement().startsWith("GES-23")));
    }
}
