package br.com.ziipsan.commercial.collection.card;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Abertura do pagamento por cartao de UMA parcela do parcelamento
 * (ED-28 sobre o acordo da P4.1): cria a sessao de link ou checkout na
 * credenciadora contratada pelo tenant e devolve a URL HOSPEDADA pela
 * credenciadora. E nessa pagina — fora do produto — que o pagador
 * digita o cartao; o produto nunca ve, recebe nem grava o cartao.
 *
 * <p>Regras:
 * <ul>
 *   <li>recurso desligado ({@code card.enabled}) ou credenciadora nao
 *       configurada: recusa explicita dos parametros, sem sessao;</li>
 *   <li>o acordo precisa estar ATIVO e a parcela ABERTA — fatos que o
 *       chamador declara (o estado vive na P4.1/billing); parcela aqui
 *       e {@code installmentNumber}: 0 = entrada do acordo,
 *       1..n = prestacao da agenda;</li>
 *   <li>o valor da sessao e EXATAMENTE o valor esperado da parcela
 *       (declarado pelo chamador a partir da agenda da P4.1): valor
 *       divergente recusa — e essa igualdade que torna a baixa
 *       automatica do retorno inequivoca;</li>
 *   <li>a requisicao montada para a credenciadora passa pela
 *       {@link CardDataGuard} serializada antes do envio: se um dia um
 *       campo indevido entrar nela, o envio recusa em vez de trafegar.</li>
 * </ul>
 */
public final class CardCheckoutService {

    /** Codigo de descricao da operacao enviado a credenciadora (sem dado pessoal). */
    public static final String DESCRIPTION_CODE = "INSTALLMENT_CARD_PAYMENT";

    private final CardParameters parameters;
    private final CardPorts.AcquirerPort acquirer;
    private final CardPorts.CardStore store;
    private final CardPorts.AuditLogPort auditLog;

    public CardCheckoutService(CardParameters parameters, CardPorts.AcquirerPort acquirer,
                               CardPorts.CardStore store, CardPorts.AuditLogPort auditLog) {
        this.parameters = Objects.requireNonNull(parameters, "parametros de cartao sao obrigatorios");
        this.acquirer = Objects.requireNonNull(acquirer, "porta da credenciadora e obrigatoria");
        this.store = Objects.requireNonNull(store, "porta de persistencia e obrigatoria");
        this.auditLog = Objects.requireNonNull(auditLog, "porta de log de auditoria e obrigatoria");
    }

    /**
     * Sessao de checkout/link gravada (espelho de
     * {@code card_checkout_sessions}): so referencia, valores e a URL
     * hospedada — nenhum dado de cartao existe neste record.
     */
    public record CardCheckoutSession(UUID id, UUID tenantId, UUID agreementId, int installmentNumber,
                                      BigDecimal amount, String acquirerCode,
                                      CardEnums.CheckoutMode mode, String reference,
                                      String acquirerSessionId, String checkoutUrl,
                                      CardEnums.SessionStatus status, Instant expiresAt, Instant createdAt) {
        public CardCheckoutSession {
            if (id == null || tenantId == null || agreementId == null || installmentNumber < 0
                    || amount == null || amount.signum() <= 0
                    || acquirerCode == null || acquirerCode.isBlank() || mode == null
                    || reference == null || reference.isBlank()
                    || acquirerSessionId == null || acquirerSessionId.isBlank()
                    || checkoutUrl == null || checkoutUrl.isBlank()
                    || status == null || expiresAt == null || createdAt == null) {
                throw new IllegalArgumentException("sessao de checkout exige ids, parcela, valor positivo, "
                        + "credenciadora, modo, referencia, sessao/URL da credenciadora, situacao e instantes");
            }
        }

        public CardCheckoutSession withStatus(CardEnums.SessionStatus newStatus) {
            return new CardCheckoutSession(id, tenantId, agreementId, installmentNumber, amount,
                    acquirerCode, mode, reference, acquirerSessionId, checkoutUrl, newStatus,
                    expiresAt, createdAt);
        }
    }

    /**
     * @param agreementActive fato declarado pelo chamador: o acordo da P4.1 esta ACTIVE
     * @param installmentOpen fato declarado pelo chamador: a parcela esta aberta na agenda
     * @param expectedAmount  valor da parcela na agenda da P4.1 (a sessao cobra exatamente ele)
     * @param returnUrl       URL do produto para onde a credenciadora devolve o pagador
     * @param expiresAt       expiracao da sessao (relogio do chamador, injetado)
     * @param now             instante da abertura (injetado, sem relogio escondido)
     */
    public record CreateCommand(UUID tenantId, UUID agreementId, int installmentNumber,
                                boolean agreementActive, boolean installmentOpen,
                                BigDecimal expectedAmount, BigDecimal requestedAmount,
                                String returnUrl, Instant expiresAt, Instant now) {
    }

    public CardCheckoutSession create(CreateCommand command) {
        Objects.requireNonNull(command, "comando de abertura e obrigatorio");
        if (!parameters.enabled()) {
            throw new IllegalStateException("pagamento com cartao desligado para o tenant (card.enabled)");
        }
        String acquirerCode = parameters.acquirerCode();
        CardEnums.CheckoutMode mode = parameters.checkoutMode();
        if (!command.agreementActive()) {
            throw new IllegalStateException("acordo de parcelamento nao esta ACTIVE: sessao de cartao recusada");
        }
        if (!command.installmentOpen()) {
            throw new IllegalStateException("parcela nao esta aberta na agenda: sessao de cartao recusada");
        }
        if (command.installmentNumber() < 0) {
            throw new IllegalArgumentException("parcela invalida: " + command.installmentNumber());
        }
        if (command.expectedAmount() == null || command.expectedAmount().signum() <= 0
                || command.requestedAmount() == null
                || command.requestedAmount().compareTo(command.expectedAmount()) != 0) {
            throw new IllegalArgumentException("valor da sessao (" + command.requestedAmount()
                    + ") deve ser exatamente o valor da parcela na agenda (" + command.expectedAmount() + ")");
        }
        if (command.expiresAt() == null || command.now() == null || !command.expiresAt().isAfter(command.now())) {
            throw new IllegalArgumentException("expiracao da sessao deve ser posterior a abertura");
        }
        String reference = UUID.randomUUID().toString();
        var request = new CardPorts.AcquirerPort.CheckoutRequest(command.tenantId(), acquirerCode,
                reference, command.expectedAmount(), DESCRIPTION_CODE, command.returnUrl(),
                command.expiresAt());
        // Segunda linha de defesa: a requisicao serializada nao pode carregar dado de cartao.
        CardDataGuard.assertNoCardData(request.toString(), "requisicao de checkout a credenciadora");
        CardPorts.AcquirerPort.AcquirerSession acquirerSession = acquirer.createSession(request);
        var session = new CardCheckoutSession(UUID.randomUUID(), command.tenantId(), command.agreementId(),
                command.installmentNumber(), command.expectedAmount(), acquirerCode, mode, reference,
                acquirerSession.acquirerSessionId(), acquirerSession.checkoutUrl(),
                CardEnums.SessionStatus.CREATED, acquirerSession.expiresAt(), command.now());
        store.saveSession(session);
        auditLog.log(CardDataGuard.sanitizeForLog("card.checkout.created tenant=" + command.tenantId()
                + " agreement=" + command.agreementId() + " installment=" + command.installmentNumber()
                + " acquirer=" + acquirerCode + " reference=" + reference));
        return session;
    }
}
