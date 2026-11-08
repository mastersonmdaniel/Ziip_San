package br.com.ziipsan.platform.ai;

import br.com.ziipsan.platform.notification.multichannel.UsageMetric;
import br.com.ziipsan.platform.notification.multichannel.UsageRecord;
import br.com.ziipsan.platform.notification.multichannel.UsageStore;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Chatbot sobre a conversa da ED-42 (aceite ED-43). Bot RESTRITO ao atendimento do prestador, sem
 * assistente de proposito geral (politica Meta/WhatsApp desde 15/01/2026): so responde via funcao
 * fechada do portal. Ordem das guardas: funcao ligada -> provedor (DPA/RIPD/clausula/regiao/sem-treino)
 * -> credencial por referencia no cofre -> modelo avaliado -> PROMPT avaliado -> transbordo/injecao/
 * tema proibido/intencao nova (com ai.human_review_required invocado) -> mascara fail-closed (com log
 * tambem no ramo de falha) -> pre-checagem de cota/teto -> despacho deterministico da funcao fechada
 * (EXPLAIN_BILL via ED-38) -> modelo (porta) -> medicao SEMPRE -> descarte de vazamento na saida ->
 * reconciliacao pos-chamada de cota/teto -> log mascarado. Desligamento por tenant e por funcao =
 * enabledFeatures (classe b).
 * CORRECOES da revisao cruzada aplicadas nesta classe: M2 (saida), M3 (prompt), M4 (despacho ED-38),
 * m1 (medicao da chamada descartada), m2 (reconciliacao), m3 (human_review_required/intent), m4 (log). */
public class ChatbotService {
    public enum Outcome { ANSWERED, FEATURE_DISABLED, PROVIDER_BLOCKED, NO_CREDENTIAL, TRANSFERRED_TO_HUMAN,
                          QUOTA_BLOCKED, OUTPUT_DISCARDED_LEAK, MODEL_NOT_EVALUATED, PROMPT_NOT_EVALUATED }
    public record ChatRequest(UUID conversationId, String userText, String intent, String closedFunction,
                              Set<String> consumerPermissions, boolean humanRequested,
                              List<String> knownSensitiveValues, int messagesInWindow, List<String> recentTexts) {}
    public record ChatResult(Outcome outcome, String reply, String handoverReason, long tokensUsed) {}
    /** Referencia da conta em discussao na conversa (resolvida da conversa ED-42 pela fiacao). */
    public record BillRef(String consumerSubject, UUID billId) {}
    /** Porta de contexto: qual conta a conversa esta discutindo. Sem ela, EXPLAIN_BILL nao despacha. */
    public interface BillContextPort { Optional<BillRef> billFor(UUID tenantId, UUID conversationId); }

    public static final String PROMPT_ID = "chatbot";
    public static final String PROMPT_VERSION = "current";
    /** m2: estimativas da PRE-CHECAGEM apenas — declaradas, nomeadas e substituidas pelo consumo
     * REAL na reconciliacao pos-chamada (AiQuotaRule.reconciles). Nao sao preco nem tarifa. */
    static final long PRECHECK_TOKEN_ESTIMATE = 500;
    static final BigDecimal PRECHECK_COST_ESTIMATE = new BigDecimal("0.01");
    private static final String RETAINED = "[CONTEUDO-RETIDO]";

