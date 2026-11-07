package br.com.ziipsan.intelligence.gis.publication;

import br.com.ziipsan.intelligence.gis.publication.GisStyleRules.ResolvedStyle;
import br.com.ziipsan.intelligence.gis.publication.GisStyleRules.StyleRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GIS-06 (P3.25): simbologia (§10.2) — padrão Ziip por estado,
 * resolução campo a campo do override do tenant sobre a fábrica e
 * a validação do override (token map-*, símbolo só da biblioteca).
 */
class GisStyleRulesTest {

    private static final UUID TYPE_VALVE = UUID.randomUUID();

    private static StyleRow factoryDomain(String lifecycle, String color,
                                          String dash, Boolean halo) {
        return new StyleRow("FACTORY", "WATER", null, lifecycle, null,
                color, 2.0, dash, halo, null, null, null);
    }

    @Test
    @DisplayName("padrão Ziip por estado: traço muda com o estado, não só a cor")
    void ziipDashByState() {
        assertThat(GisStyleRules.dashFor("IN_SERVICE")).isEmpty();
        assertThat(GisStyleRules.dashFor("PLANNED")).containsExactly(3.0, 2.0);
        assertThat(GisStyleRules.dashFor("UNDER_CONSTRUCTION"))
                .containsExactly(4.0, 2.0, 1.0, 2.0);
        assertThat(GisStyleRules.dashFor("ABANDONED")).containsExactly(2.0, 2.0);
        assertThat(GisStyleRules.widthFor("ABANDONED")).isEqualTo(1.0);
        assertThat(GisStyleRules.widthFor("IN_SERVICE")).isEqualTo(2.0);
    }

    @Test
    @DisplayName("sem nenhuma linha: padrão embutido usa o token do domínio e halo fora da meta")
    void embeddedDefault() {
        ResolvedStyle inService = GisStyleRules.resolve(List.of(), "WATER", null,
                "IN_SERVICE", "WITHIN_TARGET", "map-water");
        assertThat(inService.colorToken()).isEqualTo("map-water");
        assertThat(inService.lineDash()).isEmpty();
        assertThat(inService.halo()).isFalse();

        ResolvedStyle outOfTarget = GisStyleRules.resolve(List.of(), "WATER", null,
                "IN_SERVICE", "OUT_OF_TARGET", "map-water");
        assertThat(outOfTarget.halo()).isTrue();

        ResolvedStyle abandoned = GisStyleRules.resolve(List.of(), "SEWER", null,
                "ABANDONED", null, "map-sewer");
        assertThat(abandoned.colorToken()).isEqualTo("map-abandoned");
    }

    @Test
    @DisplayName("override do tenant por tipo vence a fábrica do domínio, campo a campo")
    void tenantTypeOverrideWinsPerField() {
        List<StyleRow> rows = List.of(
                factoryDomain("IN_SERVICE", "map-water", "[]", false),
                new StyleRow("TENANT", null, TYPE_VALVE, "IN_SERVICE", null,
                        "map-sewer", null, null, null, "valve-symbol", null, null));
        ResolvedStyle resolved = GisStyleRules.resolve(rows, "WATER", TYPE_VALVE,
                "IN_SERVICE", null, "map-water");
        // Cor e ícone do override; traço e halo herdados da fábrica.
        assertThat(resolved.colorToken()).isEqualTo("map-sewer");
        assertThat(resolved.iconKey()).isEqualTo("valve-symbol");
        assertThat(resolved.lineDash()).isEmpty();
        assertThat(resolved.halo()).isFalse();
    }

    @Test
    @DisplayName("tipo de fábrica vence genérica do tenant; domínio do tenant vence domínio de fábrica")
    void precedenceBands() {
        List<StyleRow> rows = List.of(
                new StyleRow("FACTORY", null, TYPE_VALVE, "PLANNED", null,
                        "map-water", null, null, null, null, null, null),
                new StyleRow("TENANT", null, null, "PLANNED", null,
                        "map-waste", null, null, null, null, null, null),
                factoryDomain("PLANNED", "map-water", "[3, 2]", false),
                new StyleRow("TENANT", "WATER", null, "PLANNED", null,
                        null, 5.0, null, null, null, null, null));
        ResolvedStyle byType = GisStyleRules.resolve(rows, "WATER", TYPE_VALVE,
                "PLANNED", null, "map-water");
        assertThat(byType.colorToken()).isEqualTo("map-water");
        ResolvedStyle byDomain = GisStyleRules.resolve(rows, "WATER", UUID.randomUUID(),
                "PLANNED", null, "map-water");
        assertThat(byDomain.lineWidth()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("linha de accuracy exata vence a linha sem accuracy da mesma faixa")
    void accuracyRowWins() {
        List<StyleRow> rows = List.of(
                factoryDomain("IN_SERVICE", "map-water", "[]", false),
                new StyleRow("FACTORY", "WATER", null, "IN_SERVICE", "OUT_OF_TARGET",
                        null, null, null, true, null, null, null));
        ResolvedStyle resolved = GisStyleRules.resolve(rows, "WATER", null,
                "IN_SERVICE", "OUT_OF_TARGET", "map-water");
        assertThat(resolved.halo()).isTrue();
        assertThat(resolved.colorToken()).isEqualTo("map-water");
    }

    @Test
    @DisplayName("override inválido: cor fora de token, estado desconhecido, símbolo fora da biblioteca")
    void overrideValidation() {
        Set<String> library = Set.of("valve-symbol", "hydrant-symbol");
        GisStyleRules.validateOverride("IN_SERVICE", null, "map-water",
                "valve-symbol", library);
        assertThatThrownBy(() -> GisStyleRules.validateOverride(
                "IN_SERVICE", null, "#00548F", null, library))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("map-*");
        assertThatThrownBy(() -> GisStyleRules.validateOverride(
                "VOANDO", null, "map-water", null, library))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GisStyleRules.validateOverride(
                "IN_SERVICE", null, "map-water", "svg-enviado-pelo-tenant", library))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("biblioteca");
    }
}
