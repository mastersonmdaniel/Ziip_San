package br.com.ziipsan.integration.hub;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * P3.37: fila de falhas com reprocessamento de mensagens. Toda chamada
 * que esgota as repetições do despacho (ou é recusada de negócio e a
 * operação manda para a fila) vira uma {@link FailedMessage} com a carga
 * ORIGINAL guardada para reprocessar — e a VISÃO da fila
 * ({@link FailedMessageView}) expõe só a carga mascarada: a tela de
 * reprocessamento nunca mostra segredo nem dado pessoal.
 *
 * <p>Reprocessar repete a MESMA chave de idempotência e o MESMO
 * {@code correlation_id} acrescido do sufixo da tentativa na correlação
 * da chamada (a correlação guardada na mensagem não muda — é a da
 * operação original; o despacho do reprocesso recebe a sua própria).
 * Sucesso resolve a mensagem; falha conta tentativa; ao atingir
 * {@code maxAttempts} a mensagem morre ({@code DEAD}) e NÃO é mais
 * reprocessável — reprocessar morta é erro explícito, não no-op.
 *
 * <p>A persistência é a porta {@link FailureStore}; a implementação em
 * memória acompanha para o ambiente local/teste, no espírito da P0.45.
 */
public final class FailureQueueService {

    public enum FailureStatus { PENDING, RESOLVED, DEAD }

