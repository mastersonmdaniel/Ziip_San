package br.com.ziipsan.intelligence.gis.thematic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GIS-08 (P3.27): a V13_13 contra o aceite e contra o código — a
 * tabela e as views que o {@link GisThematicSources} e os
 * relatórios declaram têm de existir com esses nomes, as sementes
 * dos cenários têm de estar lá (sem limiar inventado no SG-882 e
 * sem código de OS inventado no SG-877/878/879) e toda view sai
 * com {@code security_invoker}.
 */
class GisThematicMigrationConsistencyTest {

    private static String migration() {
        String path = "/db/migration/tenant/V13_13__gis_thematic_maps.sql";
        try (InputStream in = GisThematicMigrationConsistencyTest.class.getResourceAsStream(path)) {
            assertThat(in).as("migration no classpath: " + path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("tabela gis_thematic_map registrada, com unicidade por tenant e a trava pessoal x agregação")
    void thematicTable() {
        String sql = migration();
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS gis_thematic_map")
                .contains("register_tenant_table('gis_thematic_map')")
                .contains("uq_gis_thematic_map_tenant_key UNIQUE (tenant_id, key)")
                .contains("ck_gis_thematic_map_personal_aggregation")
                .contains("set_config('app.current_tenant', '${tenant_id}', true)");
    }

    @Test
    @DisplayName("toda fonte do GisThematicSources é uma view da migration, com security_invoker")
    void sourceViewsExist() {
        String sql = migration();
        for (GisThematicSources source : GisThematicSources.values()) {
            assertThat(sql).contains("CREATE OR REPLACE VIEW " + source.viewName());
        }
        assertThat(sql).contains("CREATE OR REPLACE VIEW gis_thematic_unit_base");
        assertThat(sql.split("security_invoker = true").length - 1)
                .isGreaterThanOrEqualTo(10);
    }

    @Test
    @DisplayName("relatórios: views rpt_* do núcleo e a função de extensão em qualquer data")
    void reportViewsAndFunction() {
        String sql = migration();
        assertThat(sql).contains("CREATE OR REPLACE VIEW rpt_gis_extensao_rede")
                .contains("CREATE OR REPLACE VIEW rpt_gis_precisao_cadastro")
                .contains("CREATE OR REPLACE FUNCTION gis_network_extension_at(p_date date)")
                .contains("SURVEYED_WITHIN_TARGET")
                .contains("SURVEYED_OUT_OF_TARGET")
                .contains("APPROXIMATE");
    }

    @Test
    @DisplayName("sementes dos cenários do aceite: SG-873 a SG-883, TR-1062/1063 e precisão")
    void factorySeeds() {
        String sql = migration();
        for (String key : List.of("consumo-vs-media", "consumo-faturado-media",
                "consumo-lido-zero", "ligacoes-cortadas", "os-no-mapa",
                "os-vazamento-cavalete", "os-vazamento-rede", "os-falta-agua-qualidade",
                "hidrometros-idade-tipo", "faturas-vencidas-60", "grandes-consumidores",
                "rede-agua-operacao", "rede-esgoto-operacao", "precisao-cadastro")) {
            assertThat(sql).contains("'" + key + "'");
        }
        // SG-881: os 60 dias são o texto do cenário, não um parâmetro
        // (na view gis_thematic_unit_overdue a coluna vem da subconsulta
        // de bills, sem alias — o texto real da migration).
        assertThat(sql).contains("due_date < CURRENT_DATE - 60");
        // SG-876: o vocabulário é o system_role da V3_14 e aparece com
        // aspas duplas, dentro dos JSON das sementes (classes e filtro).
        assertThat(sql).contains("\"CUT\"").contains("\"SUSPENDED\"")
                .contains("\"DISCONNECTED\"");
    }

    @Test
    @DisplayName("view-base: as duas subconsultas de unit_subcategories aplicam o predicado de vigência da V3_12/ED-13")
    void unitBaseAppliesSubcategoryValidity() {
        String sql = migration();
        assertThat(sql.split("us\\.valid_from <= CURRENT_DATE").length - 1).isEqualTo(2);
        assertThat(sql.split("us\\.valid_to IS NULL OR us\\.valid_to >= CURRENT_DATE").length - 1)
                .isEqualTo(2);
    }

    @Test
    @DisplayName("sem dado inventado: SG-882 sem classes e SG-877/878/879 com serviceTypeCodes vazio")
    void noInventedSeeds() {
        String sql = migration();
        // grandes consumidores: classes_json vazio na semente.
        assertThat(sql).contains("'grandes-consumidores'")
                .contains("'[]'::jsonb, '{}'::jsonb, 'FACTORY', TRUE)");
        // cenários de OS de vazamento/falta: a lista de tipos nasce vazia.
        assertThat(sql.split("\"serviceTypeCodes\":\\[\\]").length - 1).isEqualTo(3);
    }

    @Test
    @DisplayName("precisão: os grupos separam levantado na meta, fora da meta e aproximado nas seis tabelas")
    void precisionGroups() {
        String sql = migration();
        for (String table : List.of("gis_node", "gis_arc", "gis_connection",
                "gis_structure", "gis_zone", "gis_poi")) {
            assertThat(sql).contains("FROM " + table + " f");
        }
        assertThat(sql).contains("METER_AVERAGE");
    }
}
