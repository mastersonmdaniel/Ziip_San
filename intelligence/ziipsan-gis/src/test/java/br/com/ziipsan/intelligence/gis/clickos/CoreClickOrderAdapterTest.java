package br.com.ziipsan.intelligence.gis.clickos;

import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;
import br.com.ziipsan.core.serviceorder.TargetType;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsModel.ClickPropagationConfig;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsModel.PropagationMode;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsPorts.ClickServiceOrderPayload;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.Target;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static br.com.ziipsan.intelligence.gis.maneuver.ManeuverFixtures.ACTOR;
import static br.com.ziipsan.intelligence.gis.maneuver.ManeuverFixtures.TENANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fiação P-II1-FIACAO (Ciclo 2 do PDCA GIS): a tradução do payload
 * do clique para o motor real de OS, provada sobre um motor falso
 * manuscrito que regista as chamadas na ordem. O comportamento do
 * motor contra a BD é prova de IT (bloqueada pelo 42501 — ver o
 * relatório do ciclo).
 */
class CoreClickOrderAdapterTest {

    private static final String TYPE = "TIPO-SINTETICO-DE-TESTE";
    private static final UUID ORDER = UUID.randomUUID();

    private static final class RecordingEngine implements ClickOrderEngine {
        ServiceOrderEnums.TypeIndicator indicator = ServiceOrderEnums.TypeIndicator.FIELD;
        final List<String> calls = new ArrayList<>();
        final List<TargetType> targetTypes = new ArrayList<>();
        final List<UUID> targetIds = new ArrayList<>();

        @Override
        public ServiceOrderEnums.TypeIndicator typeIndicator(String serviceTypeCode) {
            calls.add("type:" + serviceTypeCode);
            return indicator;
        }

        @Override
        public UUID createOrder(String serviceTypeCode,
                                ServiceOrderEnums.ExecutionMode executionMode, UUID requestedBy) {
            calls.add("create:" + serviceTypeCode + ":" + executionMode + ":" + requestedBy);
            return ORDER;
        }

        @Override
        public void addTarget(UUID orderId, TargetType targetType, UUID targetId, UUID actorId) {
            calls.add("target:" + orderId);
            targetTypes.add(targetType);
            targetIds.add(targetId);
        }

        @Override
        public void submit(UUID orderId, UUID actorId) {
            calls.add("submit:" + orderId + ":" + actorId);
        }
    }

    private RecordingEngine engine;
    private CoreClickOrderAdapter adapter;
    private ClickServiceOrderService payloadSource;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT);
        TenantContextHolder.setTenant("tenant_teste");
        engine = new RecordingEngine();
        adapter = new CoreClickOrderAdapter(() -> engine);
        payloadSource = new ClickServiceOrderService(
                t -> br.com.ziipsan.intelligence.gis.maneuver.ManeuverFixtures.base(), null);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private ClickServiceOrderPayload readyPayload(Target clicked) {
        return payloadSource.buildPayload(TENANT, ACTOR, TYPE, clicked,
                PropagationMode.DOWNSTREAM, ClickPropagationConfig.proofDefaults());
    }

    @Test
    void fieldTypeCreatesFieldOrderRegistersEveryCoreTargetAndSubmits() {
        ClickServiceOrderPayload payload = readyPayload(
                Target.node(br.com.ziipsan.intelligence.gis.maneuver.ManeuverFixtures.N3));
        assertTrue(payload.readyToSubmit());
        UUID created = adapter.createFromClickPayload(payload);
        assertEquals(ORDER, created);
        // Ordem das chamadas: tipo -> criar -> alvos (na ordem do payload) -> submeter.
        assertEquals("type:" + TYPE, engine.calls.get(0));
        assertEquals("create:" + TYPE + ":FIELD:" + ACTOR, engine.calls.get(1));
        assertEquals("submit:" + ORDER + ":" + ACTOR, engine.calls.get(engine.calls.size() - 1));
        assertEquals(payload.coreTargets().stream().map(h -> h.targetType()).toList(),
                engine.targetTypes);
        assertEquals(payload.coreTargets().stream().map(h -> h.targetId()).toList(),
                engine.targetIds);
        assertEquals(TargetType.GIS_POINT, engine.targetTypes.get(0));
    }

    @Test
    void internalTypeCreatesInternalOrder() {
        engine.indicator = ServiceOrderEnums.TypeIndicator.INTERNAL;
        adapter.createFromClickPayload(readyPayload(
                Target.node(br.com.ziipsan.intelligence.gis.maneuver.ManeuverFixtures.N3)));
        assertTrue(engine.calls.stream()
                .anyMatch(c -> c.equals("create:" + TYPE + ":INTERNAL:" + ACTOR)));
    }

    @Test
    void arcClickRegistersGisLinearTarget() {
        adapter.createFromClickPayload(readyPayload(
                Target.arc(br.com.ziipsan.intelligence.gis.maneuver.ManeuverFixtures.A9)));
        assertEquals(TargetType.GIS_LINEAR, engine.targetTypes.get(0));
    }

    @Test
    void payloadOfAnotherTenantIsRefusedAndEngineIsNotCalled() {
        ClickServiceOrderPayload payload = payloadSource.buildPayload(
                UUID.randomUUID(), ACTOR, TYPE,
                Target.node(br.com.ziipsan.intelligence.gis.maneuver.ManeuverFixtures.N3),
                PropagationMode.DOWNSTREAM, ClickPropagationConfig.proofDefaults());
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> adapter.createFromClickPayload(payload));
        assertTrue(e.getMessage().contains("tenant"));
        assertEquals(List.of(), engine.calls);
    }
}
