package br.com.ziipsan.intelligence.gis.webedit;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GisWebEditRulesTest {

    @Test
    void layerPermissionIsPerLayerAndOperation() {
        Set<String> granted = Set.of("VIEW", "EDIT_GEOMETRY");
        assertTrue(GisWebEditRules.canOperate(granted, "EDIT_GEOMETRY"));
        assertFalse(GisWebEditRules.canOperate(granted, "SPLIT"));
        assertThrows(GisWebEditRules.LayerPermissionException.class,
                () -> GisWebEditRules.ensureCanOperate(granted, "DELETE"));
        assertThrows(IllegalArgumentException.class,
                () -> GisWebEditRules.canOperate(granted, "FLY"));
    }

    @Test
    void editingRequiresDraftPackageOfEditorType() {
        assertDoesNotThrow(() ->
                GisWebEditRules.ensureEditableChangeSet("PROJECT", "DRAFT"));
        assertThrows(IllegalStateException.class, () ->
                GisWebEditRules.ensureEditableChangeSet("PROJECT", "APPLIED"));
        assertThrows(IllegalArgumentException.class, () ->
                GisWebEditRules.ensureEditableChangeSet("IMPORT", "DRAFT"));
    }

    @Test
    void serviceOrderOpensPackageOnlyWhenItAltersNetwork() {
        assertTrue(GisWebEditRules.serviceOrderOpensChangeSet(true));
        assertFalse(GisWebEditRules.serviceOrderOpensChangeSet(false));
        assertEquals("SERVICE_ORDER",
                GisWebEditRules.changeSetTypeForServiceOrder());
    }

    @Test
    void projectRequiresOwnProjectAndAsBuiltPackages() {
        assertDoesNotThrow(() ->
                GisWebEditRules.ensureProjectPair("PROJECT", "AS_BUILT"));
        assertThrows(IllegalArgumentException.class, () ->
                GisWebEditRules.ensureProjectPair("PROJECT", "PROJECT"));
    }

    @Test
    void plantStructureKindsAreTheSg148To152Set() {
        for (String kind : new String[]{"ETA", "ETE", "ELEVATORY", "RESERVOIR"}) {
            assertDoesNotThrow(() -> GisWebEditRules.ensurePlantStructure(kind));
        }
        assertThrows(IllegalArgumentException.class,
                () -> GisWebEditRules.ensurePlantStructure("PUMP_HOUSE_X"));
    }

    @Test
    void geometryMustFitLayerKind() {
        assertDoesNotThrow(() ->
                GisWebEditRules.ensureGeometryFits("ARC", "LINESTRING"));
        assertDoesNotThrow(() ->
                GisWebEditRules.ensureGeometryFits("STRUCTURE", "POINT"));
        assertThrows(IllegalArgumentException.class, () ->
                GisWebEditRules.ensureGeometryFits("NODE", "POLYGON"));
    }

    @Test
    void userLayerBelongsToTenantAndOwner() {
        UUID tenant = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        assertDoesNotThrow(() -> GisWebEditRules.ensureUserLayerOwnedBy(
                tenant, tenant, owner, owner));
        assertThrows(GisWebEditRules.LayerPermissionException.class, () ->
                GisWebEditRules.ensureUserLayerOwnedBy(
                        tenant, UUID.randomUUID(), owner, owner));
        assertThrows(GisWebEditRules.LayerPermissionException.class, () ->
                GisWebEditRules.ensureUserLayerOwnedBy(
                        tenant, tenant, owner, UUID.randomUUID()));
    }

    @Test
    void joinIsWhitelisted() {
        assertDoesNotThrow(() -> GisWebEditRules.ensureJoinAllowed(
                "gis_arc", "consumer_units", "LEFT", "service_connection_id"));
        assertThrows(IllegalArgumentException.class, () ->
                GisWebEditRules.ensureJoinAllowed(
                        "gis_arc", "pg_shadow", "LEFT", "code"));
        assertThrows(IllegalArgumentException.class, () ->
                GisWebEditRules.ensureJoinAllowed(
                        "gis_arc", "gis_node", "FULL", "code"));
    }
}
