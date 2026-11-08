package br.com.ziipsan.intelligence.gis.ogc;

import br.com.ziipsan.core.json.Json;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Provisionamento do GeoServer pelo produto (§7.2 itens 1/2/5;
 * exceção do ADR-0010): workspace por tenant, store PostGIS com a
 * role SOMENTE LEITURA do tenant ({@code gis_ro_<tenant>}), camadas
 * das views de publicação e estilos SLD — tudo gerado e enviado
 * pela API REST, sem configuração manual. A política é validada no
 * construtor: versão FIXADA (valor vem da decisão de implantação —
 * esta entrega não inventa um número de versão), sem porta
 * publicada, sem console administrativo exposto e WFS-T/edição
 * desligados. A senha da role NÃO trafega neste código: o payload
 * referencia o segredo pelo nome e a integração o resolve no cofre.
 *
 * <p>Correções ED-67 (revisão independente):
 * <ul>
 *   <li><b>D11a</b> — {@link #provision} e {@link #refresh} EXIGEM a
 *       {@link GeoServerPolicy} (antes a policy era decorativa e
 *       nunca era passada ao provisionamento);</li>
 *   <li><b>D11b</b> — o payload do store traz {@code host} e
 *       {@code port} da conexão PostGIS (sem eles o store real não
 *       conecta); os valores vêm da implantação, nunca presumidos;</li>
 *   <li><b>D11c</b> — o SLD enviado é ASSOCIADO como estilo padrão
 *       da camada (PUT em {@code /layers/<camada>});</li>
 *   <li><b>D11d</b> — {@link #refresh} é o caminho de ATUALIZAÇÃO a
 *       cada mudança de estilo ou camada (§7.2 item 5): PUT no
 *       store, nas featuretypes, nos estilos e na associação.</li>
 *   <li><b>D15a</b> — convenção ÚNICA de nomes: a view de publicação
 *       de uma camada é {@code pub_<camada>} e a view agregada é
 *       {@code pub_<camada>_agg}; a chave da camada agregada é
 *       {@code <camada>_agg} — a mesma que a guarda WMS devolve
 *       ({@link GisWmsProxyGuard}).</li>
 * </ul>
 */
public final class GisGeoServerProvisioning {

    public record GeoServerPolicy(String pinnedVersion, boolean publishPort,
                                  boolean adminConsoleExposed, boolean wfsTransactionEnabled) {
        public GeoServerPolicy {
            if (pinnedVersion == null || pinnedVersion.isBlank())
                throw new IllegalArgumentException("GeoServer exige versão fixada (§7.2 item 5) — informe a versão decidida na implantação");
            if (publishPort) throw new IllegalArgumentException("GeoServer sem porta publicada (§7.2 item 1)");
            if (adminConsoleExposed) throw new IllegalArgumentException("console administrativo do GeoServer não pode ser exposto (§7.2 item 1)");
            if (wfsTransactionEnabled) throw new IllegalArgumentException("WFS-T/edição do GeoServer ficam desligados (§7.2 item 1)");
        }
    }

    /** Porta REST do GeoServer (só a api fala com ele, como usuário de serviço). */
    public interface GeoServerRestPort {
        void post(String path, String jsonBody);
        void put(String path, String body, String contentType);
    }

    private GisGeoServerProvisioning(){}

    public static String workspaceName(UUID tenantId){ return "tenant-" + tenantId; }
    public static String storeName(UUID tenantId){ return "postgis-" + tenantId; }
    public static String readOnlyRole(UUID tenantId){ return "gis_ro_" + tenantId.toString().replace("-",""); }

    /** Convenção D15a: view de publicação da camada (nativeName da featuretype). */
    public static String viewName(String layerKey){ return "pub_" + layerKey.replace('-','_'); }
    /** Convenção D15a: chave da camada agregada (a mesma devolvida pela guarda WMS). */
    public static String aggregatedLayerKey(String layerKey){ return layerKey + "_agg"; }
    /** Convenção D15a: view agregada da camada pessoal. */
    public static String aggregatedViewName(String layerKey){ return viewName(layerKey) + "_agg"; }

    public static String workspaceJson(UUID tenantId){
        return Json.write(Map.of("workspace", Map.of("name", workspaceName(tenantId))));
    }

    public static String dataStoreJson(UUID tenantId, String host, int port, String database, String schema, String secretRef){
        return Json.write(Map.of("dataStore", Map.of(
            "name", storeName(tenantId), "type","PostGIS", "enabled", true,
            "connectionParameters", Map.of("entry", List.of(
                Map.of("@key","dbtype","$","postgis"),
                Map.of("@key","host","$",host),
                Map.of("@key","port","$",String.valueOf(port)),
                Map.of("@key","database","$",database),
                Map.of("@key","schema","$",schema),
                Map.of("@key","user","$", readOnlyRole(tenantId)),
                Map.of("@key","passwd","$","secret:" + secretRef))))));
    }

    private static String featureTypeJson(String layerKey){
        return Json.write(Map.of("featureType", Map.of("name", layerKey, "nativeName", viewName(layerKey),
                "enabled", true)));
    }

    /** Associação do SLD como estilo PADRÃO da camada (D11c). */
    private static String defaultStyleJson(UUID tenantId, String layerKey){
        return Json.write(Map.of("layer", Map.of("name", layerKey,
                "defaultStyle", Map.of("name", layerKey, "workspace", workspaceName(tenantId)))));
    }

    private static void requirePolicy(GeoServerPolicy policy){
        if (policy == null) throw new IllegalArgumentException(
            "provisionamento do GeoServer exige a GeoServerPolicy validada (§7.2 item 5) — D11");
    }

    /**
     * Cria workspace, store, uma camada por coleção e o estilo SLD
     * de cada camada (associado como padrão), só pela REST.
     */
    public static void provision(GeoServerRestPort rest, GeoServerPolicy policy, UUID tenantId,
                                 String host, int port, String database, String schema,
                                 String secretRef, List<String> layerKeys, Map<String,String> sldByLayer) {
        requirePolicy(policy);
        String ws = workspaceName(tenantId);
        rest.post("/rest/workspaces", workspaceJson(tenantId));
        rest.post("/rest/workspaces/" + ws + "/datastores", dataStoreJson(tenantId, host, port, database, schema, secretRef));
        for (String layer : layerKeys) {
            rest.post("/rest/workspaces/" + ws + "/datastores/" + storeName(tenantId) + "/featuretypes",
                featureTypeJson(layer));
            String sld = sldByLayer.get(layer);
            if (sld != null) {
                rest.put("/rest/workspaces/" + ws + "/styles/" + layer + ".sld", sld, "application/vnd.ogc.sld+xml");
                rest.put("/rest/workspaces/" + ws + "/layers/" + layer,
                        defaultStyleJson(tenantId, layer), "application/json");
            }
        }
    }

    /**
     * Atualização (§7.2 item 5: "a cada mudança de estilo ou
     * camada"): PUT no store, nas featuretypes e nos estilos, com a
     * associação do estilo padrão refeita. Idempotente na REST do
     * GeoServer (PUT cria-ou-substitui estilo; featuretype é
     * atualizado).
     */
    public static void refresh(GeoServerRestPort rest, GeoServerPolicy policy, UUID tenantId,
                               String host, int port, String database, String schema,
                               String secretRef, List<String> layerKeys, Map<String,String> sldByLayer) {
        requirePolicy(policy);
        String ws = workspaceName(tenantId);
        rest.put("/rest/workspaces/" + ws + "/datastores/" + storeName(tenantId),
                dataStoreJson(tenantId, host, port, database, schema, secretRef), "application/json");
        for (String layer : layerKeys) {
            rest.put("/rest/workspaces/" + ws + "/datastores/" + storeName(tenantId) + "/featuretypes/" + layer,
                    featureTypeJson(layer), "application/json");
            String sld = sldByLayer.get(layer);
            if (sld != null) {
                rest.put("/rest/workspaces/" + ws + "/styles/" + layer + ".sld", sld, "application/vnd.ogc.sld+xml");
                rest.put("/rest/workspaces/" + ws + "/layers/" + layer,
                        defaultStyleJson(tenantId, layer), "application/json");
            }
        }
    }
}
