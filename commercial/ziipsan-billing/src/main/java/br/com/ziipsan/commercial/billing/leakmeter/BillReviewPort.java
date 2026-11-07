package br.com.ziipsan.commercial.billing.leakmeter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Porta para a revisao de conta da P2.20: a revisao por vazamento PARTE da
 * OS {@code BILL_REVIEW} (aceite da P3.28) -- antes de existir pedido de
 * vazamento, a conta entra em revisao pelo fluxo da P2.20, com os efeitos
 * suspensos de {@code bill.review_effects} e o teto de
 * {@code bill.review_max_days}.
 *
 * <p><b>O estado pendente da P2.20 e parte do contrato (correcao
 * pos-revisao, achado A1):</b> a fabrica dos tipos {@code BILL_*} da P2.20
 * exige aprovacao ({@code FACTORY_APPROVAL_FOR_BILLING_CHANGES = true}).
 * Com aprovacao exigida e {@code approverId == null},
 * {@code BillMaintenanceService.openReview} devolve
 * {@code MaintenanceResult(applied=false, status="AWAITING_APPROVAL")} e a
 * linha em {@code bill_reviews} <b>NAO e criada</b> -- ela so nasce dentro
 * do {@code execute} da OS, em {@code applyReviewOpening}. Como
 * {@code leak_reviews.bill_review_id} e {@code NOT NULL} com FK para
 * {@code bill_reviews}, o desenho nao representa pedido de vazamento sem a
 * linha da P2.20: por isso o resultado desta porta e selado --
 * {@link Opened} (a linha existe) ou {@link AwaitingApproval} (so a OS
 * existe) -- e o servico de vazamento so cria a OS filha e grava o pedido
 * sobre {@link Opened}.
 *
 * <p><b>Fronteira declarada (sem adaptador nesta entrega):</b> o adaptador
 * vive na raiz de composicao, onde o {@code BillMaintenanceService} da
 * P2.20 ja esta montado com os 16 colaboradores dele, e delega para
 * {@code BillMaintenanceService.openReview(tenantId, billId, reviewReasonId,
 * protocolId, note, actorId, approverId, idempotencyKey, parameters, today)}
 * com os {@code MaintenanceParameters} vigentes do tenant. A receita do
 * adaptador, obrigatoria:
 * <ul>
 *   <li>se {@code result.applied()} for falso (caminho padrao sem
 *       aprovador), devolver {@link AwaitingApproval} com
 *       {@code result.serviceOrderId()} -- NUNCA procurar linha em
 *       {@code bill_reviews} nesse estado, porque ela nao existe;</li>
 *   <li>a RETOMADA e a mesma chamada com a MESMA {@code Idempotency-Key} e
 *       um {@code approverId} (diferente do autor): a P2.20 aprova e
 *       executa a OS ja criada, a linha nasce, e so entao o adaptador
 *       devolve {@link Opened}, resolvendo o {@code billReviewId} via
 *       {@code BillReviewRepository.findByTenantIdAndBillIdAndStatus(tenantId,
 *       billId, "OPEN")} sobre o resultado APLICADO (o
 *       {@code MaintenanceResult} da P2.20 nao carrega o id da linha de
 *       revisao -- carrega a OS e a conta).</li>
 * </ul>
 * Mesmo desenho das portas da P2.20 cujos adaptadores vivem nos modulos
 * donos ({@code ConsumptionLinkGateway}, {@code AnalysisQueueSource}).
 */
public interface BillReviewPort {

    /** Resultado da abertura da revisao de conta na P2.20: aplicada ou pendente de aprovacao. */
    sealed interface OpenBillReviewResult permits Opened, AwaitingApproval {
    }

    /** Revisao aplicada: a linha em bill_reviews existe, e a OS BILL_REVIEW dela. */
    record Opened(UUID billReviewId, UUID billReviewOrderId) implements OpenBillReviewResult {
    }

    /**
     * Revisao aguardando aprovacao na P2.20: existe apenas a OS
     * {@code BILL_REVIEW} ({@code billReviewOrderId}); NAO ha linha em
     * {@code bill_reviews} ainda. A retomada e repetir a abertura com a
     * mesma {@code Idempotency-Key} e um aprovador.
     */
    record AwaitingApproval(UUID billReviewOrderId) implements OpenBillReviewResult {
    }

    /**
     * Abre (ou retoma) a revisao de conta da P2.20 para esta conta (motivo
     * de catalogo e anotacao obrigatorios, como la). Conta que ja tem
     * revisao aberta e recusada la -- e portanto aqui, sem traducao.
     */
    OpenBillReviewResult openBillReview(UUID tenantId, UUID billId, UUID reviewReasonId, UUID protocolId,
                                        String note, UUID actorId, UUID approverId,
                                        String idempotencyKey, LocalDate today);
}
