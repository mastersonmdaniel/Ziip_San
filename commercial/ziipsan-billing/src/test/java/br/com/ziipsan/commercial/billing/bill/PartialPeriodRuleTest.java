package br.com.ziipsan.commercial.billing.bill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Período parcial proporcional (JOR-11, RAS-14, P2.5): {@code bill.partial_period_rule} com
 * padrão proporcional, faixa de referência de 27 a 33 dias (Res. ARESC 046/2016).
 *
 * <p>Valores FICTÍCIOS, recalculados à mão em cada teste: parte fixa cheia de 30,00 e franquia
 * cheia de 10 m³ (os mesmos da tarifa fictícia T1), ciclo de referência de 30 dias. Fator de
 * dias com 4 casas HALF_UP; parte fixa HALF_UP aos centavos; franquia e consumo HALF_UP ao m³.
 */
class PartialPeriodRuleTest {

    private static PartialPeriodRule.Input input(PartialPeriodRule.PeriodKind kind, int days, int measured) {
        return new PartialPeriodRule.Input(kind, days, 30, new BigDecimal("30.00"), 10, measured, false, false);
    }

    @Test
    @DisplayName("primeira conta (15 de 30 dias, medido 8): fator 0,5000; fixa 30,00 x 0,5 = 15,00; franquia 10 x 0,5 = 5; excedente 8 - 5 = 3")
    void firstBillIsProportional() {
        PartialPeriodRule.Result r = PartialPeriodRule.adjust(
                input(PartialPeriodRule.PeriodKind.FIRST_BILL, 15, 8));

        assertThat(r.partial()).isTrue();
        assertThat(r.withinReferenceBand()).isFalse();
        assertThat(r.dayFactor()).isEqualByComparingTo("0.5000");
        assertThat(r.fixedAmount()).isEqualByComparingTo("15.00");
        assertThat(r.minimumConsumption()).isEqualTo(5);
        // O medido da primeira conta é o consumo real dos 15 dias: não é escalado.
        assertThat(r.billedConsumption()).isEqualTo(8);
        assertThat(r.excessConsumption()).isEqualTo(3);
    }

    @Test
    @DisplayName("conta final (10 de 30 dias, medido 4): fator 0,3333; fixa 30,00 x 0,3333 = 9,999 -> 10,00; franquia 10 x 0,3333 = 3,333 -> 3; excedente 4 - 3 = 1")
    void finalBillIsProportional() {
        PartialPeriodRule.Result r = PartialPeriodRule.adjust(
                input(PartialPeriodRule.PeriodKind.FINAL_BILL, 10, 4));

        assertThat(r.partial()).isTrue();
        assertThat(r.dayFactor()).isEqualByComparingTo("0.3333");
        assertThat(r.fixedAmount()).isEqualByComparingTo("10.00");
        assertThat(r.minimumConsumption()).isEqualTo(3);
        assertThat(r.billedConsumption()).isEqualTo(4);
        assertThat(r.excessConsumption()).isEqualTo(1);
    }

    @Test
    @DisplayName("ciclo regular dentro da faixa (30 dias, medido 27): sem ajuste; fixa cheia 30,00; franquia 10; excedente 27 - 10 = 17")
    void regularCycleWithinTheBandIsNotAdjusted() {
        PartialPeriodRule.Result r = PartialPeriodRule.adjust(
                input(PartialPeriodRule.PeriodKind.REGULAR_CYCLE, 30, 27));

        assertThat(r.partial()).isFalse();
        assertThat(r.withinReferenceBand()).isTrue();
        assertThat(r.dayFactor()).isEqualByComparingTo("1.0000");
        assertThat(r.fixedAmount()).isEqualByComparingTo("30.00");
        assertThat(r.minimumConsumption()).isEqualTo(10);
        assertThat(r.billedConsumption()).isEqualTo(27);
        assertThat(r.excessConsumption()).isEqualTo(17);
    }

    @Test
    @DisplayName("bordas da faixa (27 e 33 dias, ciclo regular): dentro da faixa, sem ajuste")
    void bandEdgesAreWithinTheBand() {
        assertThat(PartialPeriodRule.adjust(input(PartialPeriodRule.PeriodKind.REGULAR_CYCLE, 27, 20)).partial())
                .isFalse();
        assertThat(PartialPeriodRule.adjust(input(PartialPeriodRule.PeriodKind.REGULAR_CYCLE, 33, 20)).partial())
                .isFalse();
    }

