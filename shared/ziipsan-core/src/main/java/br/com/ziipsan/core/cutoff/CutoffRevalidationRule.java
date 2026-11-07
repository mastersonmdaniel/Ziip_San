package br.com.ziipsan.core.cutoff;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

/**
 * Revalidation of the cutoff at execution (class a,
 * {@code cut.revalidate_on_execution}, 20d OS-07). Pure and offline by
 * construction: the field execution (ED-20) carries a snapshot of the
 * facts and asks this rule; the answer does not depend on connectivity.
 *
 * <p>Checks, in the order of the acceptance text — the first failure
 * wins and the work order is CANCELLED through the P1.31 engine
 * ({@code closeNotExecuted} with the close reason
 * {@link CutoffOrderTypes#REVALIDATION_CLOSE_REASON}):
 * <ol>
 *   <li>settlement or installment agreement of the motivating debts
 *       after the order was issued;</li>
 *   <li>a bill under review or contested;</li>
 *   <li>the temporal bans at the execution instant (judicial order and
 *       evidenced emergency exempt only this layer, as in the gate);</li>
 *   <li>a still-valid notice (proof, period elapsed, not expired) —
 *       debt reason only;</li>
 *   <li>a protected class acquired or confirmed after issuance
 *       (essential user / health-dependent: every reason; social
 *       tariff: debt reason, unless the agency allows).</li>
 * </ol>
 */
public final class CutoffRevalidationRule {

    private CutoffRevalidationRule() {
    }

    /** Snapshot of the facts at execution time (works offline, ED-20). */
    public record Snapshot(
            boolean motivatingDebtsSettledOrAgreed,
            boolean billUnderReviewOrContested,
            CutoffEnums.Reason reason,
            boolean judicialOrder,
            ZonedDateTime executionWhen,
            CutoffCalendar.CalendarData calendar,
            CutoffNoticeRule.Notice notice,
            Set<CutoffEnums.ProtectedClass> unitProtectedClasses,
            CutoffParameters.Values params) {
        public Snapshot {
            if (reason == null || executionWhen == null || calendar == null || params == null) {
                throw new IllegalArgumentException("motivo, instante, calendario e parametros sao obrigatorios");
            }
            unitProtectedClasses = unitProtectedClasses == null ? Set.of() : Set.copyOf(unitProtectedClasses);
        }
    }

    /** Empty when the cutoff may execute; otherwise the cancelling failure. */
    public static Optional<CutoffEnums.RevalidationFailure> evaluate(Snapshot snapshot) {
        if (snapshot.motivatingDebtsSettledOrAgreed()) {
            return Optional.of(CutoffEnums.RevalidationFailure.DEBT_SETTLED);
        }
        if (snapshot.billUnderReviewOrContested()) {
            return Optional.of(CutoffEnums.RevalidationFailure.BILL_UNDER_REVIEW_OR_CONTESTED);
        }
        boolean followsBans = snapshot.params().reasonFollowsTemporalBans(snapshot.reason())
                && snapshot.reason() != CutoffEnums.Reason.TECHNICAL_EMERGENCY;
        if (followsBans && !snapshot.judicialOrder()
                && CutoffCalendar.isForbidden(snapshot.executionWhen(), snapshot.calendar())) {
            return Optional.of(CutoffEnums.RevalidationFailure.TEMPORAL_BAN);
        }
        if (snapshot.reason() == CutoffEnums.Reason.DEFAULT) {
            if (snapshot.notice() == null) {
                throw new IllegalArgumentException("revalidacao de corte por inadimplencia exige o aviso");
            }
            var noticeFailure = CutoffNoticeRule.noticeFailure(
                    snapshot.notice(), snapshot.executionWhen().toLocalDate(), snapshot.params());
            if (noticeFailure.isPresent()) {
                return noticeFailure;
            }
        }
        if (snapshot.unitProtectedClasses().contains(CutoffEnums.ProtectedClass.ESSENTIAL_USER)
                || snapshot.unitProtectedClasses().contains(CutoffEnums.ProtectedClass.HEALTH_DEPENDENT)) {
            return Optional.of(CutoffEnums.RevalidationFailure.PROTECTED_CLASS);
        }
        if (snapshot.reason() == CutoffEnums.Reason.DEFAULT
                && snapshot.unitProtectedClasses().contains(CutoffEnums.ProtectedClass.SOCIAL_TARIFF)
                && snapshot.params().protectedClasses().contains(CutoffEnums.ProtectedClass.SOCIAL_TARIFF)
                && !snapshot.params().socialTariffCutAllowed()) {
            return Optional.of(CutoffEnums.RevalidationFailure.PROTECTED_CLASS);
        }
        return Optional.empty();
    }
}
