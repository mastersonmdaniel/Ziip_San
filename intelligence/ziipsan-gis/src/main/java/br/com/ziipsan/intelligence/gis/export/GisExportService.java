package br.com.ziipsan.intelligence.gis.export;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.intelligence.gis.ogc.GisTenantParameterPort;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Exportação da GIS-16 (§9.3/§9.4): filtros do §9.3 expressos no
 * {@link GisExportFilter} (área, domínio, ciclo de vida e "rede na
 * data" — correção D9), guardas do §12 aplicadas ANTES de
 * serializar, metadado MGB em toda exportação e registro do acesso
 * pessoal pela porta da integração (P0.11). Formatos de texto
 * (GeoJSON, CSV, KML, DXF) são serializados aqui; GPKG/SHP são
 * binários de GDAL e saem pela {@link BinaryExportPort} — sem a
 * porta ligada, a chamada FALHA declarando a pendência, em vez de
 * entregar um arquivo falso.
 *
 * <p>Correções ED-67 (revisão independente): D5 — GeoJSON de POINT
 * com a posição única da RFC 7946; D7 — agregados externos derivados
 * das feições pessoais carregadas quando o pedido não os fornece, e
 * o piso de agregação lido do parâmetro do tenant pela
 * {@link GisTenantParameterPort} quando ligada; D15d — no caminho
 * externo, {@code ExportResult.format} declara o conteúdo real
 * (GEOJSON de agregados), não o formato pedido; D15e — feição com
 * marcador sensível é recusada ANTES de qualquer registro no
 * {@code personal_data_access_log} (as demais recusas posteriores ao
 * registro ficam registradas como tentativa — decisão explícita).
 */
@Service
public class GisExportService {

    /** Leitura das feições do tenant (mesma fonte dos tiles/OGC; nunca outro tenant). */
    public interface FeatureSource {
        List<GisExportFeature> load(UUID tenantId, String layerKey);

        /** Leitura com os filtros do §9.3 (D9): a camada recorta na fonte; os demais filtros filtram as feições. */
        default List<GisExportFeature> load(UUID tenantId, String layerKey, GisExportFilter filter) {
            List<GisExportFeature> all = load(tenantId, layerKey);
            return filter == null ? all : all.stream().filter(filter::test).toList();
        }
    }

    /** Escrita binária com GDAL (GPKG 1.4 / SHP zipado), ligada na integração. */
    public interface BinaryExportPort {
        byte[] write(GisExportFormats format, List<GisExportFeature> features,
                     GisExportProfiles.Profile profile);
    }

    /** Registro no personal_data_access_log (P0.11), ligado na integração. */
    public interface PersonalAccessRegistrar {
        void register(UUID tenantId, UUID userId, String layerKey, String purpose);
    }

    public record ExportRequest(GisExportFormats format, String layerKey,
                                GisExportProfiles.Profile profile,
                                GisExportGuard.Audience audience, boolean canViewPersonal,
                                int aggregationMinUnitsParam,
                                List<GisExportGuard.PersonalAggregate> aggregates,
                                GisMgbMetadata.Metadata metadata, String purpose,
                                GisExportFilter filter) {
        /** Construtor sem filtro (pedidos anteriores à correção D9). */
        public ExportRequest(GisExportFormats format, String layerKey,
                             GisExportProfiles.Profile profile,
                             GisExportGuard.Audience audience, boolean canViewPersonal,
                             int aggregationMinUnitsParam,
                             List<GisExportGuard.PersonalAggregate> aggregates,
                             GisMgbMetadata.Metadata metadata, String purpose) {
            this(format, layerKey, profile, audience, canViewPersonal, aggregationMinUnitsParam,
                    aggregates, metadata, purpose, null);
        }
    }

    public record ExportResult(GisExportFormats format, String contentType, String filename,
                               byte[] content, String metadataXml, boolean personalIncluded,
                               boolean profileValidated, List<GisExportGuard.PersonalAggregate> publishedAggregates) {}

    private final FeatureSource source;
    private final BinaryExportPort binaryPort;
    private final PersonalAccessRegistrar registrar;
    private GisTenantParameterPort tenantParameters;

    public GisExportService(FeatureSource source, BinaryExportPort binaryPort,
                            PersonalAccessRegistrar registrar) {
        this.source = source; this.binaryPort = binaryPort; this.registrar = registrar;
    }

    /** Liga a leitura do parâmetro {@code gis.public_aggregation_min_units} do tenant (D7), quando a integração a fornecer. */
    public void setTenantParameterPort(GisTenantParameterPort tenantParameters){ this.tenantParameters = tenantParameters; }

    /** Piso pedido pelo chamador, substituído pelo parâmetro do tenant quando a porta está ligada (D7). */
    private int effectiveParam(UUID tenantId, int requested) {
        if (tenantParameters != null) {
            Integer v = tenantParameters.aggregationMinUnits(tenantId);
            if (v != null) return v;
        }
        return requested;
    }

    /** Agregados derivados das feições pessoais carregadas (célula → contagem), quando o pedido não os fornece (D7). */
    public static List<GisExportGuard.PersonalAggregate> deriveAggregates(List<GisExportFeature> features) {
        Map<String, Long> byCell = new TreeMap<>();
        for (GisExportFeature f : features)
            if (f.personal() && f.cellKey() != null) byCell.merge(f.cellKey(), 1L, Long::sum);
        return byCell.entrySet().stream()
                .map(e -> new GisExportGuard.PersonalAggregate(e.getKey(), e.getValue())).toList();
    }

    public ExportResult export(UUID tenantId, UUID userId, ExportRequest req) {
        if (req.metadata() == null) throw new IllegalArgumentException("toda exportação exige metadado MGB (§9.3)");
        if (req.format() == null) throw new IllegalArgumentException("formato de exportação é obrigatório");
        List<GisExportFeature> features = source.load(tenantId, req.layerKey(), req.filter());
        boolean personal = features.stream().anyMatch(GisExportFeature::personal);
        int param = effectiveParam(tenantId, req.aggregationMinUnitsParam());
        // Audiência externa com camada pessoal: só agregados publicáveis, nenhum indivíduo.
        if (personal && req.audience() != GisExportGuard.Audience.BACKOFFICE) {
            var published = GisExportGuard.publishableAggregates(
                    req.aggregates() == null || req.aggregates().isEmpty()
                            ? deriveAggregates(features) : req.aggregates(), param);
            GisExportGuard.checkIndividualExport(req.audience(),
                    features.stream().filter(f -> !f.personal()).toList(),
                    req.canViewPersonal(), false);
            String meta = GisMgbMetadata.toXml(req.metadata());
            // D15d: o conteúdo é o JSON de agregados — o formato declarado é o do conteúdo.
            return new ExportResult(GisExportFormats.GEOJSON, "application/json", "export-aggregates.json",
                    Json.write(Map.of("type","Aggregates","audience",req.audience().name(),
                            "aggregates", published)).getBytes(StandardCharsets.UTF_8),
                    meta, false, GisExportProfiles.validated(req.profile()), published);
        }
        if (personal && req.audience() == GisExportGuard.Audience.BACKOFFICE && !req.canViewPersonal())
            throw new IllegalArgumentException("camada pessoal exige gis.view-personal (§12 item 2)");
        if (personal) {
            // D15e: marcador sensível recusa ANTES do registro — tentativa
            // recusada por marcador sensível não gera linha no log de acesso.
            for (GisExportFeature f : features)
                if (f.sensitiveMarker()) throw new IllegalArgumentException(
                    "feição com marcador sensível nunca sai em exportação (§12 item 4): " + f.id());
            if (registrar == null) throw new IllegalStateException(
                "exportação com camada pessoal sem porta de registro do personal_data_access_log ligada (pendência de integração)");
            registrar.register(tenantId, userId, req.layerKey(), req.purpose());
        }
        GisExportGuard.checkIndividualExport(req.audience(), features, req.canViewPersonal(), personal);
        String meta = GisMgbMetadata.toXml(req.metadata());
        byte[] content;
        if (req.format().binary()) {
            if (binaryPort == null) throw new UnsupportedOperationException(
                "formato binário " + req.format() + " exige a porta GDAL (GisBinaryExportPort) — pendência de integração declarada da ED-67");
            content = binaryPort.write(req.format(), features, req.profile());
        } else {
            content = switch (req.format()) {
                case GEOJSON -> geoJson(features, req.profile()).getBytes(StandardCharsets.UTF_8);
                case CSV -> csv(features).getBytes(StandardCharsets.UTF_8);
                case KML -> kml(features, req.profile()).getBytes(StandardCharsets.UTF_8);
                case DXF -> dxf(features, req.profile()).getBytes(StandardCharsets.UTF_8);
                default -> throw new IllegalArgumentException("formato sem serializador de texto: " + req.format());
            };
        }
        return new ExportResult(req.format(), req.format().contentType(),
                "export-" + req.layerKey() + "." + req.format().extension(), content, meta,
                personal, GisExportProfiles.validated(req.profile()), List.of());
    }

    /** Mapa da FeatureCollection (base do GeoJSON e das respostas OGC, que acrescentam os campos do Core). */
    public static Map<String,Object> geoJsonMap(List<GisExportFeature> features, GisExportProfiles.Profile profile) {
        List<Object> out = new ArrayList<>();
        for (GisExportFeature f : features) {
            Map<String,Object> geom = new LinkedHashMap<>();
            geom.put("type", geoJsonType(f.geometryType()));
            // Correção D5 (RFC 7946): POINT é UMA posição [lon,lat];
            // LINESTRING é a lista de posições; POLYGON é a lista de anéis.
            Object coords = switch (f.geometryType()) {
                case "POINT" -> f.coordinates().get(0);
                case "POLYGON" -> List.of(f.coordinates());
                default -> f.coordinates();
            };
            geom.put("coordinates", coords);
            Map<String,Object> props = new TreeMap<>(f.properties());
            props.put("id", f.id()); props.put("layer", GisExportProfiles.layerFor(profile, f));
            props.put("typeCode", f.typeCode()); props.put("lifecycleStatus", f.lifecycleStatus());
            out.add(Map.of("type","Feature","id",f.id(),"geometry",geom,"properties",props));
        }
        Map<String,Object> fc = new LinkedHashMap<>();
        fc.put("type", "FeatureCollection");
        fc.put("features", out);
        return fc;
    }

    public static String geoJson(List<GisExportFeature> features, GisExportProfiles.Profile profile) {
        return Json.write(geoJsonMap(features, profile));
    }

    private static String geoJsonType(String t){
        return switch(t){ case "POINT"->"Point"; case "LINESTRING"->"LineString"; default->"Polygon"; };
    }

    static String csv(List<GisExportFeature> features) {
        java.util.SortedSet<String> keys = new java.util.TreeSet<>();
        features.forEach(f -> keys.addAll(f.properties().keySet()));
        StringBuilder sb = new StringBuilder("id;layer;type_code;lifecycle_status;geometry");
        keys.forEach(k -> sb.append(";").append(k));
        sb.append("\n");
        for (GisExportFeature f : features) {
            sb.append(csvCell(f.id())).append(";").append(csvCell(f.layerKey())).append(";")
              .append(csvCell(f.typeCode())).append(";").append(csvCell(f.lifecycleStatus())).append(";")
              .append(csvCell(Json.write(f.coordinates())));
            for (String k : keys) sb.append(";").append(csvCell(String.valueOf(f.properties().getOrDefault(k,""))));
            sb.append("\n");
        }
        return sb.toString();
    }

    private static String csvCell(String v){
        String s = v == null ? "" : v;
        return (s.contains(";") || s.contains("\"") || s.contains("\n")) ? "\"" + s.replace("\"","\"\"") + "\"" : s;
    }

    static String kml(List<GisExportFeature> features, GisExportProfiles.Profile profile) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document>\n");
        for (GisExportFeature f : features) {
            sb.append("<Placemark><name>").append(GisMgbMetadata.esc(f.id())).append("</name>");
            String coords = coordsText(f);
            switch (f.geometryType()) {
                case "POINT" -> sb.append("<Point><coordinates>").append(coords).append("</coordinates></Point>");
                case "LINESTRING" -> sb.append("<LineString><coordinates>").append(coords).append("</coordinates></LineString>");
                default -> sb.append("<Polygon><outerBoundaryIs><LinearRing><coordinates>").append(coords).append("</coordinates></LinearRing></outerBoundaryIs></Polygon>");
            }
            sb.append("</Placemark>\n");
        }
        return sb.append("</Document></kml>\n").toString();
    }

    private static String coordsText(GisExportFeature f){
        List<String> pts = new ArrayList<>();
        f.coordinates().forEach(c -> pts.add(c.get(0) + "," + c.get(1)));
        return String.join(" ", pts);
    }

    /** DXF mínimo (§9.3: camada por tipo): seção ENTITIES com POINT/LWPOLYLINE por feição. Sem blocos por símbolo (limitação declarada). */
    static String dxf(List<GisExportFeature> features, GisExportProfiles.Profile profile) {
        StringBuilder sb = new StringBuilder("0\nSECTION\n2\nENTITIES\n");
        for (GisExportFeature f : features) {
            String layer = GisExportProfiles.layerFor(profile, f);
            if (f.geometryType().equals("POINT")) {
                var c = f.coordinates().get(0);
                sb.append("0\nPOINT\n8\n").append(layer).append("\n10\n").append(c.get(0)).append("\n20\n").append(c.get(1)).append("\n");
            } else {
                sb.append("0\nLWPOLYLINE\n8\n").append(layer).append("\n90\n").append(f.coordinates().size()).append("\n70\n")
                  .append(f.geometryType().equals("POLYGON") ? "1" : "0").append("\n");
                f.coordinates().forEach(c -> sb.append("10\n").append(c.get(0)).append("\n20\n").append(c.get(1)).append("\n"));
            }
        }
        return sb.append("0\nENDSEC\n0\nEOF\n").toString();
    }
}
