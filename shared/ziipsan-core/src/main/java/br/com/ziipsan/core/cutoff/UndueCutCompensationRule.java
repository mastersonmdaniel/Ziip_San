package br.com.ziipsan.core.cutoff;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

/**
 * Automatic compensation for an undue cutoff or a late reconnection
 * (P3.4), by the formula of {@code rec.undue_cut_compensation} chosen in
 * the agency profile (P2.31) or the tenant parameter. Pure; money is
 * {@code BigDecimal} at 2 places, HALF_UP.
 *
 * <ul>
 *   <li><b>ARESC/AGIR family:</b> credit = the GREATER of 2× the urgent
 *       reconnection fee and 20% of the invoice — the LAST invoice for
 *       AGIR ({@code GREATER_OF_2X_URGENT_FEE_AND_20PCT_LAST_INVOICE}),
 *       the FIRST invoice AFTER the reconnection for ARESC. When that
 *       first invoice does not exist yet, the result is PENDING_BASIS
 *       with NO amount: the credit is computed when the invoice exists,
 *       never estimated.</li>
 *   <li><b>ARIS:</b> {@code RECONNECTION_4H_NO_CHARGE} — no money credit;
 *       the effect is the deadline: reconnection in 4 h, no charge.</li>
 *   <li><b>Manual:</b> {@code MANUAL_CREDIT_WITH_APPROVAL} — the amount
 *       is given by a person and the credit only exists with an approver
 *       different from the proposer (the P0.19 separation, applied by
 *       the caller through {@link #assertManualApproval}).</li>
 * </ul>
 *
 * <p>Missing inputs are explicit errors (absent urgent fee, absent
 * invoice where the formula needs one), never a zero credit.
 */
public final class UndueCutCompensationRule {

    private UndueCutCompensationRule() {
    }

    public enum CompensationStatus {
        PENDING_BASIS,
        PENDING_APPROVAL,
        CREDITED
    }

    public record Compensation(
            CutoffEnums.UndueCompensationFormula formula,
            CompensationStatus status,
            BigDecimal creditAmount,
            OffsetDateTime reconnectionDueAt,
            boolean reconnectionFeeWaived) {
    }

    /**
     * @param urgentFee {@code rec.urgent_fee} of the tenant (required by the GREATER_OF formulas)
     * @param lastInvoiceAmount amount of the last invoice before the cutoff (AGIR basis)
     * @param firstInvoiceAfterReconnectionAmount amount of the first invoice after the
     *        reconnection, or null when it does not exist yet (ARESC basis)
     * @param manualAmount the human-given amount (manual formula only)
     * @param detectedAt when the undue cutoff / lateness was detected (ARIS deadline base)
     */
    public static Compensation compute(CutoffEnums.UndueCompensationFormula formula,
            BigDecimal urgentFee, BigDecimal lastInvoiceAmount,
            BigDecimal firstInvoiceAfterReconnectionAmount, BigDecimal manualAmount,
            OffsetDateTime detectedAt) {
        if (formula == null) {
            throw new IllegalArgumentException("formula de compensacao e obrigatoria");
        }
        return switch (formula) {
            case RECONNECTION_4H_NO_CHARGE -> {
                if (detectedAt == null) {
                    throw new IllegalArgumentException("a formula da ARIS exige o instante da constatacao");
                }
                yield new Compensation(formula, CompensationStatus.CREDITED, new BigDecimal("0.00"),
                        detectedAt.plusHours(4), true);
            }
            case GREATER_OF_2X_URGENT_FEE_AND_20PCT_LAST_INVOICE -> {
                requireUrgentFee(urgentFee);
                if (lastInvoiceAmount == null) {
                    throw new IllegalStateException("a formula exige a ultima fatura: sem ela, sem credito calculado");
                }
                yield credited(formula, greaterOf(urgentFee, lastInvoiceAmount));
            }
            case GREATER_OF_2X_URGENT_FEE_AND_20PCT_FIRST_INVOICE_AFTER_RECONNECTION -> {
                requireUrgentFee(urgentFee);
                if (firstInvoiceAfterReconnectionAmount == null) {
                    yield new Compensation(formula, CompensationStatus.PENDING_BASIS, null, null, false);
                }
                yield credited(formula, greaterOf(urgentFee, firstInvoiceAfterReconnectionAmount));
            }
            case MANUAL_CREDIT_WITH_APPROVAL -> {
                if (manualAmount == null) {
                    throw new IllegalStateException("credito manual exige o valor informado por pessoa");
                }
                yield new Compensation(formula, CompensationStatus.PENDING_APPROVAL,
                        money(manualAmount), null, false);
            }
        };
    }

    /** The manual credit is only credited with an approver different from the proposer. */
    public static Compensation approveManual(Compensation pending, java.util.UUID proposerId,
            java.util.UUID approverId) {
        if (pending.formula() != CutoffEnums.UndueCompensationFormula.MANUAL_CREDIT_WITH_APPROVAL
                || pending.status() != CompensationStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("so credito manual pendente de aprovacao pode ser aprovado aqui");
        }
        if (proposerId == null || approverId == null || proposerId.equals(approverId)) {
            throw new IllegalArgumentException("quem aprova o credito manual precisa ser outra pessoa");
        }
        return new Compensation(pending.formula(), CompensationStatus.CREDITED,
                pending.creditAmount(), pending.reconnectionDueAt(), pending.reconnectionFeeWaived());
    }

    private static Compensation credited(CutoffEnums.UndueCompensationFormula formula, BigDecimal amount) {
        return new Compensation(formula, CompensationStatus.CREDITED, amount, null, false);
    }

    private static BigDecimal greaterOf(BigDecimal urgentFee, BigDecimal invoiceAmount) {
        BigDecimal twiceFee = money(urgentFee.multiply(new BigDecimal("2")));
        BigDecimal twentyPct = money(invoiceAmount.multiply(new BigDecimal("0.20")));
        return twiceFee.compareTo(twentyPct) >= 0 ? twiceFee : twentyPct;
    }

    private static void requireUrgentFee(BigDecimal urgentFee) {
        if (urgentFee == null) {
            throw new IllegalStateException("rec.urgent_fee sem valor: a formula do maior entre 2x a urgencia"
                    + " e 20% da fatura nao e calculada sem a taxa de urgencia");
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
