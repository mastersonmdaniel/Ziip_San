package br.com.ziipsan.operations.metering.reading;

import br.com.ziipsan.operations.metering.rules.ExpectedReadingRange;
import br.com.ziipsan.operations.metering.rules.MeteringErrorCode;
import br.com.ziipsan.operations.metering.rules.MeteringInputException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Relatório de faixa falsa de leitura (ED-15): a lista das leituras que
 * caíram <b>fora da faixa esperada</b> da unidade (a faixa da P2.5,
 * {@link ExpectedReadingRange}) — a "faixa falsa" é a leitura que a faixa
 * acusa como suspeita, para conferência (e eventual releitura amostral),
 * não uma recusa automática.
 *
 * <p>O relatório traz cada ocorrência com a faixa que a acusou e o total
 * por leiturista, porque faixa falsa concentrada num leiturista é sinal
 * de qualidade de leitura (ver também a produtividade da ED-15). Leitura
 * dentro da faixa nunca entra no relatório.
 */
public final class FalseRangeReport {

    private FalseRangeReport() {
    }

    /** Uma leitura conferida contra a sua faixa esperada. */
    public record Entry(UUID unitId, UUID readerId, int reading, ExpectedReadingRange expectedRange) {
        public Entry {
            if (unitId == null || readerId == null) {
                throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                        "entrada do relatório sem unidade ou sem leiturista");
            }
            if (expectedRange == null) {
                throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                        "entrada do relatório sem a faixa esperada da unidade");
            }
            if (reading < 0) {
                throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                        "leitura negativa na entrada do relatório");
            }
        }

        public boolean isFalseRange() {
            return expectedRange.isOutOfRange(reading);
        }
    }

    /**
     * @param falseRangeEntries só as leituras fora da faixa, na ordem da entrada
     * @param countByReader     total de faixas falsas por leiturista
     */
    public record Report(List<Entry> falseRangeEntries, Map<UUID, Long> countByReader) {
        public Report {
            falseRangeEntries = List.copyOf(falseRangeEntries);
            countByReader = Map.copyOf(countByReader);
        }

        public int total() {
            return falseRangeEntries.size();
        }
    }

    public static Report build(List<Entry> entries) {
        if (entries == null) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    "relatório de faixa falsa exige as leituras conferidas");
        }
        List<Entry> falseOnes = new ArrayList<>();
        Map<UUID, Long> byReader = new LinkedHashMap<>();
        for (Entry entry : entries) {
            if (entry.isFalseRange()) {
                falseOnes.add(entry);
                byReader.merge(entry.readerId(), 1L, Long::sum);
            }
        }
        return new Report(falseOnes, byReader);
    }
}
