package br.com.ziipsan.intelligence.gis.clickos;

import br.com.ziipsan.intelligence.gis.clickos.ClickOsDtos.ClickInput;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsDtos.CreatedView;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsDtos.PayloadView;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsDtos.PropagationView;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Serviço de aplicação da II-1 (Ciclo 2 do PDCA GIS): delegação ao
 * núcleo puro com retrato falso conhecido e porta de OS falsa que
 * regista; e a recusa nomeada quando nenhum retrato está registado
 * (P-PDCAGIS2-01 — não há implementação de produção da SnapshotPort
 * em todo o repo).
 */
class ClickOsApplicationServiceTest {

    private static final String TYPE = "TIPO-SINTETICO-DE-TESTE";
    private static final UUID CREATED = UUID.randomUUID();

    private ClickInput nodeClick(String serviceTypeCode) {
        return new ClickInput(null, ManeuverFixtures.N3, "DOWNSTREAM",
                serviceTypeCode, null, null);
    }

    @Test
    void propagateDelegatesToTheCoreEngine() {
        ClickOsApplicationService service = new ClickOsApplicationService(
                t -> ManeuverFixtures.base(), payload -> CREATED);
        PropagationView view = service.propagate(ManeuverFixtures.TENANT, nodeClick(TYPE));
        assertEquals("DOWNSTREAM", view.mode());
        assertEquals("WITH_AFFECTED_UNITS", view.outcome());
        assertEquals(List.of(ManeuverFixtures.U2, ManeuverFixtures.U3,
                ManeuverFixtures.U4, ManeuverFixtures.U5), view.affectedUnitIds());
    }

    @Test
    void payloadWithoutServiceTypeIsNotReadyAndNamesTheGap() {
        ClickOsApplicationService service = new ClickOsApplicationService(
                t -> ManeuverFixtures.base(), payload -> CREATED);
        PayloadView view = service.buildPayload(
                ManeuverFixtures.TENANT, ManeuverFixtures.ACTOR, nodeClick(null));
        assertFalse(view.readyToSubmit());
        assertTrue(view.missingForSubmit().stream().anyMatch(m -> m.contains("serviceTypeCode")));
    }

    @Test
    void createSubmitsTheReadyPayloadOnceAndReturnsTheCoreId() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<ClickOsPorts.ClickServiceOrderPayload> seen = new AtomicReference<>();
        ClickOsApplicationService service = new ClickOsApplicationService(
                t -> ManeuverFixtures.base(), payload -> {
                    calls.incrementAndGet();
                    seen.set(payload);
                    return CREATED;
                });
        CreatedView created = service.createFromClick(
                ManeuverFixtures.TENANT, ManeuverFixtures.ACTOR, nodeClick(TYPE));
        assertEquals(CREATED, created.orderId());
        assertEquals(1, calls.get());
        assertEquals(TYPE, seen.get().serviceTypeCode());
        assertTrue(created.payload().readyToSubmit());
    }

    @Test
    void createWithUnreadyPayloadIsRefusedByTheCoreAndPortIsNotCalled() {
        AtomicInteger calls = new AtomicInteger();
        ClickOsApplicationService service = new ClickOsApplicationService(
                t -> ManeuverFixtures.base(), payload -> {
                    calls.incrementAndGet();
                    return CREATED;
                });
        assertThrows(IllegalStateException.class, () -> service.createFromClick(
                ManeuverFixtures.TENANT, ManeuverFixtures.ACTOR, nodeClick(null)));
        assertEquals(0, calls.get());
    }

    @Test
    void missingSnapshotBeanRefusesWithTheNamedPendency() {
        ObjectProvider<br.com.ziipsan.intelligence.gis.maneuver.ManeuverPorts.SnapshotPort> empty =
                emptyProvider();
        ClickOsApplicationService service = new ClickOsApplicationService(empty, payload -> CREATED);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.propagate(ManeuverFixtures.TENANT, nodeClick(TYPE)));
        assertTrue(e.getMessage().contains("P-PDCAGIS2-01"));
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<br.com.ziipsan.intelligence.gis.maneuver.ManeuverPorts.SnapshotPort>
    emptyProvider() {
        return (ObjectProvider<br.com.ziipsan.intelligence.gis.maneuver.ManeuverPorts.SnapshotPort>)
                Proxy.newProxyInstance(ClickOsApplicationServiceTest.class.getClassLoader(),
                        new Class<?>[]{ObjectProvider.class},
                        (proxy, method, args) -> switch (method.getName()) {
                            case "getIfAvailable" -> null;
                            case "getObject" -> throw new IllegalStateException("sem bean");
                            default -> throw new UnsupportedOperationException(method.getName());
                        });
    }
}