    private final AiParameters params; private final AiProviderGate.ProviderRegistryPort providers;
    private final ModelPort model; private final ModelPromptRegistry registry;
    private final AiConversationLog.LogStore log; private final UsageStore usage; private final Clock clock;
    private final java.util.function.LongSupplier usedTokens; private final java.util.function.Supplier<BigDecimal> spent;
    private final BillExplainFunction billExplain; private final BillContextPort billContext;
    public ChatbotService(AiParameters p, AiProviderGate.ProviderRegistryPort pr, ModelPort m, ModelPromptRegistry r,
                          AiConversationLog.LogStore l, UsageStore u, Clock c,
                          java.util.function.LongSupplier used, java.util.function.Supplier<BigDecimal> spent) {
        this(p, pr, m, r, l, u, c, used, spent, null, null);
    }
    public ChatbotService(AiParameters p, AiProviderGate.ProviderRegistryPort pr, ModelPort m, ModelPromptRegistry r,
                          AiConversationLog.LogStore l, UsageStore u, Clock c,
                          java.util.function.LongSupplier used, java.util.function.Supplier<BigDecimal> spent,
                          BillExplainFunction billExplain, BillContextPort billContext) {
        this.params=p; this.providers=pr; this.model=m; this.registry=r; this.log=l; this.usage=u; this.clock=c;
        this.usedTokens=used; this.spent=spent; this.billExplain=billExplain; this.billContext=billContext;
    }
    public ChatResult chat(UUID tenantId, ChatRequest req) {
        if (!params.enabledFeatures(tenantId).contains(AiFeature.CHATBOT))
            return new ChatResult(Outcome.FEATURE_DISABLED, null, "ai.enabled_features-off", 0);
        var reg = providers.registration(tenantId).orElse(null);
        var verdict = AiProviderGate.check(reg, params.providerRegion(tenantId).orElse(null));
        if (verdict != AiProviderGate.Verdict.ALLOWED)
            return new ChatResult(Outcome.PROVIDER_BLOCKED, null, verdict.name(), 0);
        var cred = params.credentialReference(tenantId);
        var modelId = params.modelId(tenantId);
        if (cred.isEmpty() || modelId.isEmpty())
            return new ChatResult(Outcome.NO_CREDENTIAL, null, "provider-not-configured-for-tenant", 0);
        if (!registry.usableModel(modelId.get(), PROMPT_VERSION))
            return new ChatResult(Outcome.MODEL_NOT_EVALUATED, null, "model-not-evaluated", 0);
        // M3: avaliacao de PROMPT imposta na mesma guarda — sem prompt avaliado, sem chamada ao modelo.
        if (!registry.usablePrompt(PROMPT_ID, PROMPT_VERSION))
            return new ChatResult(Outcome.PROMPT_NOT_EVALUATED, null, "prompt-not-evaluated", 0);
        boolean injection = PromptInjectionGuard.looksLikeInjection(req.userText());
        boolean hasFn = req.closedFunction() != null && ClosedFunctionRegistry.allowed(req.closedFunction(), req.consumerPermissions());
        // m3: ai.human_review_required invocado de fato; intent/isNewIntent ligados ao servico.
        boolean reviewRequired = params.humanReviewRequired(tenantId, AiFeature.CHATBOT);
        boolean newIntent = SentimentAndIntent.isNewIntent(req.intent());
        var decision = HumanHandover.decide(req.userText(), req.humanRequested(), hasFn, injection,
            SentimentAndIntent.sentiment(req.userText()),
            SentimentAndIntent.anomalous(req.messagesInWindow(), req.recentTexts()),
            reviewRequired, newIntent);
        String masked = PiiMasker.mask(req.userText(), req.knownSensitiveValues());
        if (decision.transfer()) {
            appendLog(tenantId, req, masked, null, 0, "TRANSFERRED:"+decision.reason());
            return new ChatResult(Outcome.TRANSFERRED_TO_HUMAN, null, decision.reason(), 0);
        }
        if (PiiMasker.containsStructuredPii(masked)) {
            // m4 (SG-701): o ramo de mascara-falhou TAMBEM grava log — com marcador de retencao,
            // nunca com o texto ofensor (o log so aceita texto mascarado).
            appendLog(tenantId, req, RETAINED, null, 0, "MASKING_FAILED_FAIL_CLOSED");
            return new ChatResult(Outcome.TRANSFERRED_TO_HUMAN, null, "masking-failed-fail-closed", 0);
        }
        var q = AiQuotaRule.check(params.tokenQuota(tenantId).orElse(null), usedTokens.getAsLong(), PRECHECK_TOKEN_ESTIMATE,
            params.costCeiling(tenantId).orElse(null), spent.get(), PRECHECK_COST_ESTIMATE);
        if (q != AiQuotaRule.Verdict.ALLOWED) return new ChatResult(Outcome.QUOTA_BLOCKED, null, q.name(), 0);
        // M4: EXPLAIN_BILL despacha pelo motor deterministico da ED-38 (GES-26); a IA so redige sobre ele.
        String modelInput = masked;
        if ("EXPLAIN_BILL".equals(req.closedFunction())) {
            if (billExplain == null || billContext == null) {
                appendLog(tenantId, req, masked, null, 0, "TRANSFERRED:bill-explain-not-wired");
                return new ChatResult(Outcome.TRANSFERRED_TO_HUMAN, null, "bill-explain-not-wired", 0);
            }
            var ref = billContext.billFor(tenantId, req.conversationId());
            if (ref.isEmpty()) {
                appendLog(tenantId, req, masked, null, 0, "TRANSFERRED:bill-context-missing");
                return new ChatResult(Outcome.TRANSFERRED_TO_HUMAN, null, "bill-context-missing", 0);
            }
            try {
                var deterministic = billExplain.explain(tenantId, ref.get().consumerSubject(), ref.get().billId());
                modelInput = BillExplainFunction.generativeWordingAllowed(deterministic.fullText());
            } catch (RuntimeException e) {
                appendLog(tenantId, req, masked, null, 0, "TRANSFERRED:bill-explain-unavailable");
                return new ChatResult(Outcome.TRANSFERRED_TO_HUMAN, null, "bill-explain-unavailable", 0);
            }
        }
        var result = model.complete(new ModelPort.ModelCall(tenantId, modelId.get(), cred.get(), PROMPT_ID+"@"+PROMPT_VERSION,
            List.of(new ModelPort.ModelMessage("user", modelInput)), 500));
        // m1: TODA chamada ao modelo e medida na ED-41 — inclusive a descartada por vazamento e a
        // reprovada na reconciliacao. Consumo real nunca fica subcontado.
        usage.append(new UsageRecord(tenantId, "ED-43", UsageMetric.AI_TOKENS, result.tokensUsed(), result.estimatedCost(), req.conversationId(), clock.instant()));
        // M2: simetria entrada/saida — a saida e descartada se carregar QUALQUER padrao que a mascara
        // de entrada cobre (CPF/CNPJ/e-mail/telefone/CEP, via detector completo) ou valor conhecido.
        if (PromptInjectionGuard.outputLeaksPii(result.text()) || PiiMasker.containsKnownValue(result.text(), req.knownSensitiveValues())) {
            appendLog(tenantId, req, masked, "[SAIDA-DESCARTADA]", result.tokensUsed(), "OUTPUT_LEAK_DISCARDED");
            return new ChatResult(Outcome.OUTPUT_DISCARDED_LEAK, null, "output-leaked-pii", result.tokensUsed());
        }
        // m2: reconciliacao com o consumo REAL pos-chamada; estouro retem a resposta (mas ja mediu).
        if (!AiQuotaRule.reconciles(params.tokenQuota(tenantId).orElse(null), usedTokens.getAsLong(), result.tokensUsed(),
                params.costCeiling(tenantId).orElse(null), spent.get(), result.estimatedCost())) {
            appendLog(tenantId, req, masked, null, result.tokensUsed(), "QUOTA_EXCEEDED_POST_CALL");
            return new ChatResult(Outcome.QUOTA_BLOCKED, null, "quota-exceeded-post-call-reconciliation", result.tokensUsed());
        }
        // M2: o consumidor recebe a MESMA versao mascarada que vai ao log — nunca o texto cru do modelo.
        String maskedReply = PiiMasker.mask(result.text(), req.knownSensitiveValues());
        appendLog(tenantId, req, masked, maskedReply, result.tokensUsed(), "ANSWERED");
        return new ChatResult(Outcome.ANSWERED, maskedReply, null, result.tokensUsed());
    }
    private void appendLog(UUID t, ChatRequest r, String maskedIn, String maskedOut, long tokens, String outcome) {
        // Defesa do SG-701: se por qualquer caminho restar PII no texto, o log guarda o marcador.
        String safeIn = PiiMasker.containsStructuredPii(maskedIn) ? RETAINED : maskedIn;
        log.append(new AiConversationLog(t, r.conversationId(), AiFeature.CHATBOT,
            params.modelId(t).orElse("unconfigured"), PROMPT_ID+"@"+PROMPT_VERSION, safeIn, maskedOut, tokens, outcome, false, clock.instant()));
    }
}
