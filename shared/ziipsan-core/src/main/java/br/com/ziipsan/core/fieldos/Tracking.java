package br.com.ziipsan.core.fieldos;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Trilha GPS do leiturista e da equipe (ED-20) e a governanca LGPD dela:
 * <ul>
 *   <li>finalidade e base legal REGISTRADAS: acompanhamento da execucao
 *       das OS em campo; LGPD art. 7o, V (execucao de contrato) e IX
 *       (interesse legitimo) — constantes {@link #PURPOSE} e
 *       {@link #LEGAL_BASES};</li>
 *   <li>rastreamento SO durante a jornada ({@code field.track_only_in_shift}
 *       travado em verdadeiro): ponto fora da janela da jornada e recusado
 *       na ingestao, nao gravado;</li>
 *   <li>aviso ao empregado: sem o aceite do aviso registrado, a trilha do
 *       trabalhador nao comeca ({@link #requireEmployeeNotice}) — e o
 *       portao e COMPOSTO na ingestao ({@link #ingest} exige o aviso,
 *       correcao D-1): nao existe ingestao de ponto GPS que pule o aviso;</li>
 *   <li>acesso a trilha SO da chefia ({@link #requireSupervisorAccess}); o
 *       item correspondente entra no RIPD do tenant (P3.10) — descritor em
 *       {@link #RIPD_ITEM_CODE};</li>
 *   <li>retencao por {@code field.track_retention_days} (so reduz — ver
 *       {@link FieldOsParameters#validateRetentionDays}): o corte de expurgo
 *       e {@link #purgeCutoff}.</li>
 * </ul>
 * O resumo do painel (deslocamento, tempo, paradas) e calculado aqui de
 * forma pura; o limiar de parada e informado pelo chamador (configuracao
 * do tenant) — nenhum limiar e presumido nesta entrega.
 */
public final class Tracking {

    public static final String PURPOSE =
            "Acompanhamento da execucao das ordens de servico em campo (deslocamento, tempo e paradas da jornada)";
    public static final List<String> LEGAL_BASES = List.of("LGPD art. 7o, V", "LGPD art. 7o, IX");
    public static final String RIPD_ITEM_CODE = "FIELD_GPS_TRACKING";

    public record TrackPoint(UUID workerId, UUID teamIdOrNull, Instant at, double latitude, double longitude) {
        public TrackPoint {
            Objects.requireNonNull(workerId, "trabalhador e obrigatorio");
            Objects.requireNonNull(at, "instante do ponto e obrigatorio");
            if (latitude < -90 || latitude > 90) throw new IllegalArgumentException("latitude fora da faixa");
            if (longitude < -180 || longitude > 180) throw new IllegalArgumentException("longitude fora da faixa");
        }
    }

    /** Janela da jornada do trabalhador num dia (inicio/fim declarados pela escala). */
    public record Shift(Instant start, Instant end) {
        public Shift {
            Objects.requireNonNull(start, "inicio da jornada e obrigatorio");
            Objects.requireNonNull(end, "fim da jornada e obrigatorio");
            if (!end.isAfter(start)) throw new IllegalArgumentException("fim da jornada deve ser depois do inicio");
        }
        public boolean contains(Instant at) { return !at.isBefore(start) && !at.isAfter(end); }
    }

    public record Stop(Instant from, Instant to) {
        public Duration duration() { return Duration.between(from, to); }
    }

    public record Summary(double distanceMeters, Duration trackedTime, Duration movingTime,
                          List<Stop> stops) {
        public Summary { stops = stops == null ? List.of() : List.copyOf(stops); }
    }

    public enum AccessRole { SUPERVISOR, FIELD_WORKER, OTHER }

    private Tracking() {}

    public static void requireEmployeeNotice(boolean noticeAcknowledged) {
        if (!noticeAcknowledged) throw new IllegalStateException(
                "aviso de rastreamento ao empregado nao registrado: a trilha nao comeca sem o aviso (LGPD)");
    }

    public static void requireSupervisorAccess(AccessRole role) {
        if (role != AccessRole.SUPERVISOR) throw new IllegalStateException(
                "acesso a trilha GPS restrito a chefia; papel " + role + " nao acessa");
    }

    /**
     * Ingestao — o UNICO portao de entrada da trilha (correcao D-1). Compoe,
     * nesta ordem, os tres portoes: (1) aviso ao empregado
     * ({@link #requireEmployeeNotice} — sem aviso valido, recusa nomeada e
     * NENHUM ponto e ingerido); (2) trava de jornada
     * ({@link FieldOsParameters#requireTrackOnlyInShift}); (3) ponto dentro
     * da janela da jornada. Ponto recusado nao e gravado.
     *
     * <p>Nenhum caminho legitimo de ingestao sem o portao do aviso existe
     * neste nucleo: persistir direto pela {@code TrackStore} sem passar por
     * aqui e proibido — a composicao aviso-&gt;ingestao-&gt;persistencia e
     * exigencia nomeada da integracao (pendencia P-ED20-02).
     */
    public static TrackPoint ingest(TrackPoint point, Shift shift, boolean trackOnlyInShiftConfigured,
                                    boolean employeeNoticeAcknowledged) {
        requireEmployeeNotice(employeeNoticeAcknowledged);
        FieldOsParameters.requireTrackOnlyInShift(trackOnlyInShiftConfigured);
        Objects.requireNonNull(point, "ponto e obrigatorio");
        Objects.requireNonNull(shift, "jornada e obrigatoria");
        if (!shift.contains(point.at())) throw new IllegalStateException(
                "ponto fora da jornada recusado: field.track_only_in_shift travado em verdadeiro");
        return point;
    }

    public static Instant purgeCutoff(Instant now, int retentionDays) {
        Objects.requireNonNull(now, "instante de referencia e obrigatorio");
        if (retentionDays <= 0) throw new IllegalArgumentException("retencao deve ser positiva");
        return now.minus(Duration.ofDays(retentionDays));
    }

    static double haversineMeters(TrackPoint a, TrackPoint b) {
        double r = 6_371_000.0;
        double dLat = Math.toRadians(b.latitude() - a.latitude());
        double dLon = Math.toRadians(b.longitude() - a.longitude());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.latitude())) * Math.cos(Math.toRadians(b.latitude()))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(h));
    }

    /**
     * Resumo da trilha: distancia total, tempo rastreado, tempo em movimento
     * e paradas. Um trecho e PARADA quando a distancia entre pontos
     * consecutivos fica abaixo de {@code stopRadiusMeters} por pelo menos
     * {@code stopMinDuration} — os dois limiares vem do chamador.
     */
    public static Summary summarize(List<TrackPoint> points, double stopRadiusMeters, Duration stopMinDuration) {
        Objects.requireNonNull(points, "pontos sao obrigatorios");
        Objects.requireNonNull(stopMinDuration, "limiar de parada e obrigatorio");
        if (stopRadiusMeters < 0) throw new IllegalArgumentException("raio de parada nao pode ser negativo");
        if (points.size() < 2) return new Summary(0, Duration.ZERO, Duration.ZERO, List.of());
        List<TrackPoint> sorted = points.stream().sorted(java.util.Comparator.comparing(TrackPoint::at)).toList();
        double distance = 0;
        Duration moving = Duration.ZERO;
        List<Stop> stops = new ArrayList<>();
        Instant stopStart = null;
        for (int i = 1; i < sorted.size(); i++) {
            TrackPoint prev = sorted.get(i - 1), cur = sorted.get(i);
            double d = haversineMeters(prev, cur);
            distance += d;
            Duration gap = Duration.between(prev.at(), cur.at());
            if (d > stopRadiusMeters) {
                moving = moving.plus(gap);
                if (stopStart != null) {
                    if (Duration.between(stopStart, prev.at()).compareTo(stopMinDuration) >= 0) {
                        stops.add(new Stop(stopStart, prev.at()));
                    }
                    stopStart = null;
                }
            } else if (stopStart == null) {
                stopStart = prev.at();
            }
        }
        if (stopStart != null) {
            Instant last = sorted.get(sorted.size() - 1).at();
            if (Duration.between(stopStart, last).compareTo(stopMinDuration) >= 0) stops.add(new Stop(stopStart, last));
        }
        Duration tracked = Duration.between(sorted.get(0).at(), sorted.get(sorted.size() - 1).at());
        return new Summary(distance, tracked, moving, stops);
    }
}
