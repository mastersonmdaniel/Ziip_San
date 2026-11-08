package br.com.ziipsan.commercial.billing.saaspricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Motor aritmetico da planilha (ED-10): custo mensal de um tenant numa
 * faixa e preco com a margem parametrizada. Aritmetica pura sobre os
 * dados da versao da planilha - nenhuma constante de negocio alem do
 * arredondamento monetario (HALF_UP, 2 casas).
 */
public final class PriceCalculator {

    private PriceCalculator() {}

    public record CostBreakdown(BigDecimal fixedPerTenant, BigDecimal perConnection,
                                BigDecimal sharedPool, BigDecimal labor, BigDecimal total) {}

    public record PricedBand(SizeBand band, long activeConnections, int poolTenants,
                             CostBreakdown cost, BigDecimal marginPercent, BigDecimal monthlyPrice) {}

    /**
     * Custo mensal: FIXED somados; PER_CONNECTION x ligacoes; SHARED_POOL
     * divididos pelos tenants do pool (poolTenants e dado da avaliacao -
     * sem pool declarado, custo compartilhado nao se dilui por presuncao);
     * mais o homem-hora dos perfis.
     */
    public static CostBreakdown monthlyCost(PriceSheet sheet, long activeConnections, int poolTenants) {
        if (sheet == null) throw new IllegalArgumentException("planilha e obrigatoria");
        if (poolTenants < 1) throw new IllegalArgumentException("pool de tenants invalido: " + poolTenants);
        BigDecimal fixed = BigDecimal.ZERO, perConnUnit = BigDecimal.ZERO, shared = BigDecimal.ZERO;
        for (CostComponent c : sheet.costComponents()) {
            switch (c.allocation()) {
                case FIXED_PER_TENANT -> fixed = fixed.add(c.monthlyAmount());
                case PER_CONNECTION -> perConnUnit = perConnUnit.add(c.monthlyAmount());
                case SHARED_POOL -> shared = shared.add(c.monthlyAmount());
            }
        }
        BigDecimal perConnection = perConnUnit.multiply(BigDecimal.valueOf(activeConnections));
        BigDecimal sharedShare = shared.divide(BigDecimal.valueOf(poolTenants), 2, RoundingMode.HALF_UP);
        BigDecimal labor = sheet.profiles().stream().map(ProfessionalProfile::monthlyCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = fixed.add(perConnection).add(sharedShare).add(labor);
        return new CostBreakdown(money(fixed), money(perConnection), money(sharedShare), money(labor), money(total));
    }

    /** Preco = custo x (1 + margem). Margem ausente = recusa [A DEFINIR] via parametros. */
    public static PricedBand price(PriceSheet sheet, SaasPricingParameters params,
                                   long activeConnections, int poolTenants) {
        if (params == null) throw new IllegalArgumentException("parametros comerciais sao obrigatorios");
        BigDecimal margin = params.marginPercent();
        CostBreakdown cost = monthlyCost(sheet, activeConnections, poolTenants);
        BigDecimal price = cost.total().multiply(BigDecimal.ONE.add(margin.movePointLeft(2)));
        return new PricedBand(sheet.bandFor(activeConnections), activeConnections, poolTenants,
                cost, margin, money(price));
    }

    static BigDecimal money(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }
}
