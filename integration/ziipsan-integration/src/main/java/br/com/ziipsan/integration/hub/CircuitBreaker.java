package br.com.ziipsan.integration.hub;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * P3.37: circuito aberto por integração. Depois de
 * {@code failureThreshold} falhas consecutivas, o circuito ABRE e o hub
 * recusa novas chamadas por {@code openDuration} — sem tocar o parceiro
 * que já está falhando. Passada a duração, o circuito fica MEIO-ABERTO:
 * uma chamada de prova passa; sucesso fecha, falha reabre por outra
 * duração inteira.
 *
 * <p>Determinístico: todos os métodos recebem o instante; quem chama
 * passa o relógio. Uso por (tenant, integração) — a fiação mantém uma
 * instância por par; esta classe não é sincronizada porque o despacho
 * do hub é a fronteira que serializa por par na fiação.
 */
public final class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final Duration openDuration;
    private int consecutiveFailures;
    private Instant openedAt;

    public CircuitBreaker(int failureThreshold, Duration openDuration) {
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("limiar de falhas do circuito deve ser >= 1: " + failureThreshold);
        }
        if (openDuration == null || openDuration.isZero() || openDuration.isNegative()) {
            throw new IllegalArgumentException("duração de circuito aberto deve ser positiva: " + openDuration);
        }
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
    }

    /** Padrão do hub: 5 falhas consecutivas abrem o circuito por 60 s. */
    public static CircuitBreaker hubDefault() {
        return new CircuitBreaker(5, Duration.ofSeconds(60));
    }

    public State stateAt(Instant now) {
        Objects.requireNonNull(now, "instante é obrigatório no circuito");
        if (openedAt == null) {
            return State.CLOSED;
        }
        return now.isBefore(openedAt.plus(openDuration)) ? State.OPEN : State.HALF_OPEN;
    }

    /** A chamada pode passar neste instante? (Em MEIO-ABERTO, é a chamada de prova.) */
    public boolean allowRequest(Instant now) {
        return stateAt(now) != State.OPEN;
    }

    /** Sucesso: zera as falhas consecutivas e fecha o circuito. */
    public void recordSuccess() {
        consecutiveFailures = 0;
        openedAt = null;
    }

    /** Falha: em MEIO-ABERTO reabre na hora; fechado, abre ao atingir o limiar. */
    public void recordFailure(Instant now) {
        Objects.requireNonNull(now, "instante é obrigatório no circuito");
        if (openedAt != null) {
            // Já estava aberto/meio-aberto: a prova falhou — reabre por outra duração.
            openedAt = now;
            consecutiveFailures = failureThreshold;
            return;
        }
        consecutiveFailures++;
        if (consecutiveFailures >= failureThreshold) {
            openedAt = now;
        }
    }

    public int consecutiveFailures() {
        return consecutiveFailures;
    }

    /** Instante em que o circuito abriu (vazio se fechado). */
    public Instant openedAt() {
        return openedAt;
    }
}
