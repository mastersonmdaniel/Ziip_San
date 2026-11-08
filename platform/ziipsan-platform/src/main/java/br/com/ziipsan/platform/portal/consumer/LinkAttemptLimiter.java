package br.com.ziipsan.platform.portal.consumer;

import br.com.ziipsan.core.api.ApiException;

import java.time.Duration;

/**
 * Limite de tentativas do VINCULO (P3.20, ADR-0008): contagens por unidade
 * (conta), por IP e por tenant na janela, com BLOQUEIO PROGRESSIVO da
 * dimensao que estourou (nunca da conta do portal da vitima: o bloqueio e
 * da combinacao que tenta, por tempo crescente). Ao cruzar o limiar de
 * alerta, o chamador registra a tentativa e emite o alerta de volume a
 * Central. Funcao pura sobre as contagens; a persistencia e das portas.
 */
public final class LinkAttemptLimiter {

    public enum Decision { ALLOW, BLOCKED }

    public record Verdict(Decision decision, Duration blockFor, boolean volumeAlert) { }

    /** Limites do vinculo; valores vem da integracao (fabrica do tenant), nunca inventados aqui. */
    public record Limits(int maxAttemptsPerUnit, int maxAttemptsPerIp, int maxAttemptsPerTenant,
                         int alertThreshold) {
        public Limits {
            if (maxAttemptsPerUnit <= 0 || maxAttemptsPerIp <= 0 || maxAttemptsPerTenant <= 0
                    || alertThreshold <= 0) {
                throw new IllegalArgumentException("limites de tentativa devem ser positivos");
            }
        }
    }

    private static final Duration BASE_BLOCK = Duration.ofMinutes(1);
    private static final Duration MAX_BLOCK = Duration.ofHours(24);

    private LinkAttemptLimiter() { }

    public static Verdict evaluate(int unitAttempts, int ipAttempts, int tenantAttempts, Limits limits) {
        if (limits == null) {
            throw new IllegalArgumentException("limites sao obrigatorios");
        }
        if (unitAttempts < 0 || ipAttempts < 0 || tenantAttempts < 0) {
            throw new IllegalArgumentException("contagens nao podem ser negativas");
        }
        boolean alert = unitAttempts >= limits.alertThreshold() || ipAttempts >= limits.alertThreshold()
                || tenantAttempts >= limits.alertThreshold();
        int overUnit = unitAttempts - limits.maxAttemptsPerUnit();
        int overIp = ipAttempts - limits.maxAttemptsPerIp();
        int overTenant = tenantAttempts - limits.maxAttemptsPerTenant();
        int worstOver = Math.max(overUnit, Math.max(overIp, overTenant));
        if (worstOver < 0) {
            return new Verdict(Decision.ALLOW, Duration.ZERO, alert);
        }
        // Bloqueio progressivo: dobra a cada estouro alem do limite, com teto.
        Duration block = BASE_BLOCK;
        for (int i = 0; i < worstOver && block.compareTo(MAX_BLOCK) < 0; i++) {
            block = block.multipliedBy(2);
            if (block.compareTo(MAX_BLOCK) > 0) {
                block = MAX_BLOCK;
            }
        }
        return new Verdict(Decision.BLOCKED, block, true);
    }

    public static void enforce(Verdict verdict) {
        if (verdict.decision() == Decision.BLOCKED) {
            throw new ApiException.TooManyRequests("Muitas tentativas de vinculo. Aguarde e tente novamente.");
        }
    }
}
