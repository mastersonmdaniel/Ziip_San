package br.com.ziipsan.intelligence.gis.publication;

import br.com.ziipsan.intelligence.gis.publication.GisMapLibreStyleFactory.BasemapDef;
import br.com.ziipsan.intelligence.gis.publication.GisMapLibreStyleFactory.GeometryKind;
import br.com.ziipsan.intelligence.gis.publication.GisMapLibreStyleFactory.LayerDef;
import br.com.ziipsan.intelligence.gis.publication.GisMapLibreStyleFactory.WmsDef;
import br.com.ziipsan.intelligence.gis.publication.GisStyleRules.ResolvedStyle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GIS-06 (P3.25): o documento MapLibre gerado da fonte única
 * (§10.2) — fonte por camada com promoteId, traço por estado em
 * camadas filtradas, halo de precisão, rótulo, mapa base e WMS.
 * As cores saem como marcadores token:map-* (a resolução por tema
 * claro/escuro é do frontend, testada lá).
 */
class GisMapLibreStyleFactoryTest {

    private static ResolvedStyle style(String token, List<Double> dash, boolean halo) {
        return new ResolvedStyle(token, 2.0, dash, halo, null, "code", 16);
    }

    private static LayerDef arcs() {
        Map<String, ResolvedStyle> byLifecycle = new LinkedHashMap<>();
        byLifecycle.put("IN_SERVICE", style("map-water", List.of(), true));
        byLifecycle.put("PLANNED", style("map-water", List.of(3.0, 2.0), false));
        byLifecycle.put("UNDER_CONSTRUCTION", style("map-water", List.of(4.0, 2.0, 1.0, 2.0), false));
        byLifecycle.put("ABANDONED", style("map-abandoned", List.of(2.0, 2.0), false));
        return new LayerDef("water-arcs", "Água — arcos", false, 0,
                GeometryKind.LINE, byLifecycle);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> layersOf(Map<String, Object> style) {
        return (List<Map<String, Object>>) style.get("layers");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> layerById(Map<String, Object> style, String id) {
        return layersOf(style).stream().filter(l -> id.equals(l.get("id")))
                .findFirst().orElseThrow(() -> new AssertionError("camada " + id));
    }

    private static Map<String, Object> build() {
        return GisMapLibreStyleFactory.buildStyle("/api/v1", "light",
                List.of(arcs()),
                List.of(new BasemapDef("osm", "OpenStreetMap",
                        "https://tile.openstreetmap.org/{z}/{x}/{y}.png", null,
                        "© OpenStreetMap contributors", true)),
                List.of(new WmsDef("42", "Ortofoto municipal",
                        "https://geo.exemplo.invalid/wms", "orto2024",
                        "1.3.0", "image/png", true)));
    }

    @Test
    @DisplayName("fontes: base raster, WMS e vetorial da camada com promoteId")
    void sources() {
        Map<String, Object> style = build();
        @SuppressWarnings("unchecked")
        Map<String, Object> sources = (Map<String, Object>) style.get("sources");
        assertThat(sources).containsKeys("basemap-osm", "wms-42", "gis-water-arcs");
        @SuppressWarnings("unchecked")
        Map<String, Object> vector = (Map<String, Object>) sources.get("gis-water-arcs");
        assertThat(vector.get("tiles"))
                .isEqualTo(List.of("/api/v1/gis/tiles/water-arcs/{z}/{x}/{y}.mvt"));
        assertThat(vector.get("promoteId")).isEqualTo("id");
    }

    @Test
    @DisplayName("traço por estado: camada filtrada por lifecycle, dash só fora de operação")
    void dashPerLifecycleLayer() {
        Map<String, Object> style = build();
        Map<String, Object> planned = layerById(style, "water-arcs-planned");
        @SuppressWarnings("unchecked")
        Map<String, Object> paint = (Map<String, Object>) planned.get("paint");
        assertThat(paint.get("line-dasharray")).isEqualTo(List.of(3.0, 2.0));
        assertThat(paint.get("line-color")).isEqualTo("token:map-water");
        assertThat(planned.get("filter").toString()).contains("PLANNED");

        Map<String, Object> inService = layerById(style, "water-arcs-in_service");
        @SuppressWarnings("unchecked")
        Map<String, Object> inServicePaint = (Map<String, Object>) inService.get("paint");
        assertThat(inServicePaint).doesNotContainKey("line-dasharray");
    }

    @Test
    @DisplayName("halo só para fora da meta; rótulo com campo e zoom do estilo")
    void haloAndLabels() {
        Map<String, Object> style = build();
        Map<String, Object> halo = layerById(style, "water-arcs-halo-in_service");
        assertThat(halo.get("filter").toString()).contains("OUT_OF_TARGET");
        Map<String, Object> labels = layerById(style, "water-arcs-labels");
        assertThat(labels.get("minzoom")).isEqualTo(16);
    }

    @Test
    @DisplayName("A4: uma camada raster por mapa base, visível só a default (não a primeira)")
    void oneLayerPerBasemapDefaultVisible() {
        Map<String, Object> style = GisMapLibreStyleFactory.buildStyle("/api/v1", "light",
                List.of(arcs()),
                List.of(
                        new BasemapDef("custom", "Ortofoto do tenant",
                                "https://geo.exemplo.invalid/tiles/{z}/{x}/{y}.png", null,
                                "© Fornecedor", false),
                        new BasemapDef("osm", "OpenStreetMap",
                                "https://tile.openstreetmap.org/{z}/{x}/{y}.png", null,
                                "© OpenStreetMap contributors", true)),
                List.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> sources = (Map<String, Object>) style.get("sources");
        assertThat(sources).containsKeys("basemap-custom", "basemap-osm");
        @SuppressWarnings("unchecked")
        Map<String, Object> osmLayout = (Map<String, Object>)
                layerById(style, "basemap-osm").get("layout");
        @SuppressWarnings("unchecked")
        Map<String, Object> customLayout = (Map<String, Object>)
                layerById(style, "basemap-custom").get("layout");
        assertThat(osmLayout.get("visibility")).isEqualTo("visible");
        assertThat(customLayout.get("visibility")).isEqualTo("none");
    }

    @Test
    @DisplayName("A3: documento não declara sprite nem glyphs enquanto a arte não existe")
    void noSpriteOrGlyphsUntilArtExists() {
        Map<String, Object> style = build();
        assertThat(style).doesNotContainKeys("sprite", "glyphs");
    }

    @Test
    @DisplayName("A2 (fábrica): halo vem do mapa de accuracy; cor divergente por tipo vira match")
    void haloAndTypeMatchFromResolvedMaps() {
        Map<String, ResolvedStyle> byLifecycle = new LinkedHashMap<>();
        byLifecycle.put("IN_SERVICE", style("map-water", List.of(), false));
        Map<String, ResolvedStyle> haloByLifecycle = Map.of(
                "IN_SERVICE", style("map-water", List.of(), true));
        Map<String, Map<String, ResolvedStyle>> byType = Map.of(
                "VALVE", Map.of("IN_SERVICE", style("map-sewer", List.of(), false)));
        LayerDef layer = new LayerDef("water-arcs", "Água — arcos", false, 0,
                GeometryKind.LINE, byLifecycle, haloByLifecycle, byType);
        Map<String, Object> style = GisMapLibreStyleFactory.buildStyle(
                "/api/v1", "light", List.of(layer), List.of(), List.of());
        assertThat(layerById(style, "water-arcs-halo-in_service").get("filter").toString())
                .contains("OUT_OF_TARGET");
        @SuppressWarnings("unchecked")
        Map<String, Object> paint = (Map<String, Object>)
                layerById(style, "water-arcs-in_service").get("paint");
        assertThat(paint.get("line-color").toString())
                .contains("match").contains("VALVE").contains("token:map-sewer");
    }

    @Test
    @DisplayName("WMS: URL de tile com BBOX 3857 e camadas da fonte")
    void wmsTileUrl() {
        String url = GisMapLibreStyleFactory.wmsTileUrl(new WmsDef("1", "Orto",
                "https://geo.exemplo.invalid/wms?chave=1", "orto", "1.3.0",
                "image/png", true));
        assertThat(url).startsWith("https://geo.exemplo.invalid/wms?chave=1&SERVICE=WMS")
                .contains("LAYERS=orto").contains("CRS=EPSG:3857")
                .contains("BBOX={bbox-epsg-3857}").contains("TRANSPARENT=true");
    }

    @Test
    @DisplayName("espessura por DN: expressão interpolate sobre o atributo dn")
    void dnWidth() {
        Object expr = GisMapLibreStyleFactory.dnWidthExpression(2.0);
        assertThat(expr.toString()).contains("interpolate").contains("dn");
    }
}
