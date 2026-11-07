package br.com.ziipsan.commercial.accounting.managerial;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Contabilidade gerencial (ED-32): custo por m3 faturado, por ligacao e por
 * categoria; margem por categoria; custo do subsidio; custo total da OS
 * (material + mao de obra por cost.hourly_rates com vigencia + frota quando a
 * ED-59 existir + terceiros), com rateio por cost.allocation_method.
 *
 * Correcoes pos-revisao:
 * - (M5) rateio distribui o RESIDUO de arredondamento na MAIOR parcela:
 *   a soma das parcelas e sempre identica ao total (antes 100,00 em 3 partes
 *   iguais distribuia 99,99 — 1 centavo sumia).
 * - (M6) "por categoria" agora e dimensao de verdade: CategoryCost carrega a
 *   categoria e byCategory devolve custo/m3, custo/ligacao e margem POR
 *   categoria (antes os metodos recebiam totais ja agregados pelo chamador).
 * - (M3) taxas cost.hourly_rates vigentes sobrepostas para a mesma funcao sao
 *   recusadas como ambiguidade (antes vencia a primeira da lista).
 * - (L2) validacoes de HourlyRate (custo nulo/negativo, vigencia invertida,
 *   funcao em branco), LaborLine (horas <= 0, campos nulos) e ServiceOrderCost
 *   (componentes nulos/negativos — fleetCost continua podendo ser nulo, que e
 *   o desenho enquanto a ED-59 nao existir).
 * Nota (L4): os valores de AllocationMethod nao foram localizados em catalogo
 * oficial nas fontes locais; ficam como estao, a confirmar na integracao
 * contra a definicao de cost.allocation_method.
 */
public final class ManagerialCosts {

    public enum AllocationMethod { DIRECT, BY_BILLED_VOLUME, BY_CONNECTIONS, BY_REVENUE }

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
    /** fleetCost e nulo enquanto a ED-59 nao existir (frota fora desta entrega de custo). */
    public record ServiceOrderCost(BigDecimal materials, List<LaborLine> labor, List<HourlyRate> hourlyRates,
                                   BigDecimal fleetCost, BigDecimal thirdParties) {
        public ServiceOrderCost {
            requireNonNegativeAmount(materials, "material da OS");
            Objects.requireNonNull(labor, "linhas de mao de obra da OS sao obrigatorias");
            Objects.requireNonNull(hourlyRates, "cost.hourly_rates e obrigatorio");
            if (fleetCost != null && fleetCost.signum() < 0) throw new IllegalArgumentException("custo de frota nao pode ser negativo: " + fleetCost);
            requireNonNegativeAmount(thirdParties, "terceiros da OS");
        }
    }

    /** Totais de uma categoria (dimensao gerencial): receita, custo, m3 faturado e ligacoes da categoria. */
    public record CategoryCost(String category, BigDecimal revenue, BigDecimal cost, BigDecimal billedCubicMeters, long connections) {
        public CategoryCost {
            if (category == null || category.isBlank()) throw new IllegalArgumentException("categoria e obrigatoria");
            Objects.requireNonNull(revenue, "receita da categoria e obrigatoria");
            Objects.requireNonNull(cost, "custo da categoria e obrigatorio");
            Objects.requireNonNull(billedCubicMeters, "m3 faturado da categoria e obrigatorio");
        }
    }
    /** Indicadores gerenciais de uma categoria. */
    public record CategoryResult(String category, BigDecimal costPerBilledCubicMeter, BigDecimal costPerConnection, BigDecimal margin) {}

    private ManagerialCosts() {}

