package br.com.ziipsan.core.sewerraw;

import br.com.ziipsan.core.effluent.SludgeRecord;
import br.com.ziipsan.core.stationops.StationUnit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BiosolidServiceTest {

    private SludgeRecord dispatchedSludge() {
        SludgeRecord r = new SludgeRecord(UUID.randomUUID(), TestSupport.TENANT, UUID.randomUUID(),
                StationUnit.UnitKind.WWTP, SludgeRecord.ResidueKind.SLUDGE, "ORIGEM-FIXTURE",
                new BigDecimal("12"), "t", SludgeRecord.QuantityMethod.WEIGHED,
                Instant.parse("2026-03-01T10:00:00Z"), null);
        r.store("PATIO-FIXTURE", Instant.parse("2026-03-02T10:00:00Z"));
        r.dispatch("TRANSP-FIXTURE", "DEST-FIXTURE", "DOC-FIXTURE", Instant.parse("2026-03-03T10:00:00Z"));
        return r;
    }
    private List<BiosolidService.BiosolidCriterion> criteria() {
        return List.of(
                new BiosolidService.BiosolidCriterion("CRIT-1", "Criterio declarado 1 [FIXTURE]", "CONAMA 498/2020"),
                new BiosolidService.BiosolidCriterion("CRIT-2", "Criterio declarado 2 [FIXTURE]", "CONAMA 498/2020"));
    }

    @Test void eligibleWhenAllDeclaredCriteriaEvaluatedConforming() {
        var lot = BiosolidService.produce(dispatchedSludge(), criteria(), List.of(
                new BiosolidService.CriterionEvaluation("CRIT-1", true, "ENSAIO-FIXTURE-1"),
                new BiosolidService.CriterionEvaluation("CRIT-2", true, "ENSAIO-FIXTURE-2")));
        assertEquals(BiosolidService.BiosolidStatus.ELIGIBLE, lot.status());
        assertEquals(0, new BigDecimal("12").compareTo(lot.quantity()));
    }

    @Test void indeterminateWhenCriterionUnevaluatedAndNotEligibleWhenNonConforming() {
        var ind = BiosolidService.produce(dispatchedSludge(), criteria(), List.of(
                new BiosolidService.CriterionEvaluation("CRIT-1", true, "ENSAIO-FIXTURE-1")));
        assertEquals(BiosolidService.BiosolidStatus.INDETERMINATE, ind.status());
        var not = BiosolidService.produce(dispatchedSludge(), criteria(), List.of(
                new BiosolidService.CriterionEvaluation("CRIT-1", true, "ENSAIO-FIXTURE-1"),
                new BiosolidService.CriterionEvaluation("CRIT-2", false, "ENSAIO-FIXTURE-2")));
        assertEquals(BiosolidService.BiosolidStatus.NOT_ELIGIBLE, not.status());
    }

    @Test void refusesEmptyCriteriaNonSludgeAndUndispatchedSludge() {
        assertThrows(IllegalArgumentException.class, () -> BiosolidService.produce(dispatchedSludge(), List.of(), List.of()));
        SludgeRecord grit = new SludgeRecord(UUID.randomUUID(), TestSupport.TENANT, UUID.randomUUID(),
                StationUnit.UnitKind.WWTP, SludgeRecord.ResidueKind.GRIT, "ORIGEM-FIXTURE",
                new BigDecimal("1"), "t", SludgeRecord.QuantityMethod.WEIGHED,
                Instant.parse("2026-03-01T10:00:00Z"), null);
        grit.store("PATIO", Instant.parse("2026-03-02T10:00:00Z"));
        grit.dispatch("T", "D", "DOC", Instant.parse("2026-03-03T10:00:00Z"));
        assertThrows(IllegalArgumentException.class, () -> BiosolidService.produce(grit, criteria(), List.of()));
        SludgeRecord fresh = new SludgeRecord(UUID.randomUUID(), TestSupport.TENANT, UUID.randomUUID(),
                StationUnit.UnitKind.WWTP, SludgeRecord.ResidueKind.SLUDGE, "ORIGEM-FIXTURE",
                new BigDecimal("1"), "t", SludgeRecord.QuantityMethod.WEIGHED,
                Instant.parse("2026-03-01T10:00:00Z"), null);
        assertThrows(IllegalArgumentException.class, () -> BiosolidService.produce(fresh, criteria(), List.of()));
    }
}
