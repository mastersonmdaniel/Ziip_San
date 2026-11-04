package br.com.ziipsan.core.protocol;

/**
 * Value of the tenant parameter {@code sac.deadline_counting_rule}
 * (PARAMETROS-DO-TENANT, 20d §5, v2.15; factory default CALENDAR, the
 * counting most favorable to the user). CALENDAR counts calendar days;
 * BUSINESS counts business days, skipping Saturdays, Sundays and the
 * holidays selected by {@code sac.holiday_scope}.
 */
public enum DeadlineCountingRule {
    CALENDAR,
    BUSINESS
}
