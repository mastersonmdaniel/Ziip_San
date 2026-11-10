package br.com.ziipsan.platform.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * II-8: a fachada RED/USE regista as métricas do lote e do sync com os
 * rótulos declarados — e recusa na origem qualquer tentativa com rótulo
 * proibido, restrito fora da allowlist ou desconhecido.
 */
class RedUseMetricsTest {

    @Test
    @DisplayName("itens do lote e do sync contam por outcome; duração do lote é timer")
    void recordsBatchAndSync() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RedUseMetrics metrics = new RedUseMetrics(registry);
        metrics.recordBatchItem(RedUseMetrics.Outcome.SUCCESS);
        metrics.recordBatchItem(RedUseMetrics.Outcome.SUCCESS);
        metrics.recordBatchItem(RedUseMetrics.Outcome.FAILURE);
        metrics.recordFieldSyncItem(RedUseMetrics.Outcome.SUCCESS);
        metrics.recordBatchDuration(Duration.ofMillis(250));

        assertThat(registry.find(RedUseMetrics.BATCH_ITEMS).tag("outcome", "SUCCESS").counter().count())
                .isEqualTo(2.0);
        assertThat(registry.find(RedUseMetrics.BATCH_ITEMS).tag("outcome", "FAILURE").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.find(RedUseMetrics.FIELD_SYNC_ITEMS).tag("outcome", "SUCCESS").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.find(RedUseMetrics.BATCH_DURATION).timer().count()).isEqualTo(1);
        assertThat(registry.find(RedUseMetrics.BATCH_DURATION).timer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                .isEqualTo(250.0);
    }

    @Test
    @DisplayName("tentativa com uc_id é recusada na origem e nada é registado")
    void forbiddenLabelRefusedAtSource() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RedUseMetrics metrics = new RedUseMetrics(registry);
        assertThatThrownBy(() -> metrics.counter("metrica_livre", Map.of("uc_id", "42")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("uc_id");
        assertThat(registry.getMeters()).isEmpty();
    }

    @Test
    @DisplayName("tenant fora da allowlist e rótulo desconhecido também são recusados")
    void restrictedAndUnknownRefused() {
        RedUseMetrics metrics = new RedUseMetrics(new SimpleMeterRegistry());
        assertThatThrownBy(() -> metrics.counter("http.server.requests", Map.of("tenant", "t-1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> metrics.counter("metrica", Map.of("cidade", "x")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("duração negativa é erro explícito")
    void negativeDurationRefused() {
        RedUseMetrics metrics = new RedUseMetrics(new SimpleMeterRegistry());
        assertThatThrownBy(() -> metrics.recordBatchDuration(Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
