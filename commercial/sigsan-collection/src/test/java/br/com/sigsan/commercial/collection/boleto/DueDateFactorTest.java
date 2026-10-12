package br.com.sigsan.commercial.collection.boleto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fator de vencimento com a virada de 22/02/2025.
 *
 * <p>Contas à mão. Ciclo legado: base 07/10/1997; de 07/10/1997 a 07/10/2000 são 1.096 dias (365 +
 * 365 + 366, com o 29/02/2000); de 07/10 a 06/12 são 24 + 30 + 6 = 60 dias; logo 06/12/2000 =
 * 1.156 (exemplo do manual do Santander v36). 15/12/2000 = 1.165 e 30/12/2000 = 1.180 (mesmo
 * manual). O fator 9.999 cai em 07/10/1997 + 9.999 dias = 21/02/2025.
 *
 * <p>Ciclo atual: 22/02/2025 = 1000, 23/02/2025 = 1001, 24/02/2025 = 1002 (manual do Santander
 * v36). O fator 9.999 cai em 22/02/2025 + 8.999 dias = 13/10/2049. De 22/02/2025 a 22/02/2026 são 365
 * dias, a 22/02/2027 são 730 e a 22/02/2028 são 1.095 (2028 é bissexto, mas o 29/02 só entra depois
 * de 22/02/2028).
 */
class DueDateFactorTest {

    @Test
    @DisplayName("exemplos do manual do Santander no ciclo legado: 06/12/2000=1156, 15/12/2000=1165, 30/12/2000=1180")
    void legacyCycleManualExamples() {
        assertThat(DueDateFactor.encode(LocalDate.of(2000, 12, 6))).isEqualTo(1156);
        assertThat(DueDateFactor.encode(LocalDate.of(2000, 12, 15))).isEqualTo(1165);
        assertThat(DueDateFactor.encode(LocalDate.of(2000, 12, 30))).isEqualTo(1180);
    }

    @Test
    @DisplayName("exemplos do manual do Santander na nova data-base: 22/02/2025=1000, 23=1001, 24=1002")
    void newBaseManualExamples() {
        assertThat(DueDateFactor.encode(LocalDate.of(2025, 2, 22))).isEqualTo(1000);
        assertThat(DueDateFactor.encode(LocalDate.of(2025, 2, 23))).isEqualTo(1001);
        assertThat(DueDateFactor.encode(LocalDate.of(2025, 2, 24))).isEqualTo(1002);
    }

    @Test
    @DisplayName("a virada: 21/02/2025 é o último 9999 do ciclo legado e 22/02/2025 volta a 1000")
    void theWrapAround() {
        assertThat(DueDateFactor.LEGACY_LAST_DATE).isEqualTo(LocalDate.of(2025, 2, 21));
        assertThat(DueDateFactor.encode(LocalDate.of(2025, 2, 20))).isEqualTo(9998);
        assertThat(DueDateFactor.encode(LocalDate.of(2025, 2, 21))).isEqualTo(9999);
        assertThat(DueDateFactor.encode(LocalDate.of(2025, 2, 22))).isEqualTo(1000);
    }

    @Test
    @DisplayName("datas intermediárias do ciclo atual, calculadas à mão")
    void currentCycleSamples() {
        assertThat(DueDateFactor.encode(LocalDate.of(2026, 2, 22))).isEqualTo(1365);  // +365
        assertThat(DueDateFactor.encode(LocalDate.of(2027, 2, 22))).isEqualTo(1730);  // +730
        assertThat(DueDateFactor.encode(LocalDate.of(2028, 2, 22))).isEqualTo(2095);  // +1095
        assertThat(DueDateFactor.encode(LocalDate.of(2026, 10, 12))).isEqualTo(1597); // +597
        assertThat(DueDateFactor.encode(LocalDate.of(2026, 12, 21))).isEqualTo(1667); // +667
        assertThat(DueDateFactor.encode(LocalDate.of(2030, 8, 15))).isEqualTo(3000);  // +2000
    }

