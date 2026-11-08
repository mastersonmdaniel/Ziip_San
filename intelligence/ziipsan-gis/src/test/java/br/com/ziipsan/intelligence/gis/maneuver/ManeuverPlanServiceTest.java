package br.com.ziipsan.intelligence.gis.maneuver;

import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.NetworkSnapshot;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.Target;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.ValvePosition;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverPlanService.Execution;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverPlanService.ManeuverPlan;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverPlanService.PlanStatus;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverPlanService.PropertyOutagePeriod;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverPorts.BatchRequest;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverPorts.BatchResult;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverPorts.CreatedOrder;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverPorts.NoticeReceipt;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverPorts.NoticeRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static br.com.ziipsan.intelligence.gis.maneuver.ManeuverFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plano de manobra: aviso SÓ pela ED-41, OS em lote SÓ pela ED-36,
 * baixa atualizando a válvula e o período por imóvel. Fakes das
 * portas — nenhum envio e nenhuma OS real nascem no teste.
 */
class ManeuverPlanServiceTest {

    static final class Fakes {
        final List<NoticeRequest> notices = new ArrayList<>();
        final List<BatchRequest> batches = new ArrayList<>();
        final List<String> valveWrites = new ArrayList<>();
        int seq = 0;

        ManeuverPlanService service() {
            return new ManeuverPlanService(
                    req -> { notices.add(req); return new NoticeReceipt("disp-1"); },
                    req -> {
                        batches.add(req);
                        UUID batchId = id(700 + (++seq));
                        List<CreatedOrder> orders = req.items().stream()
                                .map(i -> new CreatedOrder(id(800 + (++seq)), i.valveNodeId())).toList();
                        return new BatchResult(batchId, orders);
                    },
                    (tenant, valve, pos, os, at) -> valveWrites.add(valve + ":" + pos + "@" + at));
        }
    }

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 10, 8, 0);
    private static final LocalDateTime RETURN = LocalDateTime.of(2026, 11, 10, 18, 0);

    private Execution plannedExecution(Fakes f) {
        ManeuverPlan plan = f.service().plan(id(600), TENANT, base(), Target.arc(A5),
                START, RETURN, "troca de trecho", ACTOR);
        Execution ex = f.service().start(plan);
        ex.dispatchNotice();
        ex.createOrders();
        return ex;
    }

    @Test
    void noticeGoesOnlyThroughEd41PortWithSensitiveFirstOrder() {
        Fakes f = new Fakes();
        Execution ex = plannedExecution(f);
        assertEquals(1, f.notices.size());
        NoticeRequest n = f.notices.get(0);
        assertEquals(List.of(U2, U3, U4, U1), n.consumerUnitIdsInNoticeOrder());
        assertEquals(List.of(U4), n.essentialUnitIds());
        assertEquals(ManeuverPlanService.NOTICE_EVENT_CODE, n.eventCode());
    }

    @Test
    void batchesGoOnlyThroughEd36PortAndReopenDependsOnClose() {
        Fakes f = new Fakes();
        Execution ex = plannedExecution(f);
        assertEquals(2, f.batches.size());
        assertEquals(ManeuverPorts.BatchPurpose.VALVE_CLOSE, f.batches.get(0).purpose());
        assertEquals(List.of(VA, VB), f.batches.get(0).items().stream().map(i -> i.valveNodeId()).toList());
        assertEquals(ManeuverPorts.BatchPurpose.VALVE_REOPEN, f.batches.get(1).purpose());
        assertEquals(ex.closeBatch().batchId(), f.batches.get(1).dependsOnBatchId());
        // A válvula inoperável atravessada NÃO ganha OS.
        assertTrue(f.batches.stream().flatMap(b -> b.items().stream())
                .noneMatch(i -> i.valveNodeId().equals(VX)));
    }

    @Test
    void completionUpdatesValvesAndPropertyOutagePeriods() {
        Fakes f = new Fakes();
        Execution ex = plannedExecution(f);
        var closeOrders = ex.closeBatch().orders();
        var reopenOrders = ex.reopenBatch().orders();

        ex.completeOrder(closeOrders.get(0).workOrderId(), LocalDateTime.of(2026, 11, 10, 10, 0));
        assertEquals(PlanStatus.ORDERS_CREATED, ex.status());
        assertTrue(ex.outagePeriods().isEmpty());
        ex.completeOrder(closeOrders.get(1).workOrderId(), LocalDateTime.of(2026, 11, 10, 11, 0));
        assertEquals(PlanStatus.ISOLATED, ex.status());
        assertEquals(LocalDateTime.of(2026, 11, 10, 11, 0), ex.isolationEffectiveAt());
        assertEquals(3, ex.outagePeriods().size());
        assertTrue(ex.outagePeriods().stream().allMatch(p -> p.endedAt() == null));

        ex.completeOrder(reopenOrders.get(0).workOrderId(), LocalDateTime.of(2026, 11, 10, 15, 0));
        ex.completeOrder(reopenOrders.get(1).workOrderId(), LocalDateTime.of(2026, 11, 10, 16, 0));
        assertEquals(PlanStatus.COMPLETED, ex.status());
        for (PropertyOutagePeriod p : ex.outagePeriods()) {
            assertEquals(Duration.ofHours(5), p.duration());
        }
        // Estado das válvulas escrito a cada baixa, na ordem das baixas.
        assertEquals(List.of(
                VA + ":CLOSED@2026-11-10T10:00",
                VB + ":CLOSED@2026-11-10T11:00",
                VA + ":OPEN@2026-11-10T15:00",
                VB + ":OPEN@2026-11-10T16:00"), f.valveWrites);
    }

    /**
     * Sonda D5 da revisão (MENOR): o imóvel id(501) tem DUAS conexões
     * funcionais afetadas (C1 em N2 com U1 e C7 em N3 com U6) — o
     * período de desabastecimento é UM por imóvel, com as unidades
     * fundidas, e não um período por conexão.
     */
    @Test
    void outagePeriodsAreGroupedByProperty() {
        Fakes f = new Fakes();
        NetworkSnapshot snap = withPropertySharingConnections();
        ManeuverPlan plan = f.service().plan(id(602), TENANT, snap, Target.arc(A5),
                START, RETURN, "troca de trecho", ACTOR);
        Execution ex = f.service().start(plan);
        ex.dispatchNotice();
        ex.createOrders();
        var closeOrders = ex.closeBatch().orders();
        ex.completeOrder(closeOrders.get(0).workOrderId(), LocalDateTime.of(2026, 11, 10, 10, 0));
        ex.completeOrder(closeOrders.get(1).workOrderId(), LocalDateTime.of(2026, 11, 10, 11, 0));
        assertEquals(PlanStatus.ISOLATED, ex.status());
        // 4 conexões afetadas (C1, C2, C3, C7) em 3 imóveis: 3 períodos.
        assertEquals(3, ex.outagePeriods().size());
        PropertyOutagePeriod shared = ex.outagePeriods().stream()
                .filter(p -> p.propertyId().equals(id(501))).findFirst().orElseThrow();
        // Ordem do aviso: C1 (regular, codigo menor) antes de C7.
        assertEquals(List.of(U1, id(206)), shared.consumerUnitIds());
        assertEquals(LocalDateTime.of(2026, 11, 10, 11, 0), shared.startedAt());
    }

    @Test
    void reopenBeforeIsolationAndDuplicateCompletionAreRefused() {
        Fakes f = new Fakes();
        Execution ex = plannedExecution(f);
        UUID reopenOrder = ex.reopenBatch().orders().get(0).workOrderId();
        assertThrows(IllegalStateException.class,
                () -> ex.completeOrder(reopenOrder, LocalDateTime.of(2026, 11, 10, 9, 0)));
        UUID closeOrder = ex.closeBatch().orders().get(0).workOrderId();
        ex.completeOrder(closeOrder, LocalDateTime.of(2026, 11, 10, 10, 0));
        assertThrows(IllegalStateException.class,
                () -> ex.completeOrder(closeOrder, LocalDateTime.of(2026, 11, 10, 10, 30)));
        assertThrows(IllegalArgumentException.class,
                () -> ex.completeOrder(id(999), LocalDateTime.of(2026, 11, 10, 10, 30)));
    }

    @Test
    void ordersRequireNoticeFirstAndUnisolableTargetHasNoPlan() {
        Fakes f = new Fakes();
        ManeuverPlan plan = f.service().plan(id(600), TENANT, base(), Target.arc(A5),
                START, RETURN, "troca de trecho", ACTOR);
        Execution ex = f.service().start(plan);
        assertThrows(IllegalStateException.class, ex::createOrders);
        assertThrows(IllegalArgumentException.class, () -> f.service().plan(id(601), TENANT,
                base(), Target.arc(A1), START, RETURN, "sem isolamento", ACTOR));
        assertNull(ex.noticeReceipt());
    }
}
