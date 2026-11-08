package br.com.ziipsan.intelligence.gis.waste;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SG-1340 (parte): camadas de resíduos do perfil genérico. */
class GisWasteImportLayersTest {

    @Test
    void genericProfileCoversAllFeatureTypes() {
        assertTrue(GisWasteImportLayers.coversAllFeatureTypes());
        assertEquals(13, GisWasteImportLayers.GENERIC_PROFILE_LAYERS.size());
    }

    @Test
    void layerTargetsMatchTheCatalog() {
        assertEquals("gis_structure", GisWasteImportLayers.bySourceLayer("ECOPOINT")
                .orElseThrow().featureTable());
        assertEquals("gis_zone", GisWasteImportLayers.bySourceLayer("COLLECTION_SECTOR")
                .orElseThrow().featureTable());
        assertEquals("gis14:ILLEGAL_DUMP", GisWasteImportLayers.bySourceLayer("ILLEGAL_DUMP")
                .orElseThrow().featureTypeCode());
        assertTrue(GisWasteImportLayers.bySourceLayer("A_REDE").isEmpty(),
                "camada de agua e da GIS-07, nao da GIS-14");
    }
}
