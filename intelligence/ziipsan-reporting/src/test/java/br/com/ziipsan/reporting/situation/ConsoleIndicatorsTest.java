package br.com.ziipsan.reporting.situation;

import br.com.ziipsan.core.parameters.ParameterVersionRule;
import br.com.ziipsan.reporting.indicator.ConfidenceGrade;
import br.com.ziipsan.reporting.indicator.InMemoryIndicatorStore;
import br.com.ziipsan.reporting.indicator.IndicatorAccess;
import br.com.ziipsan.reporting.indicator.IndicatorDefinition;
import br.com.ziipsan.reporting.indicator.IndicatorPeriodicity;
import br.com.ziipsan.reporting.indicator.IndicatorRegistryService;
import br.com.ziipsan.reporting.indicator.IndicatorTarget;
import br.com.ziipsan.reporting.indicator.IndicatorVariable;
import br.com.ziipsan.reporting.indicator.Semaphore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ED-93 (JOR-19): indicador do console = apuracao do registro REAL
 * da P3.41, verbatim — mesmo teste central da ED-49, no console.
 * Massa sintetica declarada (indicador ED93-TESTE-OPS, formula
 * identidade sobre variavel manual): nenhum codigo/valor e real.
 */
class ConsoleIndicatorsTest {

    private static final UUID EDITOR = UUID.fromString("00000000-0000-0000-0000-000000000051");
    private static final UUID RESPONSIBLE = UUID.fromString("00000000-0000-0000-0000-000000000052");
    private static final String CODE = "ED93-TESTE-OPS";
    private static final LocalDate JAN_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate JAN_END = LocalDate.of(2026, 1, 31);
    private static final ParameterVersionRule.Validity VALIDITY_2026 =
            new ParameterVersionRule.Validity(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

    private IndicatorRegistryService registry() {
        IndicatorRegistryService registry = new IndicatorRegistryService(new InMemoryIndicatorStore(),
                Clock.fixed(Instant.parse("2026-02-10T12:00:00Z"), ZoneOffset.UTC));
        registry.define(IndicatorAccess.EDITOR,
                new IndicatorDefinition(SituationFixtures.TENANT, CODE, "Indicador sintetico do console",
                        "definicao sintetica de teste", "un", IndicatorPeriodicity.MONTHLY,
                        ConfidenceGrade.HIGH, "contexto sintetico", null, Set.of("SALA_SITUACAO"),
                        List.of(IndicatorVariable.manual("valor", RESPONSIBLE))),
                "valor", VALIDITY_2026, EDITOR);
        return registry;
    }

    @Test
    void displayedValueMatchesRegistryComputationExactly() {
        IndicatorRegistryService registry = registry();
        registry.setTarget(IndicatorAccess.EDITOR, SituationFixtures.TENANT,
                new IndicatorTarget(CODE, JAN_START, JAN_END, null, new BigDecimal("50"),
                        new BigDecimal("10"), IndicatorTarget.Direction.HIGHER_IS_BETTER), EDITOR);
        SituationIndicators indicators = new SituationIndicators(registry);
        Map<String, Map<String, BigDecimal>> values = Map.of(CODE, Map.of("valor", new BigDecimal("47")));

        List<SituationIndicators.ConsoleIndicatorView> views = indicators.read(SituationFixtures.TENANT,
                List.of(new SituationIndicators.Binding(CODE)), JAN_START, JAN_END, null, values);
        IndicatorRegistryService.Computation direct = registry.compute(SituationFixtures.TENANT, CODE,
                JAN_START, JAN_END, null, Map.of("valor", new BigDecimal("47")));

        assertEquals(1, views.size());
        assertEquals(0, direct.value().compareTo(views.get(0).value()));
        assertEquals(direct.semaphore(), views.get(0).semaphore());
        assertEquals(Semaphore.YELLOW, views.get(0).semaphore());
        assertEquals(direct.formulaVersion(), views.get(0).formulaVersion());
        assertEquals(direct.frozen(), views.get(0).frozen());
    }

    @Test
    void closedPeriodShowsTheFrozenRegistryValue() {
        IndicatorRegistryService registry = registry();
        registry.close(IndicatorAccess.EDITOR, SituationFixtures.TENANT, CODE, JAN_START, JAN_END, null,
                Map.of("valor", new BigDecimal("100")), EDITOR);
        SituationIndicators indicators = new SituationIndicators(registry);

        List<SituationIndicators.ConsoleIndicatorView> views = indicators.read(SituationFixtures.TENANT,
                List.of(new SituationIndicators.Binding(CODE)), JAN_START, JAN_END, null,
                Map.of(CODE, Map.of("valor", new BigDecimal("999"))));

        assertEquals(0, new BigDecimal("100").compareTo(views.get(0).value()));
        assertTrue(views.get(0).frozen());
    }

    @Test
    void unboundConsoleHasNoIndicatorsAndUnknownCodeFailsByName() {
        SituationIndicators indicators = new SituationIndicators(registry());
        assertTrue(indicators.read(SituationFixtures.TENANT, List.of(), JAN_START, JAN_END, null, Map.of()).isEmpty());
        assertThrows(RuntimeException.class, () -> indicators.read(SituationFixtures.TENANT,
                List.of(new SituationIndicators.Binding("NAO-EXISTE")), JAN_START, JAN_END, null, Map.of()));
    }
}
