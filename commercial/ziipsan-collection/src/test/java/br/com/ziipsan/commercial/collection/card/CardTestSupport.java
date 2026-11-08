package br.com.ziipsan.commercial.collection.card;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Portas falsas em memoria (sem mocks), no padrao dos pacotes vizinhos (P3.2/P4.1). */
public final class CardTestSupport {

    public static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID AGREEMENT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final String ACQUIRER = "ACQ_TENANT_01";
    public static final String TEST_PAN = "4111111111111111";
    public static final String TEST_PAN_SPACED = "4111 1111 1111 1111";

    private CardTestSupport() {
    }

    public static CardParameters params(boolean enabled) {
        Map<String, String> values = new HashMap<>();
        values.put(CardParameters.ENABLED, Boolean.toString(enabled));
        if (enabled) {
            values.put(CardParameters.ACQUIRER_CODE, "\"" + ACQUIRER + "\"");
            values.put(CardParameters.CHECKOUT_MODE, "\"PAYMENT_LINK\"");
            values.put(CardParameters.STATEMENT_TOLERANCE, "0.05");
        }
        return CardParameters.fromValues(values);
    }

    /** Credenciadora falsa: registra a requisicao recebida e devolve o que o teste programar. */
    public static final class FakeAcquirer implements CardPorts.AcquirerPort {
        public CheckoutRequest lastRequest;
        public int createCalls = 0;
        public final Map<String, AcquirerTransaction> transactions = new LinkedHashMap<>();
        public List<StatementLine> statement = List.of();

        @Override
        public AcquirerSession createSession(CheckoutRequest request) {
            createCalls++;
            lastRequest = request;
            return new AcquirerSession("sess-" + request.reference(),
                    "https://checkout.example.invalid/pay/" + request.reference(),
                    request.expiresAt());
        }

        @Override
        public Optional<AcquirerTransaction> fetchTransaction(String acquirerTransactionId) {
            return Optional.ofNullable(transactions.get(acquirerTransactionId));
        }

        @Override
        public List<StatementLine> listStatement(LocalDate from, LocalDate to) {
            return statement;
        }

        public void programApproved(String txId, String reference, String gross, String fee) {
            BigDecimal g = new BigDecimal(gross);
            BigDecimal f = new BigDecimal(fee);
            transactions.put(txId, new AcquirerTransaction(txId, reference,
                    CardEnums.AcquirerTxStatus.APPROVED, g, f, g.subtract(f),
                    Instant.parse("2026-11-05T14:30:00Z"), "AUTH-123"));
        }
    }

    /** Baixa falsa: registra as ordens de baixa e de estorno. */
    public static final class FakeSettlements implements CardPorts.SettlementPort {
        public final List<SettleCommand> settled = new ArrayList<>();
        public final List<UUID> reversed = new ArrayList<>();

        @Override
        public UUID settleCardPayment(SettleCommand command) {
            settled.add(command);
            return UUID.fromString("33333333-3333-3333-3333-333333333333");
        }

        @Override
        public void reverseCardPayment(UUID tenantId, UUID paymentId, String reasonCode) {
            reversed.add(paymentId);
        }
    }

    /** Persistencia falsa em memoria, com a idempotencia de eventos do contrato. */
    public static final class FakeStore implements CardPorts.CardStore {
        public final Map<String, CardCheckoutService.CardCheckoutSession> sessions = new LinkedHashMap<>();
        public final java.util.Set<String> events = new java.util.HashSet<>();
        /** Hashes de payload recebidos em recordEvent, por eventId (prova da correcao D4). */
        public final Map<String, String> eventPayloadHashes = new LinkedHashMap<>();
        /** Instantes de recebimento recebidos em recordEvent, por eventId (prova da correcao D4). */
        public final Map<String, Instant> eventReceivedAts = new LinkedHashMap<>();
        public final Map<String, CardSettlement> settlementsByTx = new LinkedHashMap<>();
        public final List<String> statusUpdates = new ArrayList<>();

        @Override
        public void saveSession(CardCheckoutService.CardCheckoutSession session) {
            sessions.put(session.reference(), session);
        }

        @Override
        public Optional<CardCheckoutService.CardCheckoutSession> findSessionByReference(UUID tenantId, String reference) {
            return Optional.ofNullable(sessions.get(reference));
        }

        @Override
        public void updateSessionStatus(UUID tenantId, UUID sessionId, CardEnums.SessionStatus status) {
            statusUpdates.add(sessionId + "=" + status);
            sessions.replaceAll((ref, s) -> s.id().equals(sessionId) ? s.withStatus(status) : s);
        }

        @Override
        public boolean recordEvent(UUID tenantId, String acquirerCode, String eventId, String txId,
                                   String payloadHash, Instant receivedAt) {
            boolean added = events.add(tenantId + "|" + acquirerCode + "|" + eventId);
            if (added) {
                eventPayloadHashes.put(eventId, payloadHash);
                eventReceivedAts.put(eventId, receivedAt);
            }
            return added;
        }

        @Override
        public Optional<CardSettlement> findSettlementByTransaction(UUID tenantId, String txId) {
            return Optional.ofNullable(settlementsByTx.get(txId));
        }

        @Override
        public void saveSettlement(CardSettlement settlement) {
            settlementsByTx.put(settlement.acquirerTransactionId(), settlement);
        }

        @Override
        public List<CardSettlement> listSettlements(UUID tenantId, LocalDate from, LocalDate to) {
            return List.copyOf(settlementsByTx.values());
        }
    }

    /** Log capturado: a prova de "logs sem dado de cartao" le esta lista. */
    public static final class CapturingLog implements CardPorts.AuditLogPort {
        public final List<String> lines = new ArrayList<>();

        @Override
        public void log(String message) {
            lines.add(message);
        }
    }
}
