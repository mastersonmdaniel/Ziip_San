package br.com.ziipsan.commercial.billing.bill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.d;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Casos C-* (seção 10.10): o crédito nunca excede a conta e o resíduo passa ao mês seguinte.
 * Todos os valores são fictícios.
 */
class CreditApplierTest {

    private static CreditReceivable credit(int n, String total, int installments, int done, String residual) {
        return new CreditReceivable(id(n), "TESTE", d(total), installments, done, d(residual));
    }

    /** Aplica o resultado de um mês ao crédito, como a persistência faria. */
    private static CreditReceivable after(CreditReceivable before, CreditLine line) {
        return new CreditReceivable(before.id(), before.origin(), before.total(), before.installments(),
                line.installmentsDoneAfter(), line.residualAfter());
    }

    @Test
    @DisplayName("C-01: crédito de R$ 35,00 em 6 parcelas, mês a mês: 5,83 ×5 e 5,85; soma 35,00")
    void c01() {
        CreditReceivable credit = credit(1, "35.00", 6, 0, "0.00");
        List<BigDecimal> applied = new ArrayList<>();
        for (int month = 0; month < 6; month++) {
            CreditApplier.Application a = CreditApplier.apply(d("1000.00"), List.of(credit));
            applied.add(a.totalApplied());
            credit = after(credit, a.lines().get(0));
        }

        assertThat(applied).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(d("5.83"), d("5.83"), d("5.83"), d("5.83"), d("5.83"), d("5.85"));
        assertThat(applied.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("35.00");
        assertThat(credit.installmentsDone()).isEqualTo(6);
        assertThat(credit.residual()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("C-02: conta 100,00 (água 50 + esgoto 40 + débitos 10), crédito 150,00 em 1 parcela: aplica 100,00, resíduo 50,00, total 0,00")
    void c02() {
        CreditApplier.Application a = CreditApplier.apply(d("100.00"), List.of(credit(1, "150.00", 1, 0, "0.00")));

        assertThat(a.totalApplied()).isEqualByComparingTo("100.00");
        assertThat(a.lines()).hasSize(1);
        CreditLine line = a.lines().get(0);
        assertThat(line.applied()).isEqualByComparingTo("100.00");
        assertThat(line.residualAfter()).isEqualByComparingTo("50.00");
        assertThat(line.installmentNo()).isEqualTo(1);
        assertThat(line.installmentsDoneAfter()).isEqualTo(1);
        // total da conta = 100,00 − 100,00 = 0,00
        assertThat(d("100.00").subtract(a.totalApplied())).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("C-03: mês seguinte, o crédito de C-02 (1/1 já cumprida) com resíduo 50,00 e conta de 80,00: aplica 50,00, resíduo 0,00, total 30,00")
    void c03() {
        CreditApplier.Application a = CreditApplier.apply(d("80.00"), List.of(credit(1, "150.00", 1, 1, "50.00")));

        CreditLine line = a.lines().get(0);
        assertThat(line.applied()).isEqualByComparingTo("50.00");
        assertThat(line.residualAfter()).isEqualByComparingTo("0.00");
        assertThat(line.installmentNo()).as("só resíduo, nenhuma parcela nova").isNull();
        assertThat(line.installmentsDoneAfter()).isEqualTo(1);
        assertThat(d("80.00").subtract(a.totalApplied())).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("C-04: dois créditos de 60,00 numa conta de 100,00: A aplica 60,00; B aplica 40,00 e fica com resíduo 20,00")
    void c04() {
        CreditApplier.Application a = CreditApplier.apply(d("100.00"),
                List.of(credit(1, "60.00", 1, 0, "0.00"), credit(2, "60.00", 1, 0, "0.00")));

        assertThat(a.lines()).hasSize(2);
        assertThat(a.lines().get(0).applied()).isEqualByComparingTo("60.00");
        assertThat(a.lines().get(0).residualAfter()).isEqualByComparingTo("0.00");
        assertThat(a.lines().get(1).applied()).isEqualByComparingTo("40.00");
        assertThat(a.lines().get(1).residualAfter()).isEqualByComparingTo("20.00");
        assertThat(a.totalApplied()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("crédito que a conta não alcança não avança: nem parcela nem resíduo mudam")
    void unreachedCreditDoesNotAdvance() {
        CreditApplier.Application a = CreditApplier.apply(d("60.00"),
                List.of(credit(1, "60.00", 1, 0, "0.00"), credit(2, "60.00", 3, 0, "0.00")));

        assertThat(a.lines()).hasSize(1);
        assertThat(a.lines().get(0).receivableId()).isEqualTo(id(1));
    }

    @Test
    @DisplayName("conta de valor zero não realiza crédito algum")
    void zeroBillAppliesNothing() {
        CreditApplier.Application a = CreditApplier.apply(d("0.00"), List.of(credit(1, "60.00", 3, 0, "0.00")));

        assertThat(a.lines()).isEmpty();
        assertThat(a.totalApplied()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("parcela do mês + resíduo do anterior: 100,00 em 4 (25,00); conta de 10,00 deixa resíduo 15,00; mês seguinte realiza 25,00 + 15,00 = 40,00")
    void residualAddsToNextInstallment() {
        CreditReceivable credit = credit(1, "100.00", 4, 0, "0.00");

        CreditApplier.Application first = CreditApplier.apply(d("10.00"), List.of(credit));
        assertThat(first.lines().get(0).applied()).isEqualByComparingTo("10.00");
        assertThat(first.lines().get(0).residualAfter()).isEqualByComparingTo("15.00");
        credit = after(credit, first.lines().get(0));

        CreditApplier.Application second = CreditApplier.apply(d("100.00"), List.of(credit));
        assertThat(second.lines().get(0).applied()).isEqualByComparingTo("40.00");
        assertThat(second.lines().get(0).residualAfter()).isEqualByComparingTo("0.00");
        assertThat(second.lines().get(0).installmentNo()).isEqualTo(2);
    }

    @Test
    @DisplayName("crédito totalmente realizado (todas as parcelas, sem resíduo) é ignorado")
    void fullyRealizedCreditIsIgnored() {
        CreditApplier.Application a = CreditApplier.apply(d("100.00"), List.of(credit(1, "60.00", 2, 2, "0.00")));

        assertThat(a.lines()).isEmpty();
    }

    @Test
    @DisplayName("propriedade (10.16): em qualquer sequência de contas, o crédito nunca excede a conta e a soma realizada é exatamente o valor do crédito")
    void creditNeverExceedsBillAndSumsToTotal() {
        Random random = new Random(2026);
        for (int run = 0; run < 300; run++) {
            BigDecimal total = BigDecimal.valueOf(100 + random.nextInt(20000), 2);
            int installments = 1 + random.nextInt(12);
            if (total.compareTo(BigDecimal.valueOf(installments).movePointLeft(2)) < 0) {
                continue;
            }
            CreditReceivable credit = new CreditReceivable(id(1), "TESTE", total, installments, 0, d("0.00"));
            BigDecimal realized = BigDecimal.ZERO;
            int months = 0;
            while ((credit.installmentsDone() < credit.installments() || credit.residual().signum() > 0) && months < 2000) {
                BigDecimal bill = BigDecimal.valueOf(random.nextInt(8000), 2);
                CreditApplier.Application a = CreditApplier.apply(bill, List.of(credit));
                assertThat(a.totalApplied()).as("crédito ≤ conta").isLessThanOrEqualTo(bill);
                for (CreditLine line : a.lines()) {
                    assertThat(line.residualAfter().signum()).isGreaterThanOrEqualTo(0);
                    credit = after(credit, line);
                }
                realized = realized.add(a.totalApplied());
                months++;
            }
            assertThat(months).as("terminou").isLessThan(2000);
            assertThat(realized).as("soma realizada = valor do crédito (%s em %d)", total, installments).isEqualByComparingTo(total);
        }
    }

    @Test
    @DisplayName("entradas inválidas: conta negativa, resíduo negativo, parcelas inconsistentes")
    void invalidInputs() {
        assertThatThrownBy(() -> CreditApplier.apply(d("-1.00"), List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> credit(1, "10.00", 1, 0, "-1.00")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> credit(1, "10.00", 2, 3, "0.00")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> credit(1, "0.00", 1, 0, "0.00")).isInstanceOf(IllegalArgumentException.class);
    }
}
