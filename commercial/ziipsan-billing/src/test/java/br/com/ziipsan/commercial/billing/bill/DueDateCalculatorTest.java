package br.com.ziipsan.commercial.billing.bill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Casos V-* (seção 10.15). Datas e parâmetros fictícios. A regra reproduz a do legado, inclusive o
 * ponto que o próprio levantamento marca como "a decidir" (V-03); ver o README.
 */
class DueDateCalculatorTest {

    private static LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    @Test
    @DisplayName("V-01: rota vence 15/10, imóvel dia 20, mês seguinte NÃO: 15 ≤ 20 → 20/10/2026")
    void v01() {
        assertThat(DueDateCalculator.dueDate(date(2026, 10, 15), 20, false, date(2026, 10, 2), 5, null))
                .isEqualTo(date(2026, 10, 20));
    }

    @Test
    @DisplayName("V-02: rota 15/10, imóvel dia 10, hoje 02/10, mínimo 5 dias: 15/10 > 02/10 + 5 = 07/10 → 15/10/2026")
    void v02() {
        assertThat(DueDateCalculator.dueDate(date(2026, 10, 15), 10, false, date(2026, 10, 2), 5, null))
                .isEqualTo(date(2026, 10, 15));
    }

    @Test
    @DisplayName("V-03: rota 15/10, imóvel dia 10, hoje 12/10: 15/10 ≤ 12/10 + 5 = 17/10 → vai para o mês seguinte com o DIA DA ROTA: 15/11/2026")
    void v03() {
        // Comportamento do legado, preservado e marcado "a decidir" no próprio levantamento: o dia alternativo (10)
        // é descartado e vale o dia da rota (15) no mês seguinte. A alternativa "esperada" seria 10/11/2026.
        assertThat(DueDateCalculator.dueDate(date(2026, 10, 15), 10, false, date(2026, 10, 12), 5, null))
                .isEqualTo(date(2026, 11, 15));
    }

    @Test
    @DisplayName("V-04: imóvel dia 31, mês seguinte SIM, base 15/11: dezembro tem 31 → 31/12/2026; em fevereiro vale o último dia")
    void v04() {
        assertThat(DueDateCalculator.dueDate(date(2026, 11, 15), 31, true, date(2026, 11, 1), 5, null))
                .isEqualTo(date(2026, 12, 31));
        // fevereiro de 2027 tem 28 dias
        assertThat(DueDateCalculator.dueDate(date(2027, 1, 15), 31, true, date(2027, 1, 1), 5, null))
                .isEqualTo(date(2027, 2, 28));
        // no mesmo mês: dia alternativo 31 em fevereiro de 2026 (28 dias)
        assertThat(DueDateCalculator.dueDate(date(2026, 2, 10), 31, false, date(2026, 2, 1), 5, null))
                .isEqualTo(date(2026, 2, 28));
    }

    @Test
    @DisplayName("V-05: entrega por correio, sem dia alternativo: vencimento da rota + 3 dias; com dia alternativo os dias de correio não entram")
    void v05() {
        assertThat(DueDateCalculator.dueDate(date(2026, 10, 15), null, false, date(2026, 10, 2), 5, 3))
                .isEqualTo(date(2026, 10, 18));
        assertThat(DueDateCalculator.dueDate(date(2026, 10, 15), 20, false, date(2026, 10, 2), 5, 3))
                .isEqualTo(date(2026, 10, 20));
        assertThat(DueDateCalculator.dueDate(date(2026, 10, 15), null, false, date(2026, 10, 2), 5, null))
                .isEqualTo(date(2026, 10, 15));
    }

    @Test
    @DisplayName("mês seguinte do dia da rota quando o dia é 31: 31/10 + 1 mês = 30/11 (java.time ajusta ao último dia)")
    void routeDayThirtyOneRollsToLastDay() {
        // rota 31/10, dia alternativo 5 (< 31), hoje 28/10, mínimo 5: 31/10 ≤ 28/10 + 5 = 02/11 → mês seguinte: 30/11
        assertThat(DueDateCalculator.dueDate(date(2026, 10, 31), 5, false, date(2026, 10, 28), 5, null))
                .isEqualTo(date(2026, 11, 30));
    }

    @Test
    @DisplayName("validade: último dia do mês de (vencimento + meses de validade)")
    void validity() {
        assertThat(DueDateCalculator.validUntil(date(2026, 10, 15), 3)).isEqualTo(date(2027, 1, 31));
        assertThat(DueDateCalculator.validUntil(date(2026, 10, 15), 4)).isEqualTo(date(2027, 2, 28));
        assertThat(DueDateCalculator.validUntil(date(2026, 10, 15), 0)).isEqualTo(date(2026, 10, 31));
        assertThatThrownBy(() -> DueDateCalculator.validUntil(date(2026, 10, 15), -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("dia de vencimento fora de 1..31 é recusado")
    void invalidDay() {
        assertThatThrownBy(() -> DueDateCalculator.dueDate(date(2026, 10, 15), 0, false, date(2026, 10, 2), 5, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DueDateCalculator.dueDate(date(2026, 10, 15), 32, false, date(2026, 10, 2), 5, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
