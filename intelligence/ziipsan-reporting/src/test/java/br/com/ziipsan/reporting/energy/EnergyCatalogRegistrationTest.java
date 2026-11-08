package br.com.ziipsan.reporting.energy;

import br.com.ziipsan.core.parameters.ParameterVersionRule;
import br.com.ziipsan.reporting.indicator.ConfidenceGrade;
import br.com.ziipsan.reporting.indicator.InMemoryIndicatorStore;
import br.com.ziipsan.reporting.indicator.IndicatorAccess;
import br.com.ziipsan.reporting.indicator.IndicatorDefinition;
import br.com.ziipsan.reporting.indicator.IndicatorRegistryService;
import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Indicadores de energia/ESG registrados no registro da P3.41 e
 * marcados para o catalogo da ED-48 (aceite). As views rpt_* sao o
 * mapeamento de fiacao declarado pelo teste — a ED-90 nao presume
 * view nenhuma.
 */
class EnergyCatalogRegistrationTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final ParameterVersionRule.Validity VALIDITY =
            new ParameterVersionRule.Validity(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

    private IndicatorRegistryService registry;
    private EnergyCatalogService catalog;

    private static Map<String, String> viewMappings() {
        return Map.of(
                "kwh_total", "rpt_energy_kwh_total",
                "volume_m3", "rpt_energy_volume_m3",
                "consumo_ponta_kwh", "rpt_energy_peak_kwh",
                "demanda_medida_kw", "rpt_energy_demand_measured",
                "demanda_contratada_kw", "rpt_energy_demand_contracted",
                "fator_potencia", "rpt_energy_power_factor",
                "emissoes_escopo1", "rpt_esg_scope1",
                "emissoes_escopo2", "rpt_esg_scope2");
    }

    @BeforeEach
    void setUp() {
        registry = new IndicatorRegistryService(new InMemoryIndicatorStore(),
                Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC));
        catalog = new EnergyCatalogService(registry);
    }

    @Test
    void catalogoMaterializaSeteIndicadoresNoRegistro() {
        List<IndicatorDefinition> defined = catalog.registerCatalog(IndicatorAccess.EDITOR,
                TENANT, viewMappings(), null, ConfidenceGrade.MEDIUM, VALIDITY, ACTOR);
        assertThat(defined).hasSize(7);
        assertThat(defined).extracting(IndicatorDefinition::code)
                .containsExactly("ENERGY.KWH_PER_M3", "ENERGY.PEAK_SHARE", "ENERGY.DEMAND_RATIO",
                        "ENERGY.POWER_FACTOR", "ESG.SCOPE1_EMISSIONS", "ESG.SCOPE2_EMISSIONS",
                        "ESG.TOTAL_EMISSIONS");
        assertThat(defined).allSatisfy(d -> {
            assertThat(d.profileTags()).contains(EnergyCatalogService.PROFILE_TAG);
            assertThat(d.variables()).allSatisfy(v ->
                    assertThat(v.reportView()).startsWith("rpt_"));
        });
    }

    @Test
    void semMapeamentoDeViewRecusaAntesDeGravar() {
        assertThatThrownBy(() -> catalog.registerCatalog(IndicatorAccess.EDITOR,
                TENANT, Map.of(), null, ConfidenceGrade.MEDIUM, VALIDITY, ACTOR))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("ENERGY_VARIABLE_VIEW_MAPPING_REQUIRED");
        // Conversao de todas as fontes antes de qualquer define: nada gravado.
        assertThatThrownBy(() -> registry.definition(TENANT, "ENERGY.KWH_PER_M3"))
                .isInstanceOf(IndicatorValidationException.class);
    }

    @Test
    void calculoDoIndicadorRegistradoUsaFormulaDaP341() {
        catalog.registerCatalog(IndicatorAccess.EDITOR, TENANT, viewMappings(), null,
                ConfidenceGrade.MEDIUM, VALIDITY, ACTOR);
        var computation = registry.compute(TENANT, "ENERGY.KWH_PER_M3",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null,
                Map.of("kwh_total", new BigDecimal("12000"), "volume_m3", new BigDecimal("3000")));
        assertThat(computation.value()).isEqualByComparingTo("4");
    }

    @Test
    void semGrauDeConfiancaRecusa() {
        assertThatThrownBy(() -> catalog.registerCatalog(IndicatorAccess.EDITOR,
                TENANT, viewMappings(), null, null, VALIDITY, ACTOR))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("ENERGY_CONFIDENCE_GRADE_REQUIRED");
    }
}
