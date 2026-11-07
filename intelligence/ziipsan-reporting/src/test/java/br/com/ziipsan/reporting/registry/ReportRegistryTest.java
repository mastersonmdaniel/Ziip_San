package br.com.ziipsan.reporting.registry;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Catalogo da P3.22: os relatorios do acceptance, um a um. */
class ReportRegistryTest {

    private final ReportRegistry registry = new ReportRegistry();

    @Test
    void catalogoTemOs27RelatoriosDoAcceptanceSemDuplicar() {
        assertThat(registry.all()).hasSize(27);
        Set<String> codes = new HashSet<>();
        Set<String> views = new HashSet<>();
        for (ReportDefinition definition : registry.all()) {
            assertThat(codes.add(definition.code())).as("codigo duplicado %s", definition.code()).isTrue();
            assertThat(views.add(definition.viewName())).as("view duplicada %s", definition.viewName()).isTrue();
            assertThat(definition.origin()).isEqualTo(ReportOrigin.BUILTIN);
            assertThat(definition.requiredPermission())
                    .isEqualTo("reporting." + definition.code().toLowerCase() + ":export");
            assertThat(definition.columns()).isNotEmpty();
            assertThat(definition.maxRows()).isPositive();
        }
        assertThat(codes).contains("RPT-001", "RPT-002", "RPT-003", "RPT-004", "RPT-005",
                "RPT-006", "RPT-007", "RPT-008", "RPT-009", "RPT-010", "RPT-011", "RPT-012",
                "RPT-013", "RPT-014", "RPT-015", "RPT-016", "RPT-017", "RPT-018", "RPT-019",
                "RPT-020", "RPT-021", "RPT-022", "RPT-023", "RPT-024", "RPT-025", "RPT-026", "RPT-027");
    }

    @Test
    void dadoPessoalSoNasColunasDeclaradas() {
        // documento do pagador so aparece (mascarado) em nao identificados e duplicidade
        for (ReportDefinition definition : registry.all()) {
            boolean hasPersonal = definition.columns().stream().anyMatch(ReportColumn::personal);
            if (definition.code().equals("RPT-002") || definition.code().equals("RPT-003")) {
                assertThat(hasPersonal).as(definition.code()).isTrue();
            } else {
                assertThat(hasPersonal).as(definition.code()).isFalse();
            }
        }
    }

    @Test
    void reclamacoesArt54TemPercentualDeclarado() {
        ReportDefinition art54 = registry.find("RPT-027").orElseThrow();
        assertThat(art54.hasColumn("percentual_nao_atendido")).isTrue();
        assertThat(art54.hasColumn("motivo")).isTrue();
        assertThat(art54.hasColumn("sistema")).isTrue();
        assertThat(art54.hasColumn("mes")).isTrue();
    }
}
