package br.com.sigsan.intelligence.quality.lab.audit;

import br.com.sigsan.intelligence.quality.domain.QualityAnalysisResult;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * O que a trilha de auditoria guarda de um resultado de análise (Portaria GM/MS 888/2021).
 *
 * <p>Amostra e parâmetro entram só pelo id: serializar as associações preguiçosas carregaria (ou
 * quebraria em) proxies JPA. Ficam de fora quem analisou e as observações em texto livre.
 */
public record AnalysisResultSnapshot(
        UUID id,
        UUID sampleId,
        UUID parameterId,
        BigDecimal resultValue,
        String resultText,
        String unit,
        String methodCode,
        BigDecimal vmp,
        QualityAnalysisResult.ConformityStatus conformity,
        QualityAnalysisResult.AnalysisStatus status,
        OffsetDateTime analyzedAt,
        UUID instrumentId
) {

    public static AnalysisResultSnapshot of(QualityAnalysisResult r) {
        return new AnalysisResultSnapshot(r.getId(),
                r.getSample() == null ? null : r.getSample().getId(),
                r.getParameter() == null ? null : r.getParameter().getId(),
                r.getResultValue(), r.getResultText(), r.getUnit(), r.getMethodCode(), r.getVmp(),
                r.getConformity(), r.getStatus(), r.getAnalyzedAt(), r.getInstrumentId());
    }
}
