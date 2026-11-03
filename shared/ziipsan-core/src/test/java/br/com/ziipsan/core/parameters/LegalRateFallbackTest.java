package br.com.ziipsan.core.parameters;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P0.20, fee.legal_rate_fallback (CC art. 406; metodologia Res. CMN 5.171/2024).
 * As taxas do mapa são sintéticas, só para provar a regra: o produto não traz taxa
 * nenhuma embutida; a carga mensal do BCB chega fora do P0.20.
 */
class LegalRateFallbackTest {

    @Test
    @DisplayName("mês com taxa divulgada devolve a taxa daquele mês, sem herdar outro mês")
    void disclosedMonthReturnsItsOwnRate() {
        var rates = Map.of(YearMonth.of(2026, 10), new BigDecimal("1.00"),
                YearMonth.of(2026, 11), new BigDecimal("1.10"));
        assertThat(LegalRateFallback.rateFor(YearMonth.of(2026, 11), rates))
                .isEqualByComparingTo("1.10");
    }

    @Test
    @DisplayName("mês sem taxa divulgada falha com erro explícito (nunca taxa inventada)")
    void missingMonthFailsExplicitly() {
        assertThatThrownBy(() -> LegalRateFallback.rateFor(
                YearMonth.of(2026, 12), Map.of(YearMonth.of(2026, 10), new BigDecimal("1.00"))))
                .isInstanceOf(MissingParameterException.class)
                .hasMessageContaining("fee.legal_rate_fallback")
                .hasMessageContaining("2026-12");
    }
}
