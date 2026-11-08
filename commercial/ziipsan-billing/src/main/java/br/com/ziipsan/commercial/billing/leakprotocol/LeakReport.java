package br.com.ziipsan.commercial.billing.leakprotocol;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Laudo da revisao por vazamento (ED-23): documento logico emitido a
 * partir da {@link FieldInspection}, no celular ou no backoffice, SEM
 * versao em papel -- a via do usuario e o documento assinado.
 *
 * <p>A conclusao e ato tecnico registrado ({@link Conclusion}):
 * <ul>
 *   <li>laudo que conclui {@code LEAK_CONFIRMED} exige vistoria com
 *       vazamento encontrado -- conclusao contra o fato de campo nao
 *       se constroi;</li>
 *   <li>laudo {@code INCONCLUSIVE} exige o motivo da inconclusao
 *       registrado (o protocolo volta a fase de vistoria ou e decidido
 *       com o motivo, nunca some);</li>
 *   <li>o laudo so sustenta decisao DEPOIS de assinado no nivel da
 *       P3.34 (ver {@link LeakDecisionFlow}); nao assinado, ele e um
 *       rascunho nomeado ({@link #signedReceipt()} vazio).</li>
 * </ul>
 * Nenhum criterio tecnico de vazamento e codificado aqui: a conclusao
 * vem do tecnico responsavel, identificada por {@code issuedBy}.
 */
public final class LeakReport {

    /** Conclusao tecnica do laudo. */
    public enum Conclusion {
        LEAK_CONFIRMED,
        NO_LEAK_FOUND,
        INCONCLUSIVE
    }

    private final UUID reportId;
    private final UUID inspectionServiceOrderId;
    private final Conclusion conclusion;
    private final String inconclusiveReason;
    private final UUID issuedBy;
    private final Instant issuedAt;
    private LeakProtocolPorts.SignatureReceipt signedReceipt;

    public LeakReport(UUID reportId, FieldInspection inspection, Conclusion conclusion,
                      String inconclusiveReason, UUID issuedBy, Instant issuedAt) {
        this.reportId = Objects.requireNonNull(reportId, "id do laudo e obrigatorio");
        Objects.requireNonNull(inspection, "vistoria do laudo e obrigatoria");
        this.inspectionServiceOrderId = inspection.serviceOrderId();
        this.conclusion = Objects.requireNonNull(conclusion, "conclusao do laudo e obrigatoria");
        this.issuedBy = Objects.requireNonNull(issuedBy, "responsavel tecnico do laudo e obrigatorio");
        this.issuedAt = Objects.requireNonNull(issuedAt, "instante de emissao do laudo e obrigatorio");
        if (conclusion == Conclusion.LEAK_CONFIRMED && !inspection.leakFound()) {
            throw new IllegalArgumentException(
                    "laudo nao pode concluir LEAK_CONFIRMED: a vistoria nao registrou vazamento encontrado");
        }
        if (conclusion == Conclusion.INCONCLUSIVE
                && (inconclusiveReason == null || inconclusiveReason.isBlank())) {
            throw new IllegalArgumentException(
                    "laudo INCONCLUSIVE exige o motivo da inconclusao registrado");
        }
        this.inconclusiveReason = inconclusiveReason;
    }

    public UUID reportId() {
        return reportId;
    }

    /** OS LEAK_REVIEW cuja vistoria sustenta este laudo. */
    public UUID inspectionServiceOrderId() {
        return inspectionServiceOrderId;
    }

    public Conclusion conclusion() {
        return conclusion;
    }

    public String inconclusiveReason() {
        return inconclusiveReason;
    }

    public UUID issuedBy() {
        return issuedBy;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    /** Recibo da assinatura (P3.34); vazio enquanto o laudo nao foi assinado. */
    public LeakProtocolPorts.SignatureReceipt signedReceipt() {
        return signedReceipt;
    }

    public boolean signed() {
        return signedReceipt != null;
    }

    /** Referencia logica do documento para a P3.34 assinar. */
    public String documentRef() {
        return "leak-report:" + reportId;
    }

    /** Registra o recibo da assinatura; assinar duas vezes e recusado. */
    public void markSigned(LeakProtocolPorts.SignatureReceipt receipt) {
        Objects.requireNonNull(receipt, "recibo da assinatura do laudo e obrigatorio");
        if (signedReceipt != null) {
            throw new IllegalStateException("laudo ja assinado (codigo "
                    + signedReceipt.verificationCode() + "): nova assinatura exige novo laudo");
        }
        this.signedReceipt = receipt;
    }
}
