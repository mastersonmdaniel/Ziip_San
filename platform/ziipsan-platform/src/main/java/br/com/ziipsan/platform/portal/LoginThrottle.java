package br.com.ziipsan.platform.portal;

import br.com.ziipsan.core.api.ApiException;

/**
 * Limite de tentativas de login do portal (P3.3): por identificador E por
 * IP, com atraso crescente e 429 ao estourar — SEM bloquear a conta da
 * vitima: a trava e atraso + recusa temporaria da tentativa, nunca lockout
 * que um atacante possa provocar de fora. Ao cruzar o limiar, o chamador
 * emite o alerta para a Central (sinal {@code centralAlert}).
 *
 * <p>Funcao pura sobre as contagens da janela (a persistencia das contagens
 * e dos adaptadores de borda, sobre {@code rate_limit_buckets} da core V5,
 * e da integracao).
 */
public final class LoginThrottle {

    public enum Decision { ALLOW, DELAY, TOO_MANY_REQUESTS }

    public record Verdict(Decision decision, long delayMillis, boolean centralAlert) { }

    private static final long BASE_DELAY_MILLIS = 250L;
    private static final long MAX_DELAY_MILLIS = 30_000L;

    private LoginThrottle() { }

    /**
     * @param identifierFailures falhas recentes do identificador na janela
     * @param ipFailures         falhas recentes do IP na janela
     * @param limits             {@code portal.account_throttle} vigente (ja conferido, classe c)
     */
    public static Verdict evaluate(int identifierFailures, int ipFailures,
                                   PortalParameters.AccountThrottle limits) {
        if (limits == null) {
            throw new IllegalArgumentException("limites (portal.account_throttle) sao obrigatorios");
        }
        if (identifierFailures < 0 || ipFailures < 0) {
            throw new IllegalArgumentException("contagens nao podem ser negativas");
        }
        int worst = Math.max(identifierFailures, ipFailures);
        // Alerta por dimensao (revisao cruzada da P3.3): cada contagem e
        // comparada ao SEU limite; o teto misto (min dos dois) disparava
        // alerta a Central muito antes de qualquer limite real.
        boolean alert = (identifierFailures > 0
                        && identifierFailures >= limits.maxLoginFailuresPerIdentifier() - 1)
                || (ipFailures > 0 && ipFailures >= limits.maxLoginFailuresPerIp() - 1);
        if (identifierFailures >= limits.maxLoginFailuresPerIdentifier()
                || ipFailures >= limits.maxLoginFailuresPerIp()) {
            return new Verdict(Decision.TOO_MANY_REQUESTS, 0L, true);
        }
        if (worst == 0) {
            return new Verdict(Decision.ALLOW, 0L, false);
        }
        long delay = BASE_DELAY_MILLIS;
        for (int i = 1; i < worst && delay < MAX_DELAY_MILLIS; i++) {
            delay = Math.min(MAX_DELAY_MILLIS, delay * 2);
        }
        return new Verdict(Decision.DELAY, delay, alert);
    }

    /** Converte o veredito de estouro na excecao HTTP 429 do padrao do produto. */
    public static void enforce(Verdict verdict) {
        if (verdict.decision() == Decision.TOO_MANY_REQUESTS) {
            throw new ApiException.TooManyRequests("Muitas tentativas. Aguarde e tente novamente.");
        }
    }
}
