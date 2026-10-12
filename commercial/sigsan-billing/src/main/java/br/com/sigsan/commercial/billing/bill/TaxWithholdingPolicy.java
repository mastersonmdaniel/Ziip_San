package br.com.sigsan.commercial.billing.bill;

import java.math.BigDecimal;
import java.util.List;

/**
 * Tributos que o tenant retém de uma conta, <b>na ordem em que serão calculados</b> (o último absorve
 * o resíduo de arredondamento). Não existe política "padrão": conta sem retenção usa {@code null} no
 * {@link BillInput}; retenção sem alíquotas é erro.
 */
public record TaxWithholdingPolicy(List<TaxRate> rates) {

    public TaxWithholdingPolicy {
        if (rates == null || rates.isEmpty()) {
            throw new IllegalArgumentException("Retenção de impostos pedida, mas o tenant não configurou nenhuma alíquota");
        }
        rates = List.copyOf(rates);
        if (rates.stream().map(TaxRate::ratePct).reduce(BigDecimal.ZERO, BigDecimal::add)
                .compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("A soma das alíquotas retidas excede 100%");
        }
    }

    public BigDecimal totalRatePct() {
        return rates.stream().map(TaxRate::ratePct).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
