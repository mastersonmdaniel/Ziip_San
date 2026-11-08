package br.com.ziipsan.platform.ai;

import java.util.List;
import java.util.UUID;

/** Porta do modelo de linguagem de terceiro (aceite; ADR-0042/0042): provedor por CONFIGURACAO do
 * tenant, sem default de fabrica; a chave chega a borda SOMENTE pela referencia do cofre
 * (credentialReference), nunca por valor neste nucleo — o mesmo padrao da ED-42.
 * O nucleo nunca acessa banco livremente: o modelo so recebe o prompt montado das funcoes fechadas. */
public interface ModelPort {
    record ModelMessage(String role, String maskedText) {}
    record ModelCall(UUID tenantId, String modelId, String credentialReference,
                     String promptVersion, List<ModelMessage> messages, int maxTokens) {}
    record ModelResult(String text, long tokensUsed, java.math.BigDecimal estimatedCost) {}
    ModelResult complete(ModelCall call);
    /** Duble honesto para prova local/CI: devolve texto determinístico derivado da ultima mensagem. */
    static ModelPort honestStub() {
        return call -> new ModelResult("Resposta do duble de prova sobre: "
            + call.messages().get(call.messages().size()-1).maskedText(), 10, java.math.BigDecimal.ZERO);
    }
}
