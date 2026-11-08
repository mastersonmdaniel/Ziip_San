package br.com.ziipsan.commercial.billing.crisis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContingencyTariffTest {

    private ContingencyTariff comp(ContingencyTariff.Kind kind, String pct) {
        return new ContingencyTariff(kind, new BigDecimal(pct), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                "Ato da agencia 45/2025", Set.of("ATE_10", "ATE_20"), true, "Tarifa de contingencia vigente (ato 45/2025)");
    }

    @Test @DisplayName("adicional de 10%: 100,00 -> 110,00 na faixa afetada e na vigencia")
    void surcharge() {
        var c = comp(ContingencyTariff.Kind.CONTINGENCY, "10");
        assertThat(c.apply(new BigDecimal("100.00"), "ATE_10", false, LocalDate.of(2026, 2, 1), true))
                .isEqualByComparingTo(new BigDecimal("110.00"));
    }

    @Test @DisplayName("desconto sazonal de 5%: 200,00 -> 190,00 sem exigir bandeira de contingencia")
    void seasonalDiscount() {
        var c = comp(ContingencyTariff.Kind.SEASONAL, "-5");
        assertThat(c.apply(new BigDecimal("200.00"), "ATE_20", false, LocalDate.of(2026, 2, 1), false))
                .isEqualByComparingTo(new BigDecimal("190.00"));
    }

    @Test @DisplayName("unidade essencial isenta (travado); faixa fora e fora da vigencia intactas")
    void exemptions() {
        var c = comp(ContingencyTariff.Kind.CONTINGENCY, "10");
        assertThat(c.apply(new BigDecimal("100.00"), "ATE_10", true, LocalDate.of(2026, 2, 1), true))
                .isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(c.apply(new BigDecimal("100.00"), "ACIMA_50", false, LocalDate.of(2026, 2, 1), true))
                .isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(c.apply(new BigDecimal("100.00"), "ATE_10", false, LocalDate.of(2026, 5, 1), true))
                .isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test @DisplayName("contingencia com bandeira desligada e recusada; isencao desligavel e recusada")
    void locks() {
        var c = comp(ContingencyTariff.Kind.CONTINGENCY, "10");
        assertThatThrownBy(() -> c.apply(new BigDecimal("100.00"), "ATE_10", false, LocalDate.of(2026, 2, 1), false))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ContingencyTariff(ContingencyTariff.Kind.CONTINGENCY, new BigDecimal("10"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), "Ato 1", Set.of("ATE_10"), false, "nota"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("TRAVADA");
    }

    @Test @DisplayName("sem ato, sem faixas ou sem comunicacao na fatura: recusado")
    void requiredFacts() {
        assertThatThrownBy(() -> new ContingencyTariff(ContingencyTariff.Kind.SEASONAL, new BigDecimal("5"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), " ", Set.of("ATE_10"), true, "nota"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ContingencyTariff(ContingencyTariff.Kind.SEASONAL, new BigDecimal("5"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), "Ato 1", Set.of(), true, "nota"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ContingencyTariff(ContingencyTariff.Kind.SEASONAL, new BigDecimal("5"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), "Ato 1", Set.of("ATE_10"), true, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
