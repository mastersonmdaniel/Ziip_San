package br.com.ziipsan.platform.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * II-8, teste §4.3 da estimativa (mecânica do harness): o lote corre
 * todos os itens, o placar do relatório bate com o processador, as
 * falhas são contadas e nomeadas, e as métricas agregadas conferem.
 * A prova sobre a massa real da base 12k corre pelo harness de PG
 * descartável do pacote (scripts/observability/), com os totais
 * conhecidos da base — ver RELATORIO-II-8.
 */
class BillingBatchReleaseProofTest {

    private BillingBatchReleaseProof harness(SimpleMeterRegistry registry) {
        return new BillingBatchReleaseProof(new RedUseMetrics(registry));
    }

    @Test
    @DisplayName("lote íntegro: placar completo, relatório verde e métricas batem")
    void fullBatchPasses() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        List<String> items = List.of("b1", "b2", "b3", "b4", "b5");
        BillingBatchReleaseProof.BatchReport report = harness(registry).run(items, id -> true);

        assertThat(report.totalItems()).isEqualTo(5);
        assertThat(report.succeeded()).isEqualTo(5);
        assertThat(report.failed()).isZero();
        assertThat(report.failedItemIds()).isEmpty();
        assertThat(report.complete()).isTrue();
        assertThat(report.itemsPerSecond()).isPositive();
        assertThat(registry.find(RedUseMetrics.BATCH_ITEMS).tag("outcome", "SUCCESS").counter().count())
                .isEqualTo(5.0);
        assertThat(registry.find(RedUseMetrics.BATCH_DURATION).timer().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("falhas contam, são nomeadas e o lote não aborta")
    void failuresAreCountedAndNamed() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        List<String> items = List.of("b1", "b2", "b3");
        BillingBatchReleaseProof.BatchReport report = harness(registry).run(items, id -> {
            if (id.equals("b2")) {
                throw new IllegalStateException("falha simulada do item");
            }
            return !id.equals("b3");
        });

        assertThat(report.totalItems()).isEqualTo(3);
        assertThat(report.succeeded()).isEqualTo(1);
        assertThat(report.failed()).isEqualTo(2);
        assertThat(report.failedItemIds()).containsExactly("b2", "b3");
        assertThat(report.complete()).isFalse();
        assertThat(registry.find(RedUseMetrics.BATCH_ITEMS).tag("outcome", "FAILURE").counter().count())
                .isEqualTo(2.0);
    }

    @Test
    @DisplayName("lote vazio é recusado: massa ausente não é placar zero verde")
    void emptyBatchRefused() {
        BillingBatchReleaseProof harness = harness(new SimpleMeterRegistry());
        assertThatThrownBy(() -> harness.run(List.of(), id -> true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Lote vazio");
    }
}
