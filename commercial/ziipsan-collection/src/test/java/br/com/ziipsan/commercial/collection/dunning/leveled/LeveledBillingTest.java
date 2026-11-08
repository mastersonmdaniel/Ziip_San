package br.com.ziipsan.commercial.collection.dunning.leveled;

import br.com.ziipsan.commercial.collection.dunning.DunningParameters;
import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fatura nivelada e acordo por capacidade (CAD-104). */
class LeveledBillingTest {

    @Test
    @DisplayName("nivelada: media de 100, 120 e 110 = 110,00")
    void leveledAverage() {
        assertThat(LeveledBilling.leveledAmount(List.of(
                new BigDecimal("100"), new BigDecimal("120"), new BigDecimal("110"))))
                .isEqualByComparingTo("110.00");
    }

    @Test
    @DisplayName("acerto periodico: real 250 menos nivelado 220 = deve 30; real menor vira credito")
    void periodicSettlement() {
        assertThat(LeveledBilling.periodicSettlement(
                List.of(new BigDecimal("130"), new BigDecimal("120")),
                List.of(new BigDecimal("110"), new BigDecimal("110"))))
                .isEqualByComparingTo("30");
        assertThat(LeveledBilling.periodicSettlement(
                List.of(new BigDecimal("100")), List.of(new BigDecimal("110"))))
                .isEqualByComparingTo("-10");
    }

    @Test
    @DisplayName("capacidade = renda menos o MINIMO DO PARAMETRO (600 -> 400; parametro 700 -> 300: prova de que nao e constante)")
    void capacityUsesTheParameter() {
        var decree600 = DunningParameters.fromValues(Map.of("dun.existential_minimum", "600.00"));
        assertThat(LeveledBilling.paymentCapacity(new BigDecimal("1000"), decree600))
                .isEqualByComparingTo("400.00");
        assertThat(LeveledBilling.paymentCapacity(new BigDecimal("500"), decree600))
                .isEqualByComparingTo("0");
        var act700 = DunningParameters.fromValues(Map.of("dun.existential_minimum", "700.00"));
        assertThat(LeveledBilling.paymentCapacity(new BigDecimal("1000"), act700))
                .isEqualByComparingTo("300.00");
    }

    @Test
    @DisplayName("acordo por capacidade: desligado recusa; parcela acima da capacidade recusa; no limite passa")
    void incomeBasedRules() {
        var off = DunningParameters.fromValues(Map.of(
                "inst.income_based", "false", "dun.existential_minimum", "600.00"));
        assertThatThrownBy(() -> LeveledBilling.requireIncomeBasedInstallment(
                new BigDecimal("1000"), new BigDecimal("100"), off))
                .isInstanceOf(ApiException.Validation.class);

        var on = DunningParameters.fromValues(Map.of(
                "inst.income_based", "true", "dun.existential_minimum", "600.00"));
        assertThatThrownBy(() -> LeveledBilling.requireIncomeBasedInstallment(
                new BigDecimal("1000"), new BigDecimal("450"), on))
                .isInstanceOf(ApiException.Validation.class);
        assertThat(LeveledBilling.requireIncomeBasedInstallment(
                new BigDecimal("1000"), new BigDecimal("400"), on))
                .isEqualByComparingTo("400");
        assertThatThrownBy(() -> LeveledBilling.requireIncomeBasedInstallment(
                new BigDecimal("600"), new BigDecimal("10"), on))
                .isInstanceOf(ApiException.Validation.class);

        var noMinimum = DunningParameters.fromValues(Map.of("inst.income_based", "true"));
        assertThatThrownBy(() -> LeveledBilling.requireIncomeBasedInstallment(
                new BigDecimal("1000"), new BigDecimal("100"), noMinimum))
                .isInstanceOf(MissingParameterException.class);
    }
}
