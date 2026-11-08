package br.com.ziipsan.commercial.collection.card;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Retorno da credenciadora: baixa automatica confirmada, idempotencia,
 * divergencia sem baixa, estorno — e a prova central da ED-28: payload
 * COM dado de cartao (numero de teste publico) e recusado inteiro, sem
 * gravacao, sem baixa e sem o numero em nenhum log.
 */
class CardReturnProcessorTest {

    private final CardTestSupport.FakeAcquirer acquirer = new CardTestSupport.FakeAcquirer();
    private final CardTestSupport.FakeSettlements settlements = new CardTestSupport.FakeSettlements();
    private final CardTestSupport.FakeStore store = new CardTestSupport.FakeStore();
    private final CardTestSupport.CapturingLog log = new CardTestSupport.CapturingLog();
    private final CardReturnProcessor processor =
            new CardReturnProcessor(acquirer, settlements, store, log);
    private String reference;

    @BeforeEach
    void openSession() {
        var checkout = new CardCheckoutService(CardTestSupport.params(true), acquirer, store, log);
        var session = checkout.create(new CardCheckoutService.CreateCommand(
                CardTestSupport.TENANT, CardTestSupport.AGREEMENT, 3, true, true,
                new BigDecimal("106.61"), new BigDecimal("106.61"),
                "https://portal.example.invalid/retorno",
                Instant.parse("2026-11-05T23:59:00Z"), Instant.parse("2026-11-05T10:00:00Z")));
        reference = session.reference();
    }

    private String payload(String eventId, String txId, String status, String gross) {
        return "{\"eventId\":\"" + eventId + "\",\"acquirerTransactionId\":\"" + txId
                + "\",\"sessionReference\":\"" + reference + "\",\"status\":\"" + status
                + "\",\"grossAmount\":" + gross + ",\"feeAmount\":3.20,"
                + "\"paidAt\":\"2026-11-05T14:30:00Z\"}";
    }

    private CardReturnProcessor.ReturnOutcome process(String payloadJson) {
        return processor.process(new CardReturnProcessor.ReturnCommand(
                CardTestSupport.TENANT, CardTestSupport.ACQUIRER, true, payloadJson,
                Instant.parse("2026-11-05T14:31:00Z")));
    }

    @Test
    @DisplayName("APPROVED confirmado na credenciadora: baixa automatica pelo bruto, mes de arrecadacao = mes do pagamento")
    void approvedSettles() {
        acquirer.programApproved("tx-1", reference, "106.61", "3.20");
        var outcome = process(payload("evt-1", "tx-1", "APPROVED", "106.61"));
        assertThat(outcome.decision()).isEqualTo(CardReturnProcessor.Decision.BAIXA);
        assertThat(settlements.settled).hasSize(1);
        assertThat(settlements.settled.get(0).grossAmount()).isEqualByComparingTo("106.61");
        assertThat(settlements.settled.get(0).feeAmount()).isEqualByComparingTo("3.20");
        assertThat(settlements.settled.get(0).netAmount()).isEqualByComparingTo("103.41");
        assertThat(outcome.collectionMonth()).isEqualTo(java.time.LocalDate.of(2026, 11, 1));
        assertThat(store.settlementsByTx).containsKey("tx-1");
    }

    @Test
    @DisplayName("mesmo evento reenviado: REPETIDO_SEM_EFEITO e nenhuma segunda baixa")
    void duplicateEventNoSecondSettlement() {
        acquirer.programApproved("tx-1", reference, "106.61", "3.20");
        process(payload("evt-1", "tx-1", "APPROVED", "106.61"));
        var second = process(payload("evt-1", "tx-1", "APPROVED", "106.61"));
        assertThat(second.decision()).isEqualTo(CardReturnProcessor.Decision.REPETIDO_SEM_EFEITO);
        assertThat(settlements.settled).hasSize(1);
    }

    @Test
    @DisplayName("evento novo para a MESMA transacao ja baixada: REPETIDO_SEM_EFEITO")
    void newEventSameTransactionNoSecondSettlement() {
        acquirer.programApproved("tx-1", reference, "106.61", "3.20");
        process(payload("evt-1", "tx-1", "APPROVED", "106.61"));
        var second = process(payload("evt-2", "tx-1", "APPROVED", "106.61"));
        assertThat(second.decision()).isEqualTo(CardReturnProcessor.Decision.REPETIDO_SEM_EFEITO);
        assertThat(settlements.settled).hasSize(1);
    }

    @Test
    @DisplayName("assinatura invalida: recusa sem gravar evento e sem baixa")
    void badSignatureRejected() {
        acquirer.programApproved("tx-1", reference, "106.61", "3.20");
        assertThatThrownBy(() -> processor.process(new CardReturnProcessor.ReturnCommand(
                CardTestSupport.TENANT, CardTestSupport.ACQUIRER, false,
                payload("evt-1", "tx-1", "APPROVED", "106.61"), Instant.parse("2026-11-05T14:31:00Z"))))
                .isInstanceOf(CardReturnProcessor.CardReturnRejectedException.class);
        assertThat(store.events).isEmpty();
        assertThat(settlements.settled).isEmpty();
    }

