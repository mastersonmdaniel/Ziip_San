package br.com.ziipsan.commercial.registry.recadastramento;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ED-16 — a coleta no app de campo da ED-18: idempotencia por task_id,
 * campo pre-definido, unidade do lote, documento mascarado e cada
 * recusa registrada como erro de coleta (a materia do relatorio
 * SG-911). A coleta nunca toca o cadastro: guarda coletado x vigente.
 */
class FieldCollectionIngestTest {

    @Test
    void coletaAceitaGuardaValorVigenteEMarcaDiferenca() {
        var fixture = new RecadastramentoFixture();
        UUID unit = UUID.randomUUID();
        RecadastramentoBatch batch = fixture.openBatchForUnits(unit);
        fixture.reader.set("CONSUMER_UNIT", unit, "builtAreaM2", "100");

        var receipt = fixture.service.ingest(RecadastramentoFixture.TENANT,
                fixture.submission(batch.id(), unit, null, "CONSUMER_UNIT", "builtAreaM2", "120"),
                RecadastramentoFixture.NOW);

        assertThat(receipt.duplicate()).isFalse();
        assertThat(receipt.difference()).isTrue();
        CollectedField stored = fixture.store.findCollected(receipt.collectedFieldId()).orElseThrow();
        assertThat(stored.valueAtCollection()).isEqualTo("100");
        assertThat(stored.collectedValue()).isEqualTo("120");
        assertThat(stored.status()).isEqualTo(CollectedField.Status.COLLECTED);
        // Nada foi aplicado: o aplicador nao foi chamado na coleta.
        assertThat(fixture.applier.calls).isEmpty();
    }

    @Test
    void coletaSemDiferencaMarcaDifferenceFalse() {
        var fixture = new RecadastramentoFixture();
        UUID unit = UUID.randomUUID();
        RecadastramentoBatch batch = fixture.openBatchForUnits(unit);
        fixture.reader.set("CONSUMER_UNIT", unit, "readingSequence", "10");

        var receipt = fixture.service.ingest(RecadastramentoFixture.TENANT,
                fixture.submission(batch.id(), unit, null, "CONSUMER_UNIT", "readingSequence", "10"),
                RecadastramentoFixture.NOW);

        assertThat(receipt.difference()).isFalse();
    }

    @Test
    void reenvioIdenticoDevolveDuplicadoSemNovaColeta() {
        var fixture = new RecadastramentoFixture();
        UUID unit = UUID.randomUUID();
        RecadastramentoBatch batch = fixture.openBatchForUnits(unit);
        UUID taskId = UUID.randomUUID();
        var submission = fixture.submission(batch.id(), taskId, unit, null,
                "CONSUMER_UNIT", "builtAreaM2", "120", false);

        var first = fixture.service.ingest(RecadastramentoFixture.TENANT, submission, RecadastramentoFixture.NOW);
        var second = fixture.service.ingest(RecadastramentoFixture.TENANT, submission, RecadastramentoFixture.NOW);

        assertThat(second.duplicate()).isTrue();
        assertThat(second.collectedFieldId()).isEqualTo(first.collectedFieldId());
        assertThat(fixture.store.findCollectedByBatch(batch.id())).hasSize(1);
        assertThat(fixture.store.findErrorsByBatch(batch.id())).isEmpty();
    }

    @Test
    void reenvioComCargaDiferenteViraErroDeConflito() {
        var fixture = new RecadastramentoFixture();
        UUID unit = UUID.randomUUID();
        RecadastramentoBatch batch = fixture.openBatchForUnits(unit);
        UUID taskId = UUID.randomUUID();
        fixture.service.ingest(RecadastramentoFixture.TENANT,
                fixture.submission(batch.id(), taskId, unit, null, "CONSUMER_UNIT", "builtAreaM2", "120", false),
                RecadastramentoFixture.NOW);

        assertThatThrownBy(() -> fixture.service.ingest(RecadastramentoFixture.TENANT,
                fixture.submission(batch.id(), taskId, unit, null, "CONSUMER_UNIT", "builtAreaM2", "130", false),
                RecadastramentoFixture.NOW))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(fixture.store.findCollectedByBatch(batch.id())).hasSize(1);
        assertThat(fixture.store.findErrorsByBatch(batch.id()))
                .extracting(CollectionError::code)
                .containsExactly(CollectionErrorCode.TASK_PAYLOAD_CONFLICT);
    }

