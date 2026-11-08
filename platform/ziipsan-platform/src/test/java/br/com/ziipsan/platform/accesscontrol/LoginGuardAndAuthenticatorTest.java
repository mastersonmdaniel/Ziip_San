package br.com.ziipsan.platform.accesscontrol;

import br.com.ziipsan.platform.accesscontrol.keycloak.TenantPolicyAuthenticator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VISAN POC-04 a POC-07 (alínea b) no núcleo: bloqueio em 3 tentativas com a
 * política DO TENANT do ensaio, resposta indistinta, login único, trava por
 * IP e — a prova repetida a cada atualização do Keycloak — DOIS tenants com
 * políticas diferentes no MESMO realm/núcleo.
 *
 * <p>Correção D1: a fixture chaveia TUDO por (tenantId, userId) — estado de
 * tentativas, credenciais e sessões — e os dois tenants têm usuários
 * distintos (mesmo username, ids e senhas diferentes). A versão anterior
 * chaveava só por userId e só exercitava o tenant B: um vazamento de estado
 * entre tenants passaria verde. Agora o cenário divergente roda no MESMO
 * mundo para os dois tenants.
 */
class LoginGuardAndAuthenticatorTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");
    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();
    private static final UUID USER_A = UUID.randomUUID();
    private static final UUID USER_B = UUID.randomUUID();
    private static final String PASSWORD_A = "senha-correta-do-tenant-a";
    private static final String PASSWORD_B = "senha-correta-do-tenant-b";

    /** Chave (tenant, usuário): nada é compartilhado entre tenants no fixture. */
    private record TenantUser(UUID tenantId, UUID userId) {
    }

    /** Mundo em memória: políticas, tentativas, IPs, credenciais e sessões POR TENANT. */
    private static final class World implements TenantPolicyAuthenticator.TenantPolicyPort,
            TenantPolicyAuthenticator.AttemptPort, TenantPolicyAuthenticator.CredentialPort,
            TenantPolicyAuthenticator.SessionPort {
        final Map<UUID, TenantSecurityPolicy> policies = new HashMap<>();
        final Map<TenantUser, LoginGuard.AttemptState> states = new HashMap<>();
        final Map<TenantUser, String> sessions = new HashMap<>();
        final Map<String, UUID> userIds = new HashMap<>();
        final Map<TenantUser, String> passwords = new HashMap<>();
        final Map<String, List<Instant>> ipFailures = new HashMap<>();
        Instant changedAt = NOW;

        World() {
            userIds.put(TENANT_A + "|user", USER_A);
            userIds.put(TENANT_B + "|user", USER_B);
            passwords.put(new TenantUser(TENANT_A, USER_A), PASSWORD_A);
            passwords.put(new TenantUser(TENANT_B, USER_B), PASSWORD_B);
        }

        private static String ipKey(UUID tenantId, String ip) {
            return tenantId + "|" + ip;
        }

        int failuresOf(UUID tenantId, UUID userId) {
            return states.getOrDefault(new TenantUser(tenantId, userId),
                    LoginGuard.AttemptState.NONE).consecutiveFailures();
        }

        int ipFailureCount(UUID tenantId, String ip, Instant since) {
            return (int) ipFailures.getOrDefault(ipKey(tenantId, ip), List.of()).stream()
                    .filter(at -> !at.isBefore(since)).count();
        }

        @Override public TenantSecurityPolicy policyOf(UUID tenantId) { return policies.get(tenantId); }
        @Override public TenantSecurityPolicy effectivePolicyOf(UUID tenantId, Instant now) {
            return policies.get(tenantId);
        }
        @Override public boolean ssoEnforcedFor(UUID tenantId) { return false; }
        @Override public LoginGuard.AttemptState stateOf(UUID tenantId, UUID userId) {
            return states.getOrDefault(new TenantUser(tenantId, userId), LoginGuard.AttemptState.NONE);
        }
        @Override public void saveState(UUID tenantId, UUID userId, LoginGuard.AttemptState s) {
            states.put(new TenantUser(tenantId, userId), s);
        }
        @Override public int recentFailuresByIp(UUID tenantId, String ip, Instant since) {
            return ipFailureCount(tenantId, ip, since);
        }
        @Override public void recordIpFailure(UUID tenantId, String ip, Instant at) {
            ipFailures.computeIfAbsent(ipKey(tenantId, ip), k -> new ArrayList<>()).add(at);
        }
        @Override public void clearIpFailures(UUID tenantId, String ip) {
            ipFailures.remove(ipKey(tenantId, ip));
        }
        @Override public boolean userExists(UUID tenantId, String username) {
            return userIds.containsKey(tenantId + "|" + username);
        }
        @Override public UUID userIdOf(UUID tenantId, String username) {
            return userIds.get(tenantId + "|" + username);
        }
        @Override public boolean passwordMatches(UUID tenantId, UUID userId, String candidate) {
            // userId nulo = verificação dummy de equalização de tempo (D7).
            if (userId == null) {
                return false;
            }
            return passwords.getOrDefault(new TenantUser(tenantId, userId), "").equals(candidate);
        }
        @Override public Instant passwordChangedAt(UUID tenantId, UUID userId) { return changedAt; }
        @Override public String openUniqueSession(UUID tenantId, UUID userId, Instant now) {
            String id = "sess-" + UUID.randomUUID();
            sessions.put(new TenantUser(tenantId, userId), id);
            return id;
        }
    }

    private static TenantPolicyAuthenticator authenticator(World world) {
        return new TenantPolicyAuthenticator(world, world, world, world);
    }

    @Test
    @DisplayName("POC-05: bloqueio em 3 tentativas vem do parâmetro do tenant, não de constante")
    void lockoutAtTenantThreshold() {
        World world = new World();
        world.policies.put(TENANT_A, new TenantSecurityPolicy(15, null, 0, 3, null, false, false));
        var auth = authenticator(world);
        assertThat(auth.authenticate(TENANT_A, "user", "errada", "10.0.0.1", NOW).authenticated()).isFalse();
        assertThat(auth.authenticate(TENANT_A, "user", "errada", "10.0.0.1", NOW).authenticated()).isFalse();
        assertThat(auth.authenticate(TENANT_A, "user", "errada", "10.0.0.1", NOW).authenticated()).isFalse();
        // Bloqueado: nem a senha CERTA entra durante o bloqueio.
        var blocked = auth.authenticate(TENANT_A, "user", PASSWORD_A, "10.0.0.1", NOW.plusSeconds(60));
        assertThat(blocked.authenticated()).isFalse();
        assertThat(blocked.code()).isEqualTo(LoginGuard.GENERIC_FAILURE_CODE);
    }

    @Test
    @DisplayName("dois tenants, políticas diferentes, mesmo realm: limite, estado e credencial não vazam")
    void twoTenantsDifferentPolicies() {
        World world = new World();
        world.policies.put(TENANT_A, new TenantSecurityPolicy(15, null, 0, 3, null, false, false));
        world.policies.put(TENANT_B, new TenantSecurityPolicy(15, null, 0, 5, null, false, false));
        var auth = authenticator(world);

        // Tenant A (limite 3): 3 falhas bloqueiam — nem a senha certa de A entra.
        for (int i = 0; i < 3; i++) {
            assertThat(auth.authenticate(TENANT_A, "user", "errada", "10.0.1.1", NOW)
                    .authenticated()).isFalse();
        }
        var blockedA = auth.authenticate(TENANT_A, "user", PASSWORD_A, "10.0.1.1", NOW.plusSeconds(30));
        assertThat(blockedA.authenticated()).isFalse();
        assertThat(blockedA.code()).isEqualTo(LoginGuard.GENERIC_FAILURE_CODE);

        // Tenant B (limite 5), MESMO mundo: as MESMAS 3 falhas não bloqueiam,
        // e as falhas de A não vazaram para o estado de B.
        for (int i = 0; i < 3; i++) {
            assertThat(auth.authenticate(TENANT_B, "user", "errada", "10.0.2.2", NOW)
                    .authenticated()).isFalse();
        }
        assertThat(world.failuresOf(TENANT_B, USER_B)).isEqualTo(3);
        assertThat(auth.authenticate(TENANT_B, "user", PASSWORD_B, "10.0.2.2", NOW.plusSeconds(30))
                .authenticated()).isTrue();

        // O sucesso em B NÃO zerou o estado de A nem desbloqueou A.
        assertThat(world.failuresOf(TENANT_B, USER_B)).isZero();
        assertThat(world.failuresOf(TENANT_A, USER_A)).isEqualTo(3);
        assertThat(auth.authenticate(TENANT_A, "user", PASSWORD_A, "10.0.1.1", NOW.plusSeconds(60))
                .authenticated()).isFalse();

        // Credencial não vaza: a senha de A não autentica em B.
        assertThat(auth.authenticate(TENANT_B, "user", PASSWORD_A, "10.0.2.2", NOW.plusSeconds(90))
                .authenticated()).isFalse();
    }

    @Test
    @DisplayName("trava por IP: falhas somadas de vários usuários bloqueiam o IP; sucesso zera o contador do IP")
    void ipLockoutAndClearOnSuccess() {
        World world = new World();
        world.policies.put(TENANT_A, new TenantSecurityPolicy(15, null, 0, 3, null, false, false));
        var auth = authenticator(world);
        Instant since = NOW.minus(Duration.ofMinutes(30));

        // Ataque concentrado: 3 falhas do MESMO IP contra usuário INEXISTENTE
        // (nenhuma conta individual atinge o limite; o IP atinge).
        for (int i = 0; i < 3; i++) {
            assertThat(auth.authenticate(TENANT_A, "fantasma", "qualquer", "10.0.0.7", NOW)
                    .authenticated()).isFalse();
        }
        assertThat(world.ipFailureCount(TENANT_A, "10.0.0.7", since)).isEqualTo(3);
        // Do IP bloqueado, nem a credencial CERTA do usuário real entra.
        var fromBlockedIp = auth.authenticate(TENANT_A, "user", PASSWORD_A, "10.0.0.7", NOW.plusSeconds(30));
        assertThat(fromBlockedIp.authenticated()).isFalse();
        assertThat(fromBlockedIp.code()).isEqualTo(LoginGuard.GENERIC_FAILURE_CODE);
        // De outro IP, a mesma credencial entra: a trava é do IP, não do usuário.
        assertThat(auth.authenticate(TENANT_A, "user", PASSWORD_A, "10.0.0.8", NOW.plusSeconds(30))
                .authenticated()).isTrue();

        // Sucesso zera o contador do IP: 2 falhas + sucesso + 2 falhas NÃO
        // bloqueiam (sem a limpeza seriam 4 ≥ 3 e bloqueariam).
        String ip = "10.0.0.9";
        auth.authenticate(TENANT_A, "user", "errada", ip, NOW.plusSeconds(60));
        auth.authenticate(TENANT_A, "user", "errada", ip, NOW.plusSeconds(60));
        assertThat(world.ipFailureCount(TENANT_A, ip, since)).isEqualTo(2);
        assertThat(auth.authenticate(TENANT_A, "user", PASSWORD_A, ip, NOW.plusSeconds(90))
                .authenticated()).isTrue();
        assertThat(world.ipFailureCount(TENANT_A, ip, since)).isZero();
        auth.authenticate(TENANT_A, "user", "errada", ip, NOW.plusSeconds(120));
        auth.authenticate(TENANT_A, "user", "errada", ip, NOW.plusSeconds(120));
        assertThat(auth.authenticate(TENANT_A, "user", PASSWORD_A, ip, NOW.plusSeconds(150))
                .authenticated()).isTrue();
    }

    @Test
    @DisplayName("resposta indistinta: inexistente, senha errada e bloqueado têm o mesmo código e mensagem")
    void indistinguishableFailures() {
        World world = new World();
        world.policies.put(TENANT_A, new TenantSecurityPolicy(15, null, 0, 3, null, false, false));
        var auth = authenticator(world);
        var unknown = auth.authenticate(TENANT_A, "fantasma", "qualquer", "10.0.0.1", NOW);
        var wrong = auth.authenticate(TENANT_A, "user", "errada", "10.0.0.1", NOW);
        assertThat(unknown.code()).isEqualTo(wrong.code());
        assertThat(unknown.message()).isEqualTo(wrong.message());
        assertThat(unknown.code()).isEqualTo(LoginGuard.GENERIC_FAILURE_CODE);
    }

    @Test
    @DisplayName("login único: a segunda sessão substitui a primeira")
    void singleSessionPerUser() {
        World world = new World();
        world.policies.put(TENANT_A, TenantSecurityPolicy.factoryDefault());
        var auth = authenticator(world);
        var first = auth.authenticate(TENANT_A, "user", PASSWORD_A, "10.0.0.1", NOW);
        var second = auth.authenticate(TENANT_A, "user", PASSWORD_A, "10.0.0.9", NOW.plusSeconds(30));
        assertThat(first.authenticated()).isTrue();
        assertThat(second.authenticated()).isTrue();
        assertThat(world.sessions.get(new TenantUser(TENANT_A, USER_A))).isEqualTo(second.sessionId());
        assertThat(LoginGuard.isCurrentSession(
                new LoginGuard.ActiveSession(USER_A, world.sessions.get(new TenantUser(TENANT_A, USER_A)), NOW),
                USER_A, first.sessionId()))
                .isFalse();
    }

    @Test
    @DisplayName("LoginGuard puro: sucesso zera o contador; inatividade além do limite nega genérico")
    void guardBasics() {
        TenantSecurityPolicy policy = new TenantSecurityPolicy(15, null, 0, 3, 30, false, false);
        var failed = LoginGuard.onFailure(LoginGuard.AttemptState.NONE, policy, NOW, Duration.ofMinutes(30));
        assertThat(failed.consecutiveFailures()).isEqualTo(1);
        assertThat(LoginGuard.onSuccess(NOW).consecutiveFailures()).isZero();
        var stale = new LoginGuard.AttemptState(0, null, NOW.minusSeconds(40L * 24 * 3600));
        assertThat(LoginGuard.evaluate(true, stale, policy, NOW).proceed()).isFalse();
        assertThat(LoginGuard.evaluate(true, stale, policy, NOW).code())
                .isEqualTo(LoginGuard.GENERIC_FAILURE_CODE);
    }
}
