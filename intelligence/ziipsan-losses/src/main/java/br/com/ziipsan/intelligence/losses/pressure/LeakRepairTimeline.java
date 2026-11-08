package br.com.ziipsan.intelligence.losses.pressure;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Tempo de reparo medido nas fases (ED-86, OPE-17): conhecimento
 * (inicio da ocorrencia -&gt; conhecimento/registro), localizacao
 * (conhecimento -&gt; localizado) e reparo (localizado -&gt;
 * reparado). Instante nao ocorrido = fase AUSENTE, nunca duracao
 * zero presumida. A apuracao confronta cada fase com a meta do
 * tenant em {@code leak.repair_phase_targets_hours}
 * ({@link PressureParameters.RepairPhaseTargets}); fase sem medicao
 * nao entra na apuracao (nao cumpre nem estoura meta por omissao).
 */
public final class LeakRepairTimeline {

    public record Timeline(UUID incidentIdOrNull, UUID workOrderIdOrNull,
                           LocalDateTime startedAt, LocalDateTime knownAt,
                           LocalDateTime locatedAtOrNull, LocalDateTime repairedAtOrNull) {
        public Timeline {
            Objects.requireNonNull(startedAt, "inicio da ocorrencia obrigatorio");
            Objects.requireNonNull(knownAt, "instante de conhecimento obrigatorio");
            if (knownAt.isBefore(startedAt)) throw new IllegalArgumentException(
                    "conhecimento anterior ao inicio da ocorrencia");
            if (locatedAtOrNull != null && locatedAtOrNull.isBefore(knownAt))
                throw new IllegalArgumentException("localizacao anterior ao conhecimento");
            if (repairedAtOrNull != null && locatedAtOrNull == null)
                throw new IllegalArgumentException(
                        "reparo sem localizacao registrada: a fase de localizacao nao se presume");
            if (repairedAtOrNull != null && repairedAtOrNull.isBefore(locatedAtOrNull))
                throw new IllegalArgumentException("reparo anterior a localizacao");
        }

        /** Duracoes SOMENTE das fases com os dois instantes conhecidos. */
        public Map<PressureParameters.RepairPhase, Duration> durations() {
            Map<PressureParameters.RepairPhase, Duration> d =
                    new EnumMap<>(PressureParameters.RepairPhase.class);
            d.put(PressureParameters.RepairPhase.AWARENESS, Duration.between(startedAt, knownAt));
            if (locatedAtOrNull != null) {
                d.put(PressureParameters.RepairPhase.LOCATION, Duration.between(knownAt, locatedAtOrNull));
                if (repairedAtOrNull != null)
                    d.put(PressureParameters.RepairPhase.REPAIR, Duration.between(locatedAtOrNull, repairedAtOrNull));
            }
            return Map.copyOf(d);
        }

        /** Tempo total inicio -&gt; reparo; nulo enquanto nao reparado. */
        public Duration totalOrNull() {
            return repairedAtOrNull == null ? null : Duration.between(startedAt, repairedAtOrNull);
        }
    }

    public record PhaseAssessment(PressureParameters.RepairPhase phase, Duration duration,
                                  double targetHours, boolean overTarget) {}

    public record Assessment(List<PhaseAssessment> phases,
                             List<PressureParameters.RepairPhase> breachedPhases,
                             Duration totalOrNull) {}

    private LeakRepairTimeline() {}

    public static Assessment assess(Timeline timeline, PressureParameters.RepairPhaseTargets targets) {
        Objects.requireNonNull(timeline, "linha do tempo do reparo obrigatoria");
        Objects.requireNonNull(targets, "metas das fases obrigatorias (leak.repair_phase_targets_hours)");
        Map<PressureParameters.RepairPhase, Duration> durations = timeline.durations();
        List<PhaseAssessment> phases = durations.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    double target = targets.targetFor(e.getKey());
                    boolean over = e.getValue().compareTo(Duration.ofMinutes(Math.round(target * 60.0))) > 0;
                    return new PhaseAssessment(e.getKey(), e.getValue(), target, over);
                }).toList();
        List<PressureParameters.RepairPhase> breached = phases.stream()
                .filter(PhaseAssessment::overTarget).map(PhaseAssessment::phase).toList();
        return new Assessment(phases, breached, timeline.totalOrNull());
    }
}
