package br.com.ziipsan.compliance.lgpd.incident;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/**
 * Prazos do CONTROLADOR perante a ANPD e os titulares (Res. CD/ANPD 15/2024), entregues a ele
 * nos modelos da P3.10: comunicação em <b>3 dias úteis</b> e complementação em
 * <b>20 dias úteis</b>, contados do conhecimento pelo controlador.
 *
 * <p>Dia útil aqui = segunda a sexta, fora os feriados informados. A lista de feriados é do
 * calendário do tenant (o motor de prazos/calendário é de outra tarefa); sem ela, contam-se
 * só os fins de semana — limitação declarada no relatório, não escondida.
 */
public final class AnpdDeadlines {

    /** Comunicação à ANPD e aos titulares: 3 dias úteis (Res. CD/ANPD 15/2024). */
    public static final int COMMUNICATION_BUSINESS_DAYS = 3;

    /** Complementação da comunicação: 20 dias úteis (Res. CD/ANPD 15/2024). */
    public static final int SUPPLEMENT_BUSINESS_DAYS = 20;

    private AnpdDeadlines() {
    }

    /** Soma dias úteis a partir de {@code from} (o dia inicial não conta; contagem da resolução). */
    public static LocalDate plusBusinessDays(LocalDate from, int businessDays, Set<LocalDate> holidays) {
        Objects.requireNonNull(from, "from é obrigatório");
        if (businessDays < 0) {
            throw new IllegalArgumentException("businessDays negativo: " + businessDays);
        }
        Set<LocalDate> safeHolidays = holidays == null ? Set.of() : holidays;
        LocalDate date = from;
        int remaining = businessDays;
        while (remaining > 0) {
            date = date.plusDays(1);
            if (isBusinessDay(date, safeHolidays)) {
                remaining--;
            }
        }
        return date;
    }

    /** Prazo da comunicação (3 dias úteis) a partir do conhecimento pelo controlador. */
    public static LocalDate communicationDeadline(LocalDate controllerKnownDate, Set<LocalDate> holidays) {
        return plusBusinessDays(controllerKnownDate, COMMUNICATION_BUSINESS_DAYS, holidays);
    }

    /** Prazo da complementação (20 dias úteis) a partir do conhecimento pelo controlador. */
    public static LocalDate supplementDeadline(LocalDate controllerKnownDate, Set<LocalDate> holidays) {
        return plusBusinessDays(controllerKnownDate, SUPPLEMENT_BUSINESS_DAYS, holidays);
    }

    private static boolean isBusinessDay(LocalDate date, Set<LocalDate> holidays) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY && !holidays.contains(date);
    }
}
