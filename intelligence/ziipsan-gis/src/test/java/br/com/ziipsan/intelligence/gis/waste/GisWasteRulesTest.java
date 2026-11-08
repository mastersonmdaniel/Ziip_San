package br.com.ziipsan.intelligence.gis.waste;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Regras da GIS-14 (§4.5/§5.4): uma prova por regra. */
class GisWasteRulesTest {

    private static Map<String, Object> containerAttrs() {
        return Map.of("waste_fraction", "MIXED", "container_model", "WHEELED_PE",
                "volume", 240.0);
    }

    @Test
    void containerValidAndInvalid() {
        GisWasteCatalog.WasteType container = GisWasteCatalog.requireType("CONTAINER");
        GisWasteRules.ensurePointAttributes(container, containerAttrs());
        assertThrows(IllegalArgumentException.class, () -> GisWasteRules.ensurePointAttributes(
                container, Map.of("waste_fraction", "MIXED", "container_model", "WHEELED_PE")));
        assertThrows(IllegalArgumentException.class, () -> GisWasteRules.ensurePointAttributes(
                container, Map.of("waste_fraction", "XXX", "container_model", "WHEELED_PE",
                        "volume", 240.0)));
        assertThrows(IllegalArgumentException.class, () -> GisWasteRules.ensurePointAttributes(
                container, Map.of("waste_fraction", "MIXED", "container_model", "WHEELED_PE",
                        "volume", -1.0)));
        assertThrows(IllegalArgumentException.class, () -> GisWasteRules.ensurePointAttributes(
                container, Map.of("waste_fraction", "MIXED", "container_model", "PAPELAO",
                        "volume", 240.0)));
    }

    @Test
    void collectionPointAndPev() {
        GisWasteRules.ensurePointAttributes(GisWasteCatalog.requireType("COLLECTION_POINT"),
                Map.of("waste_fraction", "DRY_RECYCLABLE", "collection_frequency", "3x/semana",
                        "shift", "MORNING"));
        GisWasteRules.ensurePointAttributes(GisWasteCatalog.requireType("DROP_OFF_POINT"),
                Map.of("accepted_fractions", List.of("GLASS", "HAZARDOUS"),
                        "responsible_party_kind", "REVERSE_LOGISTICS_SYSTEM"));
        assertThrows(IllegalArgumentException.class, () -> GisWasteRules.ensurePointAttributes(
                GisWasteCatalog.requireType("DROP_OFF_POINT"),
                Map.of("accepted_fractions", List.of(),
                        "responsible_party_kind", "MUNICIPALITY")));
    }

    @Test
    void dumpAttributesAndCleanupGate() {
        Map<String, Object> dump = Map.of("identified_on", "2026-11-01", "dump_kind", "CONSTRUCTION",
                "estimated_volume_m3", 2.5, "dump_status", "ACTIVE");
        GisWasteRules.ensurePointAttributes(GisWasteCatalog.requireType("ILLEGAL_DUMP"), dump);
        // Com foto e ACTIVE: pede limpeza. Sem foto: recusa. ELIMINATED: recusa.
        GisWasteRules.ensureCleanupRequestable("ACTIVE", true);
        GisWasteRules.ensureCleanupRequestable("REINCIDENT", true);
        assertThrows(IllegalArgumentException.class,
                () -> GisWasteRules.ensureCleanupRequestable("ACTIVE", false));
        assertThrows(IllegalArgumentException.class,
                () -> GisWasteRules.ensureCleanupRequestable("ELIMINATED", true));
        assertThrows(IllegalArgumentException.class,
                () -> GisWasteRules.ensureCleanupRequestable("CLEANING", true));
    }

    @Test
    void dumpTransitions() {
        GisWasteRules.ensureDumpTransition("ACTIVE", "CLEANING");
        GisWasteRules.ensureDumpTransition("CLEANING", "ELIMINATED");
        GisWasteRules.ensureDumpTransition("CLEANING", "ACTIVE");
        GisWasteRules.ensureDumpTransition("ELIMINATED", "REINCIDENT");
        GisWasteRules.ensureDumpTransition("REINCIDENT", "CLEANING");
        assertThrows(IllegalArgumentException.class,
                () -> GisWasteRules.ensureDumpTransition("ELIMINATED", "ACTIVE"));
        assertThrows(IllegalArgumentException.class,
                () -> GisWasteRules.ensureDumpTransition("ACTIVE", "ELIMINATED"));
        assertThrows(IllegalArgumentException.class,
                () -> GisWasteRules.ensureDumpTransition("ACTIVE", "REINCIDENT"));
    }

