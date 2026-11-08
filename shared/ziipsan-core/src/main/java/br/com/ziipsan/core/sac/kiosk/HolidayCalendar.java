package br.com.ziipsan.core.sac.kiosk;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/**
 * Calendario de feriados PARAMETRIZAVEL: a lista de feriados e DADO do
 * tenant (calendario oficial carregado na implantacao), entregue pelo
 * chamador — nenhuma data de feriado esta escrita neste codigo.
 * Dia util = segunda a sexta que nao seja feriado do calendario.
 * Feriado PROLONGADO = feriado cuja sequencia maximal de dias nao uteis
 * consecutivos que o contem tem 3 ou mais dias (emenda com o fim de
 * semana ou com outro feriado) — e so para ele valem a vespera e o dia
 * seguinte com o limite ampliado da Lei SC 12.465/2002.
 */
public interface HolidayCalendar {

    boolean isHoliday(LocalDate date);

    static HolidayCalendar of(Set<LocalDate> holidays) {
        Objects.requireNonNull(holidays, "calendario de feriados obrigatorio");
        Set<LocalDate> copy = Set.copyOf(holidays);
        return copy::contains;
    }

    default boolean isBusinessDay(LocalDate date) {
        Objects.requireNonNull(date, "data obrigatoria");
        DayOfWeek dow = date.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY && !isHoliday(date);
    }

    default boolean isProlongedHoliday(LocalDate date) {
        if (!isHoliday(date)) return false;
        int streak = 1;
        LocalDate d = date.minusDays(1);
        while (!isBusinessDay(d)) { streak++; d = d.minusDays(1); }
        d = date.plusDays(1);
        while (!isBusinessDay(d)) { streak++; d = d.plusDays(1); }
        return streak >= 3;
    }

    /**
     * Vespera de feriado prolongado OU dia seguinte a ele. A vespera e o
     * ultimo dia util antes do feriado prolongado e o dia seguinte e o
     * primeiro dia util depois dele: entre o dia util e o feriado pode
     * haver o fim de semana da emenda (ex.: feriado na segunda — a
     * vespera e a sexta anterior, nao o domingo). Por isso a busca anda
     * sobre a sequencia de dias nao uteis vizinha, e nao so sobre o dia
     * imediatamente anterior/seguinte no calendario.
     */
    default boolean isHolidayAdjacent(LocalDate date) {
        LocalDate d = date.plusDays(1);
        while (!isBusinessDay(d)) {
            if (isProlongedHoliday(d)) return true;
            d = d.plusDays(1);
        }
        LocalDate b = date.minusDays(1);
        while (!isBusinessDay(b)) {
            if (isProlongedHoliday(b)) return true;
            b = b.minusDays(1);
        }
        return false;
    }
}
