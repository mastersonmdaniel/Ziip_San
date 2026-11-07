package br.com.ziipsan.core.cutoff;

import br.com.ziipsan.core.protocol.DeadlineCalculator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Temporal bans of the cutoff (RAS-12), pure and hand-checkable. All
 * instants are read in the tenant's operational-calendar timezone
 * ({@code tenant.timezone}, P0.19/PLT-14): the caller converts to a
 * {@link ZonedDateTime} in that zone before asking.
 *
 * <p>Two layers, both always applied (the state layer adds to the
 * federal floor, never replaces it):
 * <ul>
 *   <li><b>Federal (Lei 14.015/2020</b>; Lei 8.987/1995, art. 6º, §4º, e
 *       Lei 13.460/2017, art. 6º, p.u.): a suspension may not START on a
 *       Friday, Saturday or Sunday, on a holiday, or on the eve of a
 *       holiday. Day-granular: the whole local day is banned as a start
 *       day. Optional points ({@code cal.holidays} kind OPTIONAL_POINT)
 *       are not holidays for this layer.</li>
 *   <li><b>Santa Catarina (Lei SC 11.959/2001, redação da Lei SC
 *       17.780/2019)</b>, applied when the unit's UF is SC: no cutoff in
 *       the interval [Friday 08:00, Monday 08:00), nor in [08:00 of the
 *       business day before a holiday OR optional point, 08:00 of the
 *       first business day after it), except by judicial order (the
 *       exception is applied by the gate rules, not here: this class
 *       only answers when the calendar itself forbids). Business day is
 *       Monday–Friday outside the holiday/optional-point set.</li>
 * </ul>
 *
 * <p>Holidays come from {@code cal.holidays} of the tenant (P0.19). For
 * the cutoff bans ALL spheres count (national, the unit's state and the
 * unit's municipality) — the locked rule of PARAMETROS-DO-TENANT,
 * distinct from {@code sac.holiday_scope}, which only governs service
 * deadlines. No holiday is embedded here: a date missing from the
 * register is not a holiday, and the register is the tenant's
 * responsibility (the {@link DeadlineCalculator} convention).
 */
public final class CutoffCalendar {

    /** The 08:00 boundary of the SC windows (Lei SC 17.780/2019). */
    public static final LocalTime SC_WINDOW_HOUR = LocalTime.of(8, 0);

    private CutoffCalendar() {
    }

    /** Dates that are holidays (kind HOLIDAY) for the unit's locality. */
    public record CalendarData(Set<LocalDate> holidays, Set<LocalDate> optionalPoints, String stateCode) {
        public CalendarData {
            holidays = holidays == null ? Set.of() : Set.copyOf(holidays);
            optionalPoints = optionalPoints == null ? Set.of() : Set.copyOf(optionalPoints);
        }

        boolean isSc() {
            return stateCode != null && stateCode.equalsIgnoreCase("SC");
        }

        /** Holiday or optional point: the set the SC business-day notion skips. */
        Set<LocalDate> nonBusinessDates() {
            var all = new HashSet<>(holidays);
            all.addAll(optionalPoints);
            return all;
        }
    }

    /** True when the federal layer bans STARTING a suspension on this local date. */
    public static boolean federalStartBanned(LocalDate date, Set<LocalDate> holidays) {
        return switch (date.getDayOfWeek()) {
            case FRIDAY, SATURDAY, SUNDAY -> true;
            default -> holidays.contains(date) || holidays.contains(date.plusDays(1));
        };
    }

    /** Business day for the SC law: Monday–Friday, not a holiday nor an optional point. */
    public static boolean isScBusinessDay(LocalDate date, CalendarData data) {
        return DeadlineCalculator.isBusinessDay(date, data.nonBusinessDates());
    }

    /**
     * True when the SC layer forbids a cutoff at this local date-time:
     * inside the weekly [Fri 08:00, Mon 08:00) window, or inside a
     * holiday/optional-point window [previous business day 08:00, next
     * business day 08:00).
     */
    public static boolean scForbiddenAt(LocalDateTime when, CalendarData data) {
        LocalDate date = when.toLocalDate();
        // Weekly window.
        LocalDate friday = date.minusDays((date.getDayOfWeek().getValue() + 2) % 7);
        // (getValue: Mon=1..Sun=7; the Friday of this ban-week is the most
        // recent Friday, today included.)
        LocalDateTime weekStart = friday.atTime(SC_WINDOW_HOUR);
        LocalDateTime weekEnd = friday.plusDays(3).atTime(SC_WINDOW_HOUR);
        if (!when.isBefore(weekStart) && when.isBefore(weekEnd)) {
            return true;
        }
        // Holiday / optional-point windows.
        for (LocalDate marked : data.nonBusinessDates()) {
            LocalDate prev = marked.minusDays(1);
            while (!isScBusinessDay(prev, data)) {
                prev = prev.minusDays(1);
            }
            LocalDate next = marked.plusDays(1);
            while (!isScBusinessDay(next, data)) {
                next = next.plusDays(1);
            }
            LocalDateTime start = prev.atTime(SC_WINDOW_HOUR);
            LocalDateTime end = next.atTime(SC_WINDOW_HOUR);
            if (!when.isBefore(start) && when.isBefore(end)) {
                return true;
            }
        }
        return false;
    }

    /** True when any applicable layer forbids a cutoff at this instant (in the tenant zone). */
    public static boolean isForbidden(ZonedDateTime when, CalendarData data) {
        if (when == null || data == null) {
            throw new IllegalArgumentException("instante e calendario sao obrigatorios");
        }
        if (federalStartBanned(when.toLocalDate(), data.holidays())) {
            return true;
        }
        return data.isSc() && scForbiddenAt(when.toLocalDateTime(), data);
    }

    /**
     * First local date, on or after {@code from}, whose day is not banned
     * as a start day by either layer. The SC 08:00 boundary is an
     * instant rule inside the day: a Monday after a holiday weekend is a
     * valid start DATE (the execution gate still refuses 07:00 on it) —
     * this method answers the date question of the notice (the v1 case:
     * notice on 15/10 + 30 days falls on Saturday 14/11, so the first
     * start date is Monday 16/11).
     */
    public static LocalDate firstAllowedDateOnOrAfter(LocalDate from, CalendarData data) {
        LocalDate date = from;
        while (federalStartBanned(date, data.holidays())
                || (data.isSc() && scDayFullyForbidden(date, data))) {
            date = date.plusDays(1);
        }
        return date;
    }

    /** A date is fully forbidden for SC only when the whole day sits inside a ban window. */
    private static boolean scDayFullyForbidden(LocalDate date, CalendarData data) {
        return scForbiddenAt(date.atStartOfDay(), data)
                && scForbiddenAt(date.atTime(23, 59), data);
    }

    /**
     * First allowed instant at or after {@code from}: advances past
     * federal banned days (to the next day at 00:00) and past SC windows
     * (to the window end, e.g. Monday 08:00). Bounded scan: a calendar
     * that forbids 370 days in a row is a register error, not a schedule.
     */
    public static ZonedDateTime firstAllowedInstantOnOrAfter(ZonedDateTime from, CalendarData data) {
        ZonedDateTime cursor = from;
        for (int i = 0; i < 370; i++) {
            if (!isForbidden(cursor, data)) {
                return cursor;
            }
            if (federalStartBanned(cursor.toLocalDate(), data.holidays())) {
                cursor = cursor.toLocalDate().plusDays(1).atStartOfDay(cursor.getZone());
            } else {
                // Only the SC window can still forbid: jump to its end.
                cursor = scWindowEnd(cursor, data);
            }
        }
        throw new IllegalStateException("calendario veda 370 dias seguidos: conferir cal.holidays do tenant");
    }

    private static ZonedDateTime scWindowEnd(ZonedDateTime when, CalendarData data) {
        LocalDateTime local = when.toLocalDateTime();
        LocalDate date = when.toLocalDate();
        LocalDate friday = date.minusDays((date.getDayOfWeek().getValue() + 2) % 7);
        LocalDateTime weekEnd = friday.plusDays(3).atTime(SC_WINDOW_HOUR);
        if (!local.isBefore(friday.atTime(SC_WINDOW_HOUR)) && local.isBefore(weekEnd)) {
            return weekEnd.atZone(when.getZone());
        }
        for (LocalDate marked : data.nonBusinessDates()) {
            LocalDate prev = marked.minusDays(1);
            while (!isScBusinessDay(prev, data)) {
                prev = prev.minusDays(1);
            }
            LocalDate next = marked.plusDays(1);
            while (!isScBusinessDay(next, data)) {
                next = next.plusDays(1);
            }
            LocalDateTime start = prev.atTime(SC_WINDOW_HOUR);
            LocalDateTime end = next.atTime(SC_WINDOW_HOUR);
            if (!local.isBefore(start) && local.isBefore(end)) {
                return end.atZone(when.getZone());
            }
        }
        // Not inside any known window: advance one hour (defensive; the
        // caller bounded the loop).
        return when.plusHours(1);
    }
}
