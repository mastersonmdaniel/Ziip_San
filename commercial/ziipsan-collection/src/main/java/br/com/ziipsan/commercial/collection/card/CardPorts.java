package br.com.ziipsan.commercial.collection.card;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Portas da ED-28 (padrao das P3.2/P4.1): as superficies vizinhas e a
 * credenciadora sao consumidas por interfaces, sem assinatura de
 * credenciadora real inventada. A integracao liga um adaptador por
 * credenciadora contratada pelo tenant; porta nao ligada = a operacao
 * que a exige recusa explicitamente, nunca finge o efeito.
 *
 * <p>REGRA ESTRUTURAL (o coracao do aceite): nenhum record destas
 * portas tem campo de dado de cartao. A requisicao de checkout carrega
 * somente referencia, valor e URLs do produto; a transacao devolvida
 * carrega somente identificadores da credenciadora, situacao, valores
 * e o codigo de autorizacao DA OPERACAO (emitido pela credenciadora,
 * nao e dado do cartao). O teste de arquitetura da ED-28 confere os
 * componentes de todos os records do pacote por reflexao.
 */
public final class CardPorts {

    private CardPorts() {
    }

    /**
     * A credenciadora contratada pelo tenant (18e-E14/D3, opcao A):
     * cria a sessao de link/checkout hospedado, confirma a transacao e
     * entrega o extrato para conciliacao. O adaptador real traduz a API
     * de CADA credenciadora para estes records normalizados; o contrato
     * concreto (endpoints, autenticacao, campos) e do contrato do
     * tenant com a credenciadora — marcador de integracao, nao desta
     * tarefa. A credencial da credenciadora vive no cofre e so o
     * adaptador a le.
     */
    public interface AcquirerPort {

        /** Cria a sessao na credenciadora e devolve a URL hospedada. */
        AcquirerSession createSession(CheckoutRequest request);

        /** Consulta autoritativa da transacao (equivalente ao GET /pix da P3.2). */
        Optional<AcquirerTransaction> fetchTransaction(String acquirerTransactionId);

        /** Linhas do extrato da credenciadora no periodo (conciliacao). */
        List<StatementLine> listStatement(LocalDate from, LocalDate to);

        /**
         * Requisicao de checkout: SO referencia, valor, codigo de
         * descricao e URLs do produto. Nenhum dado do pagador alem do
         * necessario a sessao, e NENHUM dado de cartao — o cartao sera
         * digitado na pagina da credenciadora.
         */
        record CheckoutRequest(UUID tenantId, String acquirerCode, String reference,
                               BigDecimal amount, String descriptionCode,
                               String returnUrl, Instant expiresAt) {
            public CheckoutRequest {
                if (tenantId == null || acquirerCode == null || acquirerCode.isBlank()
                        || reference == null || reference.isBlank()
                        || amount == null || amount.signum() <= 0
                        || descriptionCode == null || descriptionCode.isBlank()
                        || returnUrl == null || returnUrl.isBlank() || expiresAt == null) {
                    throw new IllegalArgumentException(
                            "requisicao de checkout exige tenant, credenciadora, referencia, "
                                    + "valor positivo, codigo de descricao, URL de retorno e expiracao");
                }
            }
        }

        /** Sessao criada na credenciadora: identificador e URL hospedada. */
        record AcquirerSession(String acquirerSessionId, String checkoutUrl, Instant expiresAt) {
            public AcquirerSession {
                if (acquirerSessionId == null || acquirerSessionId.isBlank()
                        || checkoutUrl == null || checkoutUrl.isBlank() || expiresAt == null) {
                    throw new IllegalArgumentException(
                            "sessao da credenciadora exige identificador, URL hospedada e expiracao");
                }
            }
        }

        /**
         * Transacao como a credenciadora a devolve (webhook normalizado
         * pelo adaptador ou consulta): identificadores da operacao,
         * situacao, valores e codigo de autorizacao da operacao.
         * {@code netAmount} deve ser {@code grossAmount - feeAmount}.
         */
        record AcquirerTransaction(String acquirerTransactionId, String sessionReference,
                                   CardEnums.AcquirerTxStatus status,
                                   BigDecimal grossAmount, BigDecimal feeAmount, BigDecimal netAmount,
                                   Instant paidAt, String authorizationCode) {
            public AcquirerTransaction {
                if (acquirerTransactionId == null || acquirerTransactionId.isBlank()
                        || sessionReference == null || sessionReference.isBlank()
                        || status == null || grossAmount == null || feeAmount == null
                        || netAmount == null) {
                    throw new IllegalArgumentException(
                            "transacao da credenciadora exige identificador, referencia da sessao, "
                                    + "situacao e valores bruto/taxa/liquido");
                }
                if (grossAmount.signum() <= 0 || feeAmount.signum() < 0 || netAmount.signum() < 0) {
                    throw new IllegalArgumentException("valores da transacao fora da faixa (bruto > 0, taxa/liquido >= 0)");
                }
            }
        }

        /** Linha do extrato da credenciadora (conciliacao da ED-28). */
        record StatementLine(String acquirerTransactionId, String sessionReference,
                             BigDecimal grossAmount, BigDecimal feeAmount, BigDecimal netAmount,
                             LocalDate settlementDate) {
            public StatementLine {
                if (acquirerTransactionId == null || acquirerTransactionId.isBlank()
                        || grossAmount == null || feeAmount == null || netAmount == null
                        || settlementDate == null) {
                    throw new IllegalArgumentException(
                            "linha de extrato exige transacao, valores e data de liquidacao");
                }
            }
        }
    }

