package br.com.ziipsan.compliance.lgpd.engine;

import br.com.ziipsan.compliance.lgpd.domain.AnonymizationReport;
import br.com.ziipsan.compliance.lgpd.domain.ConsentRecord;
import br.com.ziipsan.compliance.lgpd.repository.ConsentRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LGPD anonymization of data subjects (Art. 18, IV / Art. 13).
 *
 * <p><b>Scope.</b> This engine only redacts online identifiers on the consent records it
 * owns. It does not delete the consent record itself: Art. 8 §5 obliges the controller to
 * keep proof of consent, so wiping the record would destroy the very evidence the legal
 * basis depends on.</p>
 *
 * <p><b>Honesty note.</b> Data held by the other ZIIPSAN modules is NOT touched here. Every
 * run reports what it actually changed; anything it could not finish is reported in
 * {@link AnonymizationReport#warnings()} rather than being silently swallowed.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnonymizationEngine {

    private final ConsentRecordRepository consentRepository;

    /**
     * Subjects queued by {@link #scheduleAnonymization(UUID, String)}, keyed by tenant.
     * Purely in-memory: it is a hint that accelerates the next run, never the source of
     * truth. Candidates are always re-derived from persisted state at run time, so a lost
     * queue degrades throughput, never compliance.
     */
    private final Map<UUID, Map<UUID, Set<String>>> pendingAnonymizations = new ConcurrentHashMap<>();

    /**
     * Queues a subject for anonymization. Called when consent is revoked and the legal
     * basis was CONSENT - the only case where withdrawal by the subject compels erasure of
     * the data processed under that consent (Art. 8 §5, Art. 18 §4).
     */
    public void scheduleAnonymization(UUID subjectId, String purpose) {
        if (subjectId == null || purpose == null) {
            log.warn("Ignoring anonymization schedule without subject/purpose");
            return;
        }
        UUID tenantId = currentTenantId();
        if (tenantId == null) {
            log.warn("No tenant in context; anonymization for subject {} not queued", subjectId);
            return;
        }
        pendingAnonymizations
                .computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(subjectId, k -> ConcurrentHashMap.newKeySet())
                .add(purpose);
        log.info("Anonymization scheduled for subject {} purpose '{}' (tenant {})", subjectId, purpose, tenantId);
    }

    /**
     * Redacts direct online identifiers from every revoked consent of the tenant and
     * reports exactly what changed. Idempotent: already-redacted rows are counted as
     * unchanged, not as work.
     */
    @Transactional
    public AnonymizationReport runScheduledAnonymization(UUID tenantId) {
        if (tenantId == null) {
            return AnonymizationReport.empty(null);
        }

        OffsetDateTime executedAt = OffsetDateTime.now();
        List<String> warnings = new ArrayList<>();
        int subjects = 0;
        int consents = 0;
        int identifiers = 0;
        int pending = 0;

        List<ConsentRecord> revoked = consentRepository
                .findByTenantIdAndStatusAndDeletedAtIsNull(tenantId, ConsentRecord.ConsentStatus.REVOKED);

        Set<UUID> touchedSubjects = new java.util.HashSet<>();

        for (ConsentRecord consent : revoked) {
            subjects++;
            touchedSubjects.add(consent.getSubjectId());

            // Only consent-based processing is revoked by the subject's withdrawal.
            // Records kept under contract, legal obligation or credit protection are
            // lawful and must survive (Art. 16, I).
            if (consent.getLegalBasis() != ConsentRecord.LegalBasis.CONSENT) {
                warnings.add("Consent " + consent.getId() + " kept: legal basis "
                        + consent.getLegalBasis() + " survives revocation (Art. 16, I)");
                continue;
            }

            // Art. 8 §5: proof of consent must remain, so subjectId, purpose, consentText,
            // consentHash and the timestamps are deliberately preserved. What is dropped
            // are the online identifiers that serve no evidential purpose.
            int redacted = 0;
            if (consent.getIpAddress() != null) {
                consent.setIpAddress(null);
                redacted++;
            }
            if (consent.getUserAgent() != null) {
                consent.setUserAgent(null);
                redacted++;
            }
            if (consent.getGeolocation() != null && !"{}".equals(consent.getGeolocation())) {
                consent.setGeolocation(null);
                redacted++;
            }
            if (redacted == 0) {
                continue;
            }

            consentRepository.save(consent);
            consents++;
            identifiers += redacted;
        }

        // Anything queued but not resolved by the persisted-state sweep stays pending.
        pending = drainQueue(tenantId, warnings);

        log.info("Anonymization run tenant={} subjects={} consents={} identifiers={} pending={}",
                tenantId, subjects, consents, identifiers, pending);

        return new AnonymizationReport(tenantId, executedAt, subjects, consents, identifiers, pending, warnings);
    }

    /**
     * Drops queue entries whose consent is now fully redacted. Entries that remain are
     * reported so an operator can act on them; the queue is never silently discarded.
     */
    private int drainQueue(UUID tenantId, List<String> warnings) {
        Map<UUID, Set<String>> queue = pendingAnonymizations.get(tenantId);
        if (queue == null || queue.isEmpty()) {
            return 0;
        }

        int remaining = 0;
        for (Map.Entry<UUID, Set<String>> entry : queue.entrySet()) {
            UUID subjectId = entry.getKey();
            for (String purpose : entry.getValue()) {
                boolean outstanding = consentRepository
                        .findByTenantIdAndSubjectIdAndPurposeAndStatusAndDeletedAtIsNull(
                                tenantId, subjectId, purpose, ConsentRecord.ConsentStatus.REVOKED)
                        .filter(c -> c.getIpAddress() != null
                                || c.getUserAgent() != null
                                || (c.getGeolocation() != null && !"{}".equals(c.getGeolocation())))
                        .isPresent();
                if (outstanding) {
                    remaining++;
                    warnings.add("Subject " + subjectId + " purpose '" + purpose
                            + "' still holds identifiers outside this module");
                }
            }
        }
        queue.clear();
        return remaining;
    }

    private UUID currentTenantId() {
        try {
            return br.com.ziipsan.core.tenant.TenantContextHolder.getTenantId();
        } catch (Exception e) {
            return null;
        }
    }
}