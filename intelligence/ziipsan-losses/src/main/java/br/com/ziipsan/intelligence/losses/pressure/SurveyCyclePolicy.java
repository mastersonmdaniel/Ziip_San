package br.com.ziipsan.intelligence.losses.pressure;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Ciclo da campanha de pesquisa por classe de DMC (ED-86, OPE-17):
 * o ciclo em dias vem de {@code leak.survey_cycle_days_by_dmc_class}
 * (resolvido por {@link PressureParameters#surveyCycleDaysFor} —
 * classe sem ciclo declarado e parametro faltante, nunca um ciclo
 * presumido). DMC nunca pesquisado esta vencido por definicao.
 */
public final class SurveyCyclePolicy {

    private SurveyCyclePolicy() {}

    public static LocalDate nextDue(LocalDate lastSurveyDate, int cycleDays) {
        Objects.requireNonNull(lastSurveyDate, "data da ultima pesquisa obrigatoria");
        if (cycleDays <= 0) throw new IllegalArgumentException("ciclo deve ser positivo: " + cycleDays);
        return lastSurveyDate.plusDays(cycleDays);
    }

    /** Vencido na data de referencia (inclusive) ou quando nunca houve pesquisa. */
    public static boolean isDue(LocalDate lastSurveyDateOrNull, int cycleDays, LocalDate at) {
        Objects.requireNonNull(at, "data de referencia obrigatoria");
        if (cycleDays <= 0) throw new IllegalArgumentException("ciclo deve ser positivo: " + cycleDays);
        if (lastSurveyDateOrNull == null) return true;
        return !at.isBefore(nextDue(lastSurveyDateOrNull, cycleDays));
    }

    /** Dias de atraso na referencia (0 quando dentro do ciclo ou nunca pesquisado). */
    public static long daysOverdue(LocalDate lastSurveyDateOrNull, int cycleDays, LocalDate at) {
        Objects.requireNonNull(at, "data de referencia obrigatoria");
        if (lastSurveyDateOrNull == null) return 0;
        LocalDate due = nextDue(lastSurveyDateOrNull, cycleDays);
        return at.isBefore(due) ? 0 : java.time.temporal.ChronoUnit.DAYS.between(due, at);
    }
}
