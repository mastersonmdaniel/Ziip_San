package br.com.ziipsan.reporting.schedule;

import java.time.ZonedDateTime;

/**
 * Cron de 5 campos (minuto hora dia-do-mes mes dia-da-semana) no subconjunto
 * que o agendamento de relatorios usa: numero, '*', lista por virgula e
 * faixa a-b. Passo (/n) nao e aceito - expressao fora do subconjunto e
 * recusada na criacao do agendamento, nunca interpretada errado em silencio.
 */
public final class CronSchedule {

    private final int[] minutes;
    private final int[] hours;
    private final int[] daysOfMonth;
    private final int[] months;
    private final int[] daysOfWeek;

    private CronSchedule(String expression) {
        String[] fields = expression.trim().split("\\s+");
        if (fields.length != 5) {
            throw new IllegalArgumentException("Cron deve ter 5 campos: " + expression);
        }
        minutes = parse(fields[0], 0, 59);
        hours = parse(fields[1], 0, 23);
        daysOfMonth = parse(fields[2], 1, 31);
        months = parse(fields[3], 1, 12);
        daysOfWeek = parse(fields[4], 0, 6);
    }

    public static CronSchedule parse(String expression) {
        if (expression == null || expression.contains("/")) {
            throw new IllegalArgumentException("Expressao cron fora do subconjunto aceito: " + expression);
        }
        return new CronSchedule(expression);
    }

    private static int[] parse(String field, int min, int max) {
        java.util.TreeSet<Integer> values = new java.util.TreeSet<>();
        for (String part : field.split(",")) {
            if (part.equals("*")) {
                for (int v = min; v <= max; v++) {
                    values.add(v);
                }
            } else if (part.contains("-")) {
                String[] range = part.split("-");
                int from = Integer.parseInt(range[0]);
                int to = Integer.parseInt(range[1]);
                if (from < min || to > max || from > to) {
                    throw new IllegalArgumentException("Faixa invalida em cron: " + part);
                }
                for (int v = from; v <= to; v++) {
                    values.add(v);
                }
            } else {
                int v = Integer.parseInt(part);
                if (v < min || v > max) {
                    throw new IllegalArgumentException("Valor fora do campo em cron: " + part);
                }
                values.add(v);
            }
        }
        return values.stream().mapToInt(Integer::intValue).toArray();
    }

    private boolean contains(int[] field, int value) {
        for (int v : field) {
            if (v == value) {
                return true;
            }
        }
        return false;
    }

    /** Proxima execucao ESTRITAMENTE depois de {@code after} (busca minuto a
     *  minuto com teto de 366 dias - cron invalido na pratica falha aqui). */
    public ZonedDateTime nextAfter(ZonedDateTime after) {
        ZonedDateTime candidate = after.plusMinutes(1).withSecond(0).withNano(0);
        for (int i = 0; i < 366 * 24 * 60; i++) {
            if (contains(minutes, candidate.getMinute())
                    && contains(hours, candidate.getHour())
                    && contains(daysOfMonth, candidate.getDayOfMonth())
                    && contains(months, candidate.getMonthValue())
                    && contains(daysOfWeek, candidate.getDayOfWeek().getValue() % 7)) {
                return candidate;
            }
            candidate = candidate.plusMinutes(1);
        }
        throw new IllegalStateException("Cron sem execucao em 366 dias");
    }
}
