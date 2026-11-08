package br.com.ziipsan.intelligence.gis.qgis;

import br.com.ziipsan.core.json.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Gerador do projeto QGIS ({@code .qgz}) por tenant e perfil
 * (§7.1 item 4): o {@code .qgz} e um ZIP com o {@code .qgs} (XML do
 * projeto). Camadas apontam para as views {@code qgis_*} via
 * {@code pg_service.conf} SEM senha (a credencial fica no banco de
 * autenticacao do QGIS, referenciada por {@code authcfg}).
 *
 * <p>Simbologia: gerada da MESMA fonte unica da web (gis_style da
 * GIS-06, resolvida pelo chamador e passada em {@link LayerSpec}) —
 * esta fabrica nao inventa cor nem espessura: sem estilo resolvido, a
 * camada e recusada. Formularios: gerados do mapa de campos do tipo
 * (nome, rotulo, lista de valores) — lista vira {@code ValueRelation}/
 * {@code ValueMap} no formulario, como manda o §7.1 item 4.
 *
 * <p>O XML e montado com escape proprio minimo para valores de
 * atributo; JSON (metadados do projeto) sai SEMPRE pela classe Json
 * do core, nunca por concatenacao manual.
 */
public final class GisQgzProjectFactory {

    /** Camada do projeto: view, rotulo, grupo e estilo resolvido. */
    public record LayerSpec(
            String viewName,
            String label,
            String groupLabel,
            String colorToken,
            double lineWidth,
            List<Double> dash,
            List<FieldSpec> fields,
            int minScaleDenominator) {
    }

    /** Campo do formulario gerado do esquema do tipo. */
    public record FieldSpec(
            String name,
            String label,
            List<String> valueList) {
    }

    private GisQgzProjectFactory() {
    }

    /** Conteudo {@code .qgs} (XML) do projeto. */
    public static String projectXml(String projectTitle, String serviceName,
                                    String authCfgId, List<LayerSpec> layers) {
        if (layers == null || layers.isEmpty()) {
            throw new IllegalArgumentException("projeto QGIS sem camadas");
        }
        StringBuilder xml = new StringBuilder();
        xml.append("<!DOCTYPE qgis PUBLIC 'http://mrcc.com/qgis.dtd' 'SYSTEM'>\n");
        xml.append("<qgis version=\"3.34\" projectname=\"")
                .append(esc(projectTitle)).append("\">\n");
        xml.append("  <homePath path=\"\"/>\n");
        xml.append("  <title>").append(esc(projectTitle)).append("</title>\n");
        xml.append("  <layer-tree-group>\n");
        Map<String, List<LayerSpec>> byGroup = new LinkedHashMap<>();
        for (LayerSpec layer : layers) {
            validateLayer(layer);
            byGroup.computeIfAbsent(layer.groupLabel(), k -> new ArrayList<>())
                    .add(layer);
        }
        for (Map.Entry<String, List<LayerSpec>> group : byGroup.entrySet()) {
            xml.append("    <layer-tree-group name=\"")
                    .append(esc(group.getKey())).append("\">\n");
            for (LayerSpec layer : group.getValue()) {
                xml.append("      <layer-tree-layer name=\"")
                        .append(esc(layer.label())).append("\"/>\n");
            }
            xml.append("    </layer-tree-group>\n");
        }
        xml.append("  </layer-tree-group>\n");
        xml.append("  <projectlayers>\n");
        for (LayerSpec layer : layers) {
            appendLayer(xml, layer, serviceName, authCfgId);
        }
        xml.append("  </projectlayers>\n");
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("generator", "ziipsan-gis ED-55 GIS-10");
        meta.put("service", serviceName);
        meta.put("layers", layers.stream().map(LayerSpec::viewName).toList());
        xml.append("  <!-- metadata: ").append(Json.write(meta)).append(" -->\n");
        xml.append("</qgis>\n");
        return xml.toString();
    }

