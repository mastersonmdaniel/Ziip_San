package br.com.ziipsan.intelligence.gis.publication;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GIS-06 (P3.25): o SQL do tile (§10.1) — ST_AsMVT sobre
 * ST_TileEnvelope, filtro de tenant sempre, zoom mínimo por tipo,
 * contexto sem dado pessoal, imóveis agregados em zoom baixo e a
 * invariante parâmetros × args (um {@code ?} fora do lugar só
 * apareceria em produção).
 */
class GisTileSqlTest {

    private static final UUID TENANT = UUID.randomUUID();

    private static long placeholders(String sql) {
        return sql.chars().filter(c -> c == '?').count();
    }

    @Test
    @DisplayName("FEATURE: ST_AsMVT/ST_AsMVTGeom/ST_TileEnvelope e tenant explícito")
    void featureSqlShape() {
        String sql = GisTileSql.tileSql(GisTileLayers.WATER_ARCS, 15);
        assertThat(sql).contains("ST_AsMVT").contains("ST_AsMVTGeom")
                .contains("ST_TileEnvelope").contains("f.tenant_id = ?")
                .contains("fc.domain = 'WATER'").contains("ft.min_zoom");
        assertThat(sql).doesNotContain("SEWER");
    }

    @Test
    @DisplayName("FEATURE: atributos mínimos do §10.1 incluem o estado operacional (correção M1)")
    void featureSqlHasOperationalStatus() {
        for (GisTileLayers layer : GisTileLayers.values()) {
            if (layer.sourceKind() == GisTileLayers.SourceKind.FEATURE) {
                assertThat(GisTileSql.tileSql(layer, 15))
                        .as("%s", layer.key())
                        .contains("f.operational_status AS operational_status")
                        .contains("f.lifecycle_status AS lifecycle_status")
                        .contains("f.accuracy_status AS accuracy_status");
            }
        }
    }

    @Test
    @DisplayName("camada sem domínio não filtra domínio")
    void domainFreeLayerHasNoDomainFilter() {
        String sql = GisTileSql.tileSql(GisTileLayers.STRUCTURES, 15);
        assertThat(sql).doesNotContain("fc.domain =");
    }

    @Test
    @DisplayName("contexto exclui personal_data")
    void contextExcludesPersonalData() {
        String sql = GisTileSql.tileSql(GisTileLayers.CONTEXT, 15);
        assertThat(sql).contains("NOT c.personal_data");
    }

    @Test
    @DisplayName("imóveis: agregados por grade até z14; pontos depois")
    void propertiesAggregateAtLowZoom() {
        assertThat(GisTileSql.tileSql(GisTileLayers.PROPERTIES, 10))
                .contains("ST_SnapToGrid").contains("unit_count");
        assertThat(GisTileSql.tileSql(GisTileLayers.PROPERTIES, 16))
                .doesNotContain("ST_SnapToGrid")
                .contains("consumer_units");
    }

    @Test
    @DisplayName("args casam com os placeholders de cada camada e zoom")
    void argsMatchPlaceholders() {
        for (GisTileLayers layer : GisTileLayers.values()) {
            for (int z : new int[]{0, 10, 14, 15, 18}) {
                String sql = GisTileSql.tileSql(layer, z);
                Object[] args = GisTileSql.tileArgs(layer, TENANT, z, 1, 1);
                assertThat(args).as("%s em z=%d", layer.key(), z)
                        .hasSize((int) placeholders(sql));
            }
        }
    }

    @Test
    @DisplayName("catálogo: pessoais são imóveis e ligações; ramais só de z17")
    void catalogPersonalAndZoom() {
        assertThat(GisTileLayers.SERVICE_LINES.personal()).isTrue();
        assertThat(GisTileLayers.PROPERTIES.personal()).isTrue();
        assertThat(GisTileLayers.WATER_ARCS.personal()).isFalse();
        assertThat(GisTileLayers.SERVICE_LINES.servesAt(16)).isFalse();
        assertThat(GisTileLayers.SERVICE_LINES.servesAt(17)).isTrue();
        assertThat(GisTileLayers.visibleFor(false))
                .noneMatch(GisTileLayers::personal)
                .hasSize(GisTileLayers.values().length - 2);
        assertThat(GisTileLayers.visibleFor(true))
                .hasSize(GisTileLayers.values().length);
        assertThat(GisTileLayers.byKey("water-arcs"))
                .contains(GisTileLayers.WATER_ARCS);
        assertThat(GisTileLayers.byKey("nada")).isEmpty();
    }
}
