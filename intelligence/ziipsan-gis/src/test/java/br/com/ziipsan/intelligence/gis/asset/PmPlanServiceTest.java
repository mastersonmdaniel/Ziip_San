package br.com.ziipsan.intelligence.gis.asset;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.ziipsan.intelligence.gis.asset.PmPlanService.AssetRuntime;
import br.com.ziipsan.intelligence.gis.asset.PmPlanService.PmPlanLine;
import br.com.ziipsan.intelligence.gis.asset.PmPlanService.PmStatus;
import br.com.ziipsan.intelligence.gis.asset.PmPlanService.TriggerKind;
import br.com.ziipsan.intelligence.gis.asset.PmPlanService.WorkPlan;

import static br.com.ziipsan.intelligence.gis.asset.AssetFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class PmPlanServiceTest {

    private static WorkPlan workPlan() {
        return new WorkPlan(List.of("Inspecionar selo", "Medir vibracao"),
                List.of("Selo mecanico"), List.of("Analisador de vibracao"),
                "Bloqueio e etiquetagem do painel antes da abertura");
    }

    private static PmPlanLine timeLine() {
        return new PmPlanLine(BOMBA, "PUMP", TriggerKind.TIME, 6, null, null, workPlan(), null);
    }

    @Test
    void workPlanRequiresSafetyPlanAndExactlyOneInterval() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorkPlan(List.of("Tarefa"), List.of(), List.of(), " "));
        assertThrows(IllegalArgumentException.class,
                () -> new PmPlanLine(BOMBA, "PUMP", TriggerKind.TIME, 6, 100.0, null, workPlan(), null));
        assertThrows(IllegalArgumentException.class,
                () -> new PmPlanLine(BOMBA, "PUMP", TriggerKind.TIME, null, null, null, workPlan(), null));
    }

    @Test
    void timeTriggerDueDateFromLastPmOrCommissioning() {
        var runtime = new AssetRuntime(BOMBA, LocalDate.of(2026, 1, 10), 0, 0);
        assertEquals(LocalDate.of(2026, 7, 10),
                PmPlanService.dueDateByTime(timeLine(), runtime, null).orElseThrow());
        var neverDone = new AssetRuntime(BOMBA, null, 0, 0);
        assertEquals(LocalDate.of(2026, 7, 1),
                PmPlanService.dueDateByTime(timeLine(), neverDone, LocalDate.of(2026, 1, 1)).orElseThrow());
        assertTrue(PmPlanService.dueDateByTime(timeLine(), neverDone, null).isEmpty());
    }

    @Test
    void usageTriggersFireAtInterval() {
        var hours = new PmPlanLine(BOMBA, "PUMP", TriggerKind.RUNNING_HOURS, null, 2000.0, null, workPlan(), null);
        assertFalse(PmPlanService.dueByUsage(hours, new AssetRuntime(BOMBA, null, 1999, 0)));
        assertTrue(PmPlanService.dueByUsage(hours, new AssetRuntime(BOMBA, null, 2000, 0)));
        var volume = new PmPlanLine(BOMBA, "PUMP", TriggerKind.PUMPED_VOLUME, null, null, 50000.0, workPlan(), null);
        assertTrue(PmPlanService.dueByUsage(volume, new AssetRuntime(BOMBA, null, 0, 50000)));
    }

    @Test
    void statusRespectsLeadAndToleranceFromTenantParameters() {
        var runtime = new AssetRuntime(BOMBA, LocalDate.of(2026, 1, 10), 0, 0); // vence 2026-07-10
        // sem parametro (lead 0, tolerancia 0): so no vencimento, atraso no dia seguinte
        assertEquals(PmStatus.NOT_DUE, PmPlanService.statusAt(timeLine(), runtime, null,
                LocalDate.of(2026, 7, 9), 0, 0));
        assertEquals(PmStatus.DUE_FOR_GENERATION, PmPlanService.statusAt(timeLine(), runtime, null,
                LocalDate.of(2026, 7, 10), 0, 0));
        assertEquals(PmStatus.OVERDUE, PmPlanService.statusAt(timeLine(), runtime, null,
                LocalDate.of(2026, 7, 11), 0, 0));
        // com lead 15 e tolerancia 5 do tenant
        assertEquals(PmStatus.DUE_FOR_GENERATION, PmPlanService.statusAt(timeLine(), runtime, null,
                LocalDate.of(2026, 6, 25), 15, 5));
        assertEquals(PmStatus.DUE_FOR_GENERATION, PmPlanService.statusAt(timeLine(), runtime, null,
                LocalDate.of(2026, 7, 15), 15, 5));
    }

    @Test
    void generationCreatesOrdersOnlyForDueLines() {
        java.util.List<AssetPorts.OrderRequest> requests = new java.util.ArrayList<>();
        AssetPorts.WorkOrderPort port = req -> {
            requests.add(req);
            return new AssetPorts.CreatedOrder(UUID.randomUUID(), req.targetAssetId());
        };
        var runtime = Map.of(BOMBA, new AssetRuntime(BOMBA, LocalDate.of(2026, 1, 10), 0, 0));
        var created = PmPlanService.generateDueOrders(port, UUID.randomUUID(),
                List.of(timeLine()), runtime, Map.of(), Map.of(BOMBA, "R1"),
                LocalDate.of(2026, 7, 10), 0, 0, UUID.randomUUID());
        assertEquals(1, created.size());
        assertEquals(AssetPorts.OrderPurpose.PREVENTIVE, requests.get(0).purpose());
        assertEquals("EQUIPMENT", requests.get(0).targetType());
        var none = PmPlanService.generateDueOrders(port, UUID.randomUUID(),
                List.of(timeLine()), runtime, Map.of(), Map.of(BOMBA, "R1"),
                LocalDate.of(2026, 3, 1), 0, 0, UUID.randomUUID());
        assertEquals(0, none.size());
    }

    @Test
    void routesGroupByRegionAndChecklistComesOnlyFromParameter() {
        var other = new PmPlanLine(MOTOR, "MOTOR", TriggerKind.RUNNING_HOURS, null, 100.0, null, workPlan(), null);
        var routes = PmPlanService.groupIntoRoutes(List.of(timeLine(), other),
                Map.of(BOMBA, "R1", MOTOR, "R1"), LocalDate.of(2026, 7, 10));
        assertEquals(1, routes.size());
        assertEquals(List.of(BOMBA, MOTOR), routes.get(0).assetIds());
        // sem modelo do tenant: checklist vazio, nunca inventado
        assertEquals(List.of(), PmPlanService.checklistFor("PUMP", Map.of()));
        assertEquals(List.of("Verificar nivel de oleo"),
                PmPlanService.checklistFor("PUMP", Map.of("PUMP", List.of("Verificar nivel de oleo"))));
    }

    @Test
    void indicatorsMtbfMttrAndNullDenominators() {
        var ind = PmPlanService.indicators(7, 2, 1000, 4, 30, 6);
        assertEquals(250.0, ind.mtbfHours());
        assertEquals(5.0, ind.mttrHours());
        assertEquals(7, ind.backlogOrders());
        var noBase = PmPlanService.indicators(0, 0, 100, 0, 10, 0);
        assertNull(noBase.mtbfHours());
        assertNull(noBase.mttrHours());
    }
}
