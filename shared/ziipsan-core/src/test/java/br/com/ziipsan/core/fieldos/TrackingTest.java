package br.com.ziipsan.core.fieldos;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TrackingTest {
    static final UUID W = UUID.randomUUID();
    static final Instant T0 = Instant.parse("2026-03-10T11:00:00Z");
    static Tracking.TrackPoint p(double lat, double lon, long sec) {
        return new Tracking.TrackPoint(W, null, T0.plusSeconds(sec), lat, lon);
    }
    @Test void pointOutsideShiftIsRefused() {
        var shift = new Tracking.Shift(T0, T0.plusSeconds(3600));
        assertThrows(IllegalStateException.class, () -> Tracking.ingest(p(-26.0, -48.7, 7200), shift, true, true));
        assertNotNull(Tracking.ingest(p(-26.0, -48.7, 600), shift, true, true));
    }
    @Test void trackOnlyInShiftCannotBeConfiguredOff() {
        assertThrows(IllegalStateException.class, () -> FieldOsParameters.requireTrackOnlyInShift(false));
    }
    @Test void trailRequiresEmployeeNotice() {
        assertThrows(IllegalStateException.class, () -> Tracking.requireEmployeeNotice(false));
    }
    @Test void ingestWithoutEmployeeNoticeIsRefusedEvenInsideShift() {
        // D-1: o portao do aviso e COMPOSTO no ingest — ponto valido, dentro
        // da jornada e com a trava ligada, mas SEM aviso, e recusado.
        var shift = new Tracking.Shift(T0, T0.plusSeconds(3600));
        var ex = assertThrows(IllegalStateException.class,
                () -> Tracking.ingest(p(-26.0, -48.7, 600), shift, true, false));
        assertTrue(ex.getMessage().contains("aviso de rastreamento ao empregado"),
                "recusa deve ser nomeada pelo aviso: " + ex.getMessage());
        // e com aviso valido o mesmo ponto e ingerido
        assertNotNull(Tracking.ingest(p(-26.0, -48.7, 600), shift, true, true));
    }
    @Test void absentTrackParameterIsRefusedNotDefaulted() {
        // D-3 (lado da jornada): ausencia nao e default — Optional vazio recusa.
        assertThrows(IllegalStateException.class,
                () -> FieldOsParameters.requireTrackOnlyInShift(java.util.Optional.empty()));
        assertThrows(IllegalStateException.class,
                () -> FieldOsParameters.requireTrackOnlyInShift(java.util.Optional.of(false)));
        assertTrue(FieldOsParameters.requireTrackOnlyInShift(java.util.Optional.of(true)));
    }
    @Test void onlySupervisorReadsTrailAndPanel() {
        assertThrows(IllegalStateException.class, () -> Tracking.requireSupervisorAccess(Tracking.AccessRole.FIELD_WORKER));
        assertThrows(IllegalStateException.class, () -> GpsPanel.build(UUID.randomUUID(), T0, T0.plusSeconds(60),
                Tracking.AccessRole.OTHER, List.of()));
        assertNotNull(GpsPanel.build(UUID.randomUUID(), T0, T0.plusSeconds(60), Tracking.AccessRole.SUPERVISOR, List.of()));
    }
    @Test void retentionOnlyReducesAgainstPlatformCeiling() {
        assertEquals(30, FieldOsParameters.validateRetentionDays(30, 90));
        assertThrows(IllegalArgumentException.class, () -> FieldOsParameters.validateRetentionDays(120, 90));
        assertEquals(T0.minus(Duration.ofDays(30)), Tracking.purgeCutoff(T0, 30));
    }
    @Test void summaryComputesDistanceMovementAndStops() {
        var pts = List.of(
            p(-26.0000, -48.7000, 0),
            p(-26.0010, -48.7000, 60),     // ~111 m em movimento
            p(-26.0010, -48.7000, 660),    // parado 10 min no mesmo lugar
            p(-26.0010, -48.7000, 720));
        var s = Tracking.summarize(pts, 5.0, Duration.ofMinutes(5));
        assertTrue(s.distanceMeters() > 100 && s.distanceMeters() < 130, "distancia=" + s.distanceMeters());
        assertEquals(Duration.ofMinutes(12), s.trackedTime());
        assertEquals(1, s.stops().size());
        // parada medida da chegada ao local (t=60s) ao ultimo ponto parado (t=720s)
        assertEquals(Duration.ofMinutes(11), s.stops().get(0).duration());
    }
    @Test void legalBasesAndRipdItemAreRegistered() {
        assertEquals(List.of("LGPD art. 7o, V", "LGPD art. 7o, IX"), Tracking.LEGAL_BASES);
        assertEquals("FIELD_GPS_TRACKING", Tracking.RIPD_ITEM_CODE);
        assertFalse(Tracking.PURPOSE.isBlank());
    }
}
