package br.com.ziipsan.platform.infra.queue;

/**
 * Handler de um tipo de mensagem da outbox (P1.9, ADR-0014).
 *
 * <p>Entrega ao menos uma vez: todo handler TEM de ser idempotente, porque
 * a mensagem pode ser entregue de novo (posse vencida, falha após o efeito
 * parcial, reinício do worker). O worker marca a mensagem como concluída
 * só depois que {@link #handle(OutboxMessage)} retorna sem exceção; qualquer
 * exceção vira nova tentativa com backoff e, no limite, estado DEAD.
 */
public interface MessageHandler {

    /** Tipo atendido, igual ao {@code type} da mensagem (ex.: {@code billing.invoice.issued}). */
    String handledType();

    /** Executa o efeito da mensagem. Recebe o tenant explícito em {@link OutboxMessage#tenantId()}. */
    void handle(OutboxMessage message) throws Exception;
}
