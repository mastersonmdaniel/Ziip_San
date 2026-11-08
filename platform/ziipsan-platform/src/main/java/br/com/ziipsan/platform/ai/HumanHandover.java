package br.com.ziipsan.platform.ai;

import br.com.ziipsan.core.support.HelpDeflection;

/** Transbordo humano: o chatbot NUNCA e beco sem saida. A regra de quando transferir e a da P4.8
 * (HelpDeflection: pedido de humano transfere SEMPRE; sem artigo/funcao, transfere) combinada com a
 * ED-35/ED-43: tema proibido (ADR-0042), injecao, sentimento negativo persistente, anomalia e
 * intencao nova sem funcao fechada tambem transbordam. A execucao da transferencia e a fila da
 * ED-42/ED-35 — aqui decide-se, la executa-se. */
public final class HumanHandover {
    public record Decision(boolean transfer, String reason) {}
    private HumanHandover() {}
    public static Decision decide(String userText, boolean humanRequestedFlag, boolean hasClosedFunction,
                                  boolean injectionDetected, SentimentAndIntent.Sentiment sentiment, boolean anomalous) {
        return decide(userText, humanRequestedFlag, hasClosedFunction, injectionDetected, sentiment, anomalous, false, false);
    }
    /** CORRECAO ED-43 (m3): o parametro travado ai.human_review_required e INVOCADO no fluxo —
     * com a trava ligada, tema proibido transborda nomeando a trava; e a intencao NOVA (campo
     * intent fora do conjunto conhecido) transborda para curadoria mesmo havendo funcao fechada,
     * em vez de so transbordar quando tambem falta funcao. */
    public static Decision decide(String userText, boolean humanRequestedFlag, boolean hasClosedFunction,
                                  boolean injectionDetected, SentimentAndIntent.Sentiment sentiment, boolean anomalous,
                                  boolean humanReviewRequired, boolean newIntent) {
        if (humanRequestedFlag || HelpDeflection.requestsHuman(userText)) return new Decision(true, "human-requested");
        if (humanReviewRequired && ForbiddenDecision.isForbiddenTopic(userText)) return new Decision(true, "human-review-required-locked");
        if (ForbiddenDecision.isForbiddenTopic(userText)) return new Decision(true, "forbidden-decision-human-review-locked");
        if (injectionDetected) return new Decision(true, "prompt-injection");
        if (anomalous) return new Decision(true, "anomalous-behavior-sg699");
        if (sentiment == SentimentAndIntent.Sentiment.NEGATIVE) return new Decision(true, "negative-sentiment");
        if (newIntent) return new Decision(true, "new-intent-curation");
        if (!hasClosedFunction) return new Decision(true, "no-closed-function");
        return new Decision(false, "bot-may-answer");
    }
}
