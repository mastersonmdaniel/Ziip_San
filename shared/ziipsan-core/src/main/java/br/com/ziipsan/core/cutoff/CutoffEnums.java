package br.com.ziipsan.core.cutoff;

/**
 * Small domains of cutoff and reconnection (P3.4; the V9_2 CHECKs mirror
 * them). The vocabularies come from the sources named on each enum —
 * nothing here is a free invention of this task.
 */
public final class CutoffEnums {

    private CutoffEnums() {
    }

    /**
     * Cutoff reasons ({@code cut.reasons}, PARAMETROS-DO-TENANT G9:
     * inadimplência, fraude, risco, pedido, emergência técnica).
     * {@link #DEFAULT} is the debt default reason: it always follows the
     * full rule (class a) — notice with delivery proof, temporal bans and
     * revalidation.
     */
    public enum Reason {
        DEFAULT,
        FRAUD,
        RISK,
        REQUEST,
        TECHNICAL_EMERGENCY
    }

    /** Cut level ({@code cut.level_model}): VALVE = cavalete, BRANCH = ramal. */
    public enum CutLevel {
        VALVE,
        BRANCH
    }

    /**
     * Protected classes of the P1.3 registry (CAD-80), the locked minimum
     * list of {@code cut.protected_classes} (Lei 11.445/2007, art. 40,
     * §3º): ESSENTIAL_USER covers the essential users of the registry
     * (hospital, school, collective internment — CNES/INEP/alvará);
     * HEALTH_DEPENDENT is the person dependent on continuous supply for a
     * health reason; SOCIAL_TARIFF is the social-tariff beneficiary
     * (Lei 14.898/2024).
     */
    public enum ProtectedClass {
        ESSENTIAL_USER,
        HEALTH_DEPENDENT,
        SOCIAL_TARIFF
    }

    /**
     * Why a cutoff notice was NOT generated for a unit. Recorded per unit
     * in {@code cutoff_non_generation_reasons} (the report is P3.22).
     */
    public enum NonGenerationReason {
        NO_OVERDUE_DEBT,
        DEBT_TOO_RECENT,
        DEBT_TOO_OLD,
        DEBT_BELOW_MINIMUM,
        CONNECTION_NOT_ACTIVE,
        PROTECTED_CLASS,
        SOCIAL_TARIFF_PROTECTED,
        BILL_UNDER_REVIEW_OR_CONTESTED,
        NOTICE_ALREADY_VALID
    }

    /**
     * Revalidation failures at execution time (class a,
     * {@code cut.revalidate_on_execution}): any one of them cancels the
     * work order through the P1.31 engine ({@code closeNotExecuted}).
     * The declaration order is the check order of
     * {@link CutoffRevalidationRule}, the order of the acceptance text:
     * settlement, contestation, temporal bans, valid notice, protected
     * class.
     */
    public enum RevalidationFailure {
        DEBT_SETTLED,
        BILL_UNDER_REVIEW_OR_CONTESTED,
        TEMPORAL_BAN,
        NOTICE_NOT_DELIVERED,
        NOTICE_PERIOD_NOT_ELAPSED,
        NOTICE_EXPIRED,
        PROTECTED_CLASS
    }

    /**
     * Delivery proof of the notice, in the semantics of P3.5
     * ({@code DeliveryProofRule}): only {@link #DELIVERED} is proof;
     * attempts that were only sent, bounced or complained count as
     * {@link #NOT_DELIVERED}; {@link #NONE} is no concluded attempt.
     */
    public enum DeliveryProofStatus {
        NONE,
        DELIVERED,
        NOT_DELIVERED
    }

    /** Connection situation of the unit, as read from the registry (P1.3). */
    public enum ConnectionStatus {
        ACTIVE,
        CUT,
        SUPPRESSED,
        INACTIVE
    }

    /**
     * Reconnection release condition ({@code rec.release_condition},
     * class b; factory default {@link #MOTIVATING_DEBT_PAID_OR_AGREED}).
     */
    public enum ReleaseCondition {
        MOTIVATING_DEBT_PAID_OR_AGREED,
        DOWN_PAYMENT_PAID,
        FULL_SETTLEMENT
    }

    /**
     * Formula of {@code rec.undue_cut_compensation} (class b), in the
     * exact value vocabulary the P2.31 profiles carry: ARESC uses the
     * FIRST invoice after reconnection, AGIR the LAST invoice, ARIS has
     * no money credit (reconnection in 4 h, no charge), and the tenant
     * may choose a manual credit with approval.
     */
    public enum UndueCompensationFormula {
        GREATER_OF_2X_URGENT_FEE_AND_20PCT_LAST_INVOICE,
        GREATER_OF_2X_URGENT_FEE_AND_20PCT_FIRST_INVOICE_AFTER_RECONNECTION,
        RECONNECTION_4H_NO_CHARGE,
        MANUAL_CREDIT_WITH_APPROVAL
    }

    /** What triggered an automatic compensation (V9_2 CHECK mirrors it). */
    public enum CompensationTrigger {
        UNDUE_CUT,
        LATE_RECONNECTION
    }

    /** Fee decision of a reconnection order (V9_2 CHECK mirrors it). */
    public enum FeeDecision {
        CHARGED,
        /** Notice without delivery proof: no reconnection fee (Lei 13.460/2017, art. 6º, p.u.). */
        WAIVED_NO_PROOF,
        WAIVED_UNDUE_CUT
    }
}
