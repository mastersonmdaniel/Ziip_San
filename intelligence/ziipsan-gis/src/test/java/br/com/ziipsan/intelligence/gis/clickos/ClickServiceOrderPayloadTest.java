package br.com.ziipsan.intelligence.gis.clickos;

import br.com.ziipsan.core.serviceorder.TargetType;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsModel.ClickPropagationConfig;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsModel.PropagationMode;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsPorts.ClickServiceOrderPayload;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsPorts.CoreTargetHint;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.Target;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static br.com.ziipsan.intelligence.gis.maneuver.ManeuverFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Aceite §4.4 da estimativa II-1: o payload de OS contém elemento,
 * propagação e UCs, sai pela porta do núcleo — e só quando pronto.
 * O código de tipo usado é entrada sintética do teste (o tipo real
 * é o do catálogo do tenant, [A DEFINIR] neste pacote).
 */
class ClickServiceOrderPayloadTest {

    private static final String TYPE = "TIPO-SINTETICO-DE-TESTE";
    private static final UUID CREATED = id(950);

    private ClickServiceOrderService service(AtomicInteger calls, AtomicReference<ClickServiceOrderPayload> seen) {
        return new ClickServiceOrderService(t -> base(), payload -> {
            calls.incrementAndGet();
            seen.set(payload);
            return CREATED;
        });
    }

    @Test
    void payloadContainsClickedPropagationAndUnits() {
        ClickServiceOrderPayload p = service(new AtomicInteger(), new AtomicReference<>())
                .buildPayload(TENANT, ACTOR, TYPE, Target.node(N3),
                        PropagationMode.DOWNSTREAM, ClickPropagationConfig.proofDefaults());
        assertEquals(Target.node(N3), p.clicked());
        assertEquals(List.of(N3, VB, N6, N4, N5), p.affectedNodeIds());
        assertEquals(List.of(A6, A7, A8, A9), p.affectedArcIds());
        assertEquals(List.of(U2, U3, U4, U5), p.affectedUnitIds());
        assertEquals(8, p.totalEconomies());
        assertTrue(p.readyToSubmit());
        // Alvos do núcleo: o nó clicado (GIS_POINT) + uma CONSUMER_UNIT por UC afectada.
        assertEquals(new CoreTargetHint(TargetType.GIS_POINT, N3), p.coreTargets().get(0));
        assertEquals(List.of(U2, U3, U4, U5), p.coreTargets().subList(1, 5).stream()
                .map(h -> {
                    assertEquals(TargetType.CONSUMER_UNIT, h.targetType());
                    return h.targetId();
                }).toList());
    }

    @Test
    void arcClickMapsToGisLinearTarget() {
        ClickServiceOrderPayload p = service(new AtomicInteger(), new AtomicReference<>())
                .buildPayload(TENANT, ACTOR, TYPE, Target.arc(A9),
                        PropagationMode.DOWNSTREAM, ClickPropagationConfig.proofDefaults());
        assertEquals(new CoreTargetHint(TargetType.GIS_LINEAR, A9), p.coreTargets().get(0));
    }

    @Test
    void payloadWithoutServiceTypeIsNotReadyAndNamesTheGap() {
        ClickServiceOrderPayload p = service(new AtomicInteger(), new AtomicReference<>())
                .buildPayload(TENANT, ACTOR, null, Target.node(N3),
                        PropagationMode.DOWNSTREAM, ClickPropagationConfig.proofDefaults());
        assertFalse(p.readyToSubmit());
        assertTrue(p.missingForSubmit().stream().anyMatch(m -> m.contains("serviceTypeCode")));
    }

    @Test
    void submitGoesThroughThePortOnceWithThePayload() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<ClickServiceOrderPayload> seen = new AtomicReference<>();
        ClickServiceOrderService s = service(calls, seen);
        ClickServiceOrderPayload p = s.buildPayload(TENANT, ACTOR, TYPE, Target.node(N3),
                PropagationMode.DOWNSTREAM, ClickPropagationConfig.proofDefaults());
        assertEquals(CREATED, s.submit(p));
        assertEquals(1, calls.get());
        assertEquals(p, seen.get());
    }

    @Test
    void submitOfUnreadyPayloadIsRefusedAndPortIsNotCalled() {
        AtomicInteger calls = new AtomicInteger();
        ClickServiceOrderService s = service(calls, new AtomicReference<>());
        ClickServiceOrderPayload p = s.buildPayload(TENANT, ACTOR, null, Target.node(N3),
                PropagationMode.DOWNSTREAM, ClickPropagationConfig.proofDefaults());
        assertThrows(IllegalStateException.class, () -> s.submit(p));
        assertEquals(0, calls.get());
    }

    @Test
    void truncatedPropagationDoesNotBecomeAnOrder() {
        ClickServiceOrderPayload p = service(new AtomicInteger(), new AtomicReference<>())
                .buildPayload(TENANT, ACTOR, TYPE, Target.node(N3),
                        PropagationMode.DOWNSTREAM, new ClickPropagationConfig(null, 3));
        assertFalse(p.readyToSubmit());
        assertTrue(p.missingForSubmit().stream().anyMatch(m -> m.contains("teto de UCs")));
    }

    @Test
    void notIsolableClickDoesNotBecomeAnOrder() {
        ClickServiceOrderPayload p = service(new AtomicInteger(), new AtomicReference<>())
                .buildPayload(TENANT, ACTOR, TYPE, Target.arc(A1),
                        PropagationMode.ISOLATION, ClickPropagationConfig.proofDefaults());
        assertFalse(p.readyToSubmit());
        assertTrue(p.missingForSubmit().stream()
                .anyMatch(m -> m.contains("SOURCE_REACHED_WITHOUT_VALVE")));
    }
}
