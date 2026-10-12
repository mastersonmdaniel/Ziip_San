package br.com.sigsan.operations.metering.rules;

import br.com.sigsan.operations.metering.rules.CondominiumApportionment.Input;
import br.com.sigsan.operations.metering.rules.CondominiumApportionment.Micro;
import br.com.sigsan.operations.metering.rules.CondominiumApportionment.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Rateio de consumo de condomínio (relatório 02, 3.11; casos RC-01 a RC-04). Valores FICTÍCIOS,
 * recalculados à mão. Regra de arredondamento sob teste: divisão inteira (DOWN), com o resto
 * avisado. O rateio por valor em reais (RC-05) é do módulo de faturamento.
 */
class CondominiumApportionmentTest {

    private static List<Micro> fourMeteredMicros() {
        return List.of(new Micro("A", 20, 1, true), new Micro("B", 20, 1, true),
                new Micro("C", 20, 1, true), new Micro("D", 20, 1, true));
    }

    @Test
    @DisplayName("RC-01: macro 100, 4 micros medidos somam 80: a ratear 20; 20 DIV 4 = 5 por economia; 5 em cada micro")
    void rc01_evenDivision() {
        Result r = CondominiumApportionment.apportion(new Input(100, fourMeteredMicros(), 10));
        assertThat(r.toBeApportioned()).isEqualTo(20);
        assertThat(r.perEconomy()).isEqualTo(5);
        assertThat(r.byMicro()).containsOnly(entry("A", 5), entry("B", 5), entry("C", 5), entry("D", 5));
        assertThat(r.warnings()).isEmpty();
    }

    @Test
    @DisplayName("RC-02: macro 60 e micros 80: MAX(0, -20) = 0, ninguém recebe")
    void rc02_neverNegative() {
        Result r = CondominiumApportionment.apportion(new Input(60, fourMeteredMicros(), 10));
        assertThat(r.toBeApportioned()).isZero();
        assertThat(r.byMicro().values()).containsOnly(0);
    }

    @Test
    @DisplayName("RC-03: a ratear 80 com 4 economias medidas e teto 10: 80 DIV 4 = 20, limitado a 10; 40 m3 ficam sem ratear e isso é avisado")
    void rc03_cappedByMaximumIncrement() {
        Result r = CondominiumApportionment.apportion(new Input(160, fourMeteredMicros(), 10));
        assertThat(r.toBeApportioned()).isEqualTo(80);
        assertThat(r.perEconomy()).isEqualTo(10);
        assertThat(r.byMicro().values()).containsOnly(10);
        assertThat(r.warnings()).extracting(CalculationWarning::code).containsExactly(WarningCode.APPORTIONMENT_CAPPED);
        assertThat(r.warnings().get(0).message()).contains("40 m³");
    }

    @Test
    @DisplayName("RC-04: 1 micro não medido (2 economias) e 2 medidos; a ratear 30: só o não medido recebe: 30 DIV 2 = 15 x 2 = 30")
    void rc04_onlyNotMeteredMicrosReceive() {
        // macro 70; micros: A não medido 2 eco (10), B e C medidos (15 + 15) = 40; a ratear 30
        List<Micro> micros = List.of(new Micro("A", 10, 2, false), new Micro("B", 15, 1, true),
                new Micro("C", 15, 1, true));
        Result r = CondominiumApportionment.apportion(new Input(70, micros, 10));
        assertThat(r.toBeApportioned()).isEqualTo(30);
        assertThat(r.perEconomy()).isEqualTo(15);
        assertThat(r.byMicro()).containsOnly(entry("A", 30), entry("B", 0), entry("C", 0));
        assertThat(r.warnings()).isEmpty();
    }

