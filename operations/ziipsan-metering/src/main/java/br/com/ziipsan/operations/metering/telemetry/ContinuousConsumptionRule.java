package br.com.ziipsan.operations.metering.telemetry;


import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Alertas ao consumidor a partir da curva horária (ED-17), disparados pelo
 * canal de aviso da P3.5 junto do alerta preventivo de consumo anormal dela:
 *
 * <ul>
 *   <li><b>consumo contínuo por 24 horas</b>: as últimas 24 amostras horárias,
 *       todas com volume &gt; 0 — as 24 horas são as do aceite da ED-17, não
 *       um limiar inventado;</li>
 *   <li><b>pico</b>: uma hora com volume ESTRITAMENTE acima do limiar de pico
 *       de {@code ami.alert_thresholds} ({@link AmiAlertThresholds#peakHourlyLitres()});
 *       sem limiar configurado, não há alerta de pico.</li>
 * </ul>
 */
public final class ContinuousConsumptionRule {

    /** As 24 horas do alerta de consumo contínuo do aceite da ED-17. */
    public static final int CONTINUOUS_HOURS = 24;

    private ContinuousConsumptionRule() {
    }

    /**
     * Verdade se as últimas 24 horas da série são CONTÍGUAS (cada uma
     * exatamente 1h depois da anterior) e têm todas volume &gt; 0. Série com
     * menos de 24 amostras, ou com buraco de horas sem amostra nas últimas
     * 24, não dispara: 24 amostras espalhadas não são 24 horas contínuas.
     */
    public static boolean hasContinuous24Hours(List<HourlySample> samples) {
        TelemetryInput.notNull(samples, "samples");
        if (samples.size() < CONTINUOUS_HOURS) {
            return false;
        }
        List<HourlySample> ordered = samples.stream()
                .sorted(Comparator.comparing(HourlySample::hourStart)).toList();
        List<HourlySample> last = ordered.subList(ordered.size() - CONTINUOUS_HOURS, ordered.size());
        for (int i = 0; i < last.size(); i++) {
            if (!last.get(i).hasFlow()) {
                return false;
            }
            if (i > 0 && !last.get(i).hourStart().toInstant()
                    .equals(last.get(i - 1).hourStart().toInstant().plusSeconds(3600))) {
                return false;
            }
        }
        return true;
    }

    /** A amostra de pico (a de maior volume) se ela fica estritamente acima do limiar; vazia sem limiar ou sem pico. */
    public static Optional<HourlySample> peakAbove(List<HourlySample> samples, BigDecimal peakThresholdLitres) {
        TelemetryInput.notNull(samples, "samples");
        if (peakThresholdLitres == null) {
            return Optional.empty();
        }
        return samples.stream()
                .max(Comparator.comparing(HourlySample::volumeLitres))
                .filter(sample -> sample.volumeLitres().compareTo(peakThresholdLitres) > 0);
    }
}
