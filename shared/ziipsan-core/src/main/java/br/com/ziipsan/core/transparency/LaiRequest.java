package br.com.ziipsan.core.transparency;

import br.com.ziipsan.core.protocol.DeadlineCalculator;
import br.com.ziipsan.core.protocol.DeadlineCountingRule;
import br.com.ziipsan.core.protocol.ProtocolChannel;
import br.com.ziipsan.core.transparency.TransparencyParameters.LaiMode;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Pedido de acesso a informacao como TIPO DE PROTOCOLO da P1.36 (ED-99;
 * CAD-77), atendido no modo {@code NATIVE_SIC}: todos os vencimentos sao
 * calculados pelo motor de prazos da P1.36 ({@link DeadlineCalculator})
 * com a regra de contagem e os feriados do tenant, a partir dos prazos
 * validados de {@link LaiDeadlines} ({@code sac.lai_deadlines}, classe c,
 * nunca acima da Lei 12.527/2011):
 * <pre>
 *   resposta            responseDays        a partir da abertura
 *   prorrogacao         extensionDays       UMA vez, justificada, a
 *                                           partir do vencimento vigente
 *   recurso             appealDays          a partir da resposta
 *                                           (janela do cidadao)
 *   decisao do recurso  appealDecisionDays  a partir do recurso
 * </pre>
 * O pedido nativo so nasce com o modulo em {@code NATIVE_SIC} — nos modos
 * {@code EXTERNAL_LINK} e {@code NONE} a construcao e recusada (o pedido
 * vai ao sistema do municipio ou o modulo esta desligado).
 *
 * <p>NENHUM dado do requerente vive aqui: o vinculo e o {@code protocolId}
 * (UUID, convencao V8_1 da P1.36) e o canal {@link ProtocolChannel}; a
 * identidade fica no protocolo, como na ED-35.
 */
public final class LaiRequest {

    /** Resultado da resposta ao pedido. */
    public enum AnswerOutcome { GRANTED, PARTIALLY_GRANTED, DENIED }

    /** Resultado da decisao do recurso. */
    public enum AppealOutcome { GRANTED, DENIED }

    private final UUID protocolId;
    private final ProtocolChannel channel;
    private final LocalDate openedDate;
    private final LaiDeadlines deadlines;
    private final LocalDate originalResponseDue;
    private LocalDate currentResponseDue;
    private boolean extended;
    private String extensionJustification;

    private LocalDate answeredDate;
    private AnswerOutcome answerOutcome;

    private LocalDate appealDate;
    private LocalDate appealDecisionDue;
    private LocalDate appealDecidedDate;
    private AppealOutcome appealOutcome;

    public LaiRequest(UUID protocolId, ProtocolChannel channel, LocalDate openedDate,
                      LaiMode mode, LaiDeadlines deadlines,
                      DeadlineCountingRule rule, Set<LocalDate> holidays) {
        this.protocolId = Objects.requireNonNull(protocolId, "protocolo obrigatorio");
        this.channel = Objects.requireNonNull(channel, "canal do protocolo (P1.36) obrigatorio");
        this.openedDate = Objects.requireNonNull(openedDate, "abertura obrigatoria");
        Objects.requireNonNull(mode, "pub.lai_mode obrigatorio");
        if (mode != LaiMode.NATIVE_SIC) throw new IllegalStateException(
                "pedido LAI nativo exige pub.lai_mode=NATIVE_SIC; modo vigente: " + mode);
        this.deadlines = Objects.requireNonNull(deadlines, "sac.lai_deadlines obrigatorio");
        this.originalResponseDue = DeadlineCalculator.computeDueDate(
                openedDate, deadlines.responseDays(), rule, holidays);
        this.currentResponseDue = this.originalResponseDue;
    }

    /**
     * A UNICA prorrogacao da resposta, por {@code extensionDays}, com
     * justificativa obrigatoria, contada do vencimento vigente pelo
     * motor de prazos. Segunda prorrogacao e recusada; prorrogacao apos
     * a resposta tambem.
     */
    public void extend(String justification, DeadlineCountingRule rule, Set<LocalDate> holidays) {
        if (justification == null || justification.isBlank())
            throw new IllegalArgumentException("prorrogacao do pedido LAI exige justificativa");
        if (answeredDate != null)
            throw new IllegalStateException("pedido ja respondido nao admite prorrogacao");
        if (extended) throw new IllegalStateException(
                "pedido LAI admite uma unica prorrogacao (sac.lai_deadlines.extensionDays)");
        this.currentResponseDue = DeadlineCalculator.extendDueDate(
                currentResponseDue, deadlines.extensionDays(), rule, holidays);
        this.extended = true;
        this.extensionJustification = justification;
    }