    @Test
    @DisplayName("PROVA ED-28: payload com numero de cartao e recusado inteiro — sem evento, sem baixa, sem o numero no log")
    void payloadWithCardDataRejectedEntirely() {
        String poisoned = payload("evt-x", "tx-x", "APPROVED", "106.61")
                .replace("}", ",\"cardNumber\":\"" + CardTestSupport.TEST_PAN + "\"}");
        assertThatThrownBy(() -> process(poisoned))
                .isInstanceOf(CardReturnProcessor.CardReturnRejectedException.class);
        assertThat(store.events).isEmpty();
        assertThat(store.settlementsByTx).isEmpty();
        assertThat(settlements.settled).isEmpty();
        assertThat(log.lines).noneMatch(line -> line.contains("4111"));
    }

    @Test
    @DisplayName("confirmacao divergente (valor diferente na credenciadora): DIVERGENTE, sem baixa")
    void divergentConfirmationNoSettlement() {
        acquirer.programApproved("tx-1", reference, "99.99", "3.20");
        var outcome = process(payload("evt-1", "tx-1", "APPROVED", "106.61"));
        assertThat(outcome.decision()).isEqualTo(CardReturnProcessor.Decision.DIVERGENTE);
        assertThat(settlements.settled).isEmpty();
    }

    @Test
    @DisplayName("transacao nao encontrada na consulta: AGUARDANDO_CONFIRMACAO, sem baixa")
    void unconfirmedNoSettlement() {
        var outcome = process(payload("evt-1", "tx-404", "APPROVED", "106.61"));
        assertThat(outcome.decision()).isEqualTo(CardReturnProcessor.Decision.AGUARDANDO_CONFIRMACAO);
        assertThat(settlements.settled).isEmpty();
    }

    @Test
    @DisplayName("REFUSED: SEM_BAIXA e a sessao fica REFUSED")
    void refusedNoSettlement() {
        var outcome = process(payload("evt-1", "tx-1", "REFUSED", "106.61"));
        assertThat(outcome.decision()).isEqualTo(CardReturnProcessor.Decision.SEM_BAIXA);
        assertThat(settlements.settled).isEmpty();
        assertThat(store.sessions.get(reference).status()).isEqualTo(CardEnums.SessionStatus.REFUSED);
    }

    @Test
    @DisplayName("referencia desconhecida: PENDENTE_VINCULO, sem baixa")
    void unknownReferencePending() {
        String orphan = payload("evt-9", "tx-9", "APPROVED", "106.61")
                .replace(reference, "00000000-0000-4000-8000-000000000099");
        var outcome = process(orphan);
        assertThat(outcome.decision()).isEqualTo(CardReturnProcessor.Decision.PENDENTE_VINCULO);
        assertThat(settlements.settled).isEmpty();
    }

    @Test
    @DisplayName("D4: o evento gravado recebe o SHA-256 REAL do payload e o receivedAt da borda (payload bruto nunca gravado)")
    void eventCarriesRealPayloadHash() throws Exception {
        acquirer.programApproved("tx-1", reference, "106.61", "3.20");
        String payloadJson = payload("evt-hash", "tx-1", "APPROVED", "106.61");
        var receivedAt = Instant.parse("2026-11-05T14:31:00Z");
        processor.process(new CardReturnProcessor.ReturnCommand(
                CardTestSupport.TENANT, CardTestSupport.ACQUIRER, true, payloadJson, receivedAt));
        String expectedHash = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                        .digest(payloadJson.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThat(store.eventPayloadHashes).containsEntry("evt-hash", expectedHash);
        assertThat(expectedHash).hasSize(64);
        assertThat(store.eventReceivedAts).containsEntry("evt-hash", receivedAt);
    }

    @Test
    @DisplayName("D5: status desconhecido recusa SEM ecoar o texto do payload na excecao")
    void unknownStatusDoesNotEchoPayload() {
        String weird = "SITUACAO_INVENTADA_998877";
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> process(payload("evt-unk", "tx-1", weird, "106.61")));
        assertThat(thrown).isInstanceOf(CardReturnProcessor.CardReturnRejectedException.class);
        assertThat(thrown.getMessage()).doesNotContain(weird);
        assertThat(settlements.settled).isEmpty();
    }

    @Test
    @DisplayName("chargeback depois da baixa: ESTORNO ordenado sobre o pagamento baixado")
    void chargebackReversesSettlement() {
        acquirer.programApproved("tx-1", reference, "106.61", "3.20");
        var baixa = process(payload("evt-1", "tx-1", "APPROVED", "106.61"));
        var outcome = process(payload("evt-2", "tx-1", "CHARGED_BACK", "106.61"));
        assertThat(outcome.decision()).isEqualTo(CardReturnProcessor.Decision.ESTORNO);
        assertThat(settlements.reversed).containsExactly(baixa.paymentId());
    }
}
