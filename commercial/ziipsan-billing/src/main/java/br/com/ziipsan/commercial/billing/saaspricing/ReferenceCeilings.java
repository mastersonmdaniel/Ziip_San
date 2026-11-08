package br.com.ziipsan.commercial.billing.saaspricing;

import java.math.BigDecimal;

/**
 * Tetos de referencia dos editais, UNICOS valores monetarios fixos desta
 * entrega - porque o proprio acceptance da ED-10 os fixa literalmente:
 * VISAN R$ 18.866,65/mes e SAMAE R$ 7.636,74/mes. Servem SO para
 * COMPARACAO da planilha; nunca limitam nem ajustam o preco calculado.
 */
public final class ReferenceCeilings {
    private ReferenceCeilings() {}
    public static final BigDecimal VISAN_MONTHLY = new BigDecimal("18866.65");
    public static final BigDecimal SAMAE_MONTHLY = new BigDecimal("7636.74");

    public enum CeilingStatus { WITHIN, ABOVE }
    public record CeilingComparison(String edital, BigDecimal ceiling, BigDecimal monthlyPrice,
                                    BigDecimal difference, CeilingStatus status) {}

    public static CeilingComparison compare(String edital, BigDecimal ceiling, BigDecimal monthlyPrice) {
        if (ceiling == null || monthlyPrice == null) throw new IllegalArgumentException("teto e preco sao obrigatorios na comparacao");
        BigDecimal diff = PriceCalculator.money(monthlyPrice.subtract(ceiling));
        return new CeilingComparison(edital, ceiling, monthlyPrice, diff,
                monthlyPrice.compareTo(ceiling) <= 0 ? CeilingStatus.WITHIN : CeilingStatus.ABOVE);
    }
    public static CeilingComparison visan(BigDecimal p) { return compare("VISAN", VISAN_MONTHLY, p); }
    public static CeilingComparison samae(BigDecimal p) { return compare("SAMAE", SAMAE_MONTHLY, p); }
}