    @Test
    void sectorAttributes() {
        GisWasteRules.ensureSectorAttributes(Map.of(
                "collection_kind", "SELECTIVE", "collection_days", List.of("MON", "WED", "FRI"),
                "shift", "MORNING", "executor", "Cooperativa Exemplo"));
        assertThrows(IllegalArgumentException.class, () -> GisWasteRules.ensureSectorAttributes(
                Map.of("collection_kind", "SELECTIVE", "collection_days", List.of(),
                        "shift", "MORNING", "executor", "X")));
        assertThrows(IllegalArgumentException.class, () -> GisWasteRules.ensureSectorAttributes(
                Map.of("collection_kind", "SELECTIVE", "collection_days", List.of("FUNDAY"),
                        "shift", "MORNING", "executor", "X")));
        assertThrows(IllegalArgumentException.class, () -> GisWasteRules.ensureSectorAttributes(
                Map.of("collection_kind", "SELECTIVE", "collection_days", List.of("MON"),
                        "shift", "MORNING")));
    }

    @Test
    void routeStopsMustBeContiguousCollectableAndUnique() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        GisWasteRules.ensureRouteStops(List.of(
                new GisWasteRules.RouteStopInput(a, "CONTAINER", 1),
                new GisWasteRules.RouteStopInput(b, "ECOPOINT", 2)));
        assertThrows(IllegalArgumentException.class, () -> GisWasteRules.ensureRouteStops(List.of(
                new GisWasteRules.RouteStopInput(a, "CONTAINER", 1),
                new GisWasteRules.RouteStopInput(b, "CONTAINER", 3))));
        assertThrows(IllegalArgumentException.class, () -> GisWasteRules.ensureRouteStops(List.of(
                new GisWasteRules.RouteStopInput(a, "ILLEGAL_DUMP", 1))));
        assertThrows(IllegalArgumentException.class, () -> GisWasteRules.ensureRouteStops(List.of(
                new GisWasteRules.RouteStopInput(a, "CONTAINER", 1),
                new GisWasteRules.RouteStopInput(a, "CONTAINER", 2))));
        assertThrows(IllegalArgumentException.class, () -> GisWasteRules.ensureRouteStops(List.of()));
    }

    @Test
    void vehicleCatalog() {
        GisWasteRules.ensureVehicle("COMPACTOR_19M3");
        assertThrows(IllegalArgumentException.class, () -> GisWasteRules.ensureVehicle("MOTO"));
    }

    @Test
    void estimateNeedsParametersAndComputesKnownCase() {
        // Sem parametros: sem estimativa (nada inventado).
        assertNull(GisWasteRules.estimate(10_000.0, 4, null, null));
        assertNull(GisWasteRules.estimate(10_000.0, 4, 20.0, null));
        // Caso conhecido: 10 km a 20 km/h = 30 min + 4 paradas x 5 min = 50 min.
        GisWasteRules.RouteEstimate est = GisWasteRules.estimate(10_000.0, 4, 20.0, 5.0);
        assertNotNull(est);
        assertEquals(50.0, est.estimatedMinutes(), 1e-9);
        assertEquals(10_000.0, est.extensionM(), 1e-9);
        assertThrows(IllegalArgumentException.class,
                () -> GisWasteRules.estimate(10_000.0, 4, 0.0, 5.0));
    }

    @Test
    void propertySectorDecision() {
        UUID sector = UUID.randomUUID();
        assertEquals(GisWasteRules.PropertySectorStatus.UNLINKED,
                GisWasteRules.decidePropertySector(List.of()).status());
        GisWasteRules.PropertySectorDecision linked =
                GisWasteRules.decidePropertySector(List.of(sector));
        assertEquals(GisWasteRules.PropertySectorStatus.LINKED, linked.status());
        assertEquals(sector, linked.sectorZoneId());
        GisWasteRules.PropertySectorDecision review =
                GisWasteRules.decidePropertySector(List.of(sector, UUID.randomUUID()));
        assertEquals(GisWasteRules.PropertySectorStatus.REVIEW, review.status());
        assertNull(review.sectorZoneId());
    }

    @Test
    void wasteClassMustFollowNbr10004() {
        assertThrows(IllegalArgumentException.class, () -> GisWasteRules.ensurePointAttributes(
                GisWasteCatalog.requireType("ECOPOINT"),
                Map.of("accepted_materials", List.of("RCC"), "waste_class", "CLASS_II_A")));
        GisWasteRules.ensurePointAttributes(GisWasteCatalog.requireType("ECOPOINT"),
                Map.of("accepted_materials", List.of("RCC"), "waste_class", "CLASS_2"));
    }
}
