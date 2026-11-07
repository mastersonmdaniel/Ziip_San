package br.com.ziipsan.commercial.billing.bill;

import br.com.ziipsan.core.parameters.ParameterLimits;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

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

    // ------------------------------------------------------------------------------------------
    // P2.5 (RAS-24): opcoes de vencimento do tenant e antecedencia entrega x vencimento
    // ------------------------------------------------------------------------------------------

    /**
     * Valida {@code bill.due_date_options} com a trava do produto (P0.20):
     * dias 1..31 distintos e, para concessionária, ao menos 6 datas
     * (Lei 8.987/1995, art. 7º-A, incluído pela Lei 9.791/1999) — uma
     * configuração com 5 datas é recusada. A lista só amplia em relação à
     * aceita anteriormente.
     */
    public static List<Integer> validateDueDateOptions(List<Integer> days, boolean concessionaire,
                                                       Integer previousAcceptedCount) {
        ParameterLimits.validateDueDateOptions(days, concessionaire, previousAcceptedCount);
        return List.copyOf(days);
    }

    /**
     * Vencimento a partir das opções do tenant ({@code bill.due_date_options}) e da
     * antecedência mínima entre entrega e vencimento
     * ({@code bill.min_days_delivery_to_due}, resolvida por categoria pelo chamador).
     *
     * <pre>
     *   sem dia escolhido:  candidato = vencimento da rota
     *   com dia escolhido:  o dia precisa estar entre as opções ofertadas (senão recusa);
     *                       candidato = dia escolhido no mês da rota (último dia do mês, se não existir)
     *   antecedência:       se candidato &lt; entrega + minDays, o vencimento avança para a
     *                       ocorrência seguinte do mesmo dia (mês seguinte, com o mesmo
     *                       ajuste), repetindo mês a mês até satisfazer a antecedência --
     *                       com minDays maior que o mês, uma ocorrência só não basta
     * </pre>
     *
     * @param routeDueDate         vencimento da rota no ciclo
     * @param chosenDay            dia escolhido pelo cliente dentre as opções; {@code null} = dia da rota
     * @param options              {@code bill.due_date_options} vigente (validado aqui)
     * @param concessionaire       o tenant é concessionária (vale o mínimo de 6 datas)
     * @param deliveryDate         data de entrega da conta ao cliente
     * @param minDaysDeliveryToDue antecedência mínima, em dias, entre entrega e vencimento
     */
    public static LocalDate dueDateFromOptions(LocalDate routeDueDate, Integer chosenDay, List<Integer> options,
                                               boolean concessionaire, LocalDate deliveryDate,
                                               int minDaysDeliveryToDue) {
        validateDueDateOptions(options, concessionaire, null);
        if (minDaysDeliveryToDue < 0) {
            throw new IllegalArgumentException(
                    "bill.min_days_delivery_to_due não pode ser negativo: " + minDaysDeliveryToDue);
        }
        LocalDate candidate;
        if (chosenDay == null) {
            candidate = routeDueDate;
        } else {
            if (!options.contains(chosenDay)) {
                throw new IllegalArgumentException(
                        "Dia de vencimento " + chosenDay + " fora de bill.due_date_options: " + options);
            }
            candidate = clampedDay(YearMonth.from(routeDueDate), chosenDay);
        }
        LocalDate earliest = deliveryDate.plusDays(minDaysDeliveryToDue);
        int day = chosenDay == null ? routeDueDate.getDayOfMonth() : chosenDay;
        // Avanca mes a mes (nunca o mesmo mes duas vezes: cada passo cai num mes
        // estritamente posterior, logo o laco termina) ate a antecedencia valer.
        while (candidate.isBefore(earliest)) {
            candidate = clampedDay(YearMonth.from(candidate).plusMonths(1), day);
        }
        return candidate;
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
