package br.com.ziipsan.intelligence.losses.pressure;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Alarme de pressao baixa e alta (ED-86, OPE-04): avalia a serie de
 * pressao de um ponto de controle (vinda da ED-17 pela
 * {@link PressureSeriesPort}) contra os limites da zona
 * ({@code pressure.min_mca}/{@code pressure.max_mca} resolvidos do
 * tenant). Amostra abaixo do minimo = alarme LOW com o desvio;
 * acima do maximo = alarme HIGH. Serie vazia RECUSA a avaliacao:
 * conformidade nao se presume sem medicao.
 */
public final class PressureAlarmEvaluator {

    public enum AlarmKind { LOW, HIGH }

    public record PressureAlarm(String pointCode, LocalDateTime at, double observedMca,
                                AlarmKind kind, double limitMca, double deviationMca) {}

    public record Evaluation(List<PressureAlarm> alarms, int samples,
                             double minObservedMca, double maxObservedMca,
                             long lowCount, long highCount) {
        public boolean hasAlarms() { return !alarms.isEmpty(); }
    }

    private PressureAlarmEvaluator() {}

    public static Evaluation evaluate(PressurePoint point,
                                      List<PressureSeriesPort.PressureSample> series,
                                      PressureParameters.ZoneLimits limits) {
        Objects.requireNonNull(point, "ponto de controle obrigatorio");
        Objects.requireNonNull(limits, "limites da zona obrigatorios (pressure.min_mca/pressure.max_mca)");
        Objects.requireNonNull(series, "serie de pressao obrigatoria");
        if (series.isEmpty()) throw new IllegalArgumentException(
                "serie de pressao vazia para o ponto " + point.code()
                        + ": alarme nao se avalia sem medicao (conformidade nao se presume)");
        List<PressureAlarm> alarms = new java.util.ArrayList<>();
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (PressureSeriesPort.PressureSample s : series) {
            min = Math.min(min, s.pressureMca());
            max = Math.max(max, s.pressureMca());
            if (limits.isBelow(s.pressureMca())) {
                alarms.add(new PressureAlarm(point.code(), s.measuredAt(), s.pressureMca(),
                        AlarmKind.LOW, limits.minMca(), limits.minMca() - s.pressureMca()));
            } else if (limits.isAbove(s.pressureMca())) {
                alarms.add(new PressureAlarm(point.code(), s.measuredAt(), s.pressureMca(),
                        AlarmKind.HIGH, limits.maxMca(), s.pressureMca() - limits.maxMca()));
            }
        }
        long low = alarms.stream().filter(a -> a.kind() == AlarmKind.LOW).count();
        long high = alarms.stream().filter(a -> a.kind() == AlarmKind.HIGH).count();
        return new Evaluation(List.copyOf(alarms), series.size(), min, max, low, high);
    }

    /** Avaliacao lendo a serie pela porta da ED-17 (existencia conferida, fail-closed). */
    public static Evaluation evaluateFromPort(PressurePoint point, PressureSeriesPort port,
                                              LocalDateTime from, LocalDateTime to,
                                              PressureParameters.ZoneLimits limits) {
        Objects.requireNonNull(port, "porta da serie da ED-17 obrigatoria (fail-closed)");
        Objects.requireNonNull(from, "inicio do intervalo obrigatorio");
        Objects.requireNonNull(to, "fim do intervalo obrigatorio");
        if (to.isBefore(from)) throw new IllegalArgumentException("intervalo invertido: " + from + " > " + to);
        if (!port.exists(point.tenantId(), point.measuringPointCode()))
            throw new IllegalArgumentException(
                    "ponto de medicao inexistente na ED-17 para o tenant (fail-closed): "
                            + point.measuringPointCode());
        return evaluate(point, port.series(point.tenantId(), point.measuringPointCode(), from, to), limits);
    }
}
