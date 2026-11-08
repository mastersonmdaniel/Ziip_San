package br.com.ziipsan.platform.tenantalert;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * ED-07/P2.26: a política de entrega confiável do webhook — tentativas com
 * recuo exponencial e jitter, tolerância do carimbo contra repetição e
 * desativação automática depois de falhas consecutivas (aceite da P2.26).
 *
 * <p><b>Nenhum valor nasce aqui.</b> O aceite da P2.26 manda o comportamento
 * mas não fixa números (nem tentativas, nem limiar de desativação): todos são
 * exigidos no construtor e quem os fornece é a fiação, a partir dos
 * parâmetros do tenant da P2.26 ({@code api.webhooks_enabled} e cia.) e da
 * política da plataforma. Fingir um padrão seria inventar regra de produto.
 *
 * <p>Recuo da repetição {@code n} (1 = primeira repetição):
 * {@code min(base * 2^(n-1), teto) + jitter}, com jitter em
 * {@code [0, base)} sorteado pela fonte injetada — determinístico em teste,
 * aleatório na fiação.
 */
public record WebhookDeliveryPolicy(int maxAttempts, Duration baseBackoff, Duration maxBackoff,
                                    int consecutiveFailuresToDisable, Duration replayTolerance) {
    public WebhookDeliveryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("a política exige ao menos 1 tentativa: " + maxAttempts);
        }
        Objects.requireNonNull(baseBackoff, "o recuo base é obrigatório");
        Objects.requireNonNull(maxBackoff, "o teto do recuo é obrigatório");
        if (baseBackoff.isNegative() || maxBackoff.compareTo(baseBackoff) < 0) {
            throw new IllegalArgumentException("recuo inválido: base=" + baseBackoff + " teto=" + maxBackoff);
        }
        if (consecutiveFailuresToDisable < 1) {
            throw new IllegalArgumentException("o limiar de desativação deve ser >= 1: " + consecutiveFailuresToDisable);
        }
        Objects.requireNonNull(replayTolerance, "a tolerância de repetição é obrigatória");
        if (replayTolerance.isZero() || replayTolerance.isNegative()) {
            throw new IllegalArgumentException("a tolerância de repetição deve ser positiva: " + replayTolerance);
        }
    }

    public boolean hasAnotherAttempt(int attemptsMade) {
        if (attemptsMade < 1) {
            throw new IllegalArgumentException("tentativas feitas deve ser >= 1: " + attemptsMade);
        }
        return attemptsMade < maxAttempts;
    }

    public Duration backoffForRetry(int retryNumber, LongSupplier jitterMillisSource) {
        if (retryNumber < 1) {
            throw new IllegalArgumentException("número da repetição deve ser >= 1: " + retryNumber);
        }
        Objects.requireNonNull(jitterMillisSource, "a fonte de jitter é obrigatória");
        long factor = 1L << Math.min(retryNumber - 1, 20);
        Duration backoff = baseBackoff.multipliedBy(factor);
        if (backoff.compareTo(maxBackoff) > 0) {
            backoff = maxBackoff;
        }
        long bound = Math.max(1L, baseBackoff.toMillis());
        long jitter = Math.floorMod(jitterMillisSource.getAsLong(), bound);
        return backoff.plusMillis(jitter);
    }
}
