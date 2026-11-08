package br.com.ziipsan.commercial.collection.activedebt;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Saldo mensal CONGELADO da divida ativa (ED-31): o fechamento de um mes
 * soma as inscricoes daquele mes e congela — reprocessar devolve exatamente
 * o mesmo saldo (nada e apagado nem recalculado), no mesmo principio do
 * fechamento congelado da ED-32 e do fechamento de arrecadacao da ED-26.
 * Inscricao com data de mes congelado e recusada (ver
 * ActiveDebtEnrollment.enrollIndividual).
 */
public final class ActiveDebtMonthlyClosing {

    public record FrozenBalance(YearMonth month, BigDecimal totalEnrolled, int enrollmentCount) {}

    private ActiveDebtMonthlyClosing() {}

    public static FrozenBalance close(YearMonth month, List<ActiveDebtEnrollment.Enrollment> enrollments) {
        Objects.requireNonNull(month, "mes e obrigatorio");
        Objects.requireNonNull(enrollments, "inscricoes sao obrigatorias");
        BigDecimal total = BigDecimal.ZERO; int count = 0;
        for (var e : enrollments) {
            if (!YearMonth.from(e.enrolledOn()).equals(month))
                throw new IllegalArgumentException("inscricao de " + e.enrolledOn() + " nao pertence ao mes " + month);
            total = total.add(e.total()); count++;
        }
        return new FrozenBalance(month, total, count);
    }

    /** Reprocessamento de mes congelado: no-op que preserva o saldo congelado. */
    public static FrozenBalance reprocess(FrozenBalance frozen, List<ActiveDebtEnrollment.Enrollment> ignoredRecomputed) {
        Objects.requireNonNull(frozen, "saldo congelado e obrigatorio");
        return frozen;
    }

    public static void assertMonthOpen(java.time.LocalDate date, Set<YearMonth> frozenMonths) {
        Objects.requireNonNull(date, "data e obrigatoria");
        Objects.requireNonNull(frozenMonths, "meses congelados sao obrigatorios");
        if (frozenMonths.contains(YearMonth.from(date)))
            throw new IllegalStateException("mes " + YearMonth.from(date) + " congelado: operacao recusada");
    }
}
