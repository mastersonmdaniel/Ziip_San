package br.com.ziipsan.core.inspection;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Servico da ED-24 que executa as transicoes de
 * {@link InfractionLifecycle} pelas portas reais: documento no motor da
 * P2.30, OS no motor da P1.31 e debito no faturamento (recebivel de fonte
 * INFRACTION da P2.5). A maquina de estados decide; este servico so
 * despacha os efeitos na ordem e devolve o estado ja com os ids das
 * entidades criadas.
 *
 * <p>Os adaptadores das portas vivem na integracao (mesmo desenho do
 * {@code CoreServiceOrderGateway} da P2.20): esta classe nao conhece JPA,
 * DataSource nem o servico concreto de modelos — conhece os contratos.
 */
public final class InfractionService {

    /** Porta do debito do auto no faturamento (recebivel fonte INFRACTION, P2.5). */
    public interface InfractionDebitGateway {
        /** Lanca o recebivel do auto (source_type INFRACTION, source_id = id do auto). */
        UUID launchDebit(UUID infractionNoticeId, UUID consumerUnitId, BigDecimal amount);
        void suspendDebit(UUID infractionNoticeId);
        void reactivateDebit(UUID infractionNoticeId);
        void cancelDebit(UUID infractionNoticeId);
    }

    /** Porta de abertura das OS INFRACTION_NOTICE / INFRACTION_APPEAL no motor da P1.31. */
    public interface InfractionOrderGateway {
        UUID openOrder(String orderTypeCode, UUID consumerUnitId, UUID infractionNoticeId, UUID requestedBy);
    }

    private final InfractionDocumentRequest.DocumentEngineGateway documents;
    private final InfractionOrderGateway orders;
    private final InfractionDebitGateway debits;

    public InfractionService(InfractionDocumentRequest.DocumentEngineGateway documents,
                             InfractionOrderGateway orders, InfractionDebitGateway debits) {
        this.documents = Objects.requireNonNull(documents, "porta de documentos (P2.30) e obrigatoria");
        this.orders = Objects.requireNonNull(orders, "porta de OS (P1.31) e obrigatoria");
        this.debits = Objects.requireNonNull(debits, "porta de debito (faturamento) e obrigatoria");
    }

    /**
     * Emite o auto: documento pela P2.30, OS INFRACTION_NOTICE pela P1.31 e,
     * se o timing for ON_ISSUE_SUSPENDED_BY_APPEAL, o debito ja lancado.
     */
    public InfractionLifecycle.Case issue(FraudRecord fraud, BigDecimal amount, LocalDateTime issuedAt,
                                          int appealDays, InspectionParameters.ChargeTiming timing,
                                          String customerName, String registrationNumber, String protocolNumber,
                                          UUID issuedBy) {
        Objects.requireNonNull(issuedBy, "quem emite o auto e obrigatorio");
        InfractionLifecycle.Transition transition = InfractionLifecycle.issue(fraud, amount, issuedAt, appealDays, timing);
        InfractionLifecycle.Case result = transition.newCase();
        UUID documentId = null;
        UUID noticeOrderId = null;
        for (InfractionLifecycle.Effect effect : transition.effects()) {
            switch (effect) {
                case REQUEST_DOCUMENT -> {
                    InfractionDocumentRequest.Request request = InfractionDocumentRequest.build(
                            customerName, registrationNumber, protocolNumber, fraud.description(), appealDays);
                    documentId = documents.renderInfractionNotice(request);
                }
                case OPEN_NOTICE_ORDER -> noticeOrderId = orders.openOrder(
                        InspectionOrderTypes.INFRACTION_NOTICE, result.consumerUnitId(), result.id(), issuedBy);
                case LAUNCH_DEBIT -> debits.launchDebit(result.id(), result.consumerUnitId(), result.amount());
                default -> throw new IllegalStateException("efeito inesperado na emissao do auto: " + effect);
            }
        }
        return result.copy(result.status(), result.debitState(), documentId, noticeOrderId, null, null, null);
    }

    /** Registra o recurso (OS INFRACTION_APPEAL) e suspende o debito, quando lancado. */
    public InfractionLifecycle.Case appeal(InfractionLifecycle.Case current, LocalDateTime appealedAt, UUID appealedBy) {
        Objects.requireNonNull(appealedBy, "quem recorre e obrigatorio");
        InfractionLifecycle.Transition transition = InfractionLifecycle.appeal(current, appealedAt);
        UUID appealOrderId = null;
        for (InfractionLifecycle.Effect effect : transition.effects()) {
            switch (effect) {
                case OPEN_APPEAL_ORDER -> appealOrderId = orders.openOrder(
                        InspectionOrderTypes.INFRACTION_APPEAL, current.consumerUnitId(), current.id(), appealedBy);
                case SUSPEND_DEBIT -> debits.suspendDebit(current.id());
                default -> throw new IllegalStateException("efeito inesperado no recurso: " + effect);
            }
        }
        InfractionLifecycle.Case next = transition.newCase();
        return next.copy(next.status(), next.debitState(), null, null, appealOrderId, null, null);
    }

    /** Decide o auto: manter lanca/reativa o debito; anular cancela/impede. */
    public InfractionLifecycle.Case decide(InfractionLifecycle.Case current, boolean upheld,
                                           UUID decidedBy, LocalDateTime decidedAt) {
        InfractionLifecycle.Transition transition = InfractionLifecycle.decide(current, upheld, decidedBy, decidedAt);
        for (InfractionLifecycle.Effect effect : transition.effects()) {
            switch (effect) {
                case LAUNCH_DEBIT -> {
                    InfractionLifecycle.assertDebitLaunchAllowed(transition.newCase());
                    debits.launchDebit(current.id(), current.consumerUnitId(), current.amount());
                }
                case REACTIVATE_DEBIT -> debits.reactivateDebit(current.id());
                case CANCEL_DEBIT -> debits.cancelDebit(current.id());
                default -> throw new IllegalStateException("efeito inesperado na decisao: " + effect);
            }
        }
        return transition.newCase();
    }
}
