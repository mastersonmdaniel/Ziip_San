package br.com.ziipsan.reporting.sinisa;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comparacao entre tenants (GES-29): padrao desligado, adesao com clausula,
 * agregacao minima e o teste nomeado do aceite — tenant sem adesao NAO
 * entra no agregado.
 */
class CrossTenantBenchmarkTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();
    private static final UUID D = UUID.randomUUID();
    private static final Instant WHEN = Instant.parse("2026-01-15T12:00:00Z");

    private static CrossTenantOptIn optedIn(UUID tenant) {
        return new CrossTenantOptIn(tenant, true, "contrato, clausula de benchmark (teste)", WHEN);
    }

    @Test
    void parameterIsOffByDefault() {
        assertThat(BenchmarkParameters.crossTenantOptInEnabled(null)).isFalse();
        assertThat(BenchmarkParameters.crossTenantOptInEnabled("{}")).isFalse();
        assertThat(BenchmarkParameters.crossTenantOptInEnabled(
                "{\"bench.cross_tenant_opt_in\": false}")).isFalse();
        assertThat(BenchmarkParameters.crossTenantOptInEnabled(
                "{\"bench.cross_tenant_opt_in\": true}")).isTrue();
    }

    @Test
    void optInWithoutContractClauseIsRefused() {
        assertThatThrownBy(() -> new CrossTenantOptIn(A, true, null, WHEN))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("OPT_IN_WITHOUT_CONTRACT_CLAUSE");
        assertThatThrownBy(() -> new CrossTenantOptIn(A, true, "clausula", null))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("OPT_IN_WITHOUT_REGISTRATION");
    }

    @Test
    void tenantWithoutOptInDoesNotEnterTheAggregate() {
        // D tem valor extremo (1000): se entrasse, a mediana de {10,20,30,1000}
        // seria 25; fora do agregado, a mediana de {10,20,30} e 20.
        var values = Map.of(A, new BigDecimal("10"), B, new BigDecimal("20"),
                C, new BigDecimal("30"), D, new BigDecimal("1000"));
        var optIns = List.of(optedIn(A), optedIn(B), optedIn(C), CrossTenantOptIn.notOptedIn(D));
        var aggregate = CrossTenantBenchmark.aggregateMedian("IND-TESTE", values, optIns, 3);
        assertThat(aggregate.contributingTenants()).isEqualTo(3);
        assertThat(aggregate.median()).isEqualByComparingTo("20");
    }

    @Test
    void evenCountMedianIsTheMeanOfTheTwoCentralValues() {
        var e = UUID.randomUUID();
        var values = Map.of(A, new BigDecimal("10"), B, new BigDecimal("20"),
                C, new BigDecimal("30"), e, new BigDecimal("41"));
        var optIns = List.of(optedIn(A), optedIn(B), optedIn(C), optedIn(e));
        var aggregate = CrossTenantBenchmark.aggregateMedian("IND-TESTE", values, optIns, 3);
        assertThat(aggregate.median()).isEqualByComparingTo("25");
    }

    @Test
    void belowTheMinimumTheAggregateIsRefused() {
        var values = Map.of(A, new BigDecimal("10"), B, new BigDecimal("20"),
                C, new BigDecimal("30"));
        var optIns = List.of(optedIn(A), optedIn(B), CrossTenantOptIn.notOptedIn(C));
        assertThatThrownBy(() -> CrossTenantBenchmark.aggregateMedian("IND-TESTE", values, optIns, 3))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("INSUFFICIENT_TENANTS_FOR_AGGREGATION");
    }

    @Test
    void disabledParameterProducesNoAggregateEndToEnd() {
        // Ponta a ponta (D2): registros de adesao validos e valores
        // suficientes, mas o parametro desligado (ausente = padrao, ou
        // false explicito) => recusa e NENHUM agregado.
        var values = Map.of(A, new BigDecimal("10"), B, new BigDecimal("20"),
                C, new BigDecimal("30"));
        var optIns = List.of(optedIn(A), optedIn(B), optedIn(C));
        assertThatThrownBy(() -> CrossTenantBenchmark.aggregateMedianIfEnabled(
                "{}", "IND-TESTE", values, optIns, 3))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("BENCHMARK_CROSS_TENANT_DISABLED");
        assertThatThrownBy(() -> CrossTenantBenchmark.aggregateMedianIfEnabled(
                "{\"bench.cross_tenant_opt_in\": false}", "IND-TESTE", values, optIns, 3))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("BENCHMARK_CROSS_TENANT_DISABLED");
    }

    @Test
    void enabledParameterProducesTheAggregateEndToEnd() {
        var values = Map.of(A, new BigDecimal("10"), B, new BigDecimal("20"),
                C, new BigDecimal("30"), D, new BigDecimal("1000"));
        var optIns = List.of(optedIn(A), optedIn(B), optedIn(C), CrossTenantOptIn.notOptedIn(D));
        var aggregate = CrossTenantBenchmark.aggregateMedianIfEnabled(
                "{\"bench.cross_tenant_opt_in\": true}", "IND-TESTE", values, optIns, 3);
        assertThat(aggregate.contributingTenants()).isEqualTo(3);
        assertThat(aggregate.median()).isEqualByComparingTo("20");
    }

    @Test
    void minimumBelowTheFloorIsRefused() {
        assertThatThrownBy(() -> CrossTenantBenchmark.aggregateMedian("IND-TESTE",
                Map.of(A, new BigDecimal("1")), List.of(optedIn(A)), 2))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("AGGREGATION_MINIMUM_BELOW_FLOOR");
    }
}
