package br.com.ziipsan.platform.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de persistência das tentativas de entrega, das supressões e do estado de
 * envio — TUDO por tenant: a consulta e a gravação recebem o tenant e nunca
 * enxergam outro. É este isolamento que faz um tenant com lista ruim não
 * interromper o envio dos demais: pausa, supressão e métricas são linhas do
 * tenant, não estado global.
 *
 * <p><b>Sem migration nesta entrega (declaração):</b> nem o aceite da P3.5 nem o
 * mapa Flyway do plano (§3) reservam versão para as tabelas de prova de entrega
 * — ao contrário das vizinhas (V1_15 da P3.36, V9_2 da P3.4, onde mora também a
 * prova do aviso de corte). A implementação JDBC desta porta nasce com a versão
 * que for reservada (faixa do módulo ou sequência global pós-P3.12); inventar a
 * tabela aqui violaria o mapa.
 */
public interface DeliveryStore {

    void saveAttempt(DeliveryAttempt attempt);

    void updateAttempt(DeliveryAttempt attempt);

    Optional<DeliveryAttempt> findByProviderMessageId(UUID tenantId, String providerMessageId);

    /** Tentativas de um aviso (a identidade é o id da mensagem da outbox que o originou). */
    List<DeliveryAttempt> attemptsForNotice(UUID tenantId, UUID noticeId);

    boolean isSuppressed(UUID tenantId, String emailAddress);

    void addSuppression(UUID tenantId, String emailAddress, EmailEventType cause, Instant at);

    /** Estado de envio do tenant: pausado ou não, e o motivo da pausa (nulo quando não pausado). */
    TenantSendingState sendingState(UUID tenantId);

    void setPaused(UUID tenantId, boolean paused, String reason);

    /** Evento do provedor já processado (idempotência dos efeitos no cadastro e na supressão). */
    boolean eventAlreadyProcessed(UUID tenantId, String providerEventId);

    void markEventProcessed(UUID tenantId, String providerEventId, Instant at);

    /**
     * Métricas do tenant no período (base da taxa de reclamação). Semântica que a
     * implementação DEVE seguir (esclarecida na revisão cruzada, porque a taxa
     * depende dela): {@code countSent} conta as tentativas com {@code sentAt} no
     * período, QUALQUER que seja a situação atual delas — contar só as que ainda
     * estão em {@code SENT} encolheria o denominador a cada resolução e inflaria a
     * taxa; {@code countComplaints} conta as tentativas resolvidas como
     * {@code COMPLAINED} com {@code resolvedAt} no período.
     */
    long countSent(UUID tenantId, Instant from, Instant to);

    long countComplaints(UUID tenantId, Instant from, Instant to);

    /** Estado de envio de um tenant (a pausa de um tenant nunca é a de outro). */
    record TenantSendingState(boolean paused, String pauseReason) {
        public static final TenantSendingState ACTIVE = new TenantSendingState(false, null);
    }
}
