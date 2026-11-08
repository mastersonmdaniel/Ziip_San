package br.com.ziipsan.core.fieldos;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Sincronizacao da OS de campo no SERVIDOR (ED-20), sobre a sincronizacao
 * da ED-18 (que transporta as tarefas por {@code task_id}): ao receber a
 * execucao de campo, o servidor REVALIDA antes de aceitar qualquer efeito:
 * <ol>
 *   <li>OS de corte: revalida contra os fatos ATUAIS
 *       ({@link FieldOsPorts.CutFactsPort}). Falha -> a OS e CANCELADA pela
 *       porta de cancelamento SEM EFEITO NA LIGACAO e a execucao recebida
 *       e descartada (nenhum material baixa, nenhuma satisfacao registra);</li>
 *   <li>aceite do usuario conforme o tipo
 *       ({@code so.require_customer_acceptance_by_type}); tipo sem
 *       definicao no tenant recusa a sincronizacao ([A DEFINIR]);</li>
 *   <li>so entao: materiais usados sao entregues a baixa da ED-58 e a
 *       pesquisa de satisfacao ao atendimento da ED-35.</li>
 * </ol>
 */
public final class FieldOsSyncService {

    public enum Outcome { ACCEPTED, CANCELLED_NO_CONNECTION_EFFECT }

    public record SyncSubmission(UUID orderId, boolean cutOrder, CutPackage.Snapshot cutSnapshotOrNull,
                                 CustomerAcceptance.Evidence acceptanceOrNull,
                                 MaterialUse.TaskMaterialUse materialsOrNull,
                                 SatisfactionSurvey satisfactionOrNull) {
        public SyncSubmission {
            Objects.requireNonNull(orderId, "OS e obrigatoria");
            if (cutOrder) Objects.requireNonNull(cutSnapshotOrNull, "OS de corte sincronizada exige o pacote de corte");
        }
    }

    public record SyncResult(Outcome outcome, List<CutRevalidation.Reason> cancelReasons) {
        public SyncResult { cancelReasons = cancelReasons == null ? List.of() : List.copyOf(cancelReasons); }
        public boolean accepted() { return outcome == Outcome.ACCEPTED; }
    }

    private final FieldOsPorts.CutFactsPort cutFacts;
    private final FieldOsPorts.OrderCancellationPort cancellation;
    private final FieldOsPorts.StockWriteOffPort stock;
    private final FieldOsPorts.SacCasePort sac;
    private final FieldOsParameters parameters;

    public FieldOsSyncService(FieldOsPorts.CutFactsPort cutFacts, FieldOsPorts.OrderCancellationPort cancellation,
                              FieldOsPorts.StockWriteOffPort stock, FieldOsPorts.SacCasePort sac,
                              FieldOsParameters parameters) {
        this.cutFacts = Objects.requireNonNull(cutFacts, "fatos de corte sao obrigatorios");
        this.cancellation = Objects.requireNonNull(cancellation, "porta de cancelamento e obrigatoria");
        this.stock = Objects.requireNonNull(stock, "porta de baixa da ED-58 e obrigatoria");
        this.sac = Objects.requireNonNull(sac, "porta do atendimento da ED-35 e obrigatoria");
        this.parameters = Objects.requireNonNull(parameters, "parametros do tenant sao obrigatorios");
    }

    public SyncResult syncExecution(UUID tenantId, String orderTypeCode, SyncSubmission submission) {
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        Objects.requireNonNull(submission, "submissao e obrigatoria");

        if (submission.cutOrder()) {
            CutPackage.Facts current = cutFacts.currentFacts(tenantId, submission.orderId());
            CutRevalidation.Decision decision =
                    CutRevalidation.evaluate(submission.cutSnapshotOrNull().factsAtIssue(), current);
            if (decision.cancelled()) {
                cancellation.cancelWithoutConnectionEffect(tenantId, submission.orderId(),
                        "revalidacao de corte falhou na sincronizacao: " + decision.reasons());
                return new SyncResult(Outcome.CANCELLED_NO_CONNECTION_EFFECT, decision.reasons());
            }
        }

        CustomerAcceptance.Mode required = parameters.requiredAcceptance(tenantId, orderTypeCode)
                .orElseThrow(() -> new IllegalStateException("tipo de OS '" + orderTypeCode + "' sem definicao em "
                        + FieldOsParameters.REQUIRE_ACCEPTANCE_BY_TYPE + " do tenant [A DEFINIR]: sincronizacao recusada"));
        CustomerAcceptance.requireSatisfied(required, submission.acceptanceOrNull());

        if (submission.materialsOrNull() != null && submission.materialsOrNull().hasUse()) {
            stock.writeOff(tenantId, submission.materialsOrNull().taskId(), submission.materialsOrNull().lines());
        }
        if (submission.satisfactionOrNull() != null) {
            sac.registerSatisfaction(tenantId, submission.orderId(), submission.satisfactionOrNull());
        }
        return new SyncResult(Outcome.ACCEPTED, List.of());
    }
}
