package br.com.ziipsan.commercial.collection.pixauto;

import br.com.ziipsan.commercial.collection.pix.PixAutoDebitRules;
import br.com.ziipsan.core.document.DocumentNumber;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Autorizacao do pagador para debito recorrente por Pix Automatico (P4.5).
 *
 * <p>A autorizacao so existe COM PROVA: {@code proofHash} e o hash SHA-256
 * (64 hex) do registro da jornada de autorizacao (termo/registro produzido na
 * jornada 1 a 4, inclusive a jornada por QR Code) e {@code proofReference} e a
 * referencia localizavel desse registro. Sem prova valida, a autorizacao e
 * recusada na construcao - nao existe autorizacao "presumida".
 *
 * <p>A autorizacao declara a si mesma: se retentativas sao previstas (e o teto
 * delas, dentro do teto normativo de {@link PixAutoDebitRules}) e o valor
 * maximo por debito, quando o pagador o fixou ({@code null} = sem teto
 * declarado pelo pagador; o servico nao inventa teto).
 */
public final class PixAutoDebitAuthorization {

    public enum Status { ATIVA, CANCELADA_PELO_PAGADOR, CANCELADA_PELO_RECEBEDOR, ENCERRADA }

    private final UUID id;
    private final UUID tenantId;
    private final String psp;
    private final String pspAuthorizationId;
    private final String payerDocument;
    private final String receiverDocument;
    private final UUID unitId;
    private final int journey;
    private final boolean qrCodeAuthorization;
    private final String proofHash;
    private final String proofReference;
    private final Instant authorizedAt;
    private final boolean retryForeseen;
    private final int maxRetries;
    private final BigDecimal maxAmount;
    private Status status = Status.ATIVA;
    private Instant cancelledAt;

    public PixAutoDebitAuthorization(UUID id, UUID tenantId, String psp, String pspAuthorizationId,
                                     String payerDocument, String receiverDocument, UUID unitId,
                                     int journey, boolean qrCodeAuthorization, String proofHash,
                                     String proofReference, Instant authorizedAt, boolean retryForeseen,
                                     int maxRetries, BigDecimal maxAmount) {
        this.id = Objects.requireNonNull(id, "id da autorizacao e obrigatorio");
        this.tenantId = Objects.requireNonNull(tenantId, "tenant da autorizacao e obrigatorio");
        if (psp == null || psp.isBlank()) {
            throw new IllegalArgumentException("PSP da autorizacao e obrigatorio");
        }
        this.psp = psp;
        this.pspAuthorizationId = pspAuthorizationId;
        String payer = payerDocument == null ? null : DocumentNumber.normalize(payerDocument);
        if (payer == null || !DocumentNumber.isValidCpf(payer) && !DocumentNumber.isValidCnpj(payer)) {
            throw new IllegalArgumentException("documento do pagador invalido (P1.39)");
        }
        this.payerDocument = payer;
        String receiver = receiverDocument == null ? null : DocumentNumber.normalize(receiverDocument);
        if (receiver == null || !DocumentNumber.isValidCnpj(receiver)) {
            throw new IllegalArgumentException("CNPJ do recebedor invalido (P1.39)");
        }
        this.receiverDocument = receiver;
        this.unitId = unitId;
        if (!PixAutoDebitRules.isValidAuthorizationJourney(journey)) {
            throw new IllegalArgumentException("jornada de autorizacao fora de 1.."
                    + PixAutoDebitRules.MAX_AUTHORIZATION_JOURNEY + ": " + journey);
        }
        this.journey = journey;
        this.qrCodeAuthorization = qrCodeAuthorization;
        if (proofHash == null || !proofHash.matches("^[0-9a-fA-F]{64}$")) {
            throw new IllegalArgumentException(
                    "autorizacao sem prova: proofHash deve ser o SHA-256 (64 hex) do registro da jornada");
        }
        this.proofHash = proofHash.toLowerCase();
        if (proofReference == null || proofReference.isBlank()) {
            throw new IllegalArgumentException(
                    "autorizacao sem prova: proofReference do registro da jornada e obrigatoria");
        }
        this.proofReference = proofReference;
        this.authorizedAt = Objects.requireNonNull(authorizedAt, "instante da autorizacao e obrigatorio");
        if (retryForeseen) {
            if (maxRetries < 1 || maxRetries > PixAutoDebitRules.MAX_RETRIES) {
                throw new IllegalArgumentException("retentativas previstas na autorizacao fora de 1.."
                        + PixAutoDebitRules.MAX_RETRIES + ": " + maxRetries);
            }
        } else if (maxRetries != 0) {
            throw new IllegalArgumentException(
                    "autorizacao sem retentativa prevista deve declarar maxRetries = 0");
        }
        this.retryForeseen = retryForeseen;
        this.maxRetries = maxRetries;
        if (maxAmount != null && maxAmount.signum() <= 0) {
            throw new IllegalArgumentException("valor maximo por debito deve ser > 0 quando declarado");
        }
        this.maxAmount = maxAmount;
    }

    /** Teto efetivo de retentativas: o menor entre o da autorizacao e o da politica do tenant. */
    public int effectiveMaxRetries(PixAutoDebitRules.RetryPolicy policy) {
        if (!retryForeseen || policy == null || !policy.foreseenInAuthorization()) {
            return 0;
        }
        return Math.min(maxRetries, policy.maxRetries());
    }

    public void cancel(PixAutoDebitCancellation.Actor actor, Instant at) {
        Objects.requireNonNull(actor, "ator do cancelamento e obrigatorio");
        Objects.requireNonNull(at, "instante do cancelamento e obrigatorio");
        if (status != Status.ATIVA) {
            throw new IllegalStateException("autorizacao nao ativa nao pode ser cancelada: " + status);
        }
        this.status = actor == PixAutoDebitCancellation.Actor.PAGADOR
                ? Status.CANCELADA_PELO_PAGADOR : Status.CANCELADA_PELO_RECEBEDOR;
        this.cancelledAt = at;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public String psp() { return psp; }
    public String pspAuthorizationId() { return pspAuthorizationId; }
    public String payerDocument() { return payerDocument; }
    public String receiverDocument() { return receiverDocument; }
    public UUID unitId() { return unitId; }
    public int journey() { return journey; }
    public boolean qrCodeAuthorization() { return qrCodeAuthorization; }
    public String proofHash() { return proofHash; }
    public String proofReference() { return proofReference; }
    public Instant authorizedAt() { return authorizedAt; }
    public boolean retryForeseen() { return retryForeseen; }
    public int maxRetries() { return maxRetries; }
    public BigDecimal maxAmount() { return maxAmount; }
    public Status status() { return status; }
    public Instant cancelledAt() { return cancelledAt; }
}