    @Test
    @DisplayName("o teto de incremento não vale entre não medidos (sem teto no relatório): 90 DIV 2 = 45")
    void noCapAmongNotMetered() {
        List<Micro> micros = List.of(new Micro("A", 0, 2, false), new Micro("B", 10, 1, true));
        Result r = CondominiumApportionment.apportion(new Input(100, micros, 10));
        assertThat(r.toBeApportioned()).isEqualTo(90);
        assertThat(r.byMicro()).containsOnly(entry("A", 90), entry("B", 0));
    }

    @Test
    @DisplayName("resto da divisão inteira não se perde em silêncio: 21 DIV 4 = 5, sobra 1, com aviso")
    void remainderIsWarned() {
        // macro 101, micros 80 -> a ratear 21
        Result r = CondominiumApportionment.apportion(new Input(101, fourMeteredMicros(), 100));
        assertThat(r.perEconomy()).isEqualTo(5);
        assertThat(r.byMicro().values()).containsOnly(5);
        assertThat(r.warnings()).extracting(CalculationWarning::code)
                .containsExactly(WarningCode.APPORTIONMENT_REMAINDER_NOT_DISTRIBUTED);
        assertThat(r.warnings().get(0).message()).contains("1 m³");
    }

    @Test
    @DisplayName("micro com várias economias recebe porEconomia x economias (macro 120; micros 20 e 20 em 1 e 3 economias; a ratear 80, 80 DIV 4 = 20, teto 100)")
    void economiesWeightTheShare() {
        List<Micro> micros = List.of(new Micro("A", 20, 1, true), new Micro("B", 20, 3, true));
        Result r = CondominiumApportionment.apportion(new Input(120, micros, 100));
        assertThat(r.toBeApportioned()).isEqualTo(80);
        assertThat(r.byMicro()).containsOnly(entry("A", 20), entry("B", 60));
    }

    @Test
    @DisplayName("entradas inválidas são recusadas")
    void validation() {
        assertThatThrownBy(() -> new Input(10, List.of(), 5)).isInstanceOf(MeteringInputException.class);
        assertThatThrownBy(() -> new Input(-1, fourMeteredMicros(), 5)).isInstanceOf(MeteringInputException.class);
        assertThatThrownBy(() -> new Micro("A", 1, 0, true)).isInstanceOf(MeteringInputException.class);
    }

    @Test
    @DisplayName("invariantes (1.000 casos de semente fixa): nada negativo, soma rateada + perdido = a ratear, nunca passa do teto")
    void invariants() {
        Random random = new Random(20261011L);
        for (int i = 0; i < 1000; i++) {
            List<Micro> micros = new ArrayList<>();
            int count = 1 + random.nextInt(6);
            int sumMicros = 0;
            boolean anyNotMetered = false;
            for (int m = 0; m < count; m++) {
                int consumption = random.nextInt(60);
                boolean metered = random.nextInt(4) != 0;
                anyNotMetered |= !metered;
                micros.add(new Micro("M" + m, consumption, 1 + random.nextInt(3), metered));
                sumMicros += consumption;
            }
            int macro = random.nextInt(sumMicros + 120);
            int cap = random.nextInt(30);
            Result r = CondominiumApportionment.apportion(new Input(macro, micros, cap));

            assertThat(r.toBeApportioned()).isEqualTo(Math.max(0, macro - sumMicros));
            int distributed = r.byMicro().values().stream().mapToInt(Integer::intValue).sum();
            assertThat(r.byMicro().values()).allMatch(v -> v >= 0);
            assertThat(distributed).isLessThanOrEqualTo(r.toBeApportioned());
            if (!anyNotMetered) {
                assertThat(r.perEconomy()).isLessThanOrEqualTo(cap);
            }
            if (distributed < r.toBeApportioned()) {
                assertThat(r.warnings()).as("perda sem aviso no caso %d", i).isNotEmpty();
            } else {
                assertThat(r.warnings()).as("aviso sem perda no caso %d", i).isEmpty();
            }
        }
    }

    private static java.util.Map.Entry<String, Integer> entry(String key, int value) {
        return java.util.Map.entry(key, value);
    }
}