    /** Mensagem falhada, como guardada (carga original só aqui dentro; a visão mascara). */
    public record FailedMessage(UUID id, UUID tenantId, String integrationName, String operation,
                                String correlationId, String idempotencyKey, String payloadJson,
                                int attempts, int maxAttempts, FailureStatus status,
                                String lastError, Instant enqueuedAt, Instant lastAttemptAt) {
        public FailedMessage {
            Objects.requireNonNull(id, "id da mensagem falhada é obrigatório");
            Objects.requireNonNull(tenantId, "tenantId é obrigatório na mensagem falhada");
            IntegrationParameters.requireValidName(integrationName);
            if (correlationId == null || correlationId.isBlank()) {
                throw new IllegalArgumentException("correlation_id é obrigatório na mensagem falhada");
            }
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                throw new IllegalArgumentException("chave de idempotência é obrigatória na mensagem falhada");
            }
            if (payloadJson == null) {
                throw new IllegalArgumentException("carga é obrigatória na mensagem falhada");
            }
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("maxAttempts da mensagem deve ser >= 1: " + maxAttempts);
            }
            if (attempts < 0 || attempts > maxAttempts) {
                throw new IllegalArgumentException("tentativas fora de 0.." + maxAttempts + ": " + attempts);
            }
            Objects.requireNonNull(status, "situação é obrigatória na mensagem falhada");
            Objects.requireNonNull(enqueuedAt, "instante de entrada é obrigatório na mensagem falhada");
        }

        /** Visão para a tela: carga mascarada, sem a original. */
        public FailedMessageView view() {
            return new FailedMessageView(id, tenantId, integrationName, operation, correlationId,
                    PayloadMasker.maskForLog(payloadJson), attempts, maxAttempts, status, lastError,
                    enqueuedAt, lastAttemptAt);
        }
    }

    /** O que a tela de reprocessamento mostra (carga mascarada). */
    public record FailedMessageView(UUID id, UUID tenantId, String integrationName, String operation,
                                    String correlationId, String maskedPayloadJson,
                                    int attempts, int maxAttempts, FailureStatus status,
                                    String lastError, Instant enqueuedAt, Instant lastAttemptAt) {
    }

    /** Porta de persistência da fila. */
    public interface FailureStore {
        void save(FailedMessage message);

        Optional<FailedMessage> findById(UUID id);

        /** Mensagens do tenant (todas as situações), na ordem de entrada. */
        List<FailedMessage> listByTenant(UUID tenantId);

        /** Quantas PENDING por (tenant, integração) — profundidade da fila no painel de saúde. */
        long countPending(UUID tenantId, String integrationName);
    }

    /** Implementação em memória (ambiente local e testes; a fiação troca pela persistente). */
    public static final class InMemoryFailureStore implements FailureStore {
        private final Map<UUID, FailedMessage> byId = new LinkedHashMap<>();

        @Override
        public synchronized void save(FailedMessage message) {
            byId.put(message.id(), message);
        }

        @Override
        public synchronized Optional<FailedMessage> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public synchronized List<FailedMessage> listByTenant(UUID tenantId) {
            List<FailedMessage> out = new ArrayList<>();
            for (FailedMessage m : byId.values()) {
                if (m.tenantId().equals(tenantId)) {
                    out.add(m);
                }
            }
            return List.copyOf(out);
        }

        @Override
        public synchronized long countPending(UUID tenantId, String integrationName) {
            long count = 0;
            for (FailedMessage m : byId.values()) {
                if (m.tenantId().equals(tenantId) && m.integrationName().equals(integrationName)
                        && m.status() == FailureStatus.PENDING) {
                    count++;
                }
            }
            return count;
        }
    }

    private final FailureStore store;

    public FailureQueueService(FailureStore store) {
        this.store = Objects.requireNonNull(store, "loja da fila de falhas é obrigatória");
    }

    /** Enfileira uma falha: nasce PENDING com 1 tentativa já contada (a que falhou no despacho). */
    public FailedMessage enqueue(UUID tenantId, String integrationName, String operation,
                                 String correlationId, String idempotencyKey, String payloadJson,
                                 int maxAttempts, String lastError, Instant now) {
        Objects.requireNonNull(now, "instante é obrigatório ao enfileirar");
        FailedMessage message = new FailedMessage(UUID.randomUUID(), tenantId, integrationName, operation,
                correlationId, idempotencyKey, payloadJson, 1, maxAttempts,
                maxAttempts <= 1 ? FailureStatus.DEAD : FailureStatus.PENDING, lastError, now, now);
        store.save(message);
        return message;
    }

    /**
     * Reprocessa UMA mensagem pendente pelo conector dado, no contexto
     * dado: mesma chave de idempotência e carga original. Devolve a
     * mensagem atualizada (RESOLVED, PENDING com tentativa contada, ou
     * DEAD ao esgotar). Mensagem inexistente, resolvida ou morta: erro
     * explícito.
     */
    public FailedMessage reprocess(UUID messageId, ExternalConnector connector,
                                   ExternalConnector.ConnectorContext context, Instant now) {
        Objects.requireNonNull(connector, "conector é obrigatório no reprocessamento");
        Objects.requireNonNull(context, "contexto é obrigatório no reprocessamento");
        Objects.requireNonNull(now, "instante é obrigatório no reprocessamento");
        FailedMessage message = store.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("mensagem falhada não encontrada: " + messageId));
        if (message.status() != FailureStatus.PENDING) {
            throw new IllegalStateException("só mensagem PENDING pode ser reprocessada; mensagem "
                    + messageId + " está " + message.status());
        }
        if (!connector.name().equals(message.integrationName())) {
            throw new IllegalArgumentException("conector " + connector.name()
                    + " não é o da mensagem (" + message.integrationName() + ")");
        }
        if (!context.tenantId().equals(message.tenantId())) {
            throw new IllegalArgumentException("contexto de tenant " + context.tenantId()
                    + " não é o da mensagem (" + message.tenantId() + "): reprocesso não cruza tenant");
        }
        ExternalConnector.ConnectorRequest request = new ExternalConnector.ConnectorRequest(
                message.correlationId(), message.idempotencyKey(), message.operation(), message.payloadJson());
        ExternalConnector.ConnectorResponse response;
        try {
            response = connector.send(context, request);
        } catch (RuntimeException e) {
            response = new ExternalConnector.ConnectorResponse(
                    ExternalConnector.Outcome.UNAVAILABLE, null, null, e.getMessage());
        }
        int attempts = message.attempts() + 1;
        FailedMessage updated;
        if (response.isSuccess()) {
            updated = copy(message, attempts, FailureStatus.RESOLVED, null, now);
        } else if (attempts >= message.maxAttempts()) {
            updated = copy(message, attempts, FailureStatus.DEAD, response.detail(), now);
        } else {
            updated = copy(message, attempts, FailureStatus.PENDING, response.detail(), now);
        }
        store.save(updated);
        return updated;
    }

    /** A tela da fila de um tenant (visões mascaradas, ordem de entrada). */
    public List<FailedMessageView> queueView(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório na visão da fila");
        List<FailedMessageView> out = new ArrayList<>();
        for (FailedMessage m : store.listByTenant(tenantId)) {
            out.add(m.view());
        }
        return List.copyOf(out);
    }

    public long pendingCount(UUID tenantId, String integrationName) {
        return store.countPending(tenantId, integrationName);
    }

    private static FailedMessage copy(FailedMessage m, int attempts, FailureStatus status,
                                      String lastError, Instant lastAttemptAt) {
        return new FailedMessage(m.id(), m.tenantId(), m.integrationName(), m.operation(),
                m.correlationId(), m.idempotencyKey(), m.payloadJson(), attempts, m.maxAttempts(),
                status, lastError, m.enqueuedAt(), lastAttemptAt);
    }
}
