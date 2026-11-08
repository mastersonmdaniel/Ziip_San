package br.com.ziipsan.commercial.billing.saaspricing;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * Desconto da reversao (ADR-0024, item 3: "percentual de desconto sobre
 * a mensalidade definido na tabela comercial, aplicado a partir da
 * reversao"). O PERCENTUAL nao e fixado por nenhuma fonte: vem
 * obrigatoriamente de {@code saas.reversion_discount_percent}
 * ([A DEFINIR] enquanto nao houver tabela aprovada). A regra so fixa a
 * SEMANTICA do aceite/ADR: antes da competencia da reversao, preco
 * cheio; da reversao em diante, preco com o desconto.
 */
public final class ReversionDiscountRule {
    private ReversionDiscountRule() {}

    public static BigDecimal priceForCompetence(BigDecimal monthlyPrice, SaasPricingParameters params,
                                                YearMonth competence, YearMonth reversionCompetence) {
        if (monthlyPrice == null || competence == null) throw new IllegalArgumentException("preco e competencia sao obrigatorios");
        if (reversionCompetence == null || competence.isBefore(reversionCompetence)) return PriceCalculator.money(monthlyPrice);
        if (params == null) throw new IllegalArgumentException("parametros comerciais sao obrigatorios");
        BigDecimal pct = params.reversionDiscountPercent();
        if (pct.compareTo(new BigDecimal("100")) > 0)
            throw new IllegalArgumentException("desconto de reversao acima de 100%: " + pct);
        return PriceCalculator.money(monthlyPrice.multiply(BigDecimal.ONE.subtract(pct.movePointLeft(2))));
    }
}
