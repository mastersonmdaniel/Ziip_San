package br.com.ziipsan.commercial.collection.surcharge;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;

/**
 * Série mensal de fatores de um índice de correção monetária (dado do tenant, tabela
 * {@code surcharge_indexes}). O fator de um mês é o valor acumulado do índice naquele mês; a
 * correção entre dois meses é a razão entre os fatores. Este módulo não traz série nenhuma.
 */
@FunctionalInterface
public interface IndexSeries {

    Optional<BigDecimal> factor(YearMonth month);

    static IndexSeries of(Map<YearMonth, BigDecimal> factors) {
        Map<YearMonth, BigDecimal> copy = Map.copyOf(factors);
        return month -> Optional.ofNullable(copy.get(month));
    }
}
