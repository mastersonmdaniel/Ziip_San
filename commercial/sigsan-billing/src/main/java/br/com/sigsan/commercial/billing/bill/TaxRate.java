package br.com.sigsan.commercial.billing.bill;

import java.math.BigDecimal;

/**
 * Tributo retido na fonte e sua alíquota. Ambos são <b>parâmetro do tenant</b> (tabela
 * {@code tax_withholding_rates}, com vigência); o código não conhece nenhum tributo nem alíquota.
 *
 * @param code     código livre do tributo, definido pelo tenant (ex.: o que o contador usa)
 * @param ratePct  alíquota em %, em (0, 100]
 */
public record TaxRate(String code, BigDecimal ratePct) {

    public TaxRate {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Tributo sem código");
        }
        if (ratePct == null || ratePct.signum() <= 0 || ratePct.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Alíquota deve estar em (0, 100]: " + ratePct + " (" + code + ")");
        }
    }
}
