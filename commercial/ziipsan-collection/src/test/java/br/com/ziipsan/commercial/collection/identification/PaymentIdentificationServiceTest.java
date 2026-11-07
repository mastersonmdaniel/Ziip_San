package br.com.ziipsan.commercial.collection.identification;

import br.com.ziipsan.commercial.collection.identification.PaymentIdentificationService.Outcome;
import br.com.ziipsan.commercial.collection.identification.UnidentifiedPaymentQueue.QueueReason;
import br.com.ziipsan.commercial.collection.identification.UnidentifiedPaymentQueue.QueueStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P3.29 - fila sem cliente ficticio e OS PAYMENT_IDENTIFICATION. */
class PaymentIdentificationServiceTest {

    private static final UUID PAYMENT = UUID.randomUUID();
    private static final UUID OS_ID = UUID.randomUUID();
    private static final UUID UNIT = UUID.randomUUID();
    private static final UUID BILL = UUID.randomUUID();

    @Test
    void entradaDaFilaNaoTemClienteUnidadeNemDocumento() {
        var entry = UnidentifiedPaymentQueue.QueueEntry.unidentified(
                PAYMENT, QueueReason.UNIDENTIFIED_CLASSIFICATION);
        assertThat(entry.status()).isEqualTo(QueueStatus.NA_FILA);
        assertThat(entry.unitId()).isNull();
        assertThat(entry.documentId()).isNull();
        assertThat(entry.hasLink()).isFalse();
        // Enfileirar de novo devolve a mesma entrada (sem duplicar).
        assertThat(UnidentifiedPaymentQueue.enqueue(entry, PAYMENT,
                QueueReason.UNIDENTIFIED_CLASSIFICATION)).isSameAs(entry);
    }

    @Test
    void identificacaoVinculaUnidadeEDocumentoEDevolveAP31() {
        var service = new PaymentIdentificationService();
        var entry = UnidentifiedPaymentQueue.QueueEntry.unidentified(PAYMENT, QueueReason.PIX_WITHOUT_TXID);
        var result = service.identify(entry, OS_ID, UNIT, "BILL", BILL);
        assertThat(result.outcome()).isEqualTo(Outcome.IDENTIFIED_AND_RETURNED);
        assertThat(result.entry().status()).isEqualTo(QueueStatus.DEVOLVIDO_CLASSIFICACAO);
        assertThat(result.handoff().paymentId()).isEqualTo(PAYMENT);
        assertThat(result.handoff().unitId()).isEqualTo(UNIT);
        // M3: o handoff carrega documentId E documentType, SEM renomear para billId.
        assertThat(result.handoff().documentId()).isEqualTo(BILL);
        assertThat(result.handoff().documentType()).isEqualTo("BILL");
    }

    @Test
    void inicioDaIdentificacaoMarcaEmIdentificacao() {
        var service = new PaymentIdentificationService();
        var entry = UnidentifiedPaymentQueue.QueueEntry.unidentified(
                PAYMENT, QueueReason.UNIDENTIFIED_CLASSIFICATION);
        var emIdentificacao = service.beginIdentification(entry);
        assertThat(emIdentificacao.status()).isEqualTo(QueueStatus.EM_IDENTIFICACAO);
        // Comecar de novo e idempotente; e identificar a partir dai funciona.
        assertThat(service.beginIdentification(emIdentificacao)).isSameAs(emIdentificacao);
        var result = service.identify(emIdentificacao, OS_ID, UNIT, "BILL", BILL);
        assertThat(result.outcome()).isEqualTo(Outcome.IDENTIFIED_AND_RETURNED);
        // Sobre entrada ja devolvida, recomecar e conflito.
        assertThatThrownBy(() -> service.beginIdentification(result.entry()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void enfileirarComMotivoDivergenteEConflitoSemDuplicar() {
        var entry = UnidentifiedPaymentQueue.QueueEntry.unidentified(
                PAYMENT, QueueReason.UNIDENTIFIED_CLASSIFICATION);
        // Mesmo motivo: devolve a existente (sem duplicar) - caso original mantido.
        assertThat(UnidentifiedPaymentQueue.enqueue(entry, PAYMENT,
                QueueReason.UNIDENTIFIED_CLASSIFICATION)).isSameAs(entry);
        // Motivo divergente para o mesmo pagamento: conflito explicito (b3).
        assertThatThrownBy(() -> UnidentifiedPaymentQueue.enqueue(entry, PAYMENT,
                QueueReason.PIX_WITHOUT_TXID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("motivo divergente");
    }

    @Test
    void reenvioComMesmoDocumentoMasTipoDiferenteEConflito() {
        var service = new PaymentIdentificationService();
        var entry = UnidentifiedPaymentQueue.QueueEntry.unidentified(
                PAYMENT, QueueReason.UNIDENTIFIED_CLASSIFICATION);
        var first = service.identify(entry, OS_ID, UNIT, "BILL", BILL);
        // Mesmo documentId, documentType DIFERENTE (INVOICE): conflito (M3),
        // nao idempotencia - sameLink compara tambem o tipo.
        assertThatThrownBy(() -> service.identify(first.entry(), OS_ID, UNIT, "INVOICE", BILL))
                .isInstanceOf(IllegalStateException.class);
        // Mesmo documento E mesmo tipo: continua idempotente.
        var resent = service.identify(first.entry(), OS_ID, UNIT, "BILL", BILL);
        assertThat(resent.outcome()).isEqualTo(Outcome.ALREADY_IDENTIFIED);
    }

    @Test
    void vinculoParcialERecusado() {
        var service = new PaymentIdentificationService();
        var entry = UnidentifiedPaymentQueue.QueueEntry.unidentified(
                PAYMENT, QueueReason.UNIDENTIFIED_CLASSIFICATION);
        assertThatThrownBy(() -> service.identify(entry, OS_ID, UNIT, "BILL", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.identify(entry, OS_ID, null, "BILL", BILL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reenvioDaMesmaOsComMesmoVinculoEIdempotenteEComVinculoDiferenteEConflito() {
        var service = new PaymentIdentificationService();
        var entry = UnidentifiedPaymentQueue.QueueEntry.unidentified(
                PAYMENT, QueueReason.UNIDENTIFIED_CLASSIFICATION);
        var first = service.identify(entry, OS_ID, UNIT, "BILL", BILL);
        var resent = service.identify(first.entry(), OS_ID, UNIT, "BILL", BILL);
        assertThat(resent.outcome()).isEqualTo(Outcome.ALREADY_IDENTIFIED);
        assertThat(resent.handoff()).isNull();
        assertThatThrownBy(() -> service.identify(first.entry(), OS_ID, UNIT, "BILL", UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
    }
}