    /** Registra a resposta; resposta antes da abertura ou duplicada e recusada. */
    public void answer(LocalDate date, AnswerOutcome outcome) {
        Objects.requireNonNull(date, "data da resposta obrigatoria");
        Objects.requireNonNull(outcome, "resultado da resposta obrigatorio");
        if (answeredDate != null) throw new IllegalStateException("pedido LAI ja respondido");
        if (date.isBefore(openedDate))
            throw new IllegalArgumentException("resposta anterior a abertura do pedido");
        this.answeredDate = date;
        this.answerOutcome = outcome;
    }

    /** A resposta saiu dentro do prazo vigente? (sem resposta = {@code false}). */
    public boolean answeredWithinDeadline() {
        return answeredDate != null && !answeredDate.isAfter(currentResponseDue);
    }

    /** Ultimo dia para o cidadao recorrer: resposta + {@code appealDays} pelo motor de prazos. */
    public LocalDate appealDeadline(DeadlineCountingRule rule, Set<LocalDate> holidays) {
        if (answeredDate == null) throw new IllegalStateException(
                "janela de recurso so existe apos a resposta");
        return DeadlineCalculator.computeDueDate(answeredDate, deadlines.appealDays(), rule, holidays);
    }

    /**
     * Registra o recurso — cabivel contra resposta DENIED ou
     * PARTIALLY_GRANTED — dentro da janela de {@code appealDays} contada
     * da resposta pelo motor de prazos, e fixa o vencimento da decisao
     * ({@code appealDecisionDays} a partir do recurso).
     */
    public void appeal(LocalDate date, DeadlineCountingRule rule, Set<LocalDate> holidays) {
        Objects.requireNonNull(date, "data do recurso obrigatoria");
        if (answeredDate == null) throw new IllegalStateException(
                "recurso exige resposta previa do pedido LAI");
        if (appealDate != null) throw new IllegalStateException("recurso ja registrado neste pedido");
        if (answerOutcome == AnswerOutcome.GRANTED) throw new IllegalStateException(
                "recurso nao cabe contra resposta integralmente deferida");
        if (date.isBefore(answeredDate))
            throw new IllegalArgumentException("recurso anterior a resposta do pedido");
        LocalDate window = appealDeadline(rule, holidays);
        if (date.isAfter(window)) throw new IllegalArgumentException(
                "recurso fora da janela de " + deadlines.appealDays()
                        + " dias da resposta (vencimento " + window + ")");
        this.appealDate = date;
        this.appealDecisionDue = DeadlineCalculator.computeDueDate(
                date, deadlines.appealDecisionDays(), rule, holidays);
    }

    /** Registra a decisao do recurso; decisao antes do recurso ou duplicada e recusada. */
    public void decideAppeal(LocalDate date, AppealOutcome outcome) {
        Objects.requireNonNull(date, "data da decisao obrigatoria");
        Objects.requireNonNull(outcome, "resultado da decisao obrigatorio");
        if (appealDate == null) throw new IllegalStateException("decisao exige recurso registrado");
        if (appealDecidedDate != null) throw new IllegalStateException("recurso ja decidido");
        if (date.isBefore(appealDate))
            throw new IllegalArgumentException("decisao anterior ao recurso");
        this.appealDecidedDate = date;
        this.appealOutcome = outcome;
    }

    /** A decisao do recurso saiu dentro do prazo? (sem decisao = {@code false}). */
    public boolean appealDecidedWithinDeadline() {
        return appealDecidedDate != null && !appealDecidedDate.isAfter(appealDecisionDue);
    }

    public boolean responseOverdueOn(LocalDate date) {
        return answeredDate == null && date.isAfter(currentResponseDue);
    }

    public boolean appealDecisionOverdueOn(LocalDate date) {
        return appealDate != null && appealDecidedDate == null && date.isAfter(appealDecisionDue);
    }

    public UUID protocolId() { return protocolId; }
    public ProtocolChannel channel() { return channel; }
    public LocalDate openedDate() { return openedDate; }
    public LaiDeadlines deadlines() { return deadlines; }
    public LocalDate originalResponseDue() { return originalResponseDue; }
    public LocalDate currentResponseDue() { return currentResponseDue; }
    public boolean extended() { return extended; }
    public String extensionJustification() { return extensionJustification; }
    public LocalDate answeredDate() { return answeredDate; }
    public AnswerOutcome answerOutcome() { return answerOutcome; }
    public LocalDate appealDate() { return appealDate; }
    public LocalDate appealDecisionDue() { return appealDecisionDue; }
    public LocalDate appealDecidedDate() { return appealDecidedDate; }
    public AppealOutcome appealOutcome() { return appealOutcome; }
}
