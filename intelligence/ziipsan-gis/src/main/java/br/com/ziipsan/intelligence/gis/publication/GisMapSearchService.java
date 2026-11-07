package br.com.ziipsan.intelligence.gis.publication;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.intelligence.gis.publication.GisPublicationDtos.FeatureDetailView;
import br.com.ziipsan.intelligence.gis.publication.GisPublicationDtos.SearchHitView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Identificação e pesquisas do mapa web de consulta (§10.3):
 * ficha da feição ao clicar, pesquisa por atributo (código/nome),
 * por endereço (logradouros do contexto importado, GIS-07 — inclusive
 * em cadastramento) e por coordenada (lat/lon ou UTM SIRGAS 2000 no
 * fuso do tenant, convertido por {@link Utm} e buscado no PostGIS).
 *
 * <p>LGPD (§6.6, GIS-05): a ficha de ligação (gis_connection com
 * {@code service_connection_id}) sem {@code gis.view-personal}
 * devolve só identificador e situação — atributos, nome e vínculo
 * com a ligação comercial ficam de fora, como na ficha da GIS-05.
 * A pesquisa por atributo nunca varre gis_connection para quem não
 * tem a permissão.
 *
 * <p>Nomes de tabela vêm da lista fechada {@code FEATURE_TABLES}
 * (a mesma da GIS-04) — nunca da entrada: tabela fora da lista é
 * 400, não SQL montado com texto do usuário.
 */
@Service
@Transactional(readOnly = true)
public class GisMapSearchService {

    static final Set<String> FEATURE_TABLES = Set.of(
            "gis_node", "gis_arc", "gis_connection", "gis_structure",
            "gis_zone", "gis_poi");
    private static final int MAX_HITS = 50;

    private final JdbcTemplate jdbc;

    public GisMapSearchService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------- identificação

    public FeatureDetailView identifyForCurrentUser(String featureTable, UUID id) {
        return identify(featureTable, id, GisPublicationContext.canViewPersonal());
    }

    public FeatureDetailView identify(String featureTable, UUID id,
                                      boolean canViewPersonal) {
        UUID tenantId = GisPublicationContext.tenantId();
        if (!FEATURE_TABLES.contains(featureTable)) {
            throw new ApiException.BadRequest("tabela-de-feicao-invalida",
                    "Tabela de feição desconhecida: " + featureTable);
        }
        List<FeatureDetailView> rows = jdbc.query("""
                SELECT f.code, f.name, ft.code AS type_code, ft.label AS type_label,
                       fc.domain AS domain_key, f.lifecycle_status,
                       f.accuracy_status, f.attributes::text AS attributes,
                       ST_AsText(f.geom) AS geom_wkt,
                       f.updated_at
                FROM %s f
                JOIN gis_feature_type ft
                  ON ft.id = f.feature_type_id AND ft.tenant_id = f.tenant_id
                JOIN gis_feature_class fc
                  ON fc.id = ft.class_id AND fc.tenant_id = ft.tenant_id
                WHERE f.id = ? AND f.tenant_id = ?
                """.formatted(featureTable), (rs, i) -> {
                    Map<String, Object> attributes = Json.readMap(rs.getString("attributes"));
                    return new FeatureDetailView(featureTable, id,
                            rs.getString("code"), rs.getString("name"),
                            rs.getString("type_code"), rs.getString("type_label"),
                            rs.getString("domain_key"), rs.getString("lifecycle_status"),
                            rs.getString("accuracy_status"), attributes,
                            rs.getString("geom_wkt"), null, canViewPersonal,
                            instant(rs.getTimestamp("updated_at")));
                }, id, tenantId);
        if (rows.isEmpty()) {
            throw new ApiException.NotFound("Feição não encontrada.");
        }
        FeatureDetailView detail = rows.get(0);
        if (!"gis_connection".equals(featureTable)) {
            return detail;
        }
        UUID serviceConnectionId = jdbc.queryForList(
                "SELECT service_connection_id FROM gis_connection WHERE id = ? AND tenant_id = ?",
                UUID.class, id, tenantId).stream().findFirst().orElse(null);
        if (canViewPersonal) {
            return new FeatureDetailView(detail.featureTable(), detail.id(), detail.code(),
                    detail.name(), detail.typeCode(), detail.typeLabel(), detail.domainKey(),
                    detail.lifecycleStatus(), detail.accuracyStatus(), detail.attributes(),
                    detail.geomWkt(), serviceConnectionId, true, detail.updatedAt());
        }
        // Sem gis.view-personal: só identificador e situação (§6.6).
        return new FeatureDetailView(detail.featureTable(), detail.id(), detail.code(),
                null, detail.typeCode(), detail.typeLabel(), detail.domainKey(),
                detail.lifecycleStatus(), detail.accuracyStatus(), Map.of(),
                null, null, false, detail.updatedAt());
    }

    // ------------------------------------------------------------ pesquisas

    public List<SearchHitView> searchByAttributeForCurrentUser(String query) {
        return searchByAttribute(query, GisPublicationContext.canViewPersonal());
    }

