package br.com.ziipsan.core.watersystems;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Emissao do relatorio anual (ED-79) — FRONTEIRA DURA do aceite:
 * <ol>
 *   <li>a emissao da mensagem legal e do MOTOR DA P2.32
 *       ({@link SystemsPorts.LegalMessagePort}), com a PROVA que ele
 *       devolve — esta tarefa nao tem motor proprio, nao seleciona
 *       nem ordena mensagens;</li>
 *   <li>sem prova da P2.32, NAO ha envio: a operacao recusa com
 *       {@code LEGAL_EMISSION_PROOF_MISSING} antes de tocar na
 *       entrega;</li>
 *   <li>a entrega e das NOTIFICACOES DA P3.5
 *       ({@link SystemsPorts.NotificationPort}): o resultado dela
 *       (tentativa ou motivo nomeado) fica gravado no registro de
 *       emissao; o relatorio so vira ISSUED com prova + entrega
 *       despachada — tentativa recusada pela P3.5 deixa o relatorio
 *       READY para nova tentativa, com o motivo registrado.</li>
 * </ol>
 * As mensagens legais DA FATURA seguem o mesmo motor: quando a fatura
 * do ciclo carrega o relatorio/bloco, quem emite e a P2.32 no ciclo da
 * conta (P2.24/P2.8) — esta tarefa nao emite mensagem de fatura por
 * conta propria em nenhum caminho.
 *
 * <p><b>Correcao D1 (revisao da ED-79, 08/11/2026):</b>
 * {@link IssuanceRecord} era um record de construtor publico — a
 * sonda A3 do revisor gravou uma emissao ISSUED com
 * {@code legalProofRef="DIGITADO-A-MAO-123"} direto pelo store.
 * Agora e classe final SELADA (construtor privado, padrao M-1 da
 * ED-50): nasce SOMENTE pelas fabricas de pacote
 * {@link IssuanceRecord#issued}/{@link IssuanceRecord#deliveryNotSent},
 * chamadas por este servico. E o servico passou a conferir o VINCULO
 * da prova com o pedido (conta, versao da conta e a chave pedida
 * entre as mensagens emitidas): prova desencontrada recusa com
 * {@code LEGAL_EMISSION_PROOF_MISMATCH} antes da entrega.
 */
public final class AnnualReportIssuanceService {
    private AnnualReportIssuanceService() {}

    public enum IssuanceOutcome { ISSUED, DELIVERY_NOT_SENT }

    /**
     * Registro de uma tentativa de emissao: prova da P2.32 + resultado
     * da entrega da P3.5. SELADO (correcao D1): construtor privado;
     * os acessores seguem o estilo do record original.
     */
    public static final class IssuanceRecord {
        private final UUID id;
        private final UUID tenantId;
        private final UUID reportId;
        private final String legalProofRef;
        private final int contentCatalogVersion;
        private final int messageCatalogVersion;
        private final UUID notificationAttemptId;
        private final IssuanceOutcome outcome;
        private final String deliveryReason;
        private final Instant at;

        private IssuanceRecord(UUID id, UUID tenantId, UUID reportId, String legalProofRef,
                               int contentCatalogVersion, int messageCatalogVersion,
                               UUID notificationAttemptId, IssuanceOutcome outcome,
                               String deliveryReason, Instant at) {
            Objects.requireNonNull(id, "id do registro de emissao e obrigatorio");
            Objects.requireNonNull(tenantId, "tenant e obrigatorio");
            Objects.requireNonNull(reportId, "relatorio do registro e obrigatorio");
            if (legalProofRef == null || legalProofRef.isBlank())
                throw new IllegalArgumentException("registro de emissao exige a prova da P2.32");
            Objects.requireNonNull(outcome, "resultado da emissao e obrigatorio");
            Objects.requireNonNull(at, "instante da emissao e obrigatorio");
            if (outcome == IssuanceOutcome.ISSUED && notificationAttemptId == null)
                throw new IllegalArgumentException("emissao concluida exige a tentativa de entrega da P3.5");
            if (outcome == IssuanceOutcome.DELIVERY_NOT_SENT && (deliveryReason == null || deliveryReason.isBlank()))
                throw new IllegalArgumentException("entrega nao despachada exige o motivo nomeado da P3.5");
            this.id = id;
            this.tenantId = tenantId;
            this.reportId = reportId;
            this.legalProofRef = legalProofRef;
            this.contentCatalogVersion = contentCatalogVersion;
            this.messageCatalogVersion = messageCatalogVersion;
            this.notificationAttemptId = notificationAttemptId;
            this.outcome = outcome;
            this.deliveryReason = deliveryReason;
            this.at = at;
        }

        /** Nascimento de emissao concluida (acesso de pacote — so o servico de emissao). */
        static IssuanceRecord issued(UUID id, UUID tenantId, UUID reportId,
                                     SystemsPorts.LegalEmissionProof proof,
                                     UUID notificationAttemptId, Instant at) {
            return new IssuanceRecord(id, tenantId, reportId, proof.proofRef(),
                    proof.contentCatalogVersion(), proof.messageCatalogVersion(),
                    notificationAttemptId, IssuanceOutcome.ISSUED, null, at);
        }

        /** Nascimento de tentativa nao despachada (acesso de pacote — so o servico de emissao). */
        static IssuanceRecord deliveryNotSent(UUID id, UUID tenantId, UUID reportId,
                                              SystemsPorts.LegalEmissionProof proof,
                                              String deliveryReason, Instant at) {
            return new IssuanceRecord(id, tenantId, reportId, proof.proofRef(),
                    proof.contentCatalogVersion(), proof.messageCatalogVersion(),
                    null, IssuanceOutcome.DELIVERY_NOT_SENT, deliveryReason, at);
        }

        public UUID id() { return id; }
        public UUID tenantId() { return tenantId; }
        public UUID reportId() { return reportId; }
        public String legalProofRef() { return legalProofRef; }
        public int contentCatalogVersion() { return contentCatalogVersion; }
        public int messageCatalogVersion() { return messageCatalogVersion; }
        public UUID notificationAttemptId() { return notificationAttemptId; }
        public IssuanceOutcome outcome() { return outcome; }
        public String deliveryReason() { return deliveryReason; }
        public Instant at() { return at; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof IssuanceRecord other)) return false;
            return contentCatalogVersion == other.contentCatalogVersion
                    && messageCatalogVersion == other.messageCatalogVersion
                    && Objects.equals(id, other.id) && Objects.equals(tenantId, other.tenantId)
                    && Objects.equals(reportId, other.reportId)
                    && Objects.equals(legalProofRef, other.legalProofRef)
                    && Objects.equals(notificationAttemptId, other.notificationAttemptId)
                    && outcome == other.outcome
                    && Objects.equals(deliveryReason, other.deliveryReason)
                    && Objects.equals(at, other.at);
        }

        @Override public int hashCode() {
            return Objects.hash(id, tenantId, reportId, legalProofRef, contentCatalogVersion,
                    messageCatalogVersion, notificationAttemptId, outcome, deliveryReason, at);
        }

        @Override public String toString() {
            return "IssuanceRecord[id=" + id + ", tenantId=" + tenantId + ", reportId=" + reportId
                    + ", legalProofRef=" + legalProofRef + ", contentCatalogVersion=" + contentCatalogVersion
                    + ", messageCatalogVersion=" + messageCatalogVersion
                    + ", notificationAttemptId=" + notificationAttemptId + ", outcome=" + outcome
                    + ", deliveryReason=" + deliveryReason + ", at=" + at + "]";
        }
    }

    public static IssuanceRecord issue(SystemsStore store,
                                       SystemsPorts.LegalMessagePort legalMessages,
                                       SystemsPorts.NotificationPort notifications,
                                       UUID tenantId, UUID reportId,
                                       UUID billId, int billVersion,
                                       String catalogKey, Instant at) {
        AnnualQualityReport report = store.report(tenantId, reportId)
                .orElseThrow(() -> new SystemsExceptions.NotFoundException("REPORT_NOT_FOUND",
                        "relatorio inexistente: " + reportId));
        if (report.status() == AnnualQualityReport.Status.ISSUED)
            throw new SystemsExceptions.ValidationException("REPORT_ALREADY_ISSUED",
                    "relatorio ja emitido: " + reportId);
        if (report.status() != AnnualQualityReport.Status.READY)
            throw new SystemsExceptions.ValidationException("REPORT_NOT_READY",
                    "relatorio com meses faltantes nao emite (meses: " + report.monthsMissing() + "): " + reportId);

        SystemsPorts.LegalEmissionProof proof = legalMessages.emit(new SystemsPorts.LegalEmissionRequest(
                        tenantId, reportId, billId, billVersion, catalogKey, at))
                .orElseThrow(() -> new SystemsExceptions.ValidationException("LEGAL_EMISSION_PROOF_MISSING",
                        "motor de mensagens legais da P2.32 nao emitiu (sem prova de emissao): "
                                + "nenhum envio foi feito — chave pedida: " + catalogKey));

        // Correcao D1: a prova tem de ser DESTA emissao — mesma conta,
        // mesma versao da conta e a chave pedida entre as emitidas.
        // Prova desencontrada (replay de outra conta/versao/chave)
        // recusa ANTES de tocar na entrega.
        if (!Objects.equals(proof.billId(), billId) || proof.billVersion() != billVersion
                || !proof.emittedMessageKeys().contains(catalogKey))
            throw new SystemsExceptions.ValidationException("LEGAL_EMISSION_PROOF_MISMATCH",
                    "prova da P2.32 nao corresponde ao pedido (conta/versao/chave): "
                            + "nenhum envio foi feito — chave pedida: " + catalogKey);

        SystemsPorts.DeliveryOutcome delivery = notifications.deliver(new SystemsPorts.DeliveryRequest(
                tenantId, report.consumerUnitId(), reportId, SystemsPorts.ANNUAL_REPORT_PURPOSE, at));

        if (!delivery.sent()) {
            IssuanceRecord failed = IssuanceRecord.deliveryNotSent(UUID.randomUUID(), tenantId, reportId,
                    proof, delivery.reason(), at);
            store.saveIssuance(failed);
            return failed;
        }
        IssuanceRecord issued = IssuanceRecord.issued(UUID.randomUUID(), tenantId, reportId,
                proof, delivery.attemptId(), at);
        store.saveIssuance(issued);
        store.saveReport(report.markIssued());
        return issued;
    }
}
