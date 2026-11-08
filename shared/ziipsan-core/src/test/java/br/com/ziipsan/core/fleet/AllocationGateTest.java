package br.com.ziipsan.core.fleet;

import br.com.ziipsan.core.backoffice.WorkOrderCost;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TESTES DO ACEITE da ED-59: (1) OS que exige equipamento especial nao
 * e programada sem o recurso disponivel; (2) veiculo com documento
 * vencido NAO e alocado (negativo obrigatorio). Mais: checklist
 * pre-viagem, escala sem sobreposicao e custo da OS com a frota
 * preenchendo o campo da ED-36.
 */
class AllocationGateTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 10, 8, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 11, 10, 12, 0);
    private static final Set<FleetRegistry.SpecialEquipment> JET =
            Set.of(FleetRegistry.SpecialEquipment.HIDROJATO);

    private FieldCrewScheduling.AllocationDecision evaluate(FleetRegistry.Vehicle vehicle,
                                                            DriverDocuments.DriverLicense license,
                                                            Set<FleetRegistry.SpecialEquipment> required,
                                                            List<FieldCrewScheduling.CrewAllocation> existing,
                                                            boolean checklistRequired, List<String> items,
                                                            FieldCrewScheduling.PreTripChecklist checklist) {
        return FieldCrewScheduling.evaluate(FleetFixtures.crew(), vehicle, license, required,
                FleetFixtures.SERVICE_DATE, START, END, existing, checklistRequired, items, checklist);
    }

    private FleetRegistry.Vehicle jetVehicle(LocalDate licensing) {
        return FleetFixtures.vehicle(FleetFixtures.ASSET_JET, licensing, JET);
    }

    private DriverDocuments.DriverLicense validLicense() {
        return FleetFixtures.license(FleetFixtures.DRIVER, LocalDate.of(2027, 3, 1));
    }

    @Test
    void orderRequiringSpecialEquipmentIsNotScheduledWithoutTheResource() {
        // veiculo comum, sem hidrojato: a alocacao e recusada nomeando o equipamento
        var plain = FleetFixtures.vehicle(FleetFixtures.ASSET_VAN, LocalDate.of(2027, 1, 1), Set.of());
        var decision = evaluate(plain, validLicense(), JET, List.of(), false, List.of(), null);
        assertFalse(decision.allowed());
        assertTrue(decision.refusalReasons().stream().anyMatch(r -> r.contains("HIDROJATO")));
        // e o FATO para o motor da ED-36 (EQUIPMENT_WHEN_ED59) e falso sem recurso livre
        assertFalse(FieldCrewScheduling.equipmentAvailable(JET, List.of(plain),
                FleetFixtures.SERVICE_DATE, START, END, List.of()));
        assertTrue(FieldCrewScheduling.equipmentAvailable(JET, List.of(jetVehicle(LocalDate.of(2027, 1, 1))),
                FleetFixtures.SERVICE_DATE, START, END, List.of()));
        // sem exigencia de equipamento, a restricao nao se aplica
        assertTrue(FieldCrewScheduling.equipmentAvailable(Set.of(), List.of(),
                FleetFixtures.SERVICE_DATE, START, END, List.of()));
    }

    @Test
    void vehicleWithExpiredDocumentIsNotAllocated() {
        // NEGATIVO OBRIGATORIO DO ACEITE: licenciamento vencido na vespera da data do servico
        var expired = jetVehicle(LocalDate.of(2026, 11, 9));
        var decision = evaluate(expired, validLicense(), JET, List.of(), false, List.of(), null);
        assertFalse(decision.allowed());
        assertTrue(decision.refusalReasons().stream().anyMatch(r -> r.contains("VENCIDO")));
        // o veiculo vencido tambem nao conta como recurso disponivel para o motor da ED-36
        assertFalse(FieldCrewScheduling.equipmentAvailable(JET, List.of(expired),
                FleetFixtures.SERVICE_DATE, START, END, List.of()));
    }

    @Test
    void driverWithExpiredLicenseIsNotAllocated() {
        var decision = evaluate(jetVehicle(LocalDate.of(2027, 1, 1)),
                FleetFixtures.license(FleetFixtures.DRIVER, LocalDate.of(2026, 11, 9)),
                JET, List.of(), false, List.of(), null);
        assertFalse(decision.allowed());
        assertTrue(decision.refusalReasons().stream().anyMatch(r -> r.contains("habilitacao")));
    }

    @Test
    void validAllocationIsAllowed() {
        var decision = evaluate(jetVehicle(LocalDate.of(2027, 1, 1)), validLicense(),
                JET, List.of(), false, List.of(), null);
        assertTrue(decision.allowed());
        assertEquals(List.of(), decision.refusalReasons());
    }

    @Test
    void overlappingAllocationOnScaleIsRefused() {
        var existing = List.of(new FieldCrewScheduling.CrewAllocation(UUID.randomUUID(),
                FleetFixtures.crew().crewId(), FleetFixtures.ASSET_JET, UUID.randomUUID(),
                LocalDateTime.of(2026, 11, 10, 10, 0), LocalDateTime.of(2026, 11, 10, 14, 0)));
        var decision = evaluate(jetVehicle(LocalDate.of(2027, 1, 1)), validLicense(),
                JET, existing, false, List.of(), null);
        assertFalse(decision.allowed());
        // janela encostada (comeca exatamente no fim da existente) nao sobrepoe
        var touching = evaluate(jetVehicle(LocalDate.of(2027, 1, 1)), validLicense(), JET,
                List.of(new FieldCrewScheduling.CrewAllocation(UUID.randomUUID(),
                        UUID.randomUUID(), FleetFixtures.ASSET_JET, UUID.randomUUID(),
                        LocalDateTime.of(2026, 11, 10, 4, 0), START)),
                false, List.of(), null);
        assertTrue(touching.allowed());
    }

    @Test
    void checklistRequiredBlocksDepartureUntilComplete() {
        List<String> items = List.of("FREIOS", "PNEUS");
        var vehicle = jetVehicle(LocalDate.of(2027, 1, 1));
        // sem checklist: bloqueado
        assertFalse(evaluate(vehicle, validLicense(), JET, List.of(), true, items, null).allowed());
        // incompleto: bloqueado
        var partial = new FieldCrewScheduling.PreTripChecklist(UUID.randomUUID(),
                List.of(new FieldCrewScheduling.ChecklistEntry("FREIOS", FieldCrewScheduling.ChecklistItemResult.OK)),
                FleetFixtures.DRIVER, LocalDateTime.of(2026, 11, 10, 7, 30));
        assertFalse(evaluate(vehicle, validLicense(), JET, List.of(), true, items, partial).allowed());
        // item reprovado: bloqueado
        var failed = new FieldCrewScheduling.PreTripChecklist(UUID.randomUUID(),
                List.of(new FieldCrewScheduling.ChecklistEntry("FREIOS", FieldCrewScheduling.ChecklistItemResult.OK),
                        new FieldCrewScheduling.ChecklistEntry("PNEUS", FieldCrewScheduling.ChecklistItemResult.NOT_OK)),
                FleetFixtures.DRIVER, LocalDateTime.of(2026, 11, 10, 7, 30));
        assertFalse(evaluate(vehicle, validLicense(), JET, List.of(), true, items, failed).allowed());
        // completo e OK: liberado
        var complete = new FieldCrewScheduling.PreTripChecklist(UUID.randomUUID(),
                List.of(new FieldCrewScheduling.ChecklistEntry("FREIOS", FieldCrewScheduling.ChecklistItemResult.OK),
                        new FieldCrewScheduling.ChecklistEntry("PNEUS", FieldCrewScheduling.ChecklistItemResult.OK)),
                FleetFixtures.DRIVER, LocalDateTime.of(2026, 11, 10, 7, 30));
        assertTrue(evaluate(vehicle, validLicense(), JET, List.of(), true, items, complete).allowed());
        // exigido sem itens do tenant: falha fechada declarada
        assertFalse(evaluate(vehicle, validLicense(), JET, List.of(), true, List.of(), complete).allowed());
        // nao exigido pelo tenant: checklist nao bloqueia
        assertTrue(evaluate(vehicle, validLicense(), JET, List.of(), false, List.of(), null).allowed());
    }

    @Test
    void orderCostUsesFleetValueInRealEd36WorkOrderCost() {
        // custo por veiculo conhecido: combustivel 760,00 + manutencao 250,00 + multas 350,00 = 1.360,00
        var records = List.of(
                new FuelAndConsumption.FuelRecord(FleetFixtures.ASSET_VAN, 1_000, new BigDecimal("40"),
                        new BigDecimal("6.00"), LocalDateTime.of(2026, 10, 1, 8, 0), null),
                new FuelAndConsumption.FuelRecord(FleetFixtures.ASSET_VAN, 1_500, new BigDecimal("50"),
                        new BigDecimal("6.00"), LocalDateTime.of(2026, 10, 10, 8, 0), null),
                new FuelAndConsumption.FuelRecord(FleetFixtures.ASSET_VAN, 2_000, new BigDecimal("40"),
                        new BigDecimal("5.50"), LocalDateTime.of(2026, 10, 20, 8, 0), null));
        var fines = List.of(new DriverDocuments.TrafficFine(UUID.randomUUID(), FleetFixtures.ASSET_VAN,
                FleetFixtures.DRIVER, new BigDecimal("350.00"), LocalDateTime.of(2026, 10, 5, 9, 0), "autuacao sintetica"));
        FleetCosting.VehicleCostBreakdown perVehicle = FleetCosting.costPerVehicle(
                records, FleetFixtures.ASSET_VAN, new BigDecimal("250.00"), fines);
        assertEquals(new BigDecimal("1360.00"), perVehicle.total());

        // custo de frota da OS (55 km) preenche o campo fleet da classe REAL da ED-36
        BigDecimal fleetForOrder = FuelAndConsumption.fuelCostForOrder(55, new BigDecimal("11"), new BigDecimal("6.00"));
        WorkOrderCost.Breakdown breakdown = WorkOrderCost.total(new BigDecimal("100.00"),
                List.of(new WorkOrderCost.LaborLine("OPERADOR", new BigDecimal("2"), LocalDate.of(2026, 11, 10))),
                List.of(new WorkOrderCost.HourlyRate("OPERADOR", new BigDecimal("50.00"),
                        LocalDate.of(2026, 1, 1), null)),
                fleetForOrder, BigDecimal.ZERO);
        assertEquals(new BigDecimal("30.00"), breakdown.fleet()); // nao e mais nulo: a ED-59 existe
        assertEquals(new BigDecimal("230.00"), breakdown.total()); // 100 material + 100 mao de obra + 30 frota
    }
}
