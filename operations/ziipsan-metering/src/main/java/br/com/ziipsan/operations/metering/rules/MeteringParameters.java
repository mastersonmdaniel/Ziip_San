package br.com.ziipsan.operations.metering.rules;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.parameters.MissingParameterException;
import br.com.ziipsan.core.parameters.ParameterLimits;
import tools.jackson.databind.JsonNode;

import java.util.Set;

/**
 * A ligação da média e do teto de ciclos estimados com os parâmetros do tenant
 * (PARAMETROS-DO-TENANT, RAS-14, P2.5): {@code meter.average_months},
 * {@code meter.max_estimated_cycles} e {@code meter.after_cap_rule}.
 *
 * <p>Quem lê o valor vigente é o chamador (a versão aprovada e aceita, como faz
 * {@code TenantParameterService.currentAcceptedValue}); esta classe recebe os valores como o
 * JSON gravado em {@code parameter_versions.value} e os interpreta <b>só</b> pela classe
 * {@link Json} do core. Ausência de valor aceito é erro explícito
 * ({@link MissingParameterException}, princípio 9 do PARAMETROS-DO-TENANT): a média nunca
 * assume 6 meses "porque SC usa 6" em silêncio — os valores convergentes de SC (6 meses, até
 * 3 ciclos) são a <i>referência</i> registrada no catálogo (V3 do core, limites REFERENCE),
 * não um padrão escondido no código. {@link #scConvergent()} existe para teste e para a carga
 * dos perfis da P2.31, nomeado como o que é.
 *
 * <p>Os limites de coerência são os do core ({@link ParameterLimits}, NR 11, art. 35):
 * inteiro positivo para as duas chaves numéricas. A classe das chaves no catálogo é d (livre):
 * não há teto global para meses nem para ciclos além do positivo.
 *
 * @param averageMonths      meses que compõem a média ({@code meter.average_months})
 * @param maxEstimatedCycles teto de ciclos consecutivos faturados por estimativa/média sem
 *                           leitura ({@code meter.max_estimated_cycles})
 * @param afterCapRule       o que acontece no ciclo seguinte ao teto ({@code meter.after_cap_rule})
 */
public record MeteringParameters(int averageMonths, int maxEstimatedCycles, AfterCapRule afterCapRule) {

    /** Chave do parâmetro dos meses da média. */
    public static final String AVERAGE_MONTHS_KEY = "meter.average_months";
    /** Chave do parâmetro do teto de ciclos estimados. */
    public static final String MAX_ESTIMATED_CYCLES_KEY = "meter.max_estimated_cycles";
    /** Chave do parâmetro da regra posterior ao teto. */
    public static final String AFTER_CAP_RULE_KEY = "meter.after_cap_rule";

    public MeteringParameters {
        ParameterLimits.validateAverageMonths(averageMonths);
        ParameterLimits.validateMaxEstimatedCycles(maxEstimatedCycles);
        MeteringInputException.notNull(afterCapRule, "afterCapRule");
    }

    /**
     * Os valores convergentes das ERIs de SC (RAS-14): média de 6 meses, até 3 ciclos
     * estimados. A regra posterior diverge entre as ERIs (ARIS/AGIR: mínimo; ARESC: saldo em
     * 3 vezes), por isso ela é parâmetro até aqui — "convergente" vale para os dois números.
     */
    public static MeteringParameters scConvergent(AfterCapRule afterCapRule) {
        return new MeteringParameters(6, 3, afterCapRule);
    }

    /**
     * Interpreta os três valores vigentes do tenant, cada um como o JSON de
     * {@code parameter_versions.value} (número JSON ou texto JSON com o número, para as chaves
     * numéricas; texto JSON com o nome do enum, para a regra posterior).
     *
     * @throws MissingParameterException algum valor nulo ou em branco (sem versão aceita vigente)
     * @throws IllegalArgumentException  valor fora do limite do core ou regra posterior desconhecida
     * @throws IllegalStateException     conteúdo que não é JSON válido (da classe {@link Json})
     */
    public static MeteringParameters fromTenantValues(String averageMonthsJson,
                                                      String maxEstimatedCyclesJson,
                                                      String afterCapRuleJson) {
        return new MeteringParameters(
                intValue(averageMonthsJson, AVERAGE_MONTHS_KEY),
                intValue(maxEstimatedCyclesJson, MAX_ESTIMATED_CYCLES_KEY),
                afterCapValue(afterCapRuleJson));
    }

