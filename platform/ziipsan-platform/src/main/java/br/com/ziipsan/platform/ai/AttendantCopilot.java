package br.com.ziipsan.platform.ai;

import java.util.List;
import java.util.UUID;

/** Copiloto do atendente (GES-26): resume o historico do protocolo com o MESMO mascaramento do chatbot.
 * E AssistDraftService cobre OPE-40: ditado e rascunho do parecer de encerramento da OS (limite de 400
 * caracteres do catalogo de OS) e do resumo de turno, SEMPRE revisados pelo executor, sem acesso livre
 * do modelo ao banco — o texto de entrada e o que o executor/atendente fornece nesta chamada. */
public final class AttendantCopilot {
    private AttendantCopilot() {}
    public static String summarizeInput(List<String> protocolMessages, List<String> sensitiveValues) {
        String joined = String.join(" ", protocolMessages == null ? List.of() : protocolMessages);
        String masked = PiiMasker.mask(joined, sensitiveValues);
        if (PiiMasker.containsStructuredPii(masked)) throw new IllegalArgumentException("resumo recusado: PII estruturada apos mascara");
        return masked;
    }
}
