package br.com.ziipsan.platform.notification;

import java.util.UUID;

/**
 * Porta para a interface de envio de e-mail da P0.45. FRONTEIRA expressa do aceite:
 * o provedor é decidido na LC.2 (o recurso de tenants do Amazon SES em
 * {@code sa-east-1} é a referência) — esta tarefa modela a interface e NÃO decide
 * provedor; nenhum SDK de provedor aparece neste módulo (a P0.45 já recusa SDK de
 * provedor em módulo de domínio, por teste de fronteira dela).
 *
 * <p>O isolamento por tenant vive aqui e no {@link DeliveryStore}: identidade de
 * envio, reputação, métricas, pausa e supressão são DO tenant — enviar é sempre em
 * nome do tenant da mensagem, com o domínio dele ({@code msg.email_from_domain}).
 */
public interface EmailGateway {

    /**
     * Envia a mensagem montada a partir do domínio do tenant e devolve o id da
     * mensagem no provedor — a chave que correlaciona os eventos
     * {@code delivered/bounced/complained} com a tentativa de entrega.
     */
    String send(UUID tenantId, String fromDomain, String toAddress, RenderedMessage message);

    /** Estado de autenticação (SPF/DKIM/DMARC) do domínio de envio do tenant, no provedor. */
    SenderDomainStatus domainStatus(UUID tenantId, String fromDomain);
}
