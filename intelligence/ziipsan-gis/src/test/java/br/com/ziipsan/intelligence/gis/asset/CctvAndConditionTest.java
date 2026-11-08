package br.com.ziipsan.intelligence.gis.asset;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.ziipsan.intelligence.gis.asset.CctvInspectionService.DefectObservation;
import br.com.ziipsan.intelligence.gis.asset.CctvInspectionService.SegmentInspection;
import br.com.ziipsan.intelligence.gis.asset.ConditionMaintenance.ConditionReading;
import br.com.ziipsan.intelligence.gis.asset.ConditionMaintenance.ConditionTrigger;
import br.com.ziipsan.intelligence.gis.asset.ConditionMaintenance.CriticalSparePolicy;
import br.com.ziipsan.intelligence.gis.asset.ConditionMaintenance.Measurement;
import br.com.ziipsan.intelligence.gis.asset.ConditionMaintenance.SpareStatus;

import static br.com.ziipsan.intelligence.gis.asset.AssetFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class CctvAndConditionTest {

    @Test
    void standardMustBeConfirmedBeforeAnyImport() {
        var ex = assertThrows(IllegalStateException.class,
                () -> CctvInspectionService.requireStandard(null));
        assertTrue(ex.getMessage().contains("[A DEFINIR]"));
        assertThrows(IllegalStateException.class, () -> CctvInspectionService.requireStandard("OUTRO"));
        assertEquals(CctvInspectionService.CctvStandard.PACP, CctvInspectionService.requireStandard("PACP"));
        assertEquals(CctvInspectionService.CctvStandard.WRC, CctvInspectionService.requireStandard("WRC"));
    }

    @Test
    void pacpTransferParsesAndGradesSegment() {
        String file = "# transferencia sintetica\nT-01;0.0;START;1\nT-01;12.5;CRACK;2\nT-01;30.0;ROOTS;4\n";
        var defects = CctvInspectionService.parsePacpTransfer("T-01", file, "PACP");
        assertEquals(3, defects.size());
        var seg = new SegmentInspection(id(90), "INSP-1", defects.subList(1, 3));
        assertEquals(4, seg.segmentGrade());
        // regra declarada: pior x 20 + media x 2 = 80 + 6 = 86
        assertEquals(86.0, seg.segmentScore());
        assertEquals(0, new SegmentInspection(id(90), "INSP-2", List.of()).segmentGrade());
        assertEquals(0.0, new SegmentInspection(id(90), "INSP-2", List.of()).segmentScore());
    }

    @Test
    void pacpMalformedOrForeignLinesAreRefusedByLine() {
        assertThrows(IllegalArgumentException.class,
                () -> CctvInspectionService.parsePacpTransfer("T-01", "T-01;abc", "PACP"));
        assertThrows(IllegalArgumentException.class,
                () -> CctvInspectionService.parsePacpTransfer("T-01", "T-99;1.0;CRACK;2", "PACP"));
        assertThrows(IllegalArgumentException.class,
                () -> CctvInspectionService.parsePacpTransfer("T-01", "T-01;1.0;CRACK;x", "PACP"));
        assertThrows(IllegalArgumentException.class, () -> new DefectObservation("CRACK", 6, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new DefectObservation("CRACK", 0, 1.0));
    }

    @Test
    void reincidenceUsesTenantThresholdOnly() {
        assertTrue(CctvInspectionService.isReincident(3, 3));
        assertFalse(CctvInspectionService.isReincident(2, 3));
        assertThrows(IllegalArgumentException.class, () -> CctvInspectionService.isReincident(9, 0));
    }

    @Test
    void conditionTriggersFireOnlyWhenConfigured() {
        var trigger = new ConditionTrigger(BOMBA, Measurement.VIBRATION,
                ConditionMaintenance.Comparison.ABOVE, 7.5, "mm/s");
        var fired = ConditionMaintenance.evaluate(List.of(trigger),
                List.of(new ConditionReading(BOMBA, Measurement.VIBRATION, 8.1, "mm/s")));
        assertEquals(List.of(trigger), fired);
        assertEquals(List.of(), ConditionMaintenance.evaluate(List.of(trigger),
                List.of(new ConditionReading(BOMBA, Measurement.VIBRATION, 7.5, "mm/s"))));
        // sem gatilho configurado, leitura nenhuma dispara
        assertEquals(List.of(), ConditionMaintenance.evaluate(List.of(),
                List.of(new ConditionReading(BOMBA, Measurement.VIBRATION, 99, "mm/s"))));
    }

    @Test
    void unitMismatchIsRefusedNeverConverted() {
        var trigger = new ConditionTrigger(BOMBA, Measurement.TEMPERATURE,
                ConditionMaintenance.Comparison.ABOVE, 80, "C");
        assertThrows(IllegalArgumentException.class, () -> ConditionMaintenance.evaluate(
                List.of(trigger), List.of(new ConditionReading(BOMBA, Measurement.TEMPERATURE, 176, "F"))));
    }

    @Test
    void conditionOrdersAreGeneratedPerFiredTrigger() {
        java.util.List<AssetPorts.OrderRequest> requests = new java.util.ArrayList<>();
        AssetPorts.WorkOrderPort port = req -> {
            requests.add(req);
            return new AssetPorts.CreatedOrder(UUID.randomUUID(), req.targetAssetId());
        };
        var trigger = new ConditionTrigger(BOMBA, Measurement.RUNNING_HOURS,
                ConditionMaintenance.Comparison.ABOVE, 8000, "h");
        var created = ConditionMaintenance.generateConditionOrders(port, UUID.randomUUID(),
                List.of(trigger), Map.of(BOMBA, "R1"), java.time.LocalDate.of(2026, 8, 3), UUID.randomUUID());
        assertEquals(1, created.size());
        assertEquals(AssetPorts.OrderPurpose.CONDITION_CHECK, requests.get(0).purpose());
    }

    @Test
    void criticalSparePolicyStatuses() {
        var policy = new CriticalSparePolicy(BOMBA, "SELO-MEC-01", 2, 4);
        assertEquals(SpareStatus.BELOW_MINIMUM, ConditionMaintenance.spareStatus(policy, 1));
        assertEquals(SpareStatus.AT_REORDER_POINT, ConditionMaintenance.spareStatus(policy, 2));
        assertEquals(SpareStatus.AT_REORDER_POINT, ConditionMaintenance.spareStatus(policy, 4));
        assertEquals(SpareStatus.OK, ConditionMaintenance.spareStatus(policy, 5));
        assertThrows(IllegalArgumentException.class, () -> new CriticalSparePolicy(BOMBA, "X", 4, 2));
    }
}
