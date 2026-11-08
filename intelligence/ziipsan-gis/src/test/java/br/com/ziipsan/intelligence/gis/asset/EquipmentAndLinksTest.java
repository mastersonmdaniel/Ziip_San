package br.com.ziipsan.intelligence.gis.asset;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static br.com.ziipsan.intelligence.gis.asset.AssetFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class EquipmentAndLinksTest {

    @Test
    void sourcedCatalogHasBoundariesForEveryClass() {
        assertFalse(EquipmentClassCatalog.SOURCED_CLASSES.isEmpty());
        for (var c : EquipmentClassCatalog.SOURCED_CLASSES) {
            assertFalse(c.includes().isBlank(), c.code());
            assertFalse(c.excludes().isBlank(), c.code());
        }
        // frota e da ED-59: fora deste catalogo
        assertThrows(IllegalArgumentException.class,
                () -> EquipmentClassCatalog.requireClass(EquipmentClassCatalog.SOURCED_CLASSES, "VEHICLE"));
    }

    @Test
    void declaredBoundariesDoNotOverlap() {
        assertEquals(List.of(), EquipmentClassCatalog.boundaryOverlaps(EquipmentClassCatalog.SOURCED_CLASSES));
        var pump = EquipmentClassCatalog.requireClass(EquipmentClassCatalog.SOURCED_CLASSES, "PUMP");
        assertTrue(pump.nonLinear());
        // UP/UAR de fabrica nao inventados: correspondencia pendente de conferencia no MCPSB
        assertNull(pump.up());
        assertNull(pump.uar());
    }

    @Test
    void linksPointToAssetsWithoutPatrimonialCode() {
        var links = List.of(
                new AssetLinkRules.AssetLink(AssetLinkRules.LinkKind.GIS_FEATURE, id(501), VALVULA),
                new AssetLinkRules.AssetLink(AssetLinkRules.LinkKind.METER, id(600), id(8)));
        // hidrante id(8) existe; o segundo link aponta para ele como ativo de medidor sintetico
        assertEquals(List.of(), AssetLinkRules.validate(links, base()));
        var bad = List.of(new AssetLinkRules.AssetLink(
                AssetLinkRules.LinkKind.GIS_FEATURE, id(700), id(999)));
        assertEquals(1, AssetLinkRules.validate(bad, base()).size());
    }

    @Test
    void patrimonialCodeInFeatureAttributesIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> AssetLinkRules.rejectPatrimonialCodeInFeatureAttributes(Map.of("cib", "123")));
        assertThrows(IllegalArgumentException.class,
                () -> AssetLinkRules.rejectPatrimonialCodeInFeatureAttributes(Map.of("patrimonial_code", "X")));
        // atributos comuns da feicao passam
        AssetLinkRules.rejectPatrimonialCodeInFeatureAttributes(Map.of("material", "PVC", "dn", "110"));
    }
}
