package br.com.ziipsan.intelligence.gis.qgis;

import br.com.ziipsan.intelligence.gis.qgis.GisQgzProjectFactory.FieldSpec;
import br.com.ziipsan.intelligence.gis.qgis.GisQgzProjectFactory.LayerSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GIS-10 (ED-55): projeto .qgz gerado (§7.1 item 4). */
class GisQgzProjectFactoryTest {

    private static LayerSpec layer() {
        return new LayerSpec("qgis_arc", "Agua - arcos", "Agua",
                "map-water-main", 1.5, List.of(),
                List.of(new FieldSpec("material", "Material",
                        List.of("PVC", "FERRO FUNDIDO")),
                        new FieldSpec("code", "Codigo", List.of())),
                0);
    }

    @Test
    @DisplayName(".qgz e um ZIP com project.qgs apontando para a view, sem senha")
    void qgzIsZipWithProject() throws Exception {
        byte[] qgz = GisQgzProjectFactory.projectQgz(
                "Tenant X", "ziipsan-qgis", "auth1", List.of(layer()));
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(qgz))) {
            assertEquals("project.qgs", zip.getNextEntry().getName());
            String xml = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(xml.contains("qgis_arc"));
            assertTrue(xml.contains("service='ziipsan-qgis'"));
            assertTrue(xml.contains("authcfg=auth1"));
            assertFalse(xml.contains("password"), "projeto nunca carrega senha");
        }
    }

    @Test
    @DisplayName("formulario: lista de valores vira ValueRelation; texto vira TextEdit")
    void formWidgetsFromFields() {
        String xml = GisQgzProjectFactory.projectXml(
                "T", "svc", "a1", List.of(layer()));
        assertTrue(xml.contains("widget=\"ValueRelation\""));
        assertTrue(xml.contains("FERRO FUNDIDO"));
        assertTrue(xml.contains("widget=\"TextEdit\""));
        assertTrue(xml.contains("map-water-main"));
    }

    @Test
    @DisplayName("camada fora das views ou sem estilo da fonte unica e recusada")
    void layerOutsideViewsOrWithoutStyleIsRefused() {
        LayerSpec badView = new LayerSpec("gis_arc", "x", "g", "tok",
                1.0, List.of(), List.of(), 0);
        assertThrows(IllegalArgumentException.class, () ->
                GisQgzProjectFactory.projectXml("T", "s", "a", List.of(badView)));
        LayerSpec noStyle = new LayerSpec("qgis_arc", "x", "g", " ",
                1.0, List.of(), List.of(), 0);
        assertThrows(IllegalArgumentException.class, () ->
                GisQgzProjectFactory.projectXml("T", "s", "a", List.of(noStyle)));
    }

    @Test
    @DisplayName("pg_service.conf: verify-full e role individual, sem senha")
    void pgServiceConfHasNoPassword() {
        String role = GisQgisRules.roleNameFor(
                "11111111-2222-3333-4444-555555555555",
                "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        String conf = GisQgzProjectFactory.pgServiceConf("ziipsan-qgis",
                "db.local", 5433, "ziipsan", role);
        assertTrue(conf.contains("sslmode=verify-full"));
        assertTrue(conf.contains("user=" + role));
        assertFalse(conf.contains("password"));
        assertThrows(IllegalArgumentException.class, () ->
                GisQgzProjectFactory.pgServiceConf("s", "h", 1, "d", "postgres"));
    }
}
