package br.com.ziipsan.core.cutoff;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * The cutoff notice itself (P3.4): issued from the P2.30 template, proven
 * by the P3.5 delivery proof, and the source of the two dates the rest
 * of the flow lives by — the earliest cut date and the post-cut notice
 * deadline. Pure.
 *
 * <ul>
 *   <li><b>Earliest cut date:</b> notice date + {@code cut.min_notice_days}
 *       (calendar days; floor 30, Lei 11.445/2007, art. 40, §2º), advanced
 *       past the banned start days of {@link CutoffCalendar}. The v1
 *       case: notice on Thursday 15/10/2026 + 30 days = Saturday
 *       14/11/2026 (banned), so the first cut date is Monday
 *       16/11/2026.</li>
 *   <li><b>Validity:</b> when the agency declares
 *       {@code cut.notice_validity_days}, a notice not executed by
 *       notice date + validity is expired and a new notice is required.</li>
 *   <li><b>Proof:</b> without DELIVERED proof the notice does not sustain
 *       a cutoff, at issuance of the order and again at execution.</li>
 *   <li><b>Post-cut notice:</b> due at execution instant +
 *       {@code cut.post_cut_notice_hours}; without an accepted value for
 *       the parameter the deadline is NOT computed (explicit error —
 *       the parameter has no factory default in the sources).</li>
 * </ul>
 */
public final class CutoffNoticeRule {

    private CutoffNoticeRule() {
    }

    /** The notice as recorded in {@code cutoff_notices} (V9_2). */
    public record Notice(
            UUID id,
            UUID consumerUnitId,
            LocalDate noticeDate,
            LocalDate earliestCutDate,
            String templateCode,
            String templateVersion,
            CutoffEnums.DeliveryProofStatus deliveryProofStatus) {
        public Notice {
            if (consumerUnitId == null || noticeDate == null || earliestCutDate == null
                    || templateCode == null || templateVersion == null || deliveryProofStatus == null) {
                throw new IllegalArgumentException("aviso exige unidade, datas, modelo/versao e estado da prova");
            }
        }

        public boolean hasDeliveryProof() {
            return deliveryProofStatus == CutoffEnums.DeliveryProofStatus.DELIVERED;
        }

        /** Last valid execution date, when the agency declares a validity; empty otherwise. */
        public Optional<LocalDate> validityEnd(CutoffParameters.Values params) {
            if (params.noticeValidityDays() == null) {
                return Optional.empty();
            }
            return Optional.of(noticeDate.plusDays(params.noticeValidityDays()));
        }

        public boolean isExpiredAt(LocalDate date, CutoffParameters.Values params) {
            return validityEnd(params).map(end -> date.isAfter(end)).orElse(false);
        }
    }

    /** Earliest cut date of a notice issued on {@code noticeDate}. */
    public static LocalDate earliestCutDate(LocalDate noticeDate, CutoffParameters.Values params,
            CutoffCalendar.CalendarData calendar) {
        if (noticeDate == null || params == null || calendar == null) {
            throw new IllegalArgumentException("data do aviso, parametros e calendario sao obrigatorios");
        }
        LocalDate raw = noticeDate.plusDays(params.effectiveMinNoticeDays());
        return CutoffCalendar.firstAllowedDateOnOrAfter(raw, calendar);
    }

    /**
     * Why this notice cannot sustain a cutoff executed on
     * {@code executionDate}, or empty when it can. Proof is checked
     * first: an undelivered notice is not a notice at all for this rule.
     */
    public static Optional<CutoffEnums.RevalidationFailure> noticeFailure(
            Notice notice, LocalDate executionDate, CutoffParameters.Values params) {
        if (!notice.hasDeliveryProof()) {
            return Optional.of(CutoffEnums.RevalidationFailure.NOTICE_NOT_DELIVERED);
        }
        if (executionDate.isBefore(notice.earliestCutDate())) {
            return Optional.of(CutoffEnums.RevalidationFailure.NOTICE_PERIOD_NOT_ELAPSED);
        }
        if (notice.isExpiredAt(executionDate, params)) {
            return Optional.of(CutoffEnums.RevalidationFailure.NOTICE_EXPIRED);
        }
        return Optional.empty();
    }

    /**
     * Deadline of the post-cut notice. Throws when
     * {@code cut.post_cut_notice_hours} has no accepted value: the
     * parameter has no factory default in the sources, so no hour count
     * is presumed.
     */
    public static OffsetDateTime postCutNoticeDueAt(OffsetDateTime executedAt, CutoffParameters.Values params) {
        if (executedAt == null) {
            throw new IllegalArgumentException("instante do corte e obrigatorio");
        }
        if (params.postCutNoticeHours() == null) {
            throw new IllegalStateException("cut.post_cut_notice_hours sem valor aceito: o prazo do aviso"
                    + " pos-corte nao e calculado sem o parametro do tenant/ERI.");
        }
        return executedAt.plusHours(params.postCutNoticeHours());
    }
}
