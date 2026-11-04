package br.com.ziipsan.integration.webhook;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Recuo exponencial com jitter das tentativas de entrega (P2.26).
 *
 * <p>Base de 60 s, dobrando a cada tentativa, teto de 1 h, mais jitter
 * uniforme de 0–30% para não sincronizar reenvios de vários tenants no
 * mesmo segundo. Função pura (sem relógio nem I/O) para ser testável sem
 * banco: o teste fixa o jitter em zero através de
 * {@link #delayWithoutJitter(int)}.
 */
public final class WebhookBackoff {

    public static final Duration BASE = Duration.ofSeconds(60);
    public static final Duration MAX = Duration.ofHours(1);

    private WebhookBackoff() {
    }

    /** Atraso até a próxima tentativa, sem jitter (determinístico, para teste). */
    public static Duration delayWithoutJitter(int failedAttempts) {
        if (failedAttempts <= 0) {
            return BASE;
        }
        long seconds = BASE.getSeconds() << Math.min(failedAttempts - 1, 6);
        return Duration.ofSeconds(Math.min(seconds, MAX.getSeconds()));
    }

    /** Atraso até a próxima tentativa, com jitter de 0–30%. */
    public static Duration nextDelay(int failedAttempts) {
        Duration base = delayWithoutJitter(failedAttempts);
        long jitter = ThreadLocalRandom.current().nextLong(0, base.getSeconds() * 30 / 100 + 1);
        return base.plusSeconds(jitter);
    }
}
