package br.com.ziipsan.commercial.billing.leakprotocol;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Protocolo da revisao de consumo por vazamento (ED-23): a maquina de
 * fases que o aceite nomeia ("protocolo da revisao com fases, vistoria
 * e laudo"), sobre o pedido de vazamento que a P3.28 ja abre
 * ({@code leak_reviews} + OS {@code LEAK_REVIEW} filha da
 * {@code BILL_REVIEW} da P2.20).
 *
 * <pre>
 *   OPENED -&gt; INSPECTION -&gt; REPORT -&gt; DECISION -&gt; RECTIFICATION -&gt; CLOSED_GRANTED
 *                                  \-&gt; DECISION -&gt; CLOSED_DENIED   (sem retificacao)
 *   REPORT -&gt; INSPECTION   (laudo INCONCLUSIVE devolve a vistoria)
 *   qualquer fase nao final -&gt; CANCELLED (motivo obrigatorio)
 * </pre>
 *
 * <p>Guardas (nenhuma pulavel):
 * <ul>
 *   <li>a fase REPORT so abre com a {@link FieldInspection} registrada;</li>
 *   <li>a fase DECISION so abre com {@link LeakReport} emitido E
 *       assinado (a assinatura e aplicada por {@link LeakDecisionFlow}
 *       via P3.34 antes da transicao);</li>
 *   <li>a fase RECTIFICATION so abre com decisao GRANTED assinada, e o
 *       fechamento GRANTED so acontece com o recibo da retificacao da
 *       P2.20 -- fatura alterada SEM a P2.20 nao existe neste desenho;</li>
 *   <li>o fechamento DENIED nao passa por retificacao alguma.</li>
 * </ul>
 *
 * <p><b>Prazos de fase [A DEFINIR]:</b> nenhuma fonte do aceite fixa
 * prazo por fase deste protocolo; esta classe NAO conta prazo nenhum.
 * Prazo por fase, quando o dono/tenant definir, entra como parametro
 * do tenant e guarda nova sobre {@link #events()} -- pendencia
 * P-ED23-03 do relatorio. Os prazos que EXISTEM (reclamacao, pela
 * P3.28; suspensao da conta em revisao, pela P2.20) sao das donas e
 * nao sao recalculados aqui.
 */
public final class LeakReviewProtocol {

    /** Fases do protocolo. */
    public enum Phase {
        OPENED,
        INSPECTION,
        REPORT,
        DECISION,
        RECTIFICATION,
        CLOSED_GRANTED,
        CLOSED_DENIED,
        CANCELLED;

        public boolean terminal() {
            return this == CLOSED_GRANTED || this == CLOSED_DENIED || this == CANCELLED;
        }
    }

    /** Evento de fase: a trilha do protocolo (quem, quando, de onde para onde). */
    public record PhaseEvent(Phase from, Phase to, UUID actorId, Instant at, String note) {
    }

    private final UUID protocolId;
    private final UUID tenantId;
    private final UUID billId;
    /** Pedido de vazamento da P3.28 (leak_reviews) ao qual este protocolo se anexa. */
    private final UUID leakReviewId;
    /** OS LEAK_REVIEW da P3.28/P1.31 executada em campo pela ED-20. */
    private final UUID leakServiceOrderId;
    private final List<PhaseEvent> events = new ArrayList<>();
    private Phase phase = Phase.OPENED;
    private FieldInspection inspection;
    private LeakReport report;
    private LeakProtocolPorts.SignatureReceipt decisionSignature;
    private LeakProtocolPorts.RectificationReceipt rectification;

    public LeakReviewProtocol(UUID protocolId, UUID tenantId, UUID billId,
                              UUID leakReviewId, UUID leakServiceOrderId, UUID actorId, Instant at) {
        this.protocolId = Objects.requireNonNull(protocolId, "id do protocolo e obrigatorio");
        this.tenantId = Objects.requireNonNull(tenantId, "tenant do protocolo e obrigatorio");
        this.billId = Objects.requireNonNull(billId, "fatura da revisao e obrigatoria");
        this.leakReviewId = Objects.requireNonNull(leakReviewId,
                "pedido de vazamento da P3.28 (leakReviewId) e obrigatorio: a ED-23 nao abre pedido paralelo");
        this.leakServiceOrderId = Objects.requireNonNull(leakServiceOrderId,
                "OS LEAK_REVIEW da P3.28 e obrigatoria");
        events.add(new PhaseEvent(null, Phase.OPENED,
                Objects.requireNonNull(actorId, "ator da abertura e obrigatorio"),
                Objects.requireNonNull(at, "instante da abertura e obrigatorio"),
                "protocolo aberto sobre o pedido da P3.28"));
    }

    public UUID protocolId() {
        return protocolId;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID billId() {
        return billId;
    }

    public UUID leakReviewId() {
        return leakReviewId;
    }

    public UUID leakServiceOrderId() {
        return leakServiceOrderId;
    }

    public Phase phase() {
        return phase;
    }

    public List<PhaseEvent> events() {
        return List.copyOf(events);
    }

    public FieldInspection inspection() {
        return inspection;
    }

    public LeakReport report() {
        return report;
    }

    public LeakProtocolPorts.SignatureReceipt decisionSignature() {
        return decisionSignature;
    }

    public LeakProtocolPorts.RectificationReceipt rectification() {
        return rectification;
    }

    /** OPENED -&gt; INSPECTION: a vistoria em campo comeca (OS da ED-20 despachada). */
    public void beginInspection(UUID actorId, Instant at) {
        move(Phase.OPENED, Phase.INSPECTION, actorId, at, "vistoria iniciada");
    }

    /**
     * Registra a vistoria concluida e abre a fase do laudo
     * (INSPECTION -&gt; REPORT). A vistoria precisa ser da OS deste
     * protocolo -- vistoria de outra OS nao sustenta este laudo.
     */
    public void recordInspection(FieldInspection fieldInspection, UUID actorId, Instant at) {
        Objects.requireNonNull(fieldInspection, "vistoria e obrigatoria");
        if (!fieldInspection.serviceOrderId().equals(leakServiceOrderId)) {
            throw new IllegalArgumentException(
                    "vistoria de OS diferente da OS LEAK_REVIEW deste protocolo: recusada");
        }
        requirePhase(Phase.INSPECTION, "registrar vistoria");
        this.inspection = fieldInspection;
        move(Phase.INSPECTION, Phase.REPORT, actorId, at, "vistoria registrada; laudo pendente");
    }

    /**
     * Anexa o laudo ASSINADO e abre a fase de decisao (REPORT -&gt;
     * DECISION). Laudo inconclusivo NAO abre decisao: devolve o
     * protocolo a vistoria ({@link #returnToInspection}).
     */
    public void attachSignedReport(LeakReport signedReport, UUID actorId, Instant at) {
        Objects.requireNonNull(signedReport, "laudo e obrigatorio");
        requirePhase(Phase.REPORT, "anexar laudo");
        if (!signedReport.inspectionServiceOrderId().equals(leakServiceOrderId)) {
            throw new IllegalArgumentException("laudo de vistoria de outra OS: recusado");
        }
        if (!signedReport.signed()) {
            throw new IllegalStateException(
                    "laudo sem assinatura da P3.34 nao abre a fase de decisao (Lei 14.063/2020, nivel do tenant)");
        }
        if (signedReport.conclusion() == LeakReport.Conclusion.INCONCLUSIVE) {
            throw new IllegalStateException(
                    "laudo INCONCLUSIVE nao abre decisao: devolva o protocolo a vistoria");
        }
        this.report = signedReport;
        move(Phase.REPORT, Phase.DECISION, actorId, at, "laudo assinado anexado");
    }

    /** REPORT -&gt; INSPECTION: o laudo inconclusivo (ou a falta dele) devolve a campo. */
    public void returnToInspection(String reason, UUID actorId, Instant at) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("motivo da devolucao a vistoria e obrigatorio");
        }
        requirePhase(Phase.REPORT, "devolver a vistoria");
        this.inspection = null;
        move(Phase.REPORT, Phase.INSPECTION, actorId, at, reason);
    }

    /**
     * Registra a assinatura da decisao (P3.34) e, no GRANT, abre a
     * retificacao (DECISION -&gt; RECTIFICATION). Decisao sem assinatura
     * nao transiciona.
     */
    public void recordGrantedDecision(LeakProtocolPorts.SignatureReceipt signature, UUID actorId, Instant at) {
        Objects.requireNonNull(signature, "assinatura da decisao (P3.34) e obrigatoria");
        requirePhase(Phase.DECISION, "registrar decisao GRANTED");
        this.decisionSignature = signature;
        move(Phase.DECISION, Phase.RECTIFICATION, actorId, at, "decisao GRANTED assinada");
    }

    /** Aplica o recibo da P2.20 e fecha como GRANTED (RECTIFICATION -&gt; CLOSED_GRANTED). */
    public void completeRectification(LeakProtocolPorts.RectificationReceipt receipt, UUID actorId, Instant at) {
        Objects.requireNonNull(receipt, "recibo da retificacao da P2.20 e obrigatorio");
        requirePhase(Phase.RECTIFICATION, "concluir retificacao");
        this.rectification = receipt;
        move(Phase.RECTIFICATION, Phase.CLOSED_GRANTED, actorId, at,
                "fatura retificada pela P2.20, operacao " + receipt.operationRecordId());
    }

    /** Fecha como DENIED com a decisao assinada (DECISION -&gt; CLOSED_DENIED), sem retificacao. */
    public void recordDeniedDecision(LeakProtocolPorts.SignatureReceipt signature, UUID actorId, Instant at) {
        Objects.requireNonNull(signature, "assinatura da decisao (P3.34) e obrigatoria");
        requirePhase(Phase.DECISION, "registrar decisao DENIED");
        this.decisionSignature = signature;
        move(Phase.DECISION, Phase.CLOSED_DENIED, actorId, at, "decisao DENIED assinada; sem retificacao");
    }

    /** Cancela o protocolo em qualquer fase nao final, com motivo obrigatorio. */
    public void cancel(String reason, UUID actorId, Instant at) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("motivo do cancelamento do protocolo e obrigatorio");
        }
        if (phase.terminal()) {
            throw new IllegalStateException("protocolo em fase final (" + phase + ") nao pode ser cancelado");
        }
        move(phase, Phase.CANCELLED, actorId, at, reason);
    }

    private void move(Phase from, Phase to, UUID actorId, Instant at, String note) {
        requirePhase(from, "transicao para " + to);
        events.add(new PhaseEvent(from, to,
                Objects.requireNonNull(actorId, "ator da transicao e obrigatorio"),
                Objects.requireNonNull(at, "instante da transicao e obrigatorio"), note));
        this.phase = to;
    }

    private void requirePhase(Phase expected, String action) {
        if (phase != expected) {
            throw new IllegalStateException(action + " exige fase " + expected
                    + "; protocolo esta em " + phase);
        }
    }
}
