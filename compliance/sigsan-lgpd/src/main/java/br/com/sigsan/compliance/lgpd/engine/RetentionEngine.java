package br.com.sigsan.compliance.lgpd.engine;

import br.com.sigsan.compliance.lgpd.domain.ConsentRecord;
import br.com.sigsan.compliance.lgpd.domain.DataSubjectRequest;
import br.com.sigsan.compliance.lgpd.domain.RetentionReport;
import br.com.sigsan.compliance.lgpd.domain.RetentionSchedule;
import br.com.sigsan.compliance.lgpd.repository.ConsentRecordRepository;
import br.com.sigsan.compliance.lgpd.repository.DataSubjectRequestRepository;
import br.com.sigsan.compliance.lgpd.repository.RetentionScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Retention and disposal (LGPD Art. 15, II and Art. 16).
 *
 * <p>Walks the tenant's {@link RetentionSchedule} rows, expires consents whose term has
 * elapsed, and closes data subject requests that blew their statutory deadline.</p>
 *
 * <p><b>Honesty note.</b> This engine NEVER deletes. Art. 16 §1 requires elimination only
 * after the purpose is fulfilled <i>and</i> preserves the data where there is a legal,
 * contractual or legitimate-interest ground - a determination this module cannot make from
 * its own tables. Anything past its term is therefore counted as a deletion candidate and
 * surfaced for a human decision; {@code deletedRecords} is reported and is always 0.
 * Silently deleting here would be both irreversible and unverifiable.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionEngine {

    private final ConsentRecordRepository consentRepository;
    private final DataSubjectRequestRepository dsrRepository;
    private final RetentionScheduleRepository scheduleRepository;

    /** Art. 18 §1 - requests are answered within 15 days. */
    private static final List<DataSubjectRequest.RequestStatus> TERMINAL_DSR_STATUS = List.of(
            DataSubjectRequest.RequestStatus.COMPLETED,
            DataSubjectRequest.RequestStatus.DENIED,
            DataSubjectRequest.RequestStatus.CANCELLED,
            DataSubjectRequest.RequestStatus.EXPIRED
    );

    /**
     * Applies the tenant's retention schedule. Idempotent: expired records are no longer
     * matched on the next run, so a repeated call converges rather than accumulating.
     */
    @Transactional
    public RetentionReport runRetentionCleanup(UUID tenantId) {
        if (tenantId == null) {
            return RetentionReport.empty(null);
        }

        OffsetDateTime now = OffsetDateTime.now();
        List<String> legalReview = new ArrayList<>();
        int consentsExpired = 0;
        int requestsExpired = 0;
        int candidates = 0;

        List<RetentionSchedule> schedules = scheduleRepository.findByTenantIdAndActiveTrue(tenantId);

        // 1. Expire consents whose term elapsed. Expiry is a status change, not a deletion:
        //    the record stays as evidence of what was consented to.
        List<ConsentRecord> elapsed = consentRepository.findByTenantIdAndStatusAndExpiresAtBefore(
                tenantId, ConsentRecord.ConsentStatus.GRANTED, now);
        for (ConsentRecord consent : elapsed) {
            consent.setStatus(ConsentRecord.ConsentStatus.EXPIRED);
            consentRepository.save(consent);
            consentsExpired++;
            log.info("Consent {} expired (tenant {})", consent.getId(), tenantId);
        }

        // 2. Close data subject requests past the statutory deadline.
        List<DataSubjectRequest> overdue = dsrRepository
                .findByTenantIdAndDeadlineAtBeforeAndStatusNotIn(tenantId, now, TERMINAL_DSR_STATUS);
        for (DataSubjectRequest request : overdue) {
            // Art. 18 §4: a request may not be refused outright. Flagging it as expired
            // closes the SLA clock; the breach remains visible for the controller to answer.
            request.setStatus(DataSubjectRequest.RequestStatus.EXPIRED);
            dsrRepository.save(request);
            requestsExpired++;
            legalReview.add("DSR " + request.getId() + " (" + request.getRequestType()
                    + ") excedeu o prazo do Art. 18 §1 - resposta ainda devida ao titular");
        }

        // 3. Evaluate the declared schedules. No entity is touched: without a persisted
        //    retention anchor per record there is nothing honest to act on.
        for (RetentionSchedule schedule : schedules) {
            candidates += evaluateSchedule(schedule, now, legalReview);
            schedule.setLastRunAt(now);
            scheduleRepository.save(schedule);
        }

        RetentionReport report = new RetentionReport(tenantId, now, consentsExpired, requestsExpired,
                candidates, 0, legalReview);
        log.info("Retention run tenant={} consentsExpired={} requestsExpired={} candidates={} deleted=0",
                tenantId, consentsExpired, requestsExpired, candidates);
        return report;
    }

    /**
     * Records what the schedule would dispose of and why it was not done. Returns the
     * number of candidates it counted.
     */
    private int evaluateSchedule(RetentionSchedule schedule, OffsetDateTime now, List<String> legalReview) {
        if (schedule.getRetentionDays() == null || schedule.getRetentionDays() <= 0) {
            legalReview.add("Schedule " + schedule.getEntityType() + "/" + schedule.getCategory()
                    + " sem prazo valido; eliminacao nao avaliada (Art. 15 II)");
            return 0;
        }

        OffsetDateTime due = schedule.getNextRunAt();
        if (due != null && due.isAfter(now)) {
            return 0;
        }

        legalReview.add("Schedule " + schedule.getEntityType() + "/" + schedule.getCategory()
                + " vencida em " + schedule.getRetentionDays() + " dias; acao "
                + schedule.getDisposalAction() + " aguarda decisao sobre base legal (Art. 16 §1)"
                + (schedule.getLegalHoldReason() != null && !schedule.getLegalHoldReason().isBlank()
                        ? " - retencao mantida: " + schedule.getLegalHoldReason() : ""));

        schedule.setNextRunAt(now.plusDays(schedule.getRetentionDays()));
        return 1;
    }
}