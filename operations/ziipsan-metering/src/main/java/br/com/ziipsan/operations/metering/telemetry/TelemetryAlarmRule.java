package br.com.ziipsan.operations.metering.telemetry;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.operations.metering.rules.MeteringErrorCode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Detecção dos alarmes padronizados do medidor sobre a série horária
 * (ED-17), com os limiares dos parâmetros do tenant — nenhum limiar mora
 * nesta classe:
 *
 * <ul>
 *   <li><b>fluxo contínuo ou vazamento</b> ({@code CONTINUOUS_FLOW}): sequência
 *       de horas seguidas com volume &gt; 0 que atinge
 *       {@code meter.alarm_leak_hours} horas — um alarme por sequência, no
 *       instante em que o limiar é atingido;</li>
 *   <li><b>fluxo reverso</b> ({@code REVERSE_FLOW}): volume reverso acumulado
 *       da série que atinge {@code meter.alarm_reverse_flow_l} litros — um
 *       alarme, no instante da hora que cruza o limiar;</li>
 *   <li>os demais tipos ({@code EMPTY_PIPE}, {@code TAMPERING},
 *       {@code BATTERY_LOW}, {@code OVERFLOW} e o {@code REVERSE_FLOW}
 *       sinalizado) chegam como sinalização do dispositivo em
 *       {@link HourlySample#deviceAlarms()} e são repassados um a um.</li>
 * </ul>
 *
 * A série é ordenada por {@code hourStart} aqui dentro: quem chama não
 * precisa garantir a ordem, e horas fora de ordem não quebram a sequência
 * nem a escondem. Fluxo contínuo exige horas CONTÍGUAS: uma hora que falta
 * na série (buraco sem amostra) quebra a sequência como uma hora sem fluxo
 * — contar amostras sem conferir o relógio transformaria 3 horas espalhadas
 * em "3 horas contínuas".
 */
public final class TelemetryAlarmRule {

    private TelemetryAlarmRule() {
    }

    /** Um alarme detectado: o tipo, o instante e o detalhe em JSON (pela classe {@code Json} do core). */
    public record Detected(MeterAlarmKind kind, OffsetDateTime at, String detailJson) {
    }

    public static List<Detected> detect(List<HourlySample> samples, int leakHours,
                                        BigDecimal reverseFlowThresholdLitres) {
        TelemetryInput.notNull(samples, "samples");
        TelemetryInput.require(leakHours >= 1, MeteringErrorCode.INVALID_INPUT,
                "meter.alarm_leak_hours precisa ser >= 1");
        TelemetryInput.require(reverseFlowThresholdLitres != null
                        && reverseFlowThresholdLitres.signum() > 0, MeteringErrorCode.INVALID_INPUT,
                "meter.alarm_reverse_flow_l é obrigatório e precisa ser positivo");

        List<HourlySample> ordered = samples.stream()
                .sorted(Comparator.comparing(HourlySample::hourStart)).toList();
        List<Detected> detected = new ArrayList<>();

        int continuousRun = 0;
        boolean continuousReported = false;
        BigDecimal reverseAccumulated = BigDecimal.ZERO;
        boolean reverseReported = false;
        OffsetDateTime previousHour = null;
        for (HourlySample sample : ordered) {
            boolean contiguous = previousHour != null
                    && sample.hourStart().toInstant().equals(previousHour.toInstant().plusSeconds(3600));
            if (previousHour != null && !contiguous) {
                // Buraco de horas sem amostra: a sequência de fluxo contínuo quebra.
                continuousRun = 0;
                continuousReported = false;
            }
            previousHour = sample.hourStart();
            if (sample.hasFlow()) {
                continuousRun++;
                if (continuousRun >= leakHours && !continuousReported) {
                    detected.add(new Detected(MeterAlarmKind.CONTINUOUS_FLOW, sample.hourStart(),
                            Json.write(Map.of("continuousHours", continuousRun,
                                    "leakHoursThreshold", leakHours))));
                    continuousReported = true;
                }
            } else {
                continuousRun = 0;
                continuousReported = false;
            }
            reverseAccumulated = reverseAccumulated.add(sample.reverseOrZero());
            if (!reverseReported && reverseAccumulated.compareTo(reverseFlowThresholdLitres) >= 0) {
                detected.add(new Detected(MeterAlarmKind.REVERSE_FLOW, sample.hourStart(),
                        Json.write(Map.of("reverseLitres", reverseAccumulated,
                                "reverseThresholdLitres", reverseFlowThresholdLitres))));
                reverseReported = true;
            }
            for (MeterAlarmKind flag : sample.deviceAlarms()) {
                detected.add(new Detected(flag, sample.hourStart(),
                        Json.write(Map.of("origin", "DEVICE"))));
            }
        }
        return detected;
    }
}
