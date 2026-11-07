package br.com.ziipsan.intelligence.gis.publication;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.intelligence.gis.publication.GisMapLibreStyleFactory.BasemapDef;
import br.com.ziipsan.intelligence.gis.publication.GisMapLibreStyleFactory.GeometryKind;
import br.com.ziipsan.intelligence.gis.publication.GisMapLibreStyleFactory.LayerDef;
import br.com.ziipsan.intelligence.gis.publication.GisMapLibreStyleFactory.WmsDef;
import br.com.ziipsan.intelligence.gis.publication.GisPublicationDtos.BasemapInput;
import br.com.ziipsan.intelligence.gis.publication.GisPublicationDtos.BasemapView;
import br.com.ziipsan.intelligence.gis.publication.GisPublicationDtos.LayerView;
import br.com.ziipsan.intelligence.gis.publication.GisPublicationDtos.StyleOverrideInput;
import br.com.ziipsan.intelligence.gis.publication.GisPublicationDtos.StyleRowView;
import br.com.ziipsan.intelligence.gis.publication.GisPublicationDtos.SymbolView;
import br.com.ziipsan.intelligence.gis.publication.GisPublicationDtos.WmsSourceInput;
import br.com.ziipsan.intelligence.gis.publication.GisPublicationDtos.WmsSourceView;
import br.com.ziipsan.intelligence.gis.publication.GisStyleRules.ResolvedStyle;
import br.com.ziipsan.intelligence.gis.publication.GisStyleRules.StyleRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Estilos, camadas, mapas base, WMS e símbolos da GIS-06: lê a
 * fonte única {@code gis_style} (V13_12), resolve o estilo efetivo
 * por {@link GisStyleRules} e gera o documento MapLibre por
 * {@link GisMapLibreStyleFactory}; grava o override do tenant
 * (origin TENANT) sem código e o remove na restauração do padrão.
 *
 * <p>Camadas pessoais só entram no documento de estilo de quem tem
 * {@code gis.view-personal} — o estilo também é superfície de
 * vazamento (revelaria URL e existência da camada), não só o tile.
 *
 * <p>Símbolos: a biblioteca é o conjunto de {@code style_key} dos
 * tipos ATIVOS de pacotes ATIVOS do tenant: quando a view
 * {@code gis_domain_visible} da V13_1 existe no schema, {@link
 * #symbols()} filtra os tipos pelos domínios visíveis dela (o
 * pacote do domínio ativo em {@code platform.tenant_vertical});
 * sem a view (schema parcial sem o core), todos os tipos ativos —
 * correção pós-revisão M3: antes o filtro prometido no javadoc
 * não era aplicado. O manifesto de símbolos publica essas chaves
 * como INVENTÁRIO (correção A3: não é descritor de sprite
 * MapLibre, que exige x/y/largura/altura da arte); a arte SVG e o
 * sprite binário são da P1.30 — esta tarefa não inventa desenho
 * nem geometria de sprite.
 *
 * <p>Resolução do documento (correção pós-revisão A2): o estilo
 * de cada camada é resolvido por {@link #buildLayerDef} em três
 * planos — por domínio+estado (base), por domínio+estado COM
 * accuracy {@code OUT_OF_TARGET} (é dela que sai a camada de halo;
 * resolver sem accuracy fazia o halo nunca ser emitido) e por
 * TIPO+estado para cada tipo da camada (é dela que saem as
 * expressões {@code match} por {@code type_code} da fábrica;
 * resolver com tipo nulo fazia o override por tipo ser ignorado
 * em silêncio).
 */
@Service
@Transactional(readOnly = true)
public class GisStyleService {

    private final JdbcTemplate jdbc;
    private final String tilesBaseUrl;

    public GisStyleService(JdbcTemplate jdbc,
                           @Value("${ziipsan.gis.tiles-base-url:/api/v1}") String tilesBaseUrl) {
        this.jdbc = jdbc;
        this.tilesBaseUrl = tilesBaseUrl;
    }

    // ------------------------------------------------------------ camadas

    /** Camadas para o usuário da requisição (contexto interno). */
    public List<LayerView> layersForCurrentUser() {
        return layers(GisPublicationContext.canViewPersonal());
    }

    public List<LayerView> layers(boolean canViewPersonal) {
        UUID tenantId = GisPublicationContext.tenantId();
        return jdbc.query("""
                SELECT key, label, group_label, personal, tile_min_zoom,
                       layer_version, active FROM gis_layer
                WHERE tenant_id = ? ORDER BY sort_order, key
                """, (rs, i) -> new LayerView(rs.getString("key"), rs.getString("label"),
                rs.getString("group_label"), rs.getBoolean("personal"),
                rs.getInt("tile_min_zoom"), rs.getLong("layer_version"),
                rs.getBoolean("active")), tenantId).stream()
                .filter(l -> canViewPersonal || !l.personal())
                .toList();
    }

    // ------------------------------------------------------------- estilo

    /** Documento MapLibre do usuário da requisição (contexto interno). */
    public Map<String, Object> maplibreStyle(String theme) {
        return maplibreStyle(theme, GisPublicationContext.canViewPersonal());
    }

    /** Documento MapLibre pronto para serializar (Json do core). */
    public Map<String, Object> maplibreStyle(String theme, boolean canViewPersonal) {
        UUID tenantId = GisPublicationContext.tenantId();
        String safeTheme = "dark".equals(theme) ? "dark" : "light";
        List<StyleRow> rows = styleRows(tenantId);
        Map<String, String> domainTokens = domainTokens(tenantId);
        Map<UUID, TypeInfo> types = typesById(tenantId);

        List<LayerDef> layerDefs = new ArrayList<>();
        for (LayerView layer : layers(canViewPersonal)) {
            if (!layer.active()) {
                continue;
            }
            GisTileLayers def = GisTileLayers.byKey(layer.key()).orElse(null);
            if (def == null) {
                continue;
            }
            layerDefs.add(buildLayerDef(layer, def, rows, domainTokens, types));
        }
        List<BasemapDef> basemapDefs = basemaps().stream()
                .map(b -> new BasemapDef(b.key(), b.label(), b.tilesUrl(),
                        b.styleUrl(), b.attribution(), b.isDefault()))
                .toList();
        List<WmsDef> wmsDefs = wmsSources().stream().filter(WmsSourceView::active)
                .map(w -> new WmsDef(w.id().toString(), w.label(), w.url(),
                        w.layers(), w.version(), w.format(), w.transparent()))
                .toList();
        return GisMapLibreStyleFactory.buildStyle(
                tilesBaseUrl, safeTheme, layerDefs, basemapDefs, wmsDefs);
    }

    /**
     * Monta o {@link LayerDef} de uma camada resolvendo a fonte
     * única nos três planos da correção A2 (domínio, accuracy
     * OUT_OF_TARGET e tipo). Estático e sem banco para o teste de
     * serviço exercitar a resolução real — não um estilo montado à
     * mão — antes de passar pela fábrica.
     */
    static LayerDef buildLayerDef(LayerView layer, GisTileLayers def, List<StyleRow> rows,
                                  Map<String, String> domainTokens,
                                  Map<UUID, TypeInfo> types) {
        String domainToken = def.domainKey() == null
                ? null : domainTokens.get(def.domainKey());
        Map<String, ResolvedStyle> byLifecycle = new LinkedHashMap<>();
        Map<String, ResolvedStyle> haloByLifecycle = new LinkedHashMap<>();
        for (String lifecycle : GisStyleRules.LIFECYCLE_STATUSES) {
            byLifecycle.put(lifecycle, GisStyleRules.resolve(rows,
                    def.domainKey(), null, lifecycle, null, domainToken));
            haloByLifecycle.put(lifecycle, GisStyleRules.resolve(rows,
                    def.domainKey(), null, lifecycle, "OUT_OF_TARGET", domainToken));
        }
        Map<String, Map<String, ResolvedStyle>> byType = new LinkedHashMap<>();
        for (Map.Entry<UUID, TypeInfo> type : types.entrySet()) {
            if (!typeBelongsToLayer(type.getValue(), def)) {
                continue;
            }
            Map<String, ResolvedStyle> typeByLifecycle = new LinkedHashMap<>();
            for (String lifecycle : GisStyleRules.LIFECYCLE_STATUSES) {
                typeByLifecycle.put(lifecycle, GisStyleRules.resolve(rows,
                        def.domainKey(), type.getKey(), lifecycle, null, domainToken));
            }
            byType.put(type.getValue().code(), typeByLifecycle);
        }
        return new LayerDef(layer.key(), layer.label(), layer.personal(),
                layer.tileMinZoom(), geometryKind(def), byLifecycle,
                haloByLifecycle, byType);
    }

    /** O tipo pertence à camada? (tabela física pelo papel topológico + domínio). */
    static boolean typeBelongsToLayer(TypeInfo type, GisTileLayers def) {
        String table = featureTableForRole(type.topologyRole());
        if (table == null || !table.equals(def.featureTable())) {
            return false;
        }
        return def.domainKey() == null || def.domainKey().equals(type.domainKey());
    }

    /** Papel topológico da classe (V13_1) → tabela física de feições. */
    static String featureTableForRole(String topologyRole) {
        if (topologyRole == null) {
            return null;
        }
        return switch (topologyRole) {
            case "NODE" -> "gis_node";
            case "ARC" -> "gis_arc";
            case "CONNECTION" -> "gis_connection";
            case "STRUCTURE" -> "gis_structure";
            case "ZONE" -> "gis_zone";
            case "POI" -> "gis_poi";
            default -> null;
        };
    }

    private static GeometryKind geometryKind(GisTileLayers def) {
        if (def.sourceKind() != GisTileLayers.SourceKind.FEATURE
                && def.sourceKind() != GisTileLayers.SourceKind.CONTEXT) {
            return GeometryKind.POINT;
        }
        return switch (def.featureTable()) {
            case "gis_arc", "gis_connection" -> GeometryKind.LINE;
            case "gis_node", "gis_poi" -> GeometryKind.POINT;
            case "gis_zone" -> GeometryKind.POLYGON;
            default -> GeometryKind.ANY;
        };
    }

    public List<StyleRowView> styleRowsView() {
        UUID tenantId = GisPublicationContext.tenantId();
        Map<UUID, TypeInfo> types = typesById(tenantId);
        return jdbc.query("""
                SELECT id, origin, domain_key, feature_type_id, lifecycle_status,
                       accuracy_status, color_token, line_width,
                       line_dash::text AS line_dash, halo, icon_key,
                       label_field, label_min_zoom
                FROM gis_style WHERE tenant_id = ? AND active
                ORDER BY origin, domain_key NULLS LAST, feature_type_id NULLS FIRST,
                         lifecycle_status
                """, (rs, i) -> new StyleRowView(
                rs.getObject("id", UUID.class), rs.getString("origin"),
                rs.getString("domain_key"), rs.getObject("feature_type_id", UUID.class),
                typeCode(types, rs.getObject("feature_type_id", UUID.class)),
                rs.getString("lifecycle_status"), rs.getString("accuracy_status"),
                rs.getString("color_token"),
                rs.getObject("line_width") == null ? null : rs.getDouble("line_width"),
                parseDash(rs.getString("line_dash")),
                rs.getObject("halo") == null ? null : rs.getBoolean("halo"),
                rs.getString("icon_key"), rs.getString("label_field"),
                rs.getObject("label_min_zoom") == null ? null : rs.getInt("label_min_zoom")),
                tenantId);
    }

    /**
     * Grava (upsert) o override do tenant para um (tipo|domínio,
     * estado). Ícone só da biblioteca; cor só como token map-* —
     * validação em {@link GisStyleRules#validateOverride}.
     */
    @Transactional
    public StyleRowView saveOverride(StyleOverrideInput in) {
        UUID tenantId = GisPublicationContext.tenantId();
        UUID userId = GisPublicationContext.userId();
        Set<String> library = librarySymbolKeys(tenantId);
        try {
            GisStyleRules.validateOverride(in.lifecycleStatus(), in.accuracyStatus(),
                    in.colorToken(), in.iconKey(), library);
        } catch (IllegalArgumentException e) {
            throw new ApiException.BadRequest("estilo-invalido", e.getMessage());
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO gis_style (id, tenant_id, domain_key, feature_type_id,
                            lifecycle_status, accuracy_status, color_token, line_width,
                            line_dash, halo, icon_key, label_field, label_min_zoom,
                            origin, created_by, updated_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, 'TENANT', ?, ?)
                    ON CONFLICT (tenant_id, origin,
                        COALESCE(domain_key, ''),
                        COALESCE(feature_type_id,
                            '00000000-0000-0000-0000-000000000000'::uuid),
                        lifecycle_status, COALESCE(accuracy_status, ''))
                    DO UPDATE SET color_token = EXCLUDED.color_token,
                            line_width = EXCLUDED.line_width,
                            line_dash = EXCLUDED.line_dash,
                            halo = EXCLUDED.halo,
                            icon_key = EXCLUDED.icon_key,
                            label_field = EXCLUDED.label_field,
                            label_min_zoom = EXCLUDED.label_min_zoom,
                            active = TRUE, updated_at = clock_timestamp(),
                            updated_by = EXCLUDED.updated_by
                    """, id, tenantId, in.domainKey(), in.featureTypeId(),
                    in.lifecycleStatus(), in.accuracyStatus(), in.colorToken(),
                    in.lineWidth(),
                    in.lineDash() == null ? null : Json.write(in.lineDash()),
                    in.halo(), in.iconKey(), in.labelField(), in.labelMinZoom(),
                    userId, userId);
        } catch (DataAccessException e) {
            throw new ApiException.BadRequest("estilo-invalido",
                    "Não foi possível gravar o estilo: " + rootMessage(e));
        }
        return styleRowsView().stream()
                .filter(r -> "TENANT".equals(r.origin())
                        && r.lifecycleStatus().equals(in.lifecycleStatus())
                        && java.util.Objects.equals(r.featureTypeId(), in.featureTypeId())
                        && java.util.Objects.equals(r.domainKey(), in.domainKey())
                        && java.util.Objects.equals(r.accuracyStatus(), in.accuracyStatus()))
                .findFirst()
                .orElseThrow(() -> new ApiException.NotFound("Estilo não encontrado."));
    }

    /** Restaurar o padrão Ziip = apagar o override do tenant. */
    @Transactional
    public void restoreFactory(UUID styleRowId) {
        UUID tenantId = GisPublicationContext.tenantId();
        int removed = jdbc.update("""
                DELETE FROM gis_style WHERE id = ? AND tenant_id = ? AND origin = 'TENANT'
                """, styleRowId, tenantId);
        if (removed == 0) {
            throw new ApiException.NotFound(
                    "Override do tenant não encontrado (o padrão de fábrica não se apaga).");
        }
    }

    // ------------------------------------------------------------ símbolos

    /**
     * Biblioteca de símbolos: style_key dos tipos ativos dos
     * PACOTES ATIVOS do tenant (correção M3). Havendo a view
     * {@code gis_domain_visible} (V13_1, criada quando o core tem
     * {@code platform.tenant_vertical}), só entram tipos cujo
     * domínio é visível para o tenant; sem a view, todos os tipos
     * ativos (schema parcial — mesmo desenho de guarda por
     * {@code to_regclass} do GisValidationService).
     */
    public List<SymbolView> symbols() {
        UUID tenantId = GisPublicationContext.tenantId();
        return jdbc.query(symbolsSql(domainVisibleViewExists()),
                (rs, i) -> new SymbolView(rs.getString("key"),
                rs.getString("type_code"), rs.getString("type_label"),
                rs.getString("domain_key")), tenantId);
    }

    /** A view de domínios por pacote ativo existe neste schema? */
    private boolean domainVisibleViewExists() {
        Integer exists = jdbc.queryForObject(
                "SELECT CASE WHEN to_regclass('gis_domain_visible') IS NULL THEN 0 ELSE 1 END",
                Integer.class);
        return exists != null && exists == 1;
    }

    /** SQL da biblioteca, com e sem o filtro de pacote ativo (testável). */
    static String symbolsSql(boolean filterByVisibleDomain) {
        String domainFilter = filterByVisibleDomain ? """
                AND EXISTS (SELECT 1 FROM gis_domain_visible dv
                            WHERE dv.tenant_id = ft.tenant_id AND dv.key = fc.domain)
                """ : "";
        return """
                SELECT DISTINCT ft.style_key AS key, ft.code AS type_code,
                       ft.label AS type_label, fc.domain AS domain_key
                FROM gis_feature_type ft
                JOIN gis_feature_class fc
                  ON fc.id = ft.class_id AND fc.tenant_id = ft.tenant_id
                WHERE ft.tenant_id = ? AND ft.active AND ft.style_key IS NOT NULL
                """ + domainFilter + """
                ORDER BY fc.domain, ft.code
                """;
    }

    /**
     * Manifesto de símbolos: INVENTÁRIO das chaves publicadas para
     * este tenant (correção A3). NÃO é um descritor de sprite
     * MapLibre — esse formato exige x/y/largura/altura por imagem,
     * que só existem com a arte da P1.30; inventar geometria seria
     * dado fabricado. Por isso o documento de estilo não referencia
     * sprite até a P1.30 entregar a arte (ver GisMapLibreStyleFactory)
     * e este manifesto se declara inventário no próprio corpo.
     */
    public Map<String, Object> spriteManifest() {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("format", "inventory");
        manifest.put("consumableByMapLibreAsSprite", false);
        manifest.put("symbols", symbols().stream().map(SymbolView::key).distinct().toList());
        manifest.put("note", "Inventário de símbolos, não descritor de sprite: "
                + "a arte SVG e o sprite binário são da P1.30 (ADR-0036); com a arte, "
                + "esta rota passa a servir o descritor MapLibre (x/y/width/height) "
                + "e o documento de estilo volta a declarar o sprite nela.");
        return manifest;
    }

    private Set<String> librarySymbolKeys(UUID tenantId) {
        return Set.copyOf(symbols().stream().map(SymbolView::key).toList());
    }

    // -------------------------------------------------------- mapas base

    public List<BasemapView> basemaps() {
        UUID tenantId = GisPublicationContext.tenantId();
        return jdbc.query("""
                SELECT id, key, label, provider, tiles_url, style_url,
                       attribution, is_default, origin FROM gis_basemap
                WHERE tenant_id = ? AND active ORDER BY sort_order, label
                """, (rs, i) -> new BasemapView(rs.getObject("id", UUID.class),
                rs.getString("key"), rs.getString("label"), rs.getString("provider"),
                rs.getString("tiles_url"), rs.getString("style_url"),
                rs.getString("attribution"), rs.getBoolean("is_default"),
                rs.getString("origin")), tenantId);
    }

    @Transactional
    public BasemapView addBasemap(BasemapInput in) {
        UUID tenantId = GisPublicationContext.tenantId();
        UUID userId = GisPublicationContext.userId();
        if (!"OSM".equals(in.provider())
                && (in.licenseRef() == null || in.licenseRef().isBlank())) {
            throw new ApiException.BadRequest("mapa-base-sem-licenca",
                    "Mapa base de outro provedor exige a referência da licença (§3.10).");
        }
        UUID id = UUID.randomUUID();
        String key = "tenant-" + id.toString().substring(0, 8);
        if (in.makeDefault()) {
            jdbc.update("UPDATE gis_basemap SET is_default = FALSE WHERE tenant_id = ?",
                    tenantId);
        }
        jdbc.update("""
                INSERT INTO gis_basemap (id, tenant_id, key, label, provider,
                        tiles_url, style_url, attribution, license_ref,
                        is_default, origin, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'TENANT', ?, ?)
                """, id, tenantId, key, in.label(), in.provider(),
                in.tilesUrl(), in.styleUrl(), in.attribution(), in.licenseRef(),
                in.makeDefault(), userId, userId);
        return basemaps().stream().filter(b -> b.id().equals(id)).findFirst()
                .orElseThrow(() -> new ApiException.NotFound("Mapa base não encontrado."));
    }

    // ---------------------------------------------------------------- WMS

    public List<WmsSourceView> wmsSources() {
        UUID tenantId = GisPublicationContext.tenantId();
        return jdbc.query("""
                SELECT id, label, url, layers, wms_version, format, transparent, active
                FROM gis_wms_source WHERE tenant_id = ? ORDER BY label
                """, this::mapWms, tenantId);
    }

    @Transactional
    public WmsSourceView addWmsSource(WmsSourceInput in) {
        UUID tenantId = GisPublicationContext.tenantId();
        UUID userId = GisPublicationContext.userId();
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO gis_wms_source (id, tenant_id, label, url, layers,
                        wms_version, format, transparent, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, tenantId, in.label(), in.url(), in.layers(),
                in.version() == null ? "1.3.0" : in.version(),
                in.format() == null ? "image/png" : in.format(),
                in.transparent() == null || in.transparent(), userId, userId);
        return wmsSources().stream().filter(w -> w.id().equals(id)).findFirst()
                .orElseThrow(() -> new ApiException.NotFound("Fonte WMS não encontrada."));
    }

    @Transactional
    public void removeWmsSource(UUID id) {
        UUID tenantId = GisPublicationContext.tenantId();
        int removed = jdbc.update(
                "DELETE FROM gis_wms_source WHERE id = ? AND tenant_id = ?", id, tenantId);
        if (removed == 0) {
            throw new ApiException.NotFound("Fonte WMS não encontrada.");
        }
    }

    private WmsSourceView mapWms(ResultSet rs, int row) throws SQLException {
        return new WmsSourceView(rs.getObject("id", UUID.class), rs.getString("label"),
                rs.getString("url"), rs.getString("layers"), rs.getString("wms_version"),
                rs.getString("format"), rs.getBoolean("transparent"),
                rs.getBoolean("active"));
    }

    // ------------------------------------------------------------- internos

    record TypeInfo(String code, String label, String domainKey, String styleKey,
                    String topologyRole) {
    }

    private Map<UUID, TypeInfo> typesById(UUID tenantId) {
        Map<UUID, TypeInfo> out = new LinkedHashMap<>();
        jdbc.query("""
                SELECT ft.id, ft.code, ft.label, ft.style_key, fc.domain,
                       fc.topology_role
                FROM gis_feature_type ft
                JOIN gis_feature_class fc
                  ON fc.id = ft.class_id AND fc.tenant_id = ft.tenant_id
                WHERE ft.tenant_id = ?
                """, (rs, i) -> {
                    out.put(rs.getObject("id", UUID.class),
                            new TypeInfo(rs.getString("code"), rs.getString("label"),
                                    rs.getString("domain"), rs.getString("style_key"),
                                    rs.getString("topology_role")));
                    return null;
                }, tenantId);
        return out;
    }

    private Map<String, String> domainTokens(UUID tenantId) {
        Map<String, String> out = new LinkedHashMap<>();
        jdbc.query("SELECT key, map_color_token FROM gis_domain WHERE tenant_id = ?",
                (rs, i) -> {
                    out.put(rs.getString("key"), rs.getString("map_color_token"));
                    return null;
                }, tenantId);
        return out;
    }

    private List<StyleRow> styleRows(UUID tenantId) {
        return jdbc.query("""
                SELECT origin, domain_key, feature_type_id, lifecycle_status,
                       accuracy_status, color_token, line_width,
                       line_dash::text AS line_dash, halo, icon_key,
                       label_field, label_min_zoom
                FROM gis_style WHERE tenant_id = ? AND active
                """, (rs, i) -> new StyleRow(rs.getString("origin"),
                rs.getString("domain_key"), rs.getObject("feature_type_id", UUID.class),
                rs.getString("lifecycle_status"), rs.getString("accuracy_status"),
                rs.getString("color_token"),
                rs.getObject("line_width") == null ? null : rs.getDouble("line_width"),
                rs.getString("line_dash"),
                rs.getObject("halo") == null ? null : rs.getBoolean("halo"),
                rs.getString("icon_key"), rs.getString("label_field"),
                rs.getObject("label_min_zoom") == null ? null : rs.getInt("label_min_zoom")),
                tenantId);
    }

    private static String typeCode(Map<UUID, TypeInfo> types, UUID id) {
        TypeInfo info = id == null ? null : types.get(id);
        return info == null ? null : info.code();
    }

    private static List<Double> parseDash(String json) {
        if (json == null) {
            return null;
        }
        List<Double> out = new ArrayList<>();
        var node = Json.readTree(json);
        if (node.isArray()) {
            node.forEach(n -> out.add(n.asDouble()));
        }
        return out;
    }

    private static String rootMessage(DataAccessException e) {
        Throwable cause = e.getMostSpecificCause();
        return cause == null ? e.getMessage() : cause.getMessage();
    }
}