    private static int intValue(String json, String key) {
        JsonNode node = required(json, key);
        if (node.isNumber()) {
            if (!node.isIntegralNumber()) {
                throw new IllegalArgumentException(
                        "O parâmetro " + key + " precisa ser um inteiro; recebido número fracionário.");
            }
            return node.asInt();
        }
        if (node.isTextual()) {
            try {
                return Integer.parseInt(node.asText().trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "O parâmetro " + key + " precisa ser um inteiro; recebido texto não numérico.", e);
            }
        }
        throw new IllegalArgumentException(
                "O parâmetro " + key + " precisa ser um inteiro; recebido JSON de outro tipo.");
    }

    private static AfterCapRule afterCapValue(String json) {
        JsonNode node = required(json, AFTER_CAP_RULE_KEY);
        if (!node.isTextual()) {
            throw new IllegalArgumentException(
                    "O parâmetro " + AFTER_CAP_RULE_KEY + " precisa ser um texto com o nome da regra.");
        }
        try {
            return AfterCapRule.valueOf(node.asText().trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("O parâmetro " + AFTER_CAP_RULE_KEY + " recebeu '"
                    + node.asText() + "', fora do catálogo (BILL_MINIMUM,"
                    + " SETTLE_BALANCE_IN_3_INSTALLMENTS).", e);
        }
    }

    private static JsonNode required(String json, String key) {
        if (json == null || json.isBlank()) {
            throw new MissingParameterException(key, "nenhum valor foi informado a esta regra");
        }
        return Json.readTree(json);
    }

    /**
     * A {@link AveragePolicy} da cascata com os meses destes parâmetros. A retroação máxima e
     * os tipos/anormalidades que entram na média continuam sendo decisão de quem monta a
     * política (não são parâmetros desta frente); a retroação menor que os meses é recusada
     * pela própria {@link AveragePolicy}.
     */
    public AveragePolicy averagePolicy(int maxLookbackMonths, Set<ConsumptionType> typesCountingForAverage,
                                       Set<ConsumptionAnomalyKind> anomaliesCountingForAverage) {
        return new AveragePolicy(averageMonths, maxLookbackMonths, typesCountingForAverage,
                anomaliesCountingForAverage);
    }

    /** Decisão de faturamento sem leitura de um ciclo, dado o histórico de estimativas. */
    public enum EstimatedDecision {
        /** Dentro do teto: fatura pela média (o ciclo conta como estimado). */
        ESTIMATE_BY_AVERAGE,
        /** Teto atingido e a regra posterior é o mínimo: fatura o consumo mínimo da ligação. */
        BILL_BY_MINIMUM,
        /**
         * Teto atingido e a regra posterior é o saldo em 3 vezes: a medição não estima de novo
         * e sinaliza o acerto do saldo ao faturamento (que parcela; esta regra não parcela).
         */
        SETTLE_BALANCE_IN_3_INSTALLMENTS
    }

    /**
     * Decide o ciclo sem leitura: enquanto os ciclos estimados já usados são menos que o teto,
     * estima pela média; ao atingir o teto, vale {@code meter.after_cap_rule}.
     *
     * <p>Com os valores de SC (teto 3): os ciclos estimados 1, 2 e 3 saem pela média
     * ({@code estimatedCyclesAlreadyUsed} = 0, 1, 2 na entrada) e o 4º ciclo consecutivo sem
     * leitura ({@code estimatedCyclesAlreadyUsed} = 3) já cai na regra posterior — "até 3
     * ciclos" é o número de ciclos estimados permitidos, não o do primeiro ciclo punido.
     *
     * @param estimatedCyclesAlreadyUsed ciclos consecutivos já faturados por estimativa antes
     *                                   deste (0 = o último ciclo teve leitura)
     */
    public EstimatedDecision decideWithoutReading(int estimatedCyclesAlreadyUsed) {
        MeteringInputException.require(estimatedCyclesAlreadyUsed >= 0, MeteringErrorCode.INVALID_INPUT,
                "ciclos estimados já usados negativo");
        if (estimatedCyclesAlreadyUsed < maxEstimatedCycles) {
            return EstimatedDecision.ESTIMATE_BY_AVERAGE;
        }
        return switch (afterCapRule) {
            case BILL_MINIMUM -> EstimatedDecision.BILL_BY_MINIMUM;
            case SETTLE_BALANCE_IN_3_INSTALLMENTS -> EstimatedDecision.SETTLE_BALANCE_IN_3_INSTALLMENTS;
        };
    }
}
