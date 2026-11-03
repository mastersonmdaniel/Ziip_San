package br.com.ziipsan.platform.infra.queue;

import java.util.UUID;

/**
 * Mensagem da outbox (P1.9, ADR-0014), como o worker a entrega ao handler.
 *
 * <p>O tenant vem explícito na mensagem: o worker abre o contexto a cada
 * execução a partir daqui e NUNCA lê o {@code TenantContextHolder} em
 * ThreadLocal (que, no worker, está sempre vazio). Um handler que precise
 * do tenant usa {@link #tenantId()}; um handler que tente resolver o tenant
 * pelo ThreadLocal falha — e é esse o comportamento testado.
 */
public record OutboxMessage(UUID id, UUID tenantId, String queue, String type,
                            String payloadJson, int attempts) {
    public OutboxMessage {
        if (id == null || tenantId == null) {
            throw new IllegalArgumentException("id e tenantId sao obrigatorios");
        }
        if (queue == null || queue.isBlank()) {
            throw new IllegalArgumentException("queue da mensagem e obrigatoria");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type da mensagem e obrigatorio");
        }
        if (payloadJson == null) {
            throw new IllegalArgumentException("payloadJson e obrigatorio");
        }
        if (attempts < 0) {
            throw new IllegalArgumentException("attempts nao pode ser negativo");
        }
    }
}
