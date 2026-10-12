package br.com.sigsan.intelligence.quality.lab.audit;

import br.com.sigsan.intelligence.quality.lab.LabQcSample;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * O que a trilha de auditoria guarda de uma amostra de controle de qualidade.
 *
 * <p>Ficam de fora quem analisou (o {@code audit_log} já registra o usuário da operação) e as
 * observações em texto livre.
 */
public record LabQcSnapshot(
        UUID id,
        LabQcSample.QcType qcType,
        String sampleId,
        String runId,
        BigDecimal expectedValue,
        BigDecimal measuredValue,
        String unit,
        BigDecimal deviation,
        BigDecimal deviationPercent,
        LabQcSample.QcStatus status,
        OffsetDateTime analyzedAt
) {

    public static LabQcSnapshot of(LabQcSample q) {
        return new LabQcSnapshot(q.getId(), q.getQcType(), q.getSampleId(), q.getRunId(), q.getExpectedValue(),
                q.getMeasuredValue(), q.getUnit(), q.getDeviation(), q.getDeviationPercent(), q.getStatus(),
                q.getAnalyzedAt());
    }
}
