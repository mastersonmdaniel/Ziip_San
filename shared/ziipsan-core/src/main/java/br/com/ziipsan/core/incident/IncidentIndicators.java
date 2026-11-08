package br.com.ziipsan.core.incident;

import java.time.LocalDateTime;
import java.util.List;

/**
 * BASE dos indicadores do aceite (OPE-16) — agregados puros sobre as
 * ocorrencias, sem formula alem da nomeada:
 * <ul>
 *   <li>I-04 (NR 9/ANA, Nivel I): intermitencia por economias
 *       afetadas em paralisacoes de {@code outage.min_duration_hours}
 *       ou mais (padrao 6, so reduz) — devolve as ocorrencias que
 *       qualificam, a soma de economias do retrato do inicio e as
 *       horas-economias;</li>
 *   <li>I-05: extravasamento por quilometro de rede — devolve a
 *       contagem de OVERFLOW e o denominador (km de esgoto dos
 *       retratos); a divisao e do registro de indicadores (P3.41),
 *       denominador ausente/zero devolve indicador INDEFINIDO, nunca
 *       zero forcado.</li>
 * </ul>
 */
public final class IncidentIndicators {

    public record IntermittencyBase(List<NetworkIncident> qualifying, int economiesAffected,
                                    double economyHours) {}

    public record OverflowBase(long overflowCount, Double sewerKmOrNull) {
        public java.util.Optional<Double> perKm() {
            if (sewerKmOrNull == null || sewerKmOrNull <= 0) return java.util.Optional.empty();
            return java.util.Optional.of(overflowCount / sewerKmOrNull);
        }
    }

    private IncidentIndicators() {}

    public static IntermittencyBase intermittency(List<NetworkIncident> incidents, int minHours,
                                                  LocalDateTime now) {
        List<NetworkIncident> q = incidents.stream()
                .filter(i -> i.type() == NetworkIncident.Type.INTERRUPTION
                        || i.type() == NetworkIncident.Type.POWER_OUTAGE)
                .filter(i -> i.durationAt(now).compareTo(java.time.Duration.ofHours(minHours)) >= 0)
                .toList();
        int economies = q.stream().mapToInt(i -> i.startSnapshot().totalEconomies()).sum();
        double eh = q.stream().mapToDouble(i -> i.startSnapshot().totalEconomies()
                * (i.durationAt(now).toMinutes() / 60.0)).sum();
        return new IntermittencyBase(q, economies, eh);
    }

    public static OverflowBase overflow(List<NetworkIncident> incidents) {
        List<NetworkIncident> o = incidents.stream()
                .filter(i -> i.type() == NetworkIncident.Type.OVERFLOW).toList();
        Double km = null;
        for (NetworkIncident i : o) {
            Double v = i.startSnapshot().sewerNetworkKmOrNull();
            if (v != null) km = (km == null ? 0.0 : km) + v;
        }
        return new OverflowBase(o.size(), km);
    }
}
