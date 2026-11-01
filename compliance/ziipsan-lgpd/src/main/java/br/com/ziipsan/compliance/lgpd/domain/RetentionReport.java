package br.com.ziipsan.compliance.lgpd.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Result of a retention/cleanup run (LGPD Art. 15, II and Art. 16).
 *
 * <p>{@code deletedRecords} is reported for completeness but this engine never deletes
 * rows on its own: elimination requires an evaluated legal basis for retention
 * (Art. 16, I) that cannot be asserted automatically. Candidates that look eligible
 * are surfaced in {@code pendingLegalReview} for a human decision instead.</p>
 *
 * @param tenantId            tenant the run was scoped to
 * @param executedAt          moment the run finished
 * @param consentsExpired     consent records marked as expired
 * @param requestsExpired     data subject requests closed as expired
 * @param deletionCandidates  records past their retention date, awaiting legal review
 * @param deletedRecords      always 0 - no irreversible elimination is performed here
 * @param pendingLegalReview  descriptions of what needs a human retention decision
 */
public record RetentionReport(
        UUID tenantId,
        OffsetDateTime executedAt,
        int consentsExpired,
        int requestsExpired,
        int deletionCandidates,
        int deletedRecords,
        List<String> pendingLegalReview
) {

    public static RetentionReport empty(UUID tenantId) {
        return new RetentionReport(tenantId, OffsetDateTime.now(), 0, 0, 0, 0, List.of());
    }

    public boolean hasPendingLegalReview() {
        return !pendingLegalReview.isEmpty();
    }
}