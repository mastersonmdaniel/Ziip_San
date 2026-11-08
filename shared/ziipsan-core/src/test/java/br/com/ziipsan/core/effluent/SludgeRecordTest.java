package br.com.ziipsan.core.effluent;

import br.com.ziipsan.core.stationops.StationUnit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SludgeRecordTest {

    @Test
    void generationRequiresQuantityWithMethod() {
        assertThrows(IllegalArgumentException.class, () -> new SludgeRecord(
                UUID.randomUUID(), TestSupport.TENANT, TestSupport.ETE_ID, StationUnit.UnitKind.WWTP,
                SludgeRecord.ResidueKind.SLUDGE, "ORIGEM-A", BigDecimal.ZERO, "m3",
                SludgeRecord.QuantityMethod.WEIGHED, TestSupport.T0, null));
        assertThrows(IllegalArgumentException.class, () -> new SludgeRecord(
                UUID.randomUUID(), TestSupport.TENANT, TestSupport.ETE_ID, StationUnit.UnitKind.WWTP,
                SludgeRecord.ResidueKind.SLUDGE, "ORIGEM-A", new BigDecimal("1"), "m3",
                null, TestSupport.T0, null));
    }

    @Test
    void screeningsAndGritOnlyFromWastewaterPlant() {
        assertThrows(IllegalArgumentException.class, () -> new SludgeRecord(
                UUID.randomUUID(), TestSupport.TENANT, TestSupport.ETA_ID, StationUnit.UnitKind.WTP,
                SludgeRecord.ResidueKind.SCREENINGS, "ORIGEM-A", new BigDecimal("1"), "m3",
                SludgeRecord.QuantityMethod.VOLUME_ESTIMATED, TestSupport.T0, null));
        var grit = new SludgeRecord(UUID.randomUUID(), TestSupport.TENANT, TestSupport.ETE_ID,
                StationUnit.UnitKind.WWTP, SludgeRecord.ResidueKind.GRIT, "ORIGEM-A",
                new BigDecimal("1"), "m3", SludgeRecord.QuantityMethod.VOLUME_ESTIMATED,
                TestSupport.T0, null);
        assertEquals(SludgeRecord.Status.GENERATED, grit.status());
    }

    @Test
    void residueOnlyFromTreatmentPlants() {
        assertThrows(IllegalArgumentException.class, () -> new SludgeRecord(
                UUID.randomUUID(), TestSupport.TENANT, UUID.randomUUID(), StationUnit.UnitKind.RESERVOIR,
                SludgeRecord.ResidueKind.SLUDGE, "ORIGEM-A", new BigDecimal("1"), "m3",
                SludgeRecord.QuantityMethod.WEIGHED, TestSupport.T0, null));
    }

    @Test
    void fullLifecycleGenerationStorageDispatchClose() {
        SludgeRecord r = TestSupport.generatedSludge("ORIGEM-A");
        assertThrows(IllegalStateException.class,
                () -> r.dispatch("TRANSP-1", "DEST-A", "DOC-1", TestSupport.T0.plusSeconds(7200)));
        r.store("PATIO-A", TestSupport.T0.plusSeconds(3600));
        assertThrows(IllegalArgumentException.class,
                () -> r.dispatch("TRANSP-1", "DEST-A", " ", TestSupport.T0.plusSeconds(7200)));
        r.dispatch("TRANSP-1", "DEST-A", "DOC-1", TestSupport.T0.plusSeconds(7200));
        assertEquals(SludgeRecord.Status.DISPATCHED, r.status());
        assertEquals("DEST-A", r.destinationCode());
        r.close(TestSupport.T0.plusSeconds(86400));
        assertEquals(SludgeRecord.Status.CLOSED, r.status());
    }

    @Test
    void weighedSludgeFromWtpIsValidGeneration() {
        var r = new SludgeRecord(UUID.randomUUID(), TestSupport.TENANT, TestSupport.ETA_ID,
                StationUnit.UnitKind.WTP, SludgeRecord.ResidueKind.SLUDGE, "ORIGEM-ETA",
                new BigDecimal("800"), "kg", SludgeRecord.QuantityMethod.WEIGHED, TestSupport.T0, null);
        assertEquals(SludgeRecord.QuantityMethod.WEIGHED, r.quantityMethod());
        assertEquals(StationUnit.UnitKind.WTP, r.unitKind());
    }
}
