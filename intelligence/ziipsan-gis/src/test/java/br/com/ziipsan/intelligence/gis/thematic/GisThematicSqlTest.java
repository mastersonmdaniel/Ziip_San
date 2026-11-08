package br.com.ziipsan.intelligence.gis.thematic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GIS-08 (P3.27): o SQL montado — tenant explícito sempre, valores
 * só como parâmetro, e a invariante de segurança do canal público:
 * o SQL agregado de fonte pessoal não seleciona ponto, geometria
 * de unidade nem id de unidade, e aplica o mínimo por célula.
 */
class GisThematicSqlTest {

    private static long placeholders(String sql) {
        return sql.chars().filter(c -> c == '?').count();
    }

    @Test
    @DisplayName("pontos: view da lista fechada, tenant explícito, categoria com cast e limite parametrizado")
    void pointsSqlShape() {
        var built = GisThematicSql.pointsSql(GisThematicSources.UNIT_CONSUMPTION,
                "comparison", null,
                Map.of("consumption_type", "REAL"), "RESIDENTIAL", 101);
        assertThat(built.sql())
                .contains("FROM gis_thematic_unit_consumption")
                .contains("tenant_id = ?")
                .contains("comparison::text AS category")
                .contains("category_code = ?")
                .contains("ST_Transform(geom, 4326)")
                .contains("LIMIT ?");
        assertThat(placeholders(built.sql())).isEqualTo(built.args().size());
        assertThat(built.args()).containsExactly("__TENANT__", "REAL", "RESIDENTIAL", 101);
    }

    @Test
    @DisplayName("pontos de rede: ponto é o centroide do arco (o traço é do tile da GIS-06)")
    void networkPointsUseCentroid() {
        var built = GisThematicSql.pointsSql(GisThematicSources.NETWORK_ARC,
                "lifecycle_status", null, Map.of("domain", "WATER"), null, 10);
        assertThat(built.sql()).contains("ST_Centroid(geom)")
                .contains("FROM gis_thematic_network_arc");
        assertThat(built.sql()).doesNotContain("category_code = ?");
        assertThat(placeholders(built.sql())).isEqualTo(built.args().size());
    }

    @Test
    @DisplayName("público: agregado por célula com HAVING do mínimo e NENHUM ponto/id de unidade selecionado")
    void publicAggregateSqlHasNoPoints() {
        var built = GisThematicSql.publicAggregateSql(GisThematicSources.UNIT_CONSUMPTION,
                "comparison", Map.of(), "SECTOR", 5);
        assertThat(built.sql())
                .contains("COUNT(DISTINCT unit_id)")
                .contains("HAVING COUNT(DISTINCT unit_id) >= ?")
                .contains("sector_id");
        // A célula sai; o ponto, o id e a geometria da unidade, nunca.
        assertThat(built.sql())
                .doesNotContain("ST_X").doesNotContain("ST_Y")
                .doesNotContain("ST_AsGeoJSON").doesNotContain("geom")
                .doesNotContain("unit_id::text");
        assertThat(placeholders(built.sql())).isEqualTo(built.args().size());
        assertThat(built.args()).containsExactly("__TENANT__", 5);
    }

    @Test
    @DisplayName("público por quadra: célula é o block_id e filtro da definição entra parametrizado")
    void publicAggregateByBlock() {
        var built = GisThematicSql.publicAggregateSql(GisThematicSources.CONNECTION_STATUS,
                "system_role", Map.of("system_role", List.of("CUT", "SUSPENDED")), "BLOCK", 7);
        assertThat(built.sql()).contains("block_id")
                .contains("system_role IN (?, ?)");
        assertThat(placeholders(built.sql())).isEqualTo(built.args().size());
    }

    @Test
    @DisplayName("filtros: lista vazia de códigos nunca vira IN () — é ignorada no SQL")
    void emptyListFilterIsSkipped() {
        var built = GisThematicSql.pointsSql(GisThematicSources.SERVICE_ORDER,
                "service_type_code", null,
                Map.of("statuses", List.of("OPEN", "SCHEDULED"),
                        "serviceTypeCodes", List.of()),
                null, 10);
        assertThat(built.sql()).contains("status IN (?, ?)")
                .doesNotContain("service_type_code IN ()");
        assertThat(placeholders(built.sql())).isEqualTo(built.args().size());
    }

    @Test
    @DisplayName("relatórios e polígono: função na data, precisão agrupada e GeoJSON parametrizado")
    void reportAndPolygonSql() {
        assertThat(GisThematicSql.extensionAtSql())
                .contains("gis_network_extension_at(?)");
        assertThat(GisThematicSql.precisionSummarySql())
                .contains("FROM rpt_gis_precisao_cadastro")
                .contains("tenant_id = ?")
                .contains("GROUP BY classe, metodo_captura, grupo_precisao, dominio");
        String polygon = GisThematicSql.polygonStatsSql();
        assertThat(polygon).contains("ST_GeomFromGeoJSON(?)")
                .contains("gis_thematic_unit_consumption")
                .contains("service_connections");
        assertThat(placeholders(polygon)).isEqualTo(4);
    }
}