    /** O {@code .qgz}: ZIP com um unico {@code project.qgs}. */
    public static byte[] projectQgz(String projectTitle, String serviceName,
                                    String authCfgId, List<LayerSpec> layers) {
        String xml = projectXml(projectTitle, serviceName, authCfgId, layers);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                zip.putNextEntry(new ZipEntry("project.qgs"));
                zip.write(xml.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao gerar o .qgz", e);
        }
    }

    /**
     * Conteudo do {@code pg_service.conf}: host/porta/banco/servico,
     * sslmode verify-full e o nome da role — SEM senha (§7.1 item 4).
     */
    public static String pgServiceConf(String serviceName, String host,
                                       int port, String dbName, String roleName) {
        GisQgisRules.ensureEndpointPolicy(true, "verify-full", false, true, null);
        if (!GisQgisRules.isQgisRoleName(roleName)) {
            throw new IllegalArgumentException("role invalida para o servico QGIS");
        }
        return "[" + serviceName + "]\nhost=" + host + "\nport=" + port
                + "\ndbname=" + dbName + "\nuser=" + roleName
                + "\nsslmode=verify-full\n";
    }

    private static void validateLayer(LayerSpec layer) {
        if (!GisQgisRules.READ_VIEWS.contains(layer.viewName())
                && !GisQgisRules.EDIT_VIEWS.contains(layer.viewName())) {
            throw new IllegalArgumentException(
                    "camada do projeto QGIS fora das views qgis_*: " + layer.viewName());
        }
        if (layer.colorToken() == null || layer.colorToken().isBlank()) {
            throw new IllegalArgumentException(
                    "camada sem estilo resolvido da fonte unica (GIS-06): "
                            + layer.viewName());
        }
    }

    private static void appendLayer(StringBuilder xml, LayerSpec layer,
                                    String serviceName, String authCfgId) {
        boolean editable = GisQgisRules.EDIT_VIEWS.contains(layer.viewName());
        xml.append("    <maplayer type=\"vector\">\n");
        xml.append("      <layername>").append(esc(layer.label())).append("</layername>\n");
        xml.append("      <datasource>service='").append(esc(serviceName))
                .append("' authcfg=").append(esc(authCfgId))
                .append(" key='id' table=\"").append(esc(layer.viewName()))
                .append("\" (geom) sql=</datasource>\n");
        xml.append("      <provider encoding=\"UTF-8\">postgres</provider>\n");
        xml.append("      <renderer-v2 type=\"singleSymbol\"><symbols><symbol type=\"line\" name=\"0\">")
                .append("<layer class=\"SimpleLine\"><prop k=\"line_color\" v=\"")
                .append(esc(layer.colorToken())).append("\"/><prop k=\"line_width\" v=\"")
                .append(layer.lineWidth()).append("\"/>");
        if (layer.dash() != null && !layer.dash().isEmpty()) {
            xml.append("<prop k=\"line_style\" v=\"dash\"/>");
        }
        xml.append("</layer></symbol></symbols></renderer-v2>\n");
        if (layer.minScaleDenominator() > 0) {
            xml.append("      <minScale>").append(layer.minScaleDenominator())
                    .append("</minScale>\n");
        }
        xml.append("      <editform>").append(editable ? "generated" : "")
                .append("</editform>\n");
        xml.append("      <editorwidgets>\n");
        if (layer.fields() != null) {
            for (FieldSpec field : layer.fields()) {
                xml.append("        <editorwidget name=\"").append(esc(field.name()))
                        .append("\" widget=\"")
                        .append(field.valueList() != null && !field.valueList().isEmpty()
                                ? "ValueRelation" : "TextEdit")
                        .append("\"><label>").append(esc(field.label()))
                        .append("</label>");
                if (field.valueList() != null) {
                    for (String value : field.valueList()) {
                        xml.append("<value>").append(esc(value)).append("</value>");
                    }
                }
                xml.append("</editorwidget>\n");
            }
        }
        xml.append("      </editorwidgets>\n");
        xml.append("    </maplayer>\n");
    }

    private static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
