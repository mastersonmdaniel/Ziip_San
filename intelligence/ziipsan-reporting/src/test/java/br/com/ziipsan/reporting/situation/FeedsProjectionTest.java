package br.com.ziipsan.reporting.situation;

import br.com.ziipsan.operations.metering.telemetry.MeterAlarmKind;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ED-93: equipes, alarmes de telemetria, reservatorios e unidades fora de operacao. */
class FeedsProjectionTest {

    private List<SituationStateRow> project(SituationFeeds.CrewFeed crews,
                                            SituationFeeds.TelemetryAlarmFeed alarms,
                                            SituationFeeds.ReservoirFeed reservoirs,
                                            SituationFeeds.UnitOutageFeed outages) {
        return SituationFixtures.projector(t -> List.of(), t -> List.of(), crews, alarms, reservoirs, outages)
                .project(SituationFixtures.TENANT);
    }

    @Test
    void crewWithoutPositionIsListedButNeverPlacedOnTheMap() {
        List<SituationStateRow> rows = project(
                t -> List.of(
                        new SituationFeeds.CrewPosition("EQ-1", null, -27.0, -48.7, SituationFixtures.NOW.minusSeconds(30)),
                        new SituationFeeds.CrewPosition("EQ-2", null, null, null, SituationFixtures.NOW.minusSeconds(30))),
                t -> List.of(), t -> List.of(), t -> List.of());
        assertEquals(2, rows.size());

        InMemorySituationStateStore store = new InMemorySituationStateStore();
        store.replaceTenantSnapshot(SituationFixtures.TENANT, rows);
        SituationSnapshot snapshot = new SituationConsoleService(store).snapshot(SituationFixtures.TENANT,
                SituationSourceGuard.FeedSource.NATIVE_SUMMARY_TABLE,
                SituationFixtures.POLICY_3MIN, SituationFixtures.NOW);
        SituationMapView map = SituationMapView.fromSnapshot(snapshot);
        assertEquals(1, map.markers().size());
        assertEquals("EQ-1", map.markers().get(0).itemRef());
        assertEquals(1, map.unpositionedCount());
    }

    @Test
    void resolvedAlarmsAreExcludedOpenAndAcknowledgedRemain() {
        List<SituationStateRow> rows = project(t -> List.of(),
                t -> List.of(
                        new SituationFeeds.TelemetryAlarm("AL-1", "MP-1", MeterAlarmKind.CONTINUOUS_FLOW,
                                "OPEN", SituationFixtures.NOW.minusSeconds(60), null, null),
                        new SituationFeeds.TelemetryAlarm("AL-2", "MP-2", MeterAlarmKind.BATTERY_LOW,
                                "ACKNOWLEDGED", SituationFixtures.NOW.minusSeconds(120), null, null),
                        new SituationFeeds.TelemetryAlarm("AL-3", "MP-3", MeterAlarmKind.TAMPERING,
                                "RESOLVED", SituationFixtures.NOW.minusSeconds(180), null, null)),
                t -> List.of(), t -> List.of());
        assertEquals(2, rows.size());
        assertTrue(rows.stream().noneMatch(r -> r.itemRef().equals("AL-3")));
    }

    @Test
    void reservoirBandIsUnknownWithoutDeclaredThresholds() {
        List<SituationStateRow> rows = project(t -> List.of(), t -> List.of(),
                t -> List.of(new SituationFeeds.ReservoirLevel("RES-1", new BigDecimal("42.5"),
                        null, null, SituationFixtures.NOW.minusSeconds(10), null, null)),
                t -> List.of());
        assertEquals(1, rows.size());
        assertEquals("UNKNOWN", rows.get(0).statusLabel());
        assertEquals("42.5", rows.get(0).detail().get("levelPercent"));
    }

    @Test
    void reservoirBandUsesOnlyDeclaredThresholds() {
        List<SituationStateRow> rows = project(t -> List.of(), t -> List.of(),
                t -> List.of(
                        new SituationFeeds.ReservoirLevel("RES-BAIXO", new BigDecimal("10"),
                                new BigDecimal("20"), new BigDecimal("90"),
                                SituationFixtures.NOW.minusSeconds(10), null, null),
                        new SituationFeeds.ReservoirLevel("RES-OK", new BigDecimal("50"),
                                new BigDecimal("20"), new BigDecimal("90"),
                                SituationFixtures.NOW.minusSeconds(10), null, null)),
                t -> List.of());
        assertEquals("LOW", rows.stream().filter(r -> r.itemRef().equals("RES-BAIXO"))
                .findFirst().orElseThrow().statusLabel());
        assertTrue(rows.stream().filter(r -> r.itemRef().equals("RES-BAIXO"))
                .findFirst().orElseThrow().attention());
        assertEquals("WITHIN_DECLARED", rows.stream().filter(r -> r.itemRef().equals("RES-OK"))
                .findFirst().orElseThrow().statusLabel());
    }

    @Test
    void openUnitOutagesAreProjectedWithReasonAndSource() {
        List<SituationStateRow> rows = project(t -> List.of(), t -> List.of(), t -> List.of(),
                t -> List.of(new SituationFeeds.UnitOutage("ETA-1", SituationFixtures.NOW.minus(java.time.Duration.ofHours(1)),
                        "manutencao corretiva", "MANUAL", null, null)));
        assertEquals(1, rows.size());
        assertEquals("OUT_OF_OPERATION", rows.get(0).statusLabel());
        assertEquals("manutencao corretiva", rows.get(0).detail().get("reason"));
    }
}
