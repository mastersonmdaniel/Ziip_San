package br.com.sigsan.intelligence.quality.lab.audit;

import br.com.sigsan.intelligence.quality.lab.LabAnalysisMethod;

import java.math.BigDecimal;
import java.util.UUID;

/** O que a trilha de auditoria guarda de um método de análise: identificação e limites. */
public record LabMethodSnapshot(
        UUID id,
        String code,
        String name,
        String standardMethod,
        String methodVersion,
        BigDecimal detectionLimit,
        BigDecimal quantificationLimit
) {

    public static LabMethodSnapshot of(LabAnalysisMethod m) {
        return new LabMethodSnapshot(m.getId(), m.getCode(), m.getName(), m.getStandardMethod(),
                m.getMethodVersion(), m.getDetectionLimit(), m.getQuantificationLimit());
    }
}
