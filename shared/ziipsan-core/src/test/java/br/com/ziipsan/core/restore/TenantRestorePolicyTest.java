package br.com.ziipsan.core.restore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes puros do portao da restauracao seletiva (P3.33): papel,
 * step-up no momento, consentimento do tenant (ADR-0020) amarrado ao
 * instante-alvo e ao modo, e aplicacao em janela. Instantes fixos,
 * calculados a mao:
 * janela 2027-02-07T00:00Z a 2027-02-07T04:00Z (domingo, janela do
 * RNF-12 usada como EXEMPLO de janela declarada — a regra recebe a
 * janela como dado); execucao em 2027-02-07T01:00Z.
 */
class TenantRestorePolicyTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TENANT_USER = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant TARGET = Instant.parse("2027-02-01T12:00:00Z");
    private static final Instant NOW = Instant.parse("2027-02-07T01:00:00Z");
    private static final Instant WINDOW_START = Instant.parse("2027-02-07T00:00:00Z");
    private static final Instant WINDOW_END = Instant.parse("2027-02-07T04:00:00Z");

    private static RestoreConsent consent() {
        return RestoreConsent.grant(UUID.randomUUID(), TENANT, TENANT_USER,
                RestoreConsent.RestoreMode.REPLACE, TARGET,
                Instant.parse("2027-02-05T00:00:00Z"), Instant.parse("2027-02-10T00:00:00Z"));
    }

    private static TenantRestorePolicy.Decision evaluate(String role, Instant mfa,
                                                         RestoreConsent consent, UUID tenant,
                                                         RestoreConsent.RestoreMode mode, Instant target,
                                                         Instant now) {
        return TenantRestorePolicy.evaluateExecution(role, mfa, consent, tenant, mode, target,
                WINDOW_START, WINDOW_END, now);
    }

    @Test
    @DisplayName("caminho feliz: PLATFORM_OWNER, step-up do momento, consentimento vigente e em janela permite")
    void happyPathAllowed() {
        TenantRestorePolicy.Decision d = evaluate("PLATFORM_OWNER", NOW, consent(), TENANT,
                RestoreConsent.RestoreMode.REPLACE, TARGET, NOW);
        assertThat(d.allowed()).isTrue();
        assertThat(d.code()).isEqualTo("allowed");
    }

    @Test
    @DisplayName("no instante exato do inicio da janela: permitido (a janela e [inicio, fim))")
    void windowStartIsInclusive() {
        TenantRestorePolicy.Decision d = evaluate("PLATFORM_OWNER", WINDOW_START, consent(), TENANT,
                RestoreConsent.RestoreMode.REPLACE, TARGET, WINDOW_START);
        assertThat(d.allowed()).isTrue();
    }

    @Test
    @DisplayName("papel diverso de PLATFORM_OWNER e negado, mesmo com todo o resto valido")
    void nonOwnerDenied() {
        for (String role : new String[]{"PLATFORM_OPS", "PLATFORM_SUPPORT", "PLATFORM_FINANCE"}) {
            TenantRestorePolicy.Decision d = evaluate(role, NOW, consent(), TENANT,
                    RestoreConsent.RestoreMode.REPLACE, TARGET, NOW);
            assertThat(d.allowed()).isFalse();
            assertThat(d.code()).isEqualTo("platform-owner-required");
        }
    }

    @Test
    @DisplayName("papel fora do dominio da P0.43 e dado invalido, nao uma negacao silenciosa")
    void unknownRoleIsInvalidData() {
        assertThatThrownBy(() -> evaluate("ADMIN", NOW, consent(), TENANT,
                RestoreConsent.RestoreMode.REPLACE, TARGET, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sem step-up e com step-up de requisicao anterior: negado")
    void stepUpMustBeCurrent() {
        assertThat(evaluate("PLATFORM_OWNER", null, consent(), TENANT,
                RestoreConsent.RestoreMode.REPLACE, TARGET, NOW).code())
                .isEqualTo("mfa-required");
        assertThat(evaluate("PLATFORM_OWNER", NOW.minusSeconds(60), consent(), TENANT,
                RestoreConsent.RestoreMode.REPLACE, TARGET, NOW).code())
                .isEqualTo("mfa-not-current");
    }

    @Test
    @DisplayName("sem consentimento, de outro tenant, revogado, expirado e ainda nao vigente: negado com o codigo do motivo")
    void consentGate() {
        assertThat(evaluate("PLATFORM_OWNER", NOW, null, TENANT,
                RestoreConsent.RestoreMode.REPLACE, TARGET, NOW).code())
                .isEqualTo("consent-required");

        RestoreConsent otherTenant = RestoreConsent.grant(UUID.randomUUID(), OTHER_TENANT, TENANT_USER,
                RestoreConsent.RestoreMode.REPLACE, TARGET,
                Instant.parse("2027-02-05T00:00:00Z"), Instant.parse("2027-02-10T00:00:00Z"));
        assertThat(evaluate("PLATFORM_OWNER", NOW, otherTenant, TENANT,
                RestoreConsent.RestoreMode.REPLACE, TARGET, NOW).code())
                .isEqualTo("consent-tenant-mismatch");

        RestoreConsent revoked = consent().revoke(Instant.parse("2027-02-06T00:00:00Z"), TENANT_USER);
        assertThat(evaluate("PLATFORM_OWNER", NOW, revoked, TENANT,
                RestoreConsent.RestoreMode.REPLACE, TARGET, NOW).code())
                .isEqualTo("consent-revoked");

        RestoreConsent expired = RestoreConsent.grant(UUID.randomUUID(), TENANT, TENANT_USER,
                RestoreConsent.RestoreMode.REPLACE, TARGET,
                Instant.parse("2027-02-05T00:00:00Z"), Instant.parse("2027-02-07T00:30:00Z"));
        assertThat(evaluate("PLATFORM_OWNER", NOW, expired, TENANT,
                RestoreConsent.RestoreMode.REPLACE, TARGET, NOW).code())
                .isEqualTo("consent-expired");

        RestoreConsent future = RestoreConsent.grant(UUID.randomUUID(), TENANT, TENANT_USER,
                RestoreConsent.RestoreMode.REPLACE, TARGET,
                Instant.parse("2027-02-07T02:00:00Z"), Instant.parse("2027-02-10T00:00:00Z"));
        assertThat(evaluate("PLATFORM_OWNER", NOW, future, TENANT,
                RestoreConsent.RestoreMode.REPLACE, TARGET, NOW).code())
                .isEqualTo("consent-not-yet-valid");
    }

    @Test
    @DisplayName("instante-alvo ou modo diverso do consentido: negado (restaurar outra coisa nao e o que o tenant aprovou)")
    void consentTargetAndModeBind() {
        assertThat(evaluate("PLATFORM_OWNER", NOW, consent(), TENANT,
                RestoreConsent.RestoreMode.REPLACE, TARGET.plusSeconds(3600), NOW).code())
                .isEqualTo("consent-target-mismatch");
        assertThat(evaluate("PLATFORM_OWNER", NOW, consent(), TENANT,
                RestoreConsent.RestoreMode.MERGE, TARGET, NOW).code())
                .isEqualTo("consent-mode-mismatch");
    }

    @Test
    @DisplayName("fora da janela declarada (antes e no instante do fim): negado")
    void outsideWindowDenied() {
        Instant before = Instant.parse("2027-02-06T23:00:00Z");
        assertThat(evaluate("PLATFORM_OWNER", before, consent(), TENANT,
                RestoreConsent.RestoreMode.REPLACE, TARGET, before).code())
                .isEqualTo("outside-window");
        assertThat(evaluate("PLATFORM_OWNER", WINDOW_END, consent(), TENANT,
                RestoreConsent.RestoreMode.REPLACE, TARGET, WINDOW_END).code())
                .isEqualTo("outside-window");
    }

    @Test
    @DisplayName("consentimento: revogar duas vezes recusa; JSON sai pela classe Json com o instante-alvo")
    void consentRecordRules() {
        RestoreConsent c = consent();
        RestoreConsent revoked = c.revoke(Instant.parse("2027-02-06T00:00:00Z"), TENANT_USER);
        assertThat(revoked.isActiveAt(NOW)).isFalse();
        assertThatThrownBy(() -> revoked.revoke(NOW, TENANT_USER))
                .isInstanceOf(IllegalStateException.class);
        assertThat(c.toJson()).contains("\"targetInstant\":\"2027-02-01T12:00:00Z\"")
                .contains("\"mode\":\"REPLACE\"");
    }
}
