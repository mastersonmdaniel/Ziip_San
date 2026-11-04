package br.com.ziipsan.core.protocol;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * The single deadline engine of P1.36 (plan line 663), reused by P3.4
 * and ED-35: given an opening date, a number of days from the request
 * specification, the tenant's {@code sac.deadline_counting_rule} and the
 * holidays selected by {@code sac.holiday_scope}, it computes the
 * protocol's deadline. It is pure (no database, no clock): callers load
 * the holidays from {@code cal.holidays} and pass the filtered set in.
 *
 * <p>Semantics, fixed here so every consumer agrees:
 * <ul>
 *   <li>CALENDAR: the deadline is the opening date plus the number of
 *       days, weekends and holidays included.</li>
 *   <li>BUSINESS: the deadline is the Nth business day after the
 *       opening date — the opening day itself never counts, Saturdays,
 *       Sundays and holidays in the given set are skipped.</li>
 *   <li>An optional point ({@code cal.holidays} kind OPTIONAL_POINT)
 *       does NOT count as a non-business day by default, mirroring the
 *       V1_5/BusinessCalendar convention; a caller that counts it
 *       passes {@code includeOptionalPoints = true} to the filter.</li>
 *   <li>No holiday is ever embedded here, not even the national ones:
 *       a holiday missing from the tenant's register is a business day,
 *       and the register is the tenant's responsibility, with its source
 *       recorded (P0.19).</li>
 * </ul>
 */
public final class DeadlineCalculator {

    private DeadlineCalculator() {
    }

    /** Sphere of a {@code cal.holidays} row (V1_5 CHECK), distinct from the tenant's scope parameter. */
    public enum HolidaySphere {
        NATIONAL,
        STATE,
        MUNICIPAL
    }

    /**
     * A holiday register row, in the shape of {@code cal.holidays}
     * (V1_5). {@code stateCode} is the row's UF (state rows only) and
     * {@code municipalityIbge} its IBGE code (municipal rows only).
     */
    public record HolidayEntry(LocalDate date, HolidaySphere sphere, String stateCode,
                               String municipalityIbge, boolean optionalPoint) {
        public HolidayEntry {
            if (date == null || sphere == null) {
                throw new IllegalArgumentException("Data e esfera do feriado são obrigatórias");
            }
            if (sphere == HolidaySphere.STATE && (stateCode == null || stateCode.isBlank())) {
                throw new IllegalArgumentException("Feriado estadual exige a UF");
            }
            if (sphere == HolidaySphere.MUNICIPAL
                    && (municipalityIbge == null || municipalityIbge.isBlank())) {
                throw new IllegalArgumentException("Feriado municipal exige o código IBGE do município");
            }
        }
    }

    /**
     * Filters register rows to the dates that count for a service in
     * the given state/municipality under the tenant's scope parameter.
     * A state row of another state, or a municipal row of another
     * municipality, never counts; when the locality is unknown (null),
     * only national holidays count — the NATIONAL factory default
     * needs no locality.
     */
    public static Set<LocalDate> filterHolidayDates(Collection<HolidayEntry> entries,
                                                    HolidayScope scope, String stateCode,
                                                    String municipalityIbge,
                                                    boolean includeOptionalPoints) {
        Set<LocalDate> days = new HashSet<>();
        if (entries == null || scope == null) {
            return days;
        }
        for (HolidayEntry entry : entries) {
            boolean applies = switch (entry.sphere()) {
                case NATIONAL -> true;
                case STATE -> scope != HolidayScope.NATIONAL
                        && entry.stateCode() != null && entry.stateCode().equalsIgnoreCase(stateCode);
                case MUNICIPAL -> scope == HolidayScope.NATIONAL_STATE_MUNICIPAL
                        && entry.municipalityIbge() != null
                        && entry.municipalityIbge().equals(municipalityIbge);
            };
            if (applies && (includeOptionalPoints || !entry.optionalPoint())) {
                days.add(entry.date());
            }
        }
        return days;
    }

    /** Same as {@link #filterHolidayDates} without optional points (the default). */
    public static Set<LocalDate> filterHolidayDates(Collection<HolidayEntry> entries,
                                                    HolidayScope scope, String stateCode,
                                                    String municipalityIbge) {
        return filterHolidayDates(entries, scope, stateCode, municipalityIbge, false);
    }

    public static boolean isBusinessDay(LocalDate date, Set<LocalDate> holidays) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY
                && (holidays == null || !holidays.contains(date));
    }

    /**
     * Deadline for a protocol opened on {@code opened} with
     * {@code days} days from its specification.
     */
    public static LocalDate computeDueDate(LocalDate opened, int days,
                                           DeadlineCountingRule rule, Set<LocalDate> holidays) {
        if (opened == null) {
            throw new IllegalArgumentException("A data de abertura é obrigatória");
        }
        if (days <= 0) {
            throw new IllegalArgumentException("O prazo da especificação deve ser maior que zero");
        }
        if (rule == null) {
            throw new IllegalArgumentException("A regra de contagem é obrigatória");
        }
        if (rule == DeadlineCountingRule.CALENDAR) {
            return opened.plusDays(days);
        }
        LocalDate date = opened;
        int remaining = days;
        while (remaining > 0) {
            date = date.plusDays(1);
            if (isBusinessDay(date, holidays)) {
                remaining--;
            }
        }
        return date;
    }

    /**
     * New deadline of an extension: {@code additionalDays} counted from
     * the current deadline, under the same rule and holidays. The
     * original deadline is untouched by the caller (the extension event
     * records previous and new dates; see ProtocolService.extend).
     */
    public static LocalDate extendDueDate(LocalDate currentDue, int additionalDays,
                                          DeadlineCountingRule rule, Set<LocalDate> holidays) {
        return computeDueDate(currentDue, additionalDays, rule, holidays);
    }
}
