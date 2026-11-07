package br.com.ziipsan.commercial.collection.ampliada;

import br.com.ziipsan.commercial.billing.bill.LateFeeCalculator;
import br.com.ziipsan.commercial.billing.bill.LateFeePolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ED-26 - guia/extrato com validade, recalculado pela data real do pagamento.
 * Caso a mao: principal 200,00, validade 10/03/2026, politica FLAT multa 2%,
 * juros 1% a cada 30 dias, sem teto e sem carencia (a mesma politica mensal de
 * referencia do {@code LateFeeCalculatorTest} da P2.5).
 * Pago em 25/03: 15 dias; multa 4,00; juros 200 x 1 x 15 / 3000 = 1,00; total 205,00.
 *
 * <p>Pos-revisao (M1): os casos originais chamam a regra delegada ao billing
 * com essa politica explicita; as assercoes numericas sao as MESMAS da entrega
 * aprovada. Calendario dos testes: dias uteis = segunda a sexta (sem feriados),
 * o mesmo do teste do billing.
 */
class GuideValidityRuleTest {

    private static final LocalDate VALIDADE = LocalDate.of(2026, 3, 10);
    private static final Predicate<LocalDate> WEEKDAYS =
            day -> day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY;
    private static final BigDecimal NO_FINES = new BigDecimal("0.00");

    /** Multa 2% unica (FLAT); juros 1% a cada 30 dias, sem teto; sem carencia. */
    private static LateFeePolicy monthlyPolicy() {
        return new LateFeePolicy(LateFeePolicy.FineMode.FLAT, new BigDecimal("2"), null,
                new BigDecimal("1"), 30, null, 0);
    }

    private static GuideValidityRule.RecalculatedAmount due(String principal, LocalDate paid,
                                                            LateFeePolicy policy) {
        return GuideValidityRule.amountDueOn(new BigDecimal(principal), VALIDADE, paid,
                policy, WEEKDAYS, false, NO_FINES, false);
    }

    @Test
    void pagoDepoisDaValidadeRecalculaMultaEJurosPelaDataReal() {
        var r = due("200.00", LocalDate.of(2026, 3, 25), monthlyPolicy());
        assertThat(r.daysLate()).isEqualTo(15);
        assertThat(r.fine()).isEqualByComparingTo("4.00");
        assertThat(r.interest()).isEqualByComparingTo("1.00");
        assertThat(r.totalDue()).isEqualByComparingTo("205.00");
    }

    @Test
    void pagoNaValidadeOuAntesNaoTemAcrescimo() {
        var naValidade = due("200.00", VALIDADE, monthlyPolicy());
        assertThat(naValidade.totalDue()).isEqualByComparingTo("200.00");
        var antes = due("200.00", LocalDate.of(2026, 3, 1), monthlyPolicy());
        assertThat(antes.totalDue()).isEqualByComparingTo("200.00");
        assertThat(antes.daysLate()).isZero();
    }

    @Test
    void segundoCasoCalculadoAMao() {
        // 90,00, 10 dias: multa 1,80; juros 90 x 1 x 10 / 3000 = 0,30; total 92,10.
        var r = due("90.00", LocalDate.of(2026, 3, 20), monthlyPolicy());
        assertThat(r.fine()).isEqualByComparingTo("1.80");
        assertThat(r.interest()).isEqualByComparingTo("0.30");
        assertThat(r.totalDue()).isEqualByComparingTo("92.10");
    }

    @Test
    void guiaAvulsaValidaExigenciasMinimas() {
        var guia = new GuideValidityRule.AvulsoGuide("Maria Exemplo", "Taxa de ligacao avulsa",
                new BigDecimal("75.00"), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
        assertThat(guia.amount()).isEqualByComparingTo("75.00");
        assertThatThrownBy(() -> new GuideValidityRule.AvulsoGuide(" ", "x",
                new BigDecimal("75.00"), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GuideValidityRule.AvulsoGuide("Maria", "x",
                new BigDecimal("75.00"), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 3, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Novos pos-revisao (M1): paridade guia x conta e politica completa ---

    @Test
    void paridadeComOBillingNoCasoFlat() {
        // A guia delega ao LateFeeCalculator: multa/juros identicos aos da conta
        // com os mesmos principal, vencimento/validade, datas e politica.
        var guia = due("200.00", LocalDate.of(2026, 3, 25), monthlyPolicy());
        var conta = LateFeeCalculator.calculate(new BigDecimal("200.00"), false, NO_FINES,
                VALIDADE, LocalDate.of(2026, 3, 25), monthlyPolicy(), WEEKDAYS, false);
        assertThat(guia.fine()).isEqualByComparingTo(conta.fine());
        assertThat(guia.interest()).isEqualByComparingTo(conta.interest());
        assertThat(guia.totalDue()).isEqualByComparingTo(new BigDecimal("200.00").add(conta.total()));
    }

    @Test
    void carenciaEmDiasUteisDaPoliticaValeParaAGuia() {
        // Politica com carencia de 3 dias uteis; validade seg 12/10/2026.
        // Pago qui 15/10 (3 uteis) -> sem acrescimo. Pago sex 16/10 (4 uteis) ->
        // multa 4,00; juros 200 x 1 x 4 / 3000 = 0,27; total 204,27.
        var comCarencia = new LateFeePolicy(LateFeePolicy.FineMode.FLAT, new BigDecimal("2"), null,
                new BigDecimal("1"), 30, null, 3);
        LocalDate validadeOut = LocalDate.of(2026, 10, 12);
        var dentro = GuideValidityRule.amountDueOn(new BigDecimal("200.00"), validadeOut,
                LocalDate.of(2026, 10, 15), comCarencia, WEEKDAYS, false, NO_FINES, false);
        assertThat(dentro.fine()).isEqualByComparingTo("0.00");
        assertThat(dentro.interest()).isEqualByComparingTo("0.00");
        assertThat(dentro.totalDue()).isEqualByComparingTo("200.00");
        var fora = GuideValidityRule.amountDueOn(new BigDecimal("200.00"), validadeOut,
                LocalDate.of(2026, 10, 16), comCarencia, WEEKDAYS, false, NO_FINES, false);
        assertThat(fora.fine()).isEqualByComparingTo("4.00");
        assertThat(fora.interest()).isEqualByComparingTo("0.27");
        assertThat(fora.totalDue()).isEqualByComparingTo("204.27");
    }

    @Test
    void multaJaCobradaNaoECobradaDeNovoNaGuia() {
        // Caso base (15 dias) com fineAlreadyCharged: multa 0,00; juros seguem 1,00.
        var r = GuideValidityRule.amountDueOn(new BigDecimal("200.00"), VALIDADE,
                LocalDate.of(2026, 3, 25), monthlyPolicy(), WEEKDAYS, true, NO_FINES, false);
        assertThat(r.fine()).isEqualByComparingTo("0.00");
        assertThat(r.interest()).isEqualByComparingTo("1.00");
        assertThat(r.totalDue()).isEqualByComparingTo("201.00");
    }
}
