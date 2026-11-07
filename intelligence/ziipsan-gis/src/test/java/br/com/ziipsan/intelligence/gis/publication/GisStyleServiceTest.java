package br.com.ziipsan.intelligence.gis.publication;

import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.intelligence.gis.publication.GisPublicationDtos.LayerView;
import br.com.ziipsan.intelligence.gis.publication.GisPublicationDtos.StyleOverrideInput;
import br.com.ziipsan.intelligence.gis.publication.GisPublicationDtos.StyleRowView;
import br.com.ziipsan.intelligence.gis.publication.GisStyleRules.StyleRow;
import br.com.ziipsan.intelligence.gis.publication.GisStyleService.TypeInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GIS-06 (P3.25), correções pós-revisão no SERVIÇO de estilos —
 * o ponto onde a revisão encontrou as falhas silenciosas:
 * (A2) a resolução que gera o documento MapLibre era chamada com
 * tipo e accuracy NULOS, então o override por tipo não tinha
 * efeito e o halo OUT_OF_TARGET nunca era emitido (o teste antigo
 * da fábrica montava o estilo resolvido à mão e mascarava os
 * dois). Aqui a resolução REAL do serviço ({@code buildLayerDef})
 * alimenta a fábrica e o documento é o que se afirma;
 * (M3) o SQL da biblioteca de símbolos filtra por pacote ativo
 * quando a view gis_domain_visible existe;
 * (M4) o caminho do {@code saveOverride} monta o upsert com o
 * alvo de conflito do índice de expressões (a prova contra o
 * PostgreSQL real é a Gis06PublicationIT.styleOverrideUpsert...).
 */
class GisStyleServiceTest {

    private static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID USER = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID VALVE_TYPE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID HYDRANT_TYPE = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    /** Linhas de fábrica espelhando as sementes da V13_12 (domínio WATER). */
    private static List<StyleRow> factoryRows() {
        List<StyleRow> rows = new ArrayList<>();
        rows.add(new StyleRow("FACTORY", "WATER", null, "IN_SERVICE", null,
                "map-water", 2.0, "[]", false, null, null, null));
        rows.add(new StyleRow("FACTORY", "WATER", null, "PLANNED", null,
                "map-water", 2.0, "[3, 2]", false, null, null, null));
        rows.add(new StyleRow("FACTORY", "WATER", null, "UNDER_CONSTRUCTION", null,
                "map-water", 2.0, "[4, 2, 1, 2]", false, null, null, null));
        rows.add(new StyleRow("FACTORY", "WATER", null, "ABANDONED", null,
                "map-abandoned", 1.0, "[2, 2]", false, null, null, null));
        // A linha de accuracy da V13_12: fora da meta ganha halo.
        rows.add(new StyleRow("FACTORY", "WATER", null, "IN_SERVICE", "OUT_OF_TARGET",
                null, null, null, true, null, null, null));
        return rows;
    }

