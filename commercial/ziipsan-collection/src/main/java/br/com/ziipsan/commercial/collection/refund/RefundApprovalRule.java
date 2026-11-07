package br.com.ziipsan.commercial.collection.refund;

import br.com.ziipsan.commercial.collection.retorno.DoubleApprovalRule;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Aprovacao da devolucao (P3.29): aprovacao DUPLA somente ACIMA DA ALCADA.
 *
 * <p>A alcada ({@code doubleApprovalThreshold}) e entrada obrigatoria: e o
 * valor vigente da politica de aprovacao do tenant, resolvido pela fiacao
 * (caixa de aprovacoes da P2.28). O catalogo de parametros nao tem chave
 * propria de alcada de devolucao e esta regra NAO inventa valor: alcada
 * ausente e erro explicito. Abaixo ou na alcada, a aprovacao e simples
 * (um aprovador, nunca o solicitante). Acima dela, valem as guardas da
 * {@link DoubleApprovalRule} da P3.1 (dois aprovadores distintos, nenhum
 * deles o solicitante; {@code wf.self_approval} sempre falso).
 */
public final class RefundApprovalRule {

    private RefundApprovalRule() {
    }

    /** Acima da alcada (estritamente maior) exige aprovacao dupla. */
    public static boolean requiresDoubleApproval(BigDecimal refundAmount, BigDecimal doubleApprovalThreshold) {
        Objects.requireNonNull(refundAmount, "valor da devolucao e obrigatorio");
        if (doubleApprovalThreshold == null) {
            throw new IllegalArgumentException("alcada de aprovacao dupla e obrigatoria: "
                    + "sem valor vigente do tenant nao ha como decidir a aprovacao "
                    + "(nenhum valor e presumido)");
        }
        if (doubleApprovalThreshold.signum() < 0) {
            throw new IllegalArgumentException("alcada nao pode ser negativa: " + doubleApprovalThreshold);
        }
        if (refundAmount.signum() <= 0) {
            throw new IllegalArgumentException("valor da devolucao deve ser positivo: " + refundAmount);
        }
        return refundAmount.compareTo(doubleApprovalThreshold) > 0;
    }

    /**
     * Registra uma aprovacao. Sem exigencia de dupla, a primeira aprovacao
     * (de quem nao solicitou) aprova. Com exigencia, delega a regra dupla
     * da P3.1.
     */
    public static DoubleApprovalRule.ApprovalProgress approve(boolean doubleRequired, UUID requestedBy,
                                                              UUID firstApproverId, UUID secondApproverId,
                                                              UUID newApprover) {
        Objects.requireNonNull(requestedBy, "solicitante e obrigatorio");
        Objects.requireNonNull(newApprover, "aprovador e obrigatorio");
        if (!doubleRequired) {
            if (newApprover.equals(requestedBy)) {
                throw new IllegalArgumentException("quem solicitou a devolucao nao pode aprova-la");
            }
            if (firstApproverId != null) {
                throw new IllegalStateException("devolucao ja aprovada");
            }
            return new DoubleApprovalRule.ApprovalProgress(
                    DoubleApprovalRule.ApprovalState.APPROVED, newApprover, null);
        }
        return DoubleApprovalRule.approve(requestedBy, firstApproverId, secondApproverId, newApprover);
    }
}
