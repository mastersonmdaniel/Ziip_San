package br.com.sigsan.commercial.billing.bill;

import br.com.sigsan.commercial.billing.bill.CondominiumApportionment.Micro;
import br.com.sigsan.commercial.billing.bill.CondominiumApportionment.Share;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static br.com.sigsan.commercial.billing.fixture.FictitiousTariffs.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Casos RC-* (seção 10.13): rateio do consumo de condomínio. RC-05 (rateio por valor) está em
 * {@code InstallmentsAndApportionmentTest}. Parâmetro de incremento máximo fictício.
 */
class CondominiumApportionmentTest {

    private static List<Micro> metered(int count, int billedEach, int economiesEach) {
        List<Micro> micros = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            micros.add(new Micro(id(i), billedEach, economiesEach, true));
        }
        return micros;
    }

    private static List<Integer> consumptions(List<Share> shares) {
        return shares.stream().map(Share::consumption).toList();
    }

    @Test
    @DisplayName("RC-01: macro 100; 4 micros medidos (1 eco) com 20 cada = 80; a ratear 20; 20 DIV 4 = 5 por eco; cada micro recebe 5")
    void rc01() {
        assertThat(consumptions(CondominiumApportionment.apportion(100, metered(4, 20, 1), 10))).containsExactly(5, 5, 5, 5);
    }

    @Test
    @DisplayName("RC-02: macro 60 e micros 80: a ratear = MAX(0, −20) = 0")
    void rc02() {
        assertThat(consumptions(CondominiumApportionment.apportion(60, metered(4, 20, 1), 10))).containsExactly(0, 0, 0, 0);
    }

    @Test
    @DisplayName("RC-03: a ratear 80 com 4 eco medidas e incremento máximo 10: 80 DIV 4 = 20, limitado a 10 → cada micro recebe 10")
    void rc03() {
        // macro 160, micros 4 × 20 = 80 → a ratear 80
        assertThat(consumptions(CondominiumApportionment.apportion(160, metered(4, 20, 1), 10))).containsExactly(10, 10, 10, 10);
    }

    @Test
    @DisplayName("RC-04: 1 micro NÃO medido (2 eco) + 2 medidos; a ratear 30: rateia só entre os não medidos: 30 DIV 2 = 15 por eco → o não medido recebe 30; os medidos 0")
    void rc04() {
        // macro 50; micros faturados: não medido 0, medidos 10 + 10 = 20 → a ratear 30
        List<Micro> micros = List.of(
                new Micro(id(1), 0, 2, false),
                new Micro(id(2), 10, 1, true),
                new Micro(id(3), 10, 1, true));

        assertThat(consumptions(CondominiumApportionment.apportion(50, micros, 10))).containsExactly(30, 0, 0);
    }

    @Test
    @DisplayName("com micro não medido o limite de incremento não se aplica (só vale quando todos são medidos)")
    void capDoesNotApplyToNotMetered() {
        List<Micro> micros = List.of(new Micro(id(1), 0, 1, false), new Micro(id(2), 10, 1, true));

        // a ratear 90 − 10 = 80; um não medido (1 eco): 80 por eco, sem o teto de 10
        assertThat(consumptions(CondominiumApportionment.apportion(90, micros, 10))).containsExactly(80, 0);
    }

    @Test
    @DisplayName("a divisão é inteira: o resto de m³ não é rateado (a ratear 21 com 4 eco → 5 por eco, sobra 1)")
    void remainderIsNotApportioned() {
        List<Integer> shares = consumptions(CondominiumApportionment.apportion(101, metered(4, 20, 1), 10));

        assertThat(shares).containsExactly(5, 5, 5, 5);
        assertThat(shares.stream().mapToInt(Integer::intValue).sum()).isEqualTo(20);
    }

    @Test
    @DisplayName("micro com mais de uma economia recebe por economia: 2 eco × 5 = 10")
    void multiEconomyMicro() {
        List<Micro> micros = List.of(new Micro(id(1), 20, 2, true), new Micro(id(2), 20, 1, true), new Micro(id(3), 20, 1, true));

        // macro 100; faturado 60; a ratear 40; 40 DIV 4 eco = 10 por eco (limite 10)
        assertThat(consumptions(CondominiumApportionment.apportion(100, micros, 10))).containsExactly(20, 10, 10);
    }

    @Test
    @DisplayName("parâmetro de incremento máximo é obrigatório; condomínio vazio é recusado")
    void invalidInputs() {
        assertThatThrownBy(() -> CondominiumApportionment.apportion(100, metered(2, 10, 1), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CondominiumApportionment.apportion(100, List.of(), 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Micro(id(1), 10, 0, true)).isInstanceOf(IllegalArgumentException.class);
    }
}
