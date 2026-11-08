package br.com.ziipsan.reporting.situation;

import br.com.ziipsan.reporting.indicator.IndicatorRegistryService;
import br.com.ziipsan.reporting.indicator.Semaphore;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * ED-93 (JOR-19): os indicadores do console sao LIDOS do registro
 * unico da P3.41 — o mesmo desenho da ED-49 para os paineis. Esta
 * classe nao conhece formula, nao recalcula semaforo e nao guarda
 * meta: transporta a apuracao do registro verbatim (valor, semaforo,
 * versao da formula e o congelado de periodo fechado).
 *
 * <p>Quais indicadores aparecem no console NAO consta de nenhuma
 * fonte: a ligacao e DECLARADA por tenant (tabela
 * {@code ops_console_indicator_bindings} do SQL pendente, espelho da
 * {@code panel_indicator_bindings} da ED-49). Sem ligacao declarada,
 * o console nasce sem indicadores e a pendencia e nomeada
 * ({@link #UNBOUND_PENDING}) — nenhum indicador e presumido.
 */
public final class SituationIndicators {

    /** Pendencia nomeada quando o tenant ainda nao declarou ligacoes. */
    public static final String UNBOUND_PENDING = "console_indicator_bindings";

    private final IndicatorRegistryService registry;

    public SituationIndicators(IndicatorRegistryService registry) {
        this.registry = Objects.requireNonNull(registry, "registro de indicadores da P3.41 obrigatorio");
    }

    /** Ligacao declarada console -> indicador do registro (dado do tenant). */
    public record Binding(String indicatorCode) {
        public Binding {
            if (indicatorCode == null || indicatorCode.isBlank()) {
                throw new IllegalArgumentException("codigo do indicador obrigatorio na ligacao");
            }
        }
    }

    /** Linha de indicador do console: transporte verbatim da apuracao do registro. */
    public record ConsoleIndicatorView(String indicatorCode, BigDecimal value, Semaphore semaphore,
                                       int formulaVersion, boolean frozen) {
    }

    /**
     * Le os indicadores ligados, um a um, da apuracao do registro.
     * Codigo ligado inexistente no registro propaga o
     * {@code INDICATOR_NOT_FOUND} do registro — ligacao pendurada
     * falha nomeada, nunca vira zero (precedente da ED-49).
     */
    public List<ConsoleIndicatorView> read(UUID tenantId, List<Binding> bindings,
                                           LocalDate periodStart, LocalDate periodEnd, String areaCode,
                                           Map<String, Map<String, BigDecimal>> variableValuesByCode) {
        Objects.requireNonNull(bindings, "ligacoes declaradas obrigatorias (vazia = sem indicadores)");
        return bindings.stream().map(binding -> {
            Map<String, BigDecimal> values = variableValuesByCode == null
                    ? Map.of() : variableValuesByCode.getOrDefault(binding.indicatorCode(), Map.of());
            IndicatorRegistryService.Computation computation = registry.compute(
                    tenantId, binding.indicatorCode(), periodStart, periodEnd, areaCode, values);
            return new ConsoleIndicatorView(binding.indicatorCode(), computation.value(),
                    computation.semaphore(), computation.formulaVersion(), computation.frozen());
        }).toList();
    }
}
