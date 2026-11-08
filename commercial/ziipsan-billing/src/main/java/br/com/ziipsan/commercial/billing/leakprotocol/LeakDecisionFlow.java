package br.com.ziipsan.commercial.billing.leakprotocol;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Fluxo de decisao da ED-23: amarra, na ordem, as pecas que o aceite
 * nomeia -- laudo assinado (P3.34), decisao assinada (P3.34) e fatura
 * alterada SOMENTE pela retificacao da P2.20 com os volumes da P3.28.
 *
 * <ol>
 *   <li>{@link #signReport}: assina o laudo como {@code LAB_REPORT} na
 *       P3.34 (o nivel e o {@code sign.min_level.lab_report} do tenant,
 *       resolvido e exigido la dentro) e anexa o laudo assinado ao
 *       protocolo;</li>
 *   <li>{@link #decideGranted}: confere a coerencia minima entre laudo
 *       e desconto (GRANT por vazamento exige laudo
 *       {@code LEAK_CONFIRMED} -- o desconto em si, percentuais e
 *       volumes sao da P3.28 e chegam prontos em
 *       {@link LeakProtocolPorts.GrantedVolumes}), assina a decisao
 *       como {@code SERVICE_ORDER_ACCEPTANCE}, pede a retificacao a
 *       P2.20 e fecha o protocolo como GRANTED com o recibo dela;</li>
 *   <li>{@link #decideDenied}: assina a decisao e fecha como DENIED,
 *       sem tocar a P2.20.</li>
 * </ol>
 *
 * <p>Cada passo notifica a fase pela porta da P3.5/ED-41. Se qualquer
 * porta recusar (assinatura sem nivel no tenant, retificacao barrada
 * pelos limites da P2.20), a excecao propaga e o protocolo PERMANECE
 * na fase em que estava -- nenhum fechamento e fingido.
 *
 * <p><b>Ordem validar -&gt; assinar -&gt; avancar (correcao M2 da
 * revisao):</b> nenhuma chamada a {@code SigningPort} acontece antes
 * de as guardas de fase e de conclusao do protocolo passarem --
 * operacao recusada nao consome assinatura da P3.34 nem deixa laudo
 * assinado orfao.
 *
 * <p><b>Retomada apos recusa da P2.20 (correcao M1 da revisao,
 * decisao 4):</b> se a retificacao for recusada, o protocolo fica em
 * {@code RECTIFICATION} com a decisao ja assinada registrada; repetir
 * {@link #decideGranted} com a mesma chave de idempotencia RETOMA a
 * retificacao a partir de {@code RECTIFICATION} reutilizando essa
 * decisao -- nao assina de novo nem exige a fase {@code DECISION}.
 *
 * <p><b>Observacao da proposta tarifaria (DECISOES item 32):</b> esta
 * entrega e consumidora da conta (le a fatura e pede a retificacao).
 * Conforme a regra da cascata, a proposta NAO e executada: o desenho
 * vigente (retificacao da P2.20 sobre a conta da P2.5) e seguido como
 * esta, e a observacao fica registrada aqui e no relatorio.
 */
public final class LeakDecisionFlow {

    private final LeakProtocolPorts.SigningPort signing;
    private final LeakProtocolPorts.RectificationPort rectification;
    private final LeakProtocolPorts.NotificationPort notifications;

    public LeakDecisionFlow(LeakProtocolPorts.SigningPort signing,
                            LeakProtocolPorts.RectificationPort rectification,
                            LeakProtocolPorts.NotificationPort notifications) {
        this.signing = Objects.requireNonNull(signing, "porta de assinatura (P3.34) e obrigatoria");
        this.rectification = Objects.requireNonNull(rectification, "porta de retificacao (P2.20) e obrigatoria");
        this.notifications = Objects.requireNonNull(notifications, "porta de notificacao (P3.5/ED-41) e obrigatoria");
    }

    /** Assina o laudo na P3.34 e o anexa ao protocolo (abre a fase DECISION). */
    public LeakReport signReport(LeakReviewProtocol protocol, LeakReport report, UUID signerId, Instant at) {
        Objects.requireNonNull(protocol, "protocolo e obrigatorio");
        Objects.requireNonNull(report, "laudo e obrigatorio");
        // M2: validar fase e conclusao ANTES de assinar -- as mesmas
        // guardas que attachSignedReport/markSigned aplicariam depois,
        // antecipadas para a recusa nao consumir assinatura da P3.34.
        if (protocol.phase() != LeakReviewProtocol.Phase.REPORT) {
            throw new IllegalStateException("assinar laudo exige fase REPORT"
                    + "; protocolo esta em " + protocol.phase());
        }
        if (!report.inspectionServiceOrderId().equals(protocol.leakServiceOrderId())) {
            throw new IllegalArgumentException("laudo de vistoria de outra OS: recusado");
        }
        if (report.signed()) {
            throw new IllegalStateException("laudo ja assinado (codigo "
                    + report.signedReceipt().verificationCode()
                    + "): nova assinatura exige novo laudo");
        }
        if (report.conclusion() == LeakReport.Conclusion.INCONCLUSIVE) {
            throw new IllegalStateException(
                    "laudo INCONCLUSIVE nao abre decisao: devolva o protocolo a vistoria");
        }
        LeakProtocolPorts.SignatureReceipt receipt = signing.sign(
                protocol.tenantId(), LeakProtocolPorts.SignableKind.LAB_REPORT,
                report.documentRef(), signerId);
        report.markSigned(receipt);
        protocol.attachSignedReport(report, signerId, at);
        notifications.notifyPhase(protocol.tenantId(), protocol.protocolId(), "REPORT_SIGNED", protocol.billId());
        return report;
    }

    /**
     * GRANT completo: decisao assinada + retificacao pela P2.20 +
     * fechamento GRANTED. Os volumes vem da decisao da P3.28.
     */
    public LeakProtocolPorts.RectificationReceipt decideGranted(
            LeakReviewProtocol protocol, LeakProtocolPorts.GrantedVolumes volumes,
            UUID deciderId, String idempotencyKey, Instant at) {
        Objects.requireNonNull(protocol, "protocolo e obrigatorio");
        Objects.requireNonNull(volumes, "volumes revistos da P3.28 sao obrigatorios no GRANT");
        Objects.requireNonNull(idempotencyKey, "chave de idempotencia da retificacao e obrigatoria");
        // M2 + M1: validar fase e conclusao ANTES de assinar.
        if (protocol.phase() == LeakReviewProtocol.Phase.RECTIFICATION) {
            // M1: retomada documentada (decisao 4) -- a decisao ja foi
            // assinada e registrada na tentativa anterior; reutiliza-la
            // e ir direto a retificacao, sem nova assinatura.
            LeakReport resumedReport = protocol.report();
            if (resumedReport == null) {
                throw new IllegalStateException("GRANT exige laudo assinado anexado ao protocolo");
            }
            if (resumedReport.conclusion() != LeakReport.Conclusion.LEAK_CONFIRMED) {
                throw new IllegalStateException(
                        "GRANT por vazamento exige laudo LEAK_CONFIRMED; laudo atual: "
                                + resumedReport.conclusion());
            }
            if (protocol.decisionSignature() == null) {
                throw new IllegalStateException(
                        "retomada em RECTIFICATION exige a decisao GRANTED ja assinada");
            }
            LeakProtocolPorts.RectificationReceipt resumedReceipt = rectification.rectifyByLeakReview(
                    protocol.tenantId(), protocol.billId(), volumes, protocol.protocolId(),
                    idempotencyKey, deciderId);
            protocol.completeRectification(resumedReceipt, deciderId, at);
            notifications.notifyPhase(protocol.tenantId(), protocol.protocolId(),
                    "CLOSED_GRANTED", protocol.billId());
            return resumedReceipt;
        }
        if (protocol.phase() != LeakReviewProtocol.Phase.DECISION) {
            throw new IllegalStateException("registrar decisao GRANTED exige fase DECISION"
                    + "; protocolo esta em " + protocol.phase());
        }
        LeakReport report = protocol.report();
        if (report == null) {
            throw new IllegalStateException("GRANT exige laudo assinado anexado ao protocolo");
        }
        if (report.conclusion() != LeakReport.Conclusion.LEAK_CONFIRMED) {
            throw new IllegalStateException(
                    "GRANT por vazamento exige laudo LEAK_CONFIRMED; laudo atual: " + report.conclusion());
        }
        LeakProtocolPorts.SignatureReceipt signature = signing.sign(
                protocol.tenantId(), LeakProtocolPorts.SignableKind.DECISION,
                "leak-decision:" + protocol.protocolId() + ":GRANT", deciderId);
        protocol.recordGrantedDecision(signature, deciderId, at);
        LeakProtocolPorts.RectificationReceipt receipt = rectification.rectifyByLeakReview(
                protocol.tenantId(), protocol.billId(), volumes, protocol.protocolId(),
                idempotencyKey, deciderId);
        protocol.completeRectification(receipt, deciderId, at);
        notifications.notifyPhase(protocol.tenantId(), protocol.protocolId(), "CLOSED_GRANTED", protocol.billId());
        return receipt;
    }

    /** DENY completo: decisao assinada e fechamento, sem retificacao. */
    public void decideDenied(LeakReviewProtocol protocol, UUID deciderId, Instant at) {
        Objects.requireNonNull(protocol, "protocolo e obrigatorio");
        // M2: validar fase ANTES de assinar -- protocolo ja encerrado
        // (ou fora de DECISION) e recusado sem assinatura espuria.
        if (protocol.phase() != LeakReviewProtocol.Phase.DECISION) {
            throw new IllegalStateException("registrar decisao DENIED exige fase DECISION"
                    + "; protocolo esta em " + protocol.phase());
        }
        if (protocol.report() == null) {
            throw new IllegalStateException("DENY exige laudo assinado anexado ao protocolo");
        }
        LeakProtocolPorts.SignatureReceipt signature = signing.sign(
                protocol.tenantId(), LeakProtocolPorts.SignableKind.DECISION,
                "leak-decision:" + protocol.protocolId() + ":DENY", deciderId);
        protocol.recordDeniedDecision(signature, deciderId, at);
        notifications.notifyPhase(protocol.tenantId(), protocol.protocolId(), "CLOSED_DENIED", protocol.billId());
    }
}
