package br.com.ziipsan.intelligence.gis.waste;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.intelligence.gis.waste.GisWasteDtos.CleanupRequestView;
import br.com.ziipsan.intelligence.gis.waste.GisWasteDtos.PhotoInput;
import br.com.ziipsan.intelligence.gis.waste.GisWasteDtos.PropertySectorLinkView;
import br.com.ziipsan.intelligence.gis.waste.GisWasteDtos.RouteInput;
import br.com.ziipsan.intelligence.gis.waste.GisWasteDtos.RouteStopView;
import br.com.ziipsan.intelligence.gis.waste.GisWasteDtos.RouteView;
import br.com.ziipsan.intelligence.gis.waste.GisWasteDtos.SectorInput;
import br.com.ziipsan.intelligence.gis.waste.GisWasteDtos.SectorView;
import br.com.ziipsan.intelligence.gis.waste.GisWasteDtos.SymbolManifestView;
import br.com.ziipsan.intelligence.gis.waste.GisWasteDtos.WastePointInput;
import br.com.ziipsan.intelligence.gis.waste.GisWasteDtos.WastePointView;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * GIS-14 (resíduos sólidos): pontos e unidades do §4.5 sobre as
 * tabelas de feição da GIS-01 (gis_poi/gis_structure), setores como
 * gis_zone COLLECTION_SECTOR, vínculo imóvel↔setor para a taxa da
 * ED-25, descarte irregular com foto e pedido de OS de limpeza, e
 * rotas operacionais sobre o grafo de logradouros
 * (gis_waste_street_edge) com a matriz do pgRouting quando a
 * extensão está presente e o sequenciamento de
 * {@link GisWasteRoutePlanner}.
 *
 * <p>Fronteiras declaradas:
 * <ul>
 *   <li>a OS de limpeza em si é do núcleo de OS (ED-36/core): aqui
 *       nasce o PEDIDO (gis_waste_cleanup_request) com a foto como
 *       pré-condição; o {@code service_order_id} é preenchido pela
 *       integração ao abrir a OS — nenhum tipo de OS é inventado
 *       nesta entrega;</li>
 *   <li>a geometria entra e sai como GeoJSON 4674 (ST_GeomFromGeoJSON
 *       / ST_AsGeoJSON); a gravação direta deste serviço é a do
 *       cadastro operacional de resíduos — pontos SEM topologia de
 *       rede (§3.6.6: "ponto sem rede") e unidades —, distinta da
 *       rede em operação, cujas mudanças passam pelo pacote da
 *       GIS-04 (§6.1); a passagem destes cadastros por pacote
 *       (IMPORT/FIELD/SERVICE_ORDER) é fiação de integração
 *       declarada no relatório, como o registro da OS;</li>
 *   <li>JSON sempre pela classe Json do core.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class GisWasteService {

    private final JdbcTemplate jdbc;

    public GisWasteService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------- pontos

    @Transactional
    public WastePointView createPoint(WastePointInput in) {
        UUID tenantId = tenantId();
        UUID userId = userId();
        GisWasteCatalog.WasteType type;
        try {
            type = GisWasteCatalog.requireType(in.typeCode());
            GisWasteRules.ensurePointAttributes(type, in.attributes());
        } catch (IllegalArgumentException e) {
            throw new ApiException.BadRequest("residuo-invalido", e.getMessage());
        }
        if (type.role() == GisWasteCatalog.Role.ZONE
                || type.role() == GisWasteCatalog.Role.OPERATIONAL) {
            throw new ApiException.BadRequest("residuo-invalido",
                    "tipo " + type.code() + " nao e ponto/unidade: setor tem endpoint proprio");
        }
        UUID typeId = featureTypeId(tenantId, type);
        UUID id = UUID.randomUUID();
        String attributesJson = Json.write(in.attributes());
        String geoJson = Json.write(in.geometryGeoJson());
        try {
            if (type.role() == GisWasteCatalog.Role.POI) {
                jdbc.update("""
                        INSERT INTO gis_poi (id, tenant_id, code, feature_type_id,
                                name, lifecycle_status, capture_method, attributes,
                                geom, created_by, updated_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb,
                                ST_SetSRID(ST_GeomFromGeoJSON(?), 4674), ?, ?)
                        """, id, tenantId, in.code(), typeId, in.name(),
                        in.lifecycleStatus(), in.captureMethod(), attributesJson,
                        geoJson, userId, userId);
            } else {
                // STRUCTURE (V13_2: geom MultiPolygon OU geom_point, uma
                // das duas obrigatoria): ponto vai a geom_point; (multi)
                // poligono vai a geom; qualquer outra geometria e recusada
                // pelo CHECK da tabela, sem gravacao parcial.
                jdbc.update("""
                        INSERT INTO gis_structure (id, tenant_id, code, feature_type_id,
                                name, lifecycle_status, capture_method, attributes,
                                geom, geom_point, created_by, updated_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb,
                                CASE WHEN ST_GeometryType(ST_GeomFromGeoJSON(?))
                                          IN ('ST_Polygon', 'ST_MultiPolygon')
                                     THEN ST_Multi(ST_SetSRID(ST_GeomFromGeoJSON(?), 4674)) END,
                                CASE WHEN ST_GeometryType(ST_GeomFromGeoJSON(?)) = 'ST_Point'
                                     THEN ST_SetSRID(ST_GeomFromGeoJSON(?), 4674) END, ?, ?)
                        """, id, tenantId, in.code(), typeId, in.name(),
                        in.lifecycleStatus(), in.captureMethod(), attributesJson,
                        geoJson, geoJson, geoJson, geoJson, userId, userId);
            }
        } catch (DataAccessException e) {
            throw new ApiException.BadRequest("residuo-recusado",
                    "Banco recusou o ponto de residuos: " + e.getMostSpecificCause().getMessage());
        }
        return getPoint(id);
    }

    public WastePointView getPoint(UUID id) {
        UUID tenantId = tenantId();
        List<WastePointView> rows = jdbc.query("""
                SELECT p.id, p.code, ft.code AS type_code, 'gis_poi' AS tbl,
                       p.name, p.lifecycle_status, p.attributes::text AS attrs
                  FROM gis_poi p
                  JOIN gis_feature_type ft ON ft.id = p.feature_type_id AND ft.tenant_id = p.tenant_id
                 WHERE p.id = ? AND p.tenant_id = ?
                UNION ALL
                SELECT s.id, s.code, ft.code AS type_code, 'gis_structure' AS tbl,
                       s.name, s.lifecycle_status, s.attributes::text AS attrs
                  FROM gis_structure s
                  JOIN gis_feature_type ft ON ft.id = s.feature_type_id AND ft.tenant_id = s.tenant_id
                 WHERE s.id = ? AND s.tenant_id = ?
                """, (rs, n) -> new WastePointView(
                rs.getObject("id", UUID.class), rs.getString("code"),
                rs.getString("type_code"), rs.getString("tbl"), rs.getString("name"),
                rs.getString("lifecycle_status"), Json.readMap(rs.getString("attrs"))),
                id, tenantId, id, tenantId);
        if (rows.isEmpty()) {
            throw new ApiException.NotFound(
                    "Ponto/unidade de residuos nao encontrado: " + id);
        }
        return rows.get(0);
    }

    /** Aviso do §5.4: contêiner/PEV/ecoponto fora de setor gera issue de qualidade. */
    public List<UUID> pointsOutsideSector() {
        UUID tenantId = tenantId();
        List<UUID> out = new ArrayList<>(jdbc.queryForList("""
                SELECT p.id
                  FROM gis_poi p
                  JOIN gis_feature_type ft ON ft.id = p.feature_type_id AND ft.tenant_id = p.tenant_id
                 WHERE p.tenant_id = ? AND p.lifecycle_status = 'IN_SERVICE'
                   AND ft.code IN ('CONTAINER', 'DROP_OFF_POINT')
                   AND NOT EXISTS (
                       SELECT 1 FROM gis_zone z
                        WHERE z.tenant_id = p.tenant_id AND z.zone_type = 'COLLECTION_SECTOR'
                          AND z.lifecycle_status = 'IN_SERVICE'
                          AND ST_Contains(z.geom, p.geom))
                """, UUID.class, tenantId));
        // Ecoponto e STRUCTURE (§4.5 via gis_structure): o ponto de
        // referencia e geom_point quando existe; senao, o centroide.
        out.addAll(jdbc.queryForList("""
                SELECT s.id
                  FROM gis_structure s
                  JOIN gis_feature_type ft ON ft.id = s.feature_type_id AND ft.tenant_id = s.tenant_id
                 WHERE s.tenant_id = ? AND s.lifecycle_status = 'IN_SERVICE'
                   AND ft.code = 'ECOPOINT'
                   AND NOT EXISTS (
                       SELECT 1 FROM gis_zone z
                        WHERE z.tenant_id = s.tenant_id AND z.zone_type = 'COLLECTION_SECTOR'
                          AND z.lifecycle_status = 'IN_SERVICE'
                          AND ST_Contains(z.geom,
                              COALESCE(s.geom_point, ST_Centroid(s.geom))))
                """, UUID.class, tenantId));
        return out;
    }

    // ------------------------------------------- foto e OS de limpeza

    @Transactional
    public void attachPhoto(UUID pointId, PhotoInput in) {
        UUID tenantId = tenantId();
        UUID userId = userId();
        WastePointView point = getPoint(pointId);
        jdbc.update("""
                INSERT INTO gis_attachment (id, tenant_id, target_feature_table,
                        target_feature_id, attachment_type, document_id, description,
                        created_by, updated_by)
                VALUES (?, ?, ?, ?, 'PHOTO', ?, ?, ?, ?)
                """, UUID.randomUUID(), tenantId, point.featureTable(), pointId,
                in.documentId(), in.description(), userId, userId);
    }

    public boolean hasPhoto(UUID tenantId, UUID pointId) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM gis_attachment
                 WHERE tenant_id = ? AND target_feature_id = ? AND attachment_type = 'PHOTO'
                """, Integer.class, tenantId, pointId);
        return count != null && count > 0;
    }

    /**
     * Pedido de OS de limpeza do descarte irregular: exige foto e
     * situação ACTIVE/REINCIDENT (regra da GIS-14); move o ponto a
     * CLEANING e grava o pedido REQUESTED. O service_order_id nasce
     * nulo e é preenchido quando a integração abre a OS no núcleo
     * (ED-36) — declarado no relatório.
     */
    @Transactional
    public CleanupRequestView requestCleanup(UUID dumpPoiId) {
        UUID tenantId = tenantId();
        UUID userId = userId();
        WastePointView point = getPoint(dumpPoiId);
        if (!"ILLEGAL_DUMP".equals(point.typeCode())) {
            throw new ApiException.BadRequest("limpeza-invalida",
                    "OS de limpeza e so de ponto de descarte irregular");
        }
        String status = GisWasteRules.asString(point.attributes().get("dump_status"));
        try {
            GisWasteRules.ensureCleanupRequestable(status, hasPhoto(tenantId, dumpPoiId));
            GisWasteRules.ensureDumpTransition(status, "CLEANING");
        } catch (IllegalArgumentException e) {
            throw new ApiException.BadRequest("limpeza-invalida", e.getMessage());
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO gis_waste_cleanup_request (id, tenant_id, dump_poi_id,
                        status, requested_on, created_by, updated_by)
                VALUES (?, ?, ?, 'REQUESTED', CURRENT_DATE, ?, ?)
                """, id, tenantId, dumpPoiId, userId, userId);
        // A situacao do ponto anda junto (ACTIVE/REINCIDENT -> CLEANING).
        jdbc.update("""
                UPDATE gis_poi
                   SET attributes = jsonb_set(attributes, '{dump_status}', '"CLEANING"'),
                       updated_by = ?, updated_at = NOW()
                 WHERE id = ? AND tenant_id = ?
                """, userId, dumpPoiId, tenantId);
        return new CleanupRequestView(id, dumpPoiId, null, "REQUESTED", LocalDate.now());
    }

    // ------------------------------------------------------------- setores

    @Transactional
    public SectorView createSector(SectorInput in) {
        UUID tenantId = tenantId();
        UUID userId = userId();
        try {
            GisWasteRules.ensureSectorAttributes(in.attributes());
        } catch (IllegalArgumentException e) {
            throw new ApiException.BadRequest("setor-invalido", e.getMessage());
        }
        GisWasteCatalog.WasteType type = GisWasteCatalog.requireType("COLLECTION_SECTOR");
        UUID typeId = featureTypeId(tenantId, type);
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO gis_zone (id, tenant_id, code, feature_type_id, name,
                            lifecycle_status, capture_method, attributes, zone_type,
                            derivation, geom, created_by, updated_by)
                    VALUES (?, ?, ?, ?, ?, 'IN_SERVICE', 'DESIGN', ?::jsonb,
                            'COLLECTION_SECTOR', 'DRAWN',
                            ST_Multi(ST_SetSRID(ST_GeomFromGeoJSON(?), 4674)), ?, ?)
                    """, id, tenantId, in.code(), typeId, in.name(),
                    Json.write(in.attributes()), Json.write(in.geometryGeoJson()),
                    userId, userId);
        } catch (DataAccessException e) {
            throw new ApiException.BadRequest("setor-recusado",
                    "Banco recusou o setor de coleta: " + e.getMostSpecificCause().getMessage());
        }
        return new SectorView(id, in.code(), in.name(), in.attributes());
    }

    /**
     * (Re)calcula o vínculo de UM imóvel: ponto do imóvel em
     * consumer_units.coordinates contra os setores em operação
     * (ST_Contains); a decisão é a regra pura (1 = LINKED, 0 =
     * UNLINKED, 2+ = REVIEW). É o vínculo que a taxa da ED-25 lê.
     */
    @Transactional
    public PropertySectorLinkView linkPropertyToSector(UUID propertyUnitId) {
        UUID tenantId = tenantId();
        UUID userId = userId();
        List<UUID> containing = jdbc.queryForList("""
                SELECT z.id
                  FROM gis_zone z
                  JOIN consumer_units cu
                    ON cu.tenant_id = z.tenant_id AND cu.id = ?
                 WHERE z.tenant_id = ? AND z.zone_type = 'COLLECTION_SECTOR'
                   AND z.lifecycle_status = 'IN_SERVICE'
                   AND cu.coordinates IS NOT NULL
                   AND ST_Contains(z.geom, cu.coordinates)
                """, UUID.class, propertyUnitId, tenantId);
        GisWasteRules.PropertySectorDecision decision =
                GisWasteRules.decidePropertySector(containing);
        jdbc.update("""
                INSERT INTO gis_waste_property_sector (id, tenant_id, property_unit_id,
                        sector_zone_id, status, decided_on, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, CURRENT_DATE, ?, ?)
                ON CONFLICT (tenant_id, property_unit_id) DO UPDATE
                    SET sector_zone_id = EXCLUDED.sector_zone_id,
                        status = EXCLUDED.status, decided_on = EXCLUDED.decided_on,
                        updated_by = EXCLUDED.updated_by, updated_at = NOW()
                """, UUID.randomUUID(), tenantId, propertyUnitId,
                decision.sectorZoneId(), decision.status().name(), userId, userId);
        return new PropertySectorLinkView(propertyUnitId, decision.sectorZoneId(),
                decision.status().name(), LocalDate.now());
    }

    /** Leitura do vínculo vigente de um imóvel (consumo da ED-25). */
    public PropertySectorLinkView sectorForProperty(UUID propertyUnitId) {
        UUID tenantId = tenantId();
        List<PropertySectorLinkView> rows = jdbc.query("""
                SELECT property_unit_id, sector_zone_id, status, decided_on
                  FROM gis_waste_property_sector
                 WHERE tenant_id = ? AND property_unit_id = ?
                """, (rs, n) -> new PropertySectorLinkView(
                rs.getObject("property_unit_id", UUID.class),
                rs.getObject("sector_zone_id", UUID.class),
                rs.getString("status"),
                rs.getObject("decided_on", LocalDate.class)),
                tenantId, propertyUnitId);
        if (rows.isEmpty()) {
            throw new ApiException.NotFound(
                    "Imovel sem vinculo de setor calculado: " + propertyUnitId);
        }
        return rows.get(0);
    }

    // ------------------------------------------------------------- rotas

    /**
     * Cria a rota: valida veículo/paradas, lê a projeção de cada
     * parada no vértice mais próximo do grafo (gis_waste_feature_vertex,
     * mantida por gis_waste_project_features do SQL pendente), monta a
     * matriz de distâncias — pgr_dijkstra
     * via função do banco quando o pgRouting está presente; sem a
     * extensão, a função do banco levanta e a rota NÃO nasce com
     * distâncias inventadas —, sequencia com o planner e grava rota
     * e paradas com trecho, extensão e estimativa (esta, só com os
     * parâmetros de quem pede).
     */
    @Transactional
    public RouteView createRoute(RouteInput in) {
        UUID tenantId = tenantId();
        UUID userId = userId();
        try {
            GisWasteRules.ensureVehicle(in.vehicleKind());
        } catch (IllegalArgumentException e) {
            throw new ApiException.BadRequest("rota-invalida", e.getMessage());
        }
        // Tipos reais das paradas (o banco é a autoridade do tipo).
        List<WastePointView> pointViews = new ArrayList<>();
        List<GisWasteRules.RouteStopInput> stops = new ArrayList<>();
        for (int i = 0; i < in.stopPointIds().size(); i++) {
            WastePointView point = getPoint(in.stopPointIds().get(i));
            pointViews.add(point);
            stops.add(new GisWasteRules.RouteStopInput(point.id(), point.typeCode(), i + 1));
        }
        try {
            GisWasteRules.ensureRouteStops(stops);
        } catch (IllegalArgumentException e) {
            throw new ApiException.BadRequest("rota-invalida", e.getMessage());
        }
        // Vértices: depósito + projeção de cada parada (gis_waste_feature_vertex,
        // mantida por gis_waste_project_features do SQL pendente).
        List<Long> vertices = new ArrayList<>();
        vertices.add(in.depotVertex());
        for (int i = 0; i < in.stopPointIds().size(); i++) {
            UUID pointId = in.stopPointIds().get(i);
            List<Long> found = jdbc.queryForList("""
                    SELECT vertex_pgr_id FROM gis_waste_feature_vertex
                     WHERE tenant_id = ? AND feature_table = ? AND feature_id = ?
                    """, Long.class, tenantId, pointViews.get(i).featureTable(), pointId);
            if (found.isEmpty()) {
                throw new ApiException.BadRequest("rota-invalida",
                        "parada sem projecao no grafo de logradouros: " + pointId);
            }
            vertices.add(found.get(0));
        }
        double[][] matrix = distanceMatrixViaPgRouting(tenantId, vertices);
        List<Integer> order;
        try {
            order = GisWasteRoutePlanner.sequenceStops(matrix);
        } catch (IllegalArgumentException e) {
            throw new ApiException.BadRequest("rota-invalida", e.getMessage());
        }
        double extension = GisWasteRoutePlanner.tourCost(matrix, order)
                + GisWasteRoutePlanner.returnCost(matrix, order);
        GisWasteRules.RouteEstimate estimate = GisWasteRules.estimate(
                extension, order.size(), in.avgSpeedKmh(), in.minutesPerStop());
        UUID routeId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO gis_waste_route (id, tenant_id, code, sector_zone_id,
                        vehicle_kind, crew_label, depot_vertex, status,
                        total_extension_m, estimated_minutes, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'PLANNED', ?, ?, ?, ?)
                """, routeId, tenantId, in.code(), in.sectorZoneId(), in.vehicleKind(),
                in.crewLabel(), in.depotVertex(), extension,
                estimate == null ? null : estimate.estimatedMinutes(), userId, userId);
        List<RouteStopView> views = new ArrayList<>();
        int current = 0;
        for (int seq = 0; seq < order.size(); seq++) {
            int matrixIndex = order.get(seq);
            double leg = matrix[current][matrixIndex];
            UUID pointId = in.stopPointIds().get(matrixIndex - 1);
            String typeCode = stops.get(matrixIndex - 1).pointTypeCode();
            String featureTable = pointViews.get(matrixIndex - 1).featureTable();
            jdbc.update("""
                    INSERT INTO gis_waste_route_stop (id, tenant_id, route_id,
                            feature_table, feature_id, sequence, leg_cost_m,
                            created_by, updated_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), tenantId, routeId, featureTable, pointId,
                    seq + 1, leg, userId, userId);
            views.add(new RouteStopView(pointId, typeCode, seq + 1, leg));
            current = matrixIndex;
        }
        return new RouteView(routeId, in.code(), in.sectorZoneId(), in.vehicleKind(),
                "PLANNED", extension,
                estimate == null ? null : estimate.estimatedMinutes(), views);
    }

    /**
     * Matriz por pgr_dijkstra (função gis_waste_pgr_distance do SQL
     * pendente, que exige a extensão pgrouting e levanta sem ela).
     */
    private double[][] distanceMatrixViaPgRouting(UUID tenantId, List<Long> vertices) {
        int n = vertices.size();
        double[][] matrix = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    matrix[i][j] = 0.0;
                    continue;
                }
                Double cost = jdbc.queryForObject(
                        "SELECT gis_waste_pgr_distance(?, ?, ?)",
                        Double.class, tenantId, vertices.get(i), vertices.get(j));
                matrix[i][j] = cost == null ? Double.POSITIVE_INFINITY : cost;
            }
        }
        return matrix;
    }

    // ------------------------------------------------------------- símbolos

    /**
     * Manifesto de símbolos dos tipos ATIVOS do tenant contra a
     * biblioteca: presentInLibrary=false é o estado esperado até a
     * P1.30 entregar a arte (marcador declarado em GisWasteSymbols);
     * a lista de chaves da biblioteca vem da GIS-06 (style_key dos
     * tipos) na integração — aqui, do conjunto informado pelo
     * chamador interno.
     */
    public List<SymbolManifestView> symbolManifest(Set<String> librarySymbolKeys) {
        List<SymbolManifestView> out = new ArrayList<>();
        for (GisWasteCatalog.WasteType type : GisWasteCatalog.TYPES) {
            for (String state : GisWasteSymbols.SYMBOL_STATES) {
                String key = GisWasteSymbols.symbolKeyFor(type, state);
                out.add(new SymbolManifestView(type.code(), state, key,
                        librarySymbolKeys.contains(key)));
            }
        }
        return out;
    }

    // ------------------------------------------------------------- helpers

    private UUID featureTypeId(UUID tenantId, GisWasteCatalog.WasteType type) {
        List<UUID> rows = jdbc.queryForList("""
                SELECT id FROM gis_feature_type
                 WHERE tenant_id = ? AND factory_code = ?
                """, UUID.class, tenantId, type.factoryCode());
        if (rows.isEmpty()) {
            throw new ApiException.BadRequest("catalogo-ausente",
                    "tipo da GIS-14 nao carregado para o tenant: " + type.factoryCode()
                            + " (catalogo do §4.5 pendente de aplicacao)");
        }
        return rows.get(0);
    }

    private static UUID tenantId() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("sem tenant no contexto da requisicao");
        }
        return tenantId;
    }

    private static UUID userId() {
        UUID userId = TenantContextHolder.getUserId();
        if (userId == null) {
            throw new IllegalStateException("sem usuario no contexto da requisicao");
        }
        return userId;
    }
}
