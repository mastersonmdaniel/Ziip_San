package br.com.ziipsan.reporting.panels;

import br.com.ziipsan.core.parameters.ParameterVersionRule;
import br.com.ziipsan.reporting.indicator.ConfidenceGrade;
import br.com.ziipsan.reporting.indicator.InMemoryIndicatorStore;
import br.com.ziipsan.reporting.indicator.IndicatorAccess;
import br.com.ziipsan.reporting.indicator.IndicatorDefinition;
import br.com.ziipsan.reporting.indicator.IndicatorPeriodicity;
import br.com.ziipsan.reporting.indicator.IndicatorRegistryService;
import br.com.ziipsan.reporting.indicator.IndicatorVariable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Massa SINTETICA declarada da ED-49: indicadores de teste definidos no
 * registro real da P3.41, um por painel, com formulas simples sobre
 * variaveis declaradas. Nenhum codigo, nome ou valor aqui e dado real
 * de indicador — sao os veiculos do teste que compara o exibido com o
 * apurado no registro.
 */
final class PanelFixtures {

    static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000049");
    static final UUID OTHER_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000050");
    static final UUID EDITOR = UUID.fromString("00000000-0000-0000-0000-000000000051");
    static final UUID RESPONSIBLE = UUID.fromString("00000000-0000-0000-0000-000000000052");
    static final LocalDate JAN_START = LocalDate.of(2026, 1, 1);
    static final LocalDate JAN_END = LocalDate.of(2026, 1, 31);
    static final ParameterVersionRule.Validity VALIDITY_2026 =
            new ParameterVersionRule.Validity(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

    /** Codigo sintetico do indicador de cada painel. */
    static String codeFor(PanelKind kind) {
        return "ED49-TESTE-" + kind.name();
    }

    static IndicatorRegistryService registryWithOneIndicatorPerPanel() {
        IndicatorRegistryService registry = newRegistry();
        for (PanelKind kind : PanelKind.values()) {
            defineSimple(registry, TENANT, codeFor(kind), "Indicador sintetico de " + kind.title());
        }
        return registry;
    }

    static IndicatorRegistryService newRegistry() {
        return new IndicatorRegistryService(new InMemoryIndicatorStore(),
                Clock.fixed(Instant.parse("2026-02-10T12:00:00Z"), ZoneOffset.UTC));
    }

    /** Indicador de uma variavel manual "valor" com formula identidade "valor". */
    static void defineSimple(IndicatorRegistryService registry, UUID tenant, String code, String name) {
        registry.define(IndicatorAccess.EDITOR,
                new IndicatorDefinition(tenant, code, name, "definicao sintetica de teste", "un",
                        IndicatorPeriodicity.MONTHLY, ConfidenceGrade.HIGH, "contexto sintetico",
                        null, Set.of("PAINEIS"),
                        List.of(IndicatorVariable.manual("valor", RESPONSIBLE))),
                "valor", VALIDITY_2026, EDITOR);
    }

    static PanelCellRequest request(PanelKind kind, String value) {
        return new PanelCellRequest(kind, codeFor(kind), JAN_START, JAN_END, null,
                Map.of("valor", new BigDecimal(value)));
    }

    static List<PanelCellRequest> oneRequestPerPanel(String value) {
        return java.util.Arrays.stream(PanelKind.values()).map(k -> request(k, value)).toList();
    }

    private PanelFixtures() {
    }
}
