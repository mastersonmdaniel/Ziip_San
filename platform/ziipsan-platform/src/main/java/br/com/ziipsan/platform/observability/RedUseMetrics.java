package br.com.ziipsan.platform.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * II-8: fachada das métricas RED/USE de processo do go-live — lote de
 * faturação e sincronização de campo. (O RED do HTTP já existe desde a
 * P0.8: {@code http.server.requests} com histograma; o USE de runtime já
 * vem do Actuator: {@code jvm.*}, {@code system.*}, {@code hikaricp.*},
 * {@code process.*}. Esta fachada cobre os dois processos de negócio que
 * os SLOs do go-live medem e que não tinham métrica própria.)
 *
 * <p>Métricas (vocabulário fechado, ver
 * {@code docs/observability/red-use-convencao-ii8.md}):
 * <ul>
 *   <li>{@code ziipsan_billing_batch_items_total{outcome}} — itens do
 *       lote por desfecho ({@code SUCCESS}/{@code FAILURE});</li>
 *   <li>{@code ziipsan_billing_batch_duration_seconds} — duração de cada
 *       execução do lote (timer sem rótulos);</li>
 *   <li>{@code ziipsan_field_sync_items_total{outcome}} — itens de sync
 *       de campo por desfecho.</li>
 * </ul>
 * Nenhuma leva identificador de UC, de tenant ou de rota concreta: o
 * lote agrega por desfecho, e o detalhe por UC vive no log/trace.
 *
 * <p>Toda a registação passa pela {@link MetricLabelPolicy}: tentar
 * registar com rótulo proibido (ex.: {@code uc_id}), restrito fora da
 * allowlist ou desconhecido lança {@link IllegalArgumentException} —
 * a tentativa é recusada na origem, antes de tocar no registry. A
 * entrada genérica {@link #counter(String, Map)} existe para os
 * chamadores futuros e é a porta que o teste da II-8 exercita com o
 * rótulo proibido.
 */
public final class RedUseMetrics {

    public static final String BATCH_ITEMS = "ziipsan_billing_batch_items_total";
    public static final String BATCH_DURATION = "ziipsan_billing_batch_duration_seconds";
    public static final String FIELD_SYNC_ITEMS = "ziipsan_field_sync_items_total";

    /** Desfechos fechados das métricas de processo (domínio do rótulo {@code outcome}). */
    public enum Outcome { SUCCESS, FAILURE }

    private final MeterRegistry registry;

    public RedUseMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "MeterRegistry é obrigatório");
    }

    public void recordBatchItem(Outcome outcome) {
        Objects.requireNonNull(outcome, "outcome é obrigatório");
        counter(BATCH_ITEMS, Map.of("outcome", outcome.name())).increment();
    }

    public void recordBatchDuration(Duration duration) {
        Objects.requireNonNull(duration, "duration é obrigatória");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("Duração do lote não pode ser negativa: " + duration);
        }
        Timer.builder(BATCH_DURATION).register(registry).record(duration.toNanos(), TimeUnit.NANOSECONDS);
    }

    public void recordFieldSyncItem(Outcome outcome) {
        Objects.requireNonNull(outcome, "outcome é obrigatório");
        counter(FIELD_SYNC_ITEMS, Map.of("outcome", outcome.name())).increment();
    }

    /**
     * Porta genérica validada: constrói (ou recupera) um contador depois
     * de validar nome e rótulos contra a {@link MetricLabelPolicy}.
     * Violação = {@link IllegalArgumentException} com a lista completa —
     * nada é registado no registry.
     */
    public Counter counter(String metricName, Map<String, String> tags) {
        Objects.requireNonNull(tags, "tags são obrigatórias (use mapa vazio, não null)");
        List<MetricLabelPolicy.Violation> violations = MetricLabelPolicy.validate(metricName, tags.keySet());
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(
                    "Rótulos recusados pela política de cardinalidade (II-8) em " + metricName + ": " + violations);
        }
        String[] flat = tags.entrySet().stream()
                .flatMap(e -> java.util.stream.Stream.of(e.getKey(), e.getValue()))
                .toArray(String[]::new);
        return registry.counter(metricName, flat);
    }
}
