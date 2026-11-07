package br.com.ziipsan.commercial.collection.retorno;

import java.util.Objects;
import java.util.UUID;

/**
 * Aprovacao dupla das classificacoes manuais (P3.1): o efeito so pode ser aplicado
 * depois de DOIS aprovadores distintos, e quem solicitou a classificacao nao aprova
 * a propria solicitacao (segregacao da caixa de aprovacoes, P2.28:
 * {@code wf.self_approval} classe a, sempre falso).
 *
 * <p>Regra pura e sem estado: recebe os aprovadores ja registrados e o novo aprovador,
 * e devolve o estado resultante ou recusa com erro explicito.
 */
public final class DoubleApprovalRule {

    public enum ApprovalState { AWAITING_FIRST, AWAITING_SECOND, APPROVED }

    /** @param state estado apos esta aprovacao; os ids guardam quem ja aprovou */
    public record ApprovalProgress(ApprovalState state, UUID firstApproverId, UUID secondApproverId) {
        public boolean isApproved() {
            return state == ApprovalState.APPROVED;
        }
    }

    private DoubleApprovalRule() {
    }

    public static ApprovalProgress approve(UUID requestedBy, UUID firstApproverId, UUID secondApproverId,
                                           UUID newApprover) {
        Objects.requireNonNull(requestedBy, "solicitante e obrigatorio");
        Objects.requireNonNull(newApprover, "aprovador e obrigatorio");
        if (newApprover.equals(requestedBy)) {
            throw new IllegalArgumentException("quem solicitou a classificacao nao pode aprova-la");
        }
        if (secondApproverId != null) {
            throw new IllegalStateException("classificacao ja aprovada por dois aprovadores");
        }
        if (firstApproverId == null) {
            return new ApprovalProgress(ApprovalState.AWAITING_SECOND, newApprover, null);
        }
        if (newApprover.equals(firstApproverId)) {
            throw new IllegalArgumentException("aprovacao dupla exige dois aprovadores distintos");
        }
        return new ApprovalProgress(ApprovalState.APPROVED, firstApproverId, newApprover);
    }
}
