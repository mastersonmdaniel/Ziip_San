package br.com.sigsan.commercial.collection.surcharge;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Calendário de dias úteis: sábado, domingo e os feriados injetados não são dia útil.
 *
 * <p>Os feriados vêm do tenant (tabela {@code holidays}); este módulo <b>não traz feriado
 * nenhum</b> embutido, nem os nacionais. Um feriado omitido do cadastro vira dia útil e muda a
 * carência, então o cadastro é responsabilidade do tenant, com fonte registrada.
 */
public final class BusinessCalendar {

    public enum Scope { NATIONAL, STATE, MUNICIPAL }

    /**
     * Feriado cadastrado. {@code stateCode} (UF) é obrigatório no escopo estadual e
     * {@code municipalityIbge} no municipal.
     */
    public record HolidayEntry(LocalDate date, Scope scope, String stateCode, String municipalityIbge) {
        public HolidayEntry {
            if (date == null || scope == null) {
                throw new IllegalArgumentException("Data e escopo do feriado são obrigatórios");
            }
            if (scope == Scope.STATE && (stateCode == null || stateCode.isBlank())) {
                throw new IllegalArgumentException("Feriado estadual exige a UF");
            }
            if (scope == Scope.MUNICIPAL && (municipalityIbge == null || municipalityIbge.isBlank())) {
                throw new IllegalArgumentException("Feriado municipal exige o código IBGE do município");
            }
        }
    }

    private final Set<LocalDate> holidays;

    private BusinessCalendar(Set<LocalDate> holidays) {
        this.holidays = Set.copyOf(holidays);
    }

    /** Calendário com exatamente estes feriados. */
    public static BusinessCalendar of(Collection<LocalDate> holidays) {
        return new BusinessCalendar(new HashSet<>(holidays));
    }

    /**
     * Calendário de um município: feriados nacionais, os da UF informada e os do município
     * informado. Entradas de outros estados ou municípios são ignoradas.
     */
    public static BusinessCalendar forMunicipality(Collection<HolidayEntry> entries, String stateCode,
                                                   String municipalityIbge) {
        Set<LocalDate> days = new HashSet<>();
        for (HolidayEntry e : entries) {
            boolean applies = switch (e.scope()) {
                case NATIONAL -> true;
                case STATE -> e.stateCode().equalsIgnoreCase(stateCode);
                case MUNICIPAL -> e.municipalityIbge().equals(municipalityIbge);
            };
            if (applies) {
                days.add(e.date());
            }
        }
        return new BusinessCalendar(days);
    }

    public boolean isBusinessDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY && !holidays.contains(date);
    }

    /** Menor dia útil igual ou posterior à data. */
    public LocalDate nextBusinessDayOnOrAfter(LocalDate date) {
        LocalDate d = date;
        while (!isBusinessDay(d)) {
            d = d.plusDays(1);
        }
        return d;
    }

    /**
     * Quantidade de dias úteis {@code d} com {@code from < d <= to}. Zero se {@code to} não for
     * posterior a {@code from}.
     */
    public int businessDaysBetween(LocalDate from, LocalDate to) {
        int count = 0;
        for (LocalDate d = from.plusDays(1); !d.isAfter(to); d = d.plusDays(1)) {
            if (isBusinessDay(d)) {
                count++;
            }
        }
        return count;
    }
}
