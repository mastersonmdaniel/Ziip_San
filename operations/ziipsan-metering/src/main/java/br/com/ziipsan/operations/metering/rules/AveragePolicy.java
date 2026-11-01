package br.com.ziipsan.operations.metering.rules;

import java.util.Set;

/**
 * Política do consumo médio. Tudo vem do prestador (ou da agência reguladora): o número de meses
 * da média e o teto de retroação variam por norma (relatório 08, R-04), e os tipos e anormalidades
 * que entram na média são decisão do prestador.
 *
 * @param months                      quantos meses compõem a média (ex.: o legado usa um parâmetro por tenant)
 * @param maxLookbackMonths           quantos meses para trás se procura (>= months); mês sem registro
 *                                    ou não elegível gasta desse limite
 * @param typesCountingForAverage     tipos de consumo que entram na média
 * @param anomaliesCountingForAverage anormalidades que ainda deixam o registro entrar na média; um
 *                                    registro sem anormalidade sempre entra (se o tipo entrar)
 */
public record AveragePolicy(
        int months,
        int maxLookbackMonths,
        Set<ConsumptionType> typesCountingForAverage,
        Set<ConsumptionAnomalyKind> anomaliesCountingForAverage) {

    public AveragePolicy {
        MeteringInputException.require(months >= 1, MeteringErrorCode.INVALID_INPUT, "months deve ser >= 1");
        MeteringInputException.require(maxLookbackMonths >= months, MeteringErrorCode.INVALID_INPUT,
                "maxLookbackMonths deve ser >= months");
        typesCountingForAverage = Set.copyOf(MeteringInputException.notNull(typesCountingForAverage,
                "typesCountingForAverage"));
        anomaliesCountingForAverage = Set.copyOf(MeteringInputException.notNull(anomaliesCountingForAverage,
                "anomaliesCountingForAverage"));
    }

    boolean accepts(HistoryEntry entry) {
        return typesCountingForAverage.contains(entry.type())
                && (entry.anomaly() == null || anomaliesCountingForAverage.contains(entry.anomaly()));
    }
}
