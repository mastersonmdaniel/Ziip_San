package br.com.ziipsan.commercial.billing.bill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.function.Predicate;

import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.d;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Casos J-* (seção 10.12). Percentuais, limites e carências abaixo são FICTÍCIOS (o produto não tem
 * padrão: são política do tenant).
 *
 * <p>DIVERGÊNCIA conscientemente assumida em relação ao relatório 02: o legado conta juros por MÊS DE
 * CALENDÁRIO (J-01, J-02 e J-04 do relatório). Aqui os juros são pro rata die, então esses casos têm
 * outro resultado, calculado abaixo. O relatório manda decidir ("compatibilidade"); a armadilha 7 da
 * seção 7 diz que não deve ser replicado: 1 dia de atraso não pode custar um mês de juros.
 *
 * <p>Calendário dos testes: dias úteis = segunda a sexta (sem feriados). Em 2026, 10/10 é sábado;
 * 12/10 é segunda. Em 2027, 31/01 é domingo e 01/02 é segunda.
 */
class LateFeeCalculatorTest {

    private static final Predicate<LocalDate> WEEKDAYS =
            day -> day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY;
    private static final BigDecimal NO_FINES = d("0.00");

    /** multa 2% única; juros 1% a cada 30 dias, sem teto; sem carência */
    private static LateFeePolicy monthlyPolicy() {
        return new LateFeePolicy(LateFeePolicy.FineMode.FLAT, d("2"), null, d("1"), 30, null, 0);
    }

    /** multa 0,33% por dia com teto de 2%; juros 0,033% por dia; sem carência */
    private static LateFeePolicy dailyPolicy() {
        return new LateFeePolicy(LateFeePolicy.FineMode.PER_DAY, d("0.33"), d("2"), d("0.033"), 1, null, 0);
    }

    private static LateFeeCalculator.Result calc(String amount, boolean fineAlreadyCharged, LocalDate due, LocalDate paid,
                                                 LateFeePolicy policy, boolean directDebit) {
        return LateFeeCalculator.calculate(d(amount), fineAlreadyCharged, NO_FINES, due, paid, policy, WEEKDAYS, directDebit);
    }

    @Test
    @DisplayName("J-01 (pro rata die): conta 100,00, venc. 10/10, pago 12/11 = 33 dias: multa 2,00; juros 100 × 1% × 33/30 = 1,10; total 3,10 (o legado, por mês de calendário, dava 1,00 de juros)")
    void j01() {
        // 10/10 → 10/11 = 31 dias; + 2 = 33
        LateFeeCalculator.Result r = calc("100.00", false, LocalDate.of(2026, 10, 10), LocalDate.of(2026, 11, 12), monthlyPolicy(), false);

        assertThat(r.fine()).isEqualByComparingTo("2.00");
        assertThat(r.interest()).isEqualByComparingTo("1.10");
        assertThat(r.total()).isEqualByComparingTo("3.10");
    }

    @Test
    @DisplayName("J-02 (pro rata die): pago 20/10, mesmo mês, 10 dias: multa 2,00; juros 100 × 1% × 10/30 = 0,33 (o legado dava 0,00, 'meses = 0')")
    void j02() {
        LateFeeCalculator.Result r = calc("100.00", false, LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 20), monthlyPolicy(), false);

