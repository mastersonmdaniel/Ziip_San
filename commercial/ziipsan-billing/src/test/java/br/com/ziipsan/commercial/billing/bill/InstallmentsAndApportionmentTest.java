package br.com.ziipsan.commercial.billing.bill;

import br.com.ziipsan.commercial.billing.math.Apportionment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.d;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Casos D-* (parcelas e repartição de débito, seção 10.10) e RC-05 (rateio de valor, 10.13), mais as
 * propriedades "a soma das partes é o total" (10.16).
 */
class InstallmentsAndApportionmentTest {

    private static List<BigDecimal> allInstallments(String total, int n) {
        List<BigDecimal> parts = new ArrayList<>();
        for (int done = 0; done < n; done++) {
            parts.add(Installments.next(d(total), n, done));
        }
        return parts;
    }

    @Test
    @DisplayName("D-01: R$ 100,00 em 3 parcelas: 100/3 = 33,333 → trunca 33,33; a última leva o resto: 33,33 + 33,33 + 33,34")
    void d01() {
        assertThat(allInstallments("100.00", 3)).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(d("33.33"), d("33.33"), d("33.34"));
    }

    @Test
    @DisplayName("C-01 (parcelas): R$ 35,00 em 6: 35/6 = 5,8333 → 5,83 × 5 = 29,15; a sexta = 35,00 − 29,15 = 5,85")
    void c01Installments() {
        assertThat(allInstallments("35.00", 6)).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(d("5.83"), d("5.83"), d("5.83"), d("5.83"), d("5.83"), d("5.85"));
    }

    @ParameterizedTest(name = "R$ {0} em {1} parcelas: {2} (×{1}−1) + última {3}")
    @CsvSource({
            "10.00, 3, 3.33, 3.34",
            "10.00, 4, 2.50, 2.50",
            "0.05, 3, 0.01, 0.03",
            "1000.00, 12, 83.33, 83.37",
            "1.00, 1, 1.00, 1.00",
            "99.99, 2, 49.99, 50.00"
    })
    @DisplayName("parcela regular truncada; a última absorve os centavos")
    void regularAndLast(String total, int n, String regular, String last) {
        List<BigDecimal> parts = allInstallments(total, n);

        assertThat(parts.get(0)).isEqualByComparingTo(regular);
        assertThat(parts.get(n - 1)).isEqualByComparingTo(last);
    }

    @Test
    @DisplayName("propriedade (10.16): a soma das parcelas é exatamente o total, para qualquer total e número de parcelas")
    void installmentsAlwaysSumToTotal() {
        Random random = new Random(42);
        for (int i = 0; i < 3000; i++) {
            BigDecimal total = BigDecimal.valueOf(1 + random.nextInt(2_000_000), 2);
            int n = 1 + random.nextInt(24);
            if (total.compareTo(BigDecimal.valueOf(n).movePointLeft(2)) < 0) {
                continue; // total menor que 0,01 por parcela: coberto pelo teste de recusa
            }
            BigDecimal sum = allInstallments(total.toPlainString(), n).stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(sum).as("%s em %d", total, n).isEqualByComparingTo(total);
        }
    }

