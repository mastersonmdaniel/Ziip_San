package br.com.ziipsan.platform.accesscontrol.keycloak;

import br.com.ziipsan.platform.accesscontrol.LoginGuard;
import br.com.ziipsan.platform.accesscontrol.PasswordPolicyRule;
import br.com.ziipsan.platform.accesscontrol.TenantSecurityPolicy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Núcleo do autenticador próprio no Keycloak (ED-06, ADR-0040): num realm
 * SÓ, ele lê a política do tenant DO USUÁRIO que está autenticando e aplica
 * bloqueio por tentativas, expiração, histórico e tamanho — o Keycloak puro
 * aplicaria UMA política do realm a todos os tenants.
 *
 * <p>Esta classe é o núcleo TESTÁVEL, sem dependência da API do Keycloak: o
 * adaptador SPI (provider/factory, no pacote de implantação do Keycloak)
 * traduz o {@code AuthenticationFlowContext} para as portas abaixo e devolve
 * a {@link Outcome}. A separação é deliberada: a regressão "dois tenants,
 * políticas diferentes, mesmo realm" roda sobre ESTE núcleo a cada
 * atualização do Keycloak, sem subir o servidor (roteiro em
 * docs/seguranca desta entrega).
 *
 * <p>Ordem das checagens no login:
 * <ol>
 *   <li>SSO obrigatório do tenant recusa login local ANTES de qualquer
 *       contagem (não é falha de credencial);</li>
 *   <li>trava por IP: as falhas recentes do IP no tenant (de TODOS os
 *       usuários, inclusive inexistentes) atingindo o limiar
 *       {@code sec.lockout_attempts} do tenant negam com a MESMA resposta
 *       genérica, antes de qualquer verificação de credencial — é a trava
 *       do ataque concentrado (um IP, vários usuários); a janela da
 *       contagem é {@link #LOCK_DURATION};</li>
 *   <li>{@link LoginGuard#evaluate}: bloqueado/inativo/inexistente → a MESMA
 *       resposta genérica, sem verificar a senha (para inexistente, a porta
 *       de credencial é chamada em modo dummy só para equalizar o tempo do
 *       caminho — ver {@link CredentialPort});</li>
 *   <li>verificação da credencial pela porta (o segredo fica no Keycloak);</li>
 *   <li>falha: grava a tentativa (usuário + IP) e responde genérico; sucesso:
 *       zera os contadores do usuário E do IP, confere expiração da senha
 *       na política efetiva e abre a sessão ENCERRANDO a anterior (login
 *       único).</li>
 * </ol>
 */
public class TenantPolicyAuthenticator {

    /**
     * Duração do bloqueio ao atingir o limite — e janela da contagem de
     * falhas por IP. DEFAULT SEM FONTE no aceite/ADR (correção D6 da
     * revisão): é configuração de implantação PENDENTE DE CONFIRMAÇÃO do
     * dono; não é valor do plano.
     */
    static final Duration LOCK_DURATION = Duration.ofMinutes(30);

    public interface TenantPolicyPort {
        TenantSecurityPolicy policyOf(UUID tenantId);

        TenantSecurityPolicy effectivePolicyOf(UUID tenantId, Instant now);

        boolean ssoEnforcedFor(UUID tenantId);
    }

    public interface AttemptPort {
        LoginGuard.AttemptState stateOf(UUID tenantId, UUID userId);

        void saveState(UUID tenantId, UUID userId, LoginGuard.AttemptState state);

        /** Contagem por IP (a trava do ataque concentrado); devolve as falhas recentes do IP. */
        int recentFailuresByIp(UUID tenantId, String ipAddress, Instant since);

        void recordIpFailure(UUID tenantId, String ipAddress, Instant at);

        /** Sucesso zera também o contador do IP (o IP provou uma credencial válida). */
        void clearIpFailures(UUID tenantId, String ipAddress);
    }

    public interface CredentialPort {
        /** O usuário existe neste tenant? (inexistência responde genérico, nunca 404) */
        boolean userExists(UUID tenantId, String username);

        UUID userIdOf(UUID tenantId, String username);

        /**
         * Verifica a credencial. Com {@code userId} NULO é a verificação
         * DUMMY de equalização de tempo para usuário inexistente: o
         * adaptador SPI deve executar uma verificação de custo equivalente
         * (hash dummy) e o resultado é descartado pelo chamador. Sem isso, o
         * caminho do inexistente é mensuravelmente mais curto e a resposta
         * "indistinta" vaza existência por timing (limitação D7: o custo
         * real da dummy é do adaptador, pendência 1 do relatório).
         */
        boolean passwordMatches(UUID tenantId, UUID userId, String candidate);

        Instant passwordChangedAt(UUID tenantId, UUID userId);
    }

    public interface SessionPort {
        /** Abre a sessão e encerra a anterior do usuário (login único). */
        String openUniqueSession(UUID tenantId, UUID userId, Instant now);
    }

    /** Desfecho do login, já traduzido para a resposta indistinta quando falha. */
    public record Outcome(boolean authenticated, String code, String message, String sessionId) {
        static Outcome failure() {
            return new Outcome(false, LoginGuard.GENERIC_FAILURE_CODE,
                    LoginGuard.GENERIC_FAILURE_MESSAGE, null);
        }
    }

    private final TenantPolicyPort policies;
    private final AttemptPort attempts;
    private final CredentialPort credentials;
    private final SessionPort sessions;

    public TenantPolicyAuthenticator(TenantPolicyPort policies, AttemptPort attempts,
                                     CredentialPort credentials, SessionPort sessions) {
        if (policies == null || attempts == null || credentials == null || sessions == null) {
            throw new IllegalArgumentException("políticas, tentativas, credenciais e sessões são obrigatórias");
        }
        this.policies = policies;
        this.attempts = attempts;
        this.credentials = credentials;
        this.sessions = sessions;
    }

    public Outcome authenticate(UUID tenantId, String username, String candidatePassword,
                                String ipAddress, Instant now) {
        if (tenantId == null || username == null || now == null) {
            return Outcome.failure();
        }
        if (policies.ssoEnforcedFor(tenantId)) {
            return Outcome.failure();
        }
        TenantSecurityPolicy effective = policies.effectivePolicyOf(tenantId, now);
        // Trava por IP (correção D2): a contagem por IP é CONSUMIDA aqui —
        // falhas recentes deste IP no tenant, somadas de todos os usuários
        // (inclusive tentativas contra usuários inexistentes, que também são
        // gravadas), atingindo o limiar sec.lockout_attempts DO TENANT negam
        // com a resposta genérica. Nenhum limiar novo foi inventado para o
        // IP: é o parâmetro de tentativas do tenant, e a janela é
        // LOCK_DURATION (a constante temporal que o pacote já tinha).
        if (ipAddress != null && effective != null
                && attempts.recentFailuresByIp(tenantId, ipAddress, now.minus(LOCK_DURATION))
                        >= effective.lockoutAttempts()) {
            return Outcome.failure();
        }
        boolean exists = credentials.userExists(tenantId, username);
        if (!exists) {
            // Equalização de tempo (D7): chamar a porta também para o
            // inexistente (userId nulo = dummy no adaptador) para o caminho
            // não ser mais curto que o de senha errada; resultado descartado.
            credentials.passwordMatches(tenantId, null, candidatePassword);
        }
        UUID userId = exists ? credentials.userIdOf(tenantId, username) : null;
        LoginGuard.AttemptState state = exists ? attempts.stateOf(tenantId, userId) : LoginGuard.AttemptState.NONE;
        if (!LoginGuard.evaluate(exists, state, effective, now).proceed()) {
            if (ipAddress != null) {
                attempts.recordIpFailure(tenantId, ipAddress, now);
            }
            return Outcome.failure();
        }
        if (!credentials.passwordMatches(tenantId, userId, candidatePassword)) {
            attempts.saveState(tenantId, userId,
                    LoginGuard.onFailure(state, effective, now, LOCK_DURATION));
            if (ipAddress != null) {
                attempts.recordIpFailure(tenantId, ipAddress, now);
            }
            return Outcome.failure();
        }
        if (PasswordPolicyRule.isExpired(effective,
                credentials.passwordChangedAt(tenantId, userId), now)) {
            // Credencial certa, senha expirada na política DO TENANT: o fluxo
            // do Keycloak desvia para a troca obrigatória — nunca autentica.
            return new Outcome(false, "password-expired",
                    "A senha expirou na política deste tenant; defina uma nova senha.", null);
        }
        attempts.saveState(tenantId, userId, LoginGuard.onSuccess(now));
        if (ipAddress != null) {
            attempts.clearIpFailures(tenantId, ipAddress);
        }
        String sessionId = sessions.openUniqueSession(tenantId, userId, now);
        return new Outcome(true, "ok", "Autenticado.", sessionId);
    }
}
