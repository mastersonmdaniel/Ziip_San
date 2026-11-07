package br.com.ziipsan.commercial.collection.ampliada;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Fechamento mensal de arrecadacao CONGELADO (ED-26): fechado o mes, o total nao
 * muda - pagamento com data de mes congelado e recusado (nao entra em silencio
 * nem reprocessa o fechamento), no mesmo principio do fechamento contabil da
 * ED-32 ("sem reprocessamento que apague lancamentos").
 *
 * <p>{@code collectionMonth} segue a P3.1/DAD-19: a referencia e o mes do
 * RECEBIMENTO (dia 1), nao a competencia de faturamento da conta.
 */
public final class MonthlyClosing {

    /** Pagamento resumido para o fechamento: data real e liquido recebido. */
    public record ClosingPayment(LocalDate paidOn, BigDecimal netAmount) {
        public ClosingPayment {
            Objects.requireNonNull(paidOn, "data do pagamento e obrigatoria");
            if (netAmount == null) {
                throw new IllegalArgumentException("liquido do pagamento e obrigatorio");
            }
        }
    }

    /** Fotografia congelada do mes: total e quantidade nao mudam depois de criados. */
    public record FrozenMonth(YearMonth month, BigDecimal totalNet, int paymentCount) {
    }

    private MonthlyClosing() {
    }

    /** Recusa pagamento cuja referencia de arrecadacao ja esta congelada. */
    public static void assertMonthOpen(LocalDate paidOn, Set<YearMonth> frozenMonths) {
        Objects.requireNonNull(paidOn, "data do pagamento e obrigatoria");
        Objects.requireNonNull(frozenMonths, "conjunto de meses congelados e obrigatorio");
        YearMonth month = YearMonth.from(paidOn);
        if (frozenMonths.contains(month)) {
            throw new IllegalStateException("mes de arrecadacao " + month
                    + " ja esta fechado e congelado: pagamento de " + paidOn + " nao pode entrar nele");
        }
    }

    /** Fecha o mes somando SOMENTE pagamentos daquele mes; qualquer pagamento de
     *  outro mes na lista e erro explicito (fechamento nao mistura referencias). */
    public static FrozenMonth close(YearMonth month, List<ClosingPayment> payments) {
        Objects.requireNonNull(month, "mes do fechamento e obrigatorio");
        Objects.requireNonNull(payments, "pagamentos do fechamento sao obrigatorios");
        BigDecimal total = new BigDecimal("0.00");
        int count = 0;
        for (ClosingPayment p : payments) {
            if (!YearMonth.from(p.paidOn()).equals(month)) {
                throw new IllegalArgumentException("pagamento de " + p.paidOn()
                        + " nao pertence ao mes do fechamento " + month);
            }
            total = total.add(p.netAmount());
            count++;
        }
        return new FrozenMonth(month, total, count);
    }
}
