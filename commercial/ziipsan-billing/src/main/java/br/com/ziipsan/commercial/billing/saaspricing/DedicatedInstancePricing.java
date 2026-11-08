package br.com.ziipsan.commercial.billing.saaspricing;

import java.math.BigDecimal;
import java.util.List;

/**
 * Preco da instancia dedicada (ADR-0024, item 2 e consequencia
 * negativa registrada no ADR: "instancia dedicada custa uma pilha
 * inteira por cliente; o preco precisa cobrir isso"). A pilha propria
 * (api, worker, Keycloak e banco, na mesma hospedagem) entra como
 * componentes de custo DECLARADOS da avaliacao - nenhum custo de pilha
 * e fixado aqui. Regras: (1) preco calculado cobre a pilha com a
 * margem; (2) preco COTADO abaixo do custo da pilha e RECUSADO;
 * (3) a pilha propria so aceita componente de custo FIXO por tenant
 * (FIXED_PER_TENANT): componente PER_CONNECTION tem valor UNITARIO
 * (nao e custo da pilha) e SHARED_POOL e custo repartido da
 * plataforma - qualquer um dos dois na pilha dedicada e RECUSADO
 * em ownStackCost, em vez de somado pelo valor bruto.
 */
public final class DedicatedInstancePricing {
    private DedicatedInstancePricing() {}

    public record DedicatedQuote(BigDecimal ownStackMonthlyCost, BigDecimal marginPercent, BigDecimal monthlyPrice) {}

    public static BigDecimal ownStackCost(List<CostComponent> stackComponents) {
        if (stackComponents == null || stackComponents.isEmpty())
            throw new IllegalArgumentException("pilha propria da instancia dedicada nao declarada [A DEFINIR]");
        for (CostComponent component : stackComponents) {
            if (component == null)
                throw new IllegalArgumentException("componente da pilha propria nao declarado [A DEFINIR]");
            if (component.allocation() != CostComponent.Allocation.FIXED_PER_TENANT)
                throw new IllegalArgumentException("componente da pilha propria nao e custo fixo da pilha ("
                        + component.name() + ": " + component.allocation()
                        + ") - a pilha propria so aceita FIXED_PER_TENANT; PER_CONNECTION e custo unitario"
                        + " por ligacao e SHARED_POOL e custo repartido, nenhum dos dois compoe o custo"
                        + " da pilha dedicada pelo valor bruto");
        }
        return PriceCalculator.money(stackComponents.stream().map(CostComponent::monthlyAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    public static DedicatedQuote coveringPrice(List<CostComponent> stackComponents, SaasPricingParameters params) {
        if (params == null) throw new IllegalArgumentException("parametros comerciais sao obrigatorios");
        BigDecimal cost = ownStackCost(stackComponents);
        BigDecimal margin = params.marginPercent();
        BigDecimal price = PriceCalculator.money(cost.multiply(BigDecimal.ONE.add(margin.movePointLeft(2))));
        return new DedicatedQuote(cost, margin, price);
    }

    /** Cotacao que nao cobre a pilha propria e recusada (ADR-0024). */
    public static void assertCoversStack(BigDecimal quotedPrice, List<CostComponent> stackComponents) {
        if (quotedPrice == null) throw new IllegalArgumentException("preco cotado e obrigatorio");
        BigDecimal cost = ownStackCost(stackComponents);
        if (quotedPrice.compareTo(cost) < 0)
            throw new IllegalArgumentException("preco da instancia dedicada (" + quotedPrice
                    + ") nao cobre a pilha propria do cliente (" + cost + ") - ADR-0024");
    }
}