    @Test
    @DisplayName("o último dia do ciclo atual é 13/10/2049 (9999); depois disso não há regra")
    void endOfCurrentCycle() {
        assertThat(DueDateFactor.CURRENT_LAST_DATE).isEqualTo(LocalDate.of(2049, 10, 13));
        assertThat(DueDateFactor.encode(LocalDate.of(2049, 10, 13))).isEqualTo(9999);
        assertThatThrownBy(() -> DueDateFactor.encode(LocalDate.of(2049, 10, 14)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ciclo seguinte");
    }

    @Test
    @DisplayName("o ciclo legado começa em 08/10/1997 (fator 1); a própria base não é representável")
    void startOfLegacyCycle() {
        assertThat(DueDateFactor.encode(LocalDate.of(1997, 10, 8))).isEqualTo(1);
        assertThat(DueDateFactor.encode(LocalDate.of(2000, 7, 3))).isEqualTo(1000);
        assertThatThrownBy(() -> DueDateFactor.encode(LocalDate.of(1997, 10, 7)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DueDateFactor.encode(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("os fatores 1000 a 9999 têm duas datas candidatas, 9.000 dias uma da outra")
    void twoCandidates() {
        assertThat(DueDateFactor.candidates(1000))
                .containsExactly(LocalDate.of(2000, 7, 3), LocalDate.of(2025, 2, 22));
        assertThat(ChronoUnit.DAYS.between(LocalDate.of(2000, 7, 3), LocalDate.of(2025, 2, 22))).isEqualTo(9000);
        assertThat(DueDateFactor.candidates(9999))
                .containsExactly(LocalDate.of(2025, 2, 21), LocalDate.of(2049, 10, 13));
        assertThat(DueDateFactor.candidates(999)).hasSize(1);
        assertThat(DueDateFactor.candidates(0)).isEmpty();
        assertThatThrownBy(() -> DueDateFactor.candidates(10000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DueDateFactor.candidates(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("decodifica pela janela em volta da referência: fator 1000 em 2026 é 22/02/2025, não 03/07/2000")
    void decodePicksTheCandidateInsideTheWindow() {
        LocalDate reference = LocalDate.of(2026, 10, 12);
        assertThat(DueDateFactor.decode(1000, reference, 800, 800)).contains(LocalDate.of(2025, 2, 22));
        assertThat(DueDateFactor.decode(1667, reference, 90, 400)).contains(LocalDate.of(2026, 12, 21));
    }

    @Test
    @DisplayName("o mesmo fator, com referência antiga, decodifica no ciclo legado")
    void decodeInLegacyCycle() {
        assertThat(DueDateFactor.decode(1156, LocalDate.of(2000, 12, 1), 30, 30)).contains(LocalDate.of(2000, 12, 6));
        assertThat(DueDateFactor.decode(1000, LocalDate.of(2000, 7, 1), 30, 30)).contains(LocalDate.of(2000, 7, 3));
    }

    @Test
    @DisplayName("boleto vencido em 21/02/2025 (9999) pago em março de 2025 volta a 21/02/2025")
    void decodeOverdueAcrossTheWrap() {
        assertThat(DueDateFactor.decode(9999, LocalDate.of(2025, 3, 5), 365, 400)).contains(LocalDate.of(2025, 2, 21));
        assertThat(DueDateFactor.decode(9999, LocalDate.of(2049, 10, 1), 365, 400)).contains(LocalDate.of(2049, 10, 13));
    }

    @Test
    @DisplayName("fator 0000 é sem vencimento")
    void zeroFactorMeansNoDueDate() {
        assertThat(DueDateFactor.decode(0, LocalDate.of(2026, 10, 12), 10, 10)).isEmpty();
    }

    @Test
    @DisplayName("fora da janela e janela larga demais (ambígua) são recusados; janela é obrigatória")
    void decodeFailures() {
        LocalDate reference = LocalDate.of(2026, 10, 12);
        // 1000 em 2026: candidatas 2000 e 2025; janela de 10 dias não pega nenhuma
        assertThatThrownBy(() -> DueDateFactor.decode(1000, reference, 10, 10))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("não corresponde");
        // referência no meio das duas candidatas (2012) com janela de 5.000 dias para cada lado
        assertThatThrownBy(() -> DueDateFactor.decode(1000, LocalDate.of(2012, 10, 28), 5000, 5000))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ambíguo");
        assertThatThrownBy(() -> DueDateFactor.decode(1000, null, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DueDateFactor.decode(1000, reference, -1, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ida e volta: encode e depois decode com janela estreita devolve a data")
    void roundTripAcrossManyDates() {
        List<LocalDate> dates = List.of(LocalDate.of(2000, 12, 6), LocalDate.of(2025, 2, 21), LocalDate.of(2025, 2, 22),
                LocalDate.of(2026, 10, 12), LocalDate.of(2030, 8, 15), LocalDate.of(2049, 10, 13));
        for (LocalDate d : dates) {
            assertThat(DueDateFactor.decode(DueDateFactor.encode(d), d.minusDays(15), 60, 60)).contains(d);
        }
    }
}
