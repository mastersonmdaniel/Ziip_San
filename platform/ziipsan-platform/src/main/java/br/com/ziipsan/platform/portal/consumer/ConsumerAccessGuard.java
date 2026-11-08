package br.com.ziipsan.platform.portal.consumer;

import br.com.ziipsan.core.api.ApiException;

/**
 * Canal de acesso aos dados do consumidor (P3.20; ADR-0043, item 5):
 * historico, documentos e vinculo SOMENTE com conta propria ou gov.br.
 * O caminho de CONVIDADO da P3.3 (codigo -> fatura em aberto, 5 campos)
 * NUNCA da acesso a historico, documentos nem vinculo.
 */
public final class ConsumerAccessGuard {

    public enum Channel { OWN_ACCOUNT, GOV_BR, GUEST }

    private ConsumerAccessGuard() { }

    public static void requireAuthenticatedChannel(Channel channel) {
        if (channel == null) {
            throw new IllegalArgumentException("canal e obrigatorio");
        }
        if (channel == Channel.GUEST) {
            throw new ApiException.Forbidden(
                    "Historico, documentos e vinculo exigem conta propria ou gov.br.");
        }
    }
}
