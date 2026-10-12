package br.com.sigsan.intelligence.quality.lab;

import br.com.sigsan.core.json.Json;
import br.com.sigsan.intelligence.quality.domain.QualityParameter;
import br.com.sigsan.intelligence.quality.domain.QualityAnalysisResult;
import br.com.sigsan.intelligence.quality.domain.QualitySample;
import br.com.sigsan.intelligence.quality.repository.LabAnalysisMethodRepository;
import br.com.sigsan.intelligence.quality.repository.LabInstrumentRepository;
import br.com.sigsan.intelligence.quality.repository.LabQcSampleRepository;
import br.com.sigsan.intelligence.quality.repository.QualityAnalysisResultRepository;
import br.com.sigsan.intelligence.quality.repository.QualityParameterRepository;
import br.com.sigsan.intelligence.quality.repository.QualitySampleRepository;
import br.com.sigsan.core.tenant.TenantContextHolder;
import br.com.sigsan.core.audit.AuditLog;
import br.com.sigsan.core.audit.AuditLogRepository;
import br.com.sigsan.intelligence.quality.lab.audit.AnalysisResultSnapshot;
import br.com.sigsan.intelligence.quality.lab.audit.LabInstrumentSnapshot;
import br.com.sigsan.intelligence.quality.lab.audit.LabMethodSnapshot;
import br.com.sigsan.intelligence.quality.lab.audit.LabQcSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LabService {

    private final LabInstrumentRepository instrumentRepository;
    private final LabAnalysisMethodRepository methodRepository;
    private final LabQcSampleRepository qcSampleRepository;
    private final QualityParameterRepository parameterRepository;
    private final QualitySampleRepository sampleRepository;
    private final QualityAnalysisResultRepository analysisResultRepository;
    private final AuditLogRepository auditLogRepository;

    // ========================================================================
    // INSTRUMENT MANAGEMENT
    // ========================================================================

    @Transactional
    public LabInstrument registerInstrument(LabInstrument instrument) {
        instrument.setTenantId(TenantContextHolder.getTenantId());
        LabInstrument saved = instrumentRepository.save(instrument);
        auditLog("LabInstrument", saved.getId(), "CREATE", null, Json.write(LabInstrumentSnapshot.of(saved)));
        return saved;
    }

    @Transactional
    public LabInstrument updateInstrumentCalibration(UUID instrumentId, OffsetDateTime calibrationDate, OffsetDateTime nextDue) {
        LabInstrument instrument = instrumentRepository.findByIdAndTenantId(instrumentId, TenantContextHolder.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Instrument not found"));

        String oldValues = Json.write(LabInstrumentSnapshot.of(instrument));
        instrument.setCalibrationDue(calibrationDate.plusDays(instrument.getCalibrationFrequencyDays()));
        LabInstrument saved = instrumentRepository.save(instrument);
        auditLog("LabInstrument", instrumentId, "CALIBRATE", oldValues, Json.write(LabInstrumentSnapshot.of(saved)));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<LabInstrument> getInstrumentsDueCalibration() {
        return instrumentRepository.findByTenantIdAndCalibrationDueBeforeAndStatus(
                TenantContextHolder.getTenantId(), OffsetDateTime.now(), "ACTIVE");
    }

    // ========================================================================
    // ANALYSIS METHOD MANAGEMENT
    // ========================================================================

    @Transactional
    public LabAnalysisMethod createMethod(LabAnalysisMethod method) {
        method.setTenantId(TenantContextHolder.getTenantId());
        LabAnalysisMethod saved = methodRepository.save(method);
        auditLog("LabAnalysisMethod", saved.getId(), "CREATE", null, Json.write(LabMethodSnapshot.of(saved)));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<LabAnalysisMethod> getMethodsByParameter(UUID parameterId) {
        return methodRepository.findByParameterIdAndTenantId(parameterId, TenantContextHolder.getTenantId());
    }

    // ========================================================================
    // QC SAMPLE MANAGEMENT
    // ========================================================================

    @Transactional
    public LabQcSample recordQcResult(LabQcSample qcSample) {
        qcSample.setTenantId(TenantContextHolder.getTenantId());
        qcSample.setAnalyzedAt(OffsetDateTime.now());

        // Calculate deviation
        if (qcSample.getExpectedValue() != null && qcSample.getMeasuredValue() != null) {
            qcSample.setDeviation(qcSample.getMeasuredValue().subtract(qcSample.getExpectedValue()));
            if (qcSample.getExpectedValue().compareTo(BigDecimal.ZERO) != 0) {
                qcSample.setDeviationPercent(
                        qcSample.getDeviation().divide(qcSample.getExpectedValue(), 4, BigDecimal.ROUND_HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                );
            }
        }

        // Auto-determine status based on control limits
        if (qcSample.getMeasuredValue() != null) {
            qcSample.setStatus(determineQcStatus(qcSample));
        }

        LabQcSample saved = qcSampleRepository.save(qcSample);
        auditLog("LabQcSample", saved.getId(), "RECORD", null, Json.write(LabQcSnapshot.of(saved)));

        // Check for trends/rules (Westgard rules)
        checkQcTrends(qcSample);

        return saved;
    }

    private LabQcSample.QcStatus determineQcStatus(LabQcSample qc) {
        if (qc.getMeasuredValue() == null) return LabQcSample.QcStatus.PENDING;

        BigDecimal measured = qc.getMeasuredValue();
        boolean aboveUpperControl = qc.getControlLimitUpper() != null && measured.compareTo(qc.getControlLimitUpper()) > 0;
        boolean belowLowerControl = qc.getControlLimitLower() != null && measured.compareTo(qc.getControlLimitLower()) < 0;
        boolean aboveWarningUpper = qc.getWarningLimitUpper() != null && measured.compareTo(qc.getWarningLimitUpper()) > 0;
        boolean belowWarningLower = qc.getWarningLimitLower() != null && measured.compareTo(qc.getWarningLimitLower()) < 0;

        if (aboveUpperControl || belowLowerControl) {
            return LabQcSample.QcStatus.FAILED;
        } else if (aboveWarningUpper || belowWarningLower) {
            return LabQcSample.QcStatus.WARNING;
        }
        return LabQcSample.QcStatus.PASSED;
    }

    private void checkQcTrends(LabQcSample currentQc) {
        // Westgard rules implementation
        List<LabQcSample> recentQcs = qcSampleRepository.findByMethodIdAndAnalyzedAtAfter(
                currentQc.getMethod().getId(), OffsetDateTime.now().minusDays(30));

        // Rule 1: 2 consecutive points beyond 2SD
        // Rule 2: 4 consecutive points on same side of mean
        // Rule 3: 7 consecutive points on same side of mean
        // Rule 4: 10 consecutive points on same side of mean
        // Rule 5: 1 point beyond 3SD
        // Rule 6: 2 consecutive points on opposite sides of mean (trend)

        if (currentQc.getStatus() == LabQcSample.QcStatus.FAILED) {
            log.warn("QC FAILURE: Method {} Run {} Value {} Expected {}",
                    currentQc.getMethod().getCode(), currentQc.getRunId(),
                    currentQc.getMeasuredValue(), currentQc.getExpectedValue());
            // Trigger alert/notification
        }
    }

    @Transactional(readOnly = true)
    public List<LabQcSample> getQcHistory(UUID methodId, OffsetDateTime from, OffsetDateTime to) {
        return qcSampleRepository.findByMethodIdAndAnalyzedAtBetween(
                methodId, TenantContextHolder.getTenantId(), from, to);
    }

    // ========================================================================
    // INTEGRATION WITH QUALITY SAMPLES
    // ========================================================================

    @Transactional
    public QualityAnalysisResult recordAnalysisResult(RecordAnalysisRequest request) {
        QualitySample sample = sampleRepository.findByIdAndTenantId(request.sampleId(), TenantContextHolder.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Sample not found"));

        QualityParameter parameter = parameterRepository.findByIdAndTenantId(request.parameterId(), TenantContextHolder.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Parameter not found"));

        QualityAnalysisResult result = analysisResultRepository
                .findBySampleIdAndParameterId(request.sampleId(), request.parameterId())
                .orElseGet(() -> {
                    QualityAnalysisResult newResult = new QualityAnalysisResult();
                    newResult.setTenantId(TenantContextHolder.getTenantId());
                    newResult.setSample(sampleRepository.findById(request.sampleId()).orElseThrow());
                    newResult.setParameter(parameterRepository.findById(request.parameterId()).orElseThrow());
                    return newResult;
                });

        // Resultado novo (ainda sem id) nao tem valor antigo: um retrato so com nulos sugeriria que
        // havia um registro vazio antes.
        String oldValues = result.getId() == null ? null : Json.write(AnalysisResultSnapshot.of(result));

        result.setResultValue(request.resultValue());
        result.setResultText(request.resultText());
        result.setUnit(request.unit());
        result.setMethodCode(request.methodCode());
        result.setMethodDescription(request.methodDescription());
        result.setDetectionLimit(request.detectionLimit());
        result.setQuantificationLimit(request.quantificationLimit());
        result.setUncertainty(request.uncertainty());
        result.setVmp(request.vmp());
        result.setConformity(request.conformity());
        result.setStatus(request.status());
        result.setAnalyzedAt(request.analyzedAt() != null ? request.analyzedAt() : OffsetDateTime.now());
        result.setAnalyzedBy(request.analyzedBy());
        result.setInstrumentId(request.instrumentId());
        result.setInstrumentReading(request.instrumentReading());
        result.setQualityControl(Json.orDefault(request.qualityControl(), "{}"));
        result.setObservations(request.observations());
        result.setProperties(Json.orDefault(request.properties(), "{}"));

        QualityAnalysisResult saved = analysisResultRepository.save(result);
        auditLog("QualityAnalysisResult", saved.getId(), "RECORD", oldValues, Json.write(AnalysisResultSnapshot.of(saved)));

        // Check conformity
        if (request.conformity() == QualityAnalysisResult.ConformityStatus.NON_CONFORMING) {
            notifyNonConformance(saved);
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public List<QualityAnalysisResult> getSampleResults(UUID sampleId) {
        return analysisResultRepository.findBySampleIdAndTenantId(sampleId, TenantContextHolder.getTenantId());
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<QualityAnalysisResult>> getBatchResults(List<UUID> sampleIds) {
        Map<UUID, List<QualityAnalysisResult>> result = new HashMap<>();
        for (UUID sampleId : sampleIds) {
            result.put(sampleId, getSampleResults(sampleId));
        }
        return result;
    }

    private void notifyNonConformance(QualityAnalysisResult result) {
        log.warn("NON-CONFORMANCE: Sample {} Parameter {} Value {} VMP {}",
                result.getSample().getSampleCode(),
                result.getParameter().getCode(),
                result.getResultValue(),
                result.getVmp());
        // Send notification, create alert, etc.
    }

    /**
     * Persists an audit trail entry.
     *
     * <p>AuditLog extends BaseEntity and exposes only @Getter/@Setter, so it is built with
     * setters - the same pattern TenantProvisioningService uses. A plain Lombok @Builder
     * on the entity would silently drop the inherited id/createdAt/version fields.</p>
     *
     * <p>The caller passes a lab verb (CREATE, CALIBRATE, RECORD) that is richer than
     * {@link AuditLog.AuditAction}. It is folded into the closest enum constant and the
     * exact verb is preserved in metadata, so the myLIMS-specific meaning survives.</p>
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
     * Maps a lab domain verb onto the coarse audit vocabulary. Unknown verbs are logged
     * as UPDATE rather than throwing, so an unmapped action can never silently break a
     * laboratory transaction or lose the audit entry entirely.
     *
     * <p>RECORD covers both the insertion of a new QC sample and the upsert of an analysis
     * result, so it is recorded as CREATE; the verb in metadata keeps the distinction.</p>
     */
    private AuditLog.AuditAction toAuditAction(String action) {
        if (action == null) {
            return AuditLog.AuditAction.UPDATE;
        }
        try {
            return AuditLog.AuditAction.valueOf(action);
        } catch (IllegalArgumentException e) {
            return switch (action) {
                case "RECORD" -> AuditLog.AuditAction.CREATE;
                case "CALIBRATE" -> AuditLog.AuditAction.UPDATE;
                default -> {
                    log.warn("Unmapped audit action '{}'; recorded as UPDATE", action);
                    yield AuditLog.AuditAction.UPDATE;
                }
            };
        }
    }

    // Request DTOs
    public record RecordAnalysisRequest(
            UUID sampleId, UUID parameterId, BigDecimal resultValue, String resultText,
            String unit, String methodCode, String methodDescription,
            BigDecimal detectionLimit, BigDecimal quantificationLimit,
            BigDecimal uncertainty, BigDecimal vmp,
            QualityAnalysisResult.ConformityStatus conformity,
            QualityAnalysisResult.AnalysisStatus status,
            OffsetDateTime analyzedAt, UUID analyzedBy,
            UUID instrumentId, String instrumentReading,
            Map<String, Object> qualityControl,
            String observations, Map<String, Object> properties
    ) {}
}