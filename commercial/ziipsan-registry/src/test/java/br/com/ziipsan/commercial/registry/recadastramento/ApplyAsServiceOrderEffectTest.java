package br.com.ziipsan.commercial.registry.recadastramento;

import br.com.ziipsan.commercial.registry.quality.CorrectionQueueItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ED-16 — o "(teste)" do aceite: cada alteracao aprovada e aplicada
 * como efeito da OS de lote, com historico. So APPROVED aplica; o
 * cadastro que mudou depois da coleta vira ocorrencia incorreta
 * (STALE) e nao e sobrescrito; o encerramento executa a OS e resolve
 * a fila por ela.
 */
class ApplyAsServiceOrderEffectTest {

    @Test
    void alteracaoAprovadaEAplicadaComoEfeitoDaOsComHistorico() {
        var fixture = new RecadastramentoFixture();
        UUID unit = UUID.randomUUID();
        RecadastramentoBatch batch = fixture.openBatchForUnits(unit);
        fixture.reader.set("CONSUMER_UNIT", unit, "builtAreaM2", "100");
        UUID fieldId = fixture.service.ingest(RecadastramentoFixture.TENANT,
                fixture.submission(batch.id(), unit, null, "CONSUMER_UNIT", "builtAreaM2", "120"),
                RecadastramentoFixture.NOW).collectedFieldId();
        fixture.service.approve(fieldId, RecadastramentoFixture.APPROVER, RecadastramentoFixture.NOW);

        var result = fixture.service.applyApproved(batch.id(), RecadastramentoFixture.ACTOR,
                RecadastramentoFixture.NOW);

        assertThat(result.applied()).isEqualTo(1);
        // A aplicacao foi pela porta, com a OS do lote e os valores da coleta.
        assertThat(fixture.applier.calls).hasSize(1);
        var call = fixture.applier.calls.get(0);
        assertThat(call.serviceOrderId()).isEqualTo(batch.serviceOrderId());
        assertThat(call.targetId()).isEqualTo(unit);
        assertThat(call.oldValue()).isEqualTo("100");
        assertThat(call.newValue()).isEqualTo("120");
        // Historico gravado, ligado ao registro do cadastro e a OS.
        CollectedField applied = fixture.store.findCollected(fieldId).orElseThrow();
        assertThat(applied.status()).isEqualTo(CollectedField.Status.APPLIED);
        assertThat(applied.appliedChangeRequestId()).isEqualTo(call.changeRequestId());
        assertThat(fixture.store.findHistoryByBatch(batch.id())).hasSize(1);
        var entry = fixture.store.findHistoryByBatch(batch.id()).get(0);
        assertThat(entry.serviceOrderId()).isEqualTo(batch.serviceOrderId());
        assertThat(entry.changeRequestId()).isEqualTo(call.changeRequestId());
        assertThat(entry.collectorId()).isEqualTo(RecadastramentoFixture.COLLECTOR);
        assertThat(entry.approverId()).isEqualTo(RecadastramentoFixture.APPROVER);
        assertThat(entry.oldValue()).isEqualTo("100");
        assertThat(entry.newValue()).isEqualTo("120");
    }

    @Test
    void campoNaoAprovadoNuncaEAplicado() {
        var fixture = new RecadastramentoFixture();
        UUID unit = UUID.randomUUID();
        RecadastramentoBatch batch = fixture.openBatchForUnits(unit);
        fixture.reader.set("CONSUMER_UNIT", unit, "cnaeCode", "1234");
        fixture.service.ingest(RecadastramentoFixture.TENANT,
                fixture.submission(batch.id(), unit, null, "CONSUMER_UNIT", "cnaeCode", "5678"),
                RecadastramentoFixture.NOW);

        var result = fixture.service.applyApproved(batch.id(), RecadastramentoFixture.ACTOR,
                RecadastramentoFixture.NOW);

        assertThat(result.applied()).isZero();
        assertThat(result.awaitingDecision()).isEqualTo(1);
        assertThat(fixture.applier.calls).isEmpty();
        assertThat(fixture.store.findHistoryByBatch(batch.id())).isEmpty();
    }

