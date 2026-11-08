package br.com.ziipsan.core.stationops;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Apuracao da cobertura de rondas (ED-87): dado o intervalo vigente
 * da unidade ({@code ops.round_interval_by_unit}, ja validado contra
 * o teto normativo em {@link StationOpsParameters}), o periodo e as
 * execucoes, divide o periodo em slots de um intervalo, conta os
 * slots cobertos por uma ronda CONCLUIDA dentro do slot e nomeia os
 * slots descobertos — cada slot descoberto cujo fim ja passou e uma
 * ronda nao realizada, e e ela que dispara o alerta.
 *
 * <p>Percentual so sobre o denominador nomeado (slots esperados);
 * periodo sem slot completo devolve cobertura nula, nunca 100%
 * presumido.</p>
 *
 * <p>Alerta de slot descoberto e idempotente (correcao ED-87/D3):
 * {@link #dispatchMissedAlerts} dedupa pela chave do slot
 * (tenant + unidade + inicio/fim do slot) — alerta ja emitido nao
 * reemite, mesmo se a apuracao for despachada de novo. O registro
 * de chaves e o do nucleo (em memoria, por JVM); a dedupe DURAVEL
 * entre reinicios/agendamentos e da integracao (worker da P1.9,
 * pendencia P-ED87-01), que deve persistir a mesma chave.</p>
 */
public final class RoundCoverage {

    public record Slot(Instant start, Instant end, boolean covered, UUID coveringExecutionId) {}

    public record Assessment(UUID tenantId, UUID unitId, Instant periodStart, Instant periodEnd,
                             int intervalMinutes, List<Slot> slots) {
        public Assessment { slots = List.copyOf(slots); }
        public int expected() { return slots.size(); }
        public int coveredCount() { return (int) slots.stream().filter(Slot::covered).count(); }
        /** Slots descobertos e ja vencidos: as rondas nao realizadas do periodo. */
        public List<Slot> missedSlots() {
            return slots.stream().filter(s -> !s.covered()).toList();
        }
        /** % de cobertura sobre os slots esperados; null quando nao ha slot esperado. */
        public Double coveragePercent() {
            if (slots.isEmpty()) return null;
            return 100.0 * coveredCount() / slots.size();
        }
    }

    /** Chaves de slot ja alertadas (dedupe do D3): tenant|unidade|inicio|fim do slot. */
    private static final java.util.Set<String> DISPATCHED_ALERT_KEYS =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private RoundCoverage() {}

    /** Chave de idempotencia do alerta de um slot descoberto. */
    private static String alertKey(Assessment assessment, Slot slot) {
        return assessment.tenantId() + "|" + assessment.unitId() + "|" + slot.start() + "|" + slot.end();
    }

    public static Assessment assess(UUID tenantId, UUID unitId, Instant periodStart, Instant periodEnd,
                                    int intervalMinutes, List<RoundExecution> executions, Instant now) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId e obrigatorio");
        if (unitId == null) throw new IllegalArgumentException("unitId e obrigatorio");
        if (periodStart == null || periodEnd == null || !periodEnd.isAfter(periodStart))
            throw new IllegalArgumentException("periodo da apuracao invalido");
        if (intervalMinutes <= 0) throw new IllegalArgumentException("intervalo deve ser maior que zero");
        if (executions == null) throw new IllegalArgumentException("execucoes sao obrigatorias (podem ser vazias)");
        if (now == null) throw new IllegalArgumentException("instante da apuracao e obrigatorio");

        Duration step = Duration.ofMinutes(intervalMinutes);
        // So entram slots INTEIROS dentro do periodo E ja vencidos ate 'now'
        // (slot em curso nao e nem coberto nem perdido: ainda pode ser feito).
        List<RoundExecution> done = executions.stream()
                .filter(e -> e.unitId().equals(unitId) && e.isCompleted() && e.completedAt() != null)
                .toList();
        List<Slot> slots = new ArrayList<>();
        Instant cursor = periodStart;
        while (!cursor.plus(step).isAfter(periodEnd) && !cursor.plus(step).isAfter(now)) {
            final Instant slotStart = cursor;
            final Instant slotEnd = cursor.plus(step);
            RoundExecution cover = done.stream()
                    .filter(e -> !e.completedAt().isBefore(slotStart) && e.completedAt().isBefore(slotEnd))
                    .findFirst().orElse(null);
            slots.add(new Slot(slotStart, slotEnd, cover != null, cover == null ? null : cover.id()));
            cursor = slotEnd;
        }
        return new Assessment(tenantId, unitId, periodStart, periodEnd, intervalMinutes, slots);
    }

    /**
     * Dispara o alerta de cada slot descoberto, pela porta; devolve
     * quantos alertas NOVOS sairam. Idempotente (correcao ED-87/D3):
     * slot cuja chave ja foi alertada nao reemite — despachar a
     * mesma apuracao duas vezes emite cada alerta uma unica vez.
     */
    public static int dispatchMissedAlerts(Assessment assessment, StationOpsPorts.RoundAlertPort alerts) {
        if (assessment == null || alerts == null)
            throw new IllegalArgumentException("apuracao e porta de alerta sao obrigatorias");
        int sent = 0;
        for (Slot slot : assessment.missedSlots()) {
            String key = alertKey(assessment, slot);
            if (DISPATCHED_ALERT_KEYS.contains(key)) continue;
            alerts.notifyMissedRound(assessment.tenantId(), assessment.unitId(), slot.start(),
                    "slot de ronda sem execucao concluida: " + slot.start() + " a " + slot.end());
            // A chave so e marcada DEPOIS do envio: se a porta falhar,
            // o slot continua pendente e um novo despacho o tenta de novo.
            DISPATCHED_ALERT_KEYS.add(key);
            sent++;
        }
        return sent;
    }
}
