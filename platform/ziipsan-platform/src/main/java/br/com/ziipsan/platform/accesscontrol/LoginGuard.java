package br.com.ziipsan.platform.accesscontrol;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Tentativas de login, bloqueio e sessão única (ED-06; roteiro VISAN
 * POC-04 a POC-07, alínea b). Regras puras sobre o estado que a loja mantém:
 * <ul>
 *   <li>tentativas contadas NO SERVIDOR, por usuário E por IP — contar só no
 *       cliente ou só por IP deixaria o ataque distribuído (vários IPs, um
 *       usuário) e o ataque concentrado (um IP, vários usuários) sem trava;</li>
 *   <li>atingido {@code sec.lockout_attempts} do tenant, o usuário BLOQUEIA
 *       (no POC-05 do ensaio, o tenant usa 3 — valor de parâmetro, não
 *       constante);</li>
 *   <li>a RESPOSTA é INDISTINTA: usuário inexistente, senha errada, conta
 *       bloqueada e conta inativa devolvem o MESMO código e a MESMA mensagem
 *       ({@link #GENERIC_FAILURE}) — distinguir vazaria existência e estado
 *       da conta a quem não se autenticou;</li>
 *   <li>LOGIN ÚNICO por usuário: uma nova sessão válida ENCERRA a anterior
 *       (POC do roteiro); a sessão anterior não coexiste;</li>
 *   <li>sucesso zera o contador de falhas do usuário e do IP.</li>
 * </ul>
 * O autenticador do Keycloak chama {@link #evaluate} ANTES de concluir o
 * login e {@link #onSuccess}/{@link #onFailure} para gravar o desfecho.
 */
public final class LoginGuard {

    /** A única resposta de falha visível a quem tenta entrar. */
    public static final String GENERIC_FAILURE_CODE = "invalid-credentials";
    public static final String GENERIC_FAILURE_MESSAGE = "Usuário ou senha inválidos.";

    /** Estado de tentativas de um usuário (na loja, por tenant). */
    public record AttemptState(int consecutiveFailures, Instant lockedUntil, Instant lastLoginAt) {
        public static final AttemptState NONE = new AttemptState(0, null, null);
    }

    /** Decisão pré-login: prosseguir à verificação de credencial, ou negar já. */
    public record Decision(boolean proceed, String code, String message) {
        static Decision allow() {
            return new Decision(true, "ok", "Prosseguir.");
        }

        static Decision denyGeneric() {
            return new Decision(false, GENERIC_FAILURE_CODE, GENERIC_FAILURE_MESSAGE);
        }
    }

    private LoginGuard() {
    }

    /**
     * Pré-checagem: conta bloqueada por tentativas ou inativa além do limite
     * nega SEM revelar o motivo (resposta genérica). {@code userExists=false}
     * também nega genérico — a contagem por IP continua valendo para o chamador.
     */
    public static Decision evaluate(boolean userExists, AttemptState state,
                                    TenantSecurityPolicy effective, Instant now) {
        Objects.requireNonNull(now, "instante é obrigatório");
        if (!userExists) {
            return Decision.denyGeneric();
        }
        AttemptState s = state == null ? AttemptState.NONE : state;
        if (s.lockedUntil() != null && s.lockedUntil().isAfter(now)) {
            return Decision.denyGeneric();
        }
        if (effective != null && PasswordPolicyRule.isInactiveBeyondLimit(effective, s.lastLoginAt(), now)) {
            return Decision.denyGeneric();
        }
        return Decision.allow();
    }

    /** Novo estado após UMA falha: incrementa; no limite do tenant, bloqueia. */
    public static AttemptState onFailure(AttemptState state, TenantSecurityPolicy effective,
                                         Instant now, java.time.Duration lockDuration) {
        Objects.requireNonNull(effective, "política é obrigatória");
        Objects.requireNonNull(now, "instante é obrigatório");
        Objects.requireNonNull(lockDuration, "duração do bloqueio é obrigatória");
        AttemptState s = state == null ? AttemptState.NONE : state;
        int failures = s.consecutiveFailures() + 1;
        Instant lockedUntil = failures >= effective.lockoutAttempts() ? now.plus(lockDuration) : s.lockedUntil();
        return new AttemptState(failures, lockedUntil, s.lastLoginAt());
    }

    /** Novo estado após sucesso: zera falhas e bloqueio, marca o login. */
    public static AttemptState onSuccess(Instant now) {
        Objects.requireNonNull(now, "instante é obrigatório");
        return new AttemptState(0, null, now);
    }

    /**
     * Sessão ativa de um usuário (login único): ao abrir {@code newSessionId},
     * qualquer outra sessão do MESMO usuário deixa de valer.
     */
    public record ActiveSession(UUID userId, String sessionId, Instant openedAt) {
    }

    public static boolean isCurrentSession(ActiveSession active, UUID userId, String sessionId) {
        return active != null && active.userId().equals(userId) && active.sessionId().equals(sessionId);
    }
}
