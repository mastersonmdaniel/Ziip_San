package br.com.ziipsan.operations.metering.critique;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Deteccao proactiva de vazamento por UC (II-2, E5), metodo DECLARADO:
 * <ol>
 *   <li>para cada dia com TODOS os pontos da janela nocturna presentes,
 *       calcula-se a media do consumo nas horas nocturnas;</li>
 *   <li>um dia "com fluxo continuo" e um dia cuja media nocturna e
 *       ESTRITAMENTE maior que {@code minNightFlow} configurado;</li>
 *   <li>o sinal dispara na primeira sequencia de
 *       {@code consecutiveNights} dias de calendario CONSECUTIVOS, todos
 *       com fluxo continuo. Dia sem serie completa quebra a sequencia
 *       (sem dado nao ha continuidade presumida).</li>
 * </ol>
 * Deterministico: mesma serie + mesma configuracao = mesmo sinal.
 */
public final class LeakSignalDetector {

    public Optional<LeakSignal> detect(UUID unitId, List<HourlyConsumption> series,
                                       LeakDetectionConfig config) {
        Map<LocalDate, BigDecimal> nightMeanByDay = new TreeMap<>();
        Map<LocalDate, List<HourlyConsumption>> byDay = new TreeMap<>();
        for (HourlyConsumption point : series) {
            byDay.computeIfAbsent(point.date(), d -> new java.util.ArrayList<>()).add(point);
        }
        for (Map.Entry<LocalDate, List<HourlyConsumption>> entry : byDay.entrySet()) {
            BigDecimal mean = nightMean(entry.getValue(), config);
            if (mean != null) {
                nightMeanByDay.put(entry.getKey(), mean);
            }
        }

        LocalDate runStart = null;
        BigDecimal runMinimum = null;
        int runLength = 0;
        LocalDate previousDay = null;
        for (Map.Entry<LocalDate, BigDecimal> entry : nightMeanByDay.entrySet()) {
            LocalDate day = entry.getKey();
            boolean flows = entry.getValue().compareTo(config.minNightFlow()) > 0;
            boolean consecutive = previousDay != null && day.equals(previousDay.plusDays(1));
            if (flows && (!consecutive || runLength == 0)) {
                runStart = day;
                runMinimum = entry.getValue();
                runLength = 1;
            } else if (flows) {
                runLength++;
                runMinimum = runMinimum.min(entry.getValue());
            } else {
                runStart = null;
                runMinimum = null;
                runLength = 0;
            }
            previousDay = day;
            if (flows && runLength >= config.consecutiveNights()) {
                return Optional.of(new LeakSignal(unitId, runStart, day, runMinimum,
                        config.minNightFlow(), runLength));
            }
            if (!flows) {
                previousDay = day;
            }
        }
        return Optional.empty();
    }

    /** Media nocturna do dia; nulo quando falta algum ponto da janela (dia incompleto nao conta). */
    private BigDecimal nightMean(List<HourlyConsumption> dayPoints, LeakDetectionConfig config) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int hour : config.nightHours()) {
            BigDecimal value = null;
            for (HourlyConsumption point : dayPoints) {
                if (point.hourOfDay() == hour) {
                    value = point.consumption();
                    break;
                }
            }
            if (value == null) {
                return null;
            }
            sum = sum.add(value);
        }
        return sum.divide(BigDecimal.valueOf(config.nightHours().size()), 10, RoundingMode.HALF_UP);
    }
}
