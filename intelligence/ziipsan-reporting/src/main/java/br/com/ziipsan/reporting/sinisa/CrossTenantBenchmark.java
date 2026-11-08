package br.com.ziipsan.reporting.sinisa;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agregado anonimo entre tenants para o benchmark (GES-29). Regras, todas
 * com teste:
 * <ul>
 *   <li>so entra no agregado o tenant com ADESAO registrada e vigente
 *       ({@link CrossTenantOptIn#optedIn()}); tenant sem adesao NAO entra
 *       — nem com valor extremo (teste nomeado do aceite). O parametro
 *       {@code bench.cross_tenant_opt_in} e conferido DENTRO do caminho
 *       {@link #aggregateMedianIfEnabled} (correcao D2 da revisao
 *       cruzada): parametro desligado = recusa, nenhum agregado;</li>
 *   <li>agregacao MINIMA: menos de {@value #MINIMUM_TENANTS} tenants
 *       contribuintes = recusa nomeada, sem agregado parcial (com poucos
 *       tenants a mediana denuncia o valor individual); quem chama pode
 *       exigir minimo MAIOR, nunca menor que o piso;</li>
 *   <li>o resultado carrega so a mediana e a CONTAGEM de contribuintes —
 *       nenhum tenant, nenhum valor individual.</li>
 * </ul>
 * A mediana de contagem par e a media dos dois valores centrais, com o
 * mesmo contexto de divisao do avaliador de formulas da P3.41
 * ({@code MathContext(20, HALF_UP)}).
 */
public final class CrossTenantBenchmark {

    /** Piso de anonimizacao adotado: agregado com menos de 3 tenants nao sai. */
    public static final int MINIMUM_TENANTS = 3;

    private static final MathContext DIVISION_CONTEXT = new MathContext(20, RoundingMode.HALF_UP);

    private CrossTenantBenchmark() {
    }

    /** Agregado publicado: mediana + quantos tenants contribuiram. Nada alem disso. */
    public record Aggregate(String indicatorCode, int contributingTenants, BigDecimal median) {
    }

    /**
     * Caminho composto da agregacao (D2): confere o parametro
     * {@code bench.cross_tenant_opt_in} do tenant chamador ANTES de
     * agregar — desligado (inclusive ausente, o padrao) recusa com
     * {@code BENCHMARK_CROSS_TENANT_DISABLED} e nenhum agregado e
     * produzido. E a porta que a fiacao deve chamar; o
     * {@link #aggregateMedian} puro continua existindo para quem ja
     * compôs a conferencia, e os dois caminhos sao testados.
     */
    public static Aggregate aggregateMedianIfEnabled(String parametersJson, String indicatorCode,
                                                     Map<UUID, BigDecimal> valuesByTenant,
                                                     Collection<CrossTenantOptIn> optIns,
                                                     int minimumTenants) {
        if (!BenchmarkParameters.crossTenantOptInEnabled(parametersJson)) {
            throw new IndicatorValidationException("BENCHMARK_CROSS_TENANT_DISABLED",
                    "parametro bench.cross_tenant_opt_in desligado: comparacao entre tenants "
                            + "nao produz agregado (GES-29, padrao desligado)");
        }
        return aggregateMedian(indicatorCode, valuesByTenant, optIns, minimumTenants);
    }

    public static Aggregate aggregateMedian(String indicatorCode,
                                            Map<UUID, BigDecimal> valuesByTenant,
                                            Collection<CrossTenantOptIn> optIns,
                                            int minimumTenants) {
        if (indicatorCode == null || indicatorCode.isBlank()) {
            throw new IndicatorValidationException("BENCHMARK_INDICATOR_REQUIRED",
                    "agregado entre tenants sem indicador");
        }
        if (minimumTenants < MINIMUM_TENANTS) {
            throw new IndicatorValidationException("AGGREGATION_MINIMUM_BELOW_FLOOR",
                    "minimo de agregacao " + minimumTenants + " abaixo do piso de "
                            + MINIMUM_TENANTS + " tenants (GES-29)");
        }
        java.util.Set<UUID> optedInTenants = new java.util.HashSet<>();
        if (optIns != null) {
            for (CrossTenantOptIn optIn : optIns) {
                if (optIn.optedIn()) {
                    optedInTenants.add(optIn.tenantId());
                }
            }
        }
        List<BigDecimal> contributing = new ArrayList<>();
        if (valuesByTenant != null) {
            for (Map.Entry<UUID, BigDecimal> entry : valuesByTenant.entrySet()) {
                // Tenant sem adesao registrada fica FORA — inclusive do denominador.
                if (!optedInTenants.contains(entry.getKey())) {
                    continue;
                }
                if (entry.getValue() == null) {
                    throw new IndicatorValidationException("BENCHMARK_TENANT_VALUE_REQUIRED",
                            "valor nulo de tenant aderente no agregado de " + indicatorCode);
                }
                contributing.add(entry.getValue());
            }
        }
        if (contributing.size() < minimumTenants) {
            throw new IndicatorValidationException("INSUFFICIENT_TENANTS_FOR_AGGREGATION",
                    "agregado de " + indicatorCode + " com apenas " + contributing.size()
                            + " tenants aderentes (minimo " + minimumTenants
                            + "): agregado recusado, nenhum valor parcial e publicado");
        }
        contributing.sort(BigDecimal::compareTo);
        int middle = contributing.size() / 2;
        BigDecimal median = contributing.size() % 2 == 1
                ? contributing.get(middle)
                : contributing.get(middle - 1).add(contributing.get(middle))
                        .divide(new BigDecimal("2"), DIVISION_CONTEXT);
        return new Aggregate(indicatorCode, contributing.size(), median);
    }
}
