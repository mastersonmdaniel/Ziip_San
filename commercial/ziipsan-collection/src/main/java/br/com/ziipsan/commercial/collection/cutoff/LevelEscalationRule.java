package br.com.ziipsan.commercial.collection.cutoff;

import java.time.LocalDate;
import java.util.List;

/**
 * Sucessao dos niveis de corte (TR-417/SG-400; TR-539/SG-501): um nivel
 * so e devido depois que o nivel anterior foi EXECUTADO, decorrido o
 * intervalo minimo do calendario do nivel
 * ({@code cut.level_schedule.minDaysSincePreviousLevel}, do tenant) e
 * enquanto a divida motivadora continuar aberta. Divida quitada ou em
 * acordo encerra a sucessao — o resto do bloqueio (contestacao, classes
 * protegidas, vedacoes) e revalidado na geracao e na execucao pelas
 * regras da P3.4, nao por esta classe.
 */
public final class LevelEscalationRule {

    private LevelEscalationRule() {
    }

    /** Execucao registrada de um nivel (TR-539/SG-501). */
    public record ExecutedLevel(CutoffLevel level, LocalDate executedAt) {
        public ExecutedLevel {
            if (level == null || executedAt == null) {
                throw new IllegalArgumentException("execucao de nivel exige nivel e data");
            }
        }
    }

    public enum BlockReason {
        /** Divida motivadora quitada ou em acordo: sucessao encerrada. */
        DEBT_SETTLED_OR_AGREED,
        /** O nivel pedido nao e o proximo da sucessao (anterior nao executado). */
        PREVIOUS_LEVEL_NOT_EXECUTED,
        /** Intervalo minimo desde o nivel anterior ainda nao decorreu. */
        MIN_INTERVAL_NOT_ELAPSED,
        /** Ja esta no ultimo nivel (supressao executada). */
        ALREADY_AT_LAST_LEVEL
    }

    /** Decisao: nivel devido, ou o motivo do bloqueio. */
    public record Decision(boolean due, CutoffLevel level, BlockReason blockedReason,
                           LocalDate earliestDate) {
    }

    /**
     * Nivel devido na data, dado o historico de execucoes da unidade.
     * Sem historico, o nivel devido e o primeiro (VALVE), sujeito ao
     * portao de geracao; com historico, e o seguinte ao maior nivel
     * executado.
     */
    public static Decision nextDue(List<ExecutedLevel> history, boolean motivatingDebtOpen,
                                   CutoffExtParameters extParams, LocalDate evaluationDate) {
        if (extParams == null || evaluationDate == null) {
            throw new IllegalArgumentException("parametros e data de avaliacao sao obrigatorios");
        }
        List<ExecutedLevel> executed = history == null ? List.of() : List.copyOf(history);
        if (!motivatingDebtOpen) {
            return new Decision(false, null, BlockReason.DEBT_SETTLED_OR_AGREED, null);
        }
        if (executed.isEmpty()) {
            return new Decision(true, CutoffLevel.VALVE, null, evaluationDate);
        }
        ExecutedLevel last = executed.stream()
                .max(java.util.Comparator.comparingInt(e -> e.level().order()))
                .orElseThrow();
        // Sucessao furada: executou nivel sem o anterior — nao se avanca
        // sobre um historico inconsistente; o proximo devido continua
        // sendo o nivel que falta, e o chamador registra a inconsistencia.
        for (CutoffLevel level : CutoffLevel.values()) {
            if (level.order() < last.level().order()
                    && executed.stream().noneMatch(e -> e.level() == level)) {
                return new Decision(false, level, BlockReason.PREVIOUS_LEVEL_NOT_EXECUTED, null);
            }
        }
        if (last.level() == CutoffLevel.SUPPRESSION) {
            return new Decision(false, null, BlockReason.ALREADY_AT_LAST_LEVEL, null);
        }
        CutoffLevel next = last.level().next().orElseThrow();
        int minDays = extParams.levelSchedule(next).minDaysSincePreviousLevel();
        LocalDate earliest = last.executedAt().plusDays(minDays);
        if (evaluationDate.isBefore(earliest)) {
            return new Decision(false, next, BlockReason.MIN_INTERVAL_NOT_ELAPSED, earliest);
        }
        return new Decision(true, next, null, earliest);
    }
}
