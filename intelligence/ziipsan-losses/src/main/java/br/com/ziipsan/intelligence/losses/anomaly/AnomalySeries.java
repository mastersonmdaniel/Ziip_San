package br.com.ziipsan.intelligence.losses.anomaly;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Serie temporal de entrada da deteccao (ED-94). As tres series do
 * aceite sao DADOS DE ENTRADA — esta entrega nao recadastra nenhuma
 * fonte: a fiacao monta a serie a partir da vazao noturna por DMC da
 * ED-85 (amostras horarias da ED-17 agregadas nos macromedidores), da
 * pressao por ponto de medicao da ED-17 e do consumo horario por
 * ligacao da ED-17. Aqui a serie e um valor imutavel, ordenado e
 * validado, identificado pela referencia estavel da fonte
 * ({@code seriesRef}: codigo do DMC, codigo do ponto ou identificacao
 * da ligacao — sempre o codigo da origem, nunca um cadastro novo).
 */
public final class AnomalySeries {

    /** As tres series nomeadas no aceite da ED-94. */
    public enum SeriesKind {
        /** Vazao noturna por DMC (ED-85 sobre a curva da ED-17), litros por hora. */
        NIGHT_FLOW_DMC,
        /** Pressao por ponto de medicao (ED-17), na unidade declarada da serie. */
        PRESSURE_POINT,
        /** Consumo por ligacao (curva horaria da ED-17), litros por hora. */
        CONSUMPTION_CONNECTION
    }

    /** Um ponto da serie: instante (relogio do ponto/DMC) e valor medido. */
    public record SeriesPoint(LocalDateTime at, double value) {
        public SeriesPoint {
            Objects.requireNonNull(at, "instante do ponto obrigatorio");
            if (Double.isNaN(value) || Double.isInfinite(value) || value < 0)
                throw new IllegalArgumentException(
                        "valor da serie deve ser finito e >= 0: " + value + " em " + at);
        }
    }

    private final String seriesRef;
    private final SeriesKind kind;
    private final String unit;
    private final List<SeriesPoint> points;

    public AnomalySeries(String seriesRef, SeriesKind kind, String unit, List<SeriesPoint> points) {
        if (seriesRef == null || seriesRef.isBlank())
            throw new IllegalArgumentException("referencia da serie obrigatoria (codigo da fonte)");
        this.seriesRef = seriesRef;
        this.kind = Objects.requireNonNull(kind, "tipo da serie obrigatorio");
        if (unit == null || unit.isBlank())
            throw new IllegalArgumentException("unidade da serie obrigatoria (declarada, nunca presumida)");
        this.unit = unit;
        Objects.requireNonNull(points, "pontos da serie obrigatorios");
        List<SeriesPoint> ordered = points.stream()
                .sorted(Comparator.comparing(SeriesPoint::at)).toList();
        for (int i = 1; i < ordered.size(); i++)
            if (!ordered.get(i).at().isAfter(ordered.get(i - 1).at()))
                throw new IllegalArgumentException(
                        "serie com instantes repetidos ou fora de ordem apos a ordenacao: "
                                + ordered.get(i).at());
        this.points = List.copyOf(ordered);
    }

    public String seriesRef() { return seriesRef; }
    public SeriesKind kind() { return kind; }
    public String unit() { return unit; }
    /** Pontos em ordem cronologica estrita (imutavel). */
    public List<SeriesPoint> points() { return points; }
    public int size() { return points.size(); }
    public boolean isEmpty() { return points.isEmpty(); }
}
