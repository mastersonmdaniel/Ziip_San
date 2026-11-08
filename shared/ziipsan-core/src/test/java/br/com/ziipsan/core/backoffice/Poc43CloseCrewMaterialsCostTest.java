package br.com.ziipsan.core.backoffice;

import br.com.ziipsan.core.serviceorder.ServiceOrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** POC-43 (encerrar com executores e materiais) + custo pela regra da ED-32 + retrabalho. */
class Poc43CloseCrewMaterialsCostTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 9);
    private static final UUID P1 = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID P2 = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    @Test
    void crewMinAndQualificationValidity() {
        CrewAssignment.Member ok = new CrewAssignment.Member(P1, "ENCANADOR", "NR35", LocalDate.of(2027, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> CrewAssignment.validate(List.of(ok), 2, true, DAY)); // abaixo do minimo
        CrewAssignment.Member expired = new CrewAssignment.Member(P2, "AJUDANTE", "NR35", LocalDate.of(2026, 11, 1));
        assertThrows(IllegalArgumentException.class, () -> CrewAssignment.validate(List.of(ok, expired), 2, true, DAY)); // vencida
        CrewAssignment.Member noQual = new CrewAssignment.Member(P2, "AJUDANTE", null, null);
        assertThrows(IllegalArgumentException.class, () -> CrewAssignment.validate(List.of(ok, noQual), 2, true, DAY)); // exigida e ausente
        assertEquals(2, CrewAssignment.validate(List.of(ok,
                new CrewAssignment.Member(P2, "AJUDANTE", "NR35", LocalDate.of(2026, 12, 31))), 2, true, DAY).size());
        assertThrows(IllegalArgumentException.class, () -> CrewAssignment.validate(List.of(ok, ok), 1, false, DAY)); // repetido
    }

    @Test
    void materialsPlannedVersusUsedAndChargePolicy() {
        PlannedMaterials m = new PlannedMaterials(List.of(
                new PlannedMaterials.Line("CANO", new BigDecimal("10"), null),
                new PlannedMaterials.Line("REGISTRO", new BigDecimal("2"), null)));
        m.recordUsed("CANO", new BigDecimal("12"));
        assertEquals(1, m.overusedLines().size());
        assertEquals("CANO", m.overusedLines().get(0).materialCode());
        assertEquals(1, m.unrecordedLines().size());
        assertThrows(IllegalArgumentException.class, () -> m.recordUsed("VALVULA", new BigDecimal("1"))); // fora do previsto
        assertEquals(new BigDecimal("0.00"), PlannedMaterials.chargeAmount(SchedParameters.MaterialChargePolicy.NEVER, null));
        assertEquals(new BigDecimal("55.10"), PlannedMaterials.chargeAmount(
                SchedParameters.MaterialChargePolicy.PRICE_TABLE_ERI, new BigDecimal("55.10")));
    }

    @Test
    void orderCostByEd32RuleHandComputed() {
        // material 100,00 + mao de obra (2h x 50,00) + (1h x 60,00) = 160,00 + terceiros 0 = 260,00 sem frota (ED-59)
        List<WorkOrderCost.HourlyRate> rates = List.of(
                new WorkOrderCost.HourlyRate("ENCANADOR", new BigDecimal("50.00"), LocalDate.of(2026, 1, 1), null),
                new WorkOrderCost.HourlyRate("AJUDANTE", new BigDecimal("60.00"), LocalDate.of(2026, 1, 1), null));
        List<WorkOrderCost.LaborLine> labor = List.of(
                new WorkOrderCost.LaborLine("ENCANADOR", new BigDecimal("2"), DAY),
                new WorkOrderCost.LaborLine("AJUDANTE", new BigDecimal("1"), DAY));
        WorkOrderCost.Breakdown b = WorkOrderCost.total(new BigDecimal("100.00"), labor, rates, null, BigDecimal.ZERO);
        assertEquals(new BigDecimal("160.00"), b.labor());
        assertNull(b.fleet());
        assertEquals(new BigDecimal("260.00"), b.total());
        WorkOrderCost.Breakdown withFleet = WorkOrderCost.total(new BigDecimal("100.00"), labor, rates,
                new BigDecimal("20.00"), BigDecimal.ZERO);
        assertEquals(new BigDecimal("280.00"), withFleet.total());
    }

    @Test
    void orderCostAmbiguousOrMissingRateRefused() {
        List<WorkOrderCost.HourlyRate> overlapping = List.of(
                new WorkOrderCost.HourlyRate("ENCANADOR", new BigDecimal("50.00"), LocalDate.of(2026, 1, 1), null),
                new WorkOrderCost.HourlyRate("ENCANADOR", new BigDecimal("55.00"), LocalDate.of(2026, 6, 1), null));
        List<WorkOrderCost.LaborLine> labor = List.of(new WorkOrderCost.LaborLine("ENCANADOR", BigDecimal.ONE, DAY));
        assertThrows(IllegalStateException.class, () -> WorkOrderCost.total(BigDecimal.ZERO, labor, overlapping, null, BigDecimal.ZERO));
        assertThrows(IllegalStateException.class, () -> WorkOrderCost.total(BigDecimal.ZERO,
                List.of(new WorkOrderCost.LaborLine("ELETRICISTA", BigDecimal.ONE, DAY)), overlapping, null, BigDecimal.ZERO));
    }

    @Test
    void reworkOnlyFromClosedExecutedWithReason() {
        UUID original = UUID.randomUUID(), rework = UUID.randomUUID();
        assertEquals(original, OrderSplitAndRework.rework(original, ServiceOrderStatus.CLOSED_EXECUTED, rework, "vazamento persistiu").originalOrderId());
        assertThrows(IllegalStateException.class, () -> OrderSplitAndRework.rework(original, ServiceOrderStatus.OPEN, rework, "x"));
        assertThrows(IllegalArgumentException.class, () -> OrderSplitAndRework.rework(original, ServiceOrderStatus.CLOSED_EXECUTED, rework, " "));
    }
}
