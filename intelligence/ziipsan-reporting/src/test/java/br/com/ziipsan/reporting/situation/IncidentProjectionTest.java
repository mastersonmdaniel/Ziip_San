package br.com.ziipsan.reporting.situation;

import br.com.ziipsan.core.incident.NetworkIncident;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ED-93: ocorrencias abertas da ED-92 (entidade REAL) no console. */
class IncidentProjectionTest {

    @Test
    void openAndControlledIncidentsAreProjectedFromTheRealEd92Entity() {
        NetworkIncident open = SituationFixtures.incident("OC-1", NetworkIncident.Type.RUPTURE,
                SituationFixtures.NOW_LOCAL.minusMinutes(20));
        NetworkIncident controlled = SituationFixtures.incident("OC-2", NetworkIncident.Type.OVERFLOW,
                SituationFixtures.NOW_LOCAL.minusHours(1));
        controlled.markControlled();
        SituationProjector projector = SituationFixtures.projector(
                t -> List.of(open, controlled), t -> List.of(), t -> List.of(),
                t -> List.of(), t -> List.of(), t -> List.of());

        List<SituationStateRow> rows = projector.project(SituationFixtures.TENANT);

        assertEquals(2, rows.size());
        assertTrue(rows.stream().allMatch(r -> r.kind() == SituationItemKind.INCIDENT));
        assertEquals("RUPTURE", rows.stream().filter(r -> r.itemRef().equals("OC-1"))
                .findFirst().orElseThrow().detail().get("type"));
        assertEquals("CONTROLLED", rows.stream().filter(r -> r.itemRef().equals("OC-2"))
                .findFirst().orElseThrow().statusLabel());
        assertEquals("12", rows.get(0).detail().get("economies"));
    }

    @Test
    void restoredAndClosedIncidentsAreNotOpen() {
        NetworkIncident restored = SituationFixtures.incident("OC-3", NetworkIncident.Type.INTERRUPTION,
                SituationFixtures.NOW_LOCAL.minusHours(3));
        restored.markRestored(SituationFixtures.NOW_LOCAL.minusMinutes(30));
        NetworkIncident closed = SituationFixtures.closedIncident("OC-4");
        SituationProjector projector = SituationFixtures.projector(
                t -> List.of(restored, closed), t -> List.of(), t -> List.of(),
                t -> List.of(), t -> List.of(), t -> List.of());

        assertTrue(projector.project(SituationFixtures.TENANT).isEmpty());
    }

    @Test
    void incidentsOfAnotherTenantNeverLeakIntoTheSnapshot() {
        NetworkIncident foreign = new NetworkIncident(java.util.UUID.randomUUID(),
                SituationFixtures.OTHER_TENANT, "OC-X", NetworkIncident.Type.FLOODING, false,
                NetworkIncident.Origin.TELEMETRY, SituationFixtures.NOW_LOCAL.minusMinutes(5),
                NetworkIncident.AffectedSnapshot.empty(), null);
        SituationProjector projector = SituationFixtures.projector(
                t -> List.of(foreign), t -> List.of(), t -> List.of(),
                t -> List.of(), t -> List.of(), t -> List.of());

        assertTrue(projector.project(SituationFixtures.TENANT).isEmpty());
    }

    @Test
    void projectionIsDeterministicPerItem() {
        NetworkIncident open = SituationFixtures.incident("OC-1", NetworkIncident.Type.RUPTURE,
                SituationFixtures.NOW_LOCAL.minusMinutes(20));
        SituationProjector projector = SituationFixtures.projector(
                t -> List.of(open), t -> List.of(), t -> List.of(),
                t -> List.of(), t -> List.of(), t -> List.of());

        assertEquals(projector.project(SituationFixtures.TENANT).get(0).id(),
                projector.project(SituationFixtures.TENANT).get(0).id());
    }
}