    /**
     * Baixa na arrecadacao (P3.1): quem grava o pagamento de canal
     * CARTAO e marca a parcela do acordo (P4.1) como paga e o servico
     * transacional da integracao, a partir da ordem desta porta — o
     * mesmo desenho da baixa do retorno (P3.1) e do Pix (P3.2).
     * {@code grossAmount} e o valor da baixa (o que quita a parcela);
     * a taxa da credenciadora e custo registrado a parte e o liquido e
     * o que entra em caixa.
     */
    public interface SettlementPort {

        /** Ordena a baixa e devolve o id do pagamento gravado (payments, canal CARTAO). */
        UUID settleCardPayment(SettleCommand command);

        /** Estorna uma baixa ja feita (chargeback/devolucao confirmados pela credenciadora). */
        void reverseCardPayment(UUID tenantId, UUID paymentId, String reasonCode);

        record SettleCommand(UUID tenantId, UUID agreementId, int installmentNumber,
                             BigDecimal grossAmount, BigDecimal feeAmount, BigDecimal netAmount,
                             String acquirerCode, String acquirerTransactionId, Instant paidAt) {
            public SettleCommand {
                if (tenantId == null || agreementId == null || installmentNumber < 0
                        || grossAmount == null || grossAmount.signum() <= 0
                        || feeAmount == null || feeAmount.signum() < 0
                        || netAmount == null || netAmount.signum() < 0
                        || acquirerCode == null || acquirerCode.isBlank()
                        || acquirerTransactionId == null || acquirerTransactionId.isBlank()
                        || paidAt == null) {
                    throw new IllegalArgumentException("baixa por cartao exige tenant, acordo, parcela, "
                            + "valores, credenciadora, transacao e instante do pagamento");
                }
            }
        }
    }

    /**
     * Persistencia das sessoes, dos eventos de retorno (idempotencia) e
     * das baixas por cartao. O adaptador JPA/JDBC e fiacao da
     * integracao sobre as tabelas do SQL pendente desta entrega; o
     * contrato de {@link #recordEvent} e ATOMICO (como o da pix_inbox
     * da P3.2): a unicidade (tenant, credenciadora, evento) e do banco
     * e a corrida de duplicados chega aqui como
     * {@link DuplicateEventException}.
     */
    public interface CardStore {

        final class DuplicateEventException extends RuntimeException {
            public DuplicateEventException(String message) {
                super(message);
            }
        }

        void saveSession(CardCheckoutService.CardCheckoutSession session);

        Optional<CardCheckoutService.CardCheckoutSession> findSessionByReference(UUID tenantId, String reference);

        void updateSessionStatus(UUID tenantId, UUID sessionId, CardEnums.SessionStatus status);

        /**
         * Grava o evento de retorno para idempotencia.
         *
         * <p>Correcao D4 da revisao de 08/11/2026 (coerencia codigo x
         * SQL): a tabela {@code card_acquirer_events} exige
         * {@code payload_hash} e {@code received_at} NOT NULL — esta
         * porta RECEBE os dois, calculados por quem processa o retorno
         * (o hash SHA-256 do payload bruto, que NUNCA e persistido, e
         * o instante de recebimento da borda). O adaptador nao precisa
         * inventar valor algum para gravar o evento.
         *
         * @param payloadHash SHA-256 hexadecimal (64 caracteres) do payload bruto do retorno
         * @param receivedAt  instante de recebimento do retorno (relogio da borda)
         * @return false se o evento ja tinha sido gravado (idempotencia)
         */
        boolean recordEvent(UUID tenantId, String acquirerCode, String eventId, String acquirerTransactionId,
                            String payloadHash, Instant receivedAt);

        Optional<CardSettlement> findSettlementByTransaction(UUID tenantId, String acquirerTransactionId);

        void saveSettlement(CardSettlement settlement);

        List<CardSettlement> listSettlements(UUID tenantId, LocalDate from, LocalDate to);

        /** Baixa por cartao gravada (espelho de {@code card_settlements}). */
        record CardSettlement(UUID id, UUID tenantId, UUID agreementId, int installmentNumber,
                              UUID paymentId, String acquirerCode, String acquirerTransactionId,
                              BigDecimal grossAmount, BigDecimal feeAmount, BigDecimal netAmount,
                              Instant paidAt) {
            public CardSettlement {
                if (id == null || tenantId == null || agreementId == null || paymentId == null
                        || acquirerTransactionId == null || acquirerTransactionId.isBlank()) {
                    throw new IllegalArgumentException("baixa por cartao exige ids e transacao da credenciadora");
                }
            }
        }
    }

    /**
     * Log/auditoria do fluxo de cartao. Os servicos logam SOMENTE
     * mensagens montadas com referencias e decisoes, sempre passando
     * por {@link CardDataGuard#sanitizeForLog(String)} — nunca o
     * payload bruto. O teste da ED-28 captura esta porta num fluxo
     * completo e prova que nenhum log carrega dado de cartao.
     */
    @FunctionalInterface
    public interface AuditLogPort {
        void log(String message);
    }
}
