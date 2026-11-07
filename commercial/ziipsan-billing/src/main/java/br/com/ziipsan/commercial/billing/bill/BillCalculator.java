package br.com.ziipsan.commercial.billing.bill;

import br.com.ziipsan.commercial.billing.math.Money;
import br.com.ziipsan.commercial.billing.tariff.SewerCharge;
import br.com.ziipsan.commercial.billing.tariff.TariffCharge;
import br.com.ziipsan.commercial.billing.tariff.TariffEngine;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Calcula os valores de uma conta (domínio puro). Ordem, como no levantamento 02 (5.4 e 4.6):
 * <ol>
 *   <li>água ({@link TariffEngine#calculate}) e esgoto ({@link TariffEngine#calculateSewer});</li>
 *   <li>parcela fixa avulsa (TFDI): já resolvida pelo chamador em {@link BillInput#fixedPartValue()}
 *       (regra {@code FixedPartCharge}); entra como componente próprio, {@code ZERO} quando não há;</li>
 *   <li>débitos: a parcela do mês de cada débito ({@link Installments}, DOWN; centavos na última);</li>
 *   <li>créditos: abatidos até o valor da conta, resíduo ao mês seguinte ({@link CreditApplier});</li>
 *   <li>impostos retidos, se o tenant retém, sobre
 *       {@code água + esgoto + parcela fixa + débitos − créditos}
 *       ({@link TaxWithholding}; o último absorve o resíduo);</li>
 *   <li>total = água + esgoto + parcela fixa + débitos − créditos − impostos
 *       (a fórmula do CHECK {@code chk_bills_total_with_withholding_tax}).</li>
 * </ol>
 * Não decide <i>se</i> a conta é gerada ({@link BillGenerationRule}), nem o vencimento
 * ({@link DueDateCalculator}), nem persiste nada.
 */
public final class BillCalculator {

    private BillCalculator() {
    }

    public static BillResult calculate(BillInput in) {
        TariffCharge water = null;
        SewerCharge sewer = null;
        BigDecimal waterValue = Money.ZERO;
        BigDecimal sewerValue = Money.ZERO;
        if (in.waterConsumption() != null) {
            water = TariffEngine.calculate(in.schedule(), in.periodFrom(), in.periodTo(), in.groups(), in.waterConsumption());
            waterValue = water.net();
        }
        if (in.sewer() != null) {
            sewer = TariffEngine.calculateSewer(in.schedule(), in.periodFrom(), in.periodTo(), in.groups(),
                    in.sewer().baseVolume(), in.sewer().parameters());
            sewerValue = sewer.net();
        }

        List<DebitLine> debitLines = new ArrayList<>();
        BigDecimal debitsValue = Money.ZERO;
        for (DebitReceivable debit : in.debits()) {
            if (!debit.hasInstallmentToCharge()) {
                throw new IllegalArgumentException("Débito " + debit.id() + " não tem parcela a cobrar; "
                        + "o chamador deve selecionar só débitos com parcelas pendentes");
            }
            BigDecimal amount = Installments.next(debit.total(), debit.installments(), debit.installmentsDone());
            debitLines.add(new DebitLine(debit.id(), debit.debitTypeId(), debit.debitTypeCode(),
                    debit.installmentsDone() + 1, debit.installments(), amount));
            debitsValue = debitsValue.add(amount);
        }

        BigDecimal charges = waterValue.add(sewerValue).add(in.fixedPartValue()).add(debitsValue);
        CreditApplier.Application credits = CreditApplier.apply(charges, in.credits());
        BigDecimal creditsValue = credits.totalApplied();

        BigDecimal taxBase = charges.subtract(creditsValue);
        List<TaxLine> taxLines = new ArrayList<>();
        BigDecimal taxesValue = Money.ZERO;
        if (in.taxPolicy() != null) {
            taxLines.addAll(TaxWithholding.withhold(taxBase, in.taxPolicy()));
            for (TaxLine t : taxLines) {
                taxesValue = taxesValue.add(t.amount());
            }
            taxBase = taxLines.get(0).base();
        } else {
            taxBase = Money.ZERO;
        }

        BigDecimal total = charges.subtract(creditsValue).subtract(taxesValue);
        return new BillResult(water, sewer, waterValue, sewerValue, in.fixedPartValue(), debitLines, debitsValue,
                credits.lines(), creditsValue, taxBase, taxLines, taxesValue, total);
    }
}
