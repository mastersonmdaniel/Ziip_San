package br.com.ziipsan.commercial.registry.recadastramento;

import br.com.ziipsan.commercial.registry.quality.CorrectionQueueItem;
import br.com.ziipsan.commercial.registry.quality.QualityRuleCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ED-16 — o lote de recadastramento abre como OS interna de lote
 * (ADR-0039), a partir da fila de correcao da qualidade cadastral da
 * P3.32: sem OS nao ha lote, e item nao agrupavel volta nomeado.
 */
class BatchOpeningFromQueueTest {

    @Test
    void loteNasceComoOsInternaDeLoteAPartirDaFila() {
        var fixture = new RecadastramentoFixture();
        UUID unitA = UUID.randomUUID();
        UUID unitB = UUID.randomUUID();

        RecadastramentoBatch batch = fixture.openBatchForUnits(unitA, unitB);

        // O lote aponta para a OS que o gateway abriu para a fila.
        assertThat(batch.serviceOrderId()).isEqualTo(fixture.gateway.lastOrderId);
        assertThat(batch.status()).isEqualTo(RecadastramentoBatch.Status.OPEN);
        assertThat(batch.targetUnitIds()).containsExactlyInAnyOrder(unitA, unitB);
        // Os itens da fila ficaram BATCHED nessa mesma OS.
        assertThat(fixture.queueStore.all())
                .allMatch(i -> i.status() == CorrectionQueueItem.Status.BATCHED)
                .allMatch(i -> batch.serviceOrderId().equals(i.serviceOrderId()));
    }

    @Test
    void itemDeClienteSemUnidadeLigadaVoltaNomeadoENaoTravaOLote() {
        var fixture = new RecadastramentoFixture();
        UUID unit = UUID.randomUUID();
        var unitItem = fixture.enqueueUnitFinding(unit, QualityRuleCode.ADDRESS_WITHOUT_COORDINATES);
        var customerItem = fixture.enqueueCustomerFinding(UUID.randomUUID(), QualityRuleCode.DOCUMENT_MISSING);

        var result = fixture.service.openBatch(List.of(unitItem.id(), customerItem.id()),
                RecadastramentoFixture.ACTOR, RecadastramentoFixture.NOW);

        assertThat(result.batch().targetUnitIds()).containsExactly(unit);
        assertThat(result.ungroupableItemIds()).containsExactly(customerItem.id());
        // O item nao agrupavel continua OPEN na fila (nao some em silencio).
        assertThat(customerItem.status()).isEqualTo(CorrectionQueueItem.Status.OPEN);
    }

    @Test
    void abrirSemItemAgrupavelERecusado() {
        var fixture = new RecadastramentoFixture();
        assertThatThrownBy(() -> fixture.service.openBatch(List.of(),
                RecadastramentoFixture.ACTOR, RecadastramentoFixture.NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fixture.service.openBatch(List.of(UUID.randomUUID()),
                RecadastramentoFixture.ACTOR, RecadastramentoFixture.NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nenhum item agrupavel");
        assertThat(fixture.store.allBatches()).isEmpty();
    }

    @Test
    void loteSemOsNaoExiste() {
        assertThatThrownBy(() -> RecadastramentoBatch.open(null,
                List.of(UUID.randomUUID()), List.of(UUID.randomUUID()),
                RecadastramentoFixture.ACTOR, RecadastramentoFixture.NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OS interna de lote");
    }
}
