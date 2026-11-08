package br.com.ziipsan.operations.metering.reading;

import br.com.ziipsan.operations.metering.rules.MeteringErrorCode;
import br.com.ziipsan.operations.metering.rules.MeteringInputException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Taxa de impedimento e produtividade do ciclo de leitura (ED-15):
 *
 * <ul>
 *   <li><b>taxa de impedimento por leiturista e por rota</b> —
 *       impedimentos / visitas concluídas (leituras + impedimentos),
 *       em percentual com 2 casas; dia/rota/leiturista sem visita
 *       concluída não tem taxa (não é 0%: é ausência de denominador,
 *       e o agregado simplesmente não o inclui);</li>
 *   <li><b>produtividade por dia</b> — leituras concluídas por dia,
 *       no total e por leiturista.</li>
 * </ul>
 *
 * <p>Os fatos de entrada são os fechamentos diários já apurados (esta
 * classe agrega, não lê o aparelho): planejado precisa ser ao menos
 * leituras + impedimentos, e contador negativo é erro explícito.
 */
public final class ReaderProductivityReport {

    private ReaderProductivityReport() {
    }

    /** Fechamento de um leiturista numa rota num dia. */
    public record DailyFact(UUID readerId, UUID routeId, LocalDate date,
                            int planned, int completedReadings, int impediments) {
        public DailyFact {
            if (readerId == null || routeId == null || date == null) {
                throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                        "fato diário sem leiturista, rota ou data");
            }
            if (planned < 0 || completedReadings < 0 || impediments < 0) {
                throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                        "contadores do fato diário não podem ser negativos");
            }
            if (planned < completedReadings + impediments) {
                throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                        "planejado (" + planned + ") menor que leituras + impedimentos ("
                                + (completedReadings + impediments) + ")");
            }
        }

        /** Visitas concluídas: o denominador da taxa de impedimento. */
        public int concludedVisits() {
            return completedReadings + impediments;
        }
    }

    /**
     * @param impedimentRatePctByReader taxa de impedimento (%) por leiturista
     * @param impedimentRatePctByRoute  taxa de impedimento (%) por rota
     * @param readingsByDay             leituras concluídas por dia (total)
     * @param readingsByReaderAndDay    leituras concluídas por leiturista e dia
     */
    public record Report(Map<UUID, BigDecimal> impedimentRatePctByReader,
                         Map<UUID, BigDecimal> impedimentRatePctByRoute,
                         Map<LocalDate, Long> readingsByDay,
                         Map<UUID, Map<LocalDate, Long>> readingsByReaderAndDay) {
        public Report {
            impedimentRatePctByReader = Map.copyOf(impedimentRatePctByReader);
            impedimentRatePctByRoute = Map.copyOf(impedimentRatePctByRoute);
            readingsByDay = Map.copyOf(readingsByDay);
            readingsByReaderAndDay = Map.copyOf(readingsByReaderAndDay);
        }
    }

    public static Report build(List<DailyFact> facts) {
        if (facts == null) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    "relatório de produtividade exige os fatos diários");
        }
        Map<UUID, long[]> byReader = new LinkedHashMap<>();
        Map<UUID, long[]> byRoute = new LinkedHashMap<>();
        Map<LocalDate, Long> byDay = new LinkedHashMap<>();
        Map<UUID, Map<LocalDate, Long>> byReaderDay = new LinkedHashMap<>();
        for (DailyFact fact : facts) {
            accumulate(byReader, fact.readerId(), fact);
            accumulate(byRoute, fact.routeId(), fact);
            byDay.merge(fact.date(), (long) fact.completedReadings(), Long::sum);
            byReaderDay.computeIfAbsent(fact.readerId(), k -> new LinkedHashMap<>())
                    .merge(fact.date(), (long) fact.completedReadings(), Long::sum);
        }
        return new Report(rates(byReader), rates(byRoute), byDay, byReaderDay);
    }

    private static void accumulate(Map<UUID, long[]> map, UUID key, DailyFact fact) {
        long[] sums = map.computeIfAbsent(key, k -> new long[2]);
        sums[0] += fact.impediments();
        sums[1] += fact.concludedVisits();
    }

    private static Map<UUID, BigDecimal> rates(Map<UUID, long[]> sums) {
        Map<UUID, BigDecimal> result = new LinkedHashMap<>();
        for (Map.Entry<UUID, long[]> entry : sums.entrySet()) {
            long impediments = entry.getValue()[0];
            long concluded = entry.getValue()[1];
            if (concluded == 0) {
                continue;
            }
            result.put(entry.getKey(), BigDecimal.valueOf(impediments * 100L)
                    .divide(BigDecimal.valueOf(concluded), 2, RoundingMode.HALF_UP));
        }
        return result;
    }
}
