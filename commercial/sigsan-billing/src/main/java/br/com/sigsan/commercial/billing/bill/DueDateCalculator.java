package br.com.sigsan.commercial.billing.bill;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Vencimento da conta (relatório 02, seção 5.7). Reproduz a especificação do legado, inclusive o
 * comportamento que o próprio levantamento marca como "a decidir" (ver {@code V-03} nos testes e o
 * README): quando o dia alternativo do cliente é <b>anterior</b> ao dia de vencimento da rota, vale o
 * dia da rota, e o dia alternativo não é usado.
 *
 * <pre>
 *   sem dia alternativo:            vencimento da rota (+ dias extras de correio, se houver)
 *   dia alternativo, mesmo mês:     dia da rota ≤ dia alternativo → dia alternativo no mês da rota
 *                                   senão → dia da rota; se esse dia ≤ hoje + mínimo de dias, → mesmo
 *                                   dia da rota no mês seguinte
 *   dia alternativo, mês seguinte:  dia alternativo no mês seguinte ao da rota
 *   dia que não existe no mês (31 em fevereiro) vale o último dia do mês
 * </pre>
 */
public final class DueDateCalculator {

    private DueDateCalculator() {
    }

    /**
     * @param routeDueDate       vencimento da rota no ciclo
     * @param alternativeDay     dia de vencimento escolhido pelo cliente/imóvel; {@code null} = nenhum
     * @param nextMonth          o dia alternativo vale no mês seguinte ao da rota
     * @param today              data do faturamento
     * @param minDaysIssueToDue  mínimo de dias entre hoje e o vencimento
     * @param extraMailDays      dias extras de entrega por correio; {@code null} = entrega sem correio.
     *                           Só se soma quando não há dia alternativo
     */
    public static LocalDate dueDate(LocalDate routeDueDate, Integer alternativeDay, boolean nextMonth,
                                    LocalDate today, int minDaysIssueToDue, Integer extraMailDays) {
        if (alternativeDay != null && (alternativeDay < 1 || alternativeDay > 31)) {
            throw new IllegalArgumentException("Dia de vencimento fora de 1..31: " + alternativeDay);
        }
        if (alternativeDay == null) {
            return extraMailDays == null ? routeDueDate : routeDueDate.plusDays(extraMailDays);
        }
        if (nextMonth) {
            return clampedDay(YearMonth.from(routeDueDate).plusMonths(1), alternativeDay);
        }
        if (routeDueDate.getDayOfMonth() <= alternativeDay) {
            return clampedDay(YearMonth.from(routeDueDate), alternativeDay);
        }
        // dia alternativo anterior ao da rota: vale o dia da rota
        if (!routeDueDate.isAfter(today.plusDays(minDaysIssueToDue))) {
            return routeDueDate.plusMonths(1); // java.time ajusta ao último dia do mês quando preciso
        }
        return routeDueDate;
    }

    /** Validade da conta: último dia do mês de (vencimento + meses de validade). */
    public static LocalDate validUntil(LocalDate dueDate, int validityMonths) {
        if (validityMonths < 0) {
            throw new IllegalArgumentException("Meses de validade negativos: " + validityMonths);
        }
        return YearMonth.from(dueDate).plusMonths(validityMonths).atEndOfMonth();
    }

    private static LocalDate clampedDay(YearMonth month, int day) {
        return month.atDay(Math.min(day, month.lengthOfMonth()));
    }
}
