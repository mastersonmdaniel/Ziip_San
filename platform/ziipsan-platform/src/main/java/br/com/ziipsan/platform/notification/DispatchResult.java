package br.com.ziipsan.platform.notification;

import java.util.UUID;

/**
 * Resultado de um disparo (ou de um canal de um disparo): o que aconteceu e por
 * quê, sem ambiguidade — "não enviado" sempre carrega o motivo nomeado, para a
 * trilha e para a regra de prova (aviso não enviado não tem prova de entrega).
 * Os motivos de fronteira são honestos: SMS, WhatsApp e push são da ED-41 e o
 * remetente autenticado de SMS/voz (MP 1.396/2026) é aplicado por ela — aqui
 * eles NÃO são enviados nem simulados.
 */
public record DispatchResult(Outcome outcome, UUID attemptId, String reason) {

    public enum Outcome {
        /** Mensagem aceita pelo gateway; a tentativa aguarda o evento do provedor. */
        SENT,
        /** Canal fora de {@code msg.channels_enabled} do tenant. */
        CHANNEL_DISABLED,
        /** Preferência do destinatário desliga o canal para a finalidade (categoria não obrigatória). */
        PREFERENCE_OFF,
        /** Endereço na lista de supressão do tenant. */
        SUPPRESSED,
        /** Envio do tenant pausado (pausa é do tenant; os demais seguem enviando). */
        TENANT_PAUSED,
        /** Taxa de reclamação do tenant atingiu 0,10%: o tenant foi pausado até regularizar. */
        COMPLAINT_RATE_BREACH,
        /** Domínio do tenant sem SPF, DKIM e DMARC alinhados. */
        DOMAIN_NOT_ALIGNED,
        /** Sem contato de e-mail verificado/resolvido no cadastro para o destinatário. */
        NO_CONTACT,
        /** Canal cuja entrega é da ED-41 (SMS, WhatsApp, push): não enviado por esta tarefa. */
        FRONTIER_ED41,
        /** Canal IN_APP: a linha nasce na central da P3.36, não neste serviço. */
        DELEGATED_TO_CENTRAL
    }

    public boolean sent() {
        return outcome == Outcome.SENT;
    }

    static DispatchResult of(Outcome outcome, String reason) {
        return new DispatchResult(outcome, null, reason);
    }

    static DispatchResult sent(UUID attemptId) {
        return new DispatchResult(Outcome.SENT, attemptId, null);
    }
}
