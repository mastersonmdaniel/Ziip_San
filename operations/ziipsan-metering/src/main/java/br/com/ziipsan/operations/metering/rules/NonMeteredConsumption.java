package br.com.ziipsan.operations.metering.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

/**
 * Consumo de ligação sem hidrômetro (relatório 02, 3.9), escrito do zero.
 *
 * <ul>
 *   <li>{@link NonMeteredMode#BY_TARIFF}: o consumo mínimo da ligação ({@link ConnectionMinimum}).</li>
 *   <li>{@link NonMeteredMode#BY_AREA}: área virtual = área construída / economias virtuais, com
 *       <b>2 casas, HALF_UP</b>. Por categoria, entre as linhas da tabela cuja vigência já começou,
 *       fica a vigência mais recente; nela, a de menor área máxima que ainda cobre a área virtual.
 *       Consumo = soma de (consumo da linha &times; economias equivalentes, ou as economias).</li>
 *   <li>{@link NonMeteredMode#BY_UTILIZATION_POINTS} e {@link NonMeteredMode#BY_RESIDENTS}: igual,
 *       com o parâmetro no lugar da área; consumo = soma de (consumo da linha &times; economias).
 *       Parâmetro ausente assume 1 <b>com aviso</b>.</li>
 * </ul>
 *
 * <p>O legado completa a área ausente com "a menor área da faixa, ou 1". Aqui área ausente é erro:
 * a menor linha da tabela favoreceria o cliente sem ninguém ter decidido isso. A multiplicação
 * difere entre os modos (equivalentes na área, economias nos parâmetros) porque o relatório 02
 * assim a descreve; preservado.
 */
public final class NonMeteredConsumption {

    private NonMeteredConsumption() {
    }

    /** Resultado do cálculo, com os avisos que ele gerou. */
    public record Result(int consumption, List<CalculationWarning> warnings) {
    }

    public static Result compute(NonMeteredMode mode, List<EconomyGroup> groups, NonMeteredFacts facts,
                                 YearMonth reference) {
        MeteringInputException.notNull(mode, "nonMeteredMode");
        MeteringInputException.notNull(groups, "groups");
        MeteringInputException.notNull(facts, "nonMeteredFacts");
        MeteringInputException.notNull(reference, "reference");
        return switch (mode) {
            case BY_TARIFF -> new Result(ConnectionMinimum.of(groups), List.of());
            case BY_AREA -> byArea(groups, facts, reference);
            case BY_UTILIZATION_POINTS -> byParameter(groups, facts.utilizationPoints(), reference,
                    WarningCode.UTILIZATION_POINTS_MISSING_ASSUMED_ONE, "pontos de utilização");
            case BY_RESIDENTS -> byParameter(groups, facts.residents(), reference,
                    WarningCode.RESIDENTS_MISSING_ASSUMED_ONE, "moradores");
        };
    }

    private static Result byArea(List<EconomyGroup> groups, NonMeteredFacts facts, YearMonth reference) {
        MeteringInputException.require(facts.builtAreaM2() != null && facts.builtAreaM2().signum() > 0,
                MeteringErrorCode.MISSING_BUILT_AREA, "consumo por área exige a área construída");
        MeteringInputException.require(facts.virtualEconomies() != null && facts.virtualEconomies() > 0,
                MeteringErrorCode.MISSING_VIRTUAL_ECONOMIES, "consumo por área exige as economias virtuais");
        BigDecimal virtualArea = facts.builtAreaM2()
                .divide(BigDecimal.valueOf(facts.virtualEconomies()), 2, RoundingMode.HALF_UP);

        long total = 0;
        for (EconomyGroup group : groups) {
            List<AreaConsumptionRule> current = latestVigency(group.areaRules(), AreaConsumptionRule::validFrom,
                    reference);
            AreaConsumptionRule line = current.stream()
                    .filter(r -> r.maxArea().compareTo(virtualArea) >= 0)
                    .min(Comparator.comparing(AreaConsumptionRule::maxArea))
                    .orElseThrow(() -> new MeteringInputException(MeteringErrorCode.NO_CONSUMPTION_RULE_FOR_VALUE,
                            "categoria " + group.categoryCode() + ": nenhuma faixa de área cobre " + virtualArea
                                    + " m² em " + reference));
            total += (long) line.consumption() * group.effectiveEconomies();
        }
        return new Result(Math.toIntExact(total), List.of());
    }

    private static Result byParameter(List<EconomyGroup> groups, Integer parameterOrNull, YearMonth reference,
                                      WarningCode missingWarning, String label) {
        List<CalculationWarning> warnings = new java.util.ArrayList<>();
        int parameter;
        if (parameterOrNull == null) {
            parameter = 1;
            warnings.add(new CalculationWarning(missingWarning,
                    "Cadastro sem " + label + ": assumido 1 para buscar o consumo na tabela"));
        } else {
            MeteringInputException.require(parameterOrNull >= 0, MeteringErrorCode.INVALID_INPUT,
                    label + " negativo");
            parameter = parameterOrNull;
        }

        long total = 0;
        for (EconomyGroup group : groups) {
            List<ParameterConsumptionRule> current = latestVigency(group.parameterRules(),
                    ParameterConsumptionRule::validFrom, reference);
            ParameterConsumptionRule line = current.stream()
                    .filter(r -> r.maxParameter() >= parameter)
                    .min(Comparator.comparingInt(ParameterConsumptionRule::maxParameter))
                    .orElseThrow(() -> new MeteringInputException(MeteringErrorCode.NO_CONSUMPTION_RULE_FOR_VALUE,
                            "categoria " + group.categoryCode() + ": nenhuma faixa cobre " + label + " = "
                                    + parameter + " em " + reference));
            total += (long) line.consumption() * group.economies();
        }
        return new Result(Math.toIntExact(total), warnings);
    }

    /** Linhas da vigência mais recente que já começou até a referência. */
    private static <T> List<T> latestVigency(List<T> rules, java.util.function.Function<T, YearMonth> validFrom,
                                             YearMonth reference) {
        YearMonth latest = rules.stream().map(validFrom).filter(v -> !v.isAfter(reference))
                .max(Comparator.naturalOrder()).orElse(null);
        if (latest == null) {
            return List.of();
        }
        return rules.stream().filter(r -> validFrom.apply(r).equals(latest)).toList();
    }
}
