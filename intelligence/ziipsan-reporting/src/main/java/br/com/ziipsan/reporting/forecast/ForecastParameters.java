package br.com.ziipsan.reporting.forecast;

import br.com.ziipsan.core.json.Json;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parametros da projecao por tenant (ED-100): {@code fc.horizon_months}
 * (horizonte em meses, 1..120) e {@code fc.scenarios} (os cenarios).
 * {@link #parse(Integer, String)} le o {@code fc.scenarios} como JSON
 * objeto nome-do-cenario -> parametros, pela classe {@code Json} do core;
 * a forma canonica dos decimais no JSON e texto ("5.00"), para nao passar
 * por double; numeros JSON tambem sao aceitos, convertidos pelo texto.
 * Campo ausente em qualquer cenario e erro nomeado
 * ({@code SCENARIO_FIELD_REQUIRED}) — premissa sem fonte declarada nao
 * entra na projecao.
 */
public record ForecastParameters(int horizonMonths, List<ForecastScenario> scenarios) {

    public static final int MAX_HORIZON_MONTHS = 120;

    public ForecastParameters {
        if (horizonMonths < 1 || horizonMonths > MAX_HORIZON_MONTHS) {
            throw new ForecastValidationException("HORIZON_INVALID",
                    "fc.horizon_months fora de 1.." + MAX_HORIZON_MONTHS + ": " + horizonMonths);
        }
        if (scenarios == null || scenarios.isEmpty()) {
            throw new ForecastValidationException("SCENARIOS_REQUIRED",
                    "fc.scenarios deve definir ao menos um cenario");
        }
        scenarios = List.copyOf(scenarios);
        long distinct = scenarios.stream().map(ForecastScenario::name).distinct().count();
        if (distinct != scenarios.size()) {
            throw new ForecastValidationException("SCENARIO_DUPLICATED", "fc.scenarios com nome de cenario repetido");
        }
    }

    /** Le fc.horizon_months e fc.scenarios (JSON) para os parametros tipados. */
    public static ForecastParameters parse(Integer horizonMonths, String scenariosJson) {
        if (horizonMonths == null) {
            throw new ForecastValidationException("HORIZON_REQUIRED", "fc.horizon_months e obrigatorio");
        }
        if (scenariosJson == null || scenariosJson.isBlank()) {
            throw new ForecastValidationException("SCENARIOS_REQUIRED", "fc.scenarios e obrigatorio");
        }
        Map<String, Object> byName;
        try {
            byName = Json.readMap(scenariosJson);
        } catch (IllegalStateException e) {
            throw new ForecastValidationException("SCENARIOS_JSON_INVALID",
                    "fc.scenarios nao e um JSON objeto valido: " + e.getMessage());
        }
        if (byName.isEmpty()) {
            throw new ForecastValidationException("SCENARIOS_REQUIRED",
                    "fc.scenarios sem nenhum cenario definido");
        }
        List<ForecastScenario> scenarios = new ArrayList<>();
        for (Map.Entry<String, Object> e : byName.entrySet()) {
            if (!(e.getValue() instanceof Map<?, ?> fields)) {
                throw new ForecastValidationException("SCENARIO_FORMAT_INVALID",
                        "cenario " + e.getKey() + " de fc.scenarios nao e um objeto de parametros");
            }
            scenarios.add(new ForecastScenario(e.getKey(),
                    decimal(e.getKey(), fields, "tariffAdjustmentPercent"),
                    decimal(e.getKey(), fields, "monthlyGrowthPercent"),
                    decimal(e.getKey(), fields, "socialTariffMonthlyDiscount"),
                    decimal(e.getKey(), fields, "cashbackMonthlyAmount"),
                    decimal(e.getKey(), fields, "recoveryShift"),
                    decimal(e.getKey(), fields, "monthlyCashOutflow")));
        }
        return new ForecastParameters(horizonMonths, scenarios);
    }

    private static BigDecimal decimal(String scenario, Map<?, ?> fields, String field) {
        Object raw = fields.get(field);
        if (raw == null) {
            throw new ForecastValidationException("SCENARIO_FIELD_REQUIRED",
                    "cenario " + scenario + ": campo " + field + " ausente em fc.scenarios");
        }
        try {
            if (raw instanceof String s) {
                return new BigDecimal(s);
            }
            if (raw instanceof Number n) {
                return new BigDecimal(n.toString());
            }
        } catch (NumberFormatException ex) {
            throw new ForecastValidationException("SCENARIO_FIELD_INVALID",
                    "cenario " + scenario + ": campo " + field + " nao e decimal: " + raw);
        }
        throw new ForecastValidationException("SCENARIO_FIELD_INVALID",
                "cenario " + scenario + ": campo " + field + " nao e decimal: " + raw);
    }
}
