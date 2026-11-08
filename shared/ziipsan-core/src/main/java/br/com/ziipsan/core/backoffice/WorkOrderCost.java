package br.com.ziipsan.core.backoffice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Custo total da OS apropriado pela REGRA DA ED-32 (ED-36 consome GES-09):
 * material + mao de obra pela tabela de custo-hora por funcao com vigencia
 * ({@code cost.hourly_rates}) + frota (campo nulo enquanto a ED-59 nao
 * existir — o mesmo desenho de ManagerialCosts.ServiceOrderCost da ED-32)
 * + terceiros. A aritmetica e a mesma da ED-32, inclusive as recusas:
 * funcao sem taxa vigente e taxas vigentes sobrepostas (ambiguidade) sao
 * erro explicito, nunca "vence a primeira".
 *
 * <p>O valor do material chega precificado pelo chamador (previsto/usado da
 * {@link PlannedMaterials} x tabela de materiais da ED-34): preco nao vive
 * no nucleo da OS (P1.31).
 */
public final class WorkOrderCost {

    public record HourlyRate(String role, BigDecimal hourlyCost, LocalDate validFrom, LocalDate validTo) {
        public HourlyRate {
            if (role == null || role.isBlank()) throw new IllegalArgumentException("funcao do cost.hourly_rates e obrigatoria");
            if (hourlyCost == null || hourlyCost.signum() < 0) throw new IllegalArgumentException("custo-hora nao pode ser nulo/negativo: " + hourlyCost);
            Objects.requireNonNull(validFrom, "inicio de vigencia do custo-hora e obrigatorio");
            if (validTo != null && validTo.isBefore(validFrom)) throw new IllegalArgumentException("vigencia do custo-hora invalida");
        }
        public boolean appliesOn(LocalDate d) { return !d.isBefore(validFrom) && (validTo == null || !d.isAfter(validTo)); }
    }

    public record LaborLine(String role, BigDecimal hours, LocalDate date) {
        public LaborLine {
            if (role == null || role.isBlank()) throw new IllegalArgumentException("funcao da linha de mao de obra e obrigatoria");
            if (hours == null || hours.signum() <= 0) throw new IllegalArgumentException("horas da linha devem ser positivas: " + hours);
            Objects.requireNonNull(date, "data da linha de mao de obra e obrigatoria");
        }
    }

    public record Breakdown(BigDecimal materials, BigDecimal labor, BigDecimal fleet, BigDecimal thirdParties, BigDecimal total) {}

    private WorkOrderCost() {}

    public static Breakdown total(BigDecimal materialsAmount, List<LaborLine> labor, List<HourlyRate> hourlyRates,
                                  BigDecimal fleetCostOrNull, BigDecimal thirdParties) {
        if (materialsAmount == null || materialsAmount.signum() < 0) throw new IllegalArgumentException("valor de material nao pode ser nulo/negativo");
        Objects.requireNonNull(labor, "linhas de mao de obra sao obrigatorias");
        Objects.requireNonNull(hourlyRates, "cost.hourly_rates e obrigatorio");
        if (thirdParties == null || thirdParties.signum() < 0) throw new IllegalArgumentException("terceiros nao pode ser nulo/negativo");
        if (fleetCostOrNull != null && fleetCostOrNull.signum() < 0) throw new IllegalArgumentException("frota nao pode ser negativa");
        BigDecimal laborTotal = BigDecimal.ZERO;
        for (LaborLine l : labor) {
            List<HourlyRate> matches = hourlyRates.stream().filter(r -> r.role().equals(l.role()) && r.appliesOn(l.date())).toList();
            if (matches.isEmpty()) throw new IllegalStateException("sem cost.hourly_rates vigente para a funcao " + l.role() + " em " + l.date());
            if (matches.size() > 1) throw new IllegalStateException("cost.hourly_rates ambiguo (vigencias sobrepostas) para a funcao " + l.role() + " em " + l.date());
            laborTotal = laborTotal.add(matches.get(0).hourlyCost().multiply(l.hours()));
        }
        BigDecimal fleet = fleetCostOrNull == null ? BigDecimal.ZERO : fleetCostOrNull;
        BigDecimal total = materialsAmount.add(laborTotal).add(fleet).add(thirdParties).setScale(2, RoundingMode.HALF_UP);
        return new Breakdown(money(materialsAmount), money(laborTotal), fleetCostOrNull == null ? null : money(fleetCostOrNull),
                money(thirdParties), total);
    }

    private static BigDecimal money(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }
}