    @Test
    void campoForaDoCatalogoViraErroENaoCriaColeta() {
        var fixture = new RecadastramentoFixture();
        UUID unit = UUID.randomUUID();
        RecadastramentoBatch batch = fixture.openBatchForUnits(unit);

        assertThatThrownBy(() -> fixture.service.ingest(RecadastramentoFixture.TENANT,
                fixture.submission(batch.id(), unit, null, "CONSUMER_UNIT", "campoInventado", "x"),
                RecadastramentoFixture.NOW))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(fixture.store.findCollectedByBatch(batch.id())).isEmpty();
        assertThat(fixture.store.findErrorsByBatch(batch.id()))
                .extracting(CollectionError::code)
                .containsExactly(CollectionErrorCode.FIELD_NOT_PREDEFINED);
    }

    @Test
    void unidadeForaDoLoteEValorVazioViramErros() {
        var fixture = new RecadastramentoFixture();
        UUID unit = UUID.randomUUID();
        RecadastramentoBatch batch = fixture.openBatchForUnits(unit);

        assertThatThrownBy(() -> fixture.service.ingest(RecadastramentoFixture.TENANT,
                fixture.submission(batch.id(), UUID.randomUUID(), null, "CONSUMER_UNIT", "builtAreaM2", "120"),
                RecadastramentoFixture.NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fixture.service.ingest(RecadastramentoFixture.TENANT,
                fixture.submission(batch.id(), unit, null, "CONSUMER_UNIT", "builtAreaM2", "   "),
                RecadastramentoFixture.NOW))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(fixture.store.findErrorsByBatch(batch.id()))
                .extracting(CollectionError::code)
                .containsExactly(CollectionErrorCode.UNIT_NOT_IN_BATCH, CollectionErrorCode.VALUE_BLANK);
    }

    @Test
    void documentoSemMascaraViraErroEComMascaraColeta() {
        var fixture = new RecadastramentoFixture();
        UUID unit = UUID.randomUUID();
        UUID customer = UUID.randomUUID();
        RecadastramentoBatch batch = fixture.openBatchForUnits(unit);
        fixture.reader.set("CUSTOMER", customer, "document", "***00");

        assertThatThrownBy(() -> fixture.service.ingest(RecadastramentoFixture.TENANT,
                fixture.submission(batch.id(), UUID.randomUUID(), unit, customer,
                        "CUSTOMER", "document", "12345678909", false),
                RecadastramentoFixture.NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(fixture.store.findErrorsByBatch(batch.id()))
                .extracting(CollectionError::code)
                .containsExactly(CollectionErrorCode.DOCUMENT_NOT_MASKED);

        var receipt = fixture.service.ingest(RecadastramentoFixture.TENANT,
                fixture.submission(batch.id(), UUID.randomUUID(), unit, customer,
                        "CUSTOMER", "document", "***09", true),
                RecadastramentoFixture.NOW);
        assertThat(receipt.difference()).isTrue();
    }

    @Test
    void aparelhoRecusadoNaoGeraErroNemColeta() {
        var fixture = new RecadastramentoFixture();
        UUID unit = UUID.randomUUID();
        RecadastramentoBatch batch = fixture.openBatchForUnits(unit);
        fixture.gate.refusing = true;

        assertThatThrownBy(() -> fixture.service.ingest(RecadastramentoFixture.TENANT,
                fixture.submission(batch.id(), unit, null, "CONSUMER_UNIT", "builtAreaM2", "120"),
                RecadastramentoFixture.NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nao sincroniza");

        assertThat(fixture.store.findCollectedByBatch(batch.id())).isEmpty();
        assertThat(fixture.store.findErrorsByBatch(batch.id())).isEmpty();
    }
}
