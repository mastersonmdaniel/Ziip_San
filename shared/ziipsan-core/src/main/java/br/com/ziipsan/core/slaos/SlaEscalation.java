package br.com.ziipsan.core.slaos;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Alerta de risco e escalonamento por eventos (II-4).
 *
 * <p>Dado o mesmo (abertura, configuracao, instante) do
 * {@link DualDeadlineClock}, produz a lista ORDENADA e deterministica de
 * eventos de SLA: alerta de risco por prazo (quando esse prazo entra em
 * AT_RISK), quebra por prazo (BREACHED) e escalonamento — o nivel e o
 * numero de degraus configurados ja cruzados pela maior fracao decorrida
 * entre os prazos configurados. A sequencia de niveis por tempo decorrido
 * e, portanto, monotona e reproduzivel: e funcao pura das entradas.
 *
 * <p>Os eventos sao REGISTO (o chamador persiste/publica); esta classe
 * nao envia notificacao nenhuma — canais e destinatarios sao fiacao.
 */
public final class SlaEscalation {

    public enum EventKind { RISK_ALERT, BREACH, ESCALATED }

    public record SlaEvent(EventKind kind, DualDeadlineClock.DeadlineKind deadlineOrNull,
                           int escalationLevel, Instant at) {
        public SlaEvent {
            Objects.requireNonNull(kind, "tipo do evento e obrigatorio");
            Objects.requireNonNull(at, "instante do evento e obrigatorio");
            if (kind == EventKind.ESCALATED && deadlineOrNull != null)
                throw new IllegalArgumentException("evento de escalonamento nao pertence a um prazo so");
            if (kind != EventKind.ESCALATED && deadlineOrNull == null)
                throw new IllegalArgumentException("alerta/quebra pertence a um prazo (interno ou regulador)");
            if (escalationLevel < 0) throw new IllegalArgumentException("nivel de escalonamento nao pode ser negativo");
        }
    }

    private SlaEscalation() {}

    /** Nivel de escalonamento: degraus configurados ja cruzados pela pior fracao. */
    public static int escalationLevel(Instant openedAt, TypeSlaConfig config, Instant now) {
        int worst = DualDeadlineClock.worstElapsedPercent(openedAt, config, now);
        if (worst < 0) return 0;
        int level = 0;
        for (int step : config.escalationThresholdsPercent()) {
            if (worst >= step) level++;
        }
        return level;
    }

    /** Eventos devidos no instante: risco/quebra por prazo, depois o escalonamento. */
    public static List<SlaEvent> eventsAt(Instant openedAt, TypeSlaConfig config, Instant now,
                                          DualDeadlineClock clock) {
        Objects.requireNonNull(clock, "relogio de duplo prazo e obrigatorio");
        DualDeadlineClock.DualAssessment a = clock.evaluate(openedAt, config, now);
        List<SlaEvent> out = new ArrayList<>();
        for (DualDeadlineClock.DeadlineAssessment d
                : List.of(a.internal(), a.regulator())) {
            if (d.state() == DualDeadlineClock.DeadlineState.AT_RISK) {
                out.add(new SlaEvent(EventKind.RISK_ALERT, d.kind(), 0, now));
            } else if (d.state() == DualDeadlineClock.DeadlineState.BREACHED) {
                out.add(new SlaEvent(EventKind.BREACH, d.kind(), 0, now));
            }
        }
        int level = escalationLevel(openedAt, config, now);
        if (level > 0) {
            out.add(new SlaEvent(EventKind.ESCALATED, null, level, now));
        }
        return List.copyOf(out);
    }
}
