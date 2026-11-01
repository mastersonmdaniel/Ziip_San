package br.com.ziipsan.commercial.billing.bill;

import br.com.ziipsan.commercial.billing.tariff.CategoryCharge;
import br.com.ziipsan.commercial.billing.tariff.ChargeLine;
import br.com.ziipsan.commercial.billing.tariff.TariffGroup;
import br.com.ziipsan.commercial.billing.tariff.SewerFactorPolicy;
import br.com.ziipsan.commercial.billing.tariff.SewerParameters;
import br.com.ziipsan.commercial.billing.tariff.SliceCharge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cálculo da conta de ponta a ponta (água + esgoto + débitos − créditos − impostos), com a tarifa
 * FICTÍCIA T1. Valores recalculados à mão (passo a passo nos comentários).
 */
class BillCalculatorTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 5);
    private static final LocalDate TO = LocalDate.of(2026, 10, 4);

    private static SewerParameters sewer80() {
        return new SewerParameters(d("80"), d("100"), null, SewerFactorPolicy.EXACT, false);
    }

    private static BillInput input(List<TariffGroup> groups, Integer water, BillInput.SewerBilling sewer,
                                   List<DebitReceivable> debits, List<CreditReceivable> credits, TaxWithholdingPolicy taxes) {
        return new BillInput(t1Schedule(), FROM, TO, groups, water, sewer, debits, credits, taxes);
    }

    private static DebitReceivable debit(int n, String total, int installments, int done) {
        return new DebitReceivable(id(n), "TESTE", d(total), installments, done);
    }

    private static CreditReceivable credit(int n, String total, int installments, int done, String residual) {
        return new CreditReceivable(id(n), "TESTE", d(total), installments, done, d(residual));
    }

    private static TaxWithholdingPolicy illustrativeTaxes() {
        return new TaxWithholdingPolicy(List.of(new TaxRate("T-A", d("1.20")), new TaxRate("T-B", d("1.00")),
                new TaxRate("T-C", d("3.00")), new TaxRate("T-D", d("0.65"))));
    }

    @Test
    @DisplayName("conta completa: água 112,00 + esgoto 89,60 + débito 33,33 (1/3 de 100,00) − crédito 5,83 (1/6 de 35,00) = 229,10")
    void fullBill() {
        // água 27 m³ RES, 1 eco = 112,00 ; esgoto 80% = 89,60 ; 112,00 + 89,60 = 201,60
        // débito 100,00 em 3 → 1ª parcela 33,33 ; crédito 35,00 em 6 → 1ª parcela 5,83
        // 201,60 + 33,33 − 5,83 = 229,10
        BillResult r = BillCalculator.calculate(input(List.of(res(1)), 27,
                new BillInput.SewerBilling(27, sewer80()),
                List.of(debit(1, "100.00", 3, 0)), List.of(credit(2, "35.00", 6, 0, "0.00")), null));

        assertThat(r.waterValue()).isEqualByComparingTo("112.00");
        assertThat(r.sewerValue()).isEqualByComparingTo("89.60");
        assertThat(r.debitsValue()).isEqualByComparingTo("33.33");
        assertThat(r.creditsValue()).isEqualByComparingTo("5.83");
        assertThat(r.taxesValue()).isEqualByComparingTo("0.00");
        assertThat(r.total()).isEqualByComparingTo("229.10");
        assertThat(r.debitLines().get(0).installmentNo()).isEqualTo(1);
        assertThat(r.debitLines().get(0).installmentTotal()).isEqualTo(3);
        assertThat(r.creditLines().get(0).installmentsDoneAfter()).isEqualTo(1);
    }

    @Test
    @DisplayName("crédito nunca excede a conta: água 30,00 (mínima) com crédito de 150,00 → aplica 30,00, resíduo 120,00, total 0,00")
    void creditNeverExceedsTheBill() {
        BillResult r = BillCalculator.calculate(input(List.of(res(1)), 8, null,
                List.of(), List.of(credit(1, "150.00", 1, 0, "0.00")), null));

        assertThat(r.waterValue()).isEqualByComparingTo("30.00");
        assertThat(r.creditsValue()).isEqualByComparingTo("30.00");
        assertThat(r.creditLines().get(0).residualAfter()).isEqualByComparingTo("120.00");
        assertThat(r.total()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("I-01 pela conta: débito de 1.000,00 sem água nem esgoto, com 4 tributos: impostos 58,50, total 941,50")
    void taxesOverTheBill() {
        BillResult r = BillCalculator.calculate(input(List.of(res(1)), null, null,
                List.of(debit(1, "1000.00", 1, 0)), List.of(), illustrativeTaxes()));

        assertThat(r.taxBase()).isEqualByComparingTo("1000.00");
        assertThat(r.taxesValue()).isEqualByComparingTo("58.50");
        assertThat(r.total()).isEqualByComparingTo("941.50");
        assertThat(r.waterValue()).isEqualByComparingTo("0.00");
        assertThat(r.water()).isNull();
    }

    @Test
    @DisplayName("I-03: sem política de retenção (cliente sem retenção) os impostos são 0,00 e o total é 100% da base")
    void noWithholdingPolicy() {
        BillResult r = BillCalculator.calculate(input(List.of(res(1)), null, null,
                List.of(debit(1, "1000.00", 1, 0)), List.of(), null));

        assertThat(r.taxLines()).isEmpty();
        assertThat(r.taxesValue()).isEqualByComparingTo("0.00");
        assertThat(r.total()).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("imposto incide sobre água + esgoto + débitos − créditos: (112,00 + 89,60) − 10,00 = 191,60; T-A 2% + T-B 3%: 3,83 + 5,75 = 9,58; total 182,02")
    void taxBaseIsNetOfCredits() {
        // 191,60 × 2% = 3,832 → 3,83; total 191,60 × 5% = 9,58 (exato); último 9,58 − 3,83 = 5,75 (direto: 5,748 → 5,75)
        // total da conta = 201,60 − 10,00 − 9,58 = 182,02
        TaxWithholdingPolicy policy = new TaxWithholdingPolicy(List.of(new TaxRate("T-A", d("2.00")), new TaxRate("T-B", d("3.00"))));

        BillResult r = BillCalculator.calculate(input(List.of(res(1)), 27, new BillInput.SewerBilling(27, sewer80()),
                List.of(), List.of(credit(1, "10.00", 1, 0, "0.00")), policy));

        assertThat(r.taxBase()).isEqualByComparingTo("191.60");
        assertThat(r.taxLines().get(0).amount()).isEqualByComparingTo("3.83");
        assertThat(r.taxLines().get(1).amount()).isEqualByComparingTo("5.75");
        assertThat(r.taxesValue()).isEqualByComparingTo("9.58");
        assertThat(r.total()).isEqualByComparingTo("182.02");
    }

    @Test
    @DisplayName("só esgoto (poço, sem água): a água não é cobrada e o esgoto parte do volume do poço: 14 m³ → 46,00")
    void sewerOnly() {
        BillResult r = BillCalculator.calculate(input(List.of(res(1)), null,
                new BillInput.SewerBilling(14, new SewerParameters(d("100"), d("100"), null, SewerFactorPolicy.EXACT, false)),
                List.of(), List.of(), null));

        assertThat(r.waterValue()).isEqualByComparingTo("0.00");
        assertThat(r.sewerValue()).isEqualByComparingTo("46.00");
        assertThat(r.total()).isEqualByComparingTo("46.00");
    }

    @Test
    @DisplayName("nada a cobrar: total 0,00")
    void emptyBill() {
        BillResult r = BillCalculator.calculate(input(null, null, null, List.of(), List.of(), null));

        assertThat(r.total()).isEqualByComparingTo("0.00");
        assertThat(r.charges()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("débito sem parcela a cobrar é erro do chamador, e água sem tarifa/período/economias também")
    void invalidInputs() {
        assertThatThrownBy(() -> BillCalculator.calculate(input(List.of(res(1)), null, null,
                List.of(debit(1, "100.00", 3, 3)), List.of(), null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("parcela a cobrar");
        assertThatThrownBy(() -> new BillInput(null, FROM, TO, List.of(res(1)), 10, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BillInput(t1Schedule(), FROM, TO, List.of(), 10, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("invariantes (10.16) em muitas combinações: total = água + esgoto + débitos − créditos − impostos; crédito ≤ cobrança; Σ categorias = água; Σ faixas = categoria")
    void invariants() {
        TaxWithholdingPolicy taxes = illustrativeTaxes();
        for (int consumption = 0; consumption <= 90; consumption += 3) {
            for (int economies = 1; economies <= 4; economies++) {
                List<TariffGroup> groups = economies == 1 ? List.of(res(1)) : List.of(res(economies - 1), com(1));
                for (String creditTotal : new String[]{"0", "5.00", "250.00"}) {
                    List<CreditReceivable> credits = creditTotal.equals("0")
                            ? List.of() : List.of(credit(1, creditTotal, 3, 0, "0.00"));
                    BillResult r = BillCalculator.calculate(input(groups, consumption,
                            new BillInput.SewerBilling(consumption, sewer80()),
                            List.of(debit(2, "77.77", 4, 1)), credits, taxes));

                    BigDecimal expected = r.waterValue().add(r.sewerValue()).add(r.debitsValue())
                            .subtract(r.creditsValue()).subtract(r.taxesValue());
                    assertThat(r.total()).as("total").isEqualByComparingTo(expected);
                    assertThat(r.creditsValue()).as("crédito ≤ cobrança").isLessThanOrEqualTo(r.charges());
                    assertThat(r.total().signum()).isGreaterThanOrEqualTo(0);

                    BigDecimal categories = r.water().slices().stream().flatMap(s -> s.categories().stream())
                            .map(CategoryCharge::net).reduce(BigDecimal.ZERO, BigDecimal::add);
                    assertThat(categories).as("Σ categorias = água").isEqualByComparingTo(r.waterValue());
                    for (SliceCharge s : r.water().slices()) {
                        for (CategoryCharge c : s.categories()) {
                            BigDecimal lines = c.lines().stream().map(ChargeLine::net).reduce(BigDecimal.ZERO, BigDecimal::add);
                            assertThat(lines).as("Σ faixas = categoria").isEqualByComparingTo(c.net());
                        }
                    }
                }
            }
        }
    }
}