        assertThat(r.fine()).isEqualByComparingTo("2.00");
        assertThat(r.interest()).isEqualByComparingTo("0.33");
        assertThat(r.total()).isEqualByComparingTo("2.33");
    }

    @Test
    @DisplayName("J-03: multa diária 0,33%/dia (teto 2%) e juros 0,033%/dia, 10 dias: multa min(3,30; 2,00) = 2,00; juros 0,33% = 0,33")
    void j03() {
        LateFeeCalculator.Result r = calc("100.00", false, LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 20), dailyPolicy(), false);

        assertThat(r.fine()).isEqualByComparingTo("2.00");
        assertThat(r.interest()).isEqualByComparingTo("0.33");
    }

    @Test
    @DisplayName("J-03 abaixo do teto: 3 dias de atraso → multa 0,33 × 3 = 0,99%, ou seja 0,99; juros 0,099% = 0,10")
    void j03BelowTheCap() {
        // vencimento segunda 12/10; pago quinta 15/10 = 3 dias, 3 dias úteis
        LateFeeCalculator.Result r = calc("100.00", false, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 15), dailyPolicy(), false);

        assertThat(r.fine()).isEqualByComparingTo("0.99");
        assertThat(r.interest()).isEqualByComparingTo("0.10");
    }

    @Test
    @DisplayName("J-04 (armadilha 7): venc. 31/01/2027, pago 01/02/2027 = 1 dia: juros 100 × 1% × 1/30 = 0,03, e não 1,00 (um mês inteiro por 1 dia)")
    void j04() {
        LateFeeCalculator.Result r = calc("100.00", false, LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 1), monthlyPolicy(), false);

        assertThat(r.interest()).isEqualByComparingTo("0.03");
        assertThat(r.fine()).isEqualByComparingTo("2.00");
        assertThat(r.total()).isEqualByComparingTo("2.03");
    }

    @Test
    @DisplayName("J-05: multa já cobrada → multa 0,00; os juros seguem sendo cobrados (33 dias: 1,10)")
    void j05() {
        LateFeeCalculator.Result r = calc("100.00", true, LocalDate.of(2026, 10, 10), LocalDate.of(2026, 11, 12), monthlyPolicy(), false);

        assertThat(r.fine()).isEqualByComparingTo("0.00");
        assertThat(r.interest()).isEqualByComparingTo("1.10");
    }

    @Test
    @DisplayName("J-06: débito automático → nada é calculado")
    void j06() {
        LateFeeCalculator.Result r = calc("100.00", false, LocalDate.of(2026, 10, 10), LocalDate.of(2026, 11, 12), monthlyPolicy(), true);

        assertThat(r.total()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("carência em dias úteis: com 3 dias úteis de carência, vencimento seg 12/10 pago qui 15/10 não cobra; pago sex 16/10 (4 úteis) cobra: multa 2,00, juros 100 × 1% × 4/30 = 0,13")
    void graceBusinessDays() {
        LateFeePolicy policy = new LateFeePolicy(LateFeePolicy.FineMode.FLAT, d("2"), null, d("1"), 30, null, 3);
        LocalDate due = LocalDate.of(2026, 10, 12);

        assertThat(calc("100.00", false, due, LocalDate.of(2026, 10, 15), policy, false).total()).isEqualByComparingTo("0.00");

        LateFeeCalculator.Result r = calc("100.00", false, due, LocalDate.of(2026, 10, 16), policy, false);
        assertThat(r.fine()).isEqualByComparingTo("2.00");
        assertThat(r.interest()).isEqualByComparingTo("0.13");
    }

    @Test
    @DisplayName("pagamento no vencimento, antes dele, ou só em fim de semana sem dia útil: nada é cobrado")
    void noLateDays() {
        LocalDate due = LocalDate.of(2026, 10, 9); // sexta
        assertThat(calc("100.00", false, due, due, monthlyPolicy(), false).total()).isEqualByComparingTo("0.00");
        assertThat(calc("100.00", false, due, due.minusDays(3), monthlyPolicy(), false).total()).isEqualByComparingTo("0.00");
        // vencimento sexta, pago domingo: nenhum dia útil depois do vencimento
        assertThat(calc("100.00", false, due, LocalDate.of(2026, 10, 11), monthlyPolicy(), false).total()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("teto dos juros: 100 dias de atraso a 1%/30 dias seria 3,33%; com teto de 2,00% → 2,00")
    void interestCap() {
        LateFeePolicy capped = new LateFeePolicy(LateFeePolicy.FineMode.FLAT, d("2"), null, d("1"), 30, d("2"), 0);
        LocalDate due = LocalDate.of(2026, 1, 12);
        LocalDate paid = due.plusDays(100);

        assertThat(calc("100.00", false, due, paid, capped, false).interest()).isEqualByComparingTo("2.00");
        assertThat(calc("100.00", false, due, paid, monthlyPolicy(), false).interest()).isEqualByComparingTo("3.33");
    }

    @Test
    @DisplayName("arredondamento HALF_UP numa única divisão: 123,45 em 7 dias: multa 2,469 → 2,47; juros 123,45 × 7/3000 = 0,28805 → 0,29")
    void rounding() {
        LateFeeCalculator.Result r = calc("123.45", false, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 19), monthlyPolicy(), false);

        assertThat(r.fine()).isEqualByComparingTo("2.47");
        assertThat(r.interest()).isEqualByComparingTo("0.29");
        assertThat(r.total()).isEqualByComparingTo("2.76");
    }

    @Test
    @DisplayName("a multa não incide sobre multas que já fazem parte do valor da conta: (100,00 − 10,00) × 2% = 1,80")
    void fineExcludesFinesAlreadyInTheAmount() {
        LateFeeCalculator.Result r = LateFeeCalculator.calculate(d("100.00"), false, d("10.00"),
                LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 19), monthlyPolicy(), WEEKDAYS, false);

        assertThat(r.fine()).isEqualByComparingTo("1.80");
    }

    @Test
    @DisplayName("política sem padrão: percentual ausente, período dos juros zero e teto da multa diária ausente são recusados")
    void policyHasNoDefaults() {
        assertThatThrownBy(() -> new LateFeePolicy(LateFeePolicy.FineMode.FLAT, null, null, d("1"), 30, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LateFeePolicy(LateFeePolicy.FineMode.FLAT, d("2"), null, null, 30, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LateFeePolicy(LateFeePolicy.FineMode.FLAT, d("2"), null, d("1"), 0, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LateFeePolicy(LateFeePolicy.FineMode.PER_DAY, d("0.33"), null, d("1"), 30, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LateFeePolicy(null, d("2"), null, d("1"), 30, null, 0))
                .isInstanceOf(NullPointerException.class);
    }
}
