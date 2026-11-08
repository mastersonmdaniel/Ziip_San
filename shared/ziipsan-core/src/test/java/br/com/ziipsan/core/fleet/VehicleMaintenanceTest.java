package br.com.ziipsan.core.fleet;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Manutencao de veiculos como extensao da rotina da ED-56 (OPE-20). */
class VehicleMaintenanceTest {

    private static VehicleMaintenance.WorkPlan plan() {
        return new VehicleMaintenance.WorkPlan(List.of("trocar oleo e filtros"),
                List.of("oleo", "filtro"), List.of("elevador"), "plano de seguranca da oficina");
    }

    private static VehicleMaintenance.VehiclePmLine timeLine() {
        return new VehicleMaintenance.VehiclePmLine(FleetFixtures.ASSET_VAN,
                VehicleMaintenance.TriggerKind.TIME, 6, null, plan(), null);
    }

    @Test
    void triggerAndIntervalAreBoundLikeEd56() {
        assertThrows(IllegalArgumentException.class, () -> new VehicleMaintenance.VehiclePmLine(
                FleetFixtures.ASSET_VAN, VehicleMaintenance.TriggerKind.TIME, null, 10_000L, plan(), null));
        assertThrows(IllegalArgumentException.class, () -> new VehicleMaintenance.VehiclePmLine(
                FleetFixtures.ASSET_VAN, VehicleMaintenance.TriggerKind.ODOMETER_KM, 6, null, plan(), null));
        assertThrows(IllegalArgumentException.class, () -> new VehicleMaintenance.VehiclePmLine(
                FleetFixtures.ASSET_VAN, VehicleMaintenance.TriggerKind.TIME, 6, 10_000L, plan(), null));
    }

    @Test
    void workPlanRequiresSafetyPlanLikeEd56() {
        assertThrows(IllegalArgumentException.class, () ->
                new VehicleMaintenance.WorkPlan(List.of("tarefa"), List.of(), List.of(), " "));
    }

    @Test
    void statusByTimeUsesEd56LeadAndToleranceSemantics() {
        var line = timeLine(); // 6 meses; ultima PM 2026-01-15 -> vence 2026-07-15
        var runtime = new VehicleMaintenance.VehicleRuntime(FleetFixtures.ASSET_VAN, LocalDate.of(2026, 1, 15), 0);
        LocalDate inOp = LocalDate.of(2025, 1, 1);
        assertEquals(VehicleMaintenance.PmStatus.NOT_DUE, VehicleMaintenance.statusAt(
                line, runtime, inOp, LocalDate.of(2026, 6, 1), 15, 5));
        assertEquals(VehicleMaintenance.PmStatus.DUE_FOR_GENERATION, VehicleMaintenance.statusAt(
                line, runtime, inOp, LocalDate.of(2026, 7, 10), 15, 5)); // dentro do lead de 15 dias
        assertEquals(VehicleMaintenance.PmStatus.OVERDUE, VehicleMaintenance.statusAt(
                line, runtime, inOp, LocalDate.of(2026, 7, 21), 15, 5)); // apos vencimento + tolerancia 5
        // sem parametros do tenant (lead/tolerancia zero declarados, como na ED-56):
        assertEquals(VehicleMaintenance.PmStatus.NOT_DUE, VehicleMaintenance.statusAt(
                line, runtime, inOp, LocalDate.of(2026, 7, 10), 0, 0));
    }

    @Test
    void statusByOdometerKm() {
        var line = new VehicleMaintenance.VehiclePmLine(FleetFixtures.ASSET_VAN,
                VehicleMaintenance.TriggerKind.ODOMETER_KM, null, 10_000L, plan(), null);
        assertEquals(VehicleMaintenance.PmStatus.DUE_FOR_GENERATION, VehicleMaintenance.statusAt(
                line, new VehicleMaintenance.VehicleRuntime(FleetFixtures.ASSET_VAN, null, 10_000),
                null, LocalDate.of(2026, 11, 10), 0, 0));
        assertEquals(VehicleMaintenance.PmStatus.NOT_DUE, VehicleMaintenance.statusAt(
                line, new VehicleMaintenance.VehicleRuntime(FleetFixtures.ASSET_VAN, null, 9_999),
                null, LocalDate.of(2026, 11, 10), 0, 0));
    }

    @Test
    void generationAndCorrectiveGoThroughTheSameWorkOrderPort() {
        List<FleetPorts.VehicleOrderRequest> created = new ArrayList<>();
        FleetPorts.WorkOrderPort port = req -> {
            created.add(req);
            return new FleetPorts.CreatedOrder(UUID.randomUUID());
        };
        var runtime = new VehicleMaintenance.VehicleRuntime(FleetFixtures.ASSET_VAN, LocalDate.of(2026, 1, 15), 0);
        Optional<FleetPorts.CreatedOrder> generated = VehicleMaintenance.generatePreventive(
                port, FleetFixtures.TENANT, timeLine(), runtime, LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 7, 10), 15, 5);
        assertTrue(generated.isPresent());
        assertEquals(FleetPorts.VehicleOrderPurpose.VEHICLE_PREVENTIVE, created.get(0).purpose());
        // nao devida: nenhuma OS
        created.clear();
        assertTrue(VehicleMaintenance.generatePreventive(port, FleetFixtures.TENANT, timeLine(), runtime,
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 6, 1), 15, 5).isEmpty());
        assertEquals(0, created.size());
        // corretiva: mesma porta
        VehicleMaintenance.openCorrective(port, FleetFixtures.TENANT, FleetFixtures.ASSET_VAN, "freio com ruido");
        assertEquals(FleetPorts.VehicleOrderPurpose.VEHICLE_CORRECTIVE, created.get(0).purpose());
        assertThrows(IllegalArgumentException.class, () ->
                VehicleMaintenance.openCorrective(port, FleetFixtures.TENANT, FleetFixtures.ASSET_VAN, " "));
    }
}
