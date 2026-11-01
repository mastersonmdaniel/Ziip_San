package br.com.ziipsan.compliance.lgpd.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Result of a scheduled LGPD anonymization run (Art. 18, IV).
 *
 * <p>Carries only what was actually applied to in-memory entity state. Nothing here
 * claims that source systems outside this module were purged.</p>
 *
 * @param tenantId          tenant the run was scoped to
 * @param executedAt        moment the run finished
 * @param subjectsProcessed distinct data subjects reached by the run
 * @param consentsAnonymized consent records whose identifiers were redacted
 * @param identifiersRedacted total number of identifier fields cleared
 * @param pendingItems      subjects still queued (blocked, see warnings)
 * @param warnings          human-readable notes about what was NOT done and why
 */
public record AnonymizationReport(
        UUID tenantId,
        OffsetDateTime executedAt,
        int subjectsProcessed,
        int consentsAnonymized,
        int identifiersRedacted,
        int pendingItems,
        List<String> warnings
) {

    public static AnonymizationReport empty(UUID tenantId) {
        return new AnonymizationReport(tenantId, OffsetDateTime.now(), 0, 0, 0, 0, List.of());
    }

    public boolean hasPendingItems() {
        return pendingItems > 0;
    }
}