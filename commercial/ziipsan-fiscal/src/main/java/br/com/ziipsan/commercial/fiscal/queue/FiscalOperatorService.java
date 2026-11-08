package br.com.ziipsan.commercial.fiscal.queue;

import br.com.ziipsan.commercial.fiscal.nfage.NfageStatus;
import br.com.ziipsan.core.json.Json;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Ações do operador sobre a fila de Documentos fiscais do tenant (aceite da P2.33).
 * Cada ação exige a SUA permissão (catálogo da P0.13), motivo obrigatório e grava a
 * linha da trilha na V4_11 ANTES de qualquer efeito externo — ação sem trilha não
 * existe. As portas externas (SEFAZ) são as interfaces deste pacote: sem
 * implementação ligada, a ação fica REQUESTED/PENDING e nenhum desfecho é fingido.
 *
 * <p><b>Reenvio idempotente (aceite expresso):</b> a chave de idempotência do reenvio
 * deriva do documento e da sua chave de acesso. Reenvio repetido encontra a linha já
 * gravada e a devolve — não chama a porta de novo, não reserva número novo, não gera
 * segundo documento. Reenviar é retransmitir o MESMO documento (contingência não
 * transmitida, transmitido ou aguardando reconciliação); documento autorizado,
 * rejeitado (terminal na P2.6 — a correção é documento novo), cancelado, substituído
 * ou inutilizado não é reenviável.
 *
 * <p><b>Inutilização:</b> a {@link VoidingRule} decide sobre os números usados da
 * série; aceita, o evento é registrado conforme o leiaute (série, faixa, motivo e
 * {@code tpAmb} no detalhe JSON, serializado pela {@code Json} do core) e, com a
 * porta ligada, confirmado pelo protocolo dela. O teste em homologação
 * ({@code tpAmb=2}) contra a SEFAZ real é aceite externo (ver relatório).
 *
 * <p><b>Cancelamento pendente:</b> só documento AUTHORIZED; nasce PENDING na V4_11,
 * gera o item de tarefas do responsável pelo contrato e só sai de PENDING pela
 * confirmação da porta — a fila passa a mostrar CANCELLATION_PENDING pela projeção
 * de leitura, que lê esta pendência.
 *
 * <p><b>Pendências e tarefas:</b> {@link #registerPendency} abre o item das três
 * pendências do aceite (rejeição, contingência, cancelamento pendente) para o
 * responsável pelo contrato, e {@link #onDocumentAuthorized} encerra os itens de
 * contingência/rejeição do documento quando ele é autorizado (aceite expresso).
 */
public final class FiscalOperatorService {

    private static final ZoneId ISSUE_ZONE = ZoneId.of("America/Sao_Paulo");

    private final Clock clock;
    private final FiscalDocumentReadPort readPort;
    private final FiscalOperatorTrailPort trailPort;
    private final FiscalResendGateway resendGateway;
    private final FiscalVoidingGateway voidingGateway;
    private final FiscalCancellationGateway cancellationGateway;
    private final FiscalWorkItemPort workItemPort;

    /** Sem portas externas ligadas: as ações registram e ficam pendentes (ver javadoc). */
    public FiscalOperatorService(Clock clock, FiscalDocumentReadPort readPort,
                                 FiscalOperatorTrailPort trailPort, FiscalWorkItemPort workItemPort) {
        this(clock, readPort, trailPort, null, null, null, workItemPort);
    }

    public FiscalOperatorService(Clock clock, FiscalDocumentReadPort readPort,
                                 FiscalOperatorTrailPort trailPort,
                                 FiscalResendGateway resendGateway,
                                 FiscalVoidingGateway voidingGateway,
                                 FiscalCancellationGateway cancellationGateway,
                                 FiscalWorkItemPort workItemPort) {
        this.clock = Objects.requireNonNull(clock, "Clock é obrigatório (relógio injetável)");
        this.readPort = Objects.requireNonNull(readPort, "Porta de leitura dos documentos é obrigatória");
        this.trailPort = Objects.requireNonNull(trailPort, "Porta da trilha de ações é obrigatória");
        this.resendGateway = resendGateway;
        this.voidingGateway = voidingGateway;
        this.cancellationGateway = cancellationGateway;
        this.workItemPort = workItemPort;
    }

    // ------------------------------------------------------------------
    // Reenvio
    // ------------------------------------------------------------------

    public FiscalOperatorAction resend(UUID tenantId, UUID documentId, UUID actorId,
                                       Set<String> actorPermissions, String reason) {
        requirePermission(actorPermissions, FiscalOperatorPermissions.RESEND);
        requireReason(reason);
        FiscalDocumentView doc = document(tenantId, documentId);
        requireResendable(doc);
        String key = "RESEND:" + documentId + ":" + doc.accessKey();
        Optional<FiscalOperatorAction> existing = trailPort.findByIdempotencyKey(tenantId, key);
        if (existing.isPresent()) {
            // Reenvio repetido: devolve a tentativa já registrada. Sem nova chamada
            // à porta, sem número novo, sem segundo documento.
            return existing.get();
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("chaveAcesso", doc.accessKey());
        // TRANSMITTED/PENDING_RECONCILIATION não têm estado nesta fila (classificação
        // nula): a ausência vai como JSON null via Json do core — nunca a string "null".
        FiscalQueueState queueState = FiscalQueueClassifier.classify(doc);
        detail.put("estadoFila", queueState == null ? null : queueState.name());
        FiscalOperatorAction action = new FiscalOperatorAction(UUID.randomUUID(), tenantId,
                FiscalOperatorActionType.RESEND, FiscalOperatorActionStatus.REQUESTED,
                documentId, null, doc.serie(), (long) doc.numero(), (long) doc.numero(),
                actorId, FiscalOperatorPermissions.RESEND, reason, null, null, key,
                Json.write(detail), now());
        trailPort.record(action);
        if (resendGateway == null) {
            return action;
        }
        FiscalResendGateway.ResendOutcome outcome = resendGateway.retransmit(doc.accessKey());
        FiscalOperatorActionStatus status = outcome.resultingStatus() == NfageStatus.PENDING_RECONCILIATION
                ? FiscalOperatorActionStatus.REQUESTED : FiscalOperatorActionStatus.COMPLETED;
        trailPort.updateStatus(tenantId, action.id(), status, outcome.protocol());
        return new FiscalOperatorAction(action.id(), tenantId, action.type(), status,
                documentId, null, doc.serie(), (long) doc.numero(), (long) doc.numero(),
                actorId, action.permission(), reason, null, outcome.protocol(), key,
                action.detailJson(), action.createdAt());
    }

    private void requireResendable(FiscalDocumentView doc) {
        if (doc.kind() == FiscalDocumentKind.NFSE_AVULSA) {
            if (doc.avulsaTransmitted()) {
                throw new IllegalStateException("NFS-e avulsa já transmitida não é reenviável");
            }
            return;
        }
        NfageStatus status = doc.nfageStatus();
        boolean resendable = FiscalQueueClassifier.classify(doc) == FiscalQueueState.CONTINGENCY
                || status == NfageStatus.TRANSMITTED
                || status == NfageStatus.PENDING_RECONCILIATION;
        if (!resendable) {
            throw new IllegalStateException("Documento em " + status + " não é reenviável: "
                    + "reenvio é retransmissão de contingência/transmitido/aguardando reconciliação; "
                    + "rejeitado é terminal (correção é documento novo, P2.6)");
        }
    }

    // ------------------------------------------------------------------
    // Inutilização de numeração
    // ------------------------------------------------------------------

    public FiscalOperatorAction voidNumbers(UUID tenantId, UUID issuerId, int serie,
                                            long numberFrom, long numberTo, UUID actorId,
                                            Set<String> actorPermissions, String reason, int tpAmb) {
        requirePermission(actorPermissions, FiscalOperatorPermissions.VOID_NUMBER);
        requireReason(reason);
        if (issuerId == null) {
            throw new IllegalArgumentException("Inutilização exige o emitente (fiscal_issuers da V4_5)");
        }
        if (serie < 0) {
            throw new IllegalArgumentException("Inutilização exige série >= 0");
        }
        if (tpAmb != 1 && tpAmb != 2) {
            throw new IllegalArgumentException("tpAmb da inutilização é 1 (produção) ou 2 (homologação)");
        }
        VoidingRule.requireVoidable(numberFrom, numberTo, readPort.usedNumbers(tenantId, issuerId, serie));
        String key = "VOIDING:" + issuerId + ":" + serie + ":" + numberFrom + "-" + numberTo;
        Optional<FiscalOperatorAction> existing = trailPort.findByIdempotencyKey(tenantId, key);
        if (existing.isPresent()) {
            return existing.get();
        }
        // Registro do evento conforme o leiaute: os campos do evento de inutilização
        // (emitente, série, faixa, motivo, ambiente) no detalhe, via Json do core.
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("evento", "INUTILIZACAO_NUMERACAO");
        detail.put("emitenteId", issuerId.toString());
        detail.put("serie", serie);
        detail.put("numeroInicial", numberFrom);
        detail.put("numeroFinal", numberTo);
        detail.put("motivo", reason);
        detail.put("tpAmb", tpAmb);
        FiscalOperatorAction action = new FiscalOperatorAction(UUID.randomUUID(), tenantId,
                FiscalOperatorActionType.VOIDING, FiscalOperatorActionStatus.REQUESTED,
                null, issuerId, serie, numberFrom, numberTo,
                actorId, FiscalOperatorPermissions.VOID_NUMBER, reason, tpAmb, null, key,
                Json.write(detail), now());
        trailPort.record(action);
        if (voidingGateway == null) {
            return action;
        }
        FiscalVoidingGateway.VoidingResult result = voidingGateway.registerVoiding(
                new FiscalVoidingGateway.VoidingEvent(issuerId, serie, numberFrom, numberTo, reason, tpAmb));
        trailPort.updateStatus(tenantId, action.id(), FiscalOperatorActionStatus.COMPLETED, result.protocol());
        return new FiscalOperatorAction(action.id(), tenantId, action.type(),
                FiscalOperatorActionStatus.COMPLETED, null, issuerId, serie, numberFrom, numberTo,
                actorId, action.permission(), reason, tpAmb, result.protocol(), key,
                action.detailJson(), action.createdAt());
    }

    // ------------------------------------------------------------------
    // Cancelamento pendente
    // ------------------------------------------------------------------

    public FiscalOperatorAction requestCancellation(UUID tenantId, UUID documentId, UUID actorId,
                                                    Set<String> actorPermissions, String reason,
                                                    UUID contractResponsibleId) {
        requirePermission(actorPermissions, FiscalOperatorPermissions.REQUEST_CANCELLATION);
        requireReason(reason);
        FiscalDocumentView doc = document(tenantId, documentId);
        if (doc.kind() != FiscalDocumentKind.NFAGE || doc.nfageStatus() != NfageStatus.AUTHORIZED) {
            throw new IllegalStateException("Cancelamento pendente exige documento AUTHORIZED: "
                    + "só documento autorizado sai por evento de cancelamento (P2.6)");
        }
        Optional<FiscalOperatorAction> pending = trailPort.findPendingCancellation(tenantId, documentId);
        if (pending.isPresent()) {
            return pending.get();
        }
        String key = "CANCELLATION:" + documentId + ":" + doc.accessKey();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("chaveAcesso", doc.accessKey());
        FiscalOperatorAction action = new FiscalOperatorAction(UUID.randomUUID(), tenantId,
                FiscalOperatorActionType.CANCELLATION_REQUEST, FiscalOperatorActionStatus.PENDING,
                documentId, null, doc.serie(), (long) doc.numero(), (long) doc.numero(),
                actorId, FiscalOperatorPermissions.REQUEST_CANCELLATION, reason, null, null, key,
                Json.write(detail), now());
        trailPort.record(action);
        if (workItemPort != null) {
            workItemPort.openPendency(tenantId, FiscalPendencyKind.CANCELLATION_PENDING,
                    documentId, contractResponsibleId, actorId);
        }
        if (cancellationGateway == null) {
            return action;
        }
        FiscalCancellationGateway.CancellationOutcome outcome =
                cancellationGateway.requestCancellation(doc.accessKey(), reason);
        if (!outcome.confirmed()) {
            return action;
        }
        trailPort.updateStatus(tenantId, action.id(), FiscalOperatorActionStatus.COMPLETED, outcome.protocol());
        if (workItemPort != null) {
            workItemPort.closePendency(tenantId, FiscalPendencyKind.CANCELLATION_PENDING,
                    documentId, "CANCELLATION_CONFIRMED");
        }
        return new FiscalOperatorAction(action.id(), tenantId, action.type(),
                FiscalOperatorActionStatus.COMPLETED, documentId, null, doc.serie(),
                (long) doc.numero(), (long) doc.numero(), actorId, action.permission(), reason,
                null, outcome.protocol(), key, action.detailJson(), action.createdAt());
    }

    // ------------------------------------------------------------------
    // Pendências -> minhas tarefas (P2.28)
    // ------------------------------------------------------------------

    /**
     * Registra a pendência do documento (rejeição, contingência ou cancelamento
     * pendente) como item de tarefas do responsável pelo contrato. Documento sem
     * pendência do aceite não gera item (resultado vazio, nunca item inventado).
     */
    public Optional<FiscalPendencyKind> registerPendency(UUID tenantId, UUID documentId,
                                                         UUID contractResponsibleId, UUID requesterId) {
        if (workItemPort == null) {
            throw new IllegalStateException("Porta de itens de trabalho não ligada: a pendência "
                    + "não pode ser registrada em minhas tarefas (integração com a P2.28 pendente)");
        }
        FiscalDocumentView doc = document(tenantId, documentId);
        FiscalQueueState state = FiscalQueueClassifier.classify(doc);
        if (state == null || !FiscalQueueClassifier.generatesWorkItem(state)) {
            return Optional.empty();
        }
        FiscalPendencyKind kind = switch (state) {
            case REJECTED -> FiscalPendencyKind.REJECTION;
            case CONTINGENCY -> FiscalPendencyKind.CONTINGENCY;
            case CANCELLATION_PENDING -> FiscalPendencyKind.CANCELLATION_PENDING;
            default -> throw new IllegalStateException("Estado sem pendência de tarefa: " + state);
        };
        workItemPort.openPendency(tenantId, kind, documentId, contractResponsibleId, requesterId);
        return Optional.of(kind);
    }

    /**
     * Encerramento do aceite: encerra os itens de contingência e de rejeição
     * ligados ao documento informado, pelo id dele. Quem chama informa o
     * documento já autorizado: para NFAg-e, este método exige o estado
     * AUTHORIZED na projeção (a avulsa não tem estado do dicionário a
     * conferir). Nenhum vínculo de origem entre documentos é conferido aqui —
     * fechar o item de rejeição de um documento corrigido a partir da
     * autorização do seu sucessor é passo da integração (pendência nomeada
     * no relatório), não deste método.
     */
    public void onDocumentAuthorized(UUID tenantId, UUID authorizedDocumentId) {
        if (workItemPort == null) {
            return;
        }
        FiscalDocumentView authorized = document(tenantId, authorizedDocumentId);
        if (authorized.kind() == FiscalDocumentKind.NFAGE
                && authorized.nfageStatus() != NfageStatus.AUTHORIZED) {
            throw new IllegalStateException("Encerramento exige o documento AUTHORIZED");
        }
        workItemPort.closePendency(tenantId, FiscalPendencyKind.CONTINGENCY,
                authorizedDocumentId, "DOCUMENT_AUTHORIZED");
        workItemPort.closePendency(tenantId, FiscalPendencyKind.REJECTION,
                authorizedDocumentId, "DOCUMENT_AUTHORIZED");
    }

    // ------------------------------------------------------------------

    private FiscalDocumentView document(UUID tenantId, UUID documentId) {
        Objects.requireNonNull(tenantId, "Tenant é obrigatório");
        Objects.requireNonNull(documentId, "Documento fiscal é obrigatório");
        return readPort.findById(tenantId, documentId).orElseThrow(() ->
                new IllegalArgumentException("Documento fiscal não encontrado no tenant: " + documentId));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ISSUE_ZONE);
    }

    private static void requirePermission(Set<String> actorPermissions, String permission) {
        if (actorPermissions == null || !actorPermissions.contains(permission)) {
            throw new SecurityException("Ação fiscal recusada: falta a permissão própria " + permission);
        }
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Ação do operador exige motivo (aceite da P2.33)");
        }
    }
}