    private static Map<UUID, TypeInfo> types() {
        Map<UUID, TypeInfo> types = new LinkedHashMap<>();
        types.put(VALVE_TYPE, new TypeInfo("VALVE", "Válvula", "WATER",
                "valve-symbol", "ARC"));
        types.put(HYDRANT_TYPE, new TypeInfo("HYDRANT", "Hidrante", "WATER",
                "hydrant-symbol", "NODE"));
        return types;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> layerById(Map<String, Object> style, String id) {
        List<Map<String, Object>> layers = (List<Map<String, Object>>) style.get("layers");
        return layers.stream().filter(l -> id.equals(l.get("id"))).findFirst()
                .orElseThrow(() -> new AssertionError("camada " + id + " não emitida"));
    }

    @Test
    @DisplayName("A2: resolução do serviço emite o halo OUT_OF_TARGET no documento")
    void serviceResolutionEmitsHalo() {
        var layer = GisStyleService.buildLayerDef(
                new LayerView("water-arcs", "Água — arcos", "Água", false, 0, 1, true),
                GisTileLayers.WATER_ARCS, factoryRows(),
                Map.of("WATER", "map-water"), types());
        Map<String, Object> style = GisMapLibreStyleFactory.buildStyle(
                "/api/v1", "light", List.of(layer), List.of(), List.of());
        Map<String, Object> halo = layerById(style, "water-arcs-halo-in_service");
        assertThat(halo.get("filter").toString()).contains("OUT_OF_TARGET");
        // Sem linha de accuracy para PLANNED, o halo não vaza para ele:
        // a linha de domínio (halo=false) é a aplicável.
        List<?> layers = (List<?>) style.get("layers");
        assertThat(layers.toString()).doesNotContain("water-arcs-halo-planned");
    }

    @Test
    @DisplayName("A2: override do tenant POR TIPO vira match por type_code (cor e ícone)")
    void tenantTypeOverrideFlowsToDocument() {
        List<StyleRow> rows = factoryRows();
        rows.add(new StyleRow("TENANT", null, VALVE_TYPE, "IN_SERVICE", null,
                "map-sewer", null, null, null, "valve-symbol", null, null));
        rows.add(new StyleRow("TENANT", null, HYDRANT_TYPE, "IN_SERVICE", null,
                null, null, null, null, "hydrant-symbol", null, null));

        var arcs = GisStyleService.buildLayerDef(
                new LayerView("water-arcs", "Água — arcos", "Água", false, 0, 1, true),
                GisTileLayers.WATER_ARCS, rows, Map.of("WATER", "map-water"), types());
        var nodes = GisStyleService.buildLayerDef(
                new LayerView("water-nodes", "Água — nós", "Água", false, 0, 1, true),
                GisTileLayers.WATER_NODES, rows, Map.of("WATER", "map-water"), types());
        Map<String, Object> style = GisMapLibreStyleFactory.buildStyle(
                "/api/v1", "light", List.of(arcs, nodes), List.of(), List.of());

        @SuppressWarnings("unchecked")
        Map<String, Object> arcPaint = (Map<String, Object>)
                layerById(style, "water-arcs-in_service").get("paint");
        assertThat(arcPaint.get("line-color").toString())
                .contains("match").contains("type_code")
                .contains("VALVE").contains("token:map-sewer")
                .contains("token:map-water");

        @SuppressWarnings("unchecked")
        Map<String, Object> symbolLayout = (Map<String, Object>)
                layerById(style, "water-nodes-symbol-in_service").get("layout");
        assertThat(symbolLayout.get("icon-image").toString())
                .contains("match").contains("HYDRANT").contains("hydrant-symbol");
    }

    @Test
    @DisplayName("M3: SQL da biblioteca filtra por gis_domain_visible só quando a view existe")
    void symbolsSqlFiltersByActivePackage() {
        assertThat(GisStyleService.symbolsSql(true)).contains("gis_domain_visible")
                .contains("dv.key = fc.domain");
        assertThat(GisStyleService.symbolsSql(false)).doesNotContain("gis_domain_visible");
    }

    /** JdbcTemplate falso do saveOverride: captura o upsert e devolve a linha gravada. */
    static final class FakeJdbc extends JdbcTemplate {
        final List<Object[]> updates = new ArrayList<>();
        final List<String> updateSqls = new ArrayList<>();

        @Override
        public int update(String sql, Object... args) {
            updateSqls.add(sql);
            updates.add(args);
            return 1;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            // to_regclass('gis_domain_visible') -> 0 (sem a view).
            return (T) Integer.valueOf(0);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, Class<T> requiredType) {
            return (T) Integer.valueOf(0);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            if (sql.contains("FROM gis_style")) {
                Object[] last = updates.get(updates.size() - 1);
                StyleRowView view = new StyleRowView(UUID.randomUUID(), "TENANT",
                        (String) last[2], (UUID) last[3], null,
                        (String) last[4], (String) last[5], (String) last[6],
                        (Double) last[7], null, (Boolean) last[9],
                        (String) last[10], (String) last[11], (Integer) last[12]);
                return (List<T>) List.of(view);
            }
            return List.of();
        }
    }

    @Test
    @DisplayName("M4: saveOverride grava duas vezes no mesmo escopo pelo upsert de expressões")
    void saveOverrideUpsertsSameScope() {
        TenantContextHolder.setTenantId(TENANT);
        TenantContextHolder.setUserId(USER);
        FakeJdbc jdbc = new FakeJdbc();
        GisStyleService service = new GisStyleService(jdbc, "/api/v1");

        StyleRowView first = service.saveOverride(new StyleOverrideInput(
                null, "WATER", "IN_SERVICE", null, "map-sewer",
                null, null, null, null, null, null));
        StyleRowView second = service.saveOverride(new StyleOverrideInput(
                null, "WATER", "IN_SERVICE", null, "map-storm",
                null, null, null, null, null, null));

        assertThat(first.colorToken()).isEqualTo("map-sewer");
        assertThat(second.colorToken()).isEqualTo("map-storm");
        assertThat(jdbc.updates).hasSize(2);
        for (String sql : jdbc.updateSqls) {
            assertThat(sql).contains("ON CONFLICT (tenant_id, origin")
                    .contains("COALESCE(domain_key, '')")
                    .contains("COALESCE(feature_type_id,")
                    .contains("COALESCE(accuracy_status, '')")
                    .contains("DO UPDATE SET color_token = EXCLUDED.color_token");
        }
        // Mesmo escopo nas duas gravações (domínio, tipo, estado, accuracy).
        assertThat(jdbc.updates.get(0)[2]).isEqualTo(jdbc.updates.get(1)[2]);
        assertThat(jdbc.updates.get(0)[4]).isEqualTo(jdbc.updates.get(1)[4]);
    }
}
