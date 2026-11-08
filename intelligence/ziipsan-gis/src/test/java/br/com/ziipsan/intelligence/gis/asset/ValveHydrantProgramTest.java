package br.com.ziipsan.intelligence.gis.asset;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.ziipsan.intelligence.gis.asset.ValveHydrantProgram.HydrantTestResult;
import br.com.ziipsan.intelligence.gis.asset.ValveHydrantProgram.ValveExerciseResult;

import static br.com.ziipsan.intelligence.gis.asset.AssetFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class ValveHydrantProgramTest {

    @Test
    void awwaReferenceIsFactoryOnlyWhereAcceptanceFixesIt() {
        assertEquals(1, ValveHydrantProgram.exerciseYearsFor("CRITICAL", Map.of()));
        assertEquals(5, ValveHydrantProgram.exerciseYearsFor("STANDARD", Map.of()));
        assertEquals(5, ValveHydrantProgram.exerciseYearsFor(null, null));
        // tenant pode detalhar por classe
        assertEquals(2, ValveHydrantProgram.exerciseYearsFor("STANDARD", Map.of("STANDARD", 2)));
        assertEquals(1, ValveHydrantProgram.exerciseYearsFor("CRITICAL", Map.of("STANDARD", 2)));
    }

    @Test
    void exerciseDueByClassCalendar() {
        var last = LocalDate.of(2021, 6, 1);
        assertTrue(ValveHydrantProgram.exerciseDue(last, null, "CRITICAL", Map.of(), LocalDate.of(2022, 6, 1)));
        assertFalse(ValveHydrantProgram.exerciseDue(last, null, "STANDARD", Map.of(), LocalDate.of(2026, 5, 31)));
        assertTrue(ValveHydrantProgram.exerciseDue(last, null, "STANDARD", Map.of(), LocalDate.of(2026, 6, 1)));
        assertTrue(ValveHydrantProgram.nextExerciseDue(null, null, "CRITICAL", Map.of()).isEmpty());
    }

    @Test
    void hydrantHasNoCalendarDueWithoutTenantParameter() {
        assertTrue(ValveHydrantProgram.nextHydrantTestDue(LocalDate.of(2026, 1, 1), null).isEmpty());
        assertEquals(LocalDate.of(2026, 7, 1),
                ValveHydrantProgram.nextHydrantTestDue(LocalDate.of(2026, 1, 1), 6).orElseThrow());
    }

    @Test
    void exerciseResultFeedsOperabilityAndReplacementQueue() {
        java.util.List<String> writes = new java.util.ArrayList<>();
        AssetPorts.ValveOperabilityPort port = (tenant, asset, operable, left, at, wo) ->
                writes.add(asset + ":" + operable + ":" + left);
        var ok = new ValveExerciseResult(VALVULA, LocalDateTime.of(2026, 8, 1, 9, 0),
                12.5, "OPEN", "OPEN", true, UUID.randomUUID());
        var stuck = new ValveExerciseResult(id(77), LocalDateTime.of(2026, 8, 1, 10, 0),
                3.0, "PARTIAL", "PARTIAL", false, UUID.randomUUID());
        assertFalse(ValveHydrantProgram.applyExerciseResult(port, UUID.randomUUID(), ok));
        assertTrue(ValveHydrantProgram.applyExerciseResult(port, UUID.randomUUID(), stuck));
        assertEquals(List.of(VALVULA + ":true:OPEN", id(77) + ":false:PARTIAL"), writes);
        assertEquals(List.of(id(77)), ValveHydrantProgram.replacementQueue(List.of(ok, stuck)));
    }

    @Test
    void hydrantTestRecordsFlowPressureAndDateWithUnits() {
        var t = new HydrantTestResult(HIDRANTE, LocalDate.of(2026, 8, 2), 42.0, "L/s", 35.0, "mca",
                UUID.randomUUID());
        assertEquals(LocalDate.of(2026, 8, 2), t.testedOn());
        assertThrows(IllegalArgumentException.class, () -> new HydrantTestResult(
                HIDRANTE, LocalDate.of(2026, 8, 2), 42.0, null, 35.0, "mca", UUID.randomUUID()));
    }

    @Test
    void inoperablePercentAndEmptyBase() {
        assertEquals(25.0, ValveHydrantProgram.inoperablePercent(8, 2));
        assertNull(ValveHydrantProgram.inoperablePercent(0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ValveHydrantProgram.inoperablePercent(2, 3));
    }

    @Test
    void groupedOrdersCarryRegionAndPurpose() {
        java.util.List<AssetPorts.OrderRequest> requests = new java.util.ArrayList<>();
        AssetPorts.WorkOrderPort port = req -> {
            requests.add(req);
            return new AssetPorts.CreatedOrder(UUID.randomUUID(), req.targetAssetId());
        };
        var created = ValveHydrantProgram.generateGroupedOrders(port, UUID.randomUUID(),
                AssetPorts.OrderPurpose.VALVE_EXERCISE, "EQUIPMENT",
                List.of(VALVULA), Map.of(VALVULA, "R1"), LocalDate.of(2026, 8, 3), UUID.randomUUID());
        assertEquals(1, created.size());
        assertEquals("R1", requests.get(0).regionCode());
    }
}
