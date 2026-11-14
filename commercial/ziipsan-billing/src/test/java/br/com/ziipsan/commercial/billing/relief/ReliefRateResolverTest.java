package br.com.ziipsan.commercial.billing.relief;

import br.com.ziipsan.commercial.billing.tariff.TariffConfigurationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Grupo 2 — Resolucao de aliquota por precedencia (secao 3.3). */
class ReliefRateResolverTest {

    private static ReliefRate rate(ReliefRate.ScopeKind kind, String ref, String pct) {
        return new ReliefRate(UUID.randomUUID(), ReliefFixtures.PROGRAM_ID, kind, ref,
                new BigDecimal(pct), "fonte ficticia declarada", LocalDate.of(2026, 1, 1), null);
    }

    @Test
    @DisplayName("precedencia SERVICE > CATEGORY > TARIFF > TENANT")
    void mostSpecificScopeWins() {
        var rates = List.of(
                rate(ReliefRate.ScopeKind.TENANT, null, "10"),
                rate(ReliefRate.ScopeKind.TARIFF, "T1", "11"),
                rate(ReliefRate.ScopeKind.CATEGORY, "RES", "12"),
                rate(ReliefRate.ScopeKind.SERVICE, "LIGACAO", "13"));
        var ctx = new ReliefRateResolver.ResolutionContext("T1", "RES", "LIGACAO");
        assertThat(ReliefRateResolver.resolve(rates, ctx, ReliefFixtures.CUT)
                .effectiveRatePct()).isEqualByComparingTo("13");
        var ctxNoService = new ReliefRateResolver.ResolutionContext("T1", "RES", "OUTRO");
        assertThat(ReliefRateResolver.resolve(rates, ctxNoService, ReliefFixtures.CUT)
                .effectiveRatePct()).isEqualByComparingTo("12");
        var ctxTenant = new ReliefRateResolver.ResolutionContext("TX", "CX", "SX");
        assertThat(ReliefRateResolver.resolve(rates, ctxTenant, ReliefFixtures.CUT)
                .effectiveRatePct()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("sem linha aplicavel: erro fechado, nunca zero silencioso")
    void absenceIsClosedError() {
        assertThatThrownBy(() -> ReliefRateResolver.resolve(List.of(),
                new ReliefRateResolver.ResolutionContext("T1", "RES", null), ReliefFixtures.CUT))
                .isInstanceOf(TariffConfigurationException.class);
    }

    @Test
    @DisplayName("ambiguidade no mesmo nivel: erro fechado")
    void ambiguityIsClosedError() {
        var rates = List.of(rate(ReliefRate.ScopeKind.TENANT, null, "10"),
                rate(ReliefRate.ScopeKind.TENANT, null, "11"));
        assertThatThrownBy(() -> ReliefRateResolver.resolve(rates,
                new ReliefRateResolver.ResolutionContext(null, null, null), ReliefFixtures.CUT))
                .isInstanceOf(TariffConfigurationException.class);
    }

    @Test
    @DisplayName("aliquota sem proveniencia ou fora de [0,100) nao se aceita")
    void rateValidation() {
        assertThatThrownBy(() -> new ReliefRate(UUID.randomUUID(), ReliefFixtures.PROGRAM_ID,
                ReliefRate.ScopeKind.TENANT, null, new BigDecimal("10"), " ",
                LocalDate.of(2026, 1, 1), null))
                .isInstanceOf(TariffConfigurationException.class);
        assertThatThrownBy(() -> rate(ReliefRate.ScopeKind.TENANT, null, "100"))
                .isInstanceOf(TariffConfigurationException.class);
    }
}
