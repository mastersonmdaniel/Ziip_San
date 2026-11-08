package br.com.ziipsan.platform.ai;

import java.time.Instant;
import java.util.UUID;

/** Registro de cada conversa com dado MASCARADO e retencao propria (PLT-18; SG-701 auditoria da conversa):
 * o log guarda o texto JA mascarado, a funcao usada, tokens/custo, o veredito das guardas e se houve
 * correcao/transbordo humano. Retencao: ai.log_retention_days (classe c); purge apos o prazo. */
public record AiConversationLog(UUID tenantId, UUID conversationId, AiFeature feature, String modelId,
                                String promptVersion, String maskedUserText, String maskedReplyText,
                                long tokensUsed, String outcome, boolean humanCorrected, Instant occurredAt) {
    public AiConversationLog {
        if (tenantId == null || conversationId == null || feature == null || occurredAt == null)
            throw new IllegalArgumentException("log de IA exige tenant, conversa, funcao e instante");
        if (PiiMasker.containsStructuredPii(maskedUserText == null ? "" : maskedUserText)
            || PiiMasker.containsStructuredPii(maskedReplyText == null ? "" : maskedReplyText))
            throw new IllegalArgumentException("log de IA so aceita texto mascarado (SG-700/SG-701)");
    }
    public interface LogStore { void append(AiConversationLog log); java.util.List<AiConversationLog> byConversation(UUID tenantId, UUID conversationId); }
    public static boolean retentionExpired(AiConversationLog log, int retentionDays, Instant now) {
        return retentionDays > 0 && log.occurredAt().plusSeconds(retentionDays * 86400L).isBefore(now);
    }
}
