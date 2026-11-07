package br.com.ziipsan.commercial.collection.refund;

import br.com.ziipsan.commercial.collection.parameters.RefundParameters.DoubleRefundSetting;
import br.com.ziipsan.commercial.collection.parameters.RefundParameters.RefundDestination;
import br.com.ziipsan.commercial.collection.parameters.RefundParameters.RefundMode;
import br.com.ziipsan.commercial.collection.refund.RefundDestinationValidator.BankAccountHolderPort;
import br.com.ziipsan.commercial.collection.refund.RefundDestinationValidator.PixOrigin;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Encerramento da OS {@code REFUND_REQUEST} (P3.29): aplica UMA vez o efeito
 * decidido - credito na conta seguinte ou devolucao bancaria, conforme
 * {@code pay.refund_mode} (ver {@link RefundRequestRule#effectiveMode}).
 *
 * <p><b>Fiacao do encerramento (correcao pos-revisao M2):</b> a versao
 * original encerrava com um unico booleano {@code approved} e sem destino -
 * aprovacao e destino eram afirmados, nunca conferidos. Agora o servico:
 * <ol>
 *   <li>confere o status: so encerra devolucao {@code APROVADA} (pendente
 *       de encerramento). PENDENTE (sem origem/divergente), RECUSADA ou
 *       ENCERRADA sao recusadas com erro explicito;</li>
 *   <li>aplica {@link RefundApprovalRule}: valor x alcada x aprovacoes
 *       registradas, reutilizando as guardas da P3.1 (solicitante nunca
 *       aprova; acima da alcada, dois aprovadores distintos);</li>
 *   <li>aplica a guarda do afastamento do dobro
 *       ({@link RefundAmountCalculator#requireWaiverWhenDoubleOmitted},
 *       correcao M1): dobro devido e nao aplicado sem justificativa =
 *       recusa;</li>
 *   <li>na devolucao bancaria, exige destino parametrizado e CIFRADO gravado
 *       ({@link RefundRequestRule#requireDestinationForBankRefund}) e o
 *       VALIDA ({@link RefundDestinationValidator}); a porta bancaria recebe
 *       o destino CIFRADO - o texto claro so e revelado dentro do adaptador,
 *       via {@link RefundDestinationProtector}, no momento do envio.</li>
 * </ol>
 *
 * <p><b>Idempotencia do reenvio:</b> o encerramento consulta primeiro a
 * execucao ja gravada para esta OS ({@link RefundExecutionStore#findByServiceOrderId});
 * se ela existe, devolve {@link Outcome#ALREADY_APPLIED} com a execucao
 * original e NAO grava credito nem devolucao de novo. No banco, a garantia
 * e dupla: {@code UNIQUE(tenant, refund_request_id)} e
 * {@code UNIQUE(tenant, service_order_id)} em {@code refund_executions}
 * (V6_5). Credito na conta seguinte usa a conta informada pela fiacao
 * (a proxima conta da unidade, do billing da P2.5); sem conta seguinte
 * identificada, o encerramento de credito e erro explicito - nao se credita
 * "em alguma conta".
 */
public final class RefundClosureService {

    /** Status que autoriza o encerramento (espelha {@code refund_requests.status}). */
    public static final String STATUS_APROVADA = "APROVADA";

    /** Porta de persistencia da execucao (adaptador JPA na fiacao). */
    public interface RefundExecutionStore {
        Optional<Execution> findByServiceOrderId(UUID tenantId, UUID serviceOrderId);

        Execution save(Execution execution);
    }

    /**
     * Porta da devolucao bancaria no PSP/banco (devolve a referencia do envio).
     * Recebe o destino CIFRADO gravado ({@code destinationCiphertext}):
     * o adaptador o revela com {@link RefundDestinationProtector} dentro da
     * chamada ao PSP; o servico nunca manipula o destino em claro.
     */
    public interface BankRefundPort {
        String executeBankRefund(UUID tenantId, UUID refundRequestId, BigDecimal amount,
                                 String destinationCiphertext);
    }

    /** Execucao gravada do efeito (espelha {@code refund_executions}). */
    public record Execution(UUID id, UUID tenantId, UUID refundRequestId, UUID serviceOrderId,
                            String effect, BigDecimal amount, UUID creditedBillId, String bankReference) {
    }

    public enum Outcome { APPLIED, ALREADY_APPLIED }

    public record ClosureResult(Outcome outcome, Execution execution) {
    }

    /**
     * Aprovacoes registradas da devolucao, conferidas contra a alcada.
     *
     * @param requestedBy             quem solicitou (nunca aprova a propria devolucao)
     * @param firstApproverId         primeiro aprovador registrado (nulo = nenhuma aprovacao)
     * @param secondApproverId        segundo aprovador (exigido acima da alcada)
     * @param doubleApprovalThreshold alcada vigente do tenant (entrada obrigatoria
     *                                da fiacao, como em {@link RefundApprovalRule})
     */
    public record ApprovalData(UUID requestedBy, UUID firstApproverId, UUID secondApproverId,
                               BigDecimal doubleApprovalThreshold) {
    }

    /**
     * Dados do dobro no encerramento: o valor que vai ser executado nasceu
     * com o dobro aplicado? Se a cobranca e indevida, o dobro esta ligado e
     * {@code doubleApplied} e falso, a justificativa e obrigatoria (M1).
     */
    public record DoubleRefundData(RefundType refundType, DoubleRefundSetting doubleSetting,
                                   boolean doubleApplied, String doubleWaiverJustification) {
    }

    /**
     * Destino da devolucao bancaria no encerramento.
     *
     * @param destination           destino parametrizado ({@code pay.refund_destination})
     * @param destinationCiphertext destino CIFRADO gravado (FieldCipher); obrigatorio
     *                              na devolucao bancaria
     * @param pixOrigin             origem Pix (so para {@code PIX_RETURN_TO_PAYER})
     * @param pixKey                chave Pix informada (so para {@code PIX_KEY};
     *                              validada aqui e cifrada pela fiacao - nao persiste em claro)
     * @param holderDocument        documento do titular da devolucao
     * @param bankCode              banco (so para {@code BANK_ACCOUNT})
     * @param branch                agencia (so para {@code BANK_ACCOUNT})
     * @param account               conta (so para {@code BANK_ACCOUNT})
     */
    public record DestinationData(RefundDestination destination, String destinationCiphertext,
                                  PixOrigin pixOrigin, String pixKey, String holderDocument,
                                  String bankCode, String branch, String account) {
    }

    /**
     * @param refundStatus      status atual da devolucao; so {@code APROVADA} encerra
     * @param nextBillId        conta seguinte a creditar (obrigatoria no credito)
     * @param destinationData   destino da devolucao bancaria (obrigatorio nela;
     *                          ignorado no credito na conta seguinte)
     */
    public record ClosureInput(UUID tenantId, UUID refundRequestId, UUID serviceOrderId,
                               String refundStatus, RefundMode configuredMode, RefundMode userChoice,
                               BigDecimal refundAmount, UUID nextBillId,
                               ApprovalData approval, DoubleRefundData doubleData,
                               DestinationData destinationData) {
    }

    private final RefundExecutionStore store;
    private final BankRefundPort bankPort;
    private final BankAccountHolderPort bankAccountHolderPort;

    public RefundClosureService(RefundExecutionStore store, BankRefundPort bankPort,
                                BankAccountHolderPort bankAccountHolderPort) {
        this.store = Objects.requireNonNull(store, "store de execucoes e obrigatorio");
        this.bankPort = Objects.requireNonNull(bankPort, "porta bancaria e obrigatoria");
        this.bankAccountHolderPort = Objects.requireNonNull(bankAccountHolderPort,
                "porta de titularidade bancaria e obrigatoria");
    }

    public ClosureResult close(ClosureInput in) {
        Objects.requireNonNull(in, "entrada do encerramento e obrigatoria");
        Objects.requireNonNull(in.tenantId(), "tenant e obrigatorio");
        Objects.requireNonNull(in.refundRequestId(), "devolucao e obrigatoria");
        Objects.requireNonNull(in.serviceOrderId(), "OS REFUND_REQUEST e obrigatoria");
        // Reenvio da OS: o efeito ja existe -> nao duplica.
        Optional<Execution> existing = store.findByServiceOrderId(in.tenantId(), in.serviceOrderId());
        if (existing.isPresent()) {
            if (!existing.get().refundRequestId().equals(in.refundRequestId())) {
                throw new IllegalStateException("OS ja encerrou outra devolucao: reenvio divergente");
            }
            return new ClosureResult(Outcome.ALREADY_APPLIED, existing.get());
        }
        // (1) Status pendente de encerramento: somente APROVADA.
        if (!STATUS_APROVADA.equals(in.refundStatus())) {
            throw new IllegalStateException("devolucao nao esta pendente de encerramento "
                    + "(status=" + in.refundStatus() + "); somente APROVADA encerra");
        }
        if (in.refundAmount() == null || in.refundAmount().signum() <= 0) {
            throw new IllegalArgumentException("valor da devolucao deve ser positivo: " + in.refundAmount());
        }
        // (2) Aprovacao de verdade: alcada x aprovacoes registradas.
        requireApproved(in);
        // (3) Guarda do afastamento do dobro (M1), tambem no encerramento.
        Objects.requireNonNull(in.doubleData(), "dados do dobro sao obrigatorios no encerramento");
        RefundAmountCalculator.requireWaiverWhenDoubleOmitted(in.doubleData().refundType(),
                in.doubleData().doubleSetting(), in.doubleData().doubleApplied(),
                in.doubleData().doubleWaiverJustification());
        RefundMode effective = RefundRequestRule.effectiveMode(in.configuredMode(), in.userChoice());
        Execution execution;
        if (effective == RefundMode.CREDIT_NEXT_BILL) {
            if (in.nextBillId() == null) {
                throw new IllegalArgumentException("credito exige a conta seguinte identificada");
            }
            execution = new Execution(null, in.tenantId(), in.refundRequestId(), in.serviceOrderId(),
                    "NEXT_BILL_CREDIT", in.refundAmount(), in.nextBillId(), null);
        } else {
            // (4) Devolucao bancaria: destino cifrado gravado E validado, e a
            // porta recebe o texto cifrado.
            DestinationData dest = in.destinationData();
            if (dest == null) {
                throw new IllegalArgumentException("devolucao bancaria exige os dados do destino");
            }
            RefundRequestRule.requireDestinationForBankRefund(effective, dest.destination(),
                    dest.destinationCiphertext());
            RefundDestinationValidator.validate(dest.destination(), dest.pixOrigin(), dest.pixKey(),
                    dest.holderDocument(), bankAccountHolderPort, dest.bankCode(), dest.branch(),
                    dest.account());
            String reference = bankPort.executeBankRefund(in.tenantId(), in.refundRequestId(),
                    in.refundAmount(), dest.destinationCiphertext());
            if (reference == null || reference.isBlank()) {
                throw new IllegalStateException("PSP/banco nao devolveu referencia da devolucao");
            }
            execution = new Execution(null, in.tenantId(), in.refundRequestId(), in.serviceOrderId(),
                    "BANK_REFUND", in.refundAmount(), null, reference);
        }
        return new ClosureResult(Outcome.APPLIED, store.save(execution));
    }

    /**
     * Confere as aprovacoes registradas contra a alcada, reutilizando
     * {@link RefundApprovalRule}/{@code DoubleApprovalRule} da P3.1:
     * solicitante nunca aprova; acima da alcada, dois aprovadores distintos.
     */
    private static void requireApproved(ClosureInput in) {
        ApprovalData approval = Objects.requireNonNull(in.approval(),
                "aprovacoes da devolucao sao obrigatorias no encerramento");
        Objects.requireNonNull(approval.requestedBy(), "solicitante da devolucao e obrigatorio");
        boolean doubleRequired = RefundApprovalRule.requiresDoubleApproval(
                in.refundAmount(), approval.doubleApprovalThreshold());
        if (approval.firstApproverId() == null) {
            throw new IllegalStateException("devolucao sem nenhuma aprovacao registrada "
                    + "nao pode ser encerrada");
        }
        if (!doubleRequired) {
            var progress = RefundApprovalRule.approve(false, approval.requestedBy(), null, null,
                    approval.firstApproverId());
            if (!progress.isApproved()) {
                throw new IllegalStateException("aprovacao simples nao concluida");
            }
            return;
        }
        if (approval.secondApproverId() == null) {
            throw new IllegalStateException("valor acima da alcada exige aprovacao dupla: "
                    + "segundo aprovador ausente");
        }
        var progress = RefundApprovalRule.approve(true, approval.requestedBy(), null, null,
                approval.firstApproverId());
        progress = RefundApprovalRule.approve(true, approval.requestedBy(),
                progress.firstApproverId(), null, approval.secondApproverId());
        if (!progress.isApproved()) {
            throw new IllegalStateException("aprovacao dupla nao concluida");
        }
    }
}
