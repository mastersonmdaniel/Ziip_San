package br.com.ziipsan.platform.portal.selfcare;

import java.util.UUID;

/**
 * Funcao da explicacao da conta EXPOSTA ao assistente da ED-43 (ED-38):
 * o aceite manda a explicacao existir "no portal e como funcao disponivel
 * ao assistente da ED-43". Esta interface e o contrato da funcao; o
 * assistente (conversa, NLU, canais) e da ED-43 e NAO e construido aqui.
 * A implementacao delega a funcao pura {@link BillExplanation} sobre o
 * calculo gravado obtido pela porta — o assistente nunca recebe dado que
 * o portal nao mostraria ao titular da unidade vinculada.
 */
public interface AssistantBillFunction {

    BillExplanation.Explanation explainBill(UUID tenantId, String consumerSubject, UUID billId);

    /** Implementacao padrao sobre a porta do calculo gravado. */
    static AssistantBillFunction over(RecordedCalculationPort port, LinkedUnitPort linked) {
        if (port == null || linked == null) {
            throw new IllegalArgumentException("portas sao obrigatorias");
        }
        return (tenantId, consumerSubject, billId) -> {
            if (tenantId == null || consumerSubject == null || consumerSubject.isBlank() || billId == null) {
                throw new IllegalArgumentException("tenant, consumidor e conta sao obrigatorios");
            }
            if (!linked.isBillOfLinkedUnit(tenantId, consumerSubject, billId)) {
                throw new IllegalStateException("Conta de unidade nao vinculada a esta conta do portal.");
            }
            return BillExplanation.explain(port.recordedCalculation(tenantId, billId)
                    .orElseThrow(() -> new IllegalStateException("conta sem calculo gravado: " + billId)));
        };
    }

    interface RecordedCalculationPort {
        java.util.Optional<BillExplanation.RecordedCalculation> recordedCalculation(UUID tenantId, UUID billId);
    }

    interface LinkedUnitPort {
        boolean isBillOfLinkedUnit(UUID tenantId, String consumerSubject, UUID billId);
    }
}