    @Test
    @DisplayName("total menor que R$ 0,01 por parcela é recusado (parcela de 0,00 não existe); parcelas fora do intervalo também")
    void invalidInstallments() {
        assertThatThrownBy(() -> Installments.next(d("0.02"), 3, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Installments.next(d("10.00"), 3, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Installments.next(d("10.00"), 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Installments.next(d("0.00"), 1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("D-02: repartir R$ 10,00 entre RES (1 eco) e COM (2 eco), política do legado: fator 10/3 = 3,33; RES 3,33; COM 6,66; a diferença 0,01 vai à 1ª → 3,34 / 6,66")
    void d02ResidueToFirst() {
        assertThat(Apportionment.residueToFirst(d("10.00"), List.of(1, 2)))
                .usingElementComparator(BigDecimal::compareTo).containsExactly(d("3.34"), d("6.66"));
    }

    @Test
    @DisplayName("D-02, comparação: por maior resto o centavo vai a quem tem o maior resto (COM: 6,6667 → 6,67), dando 3,33 / 6,67; a soma é a mesma")
    void d02LargestRemainderDiffersFromLegacyPolicy() {
        // exato: 10,00 × 1/3 = 3,3333 (resto 0,0033) e 10,00 × 2/3 = 6,6667 (resto 0,0067). O centavo que sobra
        // vai ao maior resto. É uma política diferente da do legado, mais próxima do valor exato; a escolha
        // entre as duas é do produto (o relatório 02 registra a do legado).
        assertThat(Apportionment.byIntegerWeights(d("10.00"), List.of(1, 2)))
                .usingElementComparator(BigDecimal::compareTo).containsExactly(d("3.33"), d("6.67"));
    }

    @Test
    @DisplayName("RC-05: ratear R$ 70,00 entre 3 economias: Σ = 70,00 (23,33 + 23,33 + 23,34); o legado dava 69,99")
    void rc05() {
        List<BigDecimal> parts = Apportionment.byIntegerWeights(d("70.00"), List.of(1, 1, 1));

        assertThat(parts).usingElementComparator(BigDecimal::compareTo).containsExactly(d("23.33"), d("23.33"), d("23.34"));
        assertThat(parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("70.00");
        // o que o legado fazia: piso de cada parte em centavos, sem redistribuir
        BigDecimal legacyEach = d("70.00").divide(BigDecimal.valueOf(3), 2, java.math.RoundingMode.FLOOR);
        assertThat(legacyEach.multiply(BigDecimal.valueOf(3))).isEqualByComparingTo("69.99");
    }

    @Test
    @DisplayName("propriedade: por maior resto a soma é o total, nenhuma parte é negativa e cada parte fica a menos de 1 centavo do quinhão exato")
    void proportionalProperties() {
        Random random = new Random(7);
        for (int i = 0; i < 2000; i++) {
            BigDecimal total = BigDecimal.valueOf(random.nextInt(1_000_000), 2);
            int parts = 1 + random.nextInt(8);
            List<Long> weights = new ArrayList<>();
            long sum = 0;
            for (int p = 0; p < parts; p++) {
                long w = random.nextInt(50);
                weights.add(w);
                sum += w;
            }
            if (sum == 0) {
                weights.set(0, 1L);
                sum = 1;
            }
            List<BigDecimal> result = Apportionment.proportional(total, weights);

            assertThat(result.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(total);
            for (int p = 0; p < parts; p++) {
                assertThat(result.get(p).signum()).isGreaterThanOrEqualTo(0);
                BigDecimal exact = total.multiply(BigDecimal.valueOf(weights.get(p)))
                        .divide(BigDecimal.valueOf(sum), 10, java.math.RoundingMode.HALF_UP);
                assertThat(result.get(p).subtract(exact).abs()).isLessThan(d("0.01"));
                if (weights.get(p) == 0) {
                    assertThat(result.get(p)).isEqualByComparingTo("0.00");
                }
            }
        }
    }

    @Test
    @DisplayName("repartição: todos os pesos zero só é aceita para total zero; peso negativo e lista vazia são recusados")
    void apportionmentInvalidInputs() {
        assertThat(Apportionment.proportional(d("0.00"), List.of(0L, 0L)))
                .usingElementComparator(BigDecimal::compareTo).containsExactly(d("0"), d("0"));
        assertThatThrownBy(() -> Apportionment.proportional(d("1.00"), List.of(0L, 0L))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Apportionment.proportional(d("1.00"), List.of(1L, -1L))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Apportionment.proportional(d("1.00"), List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Apportionment.proportional(d("-1.00"), List.of(1L))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Apportionment.proportional(d("1.005"), List.of(1L))).isInstanceOf(ArithmeticException.class);
    }
}