    public static BigDecimal costPerBilledCubicMeter(BigDecimal totalCost, BigDecimal billedCubicMeters) {
        requirePositive(billedCubicMeters, "m3 faturado");
        return totalCost.divide(billedCubicMeters, 4, RoundingMode.HALF_UP);
    }
    public static BigDecimal costPerConnection(BigDecimal totalCost, long connections) {
        if (connections <= 0) throw new IllegalArgumentException("ligacoes deve ser positivo");
        return totalCost.divide(BigDecimal.valueOf(connections), 4, RoundingMode.HALF_UP);
    }
    /** margem = (receita - custo) / receita */
    public static BigDecimal margin(BigDecimal revenue, BigDecimal cost) {
        requirePositive(revenue, "receita da categoria");
        return revenue.subtract(cost).divide(revenue, 4, RoundingMode.HALF_UP);
    }
    /** Indicadores POR CATEGORIA: aplica as primitivas a cada categoria da lista, sem misturar categorias. */
    public static List<CategoryResult> byCategory(List<CategoryCost> categories) {
        Objects.requireNonNull(categories, "categorias sao obrigatorias");
        List<CategoryResult> out = new ArrayList<>();
        for (CategoryCost c : categories) {
            out.add(new CategoryResult(c.category(),
                    costPerBilledCubicMeter(c.cost(), c.billedCubicMeters()),
                    costPerConnection(c.cost(), c.connections()),
                    margin(c.revenue(), c.cost())));
        }
        return out;
    }
    /** Custo do subsidio = receita que a tarifa plena geraria - receita efetiva da categoria subsidiada. */
    public static BigDecimal subsidyCost(BigDecimal fullTariffRevenue, BigDecimal effectiveRevenue) {
        BigDecimal v = fullTariffRevenue.subtract(effectiveRevenue);
        if (v.signum() < 0) throw new IllegalArgumentException("receita efetiva nao pode exceder a tarifa plena no calculo do subsidio");
        return v;
    }
    public static BigDecimal serviceOrderTotalCost(ServiceOrderCost c) {
        Objects.requireNonNull(c, "custo da OS e obrigatorio");
        BigDecimal labor = BigDecimal.ZERO;
        for (LaborLine l : c.labor()) {
            List<HourlyRate> matches = c.hourlyRates().stream().filter(r -> r.role().equals(l.role()) && r.appliesOn(l.date())).toList();
            if (matches.isEmpty())
                throw new IllegalStateException("sem cost.hourly_rates vigente para a funcao " + l.role() + " em " + l.date());
            if (matches.size() > 1)
                throw new IllegalStateException("cost.hourly_rates ambiguo (vigencias sobrepostas) para a funcao " + l.role() + " em " + l.date() + ": " + matches.size() + " taxas");
            labor = labor.add(matches.get(0).hourlyCost().multiply(l.hours()));
        }
        BigDecimal total = c.materials().add(labor).add(c.thirdParties());
        if (c.fleetCost() != null) total = total.add(c.fleetCost());
        return total.setScale(2, RoundingMode.HALF_UP);
    }
    /**
     * Rateio proporcional pela chave do metodo; DIRECT devolve o total a um unico centro.
     * Residuo de arredondamento: todas as parcelas sao arredondadas a 2 casas,
     * exceto a MAIOR parcela (maior chave; em empate, a primeira na ordem do
     * mapa), que recebe total - soma das demais. Soma das parcelas == total.
     */
    public static Map<String, BigDecimal> allocate(BigDecimal total, AllocationMethod method, Map<String, BigDecimal> keys, String directCenter) {
        Objects.requireNonNull(total, "total do rateio e obrigatorio");
        Objects.requireNonNull(method, "cost.allocation_method e obrigatorio");
        if (method == AllocationMethod.DIRECT) {
            if (directCenter == null || directCenter.isBlank()) throw new IllegalArgumentException("centro direto e obrigatorio no rateio DIRECT");
            return Map.of(directCenter, total);
        }
        Objects.requireNonNull(keys, "chaves de rateio sao obrigatorias");
        if (keys.isEmpty()) throw new IllegalArgumentException("chaves de rateio nao podem ser vazias");
        BigDecimal keySum = keys.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        requirePositive(keySum, "soma das chaves de rateio");
        String largestKey = null;
        BigDecimal largestValue = null;
        for (Map.Entry<String, BigDecimal> en : keys.entrySet()) {
            if (en.getValue() == null || en.getValue().signum() < 0) throw new IllegalArgumentException("chave de rateio invalida: " + en.getKey());
            if (largestValue == null || en.getValue().compareTo(largestValue) > 0) { largestValue = en.getValue(); largestKey = en.getKey(); }
        }
        Map<String, BigDecimal> rounded = new LinkedHashMap<>();
        BigDecimal allocatedToOthers = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> en : keys.entrySet()) {
            if (en.getKey().equals(largestKey)) continue;
            BigDecimal share = total.multiply(en.getValue()).divide(keySum, 2, RoundingMode.HALF_UP);
            rounded.put(en.getKey(), share);
            allocatedToOthers = allocatedToOthers.add(share);
        }
        rounded.put(largestKey, total.subtract(allocatedToOthers));
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        for (String k : keys.keySet()) out.put(k, rounded.get(k));
        return out;
    }
    private static void requirePositive(BigDecimal v, String name) {
        if (v == null || v.signum() <= 0) throw new IllegalArgumentException(name + " deve ser positivo: " + v);
    }
    private static void requireNonNegativeAmount(BigDecimal v, String name) {
        if (v == null || v.signum() < 0) throw new IllegalArgumentException(name + " nao pode ser nulo/negativo: " + v);
    }
}
