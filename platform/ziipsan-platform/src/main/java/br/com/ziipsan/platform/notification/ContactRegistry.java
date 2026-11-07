package br.com.ziipsan.platform.notification;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta para o cadastro da P1.3 (CAD-84, DAD-30): resolve o contato de e-mail do
 * destinatário e recebe os efeitos de uma entrega fracassada. Rejeição
 * ({@code bounced}) ou reclamação ({@code complained}) MARCA o contato como
 * inválido no cadastro e ABRE PENDÊNCIA para o atendimento — os dois efeitos são
 * desta porta; chamá-la é obrigação do serviço de entrega, uma única vez por
 * evento (a idempotência é do serviço, pela identidade do evento do provedor).
 */
public interface ContactRegistry {

    /**
     * O contato de e-mail vigente do destinatário para a finalidade, ou vazio
     * quando não há contato — ausência é resultado explícito, nunca endereço
     * presumido. Exatamente um de {@code customerId}/{@code userId} é informado.
     */
    Optional<RecipientContact> resolveEmailContact(UUID tenantId, UUID customerId, UUID userId, String purposeCode);

    /** Marca o contato como inválido no cadastro, com o motivo vindo do evento do provedor. */
    void markContactInvalid(UUID tenantId, UUID contactId, String reason);

    /** Abre a pendência do contato inválido e devolve o id dela (caixa de pendências do atendimento). */
    UUID openInvalidContactPendency(UUID tenantId, UUID customerId, UUID contactId, String reason);
}
