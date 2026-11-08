package br.com.ziipsan.core.sac.kiosk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Relatorio de tempo de espera presencial (RAS-26) por UNIDADE DE
 * ATENDIMENTO e por DIA, contra os limites da Lei estadual SC
 * 12.465/2002 vindos de {@link WaitingLimitPolicy} (parametro com fabrica
 * 15/30). Espera = emissao -> inicio do atendimento, dos instantes
 * GRAVADOS na senha. Dentro do limite: espera &lt;= limite do dia.
 * Senha VIVA e ainda nao iniciada (WAITING/CALLED) NAO entra em
 * dentro/fora (nao ha espera completa): conta a parte em
 * {@code pendingStart}, para o relatorio nao esconder quem ainda
 * espera. Senha ENCERRADA sem inicio (NO_SHOW/CANCELLED — nunca
 * iniciara) NAO e pendente: conta a parte em
 * {@code closedWithoutStart} (correcao D3 da revisao), para nao
 * inflar o "ainda espera" nem esconder o encerramento.
 * {@code maxWaitMinutes} e o maior tempo de espera arredondado ao
 * minuto mais proximo (correcao D5 da revisao; antes truncava os
 * segundos, exibindo 15 para uma espera de 15min59s).
 */
public final class WaitingTimeReport {

    public record DayRow(UUID serviceUnitId, LocalDate date, int limitMinutes,
                         WaitingLimitPolicy.LimitKind limitKind,
                         int startedCount, int withinLimit, int outsideLimit,
                         BigDecimal withinPct, BigDecimal averageWaitMinutes,
                         long maxWaitMinutes, int pendingStart,
                         int closedWithoutStart) {}

    private WaitingTimeReport() {}

    public static List<DayRow> generate(List<QueueTicket> tickets, WaitingLimitPolicy policy) {
        if (tickets == null) throw new IllegalArgumentException("senhas obrigatorias");
        if (policy == null) throw new IllegalArgumentException("politica de limites obrigatoria");
        Map<String, List<QueueTicket>> byUnitDay = new TreeMap<>();
        for (QueueTicket t : tickets) {
            String key = t.serviceUnitId() + "|" + t.issuedAt().toLocalDate();
            byUnitDay.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }
        List<DayRow> rows = new ArrayList<>();
        for (List<QueueTicket> group : byUnitDay.values()) {
            QueueTicket first = group.get(0);
            LocalDate date = first.issuedAt().toLocalDate();
            WaitingLimitPolicy.AppliedLimit limit = policy.limitFor(date);
            int within = 0, outside = 0, pending = 0, closedWithoutStart = 0;
            long sumSeconds = 0, maxSeconds = 0;
            for (QueueTicket t : group) {
                if (t.serviceStarted()) {
                    long seconds = t.waitingTime().getSeconds();
                    sumSeconds += seconds;
                    maxSeconds = Math.max(maxSeconds, seconds);
                    if (seconds <= limit.minutes() * 60L) within++; else outside++;
                } else if (t.status() == QueueTicket.Status.WAITING
                        || t.status() == QueueTicket.Status.CALLED) {
                    pending++;
                } else {
                    // NO_SHOW / CANCELLED: encerrada sem inicio, nunca iniciara.
                    closedWithoutStart++;
                }
            }
            int started = within + outside;
            BigDecimal pct = started == 0 ? new BigDecimal("0.00")
                    : BigDecimal.valueOf(within * 100.0 / started).setScale(2, RoundingMode.HALF_UP);
            BigDecimal avg = started == 0 ? new BigDecimal("0.00")
                    : BigDecimal.valueOf(sumSeconds / 60.0 / started).setScale(2, RoundingMode.HALF_UP);
            rows.add(new DayRow(first.serviceUnitId(), date, limit.minutes(), limit.kind(),
                    started, within, outside, pct, avg,
                    Math.round(maxSeconds / 60.0), pending, closedWithoutStart));
        }
        rows.sort(Comparator.comparing(DayRow::serviceUnitId).thenComparing(DayRow::date));
        return List.copyOf(rows);
    }
}