    /** Por atributo: código exato/prefixo ou nome contendo o texto. */
    public List<SearchHitView> searchByAttribute(String query, boolean canViewPersonal) {
        UUID tenantId = GisPublicationContext.tenantId();
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) {
            throw new ApiException.BadRequest("consulta-curta",
                    "Informe ao menos 2 caracteres para a pesquisa por atributo.");
        }
        List<SearchHitView> hits = new ArrayList<>();
        for (String table : FEATURE_TABLES) {
            if ("gis_connection".equals(table) && !canViewPersonal) {
                continue;
            }
            hits.addAll(jdbc.query("""
                    SELECT '%s' AS feature_table, f.id, f.code, f.name,
                           ft.code AS type_code,
                           ST_X(ST_Centroid(ST_Transform(f.geom, 4326))) AS lon,
                           ST_Y(ST_Centroid(ST_Transform(f.geom, 4326))) AS lat
                    FROM %s f
                    JOIN gis_feature_type ft
                      ON ft.id = f.feature_type_id AND ft.tenant_id = f.tenant_id
                    WHERE f.tenant_id = ?
                      AND (f.code ILIKE ? OR f.name ILIKE ?)
                    ORDER BY f.code LIMIT 20
                    """.formatted(table, table), (rs, i) -> new SearchHitView(
                    "FEATURE", rs.getString("feature_table"),
                    rs.getObject("id", UUID.class), rs.getString("code"),
                    rs.getString("name"), rs.getString("type_code"),
                    rs.getDouble("lon"), rs.getDouble("lat")),
                    tenantId, q + "%", "%" + q + "%"));
            if (hits.size() >= MAX_HITS) {
                return hits.subList(0, MAX_HITS);
            }
        }
        return hits;
    }

    /**
     * Por endereço: logradouros do contexto importado (gis_context_
     * feature STREET da GIS-07), pelo nome — inclusive logradouro em
     * cadastramento, porque a fonte é o contexto, não a rede.
     */
    public List<SearchHitView> searchByAddress(String query) {
        UUID tenantId = GisPublicationContext.tenantId();
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) {
            throw new ApiException.BadRequest("consulta-curta",
                    "Informe ao menos 2 caracteres para a pesquisa por endereço.");
        }
        return jdbc.query("""
                SELECT id, code, name,
                       ST_X(ST_Centroid(ST_Transform(geom, 4326))) AS lon,
                       ST_Y(ST_Centroid(ST_Transform(geom, 4326))) AS lat
                FROM gis_context_feature
                WHERE tenant_id = ? AND context_kind = 'STREET'
                  AND name ILIKE ?
                ORDER BY name LIMIT ?
                """, (rs, i) -> new SearchHitView("ADDRESS", "gis_context_feature",
                rs.getObject("id", UUID.class), rs.getString("code"),
                rs.getString("name"), "STREET",
                rs.getDouble("lon"), rs.getDouble("lat")),
                tenantId, "%" + q + "%", MAX_HITS);
    }

    public List<SearchHitView> searchNearForCurrentUser(double lon, double lat,
                                                         double radiusMeters) {
        return searchNear(lon, lat, radiusMeters, GisPublicationContext.canViewPersonal());
    }

    /** Por coordenada geográfica: feições num raio (metros). */
    public List<SearchHitView> searchNear(double lon, double lat, double radiusMeters,
                                          boolean canViewPersonal) {
        UUID tenantId = GisPublicationContext.tenantId();
        if (lon < -180 || lon > 180 || lat < -90 || lat > 90) {
            throw new ApiException.BadRequest("coordenada-invalida",
                    "Longitude/latitude fora da faixa válida.");
        }
        double radius = Math.max(10, Math.min(radiusMeters, 5_000));
        List<SearchHitView> hits = new ArrayList<>();
        for (String table : FEATURE_TABLES) {
            if ("gis_connection".equals(table) && !canViewPersonal) {
                continue;
            }
            hits.addAll(jdbc.query("""
                    SELECT '%s' AS feature_table, f.id, f.code, f.name,
                           ft.code AS type_code,
                           ST_X(ST_Centroid(ST_Transform(f.geom, 4326))) AS lon,
                           ST_Y(ST_Centroid(ST_Transform(f.geom, 4326))) AS lat
                    FROM %s f
                    JOIN gis_feature_type ft
                      ON ft.id = f.feature_type_id AND ft.tenant_id = f.tenant_id
                    WHERE f.tenant_id = ?
                      AND ST_DWithin(f.geom::geography,
                            ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)
                    ORDER BY ST_Distance(f.geom::geography,
                            ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography)
                    LIMIT 20
                    """.formatted(table, table), (rs, i) -> new SearchHitView(
                    "NEAR", rs.getString("feature_table"),
                    rs.getObject("id", UUID.class), rs.getString("code"),
                    rs.getString("name"), rs.getString("type_code"),
                    rs.getDouble("lon"), rs.getDouble("lat")),
                    tenantId, lon, lat, radius, lon, lat));
            if (hits.size() >= MAX_HITS) {
                return hits.subList(0, MAX_HITS);
            }
        }
        return hits;
    }

    public List<SearchHitView> searchByUtmForCurrentUser(double easting, double northing,
                                                         int zone, boolean southernHemisphere,
                                                         double radiusMeters) {
        return searchByUtm(easting, northing, zone, southernHemisphere, radiusMeters,
                GisPublicationContext.canViewPersonal());
    }

    /** Por coordenada UTM (fuso do tenant): converte e busca no raio. */
    public List<SearchHitView> searchByUtm(double easting, double northing, int zone,
                                           boolean southernHemisphere, double radiusMeters,
                                           boolean canViewPersonal) {
        Utm.LonLat lonLat;
        try {
            lonLat = Utm.toLonLat(easting, northing, zone, southernHemisphere);
        } catch (IllegalArgumentException e) {
            throw new ApiException.BadRequest("coordenada-invalida", e.getMessage());
        }
        return searchNear(lonLat.lon(), lonLat.lat(), radiusMeters, canViewPersonal);
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
