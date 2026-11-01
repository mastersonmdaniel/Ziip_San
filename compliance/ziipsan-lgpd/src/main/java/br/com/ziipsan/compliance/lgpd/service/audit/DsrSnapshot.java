package br.com.ziipsan.compliance.lgpd.service.audit;

import br.com.ziipsan.compliance.lgpd.domain.DataSubjectRequest;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * O que a trilha de auditoria guarda de uma solicitação de titular (Art. 18): tipo, situação e
 * prazos, que é o necessário para demonstrar o atendimento no prazo do Art. 19.
 *
 * <p>Ficam de fora o id do titular, a descrição do pedido, os dados entregues na resposta
 * ({@code responseData}), o resumo e os motivos de recusa ou prorrogação: tudo isso é conteúdo
 * do titular, e copiá-lo para o {@code audit_log} anularia uma eliminação pedida pelo próprio
 * titular.
 */
public record DsrSnapshot(
        UUID id,
        String subjectType,
        DataSubjectRequest.RequestType requestType,
        DataSubjectRequest.RequestStatus status,
        String verificationMethod,
        OffsetDateTime deadlineAt,
        OffsetDateTime verifiedAt,
        OffsetDateTime completedAt
) {

    public static DsrSnapshot of(DataSubjectRequest r) {
        return new DsrSnapshot(r.getId(), r.getSubjectType(), r.getRequestType(), r.getStatus(),
                r.getVerificationMethod(), r.getDeadlineAt(), r.getVerifiedAt(), r.getCompletedAt());
    }
}
