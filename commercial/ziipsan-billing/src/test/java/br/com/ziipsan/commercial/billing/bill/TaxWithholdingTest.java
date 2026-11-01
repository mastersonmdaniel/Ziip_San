package br.com.ziipsan.commercial.billing.bill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.d;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Casos I-* (seção 10.11): retenção de impostos configurável por tenant, o último absorve o
 * resíduo. Tributos, códigos e alíquotas são FICTÍCIOS (IR 1,20; CSLL 1,00; COFINS 3,00; PIS 0,65;
 * soma 5,85, como no relatório); o produto não embute nenhum.
 */
class TaxWithholdingTest {

    private static TaxWithholdingPolicy illustrative() {
        return new TaxWithholdingPolicy(List.of(
                new TaxRate("T-A", d("1.20")), new TaxRate("T-B", d("1.00")),
                new TaxRate("T-C", d("3.00")), new TaxRate("T-D", d("0.65"))));
    }

    private static List<BigDecimal> amounts(List<TaxLine> lines) {
        return lines.stream().map(TaxLine::amount).toList();
    }

    private static BigDecimal sum(List<TaxLine> lines) {
        return lines.stream().map(TaxLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    @DisplayName("I-01: base 1.000,00: 12,00 + 10,00 + 30,00 + 6,50 = 58,50; conta 941,50")
    void i01() {
        List<TaxLine> lines = TaxWithholding.withhold(d("1000.00"), illustrative());

        assertThat(amounts(lines)).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(d("12.00"), d("10.00"), d("30.00"), d("6.50"));
        assertThat(sum(lines)).isEqualByComparingTo("58.50");
        assertThat(d("1000.00").subtract(sum(lines))).isEqualByComparingTo("941.50");
    }

    @Test
    @DisplayName("I-02: base 333,33: 4,00 + 3,33 + 10,00 + último 2,17 (= 19,50 − 17,33) = 19,50; conta 313,83")
    void i02() {
        // IR 333,33×1,20% = 3,99996 → 4,00; CSLL 3,3333 → 3,33; COFINS 9,9999 → 10,00; soma 17,33.
        // total = 333,33 × 5,85% = 19,499805 → 19,50; último = 19,50 − 17,33 = 2,17.
        List<TaxLine> lines = TaxWithholding.withhold(d("333.33"), illustrative());

        assertThat(amounts(lines)).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(d("4.00"), d("3.33"), d("10.00"), d("2.17"));
        assertThat(sum(lines)).isEqualByComparingTo("19.50");
        assertThat(d("333.33").subtract(sum(lines))).isEqualByComparingTo("313.83");
    }

    @Test
    @DisplayName("o último absorve o resíduo: 3 tributos de 1,50% sobre 0,30 → 0,00 + 0,00 + 0,01 (total 4,5% × 0,30 = 0,0135 → 0,01)")
    void lastAbsorbsResidue() {
        TaxWithholdingPolicy policy = new TaxWithholdingPolicy(List.of(
                new TaxRate("T-A", d("1.50")), new TaxRate("T-B", d("1.50")), new TaxRate("T-C", d("1.50"))));

        List<TaxLine> lines = TaxWithholding.withhold(d("0.30"), policy);

        // cada um: 0,30 × 1,5% = 0,0045 → HALF_DOWN 0,00 ; total 0,0135 → 0,01 ; último = 0,01 − 0,00 − 0,00
        assertThat(amounts(lines)).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(d("0.00"), d("0.00"), d("0.01"));
        assertThat(sum(lines)).isEqualByComparingTo("0.01");
    }

    @Test
    @DisplayName("resíduo negativo (divergência do legado): base 0,55 com 1,20/1,00/3,00/0,65%: a fórmula do legado dá último = −0,01; aqui o excedente volta ao anterior → 0,01 + 0,01 + 0,01 + 0,00 = 0,03")
    void negativeResidueCascadesBackwards() {
        // 0,55 × 1,20% = 0,0066 → 0,01 ; × 1,00% = 0,0055 → 0,01 (> meio centavo) ; × 3,00% = 0,0165 → 0,02 ; × 0,65% = 0,003575 → 0,00
        // soma direta 0,04; total 0,55 × 5,85% = 0,032175 → 0,03; resíduo −0,01.
        // legado: último = 0,03 − (0,01 + 0,01 + 0,02) = −0,01 (imposto negativo!). Aqui o último (0,00) não tem
        // o que devolver, então o 0,01 sai do anterior (COFINS-like: 0,02 → 0,01).
        List<TaxLine> lines = TaxWithholding.withhold(d("0.55"), illustrative());

        assertThat(amounts(lines)).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(d("0.01"), d("0.01"), d("0.01"), d("0.00"));
        assertThat(sum(lines)).isEqualByComparingTo("0.03");
    }

    @Test
    @DisplayName("sem o ajuste, o resíduo seria perdido: 3 tributos de 2,50% sobre 7,00 → 0,17 + 0,17 + 0,18 (e não 0,17 × 3 = 0,51 contra 0,525 → 0,52)")
    void withoutAbsorptionTheTotalWouldBeWrong() {
        TaxWithholdingPolicy policy = new TaxWithholdingPolicy(List.of(
                new TaxRate("T-A", d("2.50")), new TaxRate("T-B", d("2.50")), new TaxRate("T-C", d("2.50"))));

        List<TaxLine> lines = TaxWithholding.withhold(d("7.00"), policy);

        // cada um 7,00 × 2,5% = 0,175 → HALF_DOWN: empate vai para baixo → 0,17; total 7,00 × 7,5% = 0,525 → 0,52
        assertThat(amounts(lines)).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(d("0.17"), d("0.17"), d("0.18"));
        assertThat(sum(lines)).isEqualByComparingTo("0.52");
    }

    @Test
    @DisplayName("HALF_DOWN documentado: empate de meio centavo arredonda para baixo (0,10 × 5% = 0,005 → 0,00; HALF_UP daria 0,01)")
    void halfDownTie() {
        List<TaxLine> lines = TaxWithholding.withhold(d("0.10"), new TaxWithholdingPolicy(List.of(new TaxRate("T-A", d("5.00")))));

        assertThat(lines.get(0).amount()).isEqualByComparingTo("0.00");
        assertThat(d("0.10").multiply(d("5.00")).divide(d("100"), 2, java.math.RoundingMode.HALF_UP)).isEqualByComparingTo("0.01");
    }

    @Test
    @DisplayName("a base é truncada (DOWN) a 2 casas: 100,009 → 100,00")
    void baseIsTruncated() {
        List<TaxLine> lines = TaxWithholding.withhold(d("100.009"), new TaxWithholdingPolicy(List.of(new TaxRate("T-A", d("10.00")))));

        assertThat(lines.get(0).base()).isEqualByComparingTo("100.00");
        assertThat(lines.get(0).amount()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("base zero ou negativa: todos os impostos zero, sem exceção")
    void zeroBase() {
        assertThat(sum(TaxWithholding.withhold(d("0.00"), illustrative()))).isEqualByComparingTo("0.00");
        assertThat(TaxWithholding.withhold(d("0.00"), illustrative())).hasSize(4);
    }

    @Test
    @DisplayName("propriedade: para toda base de 0,01 a 3.000,00, a soma dos impostos é exatamente base × Σalíquotas (HALF_DOWN) e nenhum é negativo")
    void sumIsAlwaysTheRoundedTotal() {
        TaxWithholdingPolicy policy = illustrative();
        for (int cents = 1; cents <= 300_000; cents++) {
            BigDecimal base = BigDecimal.valueOf(cents, 2);
            List<TaxLine> lines = TaxWithholding.withhold(base, policy);
            BigDecimal expectedTotal = base.multiply(d("5.85")).divide(d("100"), 2, java.math.RoundingMode.HALF_DOWN);
            assertThat(sum(lines)).as("base %s", base).isEqualByComparingTo(expectedTotal);
            for (TaxLine l : lines) {
                assertThat(l.amount().signum()).isGreaterThanOrEqualTo(0);
            }
        }
    }

    @Test
    @DisplayName("I-03 e política: sem retenção configurada é erro; alíquota fora de (0,100] e soma acima de 100% também")
    void policyValidation() {
        assertThatThrownBy(() -> new TaxWithholdingPolicy(List.of())).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nenhuma alíquota");
        assertThatThrownBy(() -> new TaxWithholdingPolicy(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TaxRate("T-A", d("0"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TaxRate("T-A", d("100.01"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TaxRate("", d("1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TaxRate("T-A", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TaxWithholdingPolicy(List.of(new TaxRate("T-A", d("60")), new TaxRate("T-B", d("40.01")))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("100%");
    }
}
