package br.com.ziipsan.intelligence.gis.thematic;

import br.com.ziipsan.intelligence.gis.thematic.GisThematicDtos.ClassDef;
import br.com.ziipsan.intelligence.gis.thematic.GisThematicDtos.LegendEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GIS-08 (P3.27): as regras puras — classes pela classe Json do
 * core, validação contra a fonte, legenda automática, classificação
 * numérica [min, max), configuração pendente e o piso da agregação
 * pública (classe c, piso 5).
 */
class GisThematicRulesTest {

    private static ClassDef cat(String key) {
        return new ClassDef(key, "Rótulo " + key, "map-thematic-1", null, null);
    }

    private static ClassDef range(String key, String min, String max) {
        return new ClassDef(key, "Rótulo " + key, "map-thematic-1",
                min == null ? null : new BigDecimal(min),
                max == null ? null : new BigDecimal(max));
    }

    @Test
    @DisplayName("classes: ida e volta pelo Json do core preserva chave, rótulo, token e faixa")
    void classesRoundTrip() {
        List<ClassDef> classes = List.of(cat("BELOW_AVERAGE"), range("BIG", "50", null));
        String json = GisThematicRules.writeClasses(classes);
        assertThat(json).startsWith("[").contains("BELOW_AVERAGE").doesNotContain("=,");
        assertThat(GisThematicRules.parseClasses(json))
                .containsExactlyElementsOf(classes);
        assertThat(GisThematicRules.parseClasses(null)).isEmpty();
        assertThat(GisThematicRules.parseClasses("[]")).isEmpty();
    }

    @Test
    @DisplayName("classes: JSON inválido lança — nunca vira lista vazia em silêncio")
    void classesInvalidJsonThrows() {
        assertThatThrownBy(() -> GisThematicRules.parseClasses("{quebrado"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> GisThematicRules.parseClasses("{}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("validação: definição categórica sadia não tem problemas")
    void validateOk() {
        assertThat(GisThematicRules.validateDefinition("consumo-vs-media",
                GisThematicSources.UNIT_CONSUMPTION, "comparison", null,
                List.of(cat("BELOW_AVERAGE")), "{}")).isEmpty();
    }

    @Test
    @DisplayName("validação: campo fora da fonte, token fora de map-* e filtro fora da lista são recusados")
    void validateRejectsOutsideSource() {
        List<String> problems = GisThematicRules.validateDefinition("x-tema",
                GisThematicSources.UNIT_CONSUMPTION, "system_role", null,
                List.of(new ClassDef("A", "A", "red", null, null)),
                "{\"status\": \"OPEN\"}");
        assertThat(problems).hasSizeGreaterThanOrEqualTo(3);
        assertThat(String.join(";", problems))
                .contains("system_role").contains("map-*").contains("status");
    }

    @Test
    @DisplayName("validação: faixas numéricas sobrepostas ou invertidas são recusadas; encostadas passam")
    void validateNumericRanges() {
        assertThat(GisThematicRules.validateDefinition("grandes",
                GisThematicSources.UNIT_CONSUMPTION, null, "billed_consumption",
                List.of(range("MEDIO", "10", "50"), range("GRANDE", "40", null)), "{}"))
                .isNotEmpty();
        assertThat(GisThematicRules.validateDefinition("grandes",
                GisThematicSources.UNIT_CONSUMPTION, null, "billed_consumption",
                List.of(range("MEDIO", "10", "50"), range("GRANDE", "50", null)), "{}"))
                .isEmpty();
        assertThat(GisThematicRules.validateDefinition("grandes",
                GisThematicSources.UNIT_CONSUMPTION, null, "billed_consumption",
                List.of(range("MEDIO", "50", "10")), "{}")).isNotEmpty();
    }

    @Test
    @DisplayName("classificação numérica: [min, max) — o limite inferior entra, o superior não")
    void classifyNumericBounds() {
        List<ClassDef> classes = List.of(range("MEDIO", "10", "50"), range("GRANDE", "50", null));
        assertThat(GisThematicRules.classifyNumeric(classes, new BigDecimal("9"))).isNull();
        assertThat(GisThematicRules.classifyNumeric(classes, new BigDecimal("10"))).isEqualTo("MEDIO");
        assertThat(GisThematicRules.classifyNumeric(classes, new BigDecimal("49"))).isEqualTo("MEDIO");
        assertThat(GisThematicRules.classifyNumeric(classes, new BigDecimal("50"))).isEqualTo("GRANDE");
        assertThat(GisThematicRules.classifyNumeric(classes, null)).isNull();
    }

    @Test
    @DisplayName("legenda automática: com classes sai delas (com zero); sem classes nasce dos dados")
    void legendAutomatic() {
        List<LegendEntry> declared = GisThematicRules.legend(
                List.of(cat("BELOW_AVERAGE"), cat("ABOVE_AVERAGE")),
                Map.of("BELOW_AVERAGE", 7L));
        assertThat(declared).extracting(LegendEntry::key)
                .containsExactly("BELOW_AVERAGE", "ABOVE_AVERAGE");
        assertThat(declared).extracting(LegendEntry::count).containsExactly(7L, 0L);

        List<LegendEntry> dynamic = GisThematicRules.legend(List.of(),
                Map.of("VALVE", 3L, "HYDRANT", 1L));
        assertThat(dynamic).extracting(LegendEntry::key).containsExactly("HYDRANT", "VALVE");
    }

    @Test
    @DisplayName("configuração pendente: SG-877 sem tipos do tenant e SG-882 sem faixas; configurados rendem")
    void requiresConfigurationCases() {
        assertThat(GisThematicRules.requiresConfiguration(GisThematicSources.SERVICE_ORDER,
                "service_type_code", null, List.of(),
                "{\"statuses\":[\"OPEN\"],\"serviceTypeCodes\":[]}")).isTrue();
        assertThat(GisThematicRules.requiresConfiguration(GisThematicSources.SERVICE_ORDER,
                "service_type_code", null, List.of(),
                "{\"statuses\":[\"OPEN\"],\"serviceTypeCodes\":[\"LEAK_METER\"]}")).isFalse();
        assertThat(GisThematicRules.requiresConfiguration(GisThematicSources.SERVICE_ORDER,
                "service_type_code", null, List.of(), "{\"statuses\":[\"OPEN\"]}")).isFalse();
        assertThat(GisThematicRules.requiresConfiguration(GisThematicSources.UNIT_CONSUMPTION,
                null, "billed_consumption", List.of(), "{}")).isTrue();
        assertThat(GisThematicRules.requiresConfiguration(GisThematicSources.UNIT_CONSUMPTION,
                null, "billed_consumption", List.of(range("GRANDE", "50", null)), "{}")).isFalse();
    }

    @Test
    @DisplayName("agregação pública: piso 5 de classe c reaplicado; célula de 4 nunca publica")
    void aggregationFloor() {
        assertThat(GisThematicRules.effectiveMinUnits(null)).isEqualTo(5);
        assertThat(GisThematicRules.effectiveMinUnits(3)).isEqualTo(5);
        assertThat(GisThematicRules.effectiveMinUnits(5)).isEqualTo(5);
        assertThat(GisThematicRules.effectiveMinUnits(12)).isEqualTo(12);
        assertThat(GisThematicRules.cellPublishable(4, 5)).isFalse();
        assertThat(GisThematicRules.cellPublishable(5, 5)).isTrue();
        assertThat(GisThematicRules.cellPublishable(11, 12)).isFalse();
    }
}
