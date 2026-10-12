package br.com.sigsan.compliance.lgpd.service;

import br.com.sigsan.core.json.Json;
import br.com.sigsan.compliance.lgpd.domain.*;
import br.com.sigsan.compliance.lgpd.repository.ConsentRecordRepository;
import br.com.sigsan.compliance.lgpd.repository.DataSubjectRequestRepository;
import br.com.sigsan.compliance.lgpd.repository.DpiaRecordRepository;
import br.com.sigsan.compliance.lgpd.engine.*;
import br.com.sigsan.compliance.lgpd.service.audit.ConsentSnapshot;
import br.com.sigsan.compliance.lgpd.service.audit.DpiaSnapshot;
import br.com.sigsan.compliance.lgpd.service.audit.DsrSnapshot;
import br.com.sigsan.core.tenant.TenantContextHolder;
import br.com.sigsan.core.audit.AuditLogRepository;
import br.com.sigsan.core.audit.AuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LgpdEngineService {

    private final ConsentRecordRepository consentRepository;
    private final DpiaRecordRepository dpiaRepository;
    private final DataSubjectRequestRepository dsrRepository;
    private final AnonymizationEngine anonymizationEngine;
    private final DpiaEngine dpiaEngine;
    private final DataSubjectRightsEngine dsrEngine;
    private final RetentionEngine retentionEngine;
    private final AuditLogRepository auditLogRepository;

    // ========================================================================
    // CONSENT MANAGEMENT
    // ========================================================================

    @Transactional
    @CacheEvict(value = "consents", key = "#tenantId + '_' + #subjectId")
    public ConsentRecord grantConsent(GrantConsentRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();

        // Check for existing consent
        Optional<ConsentRecord> existing = consentRepository
                .findByTenantIdAndSubjectIdAndPurposeAndStatus(
                        TenantContextHolder.getTenantId(), request.subjectId(), request.purpose(), ConsentRecord.ConsentStatus.GRANTED);

        if (existing.isPresent()) {
            // Update existing
            ConsentRecord existingConsent = existing.get();
            existingConsent.setStatus(ConsentRecord.ConsentStatus.GRANTED);
            existingConsent.setGrantedAt(OffsetDateTime.now());
            existingConsent.setVersion(existingConsent.getVersion() + 1);
            existingConsent.setConsentText(request.consentText());
            existingConsent.setConsentHash(hashConsentText(request.consentText()));
            existingConsent.setIpAddress(request.ipAddress());
            existingConsent.setUserAgent(request.userAgent());
            existingConsent.setGeolocation(Json.orDefault(request.geolocation(), "{}"));

            return consentRepository.save(existingConsent);
        }

// Create new consent. Built with setters, not @Builder: ConsentRecord extends
        // TenantAwareEntity -> BaseEntity, and a plain Lombok @Builder silently drops
        // the inherited id / createdAt / version fields.
        ConsentRecord consent = new ConsentRecord();
        consent.setTenantId(TenantContextHolder.getTenantId());
        consent.setSubjectId(request.subjectId());
        consent.setSubjectType(request.subjectType());
        consent.setPurpose(request.purpose());
        consent.setPurposeCategory(request.purposeCategory());
        consent.setLegalBasis(request.legalBasis());
        consent.setStatus(ConsentRecord.ConsentStatus.GRANTED);
        consent.setGrantedAt(OffsetDateTime.now());
        consent.setExpiresAt(request.expiresAt());
        consent.setConsentText(request.consentText());
        consent.setConsentHash(hashConsentText(request.consentText()));
        consent.setIpAddress(request.ipAddress());
        consent.setUserAgent(request.userAgent());
        consent.setGeolocation(Json.orDefault(request.geolocation(), "{}"));
        consent.setMetadata(Json.orDefault(request.metadata(), "{}"));

        ConsentRecord saved = consentRepository.save(consent);

        // Audit
        auditLog(ConsentRecord.class.getSimpleName(), saved.getId(), "GRANT", null, Json.write(ConsentSnapshot.of(saved)));

        return saved;
    }

    @Transactional
    @CacheEvict(value = "consents", allEntries = true)
    public ConsentRecord revokeConsent(UUID consentId, RevokeConsentRequest request) {
        ConsentRecord consent = consentRepository.findByIdAndTenantId(consentId, TenantContextHolder.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Consent not found"));

        if (consent.getStatus() == ConsentRecord.ConsentStatus.REVOKED) {
            throw new IllegalStateException("Consent already revoked");
        }

        String oldValues = Json.write(ConsentSnapshot.of(consent));

        consent.setStatus(ConsentRecord.ConsentStatus.REVOKED);
        consent.setRevokedAt(OffsetDateTime.now());
        consent.setWithdrawalMethod(request.method());
        consent.setWithdrawalReason(request.reason());

        ConsentRecord saved = consentRepository.save(consent);

        // Trigger anonymization if required
        if (consent.getLegalBasis() == ConsentRecord.LegalBasis.CONSENT) {
            anonymizationEngine.scheduleAnonymization(consent.getSubjectId(), consent.getPurpose());
        }

        auditLog(ConsentRecord.class.getSimpleName(), consentId, "REVOKE", oldValues, Json.write(ConsentSnapshot.of(saved)));

        return saved;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "consents", key = "#tenantId + '_' + #subjectId + '_' + #purpose")
    public Optional<ConsentRecord> getActiveConsent(UUID subjectId, String purpose) {
        return consentRepository.findByTenantIdAndSubjectIdAndPurposeAndStatus(
                TenantContextHolder.getTenantId(), subjectId, purpose, ConsentRecord.ConsentStatus.GRANTED);
    }

    @Transactional(readOnly = true)
    public List<ConsentRecord> getSubjectConsents(UUID subjectId) {
        return consentRepository.findByTenantIdAndSubjectId(TenantContextHolder.getTenantId(), subjectId);
    }

    // ========================================================================
    // DPIA AUTOMATION
    // ========================================================================

    @Transactional
    public DpiaRecord createDpia(CreateDpiaRequest request) {
// Setters, not @Builder - see the note in grantConsent.
        DpiaRecord dpia = new DpiaRecord();
        dpia.setTenantId(TenantContextHolder.getTenantId());
        dpia.setProcessingActivity(request.processingActivity());
        dpia.setProcessingDescription(request.processingDescription());
        dpia.setDataCategories(Json.orDefault(request.dataCategories(), "[]"));
        dpia.setSpecialCategories(Json.orDefault(request.specialCategories(), "[]"));
        dpia.setDataSubjects(Json.orDefault(request.dataSubjects(), "[]"));
        dpia.setRecipients(Json.orDefault(request.recipients(), "[]"));
        dpia.setThirdCountryTransfers(Json.orDefault(request.thirdCountryTransfers(), "[]"));
        dpia.setRetentionPeriod(request.retentionPeriod());
        dpia.setSecurityMeasures(Json.orDefault(request.securityMeasures(), "{}"));
        dpia.setStatus(DpiaRecord.DpiaStatus.DRAFT);
        dpia.setReviewDate(OffsetDateTime.now().plusYears(1));

        // Auto-calculate risk
        dpiaEngine.assessRisk(dpia);

        DpiaRecord saved = dpiaRepository.save(dpia);
        auditLog(DpiaRecord.class.getSimpleName(), saved.getId(), "CREATE", null, Json.write(DpiaSnapshot.of(saved)));

        return saved;
    }

    @Transactional
    public DpiaRecord assessAndApproveDpia(UUID dpiaId, ApproveDpiaRequest request) {
        DpiaRecord dpia = dpiaRepository.findByIdAndTenantId(dpiaId, TenantContextHolder.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("DPIA not found"));

        // Retrato ANTES da reavaliacao: a versao anterior serializava o mesmo objeto ja alterado
        // como valor antigo e novo, e a auditoria nao mostrava mudanca nenhuma.
        String oldValues = Json.write(DpiaSnapshot.of(dpia));

        // Re-assess with latest data
        dpiaEngine.assessRisk(dpia);

        if (request.approved()) {
            dpia.setStatus(DpiaRecord.DpiaStatus.APPROVED);
            dpia.setApprovedBy(request.approverId());
            dpia.setApprovedAt(OffsetDateTime.now());
            dpia.setDpoSignature(Json.orDefault(request.dpoSignature(), "{}"));
            dpia.setReviewDate(OffsetDateTime.now().plusYears(1));
        } else {
            dpia.setStatus(DpiaRecord.DpiaStatus.REJECTED);
        }

        DpiaRecord saved = dpiaRepository.save(dpia);
        auditLog(DpiaRecord.class.getSimpleName(), dpiaId, request.approved() ? "APPROVE" : "REJECT", oldValues,
                Json.write(DpiaSnapshot.of(saved)));

        return saved;
    }

    @Transactional(readOnly = true)
    public List<DpiaRecord> getDpiasRequiringReview() {
        OffsetDateTime now = OffsetDateTime.now();
        return dpiaRepository.findByTenantIdAndStatusAndReviewDateBefore(
                TenantContextHolder.getTenantId(), DpiaRecord.DpiaStatus.APPROVED, now);
    }

    // ========================================================================
    // DATA SUBJECT RIGHTS (DSR)
    // ========================================================================

    @Transactional
    public DataSubjectRequest createDataSubjectRequest(CreateDsrRequest request) {
        // Setters, not @Builder - see the note in grantConsent.
        DataSubjectRequest dsr = new DataSubjectRequest();
        dsr.setTenantId(TenantContextHolder.getTenantId());
        dsr.setSubjectId(request.subjectId());
        dsr.setSubjectType(request.subjectType());
        dsr.setRequestType(request.requestType());
        dsr.setStatus(DataSubjectRequest.RequestStatus.RECEIVED);
        dsr.setDescription(request.description());
        dsr.setRequestedDataCategories(Json.orDefault(request.requestedDataCategories(), "[]"));
        dsr.setVerificationMethod(request.verificationMethod());
        // LGPD art. 19, II: 15 dias para a declaracao completa (o art. 18 lista os direitos, nao o
        // prazo). Constante por enquanto; deveria ser parametro do tenant (docs/levantamento/10).
        dsr.setDeadlineAt(OffsetDateTime.now().plusDays(15));
        dsr.setMetadata(Json.orDefault(request.metadata(), "{}"));

        DataSubjectRequest saved = dsrRepository.save(dsr);

        // Trigger async processing
        dsrEngine.processRequest(saved);

        auditLog(DataSubjectRequest.class.getSimpleName(), saved.getId(), "CREATE", null, Json.write(DsrSnapshot.of(saved)));

        return saved;
    }

    @Transactional
    public DataSubjectRequest verifyDataSubjectRequest(UUID dsrId, VerifyDsrRequest request) {
        DataSubjectRequest dsr = dsrRepository.findByIdAndTenantId(dsrId, TenantContextHolder.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("DSR not found"));

        if (dsr.getStatus() != DataSubjectRequest.RequestStatus.RECEIVED && dsr.getStatus() != DataSubjectRequest.RequestStatus.VERIFICATION_PENDING) {
            throw new IllegalStateException("DSR cannot be verified in current status: " + dsr.getStatus());
        }

        String oldValues = Json.write(DsrSnapshot.of(dsr));
        dsr.setStatus(DataSubjectRequest.RequestStatus.VERIFIED);
        dsr.setVerifiedAt(OffsetDateTime.now());
        dsr.setVerifiedBy(request.verifiedBy());

        DataSubjectRequest saved = dsrRepository.save(dsr);
        auditLog(DataSubjectRequest.class.getSimpleName(), dsrId, "VERIFY", oldValues, Json.write(DsrSnapshot.of(saved)));

        // Continue processing
        dsrEngine.continueProcessing(saved);

        return saved;
    }

    @Transactional
    public DataSubjectRequest completeDataSubjectRequest(UUID dsrId, CompleteDsrRequest request) {
        DataSubjectRequest dsr = dsrRepository.findByIdAndTenantId(dsrId, TenantContextHolder.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("DSR not found"));

        String oldValues = Json.write(DsrSnapshot.of(dsr));
        dsr.setStatus(DataSubjectRequest.RequestStatus.COMPLETED);
        dsr.setCompletedAt(OffsetDateTime.now());
        dsr.setCompletedBy(request.completedBy());
        dsr.setResponseSummary(request.responseSummary());
        dsr.setResponseData(Json.orDefault(request.responseData(), "{}"));

        DataSubjectRequest saved = dsrRepository.save(dsr);
        auditLog(DataSubjectRequest.class.getSimpleName(), dsrId, "COMPLETE", oldValues, Json.write(DsrSnapshot.of(saved)));

        return saved;
    }

    @Transactional(readOnly = true)
    public List<DataSubjectRequest> getOverdueRequests() {
        return dsrRepository.findByTenantIdAndDeadlineAtBeforeAndStatusNotIn(
                TenantContextHolder.getTenantId(), OffsetDateTime.now(), List.of(
                        DataSubjectRequest.RequestStatus.COMPLETED,
                        DataSubjectRequest.RequestStatus.DENIED,
                        DataSubjectRequest.RequestStatus.CANCELLED
                ));
    }

    // ========================================================================
    // ANONYMIZATION & RETENTION
    // ========================================================================

    @Transactional
    public AnonymizationReport runScheduledAnonymization() {
        return anonymizationEngine.runScheduledAnonymization(TenantContextHolder.getTenantId());
    }

    @Transactional
    public RetentionReport runRetentionCleanup() {
        return retentionEngine.runRetentionCleanup(TenantContextHolder.getTenantId());
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private String hashConsentText(String consentText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(consentText.getBytes());
            return bytesToHex(hash);
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Persists an audit trail entry.
     *
     * <p>AuditLog extends BaseEntity and exposes only @Getter/@Setter, so it is built with
     * setters - the same pattern TenantProvisioningService uses. A plain Lombok @Builder
     * on the entity would silently drop the inherited id/createdAt/version fields.</p>
     *
     * <p>The caller passes a domain verb (GRANT, REVOKE, APPROVE...) that is richer than
     * {@link AuditLog.AuditAction}. It is folded into the closest enum constant and the
     * exact verb is preserved in metadata, so the LGPD-specific meaning survives.</p>
     */
    private void auditLog(String entityType, UUID entityId, String action, String oldValues, String newValues) {
        AuditLog audit = new AuditLog();
        audit.setTenantId(TenantContextHolder.getTenantId());
        audit.setEntityType(entityType);
        audit.setEntityId(entityId);
        audit.setAction(toAuditAction(action));
        audit.setOldValues(oldValues);
        audit.setNewValues(newValues);
        audit.setMetadata(Json.write(Map.of("action", action)));
        audit.setSuccess(true);
        auditLogRepository.save(audit);
    }

    /**
     * Maps a LGPD domain verb onto the coarse audit vocabulary. Unknown verbs are logged
     * as UPDATE rather than throwing, so an unmapped action can never silently break a
     * business transaction or lose the audit entry entirely.
     */
    private AuditLog.AuditAction toAuditAction(String action) {
        if (action == null) {
            return AuditLog.AuditAction.UPDATE;
        }
        try {
            return AuditLog.AuditAction.valueOf(action);
        } catch (IllegalArgumentException e) {
            return switch (action) {
                case "GRANT" -> AuditLog.AuditAction.CREATE;
                case "REVOKE", "APPROVE", "REJECT", "COMPLETE" -> AuditLog.AuditAction.UPDATE;
                default -> {
                    log.warn("Unmapped audit action '{}'; recorded as UPDATE", action);
                    yield AuditLog.AuditAction.UPDATE;
                }
            };
        }
    }

    // Request DTOs
    public record GrantConsentRequest(
            UUID subjectId, String subjectType, String purpose, String purposeCategory,
            ConsentRecord.LegalBasis legalBasis, String consentText, OffsetDateTime expiresAt,
            String ipAddress, String userAgent, Map<String, Object> geolocation, Map<String, Object> metadata
    ) {}

    public record RevokeConsentRequest(
            String method, String reason
    ) {}

    public record CreateDpiaRequest(
            String processingActivity, String processingDescription,
            List<String> dataCategories, List<String> specialCategories,
            List<String> dataSubjects, List<String> recipients,
            List<String> thirdCountryTransfers, String retentionPeriod,
            Map<String, Object> securityMeasures
    ) {}

    public record ApproveDpiaRequest(
            boolean approved, UUID approverId, Map<String, Object> dpoSignature
    ) {}

    public record CreateDsrRequest(
            UUID subjectId, String subjectType, DataSubjectRequest.RequestType requestType,
            String description, List<String> requestedDataCategories,
            String verificationMethod, Map<String, Object> metadata
    ) {}

    public record VerifyDsrRequest(
            UUID verifiedBy
    ) {}

    public record CompleteDsrRequest(
            UUID completedBy, String responseSummary, Map<String, Object> responseData
    ) {}
}