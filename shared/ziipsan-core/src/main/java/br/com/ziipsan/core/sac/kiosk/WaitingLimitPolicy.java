package br.com.ziipsan.core.sac.kiosk;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Limite de espera aplicavel a um dia, contra a Lei SC 12.465/2002:
 * limite comum em dia util comum e limite ampliado em vespera e no dia
 * seguinte a feriado prolongado. Os valores chegam pelos PARAMETROS
 * validados em {@link KioskParameters} (fabrica 15/30 da lei catarinense)
 * e a classificacao do dia pelo {@link HolidayCalendar} do tenant.
 */
public record WaitingLimitPolicy(HolidayCalendar calendar, int businessDayLimitMinutes,
                                 int holidayAdjacentLimitMinutes) {

    public enum LimitKind { BUSINESS_DAY, HOLIDAY_ADJACENT }

    public record AppliedLimit(int minutes, LimitKind kind) {}

    public WaitingLimitPolicy {
        Objects.requireNonNull(calendar, "calendario obrigatorio");
        if (businessDayLimitMinutes <= 0 || holidayAdjacentLimitMinutes <= 0) {
            throw new IllegalArgumentException("limites de espera devem ser maiores que zero");
        }
    }

    public AppliedLimit limitFor(LocalDate date) {
        Objects.requireNonNull(date, "data obrigatoria");
        if (calendar.isHolidayAdjacent(date)) {
            return new AppliedLimit(holidayAdjacentLimitMinutes, LimitKind.HOLIDAY_ADJACENT);
        }
        return new AppliedLimit(businessDayLimitMinutes, LimitKind.BUSINESS_DAY);
    }
}
