package br.com.ziipsan.platform.orgscope;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScopeRuleTest {

    private static final UUID LOC_A = UUID.randomUUID();
    private static final UUID LOC_B = UUID.randomUUID();
    private static final UUID SECTOR_A = UUID.randomUUID();
    private static final UUID SECTOR_B = UUID.randomUUID();
    private static final UUID ROUTE_A = UUID.randomUUID();
    private static final UUID ROUTE_B = UUID.randomUUID();

    private static EffectiveScope scopeOfLocalityA() {
        return new EffectiveScope(false, Set.of(), Set.of(LOC_A), Set.of(SECTOR_A), Set.of(ROUTE_A));
    }

    @Test
    @DisplayName("aceite: usuário com escopo na localidade A cobre registro da A e NÃO cobre o da B")
    void localityADoesNotCoverLocalityB() {
        EffectiveScope scope = scopeOfLocalityA();
        assertThat(ScopeRule.covers(scope, new AreaAnchor(LOC_A, SECTOR_A, ROUTE_A))).isTrue();
        assertThat(ScopeRule.covers(scope, new AreaAnchor(LOC_B, SECTOR_B, ROUTE_B))).isFalse();
    }

    @Test
    @DisplayName("basta um componente: setor de A cobre mesmo com a rota de outro conjunto expandido")
    void anyComponentCovers() {
        EffectiveScope sectorOnly = new EffectiveScope(false, Set.of(), Set.of(), Set.of(SECTOR_A), Set.of());
        assertThat(ScopeRule.covers(sectorOnly, new AreaAnchor(LOC_A, SECTOR_A, ROUTE_B))).isTrue();
        assertThat(ScopeRule.covers(sectorOnly, new AreaAnchor(LOC_A, SECTOR_B, ROUTE_B))).isFalse();
    }

    @Test
    @DisplayName("escopo vazio não cobre nada; irrestrito cobre tudo, inclusive sem âncora")
    void emptyAndUnrestricted() {
        AreaAnchor anchored = new AreaAnchor(LOC_A, SECTOR_A, ROUTE_A);
        assertThat(ScopeRule.covers(EffectiveScope.EMPTY, anchored)).isFalse();
        assertThat(ScopeRule.covers(EffectiveScope.EMPTY, AreaAnchor.EMPTY)).isFalse();
        assertThat(ScopeRule.covers(EffectiveScope.UNRESTRICTED, anchored)).isTrue();
        assertThat(ScopeRule.covers(EffectiveScope.UNRESTRICTED, AreaAnchor.EMPTY)).isTrue();
    }

    @Test
    @DisplayName("registro sem âncora resolvível só passa para o irrestrito (fail-closed)")
    void unanchoredRecordFailsClosed() {
        assertThat(ScopeRule.covers(scopeOfLocalityA(), AreaAnchor.EMPTY)).isFalse();
    }

    @Test
    @DisplayName("coversAll: subconjunto passa; qualquer componente fora reprova; exigência vazia não se presume coberta")
    void coversAllSemantics() {
        EffectiveScope scope = scopeOfLocalityA();
        assertThat(ScopeRule.coversAll(scope,
                new EffectiveScope(false, Set.of(), Set.of(LOC_A), Set.of(), Set.of()))).isTrue();
        assertThat(ScopeRule.coversAll(scope,
                new EffectiveScope(false, Set.of(), Set.of(LOC_A, LOC_B), Set.of(), Set.of()))).isFalse();
        assertThat(ScopeRule.coversAll(scope, EffectiveScope.EMPTY)).isFalse();
        assertThat(ScopeRule.coversAll(EffectiveScope.UNRESTRICTED, EffectiveScope.EMPTY)).isTrue();
        assertThat(ScopeRule.coversAll(scope, EffectiveScope.UNRESTRICTED)).isFalse();
    }
}
