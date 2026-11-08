package br.com.ziipsan.intelligence.gis.thematic;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.intelligence.gis.thematic.GisThematicDtos.PolygonStatsView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Seleção por polígono com estatísticas (TR-1059): ligações,
 * economias e consumo médio das unidades dentro do polígono
 * desenhado no mapa. O polígono chega como GeoJSON (RFC 7946,
 * lon/lat) e é lido pela classe {@link Json} do core antes de virar
 * geometria no banco ({@code ST_GeomFromGeoJSON} com SRID 4674 —
 * SIRGAS 2000, o sistema de armazenamento do §3.9; a diferença
 * para WGS-84 é desprezível para esta finalidade, como o próprio
 * §3.9 registra para o intercâmbio).
 *
 * <p>As estatísticas são agregados de unidades de terceiros: a
 * operação exige {@code gis.view-personal}, como toda leitura de
 * camada pessoal, e a resposta não traz nenhuma unidade
 * individual — só os quatro agregados e a área.
 */
@Service
@Transactional(readOnly = true)
public class GisSelectionService {

    private final JdbcTemplate jdbc;
    private final BooleanSupplier canViewPersonal;

    public GisSelectionService(JdbcTemplate jdbc) {
        this(jdbc, GisThematicContext::canViewPersonal);
    }

    GisSelectionService(JdbcTemplate jdbc, BooleanSupplier canViewPersonal) {
        this.jdbc = jdbc;
        this.canViewPersonal = canViewPersonal;
    }

    public PolygonStatsView statistics(String polygonGeoJson) {
        if (!canViewPersonal.getAsBoolean()) {
            throw new ApiException.Forbidden(
                    "Seleção por polígono: exige a permissão gis.view-personal.");
        }
        validatePolygon(polygonGeoJson);
        UUID tenantId = GisThematicContext.tenantId();
        return jdbc.queryForObject(GisThematicSql.polygonStatsSql(), (rs, n) ->
                new PolygonStatsView(
                        rs.getLong("units"),
                        rs.getLong("connections"),
                        rs.getLong("economies"),
                        rs.getBigDecimal("avg_consumption"),
                        rs.getBigDecimal("area_m2")),
                polygonGeoJson, tenantId, tenantId, tenantId);
    }

    /** Só Polygon com anel de pelo menos 4 posições entra; o resto é 400. */
    static void validatePolygon(String polygonGeoJson) {
        if (polygonGeoJson == null || polygonGeoJson.isBlank()) {
            throw new ApiException.BadRequest("polygon-required", "Polígono não informado.");
        }
        var root = Json.readTree(polygonGeoJson);
        if (!"Polygon".equals(root.path("type").asString())) {
            throw new ApiException.BadRequest("polygon-invalid",
                    "A geometria da seleção deve ser um GeoJSON Polygon.");
        }
        var ring = root.path("coordinates").path(0);
        if (!ring.isArray() || ring.size() < 4) {
            throw new ApiException.BadRequest("polygon-invalid",
                    "O polígono precisa de um anel com pelo menos 4 posições.");
        }
    }
}
