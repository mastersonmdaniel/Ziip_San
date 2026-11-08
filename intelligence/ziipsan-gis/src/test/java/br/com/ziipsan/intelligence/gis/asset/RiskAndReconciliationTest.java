package br.com.ziipsan.intelligence.gis.asset;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.ziipsan.intelligence.gis.asset.ReconciliationService.AssetAccountingInput;
import br.com.ziipsan.intelligence.gis.asset.ReconciliationService.Divergence;
import br.com.ziipsan.intelligence.gis.asset.RiskRenewalService.RiskBand;
import br.com.ziipsan.intelligence.gis.asset.RiskRenewalService.RiskInput;

import static br.com.ziipsan.intelligence.gis.asset.AssetFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class RiskAndReconciliationTest {

    @Test
    void probabilityFeedsOnConditionGradeAndBreaks() {
        // grau 3 + 4 rompimentos (bonus 2) = probabilidade 5 (teto)
        assertEquals(5, RiskRenewalService.probabilityOf(new RiskInput(BOMBA, 3, 4, 4, "CRITICAL")));
        assertEquals(2, RiskRenewalService.probabilityOf(new RiskInput(BOMBA, 2, 1, 4, null)));
        var a = RiskRenewalService.assess(new RiskInput(BOMBA, 3, 4, 4, "CRITICAL"));
        assertEquals(20, a.score());
        assertEquals(RiskBand.CRITICAL, a.band());
    }

    @Test
    void withoutConditionGradeAssetLeavesPlanWithReason() {
        var a = RiskRenewalService.assess(new RiskInput(BOMBA, null, 9, 5, "CRITICAL"));
        assertFalse(a.assessed());
        assertEquals("SEM_GRAU_DE_CONDICAO", a.excludedReason());
        assertThrows(IllegalArgumentException.class,
                () -> RiskRenewalService.probabilityOf(new RiskInput(BOMBA, null, 0, 3, null)));
    }

    @Test
    void renewalPlanOrdersByRiskAndKeepsExcludedAtEnd() {
        var low = new RiskInput(id(101), 1, 0, 1, null);          // 1
        var high = new RiskInput(id(102), 4, 0, 4, null);         // 16
        var unknown = new RiskInput(id(103), null, 0, 5, null);   // excluido com motivo
        var plan = RiskRenewalService.renewalPlan(List.of(low, unknown, high));
        assertEquals(List.of(id(102), id(101), id(103)),
                plan.stream().map(r -> r.assetId()).toList());
        assertEquals("SEM_GRAU_DE_CONDICAO", plan.get(2).excludedReason());
    }

    private AssetPorts.AccountingPort accountingPort(java.util.List<String> received) {
        return new AssetPorts.AccountingPort() {
            @Override
            public Optional<String> resolveTenantAccount(String reg, int exercise) {
                return "MCRSB-1".equals(reg) ? Optional.of("1.1.1.01") : Optional.empty();
            }

            @Override
            public void receiveReconciliationExport(UUID tenantId, String ref, int exercise) {
                received.add(ref);
            }
        };
    }

    @Test
    void reconciliationResolvesDeParaAndNamesDivergences() {
        java.util.List<String> received = new java.util.ArrayList<>();
        var svc = new ReconciliationService(accountingPort(received));
        var clean = new AssetAccountingInput(BOMBA, "PAT-B01", "12", "1234567", "MCRSB-1",
                new BigDecimal("45000"), false, null);
        var noDePara = new AssetAccountingInput(VALVULA, "PAT-V01", "12", "1234567", "MCRSB-9",
                new BigDecimal("3000"), false, null);
        var noCost = new AssetAccountingInput(HIDRANTE, null, null, null, null, null, false, null);
        var export = svc.reconcile(UUID.randomUUID(), 2026, List.of(clean, noDePara, noCost));
        assertEquals(3, export.lines().size());
        assertTrue(export.lines().get(0).clean());
        assertEquals("1.1.1.01", export.lines().get(0).tenantAccountCode());
        assertEquals(List.of(Divergence.NO_DEPARA_IN_ED32), export.lines().get(1).divergences());
        assertTrue(export.lines().get(2).divergences().contains(Divergence.MISSING_PATRIMONIAL_CODE));
        assertTrue(export.lines().get(2).divergences().contains(Divergence.MISSING_HISTORICAL_COST));
        assertEquals(2, export.divergentCount());
        assertEquals(List.of(export.reference()), received);
    }

    @Test
    void coverageRegistersAcceptanceCodes() {
        for (String code : List.of("OPE-09", "OPE-12", "OPE-20", "OPE-38", "GES-14",
                "TR-155", "TR-1010", "SG-1180", "COE-05", "DAD-26"))
            assertTrue(AssetCoverage.codes().contains(code), code);
    }
}