    @Test
    void campoRejeitadoNuncaEAplicado() {
        var fixture = new RecadastramentoFixture();
        UUID unit = UUID.randomUUID();
        RecadastramentoBatch batch = fixture.openBatchForUnits(unit);
        fixture.reader.set("CONSUMER_UNIT", unit, "builtAreaM2", "100");
        UUID fieldId = fixture.service.ingest(RecadastramentoFixture.TENANT,
                fixture.submission(batch.id(), unit, null, "CONSUMER_UNIT", "builtAreaM2", "120"),
                RecadastramentoFixture.NOW).collectedFieldId();
        fixture.service.reject(fieldId, RecadastramentoFixture.APPROVER,
                "valor nao confere com a foto", RecadastramentoFixture.NOW);

        var result = fixture.service.applyApproved(batch.id(), RecadastramentoFixture.ACTOR,
                RecadastramentoFixture.NOW);

        assertThat(result.rejected()).isEqualTo(1);
        assertThat(fixture.applier.calls).isEmpty();
    }

    @Test
    void aprovadoSemDiferencaNaoGeraHistoricoNemChamaAplicador() {
        var fixture = new RecadastramentoFixture();
        UUID unit = UUID.randomUUID();
        RecadastramentoBatch batch = fixture.openBatchForUnits(unit);
        fixture.reader.set("CONSUMER_UNIT", unit, "readingSequence", "10");
        UUID fieldId = fixture.service.ingest(RecadastramentoFixture.TENANT,
                fixture.submission(batch.id(), unit, null, "CONSUMER_UNIT", "readingSequence", "10"),
                RecadastramentoFixture.NOW).collectedFieldId();
        fixture.service.approve(fieldId, RecadastramentoFixture.APPROVER, RecadastramentoFixture.NOW);

        var result = fixture.service.applyApproved(batch.id(), RecadastramentoFixture.ACTOR,
                RecadastramentoFixture.NOW);

        assertThat(result.withoutDifference()).isEqualTo(1);
        assertThat(result.applied()).isZero();
        assertThat(fixture.applier.calls).isEmpty();
        assertThat(fixture.store.findHistoryByBatch(batch.id())).isEmpty();
        assertThat(fixture.store.findCollected(fieldId).orElseThrow().status())
                .isEqualTo(CollectedField.Status.APPLIED);
    }

    @Test
    void aplicarDuasVezesNaoDuplicaEfeitoNemHistorico() {
        var fixture = new RecadastramentoFixture();
        UUID unit = UUID.randomUUID();
        RecadastramentoBatch batch = fixture.openBatchForUnits(unit);
        fixture.reader.set("CONSUMER_UNIT", unit, "builtAreaM2", "100");
        UUID fieldId = fixture.service.ingest(RecadastramentoFixture.TENANT,
                fixture.submission(batch.id(), unit, null, "CONSUMER_UNIT", "builtAreaM2", "120"),
                RecadastramentoFixture.NOW).collectedFieldId();
        fixture.service.approve(fieldId, RecadastramentoFixture.APPROVER, RecadastramentoFixture.NOW);

        fixture.service.applyApproved(batch.id(), RecadastramentoFixture.ACTOR, RecadastramentoFixture.NOW);
        var second = fixture.service.applyApproved(batch.id(), RecadastramentoFixture.ACTOR,
                RecadastramentoFixture.NOW);

        assertThat(second.applied()).isZero();
        assertThat(fixture.applier.calls).hasSize(1);
        assertThat(fixture.store.findHistoryByBatch(batch.id())).hasSize(1);
    }

