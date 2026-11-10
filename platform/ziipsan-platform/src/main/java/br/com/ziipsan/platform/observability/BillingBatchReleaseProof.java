package br.com.ziipsan.platform.observability;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * II-8: harness do lote de faturação como PROVA DE RELEASE.
 *
 * <p>Desenho: o harness corre um lote de itens através de uma porta de
 * processamento ({@link ItemProcessor}) — em produção, o motor de
 * faturação já provado (ciclo da P2.5 sobre o {@code BillCalculator}/
 * {@code TariffEngine} das camadas C2/C4, que este pacote NÃO altera);
 * na prova local, o processador que a massa da base fictícia de 12.000
 * UCs alimenta. O harness regista, por execução: total de itens,
 * sucessos, falhas (com os identificadores dos itens falhados, para o
 * relatório — nunca como rótulo de métrica), duração total e ritmo
 * (itens/segundo). É este relatório que alimenta a baseline do SLO de
 * conclusão do lote (ver {@code docs/observability/slo-go-live-ii8.md}):
 * sem relatório de lote não há baseline, e sem baseline o alvo fica
 * {@code [A DEFINIR]}.
 *
 * <p>Regras herdadas do método do projeto:
 * <ul>
 *   <li>lote vazio é erro explícito, nunca "0 itens em 0 ms" verde —
 *       ausência de massa esconderia exactamente a falha que a prova
 *       existe para mostrar (mesma regra do {@link BurnRateCalculator});</li>
 *   <li>falha de um item não aborta o lote: conta-se como FAILURE e o
 *       lote continua, como faz o ciclo real; o relatório diz quantos
 *       e quais falharam;</li>
 *   <li>as métricas do lote saem pela {@link RedUseMetrics} (só
 *       desfecho agregado; identificador de item nunca é rótulo).</li>
 * </ul>
 */
public final class BillingBatchReleaseProof {

    /** Porta do motor: processa um item do lote; {@code false} ou exceção = falha do item. */
    @FunctionalInterface
    public interface ItemProcessor {
        boolean process(String itemId) throws Exception;
    }

    /** Evidência de uma execução do lote (a unidade da baseline do SLO-3). */
    public record BatchReport(
            int totalItems,
            int succeeded,
            int failed,
            List<String> failedItemIds,
            Duration duration,
            double itemsPerSecond) {

        public BatchReport {
            failedItemIds = List.copyOf(failedItemIds);
        }

        /** O lote só é prova de release verde com todos os itens processados. */
        public boolean complete() {
            return failed == 0 && succeeded == totalItems;
        }
    }

    private final RedUseMetrics metrics;

    public BillingBatchReleaseProof(RedUseMetrics metrics) {
        this.metrics = Objects.requireNonNull(metrics, "RedUseMetrics é obrigatória");
    }

    /**
     * Corre o lote sobre os itens dados. {@code itemIds} são
     * identificadores opacos para o relatório (ex.: ids das contas da
     * massa); nunca viram rótulo de métrica.
     */
    public BatchReport run(List<String> itemIds, ItemProcessor processor) {
        Objects.requireNonNull(itemIds, "itemIds são obrigatórios");
        Objects.requireNonNull(processor, "processor é obrigatório");
        if (itemIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "Lote vazio não é prova de release: sem itens não há placar (massa ausente, não zero)");
        }
        long start = System.nanoTime();
        int succeeded = 0;
        List<String> failedIds = new java.util.ArrayList<>();
        for (String itemId : itemIds) {
            boolean ok;
            try {
                ok = processor.process(itemId);
            } catch (Exception e) {
                ok = false;
            }
            if (ok) {
                succeeded++;
                metrics.recordBatchItem(RedUseMetrics.Outcome.SUCCESS);
            } else {
                failedIds.add(itemId);
                metrics.recordBatchItem(RedUseMetrics.Outcome.FAILURE);
            }
        }
        Duration duration = Duration.ofNanos(System.nanoTime() - start);
        metrics.recordBatchDuration(duration);
        double perSecond = duration.isZero() ? itemIds.size()
                : itemIds.size() / (duration.toNanos() / 1_000_000_000.0);
        return new BatchReport(itemIds.size(), succeeded, failedIds.size(), failedIds, duration, perSecond);
    }
}
