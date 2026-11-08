package br.com.ziipsan.platform.ai;

import java.util.UUID;

/** OPE-40: rascunhos assistidos. parecer de encerramento da OS limitado a 400 caracteres (catalogo de OS);
 * ditado so com assist.voice_enabled e provedor de assistencia configurado (assist.provider), sem default.
 * Rascunho NUNCA e final: exige marcacao de revisao do executor (reviewedByExecutor) para ser aceito. */
public final class AssistDraftService {
    public static final int OS_OPINION_MAX_CHARS = 400;
    private final AiParameters params;
    public AssistDraftService(AiParameters p) { this.params = p; }
    public record Draft(String text, boolean reviewedByExecutor) {
        public Draft requireReviewed() {
            if (!reviewedByExecutor) throw new IllegalStateException("rascunho de IA exige revisao do executor antes do uso");
            return this;
        }
        /** CORRECAO ED-43 (m5): transicao do fluxo OPE-40 — rascunho -> revisao do executor ->
         * utilizavel. O rascunho e imutavel: a revisao devolve uma NOVA instancia marcada, e so
         * ela passa em requireReviewed(). Sem esta transicao o fluxo nao tinha caminho de uso. */
        public Draft markReviewedByExecutor() { return new Draft(text, true); }
    }
    /** Caminho de uso do OPE-40 no servico: o executor revisa o rascunho e o marca; o resultado
     * e o unico Draft que requireReviewed() aceita. */
    public Draft reviewByExecutor(Draft draft) {
        if (draft == null) throw new IllegalArgumentException("rascunho obrigatorio para revisao");
        return draft.markReviewedByExecutor();
    }
    public Draft osOpinionDraft(UUID tenantId, String modelDraftText) {
        if (!params.enabledFeatures(tenantId).contains(AiFeature.OS_OPINION_DRAFT))
            throw new IllegalStateException("OS_OPINION_DRAFT fora de ai.enabled_features");
        if (modelDraftText == null || modelDraftText.isBlank()) throw new IllegalArgumentException("rascunho vazio");
        String t = modelDraftText.length() > OS_OPINION_MAX_CHARS ? modelDraftText.substring(0, OS_OPINION_MAX_CHARS) : modelDraftText;
        return new Draft(t, false);
    }
    public Draft shiftSummaryDraft(UUID tenantId, String modelDraftText) {
        if (!params.enabledFeatures(tenantId).contains(AiFeature.SHIFT_SUMMARY))
            throw new IllegalStateException("SHIFT_SUMMARY fora de ai.enabled_features");
        if (modelDraftText == null || modelDraftText.isBlank()) throw new IllegalArgumentException("rascunho vazio");
        return new Draft(modelDraftText, false);
    }
    public void requireDictationReady(UUID tenantId) {
        if (!params.voiceEnabled(tenantId)) throw new IllegalStateException("assist.voice_enabled desligado");
        if (params.assistProvider(tenantId).isEmpty()) throw new IllegalStateException("assist.provider nao configurado para o tenant (sem default)");
    }
}
