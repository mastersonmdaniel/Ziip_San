package br.com.ziipsan.intelligence.losses.dma;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

import br.com.ziipsan.intelligence.losses.dma.DmaModel.FlowSample;
import br.com.ziipsan.intelligence.losses.dmc.LossParameters;

/**
 * Vazao minima nocturna (VMN) sobre a serie de vazoes do DMA
 * (II-6, E3) — camada aditiva sobre o {@code NightFlowAnalyzer} da
 * ED-85 (amostras horarias, resultado em {@code double}, excecao
 * quando a janela esta vazia). Aqui: amostras em qualquer cadencia,
 * aritmetica {@link BigDecimal} exacta, desfecho TIPIFICADO e
 * cobertura da janela verificada noite a noite.
 *
 * <p><b>Parametros declarados (nenhum valor presumido):</b>
 * <ul>
 *   <li>janela nocturna — {@link LossParameters.NightWindow} da
 *       ED-85 (o padrao 02:00–04:00 e DO ACEITE da ED-85; outra
 *       janela entra como parametro do chamador);</li>
 *   <li>{@code sampleIntervalMinutes} — cadencia declarada da serie;
 *       a cobertura esperada por noite e
 *       {@code minutosDaJanela / sampleIntervalMinutes} amostras
 *       (divisao exacta exigida);</li>
 *   <li>consumo nocturno legitimo — Optional: ausente ⇒ a VMN
 *       LIQUIDA sai ausente e declarada, nunca estimada (o
 *       parametro {@code loss.legit_night_use_l_per_conn_h} da
 *       ED-85 nao tem padrao de norma).</li>
 * </ul>
 *
 * <p>Desfechos: {@code COMPLETE} (todas as noites tocadas pela serie
 * tem a janela completa; VMN = minimo exacto das amostras na
 * janela), {@code NO_SAMPLE_IN_WINDOW} (nenhuma amostra na janela),
 * {@code INCOMPLETE_COVERAGE} (alguma noite com janela parcial —
 * a VMN NAO e devolvida: minimo sobre janela parcial nao e a VMN).
 * VMN liquida negativa NAO e truncada (herdado da ED-85: expoe a
 * inconsistencia, com a flag {@code legitExceedsMinimum}).
 */
public final class MinimumNightFlow {

    public enum Status { COMPLETE, NO_SAMPLE_IN_WINDOW, INCOMPLETE_COVERAGE }

    /**
     * @param minimumNightFlowLPerH VMN bruta exacta — presente so em COMPLETE
     * @param netNightFlowLPerH VMN liquida exacta — presente so em COMPLETE
     *        com consumo legitimo declarado
     * @param absentComponents componentes declaradas ausentes (ex.: VMN liquida sem legitimo)
     * @param incompleteNights noites com cobertura parcial (diagnostico do desfecho)
     */
    public record Result(Status status, Optional<BigDecimal> minimumNightFlowLPerH,
                         Optional<BigDecimal> netNightFlowLPerH,
                         LossParameters.NightWindow window, int sampleIntervalMinutes,
                         int samplesInWindow, int nightsEvaluated,
                         List<LocalDate> incompleteNights, List<String> absentComponents,
                         boolean legitExceedsMinimum) {
        public Result {
            incompleteNights = List.copyOf(incompleteNights);
            absentComponents = List.copyOf(absentComponents);
        }
    }

    /** Nome declarado da componente liquida quando o legitimo nao foi declarado. */
    public static final String ABSENT_NET_NIGHT_FLOW = "netNightFlowLPerH";

    private MinimumNightFlow() {}

    public static Result analyze(List<FlowSample> series, LossParameters.NightWindow window,
                                 int sampleIntervalMinutes, Optional<BigDecimal> legitNightUseLPerConnH,
                                 int connections) {
        Objects.requireNonNull(series, "serie de vazoes obrigatoria");
        Objects.requireNonNull(window, "janela nocturna obrigatoria (parametro declarado)");
        Objects.requireNonNull(legitNightUseLPerConnH, "legitimo: Optional obrigatorio (vazio = ausente declarado)");
        if (sampleIntervalMinutes <= 0)
            throw new IllegalArgumentException("cadencia da serie invalida: " + sampleIntervalMinutes);
        if (connections <= 0)
            throw new IllegalArgumentException("VMN liquida exige ligacoes > 0: " + connections);
        legitNightUseLPerConnH.ifPresent(v -> {
            if (v.signum() < 0) throw new IllegalArgumentException("consumo nocturno legitimo negativo: " + v);
        });
        long windowMinutes = (window.end().toSecondOfDay() - window.start().toSecondOfDay()) / 60;
        if (windowMinutes % sampleIntervalMinutes != 0)
            throw new IllegalArgumentException("cadencia " + sampleIntervalMinutes
                    + " min nao divide exactamente a janela de " + windowMinutes + " min");
        long expectedPerNight = windowMinutes / sampleIntervalMinutes;

        List<FlowSample> inWindow = series.stream()
                .filter(s -> window.contains(s.instant().toLocalTime()))
                .toList();
        if (inWindow.isEmpty())
            return new Result(Status.NO_SAMPLE_IN_WINDOW, Optional.empty(), Optional.empty(),
                    window, sampleIntervalMinutes, 0, 0, List.of(), absent(legitNightUseLPerConnH), false);

        Map<LocalDate, Long> perNight = new TreeMap<>();
        for (FlowSample s : inWindow) perNight.merge(nightOf(s.instant(), window), 1L, Long::sum);
        List<LocalDate> incomplete = perNight.entrySet().stream()
                .filter(e -> e.getValue() != expectedPerNight)
                .map(Map.Entry::getKey).toList();
        if (!incomplete.isEmpty())
            return new Result(Status.INCOMPLETE_COVERAGE, Optional.empty(), Optional.empty(),
                    window, sampleIntervalMinutes, inWindow.size(), perNight.size(),
                    incomplete, absent(legitNightUseLPerConnH), false);

        BigDecimal minimum = inWindow.stream().map(FlowSample::flowLitresPerHour)
                .min(BigDecimal::compareTo).orElseThrow();
        Optional<BigDecimal> net = legitNightUseLPerConnH.map(legit -> minimum.subtract(
                legit.multiply(BigDecimal.valueOf(connections))));
        boolean exceeds = net.map(n -> n.signum() < 0).orElse(false);
        return new Result(Status.COMPLETE, Optional.of(minimum), net,
                window, sampleIntervalMinutes, inWindow.size(), perNight.size(),
                List.of(), absent(legitNightUseLPerConnH), exceeds);
    }

    /** A noite de uma amostra na janela: a data do instante (janela sem virada de dia, como na ED-85). */
    private static LocalDate nightOf(LocalDateTime instant, LossParameters.NightWindow window) {
        return instant.toLocalDate();
    }

    private static List<String> absent(Optional<BigDecimal> legit) {
        return legit.isEmpty() ? List.of(ABSENT_NET_NIGHT_FLOW) : List.of();
    }
}
