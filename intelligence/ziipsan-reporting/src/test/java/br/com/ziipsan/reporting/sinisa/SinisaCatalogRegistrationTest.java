package br.com.ziipsan.reporting.sinisa;

import br.com.ziipsan.core.parameters.ParameterVersionRule;
import br.com.ziipsan.reporting.indicator.ConfidenceGrade;
import br.com.ziipsan.reporting.indicator.InMemoryIndicatorStore;
import br.com.ziipsan.reporting.indicator.IndicatorAccess;
import br.com.ziipsan.reporting.indicator.IndicatorRegistryService;
import br.com.ziipsan.reporting.indicator.IndicatorTarget;
import br.com.ziipsan.reporting.indicator.IndicatorValidationException;
import br.com.ziipsan.reporting.indicator.Semaphore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Materializacao do catalogo SINISA no registro da P3.41 e aplicacao do
 * fechamento/semaforo do registro ao catalogo (aceite da ED-48).
 * Valores sao massa sintetica declarada; as views rpt_* sao o mapeamento
 * de fiacao informado pelo teste (a ED-48 nao presume view nenhuma).
 */
class SinisaCatalogRegistrationTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID RESPONSIBLE = UUID.randomUUID();
    private static final LocalDate Y2025_START = LocalDate.of(2025, 1, 1);
    private static final LocalDate Y2025_END = LocalDate.of(2025, 12, 31);
    private static final ParameterVersionRule.Validity VALIDITY_2025 =
            new ParameterVersionRule.Validity(Y2025_START, Y2025_END);

    private IndicatorRegistryService registry;
    private SinisaCatalogService catalog;

    private static Map<String, String> viewMappings() {
        return Map.of(
                "AGUA.VOLUME_PRODUZIDO", "rpt_sinisa_volume_produzido",
                "AGUA.VOLUME_FATURADO", "rpt_sinisa_volume_faturado",
                "AGUA.LIGACOES_ATIVAS", "rpt_sinisa_ligacoes_ativas",
                "FIN.RECEITA_OPERACIONAL_AGUA", "rpt_sinisa_receita_agua");
    }

    @BeforeEach
    void setUp() {
        registry = new IndicatorRegistryService(new InMemoryIndicatorStore(),
                Clock.fixed(Instant.parse("2026-02-10T12:00:00Z"), ZoneOffset.UTC));
        catalog = new SinisaCatalogService(registry);
    }

    private java.util.List<br.com.ziipsan.reporting.indicator.IndicatorDefinition> register() {
        return catalog.registerPreconfigured(IndicatorAccess.EDITOR, TENANT, viewMappings(),
                RESPONSIBLE, ConfidenceGrade.MEDIUM, VALIDITY_2025, ACTOR);
    }

    @Test
    void registersAllSixIndicatorsMarkedForTheSinisaProfile() {
        var defined = register();
        assertThat(defined).hasSize(6);
        assertThat(registry.listForProfile(TENANT, SinisaCatalogService.PROFILE_TAG)).hasSize(6);
    }

    @Test
    void unbilledWaterCaseFromV1Computes3000() {
        register();
        var result = registry.compute(TENANT, "SINISA.AGUA.AGUA_NAO_FATURADA",
                Y2025_START, Y2025_END, null,
                Map.of("volume_produzido", new BigDecimal("10000"),
                        "volume_faturado", new BigDecimal("7000")));
        assertThat(result.value()).isEqualByComparingTo("3000");
        // Sem meta declarada: NO_TARGET, nunca verde presumido (precedente P3.41).
        assertThat(result.semaphore()).isEqualTo(Semaphore.NO_TARGET);
        assertThat(result.frozen()).isFalse();
    }

    @Test
    void semaphoreComesFromTheRegistryTarget() {
        register();
        registry.setTarget(IndicatorAccess.EDITOR, TENANT,
                new IndicatorTarget("SINISA.AGUA.AGUA_NAO_FATURADA", Y2025_START, Y2025_END,
                        null, new BigDecimal("2500"), new BigDecimal("10"),
                        IndicatorTarget.Direction.LOWER_IS_BETTER), ACTOR);
        var result = registry.compute(TENANT, "SINISA.AGUA.AGUA_NAO_FATURADA",
                Y2025_START, Y2025_END, null,
                Map.of("volume_produzido", new BigDecimal("10000"),
                        "volume_faturado", new BigDecimal("7000")));
        // Meta 2500 (menor e melhor), tolerancia amarela 10% = 250 -> teto amarelo 2750; 3000 e RED.
        assertThat(result.semaphore()).isEqualTo(Semaphore.RED);
    }

    @Test
    void closingFreezesTheSinisaValueAgainstRecalculation() {
        register();
        var closing = registry.close(IndicatorAccess.EDITOR, TENANT,
                "SINISA.AGUA.AGUA_NAO_FATURADA", Y2025_START, Y2025_END, null,
                Map.of("volume_produzido", new BigDecimal("10000"),
                        "volume_faturado", new BigDecimal("7000")), ACTOR);
        assertThat(closing.value()).isEqualByComparingTo("3000");
        var recomputed = registry.compute(TENANT, "SINISA.AGUA.AGUA_NAO_FATURADA",
                Y2025_START, Y2025_END, null,
                Map.of("volume_produzido", new BigDecimal("20000"),
                        "volume_faturado", new BigDecimal("5000")));
        assertThat(recomputed.frozen()).isTrue();
        assertThat(recomputed.value()).isEqualByComparingTo("3000");
    }

    @Test
    void missingViewMappingIsRefusedBySinisaKeyAndRegistersNothing() {
        var incomplete = new java.util.HashMap<>(viewMappings());
        incomplete.remove("AGUA.LIGACOES_ATIVAS");
        assertThatThrownBy(() -> catalog.registerPreconfigured(IndicatorAccess.EDITOR, TENANT,
                incomplete, RESPONSIBLE, ConfidenceGrade.MEDIUM, VALIDITY_2025, ACTOR))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("SINISA_VARIABLE_VIEW_MAPPING_REQUIRED")
                .hasMessageContaining("AGUA.LIGACOES_ATIVAS");
        // Conversao precede qualquer define: nada foi registrado.
        assertThat(registry.listForProfile(TENANT, SinisaCatalogService.PROFILE_TAG)).isEmpty();
    }

    @Test
    void manualVariableWithoutResponsibleIsRefused() {
        assertThatThrownBy(() -> catalog.registerPreconfigured(IndicatorAccess.EDITOR, TENANT,
                viewMappings(), null, ConfidenceGrade.MEDIUM, VALIDITY_2025, ACTOR))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("SINISA_MANUAL_RESPONSIBLE_REQUIRED")
                .hasMessageContaining("BALANCO.ATIVO_TOTAL");
    }

    @Test
    void confidenceGradeIsNeverPresumed() {
        assertThatThrownBy(() -> catalog.registerPreconfigured(IndicatorAccess.EDITOR, TENANT,
                viewMappings(), RESPONSIBLE, null, VALIDITY_2025, ACTOR))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("SINISA_CONFIDENCE_GRADE_REQUIRED");
    }
}
