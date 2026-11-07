package br.com.ziipsan.commercial.collection.pixauto;

import br.com.ziipsan.commercial.collection.pix.PixAutoDebitRules;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Execucao do Pix Automatico (P4.5) sobre as regras registradas na P3.2
 * (GES-15, {@link PixAutoDebitRules}). Cada regra do aceite tem um metodo e um
 * teste nomeado:
 *
 * <ol>
 *   <li><b>Autorizacao</b> (jornadas 1 a 4, inclusive QR Code com primeiro
 *       pagamento imediato): {@link #authorize}; a autorizacao nasce com prova
 *       (ver {@link PixAutoDebitAuthorization}) e autorizacao nova de debito em
 *       conta so por Pix Automatico ({@link AccountDebitAuthorizationGate}).</li>
 *   <li><b>Agendamento</b> 2 a 10 dias antes da liquidacao:
 *       {@link #scheduleInstruction}.</li>
 *   <li><b>Notificacao ao pagador</b>: instrucao so e enviada depois de
 *       notificada, com antecedencia de {@code pay.auto_debit.notice_days}:
 *       {@link #notifyPayer} e {@link #submit}.</li>
 *   <li><b>Retentativas</b>: no maximo 3 em 7 dias e somente se previstas na
 *       autorizacao (e na politica do tenant): {@link #registerFailure}.</li>
 *   <li><b>Cancelamento</b>: pagador ate 23h59 da vespera, recebedor ate 22h do
 *       dia da liquidacao ({@link PixAutoDebitCancellation}):
 *       {@link #cancelInstruction} e {@link #cancelAuthorization}.</li>
 *   <li><b>Multa e juros no ciclo seguinte</b>:
 *       {@link #buildNextCycleInstruction} sobre {@link NextCycleChargeCalculator}.</li>
 *   <li><b>Conciliacao por identificador de cobranca</b>: {@link #reconcile}.</li>
 *   <li><b>Devolucao de debito indevido</b> em 24h pelo banco do pagador:
 *       {@link #requestUndueDebitRefund} e {@link UndueDebitRefund}.</li>
 * </ol>
 *
 * <p>Datas/horas de negocio sao hora local do tenant, injetadas por quem chama
 * (o produto grava instantes em UTC e usa a data local do tenant, P0.19).
 * Dinheiro em {@link BigDecimal}. A integracao com o PSP e so pela porta
 * {@link PspPixAutoDebitPort}; a persistencia, pela porta {@link Store}.
 */
public final class PixAutoDebitService {

    public enum FailureOutcome { RETENTATIVA_AGENDADA, FALHA_DEFINITIVA }

    public enum ReconciliationOutcome { CONCILIADO, REPETIDO_SEM_EFEITO, DIVERGENTE, NAO_IDENTIFICADO }

    public record ReconciliationResult(ReconciliationOutcome outcome, UUID instructionId) {
    }

    /** Porta de persistencia (adaptador JPA na fiacao, sobre as entidades desta entrega). */
    public interface Store {
        void saveAuthorization(PixAutoDebitAuthorization authorization);
        Optional<PixAutoDebitAuthorization> findAuthorization(UUID tenantId, UUID authorizationId);
        void saveInstruction(PixAutoDebitInstruction instruction);
        Optional<PixAutoDebitInstruction> findInstruction(UUID tenantId, UUID instructionId);
        Optional<PixAutoDebitInstruction> findInstructionByChargeIdentifier(UUID tenantId, String chargeIdentifier);
        List<PixAutoDebitInstruction> findInstructionsByAuthorization(UUID tenantId, UUID authorizationId);
        void saveRefund(UndueDebitRefund refund);
        Optional<UndueDebitRefund> findRefund(UUID tenantId, UUID refundId);
    }

    private final Store store;
    private final PspPixAutoDebitPort pspPort;
    private final PixAutoDebitRules.RetryPolicy retryPolicy;
    private final int noticeDays;

    public PixAutoDebitService(Store store, PspPixAutoDebitPort pspPort,
                               PixAutoDebitRules.RetryPolicy retryPolicy, int noticeDays) {
        this.store = Objects.requireNonNull(store, "Store e obrigatoria");
        this.pspPort = Objects.requireNonNull(pspPort, "PspPixAutoDebitPort e obrigatoria");
        this.retryPolicy = Objects.requireNonNull(retryPolicy,
                "politica de retentativa (pay.auto_debit.retry_policy) e obrigatoria");
        if (noticeDays < 0) {
            throw new IllegalArgumentException("pay.auto_debit.notice_days nao pode ser negativo");
        }
        this.noticeDays = noticeDays;
    }

    // ------------------------------------------------------------------
    // Autorizacao
    // ------------------------------------------------------------------

    /**
     * Pedido de autorizacao nova. {@code channel} passa pela trava: so
     * {@code PIX_AUTOMATICO} cria autorizacao nova.
     *
     * @param firstPaymentImmediate jornada por QR Code com primeiro pagamento
     *                              imediato: exige {@code qrCodeAuthorization} e
     *                              a instrucao imediata ja preenchida em
     *                              {@code firstInstruction}
     */
    public record AuthorizationRequest(AccountDebitAuthorizationGate.DebitChannel channel,
                                       UUID tenantId, String receiverDocument, UUID unitId,
                                       String payerDocument, int journey, boolean qrCodeAuthorization,
                                       String proofHash, String proofReference, Instant authorizedAt,
                                       boolean retryForeseen, int maxRetries, BigDecimal maxAmount,
                                       boolean firstPaymentImmediate,
                                       InstructionDraft firstInstruction) {
    }

    /** Rascunho de instrucao de um ciclo (o servico fixa id e vinculo). */
    public record InstructionDraft(String chargeIdentifier, String documentType, UUID documentId,
                                   LocalDate collectionMonth, BigDecimal principal, BigDecimal fine,
                                   BigDecimal interest, LocalDate settlementDate) {
    }

    public PixAutoDebitAuthorization authorize(AuthorizationRequest req) {
        Objects.requireNonNull(req, "pedido de autorizacao e obrigatorio");
        AccountDebitAuthorizationGate.requirePixAutomaticoForNewAuthorization(req.channel());
        if (req.firstPaymentImmediate() && !req.qrCodeAuthorization()) {
            throw new IllegalArgumentException(
                    "primeiro pagamento imediato so existe na jornada de autorizacao por QR Code");
        }
        if (req.firstPaymentImmediate() && req.firstInstruction() == null) {
            throw new IllegalArgumentException(
                    "jornada com primeiro pagamento imediato exige a instrucao do primeiro pagamento");
        }
        // Valida TUDO (jornada, documentos e prova) construindo a autorizacao
        // antes de qualquer efeito no PSP: pedido invalido nao registra nada la.
        UUID authorizationId = UUID.randomUUID();
        new PixAutoDebitAuthorization(authorizationId, req.tenantId(), pspPort.psp(), null,
                req.payerDocument(), req.receiverDocument(), req.unitId(), req.journey(),
                req.qrCodeAuthorization(), req.proofHash(), req.proofReference(), req.authorizedAt(),
                req.retryForeseen(), req.maxRetries(), req.maxAmount());
        String pspAuthorizationId = pspPort.registerAuthorization(req.payerDocument(), req.receiverDocument(),
                req.journey(), req.retryForeseen(), req.maxAmount());
        PixAutoDebitAuthorization authorization = new PixAutoDebitAuthorization(authorizationId, req.tenantId(),
                pspPort.psp(), pspAuthorizationId, req.payerDocument(), req.receiverDocument(), req.unitId(),
                req.journey(), req.qrCodeAuthorization(), req.proofHash(), req.proofReference(),
                req.authorizedAt(), req.retryForeseen(), req.maxRetries(), req.maxAmount());
        store.saveAuthorization(authorization);
        if (req.firstPaymentImmediate()) {
            // Primeiro pagamento imediato: nao passa pela antecedencia de 2 a
            // 10 dias (ele liquida na propria jornada), mas passa por
            // notificacao (a jornada e a notificacao), teto e envio ao PSP.
            PixAutoDebitInstruction first = buildInstruction(authorization, req.firstInstruction());
            first.markNotified(req.authorizedAt());
            store.saveInstruction(first);
            submitLoaded(authorization, first, req.firstInstruction().settlementDate());
        }
        return authorization;
    }

    // ------------------------------------------------------------------
    // Agendamento, notificacao e envio
    // ------------------------------------------------------------------

    /**
     * Agenda a instrucao de um ciclo: a data de agendamento deve estar de 2 a
     * 10 dias antes da liquidacao (regra registrada na P3.2).
     *
     * @param scheduledOn data local em que o agendamento esta sendo feito
     */
    public PixAutoDebitInstruction scheduleInstruction(UUID tenantId, UUID authorizationId,
                                                       InstructionDraft draft, LocalDate scheduledOn) {
        Objects.requireNonNull(draft, "rascunho da instrucao e obrigatorio");
        Objects.requireNonNull(scheduledOn, "data do agendamento e obrigatoria");
        PixAutoDebitAuthorization authorization = requireActiveAuthorization(tenantId, authorizationId);
        long leadDays = ChronoUnit.DAYS.between(scheduledOn, draft.settlementDate());
        if (leadDays < Integer.MIN_VALUE || leadDays > Integer.MAX_VALUE
                || !PixAutoDebitRules.isValidScheduleLeadDays((int) leadDays)) {
            throw new IllegalArgumentException("agendamento deve ser de "
                    + PixAutoDebitRules.MIN_SCHEDULE_DAYS_BEFORE + " a "
                    + PixAutoDebitRules.MAX_SCHEDULE_DAYS_BEFORE
                    + " dias antes da liquidacao; veio " + leadDays);
        }
        if (store.findInstructionByChargeIdentifier(tenantId, draft.chargeIdentifier()).isPresent()) {
            throw new IllegalArgumentException(
                    "identificador de cobranca ja existe para este tenant: " + draft.chargeIdentifier());
        }
        PixAutoDebitInstruction instruction = buildInstruction(authorization, draft);
        store.saveInstruction(instruction);
        return instruction;
    }

    /** Data em que a notificacao ao pagador deve ter ocorrido: liquidacao menos notice_days. */
    public LocalDate notificationDueDate(PixAutoDebitInstruction instruction) {
        return instruction.settlementDate().minusDays(noticeDays);
    }

    /** Registra a notificacao ao pagador (pre-condicao do envio). */
    public void notifyPayer(UUID tenantId, UUID instructionId, Instant notifiedAt) {
        PixAutoDebitInstruction instruction = requireInstruction(tenantId, instructionId);
        instruction.markNotified(Objects.requireNonNull(notifiedAt, "instante da notificacao e obrigatorio"));
        store.saveInstruction(instruction);
    }

    /**
     * Envia a instrucao ao PSP. Sem notificacao registrada ao pagador, NAO
     * envia. Instrucao cancelada ou de autorizacao nao ativa tambem nao envia.
     *
     * @param attemptDate data local desta tentativa (a primeira fixa a janela de retentativa)
     */
    public PixAutoDebitInstruction submit(UUID tenantId, UUID instructionId, LocalDate attemptDate) {
        PixAutoDebitInstruction instruction = requireInstruction(tenantId, instructionId);
        PixAutoDebitAuthorization authorization = requireActiveAuthorization(tenantId, instruction.authorizationId());
        submitLoaded(authorization, instruction, attemptDate);
        return instruction;
    }

    private void submitLoaded(PixAutoDebitAuthorization authorization, PixAutoDebitInstruction instruction,
                              LocalDate attemptDate) {
        if (instruction.status() == PixAutoDebitInstruction.Status.AGENDADA) {
            throw new IllegalStateException("instrucao nao pode ser enviada antes da notificacao ao pagador");
        }
        if (instruction.status() != PixAutoDebitInstruction.Status.NOTIFICADA
                && instruction.status() != PixAutoDebitInstruction.Status.FALHA_AGUARDANDO_RETENTATIVA) {
            throw new IllegalStateException("instrucao " + instruction.status() + " nao pode ser enviada");
        }
        String e2eid = pspPort.submitDebit(instruction.chargeIdentifier(), instruction.total(),
                instruction.settlementDate(), authorization.payerDocument());
        instruction.markSubmitted(e2eid, attemptDate);
        store.saveInstruction(instruction);
    }

    // ------------------------------------------------------------------
    // Falha e retentativa
    // ------------------------------------------------------------------

    /**
     * Registra a falha de um debito enviado. Retentativa so existe se prevista
     * NA AUTORIZACAO (e na politica do tenant), no teto efetivo e dentro da
     * janela da politica do tenant ({@code pay.auto_debit.retry_policy},
     * validada na P3.2 contra o teto normativo de
     * {@link PixAutoDebitRules#RETRY_WINDOW_DAYS} dias) contada da primeira
     * tentativa; fora disso, a falha e definitiva e o valor vai para a
     * instrucao do ciclo seguinte com multa e juros.
     *
     * @param retryDate data local proposta para a retentativa (obrigatoria se a falha nao for definitiva)
     */
    public FailureOutcome registerFailure(UUID tenantId, UUID instructionId, LocalDate retryDate) {
        PixAutoDebitInstruction instruction = requireInstruction(tenantId, instructionId);
        if (instruction.status() != PixAutoDebitInstruction.Status.ENVIADA) {
            throw new IllegalStateException("so instrucao ENVIADA pode registrar falha: " + instruction.status());
        }
        PixAutoDebitAuthorization authorization = requireAuthorization(tenantId, instruction.authorizationId());
        int effectiveMax = authorization.effectiveMaxRetries(retryPolicy);
        boolean canRetry = effectiveMax > 0
                && instruction.retriesUsed() < effectiveMax
                && retryDate != null
                && !retryDate.isBefore(instruction.firstAttemptDate())
                && ChronoUnit.DAYS.between(instruction.firstAttemptDate(), retryDate)
                        <= retryPolicy.windowDays();
        instruction.markFailed(canRetry);
        store.saveInstruction(instruction);
        return canRetry ? FailureOutcome.RETENTATIVA_AGENDADA : FailureOutcome.FALHA_DEFINITIVA;
    }

    // ------------------------------------------------------------------
    // Cancelamento
    // ------------------------------------------------------------------

    /**
     * Cancela uma instrucao agendada/notificada, no prazo do ator
     * ({@link PixAutoDebitCancellation}) e cancela no PSP.
     */
    public void cancelInstruction(UUID tenantId, UUID instructionId, PixAutoDebitCancellation.Actor actor,
                                  LocalDateTime at) {
        PixAutoDebitInstruction instruction = requireInstruction(tenantId, instructionId);
        if (instruction.status() == PixAutoDebitInstruction.Status.LIQUIDADA
                || instruction.status() == PixAutoDebitInstruction.Status.ENVIADA
                || instruction.status() == PixAutoDebitInstruction.Status.FALHA_DEFINITIVA
                || instruction.status() == PixAutoDebitInstruction.Status.CANCELADA_PELO_PAGADOR
                || instruction.status() == PixAutoDebitInstruction.Status.CANCELADA_PELO_RECEBEDOR) {
            throw new IllegalStateException("instrucao " + instruction.status() + " nao pode ser cancelada");
        }
        if (!PixAutoDebitCancellation.canCancel(actor, instruction.settlementDate(), at)) {
            throw new IllegalStateException("cancelamento pelo " + actor + " fora do prazo para liquidacao em "
                    + instruction.settlementDate());
        }
        instruction.markCancelled(actor);
        store.saveInstruction(instruction);
        pspPort.cancelDebit(instruction.chargeIdentifier());
    }

    /**
     * Cancela a AUTORIZACAO (pagador ou recebedor) e, com ela, todas as
     * instrucoes ainda nao enviadas dela. Instrucao ja enviada nao e tocada:
     * o debito em curso segue para liquidacao ou falha.
     */
    public PixAutoDebitAuthorization cancelAuthorization(UUID tenantId, UUID authorizationId,
                                                         PixAutoDebitCancellation.Actor actor, Instant at) {
        PixAutoDebitAuthorization authorization = requireAuthorization(tenantId, authorizationId);
        authorization.cancel(actor, at);
        store.saveAuthorization(authorization);
        for (PixAutoDebitInstruction instruction
                : store.findInstructionsByAuthorization(tenantId, authorizationId)) {
            if (instruction.status() == PixAutoDebitInstruction.Status.AGENDADA
                    || instruction.status() == PixAutoDebitInstruction.Status.NOTIFICADA
                    || instruction.status() == PixAutoDebitInstruction.Status.FALHA_AGUARDANDO_RETENTATIVA) {
                instruction.markCancelled(actor);
                store.saveInstruction(instruction);
                pspPort.cancelDebit(instruction.chargeIdentifier());
            }
        }
        return authorization;
    }

    // ------------------------------------------------------------------
    // Ciclo seguinte com multa e juros
    // ------------------------------------------------------------------

    /**
     * Monta a instrucao do ciclo seguinte de uma instrucao com FALHA_DEFINITIVA:
     * principal do novo ciclo + multa e juros sobre o principal nao pago
     * (dias de atraso contados da liquidacao original ate a nova liquidacao).
     * Aliquotas sao entradas explicitas (parametros do tenant), nunca presumidas.
     */
    public PixAutoDebitInstruction buildNextCycleInstruction(UUID tenantId, UUID failedInstructionId,
                                                             InstructionDraft nextDraft, BigDecimal finePct,
                                                             BigDecimal dailyInterestPct, LocalDate scheduledOn) {
        PixAutoDebitInstruction failed = requireInstruction(tenantId, failedInstructionId);
        if (failed.status() != PixAutoDebitInstruction.Status.FALHA_DEFINITIVA) {
            throw new IllegalStateException(
                    "multa e juros so entram no ciclo seguinte de instrucao com FALHA_DEFINITIVA: "
                            + failed.status());
        }
        int daysLate = Math.toIntExact(ChronoUnit.DAYS.between(failed.settlementDate(), nextDraft.settlementDate()));
        NextCycleChargeCalculator.NextCycleCharge charge = NextCycleChargeCalculator.compute(
                failed.principal(), nextDraft.principal(), finePct, dailyInterestPct, daysLate);
        InstructionDraft withCharges = new InstructionDraft(nextDraft.chargeIdentifier(), nextDraft.documentType(),
                nextDraft.documentId(), nextDraft.collectionMonth(), nextDraft.principal(),
                charge.fine(), charge.interest(), nextDraft.settlementDate());
        return scheduleInstruction(tenantId, failed.authorizationId(), withCharges, scheduledOn);
    }

    // ------------------------------------------------------------------
    // Conciliacao
    // ------------------------------------------------------------------

    /**
     * Concilia o retorno do PSP PELO IDENTIFICADOR DE COBRANCA: casa a
     * instrucao local, confere o valor total e baixa uma unica vez; repetido
     * com o mesmo endToEndId e sem efeito; valor divergente nao baixa.
     */
    public ReconciliationResult reconcile(UUID tenantId, String chargeIdentifier, String endToEndId,
                                          BigDecimal amount, Instant settledAt) {
        Optional<PixAutoDebitInstruction> found =
                store.findInstructionByChargeIdentifier(tenantId, chargeIdentifier);
        if (found.isEmpty()) {
            return new ReconciliationResult(ReconciliationOutcome.NAO_IDENTIFICADO, null);
        }
        PixAutoDebitInstruction instruction = found.get();
        if (instruction.status() == PixAutoDebitInstruction.Status.LIQUIDADA) {
            if (Objects.equals(instruction.endToEndId(), endToEndId)) {
                return new ReconciliationResult(ReconciliationOutcome.REPETIDO_SEM_EFEITO, instruction.id());
            }
            return new ReconciliationResult(ReconciliationOutcome.DIVERGENTE, instruction.id());
        }
        if (instruction.status() != PixAutoDebitInstruction.Status.ENVIADA) {
            // Instrucao nunca enviada, cancelada ou ja em falha (aguardando
            // retentativa ou definitiva) nao baixa por retorno de liquidacao:
            // o PSP so liquida o debito da tentativa ENVIADA corrente.
            return new ReconciliationResult(ReconciliationOutcome.DIVERGENTE, instruction.id());
        }
        if (amount == null || amount.compareTo(instruction.total()) != 0) {
            return new ReconciliationResult(ReconciliationOutcome.DIVERGENTE, instruction.id());
        }
        instruction.markSettled(endToEndId, settledAt);
        store.saveInstruction(instruction);
        return new ReconciliationResult(ReconciliationOutcome.CONCILIADO, instruction.id());
    }

    // ------------------------------------------------------------------
    // Devolucao de debito indevido
    // ------------------------------------------------------------------

    /**
     * Protocola a devolucao de um debito indevido (instrucao LIQUIDADA): o
     * pedido vai ao PSP e a devolucao e do banco do pagador, em ate 24 horas
     * contadas do pedido ({@link UndueDebitRefund#deadline()}).
     */
    public UndueDebitRefund requestUndueDebitRefund(UUID tenantId, UUID instructionId, String reason,
                                                      Instant requestedAt) {
        PixAutoDebitInstruction instruction = requireInstruction(tenantId, instructionId);
        if (instruction.status() != PixAutoDebitInstruction.Status.LIQUIDADA) {
            throw new IllegalStateException(
                    "devolucao de debito indevido exige instrucao LIQUIDADA: " + instruction.status());
        }
        String protocol = pspPort.requestUndueDebitRefund(instruction.chargeIdentifier(),
                instruction.endToEndId(), reason);
        UndueDebitRefund refund = new UndueDebitRefund(UUID.randomUUID(), instruction.id(),
                instruction.chargeIdentifier(), protocol, reason, requestedAt);
        store.saveRefund(refund);
        return refund;
    }

    public UndueDebitRefund completeUndueDebitRefund(UUID tenantId, UUID refundId, Instant completedAt) {
        UndueDebitRefund refund = store.findRefund(tenantId, refundId)
                .orElseThrow(() -> new IllegalArgumentException("devolucao nao encontrada: " + refundId));
        refund.complete(completedAt);
        store.saveRefund(refund);
        return refund;
    }

    // ------------------------------------------------------------------

    private PixAutoDebitInstruction buildInstruction(PixAutoDebitAuthorization authorization, InstructionDraft draft) {
        if (authorization.maxAmount() != null) {
            BigDecimal total = draft.principal()
                    .add(draft.fine() == null ? BigDecimal.ZERO : draft.fine())
                    .add(draft.interest() == null ? BigDecimal.ZERO : draft.interest());
            if (total.compareTo(authorization.maxAmount()) > 0) {
                throw new IllegalArgumentException("instrucao de " + total
                        + " acima do valor maximo autorizado pelo pagador: " + authorization.maxAmount());
            }
        }
        return new PixAutoDebitInstruction(UUID.randomUUID(), authorization.id(), draft.chargeIdentifier(),
                draft.documentType(), draft.documentId(), draft.collectionMonth(), draft.principal(),
                draft.fine(), draft.interest(), draft.settlementDate());
    }

    private PixAutoDebitAuthorization requireAuthorization(UUID tenantId, UUID authorizationId) {
        return store.findAuthorization(tenantId, authorizationId)
                .orElseThrow(() -> new IllegalArgumentException("autorizacao nao encontrada: " + authorizationId));
    }

    private PixAutoDebitAuthorization requireActiveAuthorization(UUID tenantId, UUID authorizationId) {
        PixAutoDebitAuthorization authorization = requireAuthorization(tenantId, authorizationId);
        if (authorization.status() != PixAutoDebitAuthorization.Status.ATIVA) {
            throw new IllegalStateException("autorizacao nao esta ATIVA: " + authorization.status());
        }
        return authorization;
    }

    private PixAutoDebitInstruction requireInstruction(UUID tenantId, UUID instructionId) {
        return store.findInstruction(tenantId, instructionId)
                .orElseThrow(() -> new IllegalArgumentException("instrucao nao encontrada: " + instructionId));
    }
}
