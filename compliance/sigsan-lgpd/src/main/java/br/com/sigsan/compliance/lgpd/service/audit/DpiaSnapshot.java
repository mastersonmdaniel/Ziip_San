package br.com.sigsan.compliance.lgpd.service.audit;

import br.com.sigsan.compliance.lgpd.domain.DpiaRecord;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * O que a trilha de auditoria guarda de um RIPD (DPIA): identificação da atividade, situação e
 * resultado da avaliação de risco.
 *
 * <p>Ficam de fora a descrição livre do tratamento e a assinatura do encarregado, que podem conter
 * nome e dados de pessoas, e o id de quem aprovou: o {@code audit_log} já registra o usuário da
 * operação na própria linha.
 */
public record DpiaSnapshot(
        UUID id,
        String processingActivity,
        DpiaRecord.DpiaStatus status,
        DpiaRecord.RiskLevel riskLevel,
        BigDecimal riskScore,
        String likelihood,
        String severity,
        String residualRisk,
        OffsetDateTime approvedAt,
        OffsetDateTime reviewDate
) {

    public static DpiaSnapshot of(DpiaRecord d) {
        return new DpiaSnapshot(d.getId(), d.getProcessingActivity(), d.getStatus(), d.getRiskLevel(),
                d.getRiskScore(), d.getLikelihood(), d.getSeverity(), d.getResidualRisk(), d.getApprovedAt(),
                d.getReviewDate());
    }
}
