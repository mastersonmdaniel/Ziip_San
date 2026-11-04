package br.com.ziipsan.core.support;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Calculadora pura de prazos de SLA (P2.15; ADR-0020, ADR-0025).
 *
 * <p>Sem banco e sem relógio: quem chama carrega os feriados (o filtro por
 * esfera vive em {@link br.com.ziipsan.core.protocol.DeadlineCalculator},
 * e o controlador da fila carrega só os nacionais do schema do tenant —
 * sem localidade conhecida, só os nacionais contam, a mesma convenção) e
 * passa o conjunto de datas aqui. Nenhum feriado é embutido, nem o
 * nacional: feriado ausente do cadastro do tenant é dia útil.
 *
 * <p>Semântica, fixada aqui para que política, pausa e relatório concordem:
 * <ul>
 *   <li>BUSINESS (horas úteis): o prazo consome minutos só dentro da
 *       janela da política (padrão 8–18, hora local do tenant), em dias
 *       úteis (sem sábado, domingo ou feriado do conjunto). Um chamado
 *       aberto fora da janela começa a contar na próxima abertura da
 *       janela; um prazo que cruza o fim do dia continua no dia útil
 *       seguinte. Precisão de minuto: o instante de origem é truncado
 *       para o minuto, e os prazos caem em fronteiras de minuto.</li>
 *   <li>ROUND_THE_CLOCK (24x7): horas de relógio, sem salto de fim de
 *       semana ou feriado — o plantão da S1 não para no sábado.</li>
 *   <li>Pausa: {@link #businessMinutesBetween} mede só os minutos úteis
 *       de um intervalo; a retomada estende o prazo por esses minutos
 *       (pausa no fim de semana quase não estende o prazo, porque quase
 *       não há minuto útil nela — e é exatamente esse o sentido de
 *       "relógio pausado").</li>
 * </ul>
 */
public final class SlaDeadlineCalculator {

    private SlaDeadlineCalculator() {
    }

    /** Calendário da política (o mesmo vocabulário da coluna {@code calendar}). */
    public enum Calendar {
        BUSINESS,
        ROUND_THE_CLOCK
    }

    /** Valida a janela de horas úteis da política (fim depois do início). */
    public static void requireWindow(int startHour, int endHour) {
        if (startHour < 0 || startHour > 23 || endHour < 1 || endHour > 24 || endHour <= startHour) {
            throw new IllegalArgumentException(
                    "Janela de horas uteis invalida: inicio " + startHour + ", fim " + endHour + ".");
        }
    }

    /** Verdadeiro quando a data é dia útil no conjunto de feriados dado. */
    public static boolean isBusinessDay(LocalDate date, Set<LocalDate> holidays) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY
                && (holidays == null || !holidays.contains(date));
    }

    /**
     * Prazo de {@code hours} horas a partir de {@code from}, no calendário dado.
     * {@code hours} deve ser positivo; {@code from} é truncado para o minuto.
     */
    public static ZonedDateTime addHours(ZonedDateTime from, long hours, Calendar calendar,
                                         Set<LocalDate> holidays, int startHour, int endHour) {
        if (from == null) {
            throw new IllegalArgumentException("O instante de origem e obrigatorio");
        }
        if (hours <= 0) {
            throw new IllegalArgumentException("O prazo em horas deve ser maior que zero");
        }
        if (calendar == null) {
            throw new IllegalArgumentException("O calendario da politica e obrigatorio");
        }
        if (calendar == Calendar.ROUND_THE_CLOCK) {
            return from.truncatedTo(ChronoUnit.MINUTES).plusHours(hours);
        }
        requireWindow(startHour, endHour);
        return addBusinessMinutes(from, hours * 60, holidays, startHour, endHour);
    }

    /**
     * Soma {@code minutes} minutos úteis a partir de {@code from}.
     * Minutos fora da janela ou fora de dia útil não contam.
     */
    public static ZonedDateTime addBusinessMinutes(ZonedDateTime from, long minutes,
                                                   Set<LocalDate> holidays, int startHour, int endHour) {
        requireWindow(startHour, endHour);
        if (minutes < 0) {
            throw new IllegalArgumentException("Minutos uteis negativos");
        }
        ZonedDateTime cursor = from.truncatedTo(ChronoUnit.MINUTES);
        long remaining = minutes;
        while (remaining > 0) {
            cursor = nextBusinessMinute(cursor, holidays, startHour, endHour);
            LocalDate day = cursor.toLocalDate();
            LocalTime windowEnd = LocalTime.of(endHour, 0);
            long available = ChronoUnit.MINUTES.between(cursor.toLocalTime(), windowEnd);
            if (available <= 0) {
                cursor = nextBusinessDayStart(day, cursor.getZone(), startHour, holidays);
                continue;
            }
            long take = Math.min(remaining, available);
            cursor = cursor.plusMinutes(take);
            remaining -= take;
            if (remaining > 0) {
                cursor = nextBusinessDayStart(day, cursor.getZone(), startHour, holidays);
            }
        }
        return cursor;
    }

    /**
     * Minutos úteis dentro de {@code [start, end)}: o que a pausa "custa"
     * ao prazo quando a retomada estende o vencimento.
     */
    public static long businessMinutesBetween(ZonedDateTime start, ZonedDateTime end,
                                              Set<LocalDate> holidays, int startHour, int endHour) {
        requireWindow(startHour, endHour);
        if (start == null || end == null || !end.isAfter(start)) {
            return 0;
        }
        long count = 0;
        ZonedDateTime cursor = start.truncatedTo(ChronoUnit.MINUTES);
        ZonedDateTime limit = end.truncatedTo(ChronoUnit.MINUTES);
        while (cursor.isBefore(limit)) {
            if (isBusinessMinute(cursor, holidays, startHour, endHour)) {
                count++;
                cursor = cursor.plusMinutes(1);
            } else {
                cursor = nextBusinessMinute(cursor, holidays, startHour, endHour);
                if (cursor.isAfter(limit)) {
                    break;
                }
            }
        }
        return count;
    }

    private static boolean isBusinessMinute(ZonedDateTime instant, Set<LocalDate> holidays,
                                            int startHour, int endHour) {
        if (!isBusinessDay(instant.toLocalDate(), holidays)) {
            return false;
        }
        int minute = instant.getHour() * 60 + instant.getMinute();
        return minute >= startHour * 60 && minute < endHour * 60;
    }

    /** Primeiro minuto útil a partir de {@code instant} (ele mesmo, se já for útil). */
    private static ZonedDateTime nextBusinessMinute(ZonedDateTime instant, Set<LocalDate> holidays,
                                                    int startHour, int endHour) {
        ZonedDateTime cursor = instant;
        while (true) {
            if (!isBusinessDay(cursor.toLocalDate(), holidays)) {
                cursor = nextBusinessDayStart(cursor.toLocalDate(), cursor.getZone(), startHour, holidays);
                continue;
            }
            int minute = cursor.getHour() * 60 + cursor.getMinute();
            if (minute < startHour * 60) {
                return cursor.toLocalDate().atTime(startHour, 0).atZone(cursor.getZone());
            }
            if (minute >= endHour * 60) {
                cursor = nextBusinessDayStart(cursor.toLocalDate(), cursor.getZone(), startHour, holidays);
                continue;
            }
            return cursor;
        }
    }

    private static ZonedDateTime nextBusinessDayStart(LocalDate day, ZoneId zone, int startHour,
                                                      Set<LocalDate> holidays) {
        LocalDate next = day.plusDays(1);
        while (!isBusinessDay(next, holidays)) {
            next = next.plusDays(1);
        }
        return next.atTime(startHour, 0).atZone(zone);
    }

    /** Cópia defensiva do conjunto de feriados (nunca nulo para os chamadores). */
    public static Set<LocalDate> copyHolidays(Collection<LocalDate> holidays) {
        return holidays == null ? Set.of() : new HashSet<>(holidays);
    }
}
