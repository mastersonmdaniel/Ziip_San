package br.com.ziipsan.core.cutoff;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Minimum selection of the cutoff notice AND of the collection document
 * (P3.4 acceptance: the same minimum selection feeds both), pure.
 *
 * <p>A unit is selected when ALL of these hold, checked in this order
 * (the first failure is the recorded non-generation reason):
 * <ol>
 *   <li>the connection is ACTIVE (a unit already cut, suppressed or
 *       inactive is not selected again);</li>
 *   <li>no bill of the unit is under review or contested (locked rule:
 *       NR 11, arts. 37–38; Lei 14.015/2020 — a bill in review is outside
 *       accrual, notice, cutoff and negativation);</li>
 *   <li>the unit is not in a protected class of the registry (P1.3,
 *       CAD-80): essential user or health-dependent never; social tariff
 *       unless {@code cut.social_tariff_allowed} was accepted for the
 *       agency;</li>
 *   <li>there is open overdue debt; at least one debt reached the
 *       minimum overdue age ({@code cut.min_overdue_days}, when the
 *       agency declares one — the ADASA case: at least 60 days);</li>
 *   <li>the motivating debts are not older than the debt-age ceiling
 *       ({@code cut.max_debt_age_days} of the agency profile — ARIS:
 *       120 days; debts past the ceiling are excluded, and if none is
 *       left the unit is not selected: DEBT_TOO_OLD);</li>
 *   <li>the selected total reaches {@code cut.min_debt_amount}, when
 *       declared.</li>
 * </ol>
 *
 * <p>Debt age is counted in calendar days from the due date to the
 * evaluation date. Only OPEN debts overdue on the evaluation date
 * (due date strictly before it) enter the selection; settled, agreed
 * (installment) or judicial debts are not motivating debts here.
 */
public final class NoticeSelectionRule {

    private NoticeSelectionRule() {
    }

    public enum DebtStatus {
        OPEN,
        SETTLED,
        IN_INSTALLMENT_AGREEMENT,
        JUDICIAL
    }

    /** One receivable of the unit (from billing, P2.5). */
    public record Debt(LocalDate dueDate, BigDecimal amount, DebtStatus status) {
        public Debt {
            if (dueDate == null || amount == null || status == null) {
                throw new IllegalArgumentException("debito exige vencimento, valor e situacao");
            }
        }

        /** Age in calendar days at the evaluation date (negative when not yet due). */
        public long ageDaysAt(LocalDate evaluationDate) {
            return ChronoUnit.DAYS.between(dueDate, evaluationDate);
        }
    }

    /** Facts of the unit, from the registry (P1.3) and billing (P2.5) ports. */
    public record UnitFacts(
            UUID consumerUnitId,
            CutoffEnums.ConnectionStatus connectionStatus,
            Set<CutoffEnums.ProtectedClass> protectedClasses,
            boolean billUnderReviewOrContested,
            List<Debt> debts) {
        public UnitFacts {
            if (consumerUnitId == null || connectionStatus == null) {
                throw new IllegalArgumentException("unidade e situacao da ligacao sao obrigatorias");
            }
            protectedClasses = protectedClasses == null ? Set.of() : Set.copyOf(protectedClasses);
            debts = debts == null ? List.of() : List.copyOf(debts);
        }
    }

    /** Result: selected debts (oldest first) or the non-generation reason. */
    public record Selection(
            boolean selected,
            CutoffEnums.NonGenerationReason nonGenerationReason,
            List<Debt> motivatingDebts,
            BigDecimal totalAmount,
            Long oldestDebtAgeDays) {
    }

    public static Selection evaluate(UnitFacts facts, CutoffParameters.Values params, LocalDate evaluationDate) {
        if (facts == null || params == null || evaluationDate == null) {
            throw new IllegalArgumentException("fatos, parametros e data de avaliacao sao obrigatorios");
        }
        if (facts.connectionStatus() != CutoffEnums.ConnectionStatus.ACTIVE) {
            return rejected(CutoffEnums.NonGenerationReason.CONNECTION_NOT_ACTIVE);
        }
        if (facts.billUnderReviewOrContested()) {
            return rejected(CutoffEnums.NonGenerationReason.BILL_UNDER_REVIEW_OR_CONTESTED);
        }
        boolean essentialOrHealth = facts.protectedClasses().contains(CutoffEnums.ProtectedClass.ESSENTIAL_USER)
                || facts.protectedClasses().contains(CutoffEnums.ProtectedClass.HEALTH_DEPENDENT);
        if (essentialOrHealth) {
            return rejected(CutoffEnums.NonGenerationReason.PROTECTED_CLASS);
        }
        if (facts.protectedClasses().contains(CutoffEnums.ProtectedClass.SOCIAL_TARIFF)
                && params.protectedClasses().contains(CutoffEnums.ProtectedClass.SOCIAL_TARIFF)
                && !params.socialTariffCutAllowed()) {
            return rejected(CutoffEnums.NonGenerationReason.SOCIAL_TARIFF_PROTECTED);
        }
        List<Debt> overdue = new ArrayList<>();
        for (Debt debt : facts.debts()) {
            if (debt.status() == DebtStatus.OPEN && debt.dueDate().isBefore(evaluationDate)) {
                overdue.add(debt);
            }
        }
        if (overdue.isEmpty()) {
            return rejected(CutoffEnums.NonGenerationReason.NO_OVERDUE_DEBT);
        }
        if (params.minOverdueDays() != null) {
            boolean anyOldEnough = overdue.stream()
                    .anyMatch(d -> d.ageDaysAt(evaluationDate) >= params.minOverdueDays());
            if (!anyOldEnough) {
                return rejected(CutoffEnums.NonGenerationReason.DEBT_TOO_RECENT);
            }
        }
        List<Debt> withinCeiling = new ArrayList<>();
        for (Debt debt : overdue) {
            if (params.maxDebtAgeDays() == null
                    || debt.ageDaysAt(evaluationDate) <= params.maxDebtAgeDays()) {
                withinCeiling.add(debt);
            }
        }
        if (withinCeiling.isEmpty()) {
            return rejected(CutoffEnums.NonGenerationReason.DEBT_TOO_OLD);
        }
        withinCeiling.sort(Comparator.comparing(Debt::dueDate));
        BigDecimal total = withinCeiling.stream().map(Debt::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (params.minDebtAmount() != null && total.compareTo(params.minDebtAmount()) < 0) {
            return rejected(CutoffEnums.NonGenerationReason.DEBT_BELOW_MINIMUM);
        }
        long oldest = withinCeiling.get(0).ageDaysAt(evaluationDate);
        return new Selection(true, null, List.copyOf(withinCeiling), total, oldest);
    }

    private static Selection rejected(CutoffEnums.NonGenerationReason reason) {
        return new Selection(false, reason, List.of(), BigDecimal.ZERO, null);
    }
}
