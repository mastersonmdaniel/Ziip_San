package br.com.ziipsan.core.slaos;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Relogio de SLA com DUPLO prazo (II-4): prazo interno e prazo do
 * regulador, avaliados de forma independente sobre a mesma abertura da OS.
 *
 * <p>Determinismo: o instante de referencia entra por parametro ou pelo
 * {@link Clock} injectado no construtor — os testes fixam o relogio e o
 * resultado e funcao pura de (abertura, configuracao, instante).
 *
 * <p>Estados por prazo: {@code WITHIN} (dentro, antes do limiar de risco),
 * {@code AT_RISK} (fração decorrida >= limiar configurado, fronteira
 * INCLUIDA, e ainda nao vencido), {@code BREACHED} (instante >= vencimento)
 * e {@code NOT_CONFIGURED} (prazo {@code [A DEFINIR]} — nenhum estado de
 * cumprimento e afirmado sobre um prazo que a fonte nao fixou).
 *
 * <p>A comparacao do limiar usa aritmetica inteira sobre segundos
 * (decorrido*100 >= limiar*total), sem ponto flutuante.
 *
 * <p>Esta classe NAO altera a maquina de estados da P1.31: e um relogio
 * de leitura sobre a OS, acoplado por composicao.
 */
public final class DualDeadlineClock {

    public enum DeadlineKind { INTERNAL, REGULATOR }

    public enum DeadlineState { WITHIN, AT_RISK, BREACHED, NOT_CONFIGURED }

    public record DeadlineAssessment(DeadlineKind kind, DeadlineState state,
                                     Instant dueAtOrNull, long elapsedSeconds) {
        public boolean breached() { return state == DeadlineState.BREACHED; }
        public boolean atRisk() { return state == DeadlineState.AT_RISK; }
    }

    public record DualAssessment(DeadlineAssessment internal, DeadlineAssessment regulator) {
        public boolean anyBreached() { return internal.breached() || regulator.breached(); }
        public boolean anyAtRisk() { return internal.atRisk() || regulator.atRisk(); }
    }

    private final Clock clock;

    public DualDeadlineClock(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "relogio injectavel e obrigatorio");
    }

    /** Avaliacao no instante do relogio injectado. */
    public DualAssessment evaluateNow(Instant openedAt, TypeSlaConfig config) {
        return evaluate(openedAt, config, clock.instant());
    }

    public DualAssessment evaluate(Instant openedAt, TypeSlaConfig config, Instant now) {
        Objects.requireNonNull(openedAt, "abertura da OS e obrigatoria");
        Objects.requireNonNull(config, "configuracao de SLA do tipo e obrigatoria");
        Objects.requireNonNull(now, "instante de referencia e obrigatorio");
        return new DualAssessment(
                assess(DeadlineKind.INTERNAL, config.internalDeadlineHours(), openedAt, config, now),
                assess(DeadlineKind.REGULATOR, config.regulatorDeadlineHours(), openedAt, config, now));
    }

    private DeadlineAssessment assess(DeadlineKind kind, Integer hours, Instant openedAt,
                                      TypeSlaConfig config, Instant now) {
        long elapsed = Math.max(0, Duration.between(openedAt, now).getSeconds());
        if (hours == null) {
            return new DeadlineAssessment(kind, DeadlineState.NOT_CONFIGURED, null, elapsed);
        }
        long total = hours * 3600L;
        Instant dueAt = openedAt.plusSeconds(total);
        DeadlineState state;
        if (elapsed >= total) {
            state = DeadlineState.BREACHED;
        } else if (elapsed * 100 >= (long) config.riskThresholdPercent() * total) {
            state = DeadlineState.AT_RISK;
        } else {
            state = DeadlineState.WITHIN;
        }
        return new DeadlineAssessment(kind, state, dueAt, elapsed);
    }

    /**
     * Maior fracao decorrida (em % inteira, piso) entre os prazos
     * CONFIGURADOS; -1 quando nenhum prazo esta configurado. Base do
     * escalonamento deterministico de {@link SlaEscalation}.
     */
    public static int worstElapsedPercent(Instant openedAt, TypeSlaConfig config, Instant now) {
        long elapsed = Math.max(0, Duration.between(openedAt, now).getSeconds());
        int worst = -1;
        for (Integer hours : new Integer[]{config.internalDeadlineHours(), config.regulatorDeadlineHours()}) {
            if (hours == null) continue;
            long total = hours * 3600L;
            int pct = (int) (elapsed * 100 / total);
            if (pct > worst) worst = pct;
        }
        return worst;
    }
}
