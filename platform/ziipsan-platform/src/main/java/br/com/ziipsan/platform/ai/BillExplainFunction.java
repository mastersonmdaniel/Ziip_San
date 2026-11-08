package br.com.ziipsan.platform.ai;

import br.com.ziipsan.platform.portal.selfcare.AssistantBillFunction;
import br.com.ziipsan.platform.portal.selfcare.BillExplanation;

import java.util.UUID;

/** Explicacao da variacao da conta (GES-26): funcao FECHADA do chatbot e da visao 360 do atendente,
 * sobre o motor DETERMINISTICO da ED-38 (BillExplanation/AssistantBillFunction, CAD-102). A IA
 * generativa, quando ligada, so REDIGE sobre os fatores deterministicos — numero nenhum nasce do modelo.
 * Gate: bill.explain_enabled; desligado = funcao recusada. */
public final class BillExplainFunction {
    private final AiParameters params; private final AssistantBillFunction delegate;
    public BillExplainFunction(AiParameters p, AssistantBillFunction d) { this.params=p; this.delegate=d; }
    public BillExplanation.Explanation explain(UUID tenantId, String consumerSubject, UUID billId) {
        if (!params.billExplainEnabled(tenantId)) throw new IllegalStateException("bill.explain_enabled desligado para o tenant");
        if (!params.enabledFeatures(tenantId).contains(AiFeature.BILL_EXPLAIN))
            throw new IllegalStateException("funcao BILL_EXPLAIN fora de ai.enabled_features do tenant");
        return delegate.explainBill(tenantId, consumerSubject, billId);
    }
    /** Redacao opcional: recebe SOMENTE o texto deterministico mascarado; o chamador valida vazamento. */
    public static String generativeWordingAllowed(String deterministicText) {
        if (PiiMasker.containsStructuredPii(deterministicText)) throw new IllegalArgumentException("texto deterministico com PII nao vai ao modelo");
        return deterministicText;
    }
}
