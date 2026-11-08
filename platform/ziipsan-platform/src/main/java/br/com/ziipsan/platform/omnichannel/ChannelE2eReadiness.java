package br.com.ziipsan.platform.omnichannel;

import java.util.List;

/**
 * Prontidão do teste de ponta a ponta por canal (aceite da ED-42: "cada
 * canal com teste de ponta a ponta em conta de teste"). Conta de teste
 * real (Meta/WhatsApp/Telegram/provedor de e-mail) NÃO existe nesta VM e
 * NÃO é fingida: todos os canais nascem {@code PENDING_EXTERNAL} e este
 * registro é a lista do que o ensaio real precisa executar, canal a
 * canal, para o item virar {@code PASSED} — por evidência do ensaio, na
 * integração/LC, nunca por declaração deste código.
 */
public final class ChannelE2eReadiness {

    public enum Status { PENDING_EXTERNAL }

    public record Check(OmnichannelChannel channel, Status status, List<String> steps) {
    }

    private static final List<String> COMMON_STEPS = List.of(
            "webhook do provedor apontado para o Ziip SAN com o segredo no cofre do tenant de teste",
            "provedor do canal registrado como aprovado no DPA do tenant de teste",
            "opt-in do morador de teste registrado no canal",
            "mensagem inbound do morador de teste vira conversa no protocolo da matrícula de teste",
            "resposta do atendente na tela chega ao morador de teste no mesmo canal/thread",
            "uso da conversa aparece na medição da ED-41 do tenant de teste",
            "cadeia de integridade do tenant de teste confere (IntegrityLog.verifyChain)");

    private ChannelE2eReadiness() {
    }

    public static List<Check> all() {
        return List.of(OmnichannelChannel.values()).stream()
                .map(channel -> new Check(channel, Status.PENDING_EXTERNAL, COMMON_STEPS))
                .toList();
    }
}
