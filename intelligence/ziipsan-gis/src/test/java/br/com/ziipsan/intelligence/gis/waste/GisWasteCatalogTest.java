package br.com.ziipsan.intelligence.gis.waste;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Catálogo do §4.5: completude e coerência interna (GIS-14). */
class GisWasteCatalogTest {

    @Test
    void catalogHasThe13TypesOfSection45() {
        assertEquals(13, GisWasteCatalog.TYPES.size());
        for (String code : new String[]{"COLLECTION_POINT", "CONTAINER", "DROP_OFF_POINT",
                "ECOPOINT", "LITTER_BIN", "ILLEGAL_DUMP", "COLLECTION_SECTOR",
                "TRANSFER_STATION", "SORTING_FACILITY", "COMPOSTING_SITE",
                "SANITARY_LANDFILL", "INERT_LANDFILL", "CONSTRUCTION_WASTE_FACILITY"}) {
            assertTrue(GisWasteCatalog.byCode(code).isPresent(), code);
        }
    }

    @Test
    void rolesFollowSection45() {
        assertEquals("gis_poi", GisWasteCatalog.requireType("CONTAINER").featureTable());
        assertEquals("gis_poi", GisWasteCatalog.requireType("ILLEGAL_DUMP").featureTable());
        assertEquals("gis_structure", GisWasteCatalog.requireType("ECOPOINT").featureTable());
        assertEquals("gis_structure", GisWasteCatalog.requireType("SANITARY_LANDFILL").featureTable());
        assertEquals("gis_zone", GisWasteCatalog.requireType("COLLECTION_SECTOR").featureTable());
        assertEquals("gis14:CONTAINER", GisWasteCatalog.requireType("CONTAINER").factoryCode());
    }

    @Test
    void routeStopTypesAreTheCollectableOnes() {
        assertEquals(5, GisWasteCatalog.routeStopTypes().size());
        assertFalse(GisWasteCatalog.requireType("ILLEGAL_DUMP").routeStop());
        assertFalse(GisWasteCatalog.requireType("COLLECTION_SECTOR").routeStop());
        assertTrue(GisWasteCatalog.requireType("ECOPOINT").routeStop());
    }

    @Test
    void containerReferenceVolumes() {
        assertTrue(GisWasteCatalog.isReferenceVolume("WHEELED_PE", 240.0));
        assertTrue(GisWasteCatalog.isReferenceVolume("WHEELED_PE", 1000.0));
        assertFalse(GisWasteCatalog.isReferenceVolume("WHEELED_PE", 999.0));
        assertTrue(GisWasteCatalog.isReferenceVolume("STATIONARY_SKIP", 5.0));
        assertFalse(GisWasteCatalog.isReferenceVolume("STATIONARY_SKIP", 240.0));
        assertFalse(GisWasteCatalog.isReferenceVolume("UNKNOWN_MODEL", 240.0));
    }

    @Test
    void everyTypeHasSymbolKeyAndKnownClass() {
        for (GisWasteCatalog.WasteType type : GisWasteCatalog.TYPES) {
            assertTrue(type.symbolKey().startsWith("waste-"), type.code());
            assertTrue(type.classCode().startsWith("WASTE_")
                    || type.classCode().equals("COLLECTION_SECTOR"), type.code());
        }
        assertThrows(IllegalArgumentException.class, () -> GisWasteCatalog.requireType("BOCA_DE_LOBO"));
    }
}
