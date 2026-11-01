package br.com.ziipsan.commercial.billing.bill;

import br.com.ziipsan.commercial.billing.tariff.SewerCharge;
import br.com.ziipsan.commercial.billing.tariff.TariffCharge;

import java.math.BigDecimal;
import java.util.List;

/**
 * Conta calculada. Invariante: {@code total = water + sewer + debits − credits − taxes}, com
 * {@code credits ≤ water + sewer + debits}.
 *
 * @param water    detalhe da tarifa de água; {@code null} se a água não foi faturada
 * @param sewer    detalhe do esgoto; {@code null} se não foi faturado
 * @param taxBase  base da retenção (água + esgoto + débitos − créditos, truncada); zero se não há retenção
 */
public record BillResult(TariffCharge water, SewerCharge sewer,
                         BigDecimal waterValue, BigDecimal sewerValue,
                         List<DebitLine> debitLines, BigDecimal debitsValue,
                         List<CreditLine> creditLines, BigDecimal creditsValue,
                         BigDecimal taxBase, List<TaxLine> taxLines, BigDecimal taxesValue,
                         BigDecimal total) {

    public BillResult {
        debitLines = List.copyOf(debitLines);
        creditLines = List.copyOf(creditLines);
        taxLines = List.copyOf(taxLines);
    }

    /** Água + esgoto + débitos, antes de créditos e impostos. */
    public BigDecimal charges() {
        return waterValue.add(sewerValue).add(debitsValue);
    }
}
