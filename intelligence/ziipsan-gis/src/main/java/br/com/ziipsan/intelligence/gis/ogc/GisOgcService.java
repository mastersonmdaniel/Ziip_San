package br.com.ziipsan.intelligence.gis.ogc;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.intelligence.gis.export.GisExportFeature;
import br.com.ziipsan.intelligence.gis.export.GisExportGuard;
import br.com.ziipsan.intelligence.gis.export.GisExportProfiles;
import br.com.ziipsan.intelligence.gis.export.GisExportService;
import br.com.ziipsan.intelligence.gis.publication.GisTileLayers;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OGC API – Features na {@code api} (GIS-17, §7.2): landing page,
 * conformance, collections e items sobre a MESMA fonte de leitura
 * dos tiles. Sem acesso anônimo (o controller exige
 * {@code gis:view}); o tenant vem do contexto — uma requisição que
 * declare outro tenant é recusada, e a fonte só é consultada com o
 * tenant do contexto.
 *
 * <p>Correções ED-67 (revisão independente):
 * <ul>
 *   <li><b>D3/D4</b> — as guardas do §12 ({@link GisExportGuard})
 *       valem em TODOS os caminhos de leitura: {@code items} aplica
 *       {@code checkIndividualExport} às feições antes de serializar
 *       (marcador sensível nunca sai; infraestrutura crítica não sai
 *       para audiência externa; feição pessoal individual nunca sai
 *       para audiência externa — só agregados) e {@code item} recebe
 *       a audiência do chamador e aplica a mesma guarda à feição
 *       individual. Antes, {@code items} vazava marcador sensível e
 *       infraestrutura crítica para terceiro e {@code item} devolvia
 *       feição pessoal individual a qualquer audiência.</li>
 *   <li><b>D6</b> — {@link #CONFORMANCE} declara SOMENTE o que está
 *       implementado: Core, GeoJSON e CRS da parte 2. NÃO se declara
 *       queryables/filtering da parte 3, a classe cql2-text nem
 *       oas30 (não há endpoint de descrição da API/OpenAPI): o
 *       filtro implementado é o subconjunto de igualdades com AND de
 *       {@link GisOgcQuery} (fora dele = recusa) e não há endpoint
 *       {@code /queryables}. As respostas de items trazem
 *       {@code numberMatched}/{@code numberReturned}/{@code timeStamp}
 *       e landing/collections trazem os links do Core.</li>
 *   <li><b>D7</b> — para audiência externa, os agregados são os
 *       fornecidos pelo chamador ou, na falta deles, derivados das
 *       feições pessoais carregadas da fonte (célula → contagem), e
 *       o piso é o parâmetro {@code gis.public_aggregation_min_units}
 *       do tenant quando a {@link GisTenantParameterPort} está ligada
 *       — nunca um 5 fixo que ignore a configuração do tenant.</li>
 * </ul>
 *
 * <p>Nota de registro (§12 item 5): a guarda é chamada nas leituras
 * com {@code personalAccessRegistered = true} porque leitura OGC não
 * é exportação — o registro de acesso à leitura é o access log da
 * {@code api} (fiação da integração, pendência P6); a permissão
 * {@code gis.view-personal} continua exigida para camada pessoal no
 * backoffice e a audiência externa nunca recebe indivíduo pessoal.
 */
@Service
public class GisOgcService {

    /**
     * Somente as classes efetivamente implementadas (D6): Core e
     * GeoJSON (parte 1) e CRS (parte 2). Parte 3 (queryables/
     * filtering) e cql2-text NÃO são declaradas — ver javadoc.
     */
    public static final List<String> CONFORMANCE = List.of(
        "http://www.opengis.net/spec/ogcapi-features-1/1.0/conf/core",
        "http://www.opengis.net/spec/ogcapi-features-1/1.0/conf/geojson",
        "http://www.opengis.net/spec/ogcapi-features-2/1.0/conf/crs");

    private final GisExportService.FeatureSource source;
    private GisTenantParameterPort tenantParameters;

    public GisOgcService(GisExportService.FeatureSource source){ this.source = source; }

    /** Liga a leitura do parâmetro de agregação do tenant (D7), quando a integração a fornecer. */
    public void setTenantParameterPort(GisTenantParameterPort tenantParameters){ this.tenantParameters = tenantParameters; }

    private int effectiveParam(UUID tenantId, int requested) {
        if (tenantParameters != null) {
            Integer v = tenantParameters.aggregationMinUnits(tenantId);
            if (v != null) return v;
        }
        return requested;
    }

    public String landingPage() {
        return Json.write(Map.of("title","ZiipSan GIS — OGC API Features","version","1.0",
            "links", List.of(
                Map.of("href","/api/v1/gis/ogc","rel","self","type","application/json"),
                Map.of("href","/api/v1/gis/ogc/conformance","rel","conformance","type","application/json"),
                Map.of("href","/api/v1/gis/ogc/collections","rel","data","type","application/json"))));
    }

    public String conformance(){ return Json.write(Map.of("conformsTo", CONFORMANCE)); }

    public List<Map<String,Object>> collections(boolean canViewPersonal) {
        return GisTileLayers.visibleFor(canViewPersonal).stream().map(l -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id", l.key()); m.put("title", l.key()); m.put("itemType","feature");
            m.put("personal", l.personal());
            m.put("crs", List.of("http://www.opengis.net/def/crs/OGC/1.3/CRS84"));
            m.put("links", List.of(
                Map.of("href","/api/v1/gis/ogc/collections/" + l.key(), "rel","self","type","application/json"),
                Map.of("href","/api/v1/gis/ogc/collections/" + l.key() + "/items", "rel","items","type","application/geo+json")));
            return m;
        }).toList();
    }

    public String collectionsJson(boolean canViewPersonal){
        return Json.write(Map.of("collections", collections(canViewPersonal),
                "links", List.of(Map.of("href","/api/v1/gis/ogc/collections","rel","self","type","application/json"))));
    }

    /**
     * Items de uma coleção. {@code claimedTenant} é o tenant que a
     * requisição porventura declare (workspace/cabeçalho): divergir
     * do tenant do contexto é recusa — isolamento por tenant (§7.2).
     * As guardas do §12 são aplicadas às feições ANTES de serializar
     * (D3): marcador sensível nunca sai, infraestrutura crítica não
     * sai para audiência externa e feição pessoal individual só sai
     * no backoffice com {@code gis.view-personal} — para audiência
     * externa, o caminho pessoal devolve SOMENTE agregados
     * publicáveis (fornecidos ou derivados da fonte, D7).
     */
    public String items(UUID contextTenant, UUID claimedTenant, String collectionId, GisOgcQuery query,
                        boolean canViewPersonal, GisExportGuard.Audience audience,
                        List<GisExportGuard.PersonalAggregate> aggregates, int aggregationParam) {
        if (claimedTenant != null && !claimedTenant.equals(contextTenant))
            throw new SecurityException("requisição declara tenant diverso do contexto — leitura cruzada recusada");
        GisTileLayers layer = GisTileLayers.byKey(collectionId)
                .orElseThrow(() -> new IllegalArgumentException("coleção desconhecida: " + collectionId));
        List<GisExportFeature> all = source.load(contextTenant, collectionId);
        List<GisExportFeature> matched = query == null ? all : all.stream().filter(query.predicate()).toList();
        List<GisExportFeature> out = query == null ? matched : query.apply(all);
        boolean anyPersonal = out.stream().anyMatch(GisExportFeature::personal);
        if (layer.personal() || anyPersonal) {
            if (audience != GisExportGuard.Audience.BACKOFFICE) {
                // As feições NÃO pessoais do mesmo pedido passam pela
                // guarda: marcador sensível/infra crítica recusam o
                // pedido inteiro, como na exportação (D3).
                GisExportGuard.checkIndividualExport(audience,
                        out.stream().filter(f -> !f.personal()).toList(), canViewPersonal, false);
                var published = GisExportGuard.publishableAggregates(
                        aggregates == null || aggregates.isEmpty()
                                ? GisExportService.deriveAggregates(all) : aggregates,
                        effectiveParam(contextTenant, aggregationParam));
                Map<String,Object> fc = new LinkedHashMap<>();
                fc.put("type", "FeatureCollection"); fc.put("collection", collectionId);
                fc.put("aggregated", true); fc.put("aggregates", published); fc.put("features", List.of());
                fc.put("numberMatched", published.size()); fc.put("numberReturned", 0);
                fc.put("timeStamp", Instant.now().toString());
                return Json.write(fc);
            }
            if (!canViewPersonal)
                throw new SecurityException("coleção pessoal exige gis.view-personal");
        }
        // Guarda do §12 sobre TUDO o que será serializado (D3).
        GisExportGuard.checkIndividualExport(audience, out, canViewPersonal, true);
        Map<String,Object> fc = GisExportService.geoJsonMap(out, GisExportProfiles.Profile.GENERIC);
        fc.put("numberMatched", matched.size());
        fc.put("numberReturned", out.size());
        fc.put("timeStamp", Instant.now().toString());
        fc.put("links", List.of(Map.of("href","/api/v1/gis/ogc/collections/" + collectionId + "/items",
                "rel","self","type","application/geo+json")));
        return Json.write(fc);
    }

    /**
     * Item individual de uma coleção — AGORA com a audiência do
     * chamador (D4a). Feição pessoal individual NUNCA é devolvida a
     * audiência externa (a guarda recusa; agregação é resposta de
     * coleção, não de item); marcador sensível nunca sai e
     * infraestrutura crítica não sai para terceiro, como em
     * {@link #items}.
     */
    public String item(UUID contextTenant, UUID claimedTenant, String collectionId, String featureId,
                       boolean canViewPersonal, GisExportGuard.Audience audience) {
        if (claimedTenant != null && !claimedTenant.equals(contextTenant))
            throw new SecurityException("requisição declara tenant diverso do contexto — leitura cruzada recusada");
        GisTileLayers layer = GisTileLayers.byKey(collectionId)
                .orElseThrow(() -> new IllegalArgumentException("coleção desconhecida: " + collectionId));
        if (layer.personal() && audience != GisExportGuard.Audience.BACKOFFICE)
            throw new IllegalArgumentException(
                "camada pessoal fora do backoffice só sai agregada por quadra/setor (§12 item 3) — item individual recusado");
        if (layer.personal() && !canViewPersonal)
            throw new SecurityException("coleção pessoal exige gis.view-personal");
        GisExportFeature found = source.load(contextTenant, collectionId).stream()
                .filter(f -> f.id().equals(featureId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("feição não encontrada nesta coleção/tenant: " + featureId));
        // Guarda do §12 sobre a feição individual (D3/D4a): cobre
        // também feição pessoal em camada não pessoal, marcador
        // sensível e infraestrutura crítica para terceiro.
        GisExportGuard.checkIndividualExport(audience, List.of(found), canViewPersonal,
                audience == GisExportGuard.Audience.BACKOFFICE);
        Map<String,Object> fc = GisExportService.geoJsonMap(List.of(found), GisExportProfiles.Profile.GENERIC);
        fc.put("numberMatched", 1); fc.put("numberReturned", 1);
        fc.put("timeStamp", Instant.now().toString());
        return Json.write(fc);
    }

    static String geoJsonOf(List<GisExportFeature> features) {
        return GisExportService.geoJson(features, GisExportProfiles.Profile.GENERIC);
    }
}
