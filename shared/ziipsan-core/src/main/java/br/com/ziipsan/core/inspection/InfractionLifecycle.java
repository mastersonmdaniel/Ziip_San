package br.com.ziipsan.core.inspection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Ciclo de vida do auto de infracao (aceite da ED-24, de v2.15):
 * <ul>
 *   <li>o auto nasce da OS {@code INFRACTION_NOTICE}, emitido pelo motor de
 *       modelos da P2.30 (ver {@link InfractionDocumentRequest});</li>
 *   <li>o prazo de defesa e {@code infraction.appeal_days} contado da
 *       emissao ({@link Case#appealDeadline()});</li>
 *   <li>o debito so e lancado conforme {@code infraction.charge_timing}:
 *       {@code AFTER_DECISION} — somente depois da decisao;
 *       {@code ON_ISSUE_SUSPENDED_BY_APPEAL} — na emissao, e o recurso o
 *       suspende;</li>
 *   <li>o recurso entra pela OS {@code INFRACTION_APPEAL} e SUSPENDE o
 *       debito: lancado, fica suspenso; nao lancado, nao pode nascer
 *       enquanto o recurso nao for decidido;</li>
 *   <li>decisao que mantem o auto: debito lancado (ou reativado, se estava
 *       suspenso — nunca duplicado). Decisao que anula: debito cancelado
 *       (se existia) e nunca lancado.</li>
 * </ul>
 *
 * <p>Esta classe e a maquina de estados pura: cada transicao devolve o novo
 * {@link Case} e os {@link Effect efeitos} que o chamador deve executar
 * pelas portas (documento, OS, debito). Nenhuma transicao fora da maquina e
 * aceita — lancar debito antes da decisao, recorrer fora do prazo ou
 * decidir auto ja decidido sao recusas explicitas, nao atalhos.
 *
 * <p>O debito, quando nasce, e um recebivel de fonte {@code INFRACTION}
 * (catalogo {@code ReceivableSourceType} do faturamento, P2.5), com
 * {@code source_id} = id do auto.
 */
public final class InfractionLifecycle {

    private InfractionLifecycle() {}

    public enum NoticeStatus {
        ISSUED, UNDER_APPEAL, DECIDED_UPHELD, DECIDED_ANNULLED
    }

    public enum DebitState {
        /** Ainda nao lancado (AFTER_DECISION antes da decisao, ou aguardando). */
        NOT_LAUNCHED,
        /** Lancado e exigivel. */
        LAUNCHED,
        /** Lancado mas suspenso pelo recurso. */
        SUSPENDED_BY_APPEAL,
        /** Lancado e cancelado pela anulacao do auto. */
        CANCELLED_BY_ANNULMENT,
        /** Nunca sera lancado (auto anulado sem debito previo). */
        NEVER_LAUNCHED_ANNULLED
    }

    /** Efeitos colaterais que a transicao manda executar pelas portas. */
    public enum Effect {
        REQUEST_DOCUMENT, OPEN_NOTICE_ORDER, OPEN_APPEAL_ORDER,
        LAUNCH_DEBIT, SUSPEND_DEBIT, REACTIVATE_DEBIT, CANCEL_DEBIT
    }

    /** Estado completo de um auto de infracao. Imutavel: transicoes devolvem copia. */
    public record Case(UUID id, UUID fraudRecordId, UUID consumerUnitId,
                       BigDecimal amount, LocalDateTime issuedAt, int appealDays,
                       InspectionParameters.ChargeTiming chargeTiming,
                       NoticeStatus status, DebitState debitState,
                       UUID documentId, UUID noticeOrderId, UUID appealOrderId,
                       UUID decidedBy, LocalDateTime decidedAt) {

        public Case {
            Objects.requireNonNull(id, "id do auto e obrigatorio");
            Objects.requireNonNull(fraudRecordId, "registro de fraude e obrigatorio");
            Objects.requireNonNull(consumerUnitId, "unidade e obrigatoria");
            Objects.requireNonNull(amount, "valor do auto e obrigatorio");
            if (amount.signum() <= 0)
                throw new IllegalArgumentException("valor do auto deve ser positivo: " + amount);
            Objects.requireNonNull(issuedAt, "emissao do auto e obrigatoria");
            if (appealDays <= 0)
                throw new IllegalArgumentException("appeal_days deve ser maior que zero: " + appealDays);
            Objects.requireNonNull(chargeTiming, "infraction.charge_timing e obrigatorio");
            Objects.requireNonNull(status, "situacao do auto e obrigatoria");
            Objects.requireNonNull(debitState, "situacao do debito e obrigatoria");
        }

        /** Prazo final de defesa: emissao + infraction.appeal_days (data, fim do dia). */
        public LocalDate appealDeadline() {
            return issuedAt.toLocalDate().plusDays(appealDays);
        }

        /** Recurso ainda cabe na data dada (ate o fim do dia do prazo). */
        public boolean appealOpenAt(LocalDate date) {
            Objects.requireNonNull(date, "data da consulta e obrigatoria");
            return status == NoticeStatus.ISSUED && !date.isAfter(appealDeadline());
        }

        public boolean decided() {
            return status == NoticeStatus.DECIDED_UPHELD || status == NoticeStatus.DECIDED_ANNULLED;
        }

        Case copy(NoticeStatus newStatus, DebitState newDebit, UUID newDocumentId, UUID newNoticeOrderId,
                  UUID newAppealOrderId, UUID newDecidedBy, LocalDateTime newDecidedAt) {
            return new Case(id, fraudRecordId, consumerUnitId, amount, issuedAt, appealDays, chargeTiming,
                    newStatus, newDebit,
                    newDocumentId != null ? newDocumentId : documentId,
                    newNoticeOrderId != null ? newNoticeOrderId : noticeOrderId,
                    newAppealOrderId != null ? newAppealOrderId : appealOrderId,
                    newDecidedBy != null ? newDecidedBy : decidedBy,
                    newDecidedAt != null ? newDecidedAt : decidedAt);
        }
    }

    public record Transition(Case newCase, List<Effect> effects) {
        public Transition {
            Objects.requireNonNull(newCase, "novo estado e obrigatorio");
            effects = effects == null ? List.of() : List.copyOf(effects);
        }
    }

    /**
     * Emissao do auto a partir de fraude CONFIRMADA. Com
     * {@code ON_ISSUE_SUSPENDED_BY_APPEAL} o debito ja nasce na emissao
     * (efeito LAUNCH_DEBIT); com {@code AFTER_DECISION} nao nasce.
     */
    public static Transition issue(FraudRecord fraud, BigDecimal amount, LocalDateTime issuedAt,
                                   int appealDays, InspectionParameters.ChargeTiming timing) {
        Objects.requireNonNull(fraud, "registro de fraude e obrigatorio");
        if (!fraud.canSupportInfractionNotice())
            throw new IllegalStateException("auto de infracao exige fraude CONFIRMED; fraude " + fraud.id() + " esta " + fraud.status());
        Objects.requireNonNull(timing, "infraction.charge_timing e obrigatorio");
        Case created = new Case(UUID.randomUUID(), fraud.id(), fraud.consumerUnitId(), amount, issuedAt,
                appealDays, timing, NoticeStatus.ISSUED,
                timing == InspectionParameters.ChargeTiming.ON_ISSUE_SUSPENDED_BY_APPEAL
                        ? DebitState.LAUNCHED : DebitState.NOT_LAUNCHED,
                null, null, null, null, null);
        List<Effect> effects = timing == InspectionParameters.ChargeTiming.ON_ISSUE_SUSPENDED_BY_APPEAL
                ? List.of(Effect.REQUEST_DOCUMENT, Effect.OPEN_NOTICE_ORDER, Effect.LAUNCH_DEBIT)
                : List.of(Effect.REQUEST_DOCUMENT, Effect.OPEN_NOTICE_ORDER);
        return new Transition(created, effects);
    }

    /**
     * Recurso pela OS INFRACTION_APPEAL: so dentro do prazo de defesa e com
     * o auto ainda nao decidido. SUSPENDE o debito: lancado vira
     * SUSPENDED_BY_APPEAL; nao lancado permanece NOT_LAUNCHED e bloqueado
     * (ver {@link #launchDebit(Case)}).
     */
    public static Transition appeal(Case current, LocalDateTime appealedAt) {
        Objects.requireNonNull(current, "auto e obrigatorio");
        Objects.requireNonNull(appealedAt, "data/hora do recurso e obrigatoria");
        if (current.decided())
            throw new IllegalStateException("auto ja decidido nao aceita recurso: " + current.id());
        if (current.status() == NoticeStatus.UNDER_APPEAL)
            throw new IllegalStateException("auto ja esta sob recurso: " + current.id());
        if (!current.appealOpenAt(appealedAt.toLocalDate()))
            throw new IllegalStateException("recurso fora do prazo de defesa (infraction.appeal_days=" + current.appealDays()
                    + ", prazo ate " + current.appealDeadline() + "): " + current.id());
        DebitState newDebit = current.debitState() == DebitState.LAUNCHED
                ? DebitState.SUSPENDED_BY_APPEAL : current.debitState();
        List<Effect> effects = current.debitState() == DebitState.LAUNCHED
                ? List.of(Effect.OPEN_APPEAL_ORDER, Effect.SUSPEND_DEBIT)
                : List.of(Effect.OPEN_APPEAL_ORDER);
        return new Transition(current.copy(NoticeStatus.UNDER_APPEAL, newDebit, null, null, null, null, null), effects);
    }

    /**
     * Decisao do recurso (ou do auto sem recurso, apos o prazo): manter
     * lanca/ reativa o debito; anular cancela/impede o debito.
     *
     * <p><b>Guarda do prazo de defesa sem recurso [A CONFIRMAR NA
     * INTEGRACAO] — interpretacao CONSERVADORA (fail-closed), correcao D1
     * da revisao cruzada da ED-24:</b> sem recurso interposto (auto ainda
     * {@code ISSUED}), a decisao so e aceita DEPOIS de decorrido o prazo
     * de defesa {@code infraction.appeal_days} contado da emissao — ou
     * seja, em data posterior a {@link Case#appealDeadline()} (o recurso
     * cabe ate o fim do dia do prazo, logo decidir no proprio dia do
     * prazo tambem e recusado). O aceite da ED-24 fixa o prazo de defesa
     * e o debito somente apos a decisao, mas NAO nomeia esta guarda: ela
     * e a leitura conservadora adotada nesta frente para nao encerrar a
     * defesa antes do prazo, e deve ser confirmada (ou substituida por
     * ADR que atribua a orquestracao temporal ao chamador) na integracao.
     * Com recurso interposto ({@code UNDER_APPEAL}), a decisao e a
     * decisao do recurso e segue o fluxo original, em qualquer data.
     */
    public static Transition decide(Case current, boolean upheld, UUID decidedBy, LocalDateTime decidedAt) {
        Objects.requireNonNull(current, "auto e obrigatorio");
        Objects.requireNonNull(decidedBy, "quem decidiu e obrigatorio");
        Objects.requireNonNull(decidedAt, "data/hora da decisao e obrigatoria");
        if (current.decided())
            throw new IllegalStateException("auto ja decidido: " + current.id());
        // D1 [A CONFIRMAR NA INTEGRACAO]: guarda conservadora (fail-closed) do
        // prazo de defesa quando NAO ha recurso — ver javadoc acima. Sem esta
        // guarda, decidir no dia seguinte a emissao lancaria o debito e
        // encerraria a defesa de infraction.appeal_days.
        if (current.status() == NoticeStatus.ISSUED
                && !decidedAt.toLocalDate().isAfter(current.appealDeadline()))
            throw new IllegalStateException("decisao sem recurso antes de decorrido o prazo de defesa recusada"
                    + " (infraction.appeal_days=" + current.appealDays() + ", prazo ate " + current.appealDeadline()
                    + ") — guarda conservadora [A CONFIRMAR NA INTEGRACAO]: " + current.id());
        if (upheld) {
            return switch (current.debitState()) {
                case NOT_LAUNCHED -> new Transition(
                        current.copy(NoticeStatus.DECIDED_UPHELD, DebitState.LAUNCHED, null, null, null, decidedBy, decidedAt),
                        List.of(Effect.LAUNCH_DEBIT));
                case SUSPENDED_BY_APPEAL -> new Transition(
                        current.copy(NoticeStatus.DECIDED_UPHELD, DebitState.LAUNCHED, null, null, null, decidedBy, decidedAt),
                        List.of(Effect.REACTIVATE_DEBIT));
                case LAUNCHED -> new Transition(
                        current.copy(NoticeStatus.DECIDED_UPHELD, DebitState.LAUNCHED, null, null, null, decidedBy, decidedAt),
                        List.of());
                default -> throw new IllegalStateException("debito em estado incompativel com manutencao do auto: " + current.debitState());
            };
        }
        return switch (current.debitState()) {
            case LAUNCHED, SUSPENDED_BY_APPEAL -> new Transition(
                    current.copy(NoticeStatus.DECIDED_ANNULLED, DebitState.CANCELLED_BY_ANNULMENT, null, null, null, decidedBy, decidedAt),
                    List.of(Effect.CANCEL_DEBIT));
            case NOT_LAUNCHED -> new Transition(
                    current.copy(NoticeStatus.DECIDED_ANNULLED, DebitState.NEVER_LAUNCHED_ANNULLED, null, null, null, decidedBy, decidedAt),
                    List.of());
            default -> throw new IllegalStateException("debito em estado incompativel com anulacao do auto: " + current.debitState());
        };
    }

    /**
     * Guarda do lancamento AVULSO de debito — chamada direta sobre um
     * estado, fora do despacho de efeitos do {@link InfractionService}.
     * Escopo real desta guarda (correcao D5 da revisao cruzada da ED-24,
     * alinhando o texto ao desenho):
     * <ul>
     *   <li>ela vale para o timing {@code AFTER_DECISION} e para
     *       lancamentos avulsos: o debito so pode nascer depois da decisao
     *       de manutencao — auto nao decidido, auto sob recurso e auto
     *       anulado sao recusados;</li>
     *   <li>o timing {@code ON_ISSUE_SUSPENDED_BY_APPEAL} lanca o debito
     *       NA EMISSAO, por desenho (semantica do proprio valor do
     *       catalogo), e esse lancamento NAO passa por esta guarda: quem
     *       o despacha e {@code InfractionService.issue}, a partir do
     *       efeito {@code LAUNCH_DEBIT} da emissao. Consequencia: chamar
     *       esta guarda sobre um auto {@code ON_ISSUE} recem-emitido
     *       lanca excecao porque ele ainda nao foi decidido — isso NAO
     *       torna ilegitimo o debito ja lancado na emissao;</li>
     *   <li>no fluxo da decisao, o servico chama esta guarda sobre o
     *       estado JA decidido (ver {@code InfractionService.decide}),
     *       onde ela confirma que um auto mantido pode ter o debito
     *       lancado e que um auto anulado nao pode.</li>
     * </ul>
     */
    public static void assertDebitLaunchAllowed(Case current) {
        Objects.requireNonNull(current, "auto e obrigatorio");
        if (current.status() == NoticeStatus.UNDER_APPEAL)
            throw new IllegalStateException("debito suspenso pelo recurso (OS INFRACTION_APPEAL) ate a decisao: " + current.id());
        if (!current.decided())
            throw new IllegalStateException("debito do auto so depois da decisao (infraction.charge_timing): " + current.id());
        if (current.status() == NoticeStatus.DECIDED_ANNULLED)
            throw new IllegalStateException("auto anulado nunca gera debito: " + current.id());
    }
}