    @Test
    void cadastroMudouDepoisDaColetaViraStaleENaoAplica() {
        var fixture = new RecadastramentoFixture();
        UUID unit = UUID.randomUUID();
        RecadastramentoBatch batch = fixture.openBatchForUnits(unit);
        fixture.reader.set("CONSUMER_UNIT", unit, "builtAreaM2", "100");
        UUID fieldId = fixture.service.ingest(RecadastramentoFixture.TENANT,
                fixture.submission(batch.id(), unit, null, "CONSUMER_UNIT", "builtAreaM2", "120"),
                RecadastramentoFixture.NOW).collectedFieldId();
        fixture.service.approve(fieldId, RecadastramentoFixture.APPROVER, RecadastramentoFixture.NOW);
        // O cadastro mudou por outro caminho depois da coleta.
        fixture.reader.set("CONSUMER_UNIT", unit, "builtAreaM2", "110");

        var result = fixture.service.applyApproved(batch.id(), RecadastramentoFixture.ACTOR,
                RecadastramentoFixture.NOW);

        assertThat(result.stale()).isEqualTo(1);
        assertThat(result.applied()).isZero();
        assertThat(fixture.applier.calls).isEmpty();
        CollectedField stale = fixture.store.findCollected(fieldId).orElseThrow();
        assertThat(stale.status()).isEqualTo(CollectedField.Status.STALE);
        assertThat(stale.valueAtApplication()).isEqualTo("110");
    }

    @Test
    void cadastroEsvaziadoDepoisDaColetaViraStaleComValorNulo() {
        var fixture = new RecadastramentoFixture();
        UUID unit = UUID.randomUUID();
        RecadastramentoBatch batch = fixture.openBatchForUnits(unit);
        fixture.reader.set("CONSUMER_UNIT", unit, "builtAreaM2", "100");
        UUID fieldId = fixture.service.ingest(RecadastramentoFixture.TENANT,
                fixture.submission(batch.id(), unit, null, "CONSUMER_UNIT", "builtAreaM2", "120"),
                RecadastramentoFixture.NOW).collectedFieldId();
        fixture.service.approve(fieldId, RecadastramentoFixture.APPROVER, RecadastramentoFixture.NOW);
        // O cadastro foi ESVAZIADO por outro caminho depois da coleta:
        // a porta de leitura devolve null para campo sem valor, e o
        // servico marca STALE com esse null (markStale(null)).
        fixture.reader.set("CONSUMER_UNIT", unit, "builtAreaM2", null);

        var result = fixture.service.applyApproved(batch.id(), RecadastramentoFixture.ACTOR,
                RecadastramentoFixture.NOW);

        assertThat(result.stale()).isEqualTo(1);
        assertThat(result.applied()).isZero();
        assertThat(fixture.applier.calls).isEmpty();
        CollectedField stale = fixture.store.findCollected(fieldId).orElseThrow();
        assertThat(stale.status()).isEqualTo(CollectedField.Status.STALE);
        // O null gravado E o valor vigente (esvaziamento), nao falta de leitura.
        assertThat(stale.valueAtApplication()).isNull();
    }

    @Test
    void markStaleAceitaValorNuloDoEsvaziamento() {
        // Direto no dominio: STALE com valor nulo e legitimo quando o
        // campo foi esvaziado apos a coleta (a porta devolve null).
        CollectedField field = CollectedField.collect(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), null, "CONSUMER_UNIT", "builtAreaM2", "100", "120",
                RecadastramentoFixture.COLLECTOR, List.of(), RecadastramentoFixture.NOW);
        field.approve(RecadastramentoFixture.APPROVER, RecadastramentoFixture.NOW);

        field.markStale(null);

