package br.com.ziipsan.commercial.billing.bill;

import br.com.ziipsan.commercial.billing.math.Money;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Abate créditos a realizar da conta (relatório 02, seção 5.6), respeitando que <b>o crédito nunca
 * excede a conta</b> e que o excedente (resíduo) passa ao mês seguinte.
 *
 * <p>Para cada crédito, na ordem recebida e enquanto sobrar valor a cobrar:
 * <pre>
 *   parcelaDoMês = Installments.next(total, n, feitas)   (0 se todas as parcelas já foram)
 *   disponível   = parcelaDoMês + resíduo
 *   aplicado     = MIN(disponível, restante a cobrar)
 *   resíduo'     = disponível − aplicado
 * </pre>
 * Um crédito que não é alcançado (a conta já zerou) não avança: nem parcela nem resíduo mudam. Todos
 * os valores são somas e subtrações exatas de valores de 2 casas; não há arredondamento aqui.
 *
 * <p>Diferença consciente em relação ao legado: o legado tinha um ramo para "última parcela já
 * concedida na referência" (reprocessamento). Aqui o reprocessamento é tratado pela idempotência da
 * conta, não por esta regra.
 */
public final class CreditApplier {

    /** Créditos abatidos e total abatido. */
    public record Application(List<CreditLine> lines, BigDecimal totalApplied) {
        public Application {
            lines = List.copyOf(lines);
        }
    }

    private CreditApplier() {
    }

    /**
     * @param amountToCharge água + esgoto + débitos da conta (≥ 0)
     */
    public static Application apply(BigDecimal amountToCharge, List<CreditReceivable> credits) {
        if (amountToCharge == null || amountToCharge.signum() < 0) {
            throw new IllegalArgumentException("Valor a cobrar inválido: " + amountToCharge);
        }
        BigDecimal remaining = amountToCharge;
        BigDecimal totalApplied = Money.ZERO;
        List<CreditLine> lines = new ArrayList<>();
        for (CreditReceivable credit : credits) {
            if (remaining.signum() <= 0) {
                break;
            }
            boolean hasInstallment = credit.installmentsDone() < credit.installments();
            BigDecimal installment = hasInstallment
                    ? Installments.next(credit.total(), credit.installments(), credit.installmentsDone())
                    : Money.ZERO;
            BigDecimal available = installment.add(credit.residual());
            if (available.signum() <= 0) {
                continue; // nada a realizar neste crédito
            }
            BigDecimal applied = Money.min(available, remaining);
            BigDecimal residualAfter = available.subtract(applied);
            Integer installmentNo = hasInstallment ? credit.installmentsDone() + 1 : null;
            int doneAfter = hasInstallment ? credit.installmentsDone() + 1 : credit.installmentsDone();
            lines.add(new CreditLine(credit.id(), installmentNo, applied, residualAfter, doneAfter));
            remaining = remaining.subtract(applied);
            totalApplied = totalApplied.add(applied);
        }
        return new Application(lines, totalApplied);
    }
}