    @Test
    @DisplayName("leitura acima de 33 dias (40 dias, medido 34): fator 1,3333; fixa 30,00 x 1,3333 = 39,999 -> 40,00; franquia 13; consumo normalizado 34/40 x 30 = 25,5 -> 26; excedente 26 - 13 = 13")
    void readingAbove33DaysIsAdjustedByDays() {
        PartialPeriodRule.Result r = PartialPeriodRule.adjust(
                input(PartialPeriodRule.PeriodKind.REGULAR_CYCLE, 40, 34));

        assertThat(r.partial()).isTrue();
        assertThat(r.withinReferenceBand()).isFalse();
        assertThat(r.dayFactor()).isEqualByComparingTo("1.3333");
        assertThat(r.fixedAmount()).isEqualByComparingTo("40.00");
        assertThat(r.minimumConsumption()).isEqualTo(13);
        assertThat(r.billedConsumption()).isEqualTo(26);
        assertThat(r.excessConsumption()).isEqualTo(13);
    }

    @Test
    @DisplayName("ciclo regular abaixo da faixa (20 dias, medido 18): fator 0,6667; fixa 20,00; franquia 7; consumo normalizado 18/20 x 30 = 27; excedente 27 - 7 = 20")
    void regularCycleBelowTheBandIsAdjustedByDays() {
        PartialPeriodRule.Result r = PartialPeriodRule.adjust(
                input(PartialPeriodRule.PeriodKind.REGULAR_CYCLE, 20, 18));

        assertThat(r.partial()).isTrue();
        assertThat(r.dayFactor()).isEqualByComparingTo("0.6667");
        assertThat(r.fixedAmount()).isEqualByComparingTo("20.00");
        assertThat(r.minimumConsumption()).isEqualTo(7);
        assertThat(r.billedConsumption()).isEqualTo(27);
        assertThat(r.excessConsumption()).isEqualTo(20);
    }

    @Test
    @DisplayName("sazonalidade e fonte alternativa (P1.3) são entradas declaradas: voltam no resultado e não mudam o valor")
    void seasonalityAndAlternativeSourceAreDeclaredInputs() {
        PartialPeriodRule.Input withFlags = new PartialPeriodRule.Input(
                PartialPeriodRule.PeriodKind.FIRST_BILL, 15, 30, new BigDecimal("30.00"), 10, 8, true, true);
        PartialPeriodRule.Result flagged = PartialPeriodRule.adjust(withFlags);
        PartialPeriodRule.Result plain = PartialPeriodRule.adjust(
                input(PartialPeriodRule.PeriodKind.FIRST_BILL, 15, 8));

        assertThat(flagged.seasonal()).isTrue();
        assertThat(flagged.hasAlternativeWaterSource()).isTrue();
        assertThat(flagged.fixedAmount()).isEqualByComparingTo(plain.fixedAmount());
        assertThat(flagged.minimumConsumption()).isEqualTo(plain.minimumConsumption());
        assertThat(flagged.billedConsumption()).isEqualTo(plain.billedConsumption());
    }

    @Test
    @DisplayName("bill.partial_period_rule: ausência vale o padrão proporcional (classe b); nome fora do catálogo é recusado")
    void parameterValueParsing() {
        assertThat(PartialPeriodRule.Rule.fromParameterValue(null))
                .isEqualTo(PartialPeriodRule.Rule.PROPORTIONAL);
        assertThat(PartialPeriodRule.Rule.fromParameterValue(" "))
                .isEqualTo(PartialPeriodRule.Rule.PROPORTIONAL);
        assertThat(PartialPeriodRule.Rule.fromParameterValue("\"PROPORTIONAL\""))
                .isEqualTo(PartialPeriodRule.Rule.PROPORTIONAL);
        assertThatThrownBy(() -> PartialPeriodRule.Rule.fromParameterValue("\"REGRA_INVENTADA\""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bill.partial_period_rule");
        assertThatThrownBy(() -> PartialPeriodRule.Rule.fromParameterValue("30"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bill.partial_period_rule");
    }

    @Test
    @DisplayName("entradas incoerentes são recusadas: dias < 1, referência < 1, valores negativos")
    void invalidInputsAreRefused() {
        assertThatThrownBy(() -> new PartialPeriodRule.Input(PartialPeriodRule.PeriodKind.FIRST_BILL,
                0, 30, new BigDecimal("30.00"), 10, 8, false, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PartialPeriodRule.Input(PartialPeriodRule.PeriodKind.FIRST_BILL,
                15, 0, new BigDecimal("30.00"), 10, 8, false, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PartialPeriodRule.Input(PartialPeriodRule.PeriodKind.FIRST_BILL,
                15, 30, new BigDecimal("30.00"), 10, -1, false, false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
