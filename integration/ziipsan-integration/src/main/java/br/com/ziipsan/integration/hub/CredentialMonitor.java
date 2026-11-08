package br.com.ziipsan.integration.hub;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * P3.37: situação de expiração de credencial e o alerta que dela sai.
 * Janela de alerta padrão: 30 dias (constante nomeada; a fiação pode
 * usar outra ao construir a avaliação). Credencial sem expiração
 * declarada é {@link ExpiryStatus#NO_EXPIRY_DECLARED} — situação
 * explícita, nunca "válida para sempre" presumida em silêncio no painel.
 *
 * <p>Fronteira inclusiva: expirando exatamente na borda da janela já é
 * {@code EXPIRING} (o alerta sai no dia em que faltam 30 dias, não no
 * dia seguinte).
 */
public final class CredentialMonitor {

    public enum ExpiryStatus { VALID, EXPIRING, EXPIRED, NO_EXPIRY_DECLARED, NOT_FOUND }

    public static final Duration DEFAULT_ALERT_WINDOW = Duration.ofDays(30);

    private CredentialMonitor() {
    }

    public static ExpiryStatus statusAt(CredentialVault.CredentialMetadata metadata, Instant now, Duration alertWindow) {
        Objects.requireNonNull(now, "instante é obrigatório na avaliação de credencial");
        Objects.requireNonNull(alertWindow, "janela de alerta é obrigatória");
        if (alertWindow.isNegative()) {
            throw new IllegalArgumentException("janela de alerta não pode ser negativa: " + alertWindow);
        }
        if (metadata == null) {
            return ExpiryStatus.NOT_FOUND;
        }
        Instant expiresAt = metadata.expiresAt();
        if (expiresAt == null) {
            return ExpiryStatus.NO_EXPIRY_DECLARED;
        }
        if (!expiresAt.isAfter(now)) {
            return ExpiryStatus.EXPIRED;
        }
        return expiresAt.isAfter(now.plus(alertWindow)) ? ExpiryStatus.VALID : ExpiryStatus.EXPIRING;
    }

    /** O painel deve alertar? (expirando ou já expirada; não encontrada também alerta — é ausência de credencial). */
    public static boolean requiresAlert(ExpiryStatus status) {
        return status == ExpiryStatus.EXPIRING || status == ExpiryStatus.EXPIRED || status == ExpiryStatus.NOT_FOUND;
    }
}
