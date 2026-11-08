package br.com.ziipsan.commercial.billing.field;

import br.com.ziipsan.commercial.billingrules.DivergenceEvaluator;
import br.com.ziipsan.commercial.billingrules.FieldBillCalculator;
import br.com.ziipsan.commercial.billingrules.FieldBillInput;
import br.com.ziipsan.commercial.billingrules.FieldBillResult;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Submissao da conta de campo (ED-19): o coracao servidor da emissao
 * simultanea. Ordem das regras (nenhuma conta nasce antes dos portoes):
 *
 * <ol>
 *   <li><b>Idempotencia por task_id:</b> ja existe registro para
 *       (tenant, task_id)? Devolve o registro como esta, com
 *       {@code duplicate = true} — sem nova conta, sem nova NFAg-e, sem
 *       reenfileirar divergencia, sem realertar a Central. O reenvio
 *       (lote a cada N, queda de rede, reimpressao) e o caso normal.</li>
 *   <li><b>Portao OCR (ED-18):</b> veredito REVIEW segura: registro nasce
 *       {@link FieldBillStatus#OCR_HELD} e nada e faturado ate a revisao.</li>
 *   <li><b>Portao de assinatura (P3.34):</b> tipo que exige assinatura com
 *       certificado ausente (ou nivel abaixo do minimo) no aparelho:
 *       registro nasce {@link FieldBillStatus#CERTIFICATE_MISSING}.</li>
 *   <li><b>Recalculo:</b> o servidor remonta a entrada (o retrato esperado
 *       e o que ELE emitiu no pacote da rota) e recalcula com o MESMO
 *       {@link FieldBillCalculator} do aparelho.</li>
 *   <li><b>Divergencia:</b> {@link DivergenceEvaluator} — de base ou de
 *       valor: a conta nasce, vai a fila, a Central e alertada UMA vez e o
 *       desfecho e {@link FieldBillStatus#DIVERGENT_QUEUED}. A aprovacao e
 *       a correcao por nova versao sao do
 *       {@link DivergenceApprovalService}.</li>
 *   <li><b>Identico:</b> {@link FieldBillStatus#CONFIRMED}.</li>
 * </ol>
 *
 * <p>Nos ramos de portao (2 e 3) o {@code billId} e reservado e estavel
 * para a tarefa (o registro existe), mas nenhuma cobranca e confirmada:
 * a situacao do registro diz exatamente por que.
 */
public final class FieldBillSubmissionService {

    /** Fuso de registro da submissao (DAD-18). */
    public static final ZoneId RECEIPT_ZONE = ZoneId.of("America/Sao_Paulo");

    /**
     * @param fieldInput   entrada como calculada em campo (retrato do aparelho)
     * @param fieldPrinted resultado impresso em campo
     * @param serverInput  entrada remontada pelo servidor (retrato esperado,
     *                     emitido por ele no pacote da rota)
     * @param ocrVerdict   nome do veredito OCR da ED-18
     * @param requiredSignatureLevel nome do nivel minimo da P3.34 para o
     *                     tipo de documento; {@code null} = tipo sem assinatura
     * @param deviceCertificatePresent certificado exigido presente no aparelho
     * @param deviceSignatureLevel nivel assinado no aparelho; {@code null} = nao assinou
     * @param nfageContingency contingencia da NFAg-e declarada
     * @param nfageContingencyMotive motivo da contingencia (obrigatorio se declarada)
     */
    public record Submission(UUID tenantId, UUID routeId,
                             FieldBillInput fieldInput, FieldBillResult fieldPrinted,
                             FieldBillInput serverInput,
                             String ocrVerdict, String requiredSignatureLevel,
                             boolean deviceCertificatePresent, String deviceSignatureLevel,
                             boolean nfageContingency, String nfageContingencyMotive) {

        public Submission {
            Objects.requireNonNull(tenantId, "tenant da submissao e obrigatorio");
            Objects.requireNonNull(fieldInput, "entrada de campo e obrigatoria");
            Objects.requireNonNull(fieldPrinted, "resultado impresso e obrigatorio");
            Objects.requireNonNull(serverInput, "entrada do servidor e obrigatoria");
            if (!fieldInput.taskId().equals(fieldPrinted.taskId())
                    || !fieldInput.taskId().equals(serverInput.taskId())) {
                throw new IllegalArgumentException("Submissao mistura task_id diferentes");
            }
        }

        public UUID taskId() {
            return fieldInput.taskId();
        }
    }

    /** @param duplicate {@code true} quando e reenvio de tarefa ja registrada */
    public record Outcome(FieldBillRecord record, boolean duplicate, UUID divergenceQueueItemId) {
    }

    private final Clock clock;
    private final FieldBillPorts.FieldBillStore store;
    private final FieldBillPorts.DivergenceQueuePort divergenceQueue;
    private final FieldBillPorts.CentralAlertPort centralAlerts;
    private final Supplier<UUID> billIds;

    public FieldBillSubmissionService(Clock clock, FieldBillPorts.FieldBillStore store,
                                      FieldBillPorts.DivergenceQueuePort divergenceQueue,
                                      FieldBillPorts.CentralAlertPort centralAlerts,
                                      Supplier<UUID> billIds) {
        this.clock = Objects.requireNonNull(clock, "Clock e obrigatorio");
        this.store = Objects.requireNonNull(store, "porta de persistencia e obrigatoria");
        this.divergenceQueue = Objects.requireNonNull(divergenceQueue, "porta da fila e obrigatoria");
        this.centralAlerts = Objects.requireNonNull(centralAlerts, "porta de alertas e obrigatoria");
        this.billIds = Objects.requireNonNull(billIds, "gerador de billId e obrigatorio");
    }

    public Outcome submit(Submission submission) {
        Objects.requireNonNull(submission, "submissao e obrigatoria");

        // 1. Idempotencia: reenvio devolve o registro, sem efeitos novos.
        var prior = store.findByTask(submission.tenantId(), submission.taskId());
        if (prior.isPresent()) {
            return new Outcome(prior.get(), true, null);
        }

        // Contingencia validada antes de qualquer registro (motivo exigido).
        NfageContingencyPolicy.FiscalSituation fiscal = NfageContingencyPolicy.situation(
                submission.nfageContingency(), submission.nfageContingencyMotive());
        boolean contingency = fiscal == NfageContingencyPolicy.FiscalSituation.CONTINGENCY_PENDING_TRANSMISSION;

        FieldBillResult serverRecalc = FieldBillCalculator.calculate(submission.serverInput());
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), RECEIPT_ZONE);
        UUID billId = billIds.get();

        // 2. Portao OCR (ED-18): critica segura a emissao.
        FieldEmissionGates.OcrGate ocr = FieldEmissionGates.OcrGate.fromEd18Verdict(submission.ocrVerdict());
        if (FieldEmissionGates.ocrHolds(ocr)) {
            FieldBillRecord held = new FieldBillRecord(submission.tenantId(), submission.taskId(),
                    submission.routeId(), billId, submission.fieldPrinted(), serverRecalc,
                    FieldBillStatus.OCR_HELD, 1, contingency, now);
            return new Outcome(store.save(held), false, null);
        }

        // 3. Portao de assinatura (P3.34): certificado ausente nao emite.
        FieldEmissionGates.SignatureGateLevel required =
                FieldEmissionGates.SignatureGateLevel.fromP334Name(submission.requiredSignatureLevel());
        FieldEmissionGates.SignatureGateLevel signed =
                FieldEmissionGates.SignatureGateLevel.fromP334Name(submission.deviceSignatureLevel());
        if (FieldEmissionGates.signatureBlocks(required, submission.deviceCertificatePresent(), signed)) {
            FieldBillRecord missing = new FieldBillRecord(submission.tenantId(), submission.taskId(),
                    submission.routeId(), billId, submission.fieldPrinted(), serverRecalc,
                    FieldBillStatus.CERTIFICATE_MISSING, 1, contingency, now);
            return new Outcome(store.save(missing), false, null);
        }

        // 4-6. Recalculo comparado: divergencia enfileira + alerta; igual confirma.
        DivergenceEvaluator.Divergence divergence = DivergenceEvaluator.evaluate(
                submission.fieldInput(), submission.fieldPrinted(),
                submission.serverInput(), serverRecalc);
        if (divergence.divergent()) {
            FieldBillRecord queued = new FieldBillRecord(submission.tenantId(), submission.taskId(),
                    submission.routeId(), billId, submission.fieldPrinted(), serverRecalc,
                    FieldBillStatus.DIVERGENT_QUEUED, 1, contingency, now);
            FieldBillRecord saved = store.save(queued);
            UUID queueItemId = divergenceQueue.enqueue(saved, divergenceJson(divergence));
            centralAlerts.divergenceDetected(saved.tenantId(), saved.taskId(), saved.billId(), queueItemId);
            return new Outcome(saved, false, queueItemId);
        }

        FieldBillRecord confirmed = new FieldBillRecord(submission.tenantId(), submission.taskId(),
                submission.routeId(), billId, submission.fieldPrinted(), serverRecalc,
                FieldBillStatus.CONFIRMED, 1, contingency, now);
        return new Outcome(store.save(confirmed), false, null);
    }

    /** Explicacao textual da divergencia para a fila (sem depender de JSON externo). */
    private static String divergenceJson(DivergenceEvaluator.Divergence divergence) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"basisDivergent\":").append(divergence.basisDivergent()).append(",\"differences\":[");
        boolean first = true;
        for (DivergenceEvaluator.FieldDifference diff : divergence.differences()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append("{\"field\":\"").append(diff.field())
                    .append("\",\"fieldValue\":\"").append(diff.fieldValue())
                    .append("\",\"serverValue\":\"").append(diff.serverValue()).append("\"}");
        }
        sb.append("]}");
        return sb.toString();
    }
}
