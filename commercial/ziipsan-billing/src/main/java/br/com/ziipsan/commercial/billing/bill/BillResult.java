package br.com.ziipsan.commercial.billing.bill;

import br.com.ziipsan.commercial.billing.math.Money;
import br.com.ziipsan.commercial.billing.tariff.SewerCharge;
import br.com.ziipsan.commercial.billing.tariff.TariffCharge;

import java.math.BigDecimal;
import java.util.List;

/**
 * Conta calculada. Invariante: {@code total = water + sewer + fixedPart + debits − credits − taxes},
 * com {@code credits ≤ water + sewer + fixedPart + debits} -- a mesma fórmula do CHECK
 * {@code chk_bills_total_with_withholding_tax} de {@code bills}.
 *
 * @param water          detalhe da tarifa de água; {@code null} se a água não foi faturada
 * @param sewer          detalhe do esgoto; {@code null} se não foi faturado
 * @param fixedPartValue parcela fixa avulsa (TFDI) cobrada na conta; {@link Money#ZERO} quando
 *                       não há TFDI (o caso de todas as contas sem unidade não conectada)
 * @param taxBase        base da retenção (água + esgoto + parcela fixa + débitos − créditos,
 *                       truncada); zero se não há retenção
 */
public record BillResult(TariffCharge water, SewerCharge sewer,
                         BigDecimal waterValue, BigDecimal sewerValue, BigDecimal fixedPartValue,
                         List<DebitLine> debitLines, BigDecimal debitsValue,
                         List<CreditLine> creditLines, BigDecimal creditsValue,
                         BigDecimal taxBase, List<TaxLine> taxLines, BigDecimal taxesValue,
                         BigDecimal total) {

    public BillResult {
        debitLines = List.copyOf(debitLines);
        creditLines = List.copyOf(creditLines);
        taxLines = List.copyOf(taxLines);
        fixedPartValue = fixedPartValue == null ? Money.ZERO : fixedPartValue;
    }

    /** Água + esgoto + parcela fixa + débitos, antes de créditos e impostos. */
    public BigDecimal charges() {
        return waterValue.add(sewerValue).add(fixedPartValue).add(debitsValue);
    }
}
