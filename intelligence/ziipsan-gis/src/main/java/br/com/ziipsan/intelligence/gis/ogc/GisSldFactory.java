package br.com.ziipsan.intelligence.gis.ogc;

import br.com.ziipsan.intelligence.gis.export.GisMgbMetadata;
import br.com.ziipsan.intelligence.gis.publication.GisStyleRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SLD 1.0 do WMS/GeoServer gerado da MESMA fonte única de estilos
 * (gis_style via GisStyleRules, §10.2/§7.2 item 4): uma regra por
 * ciclo de vida, com filtro em {@code lifecycle_status}, traço com
 * a cor do token resolvida para hex pela paleta fornecida pelo
 * chamador (os hex dos tokens {@code map-*} vivem na paleta do
 * produto — token sem hex é ERRO, nunca uma cor inventada aqui),
 * espessura e padrão de tracejado do estilo resolvido. A assinatura
 * extraível do SLD permite ao teste comparar regras e cores das
 * três saídas (MapLibre, QGIS e SLD) a partir da mesma resolução.
 *
 * <p>Correções ED-67 (revisão independente): D2 — {@link #signatures}
 * ignora pedaços que não são regra (o preâmbulo com o {@code <Name>}
 * da {@code NamedLayer} quebrava a extração com NPE sobre qualquer
 * SLD gerado pelo próprio {@link #generate}); D15b — além do
 * {@code LineSymbolizer} (sobrecarga original, linhas), há geração
 * com {@code PointSymbolizer} e {@code PolygonSymbolizer} conforme
 * o tipo de geometria da camada.
 */
public final class GisSldFactory {

    public record StyleSignature(String lifecycleStatus, String colorHex, double lineWidth, List<Double> dash) {}

    private GisSldFactory(){}

    /** Sobrecarga original: camadas de LINHA (LineSymbolizer). */
    public static String generate(String layerKey, List<Map.Entry<String, GisStyleRules.ResolvedStyle>> byLifecycle,
                                  Map<String,String> tokenHex) {
        return generate(layerKey, byLifecycle, tokenHex, "LINESTRING");
    }

    /**
     * Gera o SLD com o simbolizador do tipo de geometria da camada:
     * {@code POINT} → PointSymbolizer (marca circular preenchida com
     * o hex; tamanho = espessura resolvida), {@code POLYGON} →
     * PolygonSymbolizer (preenchimento + traço), demais →
     * LineSymbolizer.
     */
    public static String generate(String layerKey, List<Map.Entry<String, GisStyleRules.ResolvedStyle>> byLifecycle,
                                  Map<String,String> tokenHex, String geometryType) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<StyledLayerDescriptor version=\"1.0.0\" xmlns=\"http://www.opengis.net/sld\" xmlns:ogc=\"http://www.opengis.net/ogc\">");
        sb.append("<NamedLayer><Name>").append(GisMgbMetadata.esc(layerKey)).append("</Name><UserStyle><FeatureTypeStyle>");
        for (var e : byLifecycle) {
            GisStyleRules.ResolvedStyle s = e.getValue();
            String hex = tokenHex.get(s.colorToken());
            if (hex == null) throw new IllegalArgumentException(
                "token sem hex na paleta fornecida — SLD não inventa cor: " + s.colorToken());
            sb.append("<Rule><Name>").append(GisMgbMetadata.esc(e.getKey())).append("</Name>");
            sb.append("<ogc:Filter><ogc:PropertyIsEqualTo><ogc:PropertyName>lifecycle_status</ogc:PropertyName><ogc:Literal>")
              .append(GisMgbMetadata.esc(e.getKey())).append("</ogc:Literal></ogc:PropertyIsEqualTo></ogc:Filter>");
            switch (geometryType == null ? "LINESTRING" : geometryType) {
                case "POINT" -> {
                    sb.append("<PointSymbolizer><Graphic><Mark><WellKnownName>circle</WellKnownName>");
                    sb.append("<Fill><CssParameter name=\"fill\">").append(GisMgbMetadata.esc(hex)).append("</CssParameter></Fill>");
                    sb.append("</Mark><Size>").append(s.lineWidth()).append("</Size></Graphic></PointSymbolizer>");
                }
                case "POLYGON" -> {
                    sb.append("<PolygonSymbolizer>");
                    sb.append("<Fill><CssParameter name=\"fill\">").append(GisMgbMetadata.esc(hex)).append("</CssParameter></Fill>");
                    sb.append("<Stroke>");
                    sb.append("<CssParameter name=\"stroke\">").append(GisMgbMetadata.esc(hex)).append("</CssParameter>");
                    sb.append("<CssParameter name=\"stroke-width\">").append(s.lineWidth()).append("</CssParameter>");
                    appendDash(sb, s);
                    sb.append("</Stroke></PolygonSymbolizer>");
                }
                default -> {
                    sb.append("<LineSymbolizer><Stroke>");
                    sb.append("<CssParameter name=\"stroke\">").append(GisMgbMetadata.esc(hex)).append("</CssParameter>");
                    sb.append("<CssParameter name=\"stroke-width\">").append(s.lineWidth()).append("</CssParameter>");
                    appendDash(sb, s);
                    sb.append("</Stroke></LineSymbolizer>");
                }
            }
            sb.append("</Rule>");
        }
        return sb.append("</FeatureTypeStyle></UserStyle></NamedLayer></StyledLayerDescriptor>\n").toString();
    }

    private static void appendDash(StringBuilder sb, GisStyleRules.ResolvedStyle s) {
        if (!s.lineDash().isEmpty()) {
            List<String> d = new ArrayList<>();
            s.lineDash().forEach(v -> d.add(String.valueOf(v)));
            sb.append("<CssParameter name=\"stroke-dasharray\">").append(String.join(" ", d)).append("</CssParameter>");
        }
    }

    /**
     * Assinaturas (ciclo, hex, espessura, tracejado) extraídas de um SLD gerado — base do teste das três saídas.
     * Correção D2: pedaços sem contexto de regra (preâmbulo antes do primeiro
     * {@code <Rule>}, que contém o {@code <Name>} da NamedLayer mas nenhum
     * CssParameter de traço/preenchimento) são IGNORADOS, nunca parseados.
     * Em PointSymbolizer, a "espessura" da assinatura é o {@code <Size>} da marca
     * e a cor vem do {@code fill}.
     */
    public static List<StyleSignature> signatures(String sld) {
        List<StyleSignature> out = new ArrayList<>();
        for (String rule : sld.split("<Rule>")) {
            if (!rule.contains("<Name>")) continue;
            boolean hasStroke = rule.contains("<CssParameter name=\"stroke-width\">");
            boolean hasFill = rule.contains("<CssParameter name=\"fill\">");
            boolean hasSize = rule.contains("<Size>");
            if (!hasStroke && !hasFill && !hasSize) continue; // preâmbulo/NamedLayer — não é regra (D2)
            String name = between(rule, "<Name>", "</Name>");
            String hex = between(rule, "<CssParameter name=\"stroke\">", "</CssParameter>");
            if (hex == null) hex = between(rule, "<CssParameter name=\"fill\">", "</CssParameter>");
            String widthText = between(rule, "<CssParameter name=\"stroke-width\">", "</CssParameter>");
            if (widthText == null) widthText = between(rule, "<Size>", "</Size>");
            if (name == null || hex == null || widthText == null) continue;
            double width = Double.parseDouble(widthText);
            List<Double> dash = new ArrayList<>();
            String d = between(rule, "<CssParameter name=\"stroke-dasharray\">", "</CssParameter>");
            if (d != null) for (String p : d.split(" ")) dash.add(Double.parseDouble(p));
            out.add(new StyleSignature(name, hex, width, dash));
        }
        return out;
    }

    private static String between(String s, String a, String b) {
        int i = s.indexOf(a); if (i < 0) return null;
        int j = s.indexOf(b, i + a.length()); if (j < 0) return null;
        return s.substring(i + a.length(), j);
    }
}
