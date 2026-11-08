package br.com.ziipsan.intelligence.gis.webedit;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GisDrawnConnectionSyncTest {

    @Test
    void onlyAppliedConnectionItemsSync() {
        assertTrue(GisDrawnConnectionSync.shouldSync(
                "APPLIED", "gis_connection", "INSERT"));
        assertFalse(GisDrawnConnectionSync.shouldSync(
                "DRAFT", "gis_connection", "INSERT"));
        assertFalse(GisDrawnConnectionSync.shouldSync(
                "APPLIED", "gis_arc", "INSERT"));
    }

    @Test
    void idempotencyKeyIsTheFeatureItself() {
        UUID id = UUID.randomUUID();
        assertEquals("gis-connection:" + id,
                GisDrawnConnectionSync.idempotencyKey(id));
        assertThrows(IllegalArgumentException.class,
                () -> GisDrawnConnectionSync.idempotencyKey(null));
    }

    @Test
    void syncStateMachine() {
        assertTrue(GisDrawnConnectionSync.canTransition("PENDING", "SYNCED"));
        assertTrue(GisDrawnConnectionSync.canTransition("SYNCED", "RETIRED"));
        assertFalse(GisDrawnConnectionSync.canTransition("RETIRED", "SYNCED"));
        assertThrows(IllegalStateException.class,
                () -> GisDrawnConnectionSync.ensureTransition("RETIRED", "SYNCED"));
    }

    @Test
    void domainDecidesServiceKind() {
        assertEquals("WATER",
                GisDrawnConnectionSync.serviceKindForDomain("WATER"));
        assertEquals("SEWER",
                GisDrawnConnectionSync.serviceKindForDomain("SEWER"));
        assertThrows(IllegalArgumentException.class,
                () -> GisDrawnConnectionSync.serviceKindForDomain("STORM"));
    }
}