        assertThat(field.status()).isEqualTo(CollectedField.Status.STALE);
        assertThat(field.valueAtApplication()).isNull();
    }

    @Test
    void staleBloqueiaFechamentoENovaColetaSupersede() {
        var fixture = new RecadastramentoFixture();
        UUID unit = UUID.randomUUID();
        RecadastramentoBatch batch = fixture.openBatchForUnits(unit);
        fixture.reader.set("CONSUMER_UNIT", unit, "builtAreaM2", "100");
        UUID firstId = fixture.service.ingest(RecadastramentoFixture.TENANT,
                fixture.submission(batch.id(), unit, null, "CONSUMER_UNIT", "builtAreaM2", "120"),
                RecadastramentoFixture.NOW).collectedFieldId();
        fixture.service.approve(firstId, RecadastramentoFixture.APPROVER, RecadastramentoFixture.NOW);
        fixture.reader.set("CONSUMER_UNIT", unit, "builtAreaM2", "110");
        fixture.service.applyApproved(batch.id(), RecadastramentoFixture.ACTOR, RecadastramentoFixture.NOW);

        assertThatThrownBy(() -> fixture.service.closeBatch(batch.id(),
                RecadastramentoFixture.ACTOR, RecadastramentoFixture.NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STALE");

        // Nova coleta do mesmo campo: supersede a STALE e segue o fluxo.
        UUID secondId = fixture.service.ingest(RecadastramentoFixture.TENANT,
                fixture.submission(batch.id(), unit, null, "CONSUMER_UNIT", "builtAreaM2", "130"),
                RecadastramentoFixture.NOW).collectedFieldId();
        assertThat(fixture.store.findCollected(firstId).orElseThrow().status())
                .isEqualTo(CollectedField.Status.SUPERSEDED);
        fixture.service.approve(secondId, RecadastramentoFixture.APPROVER, RecadastramentoFixture.NOW);
        var result = fixture.service.applyApproved(batch.id(), RecadastramentoFixture.ACTOR,
                RecadastramentoFixture.NOW);
        assertThat(result.applied()).isEqualTo(1);
        assertThat(fixture.applier.calls.get(0).oldValue()).isEqualTo("110");
        assertThat(fixture.applier.calls.get(0).newValue()).isEqualTo("130");

        RecadastramentoBatch closed = fixture.service.closeBatch(batch.id(),
                RecadastramentoFixture.ACTOR, RecadastramentoFixture.NOW);
        assertThat(closed.status()).isEqualTo(RecadastramentoBatch.Status.CLOSED);
    }

    @Test
    void fechamentoExecutaOsEResolveFilaPelaOs() {
        var fixture = new RecadastramentoFixture();
        UUID unit = UUID.randomUUID();
        RecadastramentoBatch batch = fixture.openBatchForUnits(unit);
        fixture.reader.set("CONSUMER_UNIT", unit, "builtAreaM2", "100");
        UUID fieldId = fixture.service.ingest(RecadastramentoFixture.TENANT,
                fixture.submission(batch.id(), unit, null, "CONSUMER_UNIT", "builtAreaM2", "120"),
                RecadastramentoFixture.NOW).collectedFieldId();
        fixture.service.approve(fieldId, RecadastramentoFixture.APPROVER, RecadastramentoFixture.NOW);
        fixture.service.applyApproved(batch.id(), RecadastramentoFixture.ACTOR, RecadastramentoFixture.NOW);

        RecadastramentoBatch closed = fixture.service.closeBatch(batch.id(),
                RecadastramentoFixture.ACTOR, RecadastramentoFixture.NOW);

        assertThat(closed.status()).isEqualTo(RecadastramentoBatch.Status.CLOSED);
        assertThat(fixture.gateway.executeCount).isEqualTo(1);
        assertThat(closed.confirmedUnitIds()).containsExactly(unit);
        // A fila resolveu os itens pela OS do lote.
        assertThat(fixture.queueStore.all())
                .allMatch(i -> i.status() == CorrectionQueueItem.Status.RESOLVED)
                .allMatch(i -> batch.serviceOrderId().equals(i.serviceOrderId()));
    }

    @Test
    void fechamentoComCampoSemDecisaoERecusado() {
        var fixture = new RecadastramentoFixture();
        UUID unit = UUID.randomUUID();
        RecadastramentoBatch batch = fixture.openBatchForUnits(unit);
        fixture.service.ingest(RecadastramentoFixture.TENANT,
                fixture.submission(batch.id(), unit, null, "CONSUMER_UNIT", "builtAreaM2", "120"),
                RecadastramentoFixture.NOW);

        assertThatThrownBy(() -> fixture.service.closeBatch(batch.id(),
                RecadastramentoFixture.ACTOR, RecadastramentoFixture.NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sem decisao");

        assertThat(fixture.store.findBatch(batch.id()).orElseThrow().status())
                .isEqualTo(RecadastramentoBatch.Status.OPEN);
        assertThat(fixture.gateway.executeCount).isZero();
        assertThat(fixture.queueStore.all())
                .allMatch(i -> i.status() == CorrectionQueueItem.Status.BATCHED);
    }
}
