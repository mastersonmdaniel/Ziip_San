package br.com.ziipsan.platform.orgscope;

import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrantRuleTest {

    private static final UUID LOC_A = UUID.randomUUID();
    private static final UUID LOC_B = UUID.randomUUID();
    private static final UUID UNIT_A = UUID.randomUUID();
    private static final UUID UNIT_B = UUID.randomUUID();

    private static EffectiveScope scopeA() {
        return new EffectiveScope(false, Set.of(UNIT_A), Set.of(LOC_A), Set.of(), Set.of());
    }

    @Test
    @DisplayName("aceite: ADMIN concede permissão que possui; a que NÃO possui é recusada nomeando a excedente")
    void permissionsSubset() {
        assertThatCode(() -> GrantRule.assertCanGrantPermissions(
                Set.of("user:write", "protocol:write"), Set.of("protocol:write")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> GrantRule.assertCanGrantPermissions(
                Set.of("user:write"), Set.of("protocol:transfer-any-unit")))
                .isInstanceOf(ApiException.Validation.class)
                .hasMessageContaining("Há campos inválidos");
    }

    @Test
    @DisplayName("área dentro do próprio escopo concede; área de B para quem só tem A, não")
    void areaSubset() {
        assertThatCode(() -> GrantRule.assertCanGrantArea(scopeA(),
                new EffectiveScope(false, Set.of(), Set.of(LOC_A), Set.of(), Set.of())))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> GrantRule.assertCanGrantArea(scopeA(),
                new EffectiveScope(false, Set.of(), Set.of(LOC_B), Set.of(), Set.of())))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("irrestrito só concede quem é irrestrito")
    void unrestrictedOnlyByUnrestricted() {
        assertThatThrownBy(() -> GrantRule.assertCanGrantUnrestricted(scopeA()))
                .isInstanceOf(ApiException.Validation.class);
        assertThatCode(() -> GrantRule.assertCanGrantUnrestricted(EffectiveScope.UNRESTRICTED))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("unidade fora do recorte do concedente é recusada; unidade do recorte com áreas contidas passa")
    void orgUnitGrant() {
        EffectiveScope unitAExpansion = new EffectiveScope(
                false, Set.of(UNIT_A), Set.of(LOC_A), Set.of(), Set.of());
        assertThatCode(() -> GrantRule.assertCanGrantOrgUnit(scopeA(), UNIT_A, unitAExpansion))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> GrantRule.assertCanGrantOrgUnit(scopeA(), UNIT_B, unitAExpansion))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("unidade do recorte mas com área fora do escopo do concedente também é recusada")
    void orgUnitWithAreaBeyondGrantor() {
        EffectiveScope expansionWithB = new EffectiveScope(
                false, Set.of(UNIT_A), Set.of(LOC_A, LOC_B), Set.of(), Set.of());
        assertThatThrownBy(() -> GrantRule.assertCanGrantOrgUnit(scopeA(), UNIT_A, expansionWithB))
                .isInstanceOf(ApiException.Validation.class);
    }
}
