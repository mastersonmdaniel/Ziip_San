package br.com.ziipsan.commercial.billing.leakmeter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P3.28: o calculo do desconto, com os valores conferidos a mao.
 * Massa comum: agua medida 50, media 20 (excesso 30); esgoto medido 40,
 * media 16 (excesso 24).
 */
class LeakDiscountRuleTest {

    private static final LeakDiscountRule.Volumes COMMON = new LeakDiscountRule.Volumes(
            new BigDecimal("50"), new BigDecimal("20"), new BigDecimal("40"), new BigDecimal("16"));

    private static LeakMeterParameters params(Map<String, String> values) {
        return LeakMeterParameters.fromValues(values);
    }

    private static Map<String, String> base(String rule) {
        Map<String, String> map = new HashMap<>();
        map.put(LeakMeterParameters.LEAK_DISCOUNT_RULE, "\"" + rule + "\"");
        map.put(LeakMeterParameters.LEAK_MAX_DISCOUNT_PCT_WATER, "70");
        return map;
    }

    @Test
    @DisplayName("PCT_OF_EXCESS com esgoto pela media: agua 50 - 70% de 30 = 29; esgoto = media 16")
    void percentOfExcessSewerByAverage() {
        Map<String, String> map = base("PCT_OF_EXCESS");
        map.put(LeakMeterParameters.LEAK_SEWER_BY_AVERAGE, "true");
        LeakDiscountRule.Result r = LeakDiscountRule.apply(params(map), COMMON);
        assertThat(r.revisedWater()).isEqualByComparingTo("29");
        assertThat(r.waterDiscount()).isEqualByComparingTo("21");
        assertThat(r.revisedSewer()).isEqualByComparingTo("16");
        assertThat(r.sewerDiscount()).isEqualByComparingTo("24");
        assertThat(r.effectiveRule()).isEqualTo(LeakMeterParameters.DiscountRule.PCT_OF_EXCESS);
    }

    @Test
    @DisplayName("PCT_OF_EXCESS com esgoto por percentual: esgoto 40 - 50% de 24 = 28")
    void percentOfExcessSewerByPercent() {
        Map<String, String> map = base("PCT_OF_EXCESS");
        map.put(LeakMeterParameters.LEAK_SEWER_BY_AVERAGE, "false");
        map.put(LeakMeterParameters.LEAK_MAX_DISCOUNT_PCT_SEWER, "50");
        LeakDiscountRule.Result r = LeakDiscountRule.apply(params(map), COMMON);
        assertThat(r.revisedWater()).isEqualByComparingTo("29");
        assertThat(r.revisedSewer()).isEqualByComparingTo("28");
        assertThat(r.sewerDiscount()).isEqualByComparingTo("12");
    }

    @Test
    @DisplayName("PCT_OF_EXCESS sem excesso nao desconta nem aumenta: medido 15 com media 20 fica 15")
    void percentWithoutExcess() {
        Map<String, String> map = base("PCT_OF_EXCESS");
        map.put(LeakMeterParameters.LEAK_SEWER_BY_AVERAGE, "true");
        LeakDiscountRule.Result r = LeakDiscountRule.apply(params(map), new LeakDiscountRule.Volumes(
                new BigDecimal("15"), new BigDecimal("20"), new BigDecimal("10"), new BigDecimal("16")));
        assertThat(r.revisedWater()).isEqualByComparingTo("15");
        assertThat(r.waterDiscount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("AVERAGE_6M: revisto e a media, agua e esgoto (20 e 16)")
    void averageRule() {
        LeakDiscountRule.Result r = LeakDiscountRule.apply(params(base("AVERAGE_6M")), COMMON);
        assertThat(r.revisedWater()).isEqualByComparingTo("20");
        assertThat(r.revisedSewer()).isEqualByComparingTo("16");
        assertThat(r.effectiveRule()).isEqualTo(LeakMeterParameters.DiscountRule.AVERAGE_6M);
    }

    @Test
    @DisplayName("AVERAGE_6M e base de faturamento: medido 15 abaixo da media 20 revisa PARA a media (documentado)")
    void averageRuleRaisesBelowAverage() {
        LeakDiscountRule.Result r = LeakDiscountRule.apply(params(base("AVERAGE_6M")),
                new LeakDiscountRule.Volumes(new BigDecimal("15"), new BigDecimal("20"),
                        new BigDecimal("10"), new BigDecimal("16")));
        assertThat(r.revisedWater()).isEqualByComparingTo("20");
        assertThat(r.waterDiscount()).isEqualByComparingTo("-5");
    }

    @Test
    @DisplayName("MOST_FAVORABLE na massa comum: media (36) vence o percentual (45) e o ramo efetivo e AVERAGE_6M")
    void mostFavorablePicksAverage() {
        Map<String, String> map = base("MOST_FAVORABLE");
        map.put(LeakMeterParameters.LEAK_SEWER_BY_AVERAGE, "true");
        LeakDiscountRule.Result r = LeakDiscountRule.apply(params(map), COMMON);
        assertThat(r.revisedWater()).isEqualByComparingTo("20");
        assertThat(r.revisedSewer()).isEqualByComparingTo("16");
        assertThat(r.effectiveRule()).isEqualTo(LeakMeterParameters.DiscountRule.AVERAGE_6M);
    }

    @Test
    @DisplayName("MOST_FAVORABLE protege quem mediu abaixo da media: percentual (15+16=31) vence a media (36)")
    void mostFavorableProtectsBelowAverage() {
        Map<String, String> map = base("MOST_FAVORABLE");
        map.put(LeakMeterParameters.LEAK_SEWER_BY_AVERAGE, "true");
        LeakDiscountRule.Result r = LeakDiscountRule.apply(params(map), new LeakDiscountRule.Volumes(
                new BigDecimal("15"), new BigDecimal("20"), new BigDecimal("10"), new BigDecimal("16")));
        assertThat(r.revisedWater()).isEqualByComparingTo("15");
        assertThat(r.revisedSewer()).isEqualByComparingTo("16");
        assertThat(r.effectiveRule()).isEqualTo(LeakMeterParameters.DiscountRule.PCT_OF_EXCESS);
    }

    @Test
    @DisplayName("MOST_FAVORABLE com desconto de 100%: empate entre os ramos resolve para AVERAGE_6M")
    void mostFavorableTieGoesToAverage() {
        Map<String, String> map = base("MOST_FAVORABLE");
        map.put(LeakMeterParameters.LEAK_MAX_DISCOUNT_PCT_WATER, "100");
        map.put(LeakMeterParameters.LEAK_SEWER_BY_AVERAGE, "true");
        LeakDiscountRule.Result r = LeakDiscountRule.apply(params(map), COMMON);
        assertThat(r.revisedWater()).isEqualByComparingTo("20");
        assertThat(r.effectiveRule()).isEqualTo(LeakMeterParameters.DiscountRule.AVERAGE_6M);
    }

    @Test
    @DisplayName("aritmetica exata sem arredondamento: excesso 30 a 33,33% desconta 9,999")
    void exactArithmetic() {
        Map<String, String> map = base("PCT_OF_EXCESS");
        map.put(LeakMeterParameters.LEAK_MAX_DISCOUNT_PCT_WATER, "33.33");
        map.put(LeakMeterParameters.LEAK_SEWER_BY_AVERAGE, "true");
        LeakDiscountRule.Result r = LeakDiscountRule.apply(params(map), COMMON);
        assertThat(r.waterDiscount()).isEqualByComparingTo("9.999");
        assertThat(r.revisedWater()).isEqualByComparingTo("40.001");
    }
}
