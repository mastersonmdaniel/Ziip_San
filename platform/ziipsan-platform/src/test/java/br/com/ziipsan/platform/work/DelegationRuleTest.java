package br.com.ziipsan.platform.work;

import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Delegação temporária (P2.28), casos calculados à mão:
 * janela-base 2026-03-01T00:00:00Z → 2026-03-31T00:00:00Z = exatamente 30 dias;
 * 2026-04-01T00:00:00Z fecha 31 dias.
 */
class DelegationRuleTest {

    private static final UUID DELEGATOR = UUID.randomUUID();
    private static final UUID DELEGATE = UUID.randomUUID();
    private static final Instant START = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant END_30_DAYS = Instant.parse("2026-03-31T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-02-20T00:00:00Z");

    private static WorkDelegation delegation(Instant start, Instant end, Set<String> granted) {
        return new WorkDelegation(UUID.randomUUID(), UUID.randomUUID(), DELEGATOR, DELEGATE,
                start, end, granted, "férias", DELEGATOR, NOW, null, null);
    }

    @Test
    @DisplayName("REGRA DO ACEITE: a delegação EXPIRA na data de fim — vigente às 23:59:59 da véspera, morta no instante do fim (fim exclusivo)")
    void delegationExpiresAtEndDate() {
        WorkDelegation d = delegation(START, END_30_DAYS, Set.of());
        assertThat(d.isActiveAt(Instant.parse("2026-03-30T23:59:59Z"))).isTrue();
        assertThat(d.isActiveAt(END_30_DAYS)).isFalse();
        assertThat(d.isActiveAt(Instant.parse("2026-03-31T00:00:01Z"))).isFalse();
        // E antes do início também não vale: vigência é [início, fim).
        assertThat(d.isActiveAt(Instant.parse("2026-02-28T23:59:59Z"))).isFalse();
        assertThat(d.isActiveAt(START)).isTrue();
    }

    @Test
    @DisplayName("delegação revogada não vige nem dentro da janela")
    void revokedDelegationIsDead() {
        WorkDelegation d = new WorkDelegation(UUID.randomUUID(), UUID.randomUUID(), DELEGATOR, DELEGATE,
                START, END_30_DAYS, Set.of(), "férias", DELEGATOR, NOW,
                Instant.parse("2026-03-05T00:00:00Z"), DELEGATOR);
        assertThat(d.isActiveAt(Instant.parse("2026-03-10T00:00:00Z"))).isFalse();
    }

    @Test
    @DisplayName("REGRA DO ACEITE: o delegado NÃO recebe permissão além da do delegante — conceder o que o delegante não tem é recusado nomeando a excedente")
    void delegateCannotReceiveBeyondDelegator() {
        assertThatThrownBy(() -> DelegationRule.assertCanCreate(DELEGATOR, DELEGATE, START, END_30_DAYS,
                Set.of("so:approve", "billing:rectify"), Set.of("so:approve"), 30, NOW))
                .isInstanceOf(ApiException.Validation.class)
                .hasMessageContaining("Há campos inválidos");
        // Subconjunto (inclusive igual e vazio) passa.
        DelegationRule.assertCanCreate(DELEGATOR, DELEGATE, START, END_30_DAYS,
                Set.of("so:approve"), Set.of("so:approve", "so:read"), 30, NOW);
        DelegationRule.assertCanCreate(DELEGATOR, DELEGATE, START, END_30_DAYS,
                Set.of(), Set.of("so:approve"), 30, NOW);
    }

    @Test
    @DisplayName("permissão efetiva é concedida ∩ delegante ATUAL: se o delegante perde a permissão, o delegado perde junto")
    void effectivePermissionFollowsCurrentDelegator() {
        WorkDelegation d = delegation(START, END_30_DAYS, Set.of("so:approve", "billing:rectify"));
        Instant during = Instant.parse("2026-03-10T00:00:00Z");
        // Delegante ainda tem as duas: delegado soma as duas às próprias.
        assertThat(DelegationRule.effectivePermissions(DELEGATE, Set.of("own:read"),
                Map.of(DELEGATOR, Set.of("so:approve", "billing:rectify")), List.of(d), during))
                .containsExactlyInAnyOrder("own:read", "so:approve", "billing:rectify");
        // Delegante perdeu billing:rectify: o delegado fica só com so:approve emprestada.
        assertThat(DelegationRule.effectivePermissions(DELEGATE, Set.of("own:read"),
                Map.of(DELEGATOR, Set.of("so:approve")), List.of(d), during))
                .containsExactlyInAnyOrder("own:read", "so:approve");
        // Delegante sem conjunto conhecido: nada é presumido da foto da concessão.
        assertThat(DelegationRule.effectivePermissions(DELEGATE, Set.of("own:read"),
                Map.of(), List.of(d), during))
                .containsExactly("own:read");
        // Fora da janela, nada empresta.
        assertThat(DelegationRule.effectivePermissions(DELEGATE, Set.of("own:read"),
                Map.of(DELEGATOR, Set.of("so:approve", "billing:rectify")), List.of(d), END_30_DAYS))
                .containsExactly("own:read");
    }

    @Test
    @DisplayName("teto wf.delegation_max_days: exatamente 30 dias com teto 30 passa; 31 dias (e 30 dias + 1 segundo) recusam")
    void delegationMaxDaysCap() {
        DelegationRule.assertCanCreate(DELEGATOR, DELEGATE, START, END_30_DAYS,
                Set.of(), Set.of(), 30, NOW);
        assertThatThrownBy(() -> DelegationRule.assertCanCreate(DELEGATOR, DELEGATE, START,
                Instant.parse("2026-04-01T00:00:00Z"), Set.of(), Set.of(), 30, NOW))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> DelegationRule.assertCanCreate(DELEGATOR, DELEGATE, START,
                Instant.parse("2026-03-31T00:00:01Z"), Set.of(), Set.of(), 30, NOW))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("janela inválida, auto-delegação, delegação já expirada e teto não resolvido recusam")
    void invalidWindowsRefuse() {
        assertThatThrownBy(() -> DelegationRule.assertCanCreate(DELEGATOR, DELEGATE, END_30_DAYS, START,
                Set.of(), Set.of(), 30, NOW)).isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> DelegationRule.assertCanCreate(DELEGATOR, DELEGATOR, START, END_30_DAYS,
                Set.of(), Set.of(), 30, NOW)).isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> DelegationRule.assertCanCreate(DELEGATOR, DELEGATE,
                Instant.parse("2026-02-01T00:00:00Z"), Instant.parse("2026-02-10T00:00:00Z"),
                Set.of(), Set.of(), 30, NOW)).isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> DelegationRule.assertCanCreate(DELEGATOR, DELEGATE, START, END_30_DAYS,
                Set.of(), Set.of(), 0, NOW)).isInstanceOf(IllegalStateException.class);
    }
}
