package br.com.ziipsan.intelligence.losses.dmc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Vazao minima noturna e vazao noturna liquida (ED-85; OPE-01).
 *
 * <p>Entradas REAIS: amostras horarias de vazao do DMC (a curva
 * horaria da ED-17, {@code TelemetryHourly}, agregada nos
 * macromedidores de entrada do DMC — a agregacao e da fiacao; aqui
 * entram as amostras ja somadas, em litros por hora). A vazao minima
 * noturna e o MINIMO das amostras dentro da janela
 * {@code loss.night_window}; a vazao noturna LIQUIDA e esse minimo
 * MENOS o consumo noturno legitimo total
 * ({@code loss.legit_night_use_l_per_conn_h} x ligacoes) — definicao
 * literal do aceite. Liquida negativa NAO e truncada para zero: ela
 * denuncia inconsistencia entre o legitimo declarado e a vazao
 * medida, e o resultado a expoe com a flag {@code legitExceedsMinimum}.
 */
public final class NightFlowAnalyzer {

    /** Amostra horaria de vazao na entrada do DMC (litros por hora). */
    public record HourlyFlow(LocalDateTime hourStart, double flowLitresPerHour) {
        public HourlyFlow {
            Objects.requireNonNull(hourStart, "inicio da hora da amostra obrigatorio");
            if (flowLitresPerHour < 0 || Double.isNaN(flowLitresPerHour))
                throw new IllegalArgumentException("vazao horaria negativa: " + flowLitresPerHour);
        }
    }

    public record NightFlowResult(double minimumNightFlowLPerH, double legitNightUseTotalLPerH,
                                  double netNightFlowLPerH, int samplesInWindow,
                                  LossParameters.NightWindow window, boolean legitExceedsMinimum) {
        /** Volume da vazao liquida ao longo da janela, em m3 (indicador, nao componente do balanco). */
        public double netNightVolumeM3() { return netNightFlowLPerH * window.hours() / 1000.0; }
    }

    private NightFlowAnalyzer() {}

    public static NightFlowResult analyze(List<HourlyFlow> samples, LossParameters.NightWindow window,
                                          double legitNightUseLPerConnH, int connections) {
        Objects.requireNonNull(samples, "amostras horarias obrigatorias");
        Objects.requireNonNull(window, "janela noturna obrigatoria");
        if (connections <= 0) throw new IllegalArgumentException(
                "vazao noturna liquida exige ligacoes > 0: " + connections);
        if (legitNightUseLPerConnH < 0) throw new IllegalArgumentException(
                "consumo noturno legitimo negativo: " + legitNightUseLPerConnH);
        List<HourlyFlow> inWindow = samples.stream()
                .filter(s -> window.contains(s.hourStart().toLocalTime()))
                .toList();
        if (inWindow.isEmpty()) throw new IllegalArgumentException(
                "nenhuma amostra horaria dentro da janela noturna " + window.start() + "-" + window.end()
                        + " (vazao minima nao se presume)");
        double minimum = inWindow.stream().mapToDouble(HourlyFlow::flowLitresPerHour).min().orElseThrow();
        double legitTotal = legitNightUseLPerConnH * connections;
        double net = minimum - legitTotal;
        return new NightFlowResult(minimum, legitTotal, net, inWindow.size(), window, net < 0);
    }
}
