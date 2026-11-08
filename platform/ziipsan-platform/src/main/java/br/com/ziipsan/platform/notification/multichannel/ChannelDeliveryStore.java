package br.com.ziipsan.platform.notification.multichannel;

import br.com.ziipsan.platform.notification.NotificationChannel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de persistência das tentativas multicanal e dos eventos de provedor
 * — TUDO por tenant, como o {@code DeliveryStore} da P3.5. A tabela
 * correspondente ({@code msg_channel_deliveries}) e a de idempotência dos
 * eventos vão no SQL pendente da ED-41 (sem número global; a numeração é
 * atribuída no pouso pelo coordenador).
 */
public interface ChannelDeliveryStore {

    void save(ChannelDelivery delivery);

    void update(ChannelDelivery delivery);

    Optional<ChannelDelivery> findByProviderMessageId(UUID tenantId, String providerMessageId);

    List<ChannelDelivery> attemptsForNotice(UUID tenantId, UUID noticeId);

    /**
     * Soma dos custos estimados das tentativas CRIADAS no período, neste
     * tenant — a base do teto mensal ({@code msg.monthly_cap}): conta toda
     * tentativa nascida no período, qualquer que seja a situação atual dela
     * (o gasto nasce no envio, não na resolução).
     */
    BigDecimal sumEstimatedCost(UUID tenantId, Instant from, Instant to);

    /**
     * Contagens do período para a entregabilidade medida, por tenant e canal.
     *
     * <p><b>COORTE TEMPORAL ÚNICA (contrato fixado na correção D4 da
     * revisão):</b> as DUAS contagens usam a MESMA base — o {@code sentAt} da
     * tentativa. {@code countSent} conta as tentativas com {@code sentAt} em
     * {@code [from, to]}; {@code countWithStatus} conta, DENTRE ESSA MESMA
     * COORTE (tentativas com {@code sentAt} em {@code [from, to]}), as que
     * estão ATUALMENTE na situação pedida — o {@code resolvedAt} NUNCA entra
     * no recorte da janela. É essa unicidade que garante o invariante da
     * {@link DeliverabilityRule} (entregues + falhas + rejeitadas ≤ envios)
     * em qualquer janela: com bases mistas (envio por {@code sentAt},
     * resolução por {@code resolvedAt}), uma mensagem enviada antes da
     * janela e entregue dentro dela faria as resolvidas excederem os envios
     * e a entregabilidade lançaria exceção em vez de devolver a taxa. A
     * implementação JDBC (P-ED41-04) DEVE seguir esta coorte.
     */
    long countSent(UUID tenantId, NotificationChannel channel, Instant from, Instant to);

    long countWithStatus(UUID tenantId, NotificationChannel channel, ChannelDeliveryStatus status,
                         Instant from, Instant to);

    boolean eventAlreadyProcessed(UUID tenantId, String providerEventId);

    void markEventProcessed(UUID tenantId, String providerEventId, Instant at);
}
