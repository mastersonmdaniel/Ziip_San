package br.com.sigsan.compliance.lgpd.engine;

import br.com.sigsan.compliance.lgpd.domain.DataSubjectRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * Data subject rights (titular) workflow - LGPD Art. 18 and Art. 19.
 *
 * <p>Owns the two state transitions the service drives: intake and post-verification
 * preparation. Mutates the request in place; the caller owns persistence.</p>
 *
 * <p><b>Honesty note.</b> This engine performs NO irreversible or fabricated action.
 * It does not verify identity (that requires a human or an out-of-band channel it cannot
 * reach), does not read the data of other modules, and does not mark anything completed.
 * Art. 19 requires the response be produced from data actually held - so the engine
 * leaves the request {@code IN_PROGRESS} and hands the collection of the real data to the
 * operator, who calls {@code completeDataSubjectRequest}. Art. 18 §4 forbids refusing a
 * request outright without further and proper notice, which is why nothing is silently
 * discarded here.</p>
 */
@Slf4j
@Service
public class DataSubjectRightsEngine {

    /** Art. 18 §2: identity verification may be delayed only for clear grounds. */
    private static final int DEADLINE_DAYS = 15;

    /**
     * Intake: decides whether the request needs identity verification before any data is
     * released, and moves the request out of {@code RECEIVED} accordingly.
     *
     * <p>A request is never auto-approved here. Without a recorded verification method the
     * request is parked in {@code VERIFICATION_PENDING} with the reason, because releasing
     * data to an unverified caller is exactly the breach Art. 6 and Art. 46 guard against.</p>
     */
    public void processRequest(DataSubjectRequest request) {
        if (request == null) {
            log.warn("Null DSR received; nothing to process");
            return;
        }

        if (request.getStatus() != DataSubjectRequest.RequestStatus.RECEIVED) {
            log.debug("DSR {} not in RECEIVED status ({}); intake skipped",
                    request.getId(), request.getStatus());
            return;
        }

        if (request.getRequestType() == null) {
            log.warn("DSR {} has no request type; cannot route it", request.getId());
            return;
        }

        ensureDeadline(request);

        if (request.getVerificationMethod() == null || request.getVerificationMethod().isBlank()) {
            request.setStatus(DataSubjectRequest.RequestStatus.VERIFICATION_PENDING);
            request.setMetadata(mergeMetadata(request,
                    "verificationRequired",
                    "Nao ha metodo de verificacao registrado (Art. 18 §2). "
                            + "A solicitacao aguarda verificacao de identidade do titular."));
            log.info("DSR {} ({}) awaiting identity verification", request.getId(), request.getRequestType());
            return;
        }

        // A verification method exists but no human has confirmed the identity yet.
        // Verification is an identity proof, not a configuration flag.
        request.setStatus(DataSubjectRequest.RequestStatus.VERIFICATION_PENDING);
        log.info("DSR {} ({}) queued for verification via {}",
                request.getId(), request.getRequestType(), request.getVerificationMethod());
    }

    /**
     * Post-verification: the identity has been confirmed, so the request moves into
     * {@code IN_PROGRESS} and the deadline is (re)armed. The engine deliberately stops
     * here - producing the actual response requires reading the subject's data across the
     * modules that hold it, which this module cannot do.
     */
    public void continueProcessing(DataSubjectRequest request) {
        if (request == null) {
            log.warn("Null DSR received; nothing to continue");
            return;
        }

        if (request.getStatus() != DataSubjectRequest.RequestStatus.VERIFIED
                && request.getStatus() != DataSubjectRequest.RequestStatus.VERIFICATION_PENDING
                && request.getStatus() != DataSubjectRequest.RequestStatus.IN_PROGRESS) {
            log.debug("DSR {} cannot be continued from status {}", request.getId(), request.getStatus());
            return;
        }

        ensureDeadline(request);
        request.setStatus(DataSubjectRequest.RequestStatus.IN_PROGRESS);

        log.info("DSR {} ({}) in progress; operator must assemble the real data and complete it. "
                        + "Deadline {}", request.getId(), request.getRequestType(), request.getDeadlineAt());
    }

    /**
     * Arms the Art. 18 response deadline when the caller has not already set one.
     * Never shortens a deadline an operator has already agreed (Art. 19 §2 extension).
     */
    private void ensureDeadline(DataSubjectRequest request) {
        if (request.getDeadlineAt() == null) {
            request.setDeadlineAt(OffsetDateTime.now().plusDays(DEADLINE_DAYS));
        }
    }

    /**
     * Appends a note to the request metadata. The field is a string blob; this keeps the
     * existing shape instead of pulling a JSON mapper into the engine for one key.
     */
    private String mergeMetadata(DataSubjectRequest request, String key, String value) {
        String current = request.getMetadata();
        boolean empty = current == null || current.isBlank() || "{}".equals(current);
        return empty ? "{\"" + key + "\":\"" + value + "\"}"
                : current.substring(0, current.length() - 1) + ",\"" + key + "\":\"" + value + "\"}";
    }
}