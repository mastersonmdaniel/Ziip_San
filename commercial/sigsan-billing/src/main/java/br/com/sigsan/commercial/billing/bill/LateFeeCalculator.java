package br.com.sigsan.commercial.billing.bill;

import br.com.sigsan.commercial.billing.math.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.function.Predicate;

/**
 * Multa e juros por impontualidade (relatório 02, seção 4.7), com uma diferença deliberada em
 * relação ao legado: <b>juros contam dias, não meses de calendário</b>. O legado cobrava um mês
 * inteiro de juros para quem pagava 1 dia depois, se o vencimento fosse no fim do mês (vencimento
 * 31/01, pago 01/02); aqui os juros são pro rata die.
 *
 * <pre>
 *   dias        = pagamento − vencimento (dias corridos)
 *   carência    : se os dias úteis em (vencimento, pagamento] ≤ carência → tudo zero
 *   multa FLAT      = (conta − multasEmbutidas) × finePct / 100             (0 se já cobrada)
 *   multa PER_DAY   = (conta − multasEmbutidas) × MIN(finePct × dias, fineLimitPct) / 100
 *   juros           = conta × MIN(interestPct × dias / períodoDias, limiteJuros) / 100
 * </pre>
 * Cada valor é calculado numa única divisão final com {@link RoundingMode#HALF_UP}, 2 casas.
 * Correção monetária não está implementada (depende de fonte de índices; ver README).
 */
public final class LateFeeCalculator {

    /** Multa, juros e total devidos. */
    public record Result(BigDecimal fine, BigDecimal interest, BigDecimal total) {
    }

    private LateFeeCalculator() {
    }

    /**
     * @param amount             valor da conta em atraso
     * @param fineAlreadyCharged a multa desta conta já foi cobrada antes: não se cobra de novo (os juros seguem)
     * @param finesIncludedInAmount multas que já fazem parte de {@code amount} (0 se nenhuma); saem da base da multa
     * @param dueDate            vencimento
     * @param paymentDate        data do pagamento
     * @param businessDay        calendário de dias úteis do município (feriados incluídos)
     * @param directDebit        pagamento por débito automático: não calcula acréscimo
     */
    public static Result calculate(BigDecimal amount, boolean fineAlreadyCharged, BigDecimal finesIncludedInAmount, LocalDate dueDate,
                                   LocalDate paymentDate, LateFeePolicy policy, Predicate<LocalDate> businessDay,
                                   boolean directDebit) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Valor da conta inválido: " + amount);
        }
        if (finesIncludedInAmount == null || finesIncludedInAmount.signum() < 0) {
            throw new IllegalArgumentException("Multas embutidas no valor devem ser informadas (use 0): " + finesIncludedInAmount);
        }
        long days = ChronoUnit.DAYS.between(dueDate, paymentDate);
        if (directDebit || days <= 0 || businessDaysAfter(dueDate, paymentDate, businessDay) <= policy.graceBusinessDays()) {
            return new Result(Money.ZERO, Money.ZERO, Money.ZERO);
        }

        BigDecimal fine = Money.ZERO;
        if (!fineAlreadyCharged) {
            BigDecimal fineBase = amount.subtract(finesIncludedInAmount).max(BigDecimal.ZERO);
            BigDecimal finePct = policy.fineMode() == LateFeePolicy.FineMode.FLAT
                    ? policy.finePct()
                    : policy.finePct().multiply(BigDecimal.valueOf(days)).min(policy.fineLimitPct());
            fine = Money.percentOf(fineBase, finePct, RoundingMode.HALF_UP);
        }

        // juros: pct × dias / período, e só então × valor / 100 (uma divisão no fim)
        BigDecimal interest;
        BigDecimal rawPct = policy.interestPct().multiply(BigDecimal.valueOf(days));
        if (policy.interestLimitPct() == null) {
            interest = amount.multiply(rawPct)
                    .divide(BigDecimal.valueOf(100L * policy.interestPeriodDays()), Money.SCALE, RoundingMode.HALF_UP);
        } else {
            BigDecimal cappedAsPeriodPct = policy.interestLimitPct().multiply(BigDecimal.valueOf(policy.interestPeriodDays()));
            if (rawPct.compareTo(cappedAsPeriodPct) > 0) {
                interest = Money.percentOf(amount, policy.interestLimitPct(), RoundingMode.HALF_UP);
            } else {
                interest = amount.multiply(rawPct)
                        .divide(BigDecimal.valueOf(100L * policy.interestPeriodDays()), Money.SCALE, RoundingMode.HALF_UP);
            }
        }
        return new Result(fine, interest, fine.add(interest));
    }

    /** Dias úteis em {@code (from, to]}. */
    static long businessDaysAfter(LocalDate from, LocalDate to, Predicate<LocalDate> businessDay) {
        long count = 0;
        for (LocalDate d = from.plusDays(1); !d.isAfter(to); d = d.plusDays(1)) {
            if (businessDay.test(d)) {
                count++;
            }
        }
        return count;
    }
}
