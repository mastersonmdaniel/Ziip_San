package br.com.ziipsan.platform.notification.multichannel;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta para o cadastro da P1.3 (CAD-84, DAD-30) nos canais da ED-41: resolve
 * o contato de TELEFONE (SMS/WhatsApp) e o contato de PUSH (dispositivo) do
 * destinatário para a finalidade, sempre do cadastro — o destino NUNCA vem
 * da carga do evento (a P1.38 proíbe dado pessoal na carga), como a P3.5 já
 * faz para o e-mail via {@code ContactRegistry}. Ausência de contato é
 * resultado explícito, nunca destino presumido.
 *
 * <p>É a contrapartida multicanal do {@code ContactRegistry} da P3.5, que só
 * resolve e-mail; esta porta não altera aquela.
 */
public interface ChannelContactRegistry {

    /** Contato telefônico vigente e verificado do destinatário para a finalidade, se houver. */
    Optional<ChannelContact> resolvePhoneContact(UUID tenantId, UUID customerId, UUID userId, String purposeCode);

    /** Contato de push (token do dispositivo) vigente do destinatário para a finalidade, se houver. */
    Optional<ChannelContact> resolvePushContact(UUID tenantId, UUID customerId, UUID userId, String purposeCode);

    /** Marca o contato como inválido no cadastro (efeito de um evento REJECTED do provedor). */
    void markContactInvalid(UUID tenantId, UUID contactId, String reason);
}
