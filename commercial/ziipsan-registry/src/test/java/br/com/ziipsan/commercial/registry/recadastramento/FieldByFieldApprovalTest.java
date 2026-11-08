package br.com.ziipsan.commercial.registry.recadastramento;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ED-16 — aprovacao CAMPO A CAMPO: decidir um campo nao decide outro,
 * a decisao e unica e a rejeicao exige motivo.
 */
class FieldByFieldApprovalTest {

    private record TwoFields(RecadastramentoFixture fixture, RecadastramentoBatch batch,
                             UUID areaId, UUID sequenceId) {
    }

    private TwoFields twoCollectedFields() {
        var fixture = new RecadastramentoFixture();
        UUID unit = UUID.randomUUID();
        RecadastramentoBatch batch = fixture.openBatchForUnits(unit);
        fixture.reader.set("CONSUMER_UNIT", unit, "builtAreaM2", "100");
        fixture.reader.set("CONSUMER_UNIT", unit, "readingSequence", "10");
        UUID areaId = fixture.service.ingest(RecadastramentoFixture.TENANT,
                fixture.submission(batch.id(), unit, null, "CONSUMER_UNIT", "builtAreaM2", "120"),
                RecadastramentoFixture.NOW).collectedFieldId();
        UUID sequenceId = fixture.service.ingest(RecadastramentoFixture.TENANT,
                fixture.submission(batch.id(), unit, null, "CONSUMER_UNIT", "readingSequence", "11"),
                RecadastramentoFixture.NOW).collectedFieldId();
        return new TwoFields(fixture, batch, areaId, sequenceId);
    }

    @Test
    void aprovarUmCampoNaoAprovaOutro() {
        TwoFields two = twoCollectedFields();

        two.fixture().service.approve(two.areaId(), RecadastramentoFixture.APPROVER, RecadastramentoFixture.NOW);

        assertThat(two.fixture().store.findCollected(two.areaId()).orElseThrow().status())
                .isEqualTo(CollectedField.Status.APPROVED);
        assertThat(two.fixture().store.findCollected(two.areaId()).orElseThrow().decidedBy())
                .isEqualTo(RecadastramentoFixture.APPROVER);
        assertThat(two.fixture().store.findCollected(two.sequenceId()).orElseThrow().status())
                .isEqualTo(CollectedField.Status.COLLECTED);
    }

    @Test
    void rejeitarExigeMotivoEGuardaADecisao() {
        TwoFields two = twoCollectedFields();

        assertThatThrownBy(() -> two.fixture().service.reject(two.areaId(),
                RecadastramentoFixture.APPROVER, "  ", RecadastramentoFixture.NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("motivo");

        CollectedField rejected = two.fixture().service.reject(two.areaId(),
                RecadastramentoFixture.APPROVER, "foto ilegivel, valor nao confere",
                RecadastramentoFixture.NOW);
        assertThat(rejected.status()).isEqualTo(CollectedField.Status.REJECTED);
        assertThat(rejected.decisionReason()).isEqualTo("foto ilegivel, valor nao confere");
        // O outro campo segue intocado.
        assertThat(two.fixture().store.findCollected(two.sequenceId()).orElseThrow().status())
                .isEqualTo(CollectedField.Status.COLLECTED);
    }

    @Test
    void rejeitarRespeitaLimiteDe300CaracteresDoMotivo() {
        TwoFields two = twoCollectedFields();
        // Fronteira: 300 caracteres (o VARCHAR(300) da entidade/SQL) e aceito.
        String motivo300 = "x".repeat(300);
        CollectedField rejected = two.fixture().service.reject(two.areaId(),
                RecadastramentoFixture.APPROVER, motivo300, RecadastramentoFixture.NOW);
        assertThat(rejected.status()).isEqualTo(CollectedField.Status.REJECTED);
        assertThat(rejected.decisionReason()).hasSize(300);

        // 301 e recusado no dominio, antes de mudar o estado — nao so
        // na persistencia.
        String motivo301 = "x".repeat(301);
        assertThatThrownBy(() -> two.fixture().service.reject(two.sequenceId(),
                RecadastramentoFixture.APPROVER, motivo301, RecadastramentoFixture.NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("300");
        assertThat(two.fixture().store.findCollected(two.sequenceId()).orElseThrow().status())
                .isEqualTo(CollectedField.Status.COLLECTED);
        assertThat(two.fixture().store.findCollected(two.sequenceId()).orElseThrow().decisionReason())
                .isNull();
    }

    @Test
    void decisaoEUnicaPorCampo() {
        TwoFields two = twoCollectedFields();
        two.fixture().service.approve(two.areaId(), RecadastramentoFixture.APPROVER, RecadastramentoFixture.NOW);

        assertThatThrownBy(() -> two.fixture().service.approve(two.areaId(),
                RecadastramentoFixture.APPROVER, RecadastramentoFixture.NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> two.fixture().service.reject(two.areaId(),
                RecadastramentoFixture.APPROVER, "tarde demais", RecadastramentoFixture.NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decidirCampoInexistenteERecusado() {
        TwoFields two = twoCollectedFields();
        assertThatThrownBy(() -> two.fixture().service.approve(UUID.randomUUID(),
                RecadastramentoFixture.APPROVER, RecadastramentoFixture.NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nao encontrado");
    }
}
