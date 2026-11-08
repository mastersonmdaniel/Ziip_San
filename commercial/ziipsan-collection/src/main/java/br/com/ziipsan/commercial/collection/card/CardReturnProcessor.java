package br.com.ziipsan.commercial.collection.card;

import br.com.ziipsan.core.json.Json;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Retorno da credenciadora e BAIXA AUTOMATICA da parcela (ED-28) — o
 * fluxo espelha o do callback Pix da P3.2, nesta ordem:
 *
 * <ol>
 *   <li><b>Origem</b>: a assinatura/segredo do webhook e conferida na
 *       borda pelo adaptador da credenciadora (a credencial vive no
 *       cofre); {@code signatureValid} falso recusa sem gravar nada.</li>
 *   <li><b>Guarda de cartao</b>: o payload bruto passa pela
 *       {@link CardDataGuard} ANTES de qualquer leitura ou gravacao.
 *       Se a credenciadora (ou um forjador) mandar dado de cartao, o
 *       retorno e recusado inteiro: nada se grava, nada se baixa e o
 *       log registra so a recusa, sem o payload.</li>
 *   <li><b>Evento normalizado</b>: o adaptador de cada credenciadora
 *       traduz o webhook dela para o evento interno
 *       ({@code eventId, acquirerTransactionId, sessionReference,
 *       status, grossAmount, feeAmount, paidAt}) — JSON lido so pela
 *       classe {@link Json} do core. Campo ausente = recusa.</li>
 *   <li><b>Idempotencia</b>: {@code (tenant, credenciadora, eventId)}
 *       ja gravado = {@link Decision#REPETIDO_SEM_EFEITO}; o mesmo vale
 *       para uma segunda baixa da mesma transacao (evento novo, mesma
 *       transacao).</li>
 *   <li><b>Confirmacao</b>: aprovacao so baixa depois da consulta
 *       autoritativa a credenciadora ({@code fetchTransaction}),
 *       conferindo referencia, situacao e o bruto igual ao valor da
 *       sessao/parcela; divergencia ou ausencia =
 *       {@link Decision#DIVERGENTE}/{@link Decision#AGUARDANDO_CONFIRMACAO},
 *       sem baixa. Liquido deve ser bruto − taxa.</li>
 *   <li><b>Baixa</b>: confirmada, a baixa e ordenada pela porta de
 *       arrecadacao (P3.1, canal CARTAO) pelo BRUTO; a taxa e custo a
 *       parte e o liquido e o caixa. {@code collectionMonth} e o mes do
 *       pagamento (dia 1), a referencia de arrecadacao da P3.1.</li>
 *   <li><b>Estorno</b>: chargeback/devolucao sobre transacao ja baixada
 *       ordena a reversao da baixa; sem baixa previa, e so situacao da
 *       sessao, sem efeito financeiro.</li>
 * </ol>
 */
public final class CardReturnProcessor {

    public enum Decision {
        BAIXA, REPETIDO_SEM_EFEITO, PENDENTE_VINCULO, AGUARDANDO_CONFIRMACAO,
        DIVERGENTE, SEM_BAIXA, ESTORNO
    }

    /** Recusa do retorno (origem, forma ou dado de cartao): nada e gravado como valido nem baixado. */
    public static final class CardReturnRejectedException extends RuntimeException {
        public CardReturnRejectedException(String reason) {
            super(reason);
        }
    }

    private final CardPorts.AcquirerPort acquirer;
    private final CardPorts.SettlementPort settlements;
    private final CardPorts.CardStore store;
    private final CardPorts.AuditLogPort auditLog;

    public CardReturnProcessor(CardPorts.AcquirerPort acquirer, CardPorts.SettlementPort settlements,
                               CardPorts.CardStore store, CardPorts.AuditLogPort auditLog) {
        this.acquirer = Objects.requireNonNull(acquirer, "porta da credenciadora e obrigatoria");
        this.settlements = Objects.requireNonNull(settlements, "porta de baixa e obrigatoria");
        this.store = Objects.requireNonNull(store, "porta de persistencia e obrigatoria");
        this.auditLog = Objects.requireNonNull(auditLog, "porta de log de auditoria e obrigatoria");
    }

    /**
     * @param signatureValid a borda conferiu a assinatura/segredo do webhook com a credencial do cofre
     * @param payloadJson    evento interno normalizado pelo adaptador da credenciadora
     * @param receivedAt     instante de recebimento (relogio da borda, injetado)
     */
    public record ReturnCommand(UUID tenantId, String acquirerCode, boolean signatureValid,
                                String payloadJson, Instant receivedAt) {
    }

    /** @param paymentId pagamento da baixa (nulo quando a decisao nao baixa nem estorna) */
    public record ReturnOutcome(Decision decision, String acquirerTransactionId, UUID paymentId,
                                LocalDate collectionMonth) {
    }

    public ReturnOutcome process(ReturnCommand command) {
        Objects.requireNonNull(command, "comando de retorno e obrigatorio");
        if (!command.signatureValid()) {
            throw new CardReturnRejectedException(
                    "retorno da credenciadora sem assinatura/segredo valido: recusado sem gravacao");
        }
        // ANTES de ler ou gravar qualquer coisa: dado de cartao recusa o retorno inteiro.
        if (CardDataGuard.containsCardData(command.payloadJson())) {
            auditLog.log("card.return.rejected reason=CARD_DATA_DETECTED acquirer=" + command.acquirerCode());
            throw new CardReturnRejectedException(
                    "retorno com dado de cartao detectado: recusado inteiro, sem gravacao nem baixa (ED-28)");
        }
        JsonNode payload = Json.readTree(command.payloadJson());
        String eventId = requiredText(payload, "eventId");
        String txId = requiredText(payload, "acquirerTransactionId");
        String reference = requiredText(payload, "sessionReference");
        CardEnums.AcquirerTxStatus status = parseStatus(requiredText(payload, "status"));

        boolean recorded;
        try {
            recorded = store.recordEvent(command.tenantId(), command.acquirerCode(), eventId, txId,
                    payloadHash(command.payloadJson()), command.receivedAt());
        } catch (CardPorts.CardStore.DuplicateEventException e) {
            recorded = false;
        }
        if (!recorded) {
            return outcome(Decision.REPETIDO_SEM_EFEITO, txId, null, null);
        }

        Optional<CardCheckoutService.CardCheckoutSession> session =
                store.findSessionByReference(command.tenantId(), reference);
        if (session.isEmpty()) {
            auditLog.log(CardDataGuard.sanitizeForLog("card.return.unlinked tenant=" + command.tenantId()
                    + " acquirer=" + command.acquirerCode() + " tx=" + txId));
            return outcome(Decision.PENDENTE_VINCULO, txId, null, null);
        }
        var found = session.get();

        return switch (status) {
            case APPROVED -> processApproved(command, found, txId, payload);
            case PENDING -> outcome(Decision.AGUARDANDO_CONFIRMACAO, txId, null, null);
            case REFUSED -> {
                store.updateSessionStatus(command.tenantId(), found.id(), CardEnums.SessionStatus.REFUSED);
                yield outcome(Decision.SEM_BAIXA, txId, null, null);
            }
            case CANCELED -> {
                store.updateSessionStatus(command.tenantId(), found.id(), CardEnums.SessionStatus.CANCELED);
                yield outcome(Decision.SEM_BAIXA, txId, null, null);
            }
            case CHARGED_BACK, REFUNDED -> processReversal(command, found, txId);
        };
    }

    private ReturnOutcome processApproved(ReturnCommand command,
                                          CardCheckoutService.CardCheckoutSession session, String txId,
                                          JsonNode payload) {
        if (store.findSettlementByTransaction(command.tenantId(), txId).isPresent()) {
            return outcome(Decision.REPETIDO_SEM_EFEITO, txId, null, null);
        }
        Optional<CardPorts.AcquirerPort.AcquirerTransaction> confirmed = acquirer.fetchTransaction(txId);
        if (confirmed.isEmpty()) {
            return outcome(Decision.AGUARDANDO_CONFIRMACAO, txId, null, null);
        }
        var tx = confirmed.get();
        BigDecimal eventGross = requiredDecimal(payload, "grossAmount");
        boolean divergent = tx.status() != CardEnums.AcquirerTxStatus.APPROVED
                || !tx.sessionReference().equals(session.reference())
                || tx.grossAmount().compareTo(session.amount()) != 0
                || eventGross.compareTo(tx.grossAmount()) != 0
                || tx.netAmount().compareTo(tx.grossAmount().subtract(tx.feeAmount())) != 0;
        if (divergent) {
            auditLog.log(CardDataGuard.sanitizeForLog("card.return.divergent tenant=" + command.tenantId()
                    + " acquirer=" + command.acquirerCode() + " tx=" + txId
                    + " reference=" + session.reference()));
            return outcome(Decision.DIVERGENTE, txId, null, null);
        }
        var settle = new CardPorts.SettlementPort.SettleCommand(command.tenantId(), session.agreementId(),
                session.installmentNumber(), tx.grossAmount(), tx.feeAmount(), tx.netAmount(),
                command.acquirerCode(), txId, tx.paidAt());
        UUID paymentId = settlements.settleCardPayment(settle);
        store.saveSettlement(new CardPorts.CardStore.CardSettlement(UUID.randomUUID(), command.tenantId(),
                session.agreementId(), session.installmentNumber(), paymentId, command.acquirerCode(),
                txId, tx.grossAmount(), tx.feeAmount(), tx.netAmount(), tx.paidAt()));
        store.updateSessionStatus(command.tenantId(), session.id(), CardEnums.SessionStatus.PAID);
        LocalDate collectionMonth = tx.paidAt().atZone(java.time.ZoneOffset.UTC).toLocalDate().withDayOfMonth(1);
        auditLog.log(CardDataGuard.sanitizeForLog("card.return.settled tenant=" + command.tenantId()
                + " acquirer=" + command.acquirerCode() + " tx=" + txId
                + " reference=" + session.reference() + " payment=" + paymentId));
        return new ReturnOutcome(Decision.BAIXA, txId, paymentId, collectionMonth);
    }

    private ReturnOutcome processReversal(ReturnCommand command,
                                          CardCheckoutService.CardCheckoutSession session, String txId) {
        Optional<CardPorts.CardStore.CardSettlement> existing =
                store.findSettlementByTransaction(command.tenantId(), txId);
        if (existing.isEmpty()) {
            store.updateSessionStatus(command.tenantId(), session.id(), CardEnums.SessionStatus.CHARGED_BACK);
            return outcome(Decision.SEM_BAIXA, txId, null, null);
        }
        settlements.reverseCardPayment(command.tenantId(), existing.get().paymentId(), "ACQUIRER_REVERSAL");
        store.updateSessionStatus(command.tenantId(), session.id(), CardEnums.SessionStatus.CHARGED_BACK);
        auditLog.log(CardDataGuard.sanitizeForLog("card.return.reversed tenant=" + command.tenantId()
                + " acquirer=" + command.acquirerCode() + " tx=" + txId
                + " payment=" + existing.get().paymentId()));
        return outcome(Decision.ESTORNO, txId, existing.get().paymentId(), null);
    }

    private static ReturnOutcome outcome(Decision decision, String txId, UUID paymentId, LocalDate month) {
        return new ReturnOutcome(decision, txId, paymentId, month);
    }

    private static CardEnums.AcquirerTxStatus parseStatus(String text) {
        try {
            return CardEnums.AcquirerTxStatus.valueOf(text);
        } catch (IllegalArgumentException e) {
            // Correcao D5 da revisao de 08/11/2026: a mensagem NAO ecoa o
            // texto vindo do payload — excecoes sao logadas pela borda e
            // o campo status e texto livre da credenciadora.
            throw new CardReturnRejectedException(
                    "situacao da credenciadora desconhecida: recusado sem eco do conteudo recebido (ED-28)");
        }
    }

    /**
     * SHA-256 hexadecimal do payload bruto do retorno (correcao D4 da
     * revisao de 08/11/2026): e o valor que a porta de persistencia
     * grava em {@code card_acquirer_events.payload_hash} — o payload
     * em si NUNCA e persistido, so o hash dele.
     */
    static String payloadHash(String payloadJson) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(payloadJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel na JVM", e);
        }
    }

    private static String requiredText(JsonNode payload, String field) {
        JsonNode node = payload.get(field);
        if (node == null || node.isNull() || !node.isTextual() || node.textValue().isBlank()) {
            throw new CardReturnRejectedException("evento da credenciadora sem o campo obrigatorio '" + field + "'");
        }
        return node.textValue();
    }

    private static BigDecimal requiredDecimal(JsonNode payload, String field) {
        JsonNode node = payload.get(field);
        if (node == null || node.isNull() || !node.isNumber()) {
            throw new CardReturnRejectedException("evento da credenciadora sem o valor obrigatorio '" + field + "'");
        }
        return node.decimalValue();
    }
}
