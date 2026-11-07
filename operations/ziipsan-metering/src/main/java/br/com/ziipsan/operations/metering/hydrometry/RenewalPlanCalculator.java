package br.com.ziipsan.operations.metering.hydrometry;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Plano de renovação do parque por custo × benefício (ED-14). Cálculo explícito, sem
 * número inventado: TODAS as entradas são do chamador (estimativa de submedição do
 * hidrômetro velho, tarifa vigente e custo de troca registrados pelo tenant).
 * Benefício anual = volume de perda aparente evitada (m³/ano) × tarifa (R$/m³).
 * Benefício líquido = benefício anual − custo de troca. Ordena por líquido desc.
 */
public final class RenewalPlanCalculator {
    private RenewalPlanCalculator() {}

    public record Candidate(java.util.UUID meterId, BigDecimal avoidedApparentLossM3PerYear,
                            BigDecimal tariffPerM3, BigDecimal replacementCost) {}
    public record PlanLine(java.util.UUID meterId, BigDecimal annualBenefit, BigDecimal netBenefit,
                           BigDecimal paybackYears) {}

    public static List<PlanLine> plan(List<Candidate> candidates) {
        return candidates.stream().map(c -> {
            if (c.avoidedApparentLossM3PerYear() == null || c.tariffPerM3() == null || c.replacementCost() == null)
                throw new IllegalArgumentException("Renovação exige perda evitada, tarifa e custo de troca (entradas do tenant)");
            BigDecimal benefit = c.avoidedApparentLossM3PerYear().multiply(c.tariffPerM3());
            BigDecimal net = benefit.subtract(c.replacementCost());
            BigDecimal payback = benefit.signum() > 0
                    ? c.replacementCost().divide(benefit, 4, java.math.RoundingMode.HALF_UP) : null;
            return new PlanLine(c.meterId(), benefit, net, payback);
        }).sorted(Comparator.comparing(PlanLine::netBenefit).reversed()).toList();
    }
}
