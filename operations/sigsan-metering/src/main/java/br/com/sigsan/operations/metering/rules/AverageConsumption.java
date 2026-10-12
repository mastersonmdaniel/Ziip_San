package br.com.sigsan.operations.metering.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Consumo médio da unidade (UC0068 do legado, escrito do zero).
 *
 * <p>Percorre do mês anterior à referência para trás, até {@code maxLookbackMonths} meses. Cada mês
 * em que há registro elegível (tipo e anormalidade que entram na média) soma e conta; mês sem
 * registro, ou com registro não elegível, só gasta o limite de retroação. Para quando junta
 * {@code months} meses ou o limite acaba.
 *
 * <p><b>Arredondamento:</b> a média é o <b>truncamento</b> ({@link RoundingMode#DOWN}) da soma
 * dividida pelos meses considerados (125 / 6 = 20,83 vira 20). É a regra do legado, em m³ inteiros;
 * tem viés para baixo (armadilha 3 do relatório 02) e fica documentada aqui para ser trocada por
 * decisão do prestador, não por acidente.
 *
 * <p>Sem nenhum mês elegível, a média é o consumo mínimo da ligação, com 1 mês considerado e um
 * aviso: o fallback nunca é silencioso.
 *
 * <p><b>Interpretação:</b> o relatório diz que o mês sem registro "retroage, até o máximo". Não diz
 * se o mês com registro não elegível também gasta a retroação; esta implementação gasta (senão um
 * histórico longo de anormalidades varreria o banco inteiro). Confirmar com o prestador.
 */
public final class AverageConsumption {

    private AverageConsumption() {
    }

    public static AverageResult compute(YearMonth reference, List<HistoryEntry> history, AveragePolicy policy,
                                        int connectionMinimum) {
        MeteringInputException.notNull(reference, "reference");
        MeteringInputException.notNull(history, "history");
        MeteringInputException.notNull(policy, "policy");
        MeteringInputException.require(connectionMinimum >= 0, MeteringErrorCode.INVALID_INPUT,
                "consumo mínimo da ligação negativo");

        Map<YearMonth, HistoryEntry> byMonth = new HashMap<>();
        for (HistoryEntry entry : history) {
            MeteringInputException.notNull(entry, "entrada do histórico");
            MeteringInputException.require(entry.reference().isBefore(reference),
                    MeteringErrorCode.HISTORY_NOT_BEFORE_REFERENCE,
                    "histórico em " + entry.reference() + " não é anterior à referência " + reference);
            MeteringInputException.require(byMonth.put(entry.reference(), entry) == null,
                    MeteringErrorCode.DUPLICATE_HISTORY_MONTH, "histórico duplicado em " + entry.reference());
        }

        long sum = 0;
        int considered = 0;
        for (int back = 1; back <= policy.maxLookbackMonths() && considered < policy.months(); back++) {
            HistoryEntry entry = byMonth.get(reference.minusMonths(back));
            if (entry != null && policy.accepts(entry)) {
                sum += entry.averageBasisConsumption();
                considered++;
            }
        }

        if (considered == 0) {
            return new AverageResult(connectionMinimum, 1, AverageResult.Source.CONNECTION_MINIMUM,
                    List.of(new CalculationWarning(WarningCode.AVERAGE_FROM_CONNECTION_MINIMUM,
                            "Sem histórico elegível em " + policy.maxLookbackMonths()
                                    + " meses: média = consumo mínimo da ligação (" + connectionMinimum + " m³)")));
        }
        int average = Rounding.truncate(BigDecimal.valueOf(sum)
                .divide(BigDecimal.valueOf(considered), 0, RoundingMode.DOWN));
        return new AverageResult(average, considered, AverageResult.Source.HISTORY, List.of());
    }
}
