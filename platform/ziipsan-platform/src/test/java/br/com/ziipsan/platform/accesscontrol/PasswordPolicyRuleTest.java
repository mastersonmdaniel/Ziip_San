package br.com.ziipsan.platform.accesscontrol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyRuleTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");
    private static final PasswordPolicyRule.BreachedPasswordPort NOT_BREACHED = candidate -> false;
    private static final PasswordPolicyRule.PasswordHistoryPort NO_HISTORY_MATCH = (candidate, n) -> false;

    @Test
    @DisplayName("NIST: fator único exige 15 caracteres mesmo se o tenant configurar menos")
    void singleFactorMinimumIs15() {
        TenantSecurityPolicy configured = new TenantSecurityPolicy(8, null, 0, 5, null, false, false);
        assertThat(PasswordPolicyRule.validateNewPassword("senha-curta-1", configured, false,
                NOT_BREACHED, NO_HISTORY_MATCH).valid()).isFalse();
        assertThat(PasswordPolicyRule.validateNewPassword("uma-senha-bem-longa", configured, false,
                NOT_BREACHED, NO_HISTORY_MATCH).valid()).isTrue();
        // Com MFA, vale o configurado (piso 8).
        assertThat(PasswordPolicyRule.validateNewPassword("senha-curta-1", configured, true,
                NOT_BREACHED, NO_HISTORY_MATCH).valid()).isTrue();
    }

    @Test
    @DisplayName("NIST: senha em lista de vazadas é recusada; histórico impede repetição")
    void breachedAndHistory() {
        TenantSecurityPolicy policy = new TenantSecurityPolicy(15, null, 3, 5, null, false, false);
        assertThat(PasswordPolicyRule.validateNewPassword("uma-senha-bem-longa", policy, false,
                candidate -> true, NO_HISTORY_MATCH).code()).isEqualTo("password-breached");
        assertThat(PasswordPolicyRule.validateNewPassword("uma-senha-bem-longa", policy, false,
                NOT_BREACHED, (candidate, n) -> n == 3).code()).isEqualTo("password-in-history");
    }

    @Test
    @DisplayName("expiração e composição contrárias ao NIST só valem com aceite vigente do ADMIN")
    void contraryOptionsNeedAcceptance() {
        TenantSecurityPolicy configured = new TenantSecurityPolicy(15, 90, 0, 5, null, true, false);
        TenantSecurityPolicy withoutAcceptance =
                PasswordPolicyRule.effectivePolicy(configured, List.of(), NOW);
        assertThat(withoutAcceptance.periodicExpiryEnabled()).isFalse();
        assertThat(withoutAcceptance.compositionRequired()).isFalse();
        // Sem aceite, senha sem símbolo passa e nunca expira.
        assertThat(PasswordPolicyRule.validateNewPassword("uma senha bem longa", withoutAcceptance, false,
                NOT_BREACHED, NO_HISTORY_MATCH).valid()).isTrue();
        assertThat(PasswordPolicyRule.isExpired(withoutAcceptance, NOW.minusSeconds(200L * 24 * 3600), NOW)).isFalse();

        List<NistAcceptance> acceptances = List.of(
                new NistAcceptance(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        NistAcceptance.OPTION_PERIODIC_EXPIRY, NOW.minusSeconds(3600), null),
                new NistAcceptance(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        NistAcceptance.OPTION_COMPOSITION, NOW.minusSeconds(3600), null));
        TenantSecurityPolicy withAcceptance = PasswordPolicyRule.effectivePolicy(configured, acceptances, NOW);
        assertThat(withAcceptance.periodicExpiryEnabled()).isTrue();
        assertThat(withAcceptance.compositionRequired()).isTrue();
        assertThat(PasswordPolicyRule.isExpired(withAcceptance, NOW.minusSeconds(100L * 24 * 3600), NOW)).isTrue();
        assertThat(PasswordPolicyRule.validateNewPassword("uma senha bem longa", withAcceptance, false,
                NOT_BREACHED, NO_HISTORY_MATCH).code()).isEqualTo("password-composition");
    }

    @Test
    @DisplayName("aceite de OUTRO tenant não liga a opção contrária ao NIST neste tenant")
    void acceptanceOfAnotherTenantDoesNotApply() {
        TenantSecurityPolicy configured = new TenantSecurityPolicy(15, 90, 0, 5, null, true, false);
        UUID tenant = UUID.randomUUID();
        List<NistAcceptance> foreign = List.of(
                new NistAcceptance(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        NistAcceptance.OPTION_PERIODIC_EXPIRY, NOW.minusSeconds(3600), null),
                new NistAcceptance(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        NistAcceptance.OPTION_COMPOSITION, NOW.minusSeconds(3600), null));
        TenantSecurityPolicy foreignOnly =
                PasswordPolicyRule.effectivePolicy(configured, tenant, foreign, NOW);
        assertThat(foreignOnly.periodicExpiryEnabled()).isFalse();
        assertThat(foreignOnly.compositionRequired()).isFalse();
        // E o aceite do PRÓPRIO tenant, na mesma lista mista, vale.
        List<NistAcceptance> mixed = List.of(foreign.get(0),
                new NistAcceptance(UUID.randomUUID(), tenant, UUID.randomUUID(),
                        NistAcceptance.OPTION_PERIODIC_EXPIRY, NOW.minusSeconds(3600), null));
        TenantSecurityPolicy withOwn =
                PasswordPolicyRule.effectivePolicy(configured, tenant, mixed, NOW);
        assertThat(withOwn.periodicExpiryEnabled()).isTrue();
        assertThat(withOwn.compositionRequired()).isFalse();
    }

    @Test
    @DisplayName("padrão de fábrica: sem expiração, mínimo 15, passkey desligada")
    void factoryDefaultIsNistAligned() {
        TenantSecurityPolicy d = TenantSecurityPolicy.factoryDefault();
        assertThat(d.periodicExpiryEnabled()).isFalse();
        assertThat(d.contraryToNist()).isFalse();
        assertThat(d.effectiveMinLength(false)).isEqualTo(15);
        assertThat(d.passkeyEnabled()).isFalse();
    }
}
