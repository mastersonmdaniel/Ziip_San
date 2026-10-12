package br.com.sigsan.operations.metering.rules;

import java.util.List;

/**
 * Economias de uma categoria dentro da unidade, com o que a cascata precisa saber dela.
 *
 * <p>O consumo mínimo por economia vem da tarifa vigente (módulo de faturamento); a metering o
 * recebe pronto. Tabelas de não medido (área, pontos, moradores) chegam já resolvidas para a
 * categoria (o fallback subcategoria &rarr; categoria é de quem monta a entrada).
 *
 * @param categoryCode               código da categoria (usado para achar a ação de anormalidade)
 * @param economies                  número de economias da categoria (>= 1)
 * @param minimumConsumptionPerEconomy consumo mínimo por economia da tarifa vigente (m³)
 * @param equivalentEconomies        economias equivalentes ("fator de economias") que substituem
 *                                   {@code economies} no consumo mínimo e no não medido; nulo = usa {@code economies}
 * @param thresholds                 patamares de estouro/alto/baixo; nulo = não configurados
 * @param areaRules                  tabela de consumo por área (pode ser vazia fora do modo BY_AREA)
 * @param parameterRules             tabela de consumo por pontos/moradores (pode ser vazia)
 */
public record EconomyGroup(
        String categoryCode,
        int economies,
        int minimumConsumptionPerEconomy,
        Integer equivalentEconomies,
        CategoryThresholds thresholds,
        List<AreaConsumptionRule> areaRules,
        List<ParameterConsumptionRule> parameterRules) {

    public EconomyGroup {
        MeteringInputException.require(categoryCode != null && !categoryCode.isBlank(),
                MeteringErrorCode.INVALID_INPUT, "categoryCode é obrigatório");
        MeteringInputException.require(economies >= 1, MeteringErrorCode.NO_ECONOMIES,
                "categoria " + categoryCode + " sem economias");
        MeteringInputException.require(minimumConsumptionPerEconomy >= 0, MeteringErrorCode.INVALID_INPUT,
                "consumo mínimo por economia negativo");
        MeteringInputException.require(equivalentEconomies == null || equivalentEconomies >= 1,
                MeteringErrorCode.INVALID_INPUT, "economias equivalentes deve ser >= 1");
        areaRules = areaRules == null ? List.of() : List.copyOf(areaRules);
        parameterRules = parameterRules == null ? List.of() : List.copyOf(parameterRules);
    }

    /** Economias usadas no consumo mínimo e no não medido: as equivalentes, se houver. */
    int effectiveEconomies() {
        return equivalentEconomies != null ? equivalentEconomies : economies;
    }

    public static EconomyGroup simple(String categoryCode, int economies, int minimumConsumptionPerEconomy) {
        return new EconomyGroup(categoryCode, economies, minimumConsumptionPerEconomy, null, null, null, null);
    }
}
