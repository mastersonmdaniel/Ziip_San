package br.com.ziipsan.commercial.collection.legacydebit;

import br.com.ziipsan.core.document.DocumentNumber;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Autorizacao LEGADA de debito automatico, IMPORTADA do legado (P3.24).
 *
 * <p>Esta classe so sabe nascer como importacao conferida: nao ha
 * construtor nem fabrica de autorizacao nova - autorizacao nova de debito
 * em conta so por Pix Automatico (P4.5, trava
 * {@code AccountDebitAuthorizationGate}). A conferencia e POR MATRICULA:
 * a matricula e a chave de conferencia da importacao e e unica por tenant.
 *
 * <p>Condicao de elegibilidade levantada por optante na P0.30 e aplicada
 * aqui: so entra no FEBRABAN 150 o optante cujo banco do pagador seja o
 * MESMO do recebedor ({@link #sameBank()}). Optante de banco diverso e
 * registrado como {@link Status#INELEGIVEL_BANCO_DIVERSO} - nunca entra
 * em remessa e nunca e descartado em silencio.
 */
public final class LegacyDebitAuthorization {

    public enum Status {
        IMPORTADA_NAO_CONFERIDA, ATIVA_LEGADO, INELEGIVEL_BANCO_DIVERSO,
        CANCELADA, CONVERTIDA_PIX
    }

    private final UUID id;
    private final UUID tenantId;
    private final String enrollmentId;
    private final UUID unitId;
    private final String payerDocument;
    private final String payerBankCode;
    private final String receiverBankCode;
    private final String legacyReference;
    private final Instant importedAt;
    private Status status = Status.IMPORTADA_NAO_CONFERIDA;
    private Instant conferredAt;
    private Instant cancelledAt;
    private Instant convertedAt;

    public LegacyDebitAuthorization(UUID id, UUID tenantId, String enrollmentId, UUID unitId,
                                    String payerDocument, String payerBankCode,
                                    String receiverBankCode, String legacyReference,
                                    Instant importedAt) {
        this.id = Objects.requireNonNull(id, "id da autorizacao legada e obrigatorio");
        this.tenantId = Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        if (enrollmentId == null || enrollmentId.isBlank()) {
            throw new IllegalArgumentException(
                    "matricula e obrigatoria: a conferencia da importacao e por matricula");
        }
        this.enrollmentId = enrollmentId;
        this.unitId = unitId;
        String payer = payerDocument == null ? null : DocumentNumber.normalize(payerDocument);
        if (payer == null || !DocumentNumber.isValidCpf(payer) && !DocumentNumber.isValidCnpj(payer)) {
            throw new IllegalArgumentException("documento do pagador invalido (P1.39)");
        }
        this.payerDocument = payer;
        this.payerBankCode = requireBankCode(payerBankCode, "banco do pagador");
        this.receiverBankCode = requireBankCode(receiverBankCode, "banco do recebedor");
        if (legacyReference == null || legacyReference.isBlank()) {
            throw new IllegalArgumentException(
                    "referencia da autorizacao no legado e obrigatoria (prova da importacao)");
        }
        this.legacyReference = legacyReference;
        this.importedAt = Objects.requireNonNull(importedAt, "instante da importacao e obrigatorio");
    }

    static String requireBankCode(String code, String who) {
        if (code == null || !code.matches("^[0-9]{3}$")) {
            throw new IllegalArgumentException(who + ": codigo de banco deve ter 3 digitos: " + code);
        }
        return code;
    }

    /** Condicao por optante da P0.30: banco do pagador = banco do recebedor. */
    public boolean sameBank() {
        return payerBankCode.equals(receiverBankCode);
    }

    /**
     * Conferencia por matricula: so confere a propria matricula. Apos a
     * conferencia, a autorizacao fica ATIVA_LEGADO se elegivel (mesmo
     * banco) ou INELEGIVEL_BANCO_DIVERSO, com o motivo no estado.
     */
    public void confer(String enrollmentIdToConfer, Instant at) {
        Objects.requireNonNull(at, "instante da conferencia e obrigatorio");
        if (status != Status.IMPORTADA_NAO_CONFERIDA) {
            throw new IllegalStateException("autorizacao ja conferida ou encerrada: " + status);
        }
        if (!enrollmentId.equals(enrollmentIdToConfer)) {
            throw new IllegalArgumentException("conferencia por matricula divergente: esperada "
                    + enrollmentId + ", veio " + enrollmentIdToConfer);
        }
        this.conferredAt = at;
        this.status = sameBank() ? Status.ATIVA_LEGADO : Status.INELEGIVEL_BANCO_DIVERSO;
    }

    /** Cancelamento do optante (gera o registro de cancelamento na remessa). */
    public void cancel(Instant at) {
        Objects.requireNonNull(at, "instante do cancelamento e obrigatorio");
        if (status != Status.ATIVA_LEGADO) {
            throw new IllegalStateException("so optante ativo no legado pode ser cancelado: " + status);
        }
        this.status = Status.CANCELADA;
        this.cancelledAt = at;
    }

    /** Conversao ao Pix Automatico com consentimento guardado (campanha da P4.5). */
    public void markConvertedToPix(Instant at) {
        Objects.requireNonNull(at, "instante da conversao e obrigatorio");
        if (status != Status.ATIVA_LEGADO) {
            throw new IllegalStateException("so optante ativo no legado pode converter: " + status);
        }
        this.status = Status.CONVERTIDA_PIX;
        this.convertedAt = at;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public String enrollmentId() { return enrollmentId; }
    public UUID unitId() { return unitId; }
    public String payerDocument() { return payerDocument; }
    public String payerBankCode() { return payerBankCode; }
    public String receiverBankCode() { return receiverBankCode; }
    public String legacyReference() { return legacyReference; }
    public Instant importedAt() { return importedAt; }
    public Status status() { return status; }
    public Instant conferredAt() { return conferredAt; }
    public Instant cancelledAt() { return cancelledAt; }
    public Instant convertedAt() { return convertedAt; }
}
