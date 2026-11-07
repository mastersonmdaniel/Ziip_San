package br.com.ziipsan.platform.central;

/**
 * Chaves de métrica da Central usadas pelas abas da P2.11. São as linhas
 * que a migration core {@code V9_12} grava em
 * {@code platform.metric_definition} — sem a linha, {@code metric_point}
 * e {@code alert_rule} recusam a chave por FK (V9_1). Convenção da V9_1:
 * {@code domain.object.measure}, em minúsculas.
 */
public final class CentralMetrics {

    /** Itens obrigatórios do cronograma do ciclo vencidos e não realizados, por tenant. */
    public static final String BILLING_CYCLE_OVERDUE_ITEMS = "billing.cycle.overdue_items";

    /** Documentos fiscais aguardando desfecho (fila), por tenant. */
    public static final String FISCAL_QUEUE_PENDING = "fiscal.queue.pending";

    /** Idade (s) do documento aguardando desfecho há mais tempo, por tenant. */
    public static final String FISCAL_QUEUE_OLDEST_PENDING_AGE_S = "fiscal.queue.oldest_pending_age_s";

    private CentralMetrics() {
    }
}
