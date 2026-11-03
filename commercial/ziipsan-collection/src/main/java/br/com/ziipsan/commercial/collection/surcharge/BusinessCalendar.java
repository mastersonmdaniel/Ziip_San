package br.com.ziipsan.commercial.collection.surcharge;

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
     * Natureza do registro em {@code cal.holidays} (V1_5, P0.19). Ponto facultativo é
     * registrado, mas por padrão NÃO conta como dia não útil: se o ponto facultativo
     * vale para o calendário do tenant (corte, SLA) é decisão do tenant/ERI, aplicada
     * nas fases que consomem o calendário (P2.15, P3.4), não travada aqui.
     */
    public enum HolidayKind { HOLIDAY, OPTIONAL_POINT }

    /**
     * Feriado cadastrado. {@code stateCode} (UF) é obrigatório no escopo estadual e
     * {@code municipalityIbge} no municipal. O construtor de 4 argumentos é o feriado
     * de sempre (kind {@code HOLIDAY}); ponto facultativo usa o de 5 argumentos.
     */
    public record HolidayEntry(LocalDate date, Scope scope, String stateCode, String municipalityIbge,
                               HolidayKind kind) {
        public HolidayEntry(LocalDate date, Scope scope, String stateCode, String municipalityIbge) {
            this(date, scope, stateCode, municipalityIbge, HolidayKind.HOLIDAY);
        }

        public HolidayEntry {
            if (date == null || scope == null) {
                throw new IllegalArgumentException("Data e escopo do feriado são obrigatórios");
            }
            if (kind == null) {
                throw new IllegalArgumentException("A natureza do registro (feriado ou ponto facultativo) é obrigatória");
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
     * informado. Entradas de outros estados ou municípios são ignoradas. Pontos
     * facultativos NÃO contam como dia não útil aqui (comportamento padrão da V1_5).
     */
    public static BusinessCalendar forMunicipality(Collection<HolidayEntry> entries, String stateCode,
                                                   String municipalityIbge) {
        return forMunicipality(entries, stateCode, municipalityIbge, false);
    }

    /**
     * Como {@link #forMunicipality(Collection, String, String)}, mas com
     * {@code includeOptionalPoints = true} os pontos facultativos do município/UF
     * também contam como dia não útil — a escolha do tenant para o seu calendário.
     */
    public static BusinessCalendar forMunicipality(Collection<HolidayEntry> entries, String stateCode,
                                                   String municipalityIbge, boolean includeOptionalPoints) {
        Set<LocalDate> days = new HashSet<>();
        for (HolidayEntry e : entries) {
            boolean applies = switch (e.scope()) {
                case NATIONAL -> true;
                case STATE -> e.stateCode().equalsIgnoreCase(stateCode);
                case MUNICIPAL -> e.municipalityIbge().equals(municipalityIbge);
            };
            if (applies && (includeOptionalPoints || e.kind() == HolidayKind.HOLIDAY)) {
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
