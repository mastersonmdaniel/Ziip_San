package br.com.ziipsan.intelligence.gis.publication;

import br.com.ziipsan.intelligence.gis.publication.GisStyleRules.ResolvedStyle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gera o documento de estilo MapLibre da GIS-06 a partir da fonte
 * única {@code gis_style} (§10.2): dela saem este estilo web, o QML
 * do QGIS (GIS-10) e o SLD do WMS servidor (GIS-17) — aqui só o
 * MapLibre, que é o aceite desta tarefa.
 *
 * <p>Decisões de geração:
 * <ul>
 *   <li>uma fonte vetorial por camada MVT (a rota de tiles é por
 *       camada), com {@code promoteId} = id para a seleção por
 *       {@code feature-state} (§10.2);</li>
 *   <li>uma camada de traço POR ESTADO do ciclo de vida, com filtro
 *       — o {@code line-dasharray} do MapLibre não aceita expressão
 *       por feição, então o padrão de traço por estado (§10.2) só é
 *       fiel com camadas separadas; a COR usa o token resolvido do
 *       estado;</li>
 *   <li>override POR TIPO (§10.2): quando a resolução por tipo do
 *       serviço ({@code styleByTypeAndLifecycle}) diverge do estilo
 *       do domínio para um estado, a cor sai como expressão
 *       {@code match} sobre o atributo {@code type_code} do tile e
 *       o ícone como {@code match} sobre o {@code iconKey} do tipo
 *       — o override gravado na tela passa a ter efeito real no
 *       documento (correção pós-revisão, achado A2). Traço e
 *       espessura NÃO são expressáveis por tipo numa camada só
 *       (dasharray não é data-driven; a espessura é a expressão de
 *       DN abaixo), então override por tipo de traço/espessura
 *       vale para QGIS/SLD e, na web, o que muda por tipo é cor e
 *       ícone — limite declarado, não silêncio;</li>
 *   <li>cores como marcadores {@code "token:map-*"}: quem resolve
 *       para a cor concreta do tema claro/escuro é o frontend, a
 *       partir da paleta do ADR-0017 (palettes.css) — os hex não
 *       vivem no banco nem nesta classe;</li>
 *   <li>espessura de arco por DN (expressão {@code interpolate}
 *       sobre o atributo {@code dn} do tile, §10.2 "espessura por
 *       classe de DN"), ancorada na espessura resolvida do estado;</li>
 *   <li>halo de precisão: camada subjacente só para
 *       {@code accuracy_status = OUT_OF_TARGET}, emitida quando a
 *       resolução COM accuracy ({@code haloByLifecycle}, resolvida
 *       pelo serviço com {@code accuracyStatus = OUT_OF_TARGET})
 *       pede halo — antes da correção A2 o serviço resolvia sem
 *       accuracy e o halo nunca era emitido;</li>
 *   <li>ícone por tipo: {@code icon-image} =
 *       {@code gis:<type_code>} — o sprite publicado resolve a chave
 *       para o símbolo da biblioteca da P1.30 (ADR-0036);</li>
 *   <li>rótulos: camada de símbolo por camada de dados, campo e zoom
 *       mínimo do estilo resolvido (§10.3: rótulos por camada);</li>
 *   <li>mapas base (correção pós-revisão, achado A4): UMA camada
 *       raster POR mapa base com tiles, visível só a marcada
 *       {@code isDefault} (sem nenhuma default, a primeira da
 *       lista) — o seletor da tela alterna a visibilidade das
 *       camadas {@code basemap-<key>}, que agora existem para
 *       todos os mapas base; fontes WMS do tenant como raster
 *       acima do base (§7.2 cliente WMS);</li>
 *   <li>sprite/glyphs (correção pós-revisão, achado A3): o
 *       documento NÃO declara {@code sprite} nem {@code glyphs}
 *       enquanto a arte da P1.30 e a fonte de glifos da P0.36 não
 *       existirem — declarar apontava para rota inexistente/format
 *       inválido e o MapLibre tentava buscar e falhava. O manifesto
 *       de símbolos servido em {@code /gis/map/symbols/sprite} é
 *       inventário (ver GisStyleService), não descritor de sprite;
 *       quando a P1.30 entregar a arte, o documento volta a
 *       declarar o sprite na MESMA rota que o controller serve.</li>
 * </ul>
 * Classe pura: entra o modelo resolvido, sai o mapa do documento —
 * a serialização é da classe {@code Json} do core, no serviço.
 */
public final class GisMapLibreStyleFactory {

    public enum GeometryKind { LINE, POINT, POLYGON, ANY }

    public record LayerDef(String key, String label, boolean personal,
                           int tileMinZoom, GeometryKind geometryKind,
                           Map<String, ResolvedStyle> styleByLifecycle,
                           Map<String, ResolvedStyle> haloByLifecycle,
                           Map<String, Map<String, ResolvedStyle>> styleByTypeAndLifecycle) {

        /** Compatível: camada sem resolução por accuracy nem por tipo. */
        public LayerDef(String key, String label, boolean personal,
                        int tileMinZoom, GeometryKind geometryKind,
                        Map<String, ResolvedStyle> styleByLifecycle) {
            this(key, label, personal, tileMinZoom, geometryKind,
                    styleByLifecycle, Map.of(), Map.of());
        }
    }

    public record BasemapDef(String key, String label, String tilesUrl,
                             String styleUrl, String attribution, boolean isDefault) {
    }

    public record WmsDef(String id, String label, String url, String layers,
                         String version, String format, boolean transparent) {
    }

    private GisMapLibreStyleFactory() {
    }

    public static Map<String, Object> buildStyle(String tilesBaseUrl, String theme,
                                                 List<LayerDef> layers,
                                                 List<BasemapDef> basemaps,
                                                 List<WmsDef> wmsSources) {
        Map<String, Object> style = new LinkedHashMap<>();
        style.put("version", 8);
        style.put("name", "ZiipSan GIS — " + theme);
        // Sem "sprite"/"glyphs" de propósito (A3): ver o javadoc da classe.

        Map<String, Object> sources = new LinkedHashMap<>();
        for (BasemapDef basemap : basemaps) {
            if (basemap.tilesUrl() != null) {
                sources.put("basemap-" + basemap.key(), rasterSource(
                        List.of(basemap.tilesUrl()), basemap.attribution()));
            }
        }
        for (WmsDef wms : wmsSources) {
            sources.put("wms-" + wms.id(), rasterSource(
                    List.of(wmsTileUrl(wms)), "WMS: " + wms.label()));
        }
        for (LayerDef layer : layers) {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("type", "vector");
            source.put("tiles", List.of(tilesBaseUrl + "/gis/tiles/" + layer.key()
                    + "/{z}/{x}/{y}.mvt"));
            source.put("minzoom", layer.tileMinZoom());
            source.put("maxzoom", 22);
            source.put("promoteId", "id");
            sources.put("gis-" + layer.key(), source);
        }
        style.put("sources", sources);

        List<Object> styleLayers = new ArrayList<>();
        // Uma camada raster por mapa base; visível só a default (A4).
        BasemapDef defaultBasemap = basemaps.stream()
                .filter(b -> b.tilesUrl() != null && b.isDefault())
                .findFirst()
                .orElseGet(() -> basemaps.stream()
                        .filter(b -> b.tilesUrl() != null).findFirst().orElse(null));
        for (BasemapDef basemap : basemaps) {
            if (basemap.tilesUrl() == null) {
                continue;
            }
            Map<String, Object> layout = new LinkedHashMap<>();
            layout.put("visibility",
                    basemap == defaultBasemap ? "visible" : "none");
            styleLayers.add(layerDoc("basemap-" + basemap.key(), "raster",
                    "basemap-" + basemap.key(), null, null, layout,
                    Map.of("raster-opacity", 1)));
        }
        for (WmsDef wms : wmsSources) {
            styleLayers.add(layerDoc("wms-" + wms.id(), "raster",
                    "wms-" + wms.id(), null, Map.of(),
                    Map.of("raster-opacity", 0.85)));
        }
        for (LayerDef layer : layers) {
            styleLayers.addAll(dataLayers(layer));
        }
        style.put("layers", styleLayers);
        return style;
    }

    private static List<Object> dataLayers(LayerDef layer) {
        List<Object> out = new ArrayList<>();
        String source = "gis-" + layer.key();
        for (Map.Entry<String, ResolvedStyle> e : layer.styleByLifecycle().entrySet()) {
            String lifecycle = e.getKey();
            ResolvedStyle resolved = e.getValue();
            Object filter = List.of("==", List.of("get", "lifecycle_status"), lifecycle);
            if (layer.geometryKind() == GeometryKind.LINE
                    || layer.geometryKind() == GeometryKind.ANY) {
                ResolvedStyle haloResolved = layer.haloByLifecycle()
                        .getOrDefault(lifecycle, resolved);
                if (haloResolved.halo()) {
                    Map<String, Object> haloPaint = new LinkedHashMap<>();
                    haloPaint.put("line-color", "token:" + haloResolved.colorToken());
                    haloPaint.put("line-width", haloResolved.lineWidth() * 3.0);
                    haloPaint.put("line-opacity", 0.35);
                    out.add(layerDoc(layer.key() + "-halo-" + lifecycle.toLowerCase(),
                            "line", source, layer.key(),
                            List.of("all", filter,
                                    List.of("==", List.of("get", "accuracy_status"),
                                            "OUT_OF_TARGET")),
                            haloPaint));
                }
                Map<String, Object> paint = new LinkedHashMap<>();
                paint.put("line-color", typeColorExpression(layer, lifecycle, resolved));
                paint.put("line-width", dnWidthExpression(resolved.lineWidth()));
                if (!resolved.lineDash().isEmpty()) {
                    paint.put("line-dasharray", resolved.lineDash());
                }
                Map<String, Object> layout = new LinkedHashMap<>();
                layout.put("line-cap", "round");
                layout.put("line-join", "round");
                out.add(layerDoc(layer.key() + "-" + lifecycle.toLowerCase(),
                        "line", source, layer.key(), filter, layout, paint));
            }
            if (layer.geometryKind() == GeometryKind.POINT
                    || layer.geometryKind() == GeometryKind.ANY) {
                Map<String, Object> layout = new LinkedHashMap<>();
                layout.put("icon-image", typeIconExpression(layer, lifecycle));
                layout.put("icon-allow-overlap", false);
                layout.put("icon-size", 1.0);
                out.add(layerDoc(layer.key() + "-symbol-" + lifecycle.toLowerCase(),
                        "symbol", source, layer.key(), filter, layout, Map.of()));
            }
            if (layer.geometryKind() == GeometryKind.POLYGON) {
                Object fillColor = typeColorExpression(layer, lifecycle, resolved);
                Map<String, Object> paint = new LinkedHashMap<>();
                paint.put("fill-color", fillColor);
                paint.put("fill-opacity", 0.18);
                paint.put("fill-outline-color", fillColor);
                out.add(layerDoc(layer.key() + "-" + lifecycle.toLowerCase(),
                        "fill", source, layer.key(), filter, Map.of(), paint));
            }
        }
        // Rótulo da camada (campo/zoom do estilo IN_SERVICE resolvido).
        ResolvedStyle labelStyle = layer.styleByLifecycle().get("IN_SERVICE");
        if (labelStyle != null) {
            Map<String, Object> layout = new LinkedHashMap<>();
            layout.put("text-field", List.of("get", labelStyle.labelField()));
            layout.put("text-size", 11);
            layout.put("text-allow-overlap", false);
            Map<String, Object> paint = new LinkedHashMap<>();
            paint.put("text-color", "token:" + labelStyle.colorToken());
            paint.put("text-halo-color", "token:map-label-halo");
            paint.put("text-halo-width", 1.2);
            Map<String, Object> label = layerDoc(layer.key() + "-labels",
                    "symbol", source, layer.key(), null, layout, paint);
            label.put("minzoom", labelStyle.labelMinZoom());
            out.add(label);
        }
        return out;
    }

    /**
     * Cor da camada para um estado: o token resolvido do domínio,
     * ou expressão {@code match} por {@code type_code} quando algum
     * tipo da camada tem cor resolvida diferente (override por tipo
     * da §10.2 com efeito real — A2). Sem divergência, devolve a
     * string {@code token:map-*} de sempre.
     */
    static Object typeColorExpression(LayerDef layer, String lifecycle,
                                      ResolvedStyle fallback) {
        List<Object> expression = new ArrayList<>();
        expression.add("match");
        expression.add(List.of("get", "type_code"));
        boolean any = false;
        for (Map.Entry<String, Map<String, ResolvedStyle>> type
                : layer.styleByTypeAndLifecycle().entrySet()) {
            ResolvedStyle byType = type.getValue().get(lifecycle);
            if (byType != null && !byType.colorToken().equals(fallback.colorToken())) {
                expression.add(type.getKey());
                expression.add("token:" + byType.colorToken());
                any = true;
            }
        }
        if (!any) {
            return "token:" + fallback.colorToken();
        }
        expression.add("token:" + fallback.colorToken());
        return List.copyOf(expression);
    }

    /**
     * Ícone da camada: {@code gis:<type_code>} por padrão, com
     * {@code match} por {@code type_code} para os tipos cujo estilo
     * resolvido declara {@code iconKey} próprio (escolha de símbolo
     * da biblioteca por tipo — A2).
     */
    static Object typeIconExpression(LayerDef layer, String lifecycle) {
        Object byTypeCode = List.of("concat", "gis:", List.of("get", "type_code"));
        List<Object> expression = new ArrayList<>();
        expression.add("match");
        expression.add(List.of("get", "type_code"));
        boolean any = false;
        for (Map.Entry<String, Map<String, ResolvedStyle>> type
                : layer.styleByTypeAndLifecycle().entrySet()) {
            ResolvedStyle byType = type.getValue().get(lifecycle);
            if (byType != null && byType.iconKey() != null) {
                expression.add(type.getKey());
                expression.add(byType.iconKey());
                any = true;
            }
        }
        if (!any) {
            return byTypeCode;
        }
        expression.add(byTypeCode);
        return List.copyOf(expression);
    }

    /** Espessura por classe de DN (§10.2), ancorada na resolvida. */
    static Object dnWidthExpression(double baseWidth) {
        return List.of("interpolate", List.of("linear"),
                List.of("coalesce", List.of("get", "dn"), 0),
                0, baseWidth * 0.75,
                100, baseWidth,
                300, baseWidth * 2.0);
    }

    /** URL de tile WMS 1.3.0/1.1.1 para fonte raster do MapLibre. */
    public static String wmsTileUrl(WmsDef wms) {
        String separator = wms.url().contains("?") ? "&" : "?";
        return wms.url() + separator
                + "SERVICE=WMS&VERSION=" + wms.version()
                + "&REQUEST=GetMap&LAYERS=" + wms.layers()
                + "&FORMAT=" + wms.format()
                + "&TRANSPARENT=" + (wms.transparent() ? "true" : "false")
                + "&WIDTH=256&HEIGHT=256&CRS=EPSG:3857&STYLES="
                + "&BBOX={bbox-epsg-3857}";
    }

    private static Map<String, Object> rasterSource(List<String> tiles, String attribution) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("type", "raster");
        source.put("tiles", tiles);
        source.put("tileSize", 256);
        if (attribution != null) {
            source.put("attribution", attribution);
        }
        return source;
    }

    private static Map<String, Object> layerDoc(String id, String type, String source,
                                                String sourceLayer, Object filter,
                                                Map<String, Object> layout,
                                                Map<String, Object> paint) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", id);
        doc.put("type", type);
        doc.put("source", source);
        if (sourceLayer != null) {
            doc.put("source-layer", sourceLayer);
        }
        if (filter != null) {
            doc.put("filter", filter);
        }
        if (!layout.isEmpty()) {
            doc.put("layout", layout);
        }
        if (!paint.isEmpty()) {
            doc.put("paint", paint);
        }
        return doc;
    }

    private static Map<String, Object> layerDoc(String id, String type, String source,
                                                String sourceLayer, Object filter,
                                                Map<String, Object> paint) {
        return layerDoc(id, type, source, sourceLayer, filter, Map.of(), paint);
    }
}
