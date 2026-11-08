package br.com.ziipsan.core.effluent;

import br.com.ziipsan.core.stationops.DiaryReading;
import br.com.ziipsan.core.stationops.ShiftLog;
import br.com.ziipsan.core.stationops.StationUnit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeptageAndDiaryTest {

    @Test
    void septageReceptionRegistersVolumeAndOrigin() {
        var reception = new SeptageReception(UUID.randomUUID(), TestSupport.TENANT, TestSupport.ETE_ID,
                StationUnit.UnitKind.WWTP,
                new BigDecimal("8"), "m3", "ORIGEM-FOSSA", "Fossa septica de imovel rural [FIXTURE]",
                "TRANSP-FOSSA-1", TestSupport.T0, null);
        assertEquals("8", reception.volume().toPlainString());
        assertEquals("ORIGEM-FOSSA", reception.originCode());
    }

    @Test
    void septageReceptionRefusesMissingOriginAndNonPositiveVolume() {
        assertThrows(IllegalArgumentException.class, () -> new SeptageReception(
                UUID.randomUUID(), TestSupport.TENANT, TestSupport.ETE_ID, StationUnit.UnitKind.WWTP,
                new BigDecimal("8"), "m3",
                "ORIGEM-FOSSA", " ", "TRANSP-1", TestSupport.T0, null));
        assertThrows(IllegalArgumentException.class, () -> new SeptageReception(
                UUID.randomUUID(), TestSupport.TENANT, TestSupport.ETE_ID, StationUnit.UnitKind.WWTP,
                BigDecimal.ZERO, "m3",
                "ORIGEM-FOSSA", "Origem declarada", "TRANSP-1", TestSupport.T0, null));
    }

    @Test
    void septageReceptionRefusesNonWwtpReceivingUnit() {
        // Correcao D4: recepcao de lodo de fossa e "na ETE" — ETA,
        // elevatorias e reservatorios sao recusados, assim como a
        // ausencia do tipo da unidade receptora.
        for (StationUnit.UnitKind kind : List.of(StationUnit.UnitKind.WTP,
                StationUnit.UnitKind.PUMP_STATION, StationUnit.UnitKind.RESERVOIR)) {
            assertThrows(IllegalArgumentException.class, () -> new SeptageReception(
                    UUID.randomUUID(), TestSupport.TENANT, TestSupport.ETE_ID, kind,
                    new BigDecimal("8"), "m3",
                    "ORIGEM-FOSSA", "Origem declarada", "TRANSP-1", TestSupport.T0, null));
        }
        assertThrows(IllegalArgumentException.class, () -> new SeptageReception(
                UUID.randomUUID(), TestSupport.TENANT, TestSupport.ETE_ID, null,
                new BigDecimal("8"), "m3",
                "ORIGEM-FOSSA", "Origem declarada", "TRANSP-1", TestSupport.T0, null));
    }

    @Test
    void diaryFactsReadFlowsAndOutagesFromRealEd87Diary() {
        ShiftLog log = new ShiftLog(UUID.randomUUID(), TestSupport.ete(), LocalDate.of(2026, 3, 10),
                "DIA", "operador-1", TestSupport.T0);
        log.setEquipment(new ShiftLog.EquipmentState("BOMBA-1",
                ShiftLog.EquipmentSituation.OPERATING, new BigDecimal("120"), 3));
        log.setEquipment(new ShiftLog.EquipmentState("SOPRADOR-1",
                ShiftLog.EquipmentSituation.OUT_OF_SERVICE, null, null));
        log.addReading(new DiaryReading(DiaryReading.Kind.FLOW, null,
                new BigDecimal("42.5"), "L/s", TestSupport.T0.plusSeconds(3600)));
        log.addReading(new DiaryReading(DiaryReading.Kind.LEVEL, null,
                new BigDecimal("3.1"), "m", TestSupport.T0.plusSeconds(3600)));
        log.recordOccurrence("Parada para manutencao [FIXTURE]", TestSupport.T0.plusSeconds(5400),
                "operador-1");

        DiaryEffluentFacts.Facts facts = DiaryEffluentFacts.extract(TestSupport.ETE_ID, List.of(log));
        assertEquals(1, facts.shiftLogCount());
        assertEquals(List.of(new BigDecimal("42.5")), facts.flowReadings());
        assertEquals(List.of("SOPRADOR-1"), facts.outOfServiceEquipment());
        assertEquals(1, facts.occurrenceCount());
    }

    @Test
    void diaryFactsIgnoreOtherUnitsAndEmptyIsDeclared() {
        ShiftLog etaLog = new ShiftLog(UUID.randomUUID(), TestSupport.eta(), LocalDate.of(2026, 3, 10),
                "DIA", "operador-1", TestSupport.T0);
        etaLog.addReading(new DiaryReading(DiaryReading.Kind.FLOW, null,
                new BigDecimal("99"), "L/s", TestSupport.T0.plusSeconds(3600)));
        DiaryEffluentFacts.Facts facts = DiaryEffluentFacts.extract(TestSupport.ETE_ID, List.of(etaLog));
        assertEquals(0, facts.shiftLogCount());
        assertTrue(facts.flowReadings().isEmpty());
        assertEquals(0, DiaryEffluentFacts.extract(TestSupport.ETE_ID, List.of()).shiftLogCount());
    }
}
