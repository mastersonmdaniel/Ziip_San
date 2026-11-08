package br.com.ziipsan.platform.orgscope;

import br.com.ziipsan.core.api.ApiException;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * "ADMIN do tenant só concede escopo e permissão que possui" (P3.30,
 * aceite, com teste) — a mesma disciplina da delegação da P2.28
 * (concedidas ⊆ delegante), aplicada à concessão de escopo:
 * <ul>
 *   <li><b>Permissões:</b> o conjunto concedido precisa ser subconjunto das
 *       permissões ATUAIS do concedente (422 nomeando as excedentes);</li>
 *   <li><b>Escopo por área:</b> a expansão do que se concede precisa estar
 *       contida na expansão do escopo do concedente — concedente irrestrito
 *       concede qualquer área; concedente comum NÃO concede irrestrito;</li>
 *   <li><b>Escopo por unidade:</b> a unidade precisa estar no recorte do
 *       concedente (concedida a ele ou descendente de uma concedida) E as
 *       áreas da unidade (dela e das descendentes, expandidas) precisam
 *       estar contidas no escopo do concedente;</li>
 *   <li><b>Auto-concessão:</b> conceder a SI MESMO passa pela mesma regra —
 *       ninguém se auto-concede o que não tem; não há exceção para ADMIN,
 *       porque "ADMIN" aqui é um usuário do tenant com permissões, não um
 *       papel de plataforma.</li>
 * </ul>
 * Conjuntos "atuais" são resolvidos pelo serviço a cada concessão (porta de
 * permissões da P0.13 e resolução de escopo): foto antiga não concede nada.
 */
public final class GrantRule {

    private GrantRule() {
    }

    public static void assertCanGrantPermissions(Set<String> grantorPermissions,
                                                 Set<String> requestedPermissions) {
        Set<String> owned = grantorPermissions == null ? Set.of() : grantorPermissions;
        Set<String> requested = requestedPermissions == null ? Set.of() : requestedPermissions;
        Set<String> beyond = new HashSet<>(requested);
        beyond.removeAll(owned);
        if (!beyond.isEmpty()) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "permissions", "beyond-grantor",
                    "Só se concede permissão que se possui; excedentes: "
                            + beyond.stream().sorted().toList()));
        }
    }

    /** Concessão por ÁREA (a expansão do pedido precisa caber no escopo do concedente). */
    public static void assertCanGrantArea(EffectiveScope grantorScope, EffectiveScope requestedExpansion) {
        if (grantorScope == null || requestedExpansion == null) {
            throw new IllegalArgumentException("escopo do concedente e expansão do pedido são obrigatórios");
        }
        if (requestedExpansion.unrestricted()) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "scope", "area-form-not-unrestricted",
                    "Concessão por área nunca é irrestrita; irrestrito é uma forma própria de concessão."));
        }
        if (!ScopeRule.coversAll(grantorScope, requestedExpansion)) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "scope", "beyond-grantor-scope",
                    "Só se concede área dentro do próprio escopo."));
        }
    }

    /** Concessão IRRESTRITA: só quem é irrestrito concede irrestrito. */
    public static void assertCanGrantUnrestricted(EffectiveScope grantorScope) {
        if (grantorScope == null) {
            throw new IllegalArgumentException("escopo do concedente é obrigatório");
        }
        if (!grantorScope.unrestricted()) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "scope", "beyond-grantor-scope",
                    "Só quem tem escopo irrestrito concede escopo irrestrito."));
        }
    }

    /**
     * Concessão por UNIDADE: a unidade precisa estar no recorte do
     * concedente e a expansão de áreas dela (unidade + descendentes) precisa
     * caber no escopo dele.
     */
    public static void assertCanGrantOrgUnit(EffectiveScope grantorScope, UUID orgUnitId,
                                             EffectiveScope orgUnitAreaExpansion) {
        if (grantorScope == null || orgUnitId == null || orgUnitAreaExpansion == null) {
            throw new IllegalArgumentException("escopo do concedente, unidade e expansão da unidade são obrigatórios");
        }
        if (!ScopeRule.coversOrgUnit(grantorScope, orgUnitId)) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "orgUnitId", "unit-outside-grantor-scope",
                    "A unidade organizacional está fora do escopo de quem concede."));
        }
        // A expansão de áreas traz orgUnitIds da subárvore: coversAll compara
        // também as unidades — o concedente precisa cobrir a subárvore toda.
        if (!grantorScope.unrestricted()
                && !ScopeRule.coversAll(grantorScope,
                        new EffectiveScope(false, orgUnitAreaExpansion.orgUnitIds(),
                                orgUnitAreaExpansion.localityIds(), orgUnitAreaExpansion.sectorIds(),
                                orgUnitAreaExpansion.routeIds()))) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "orgUnitId", "beyond-grantor-scope",
                    "As áreas da unidade (e das descendentes) excedem o escopo de quem concede."));
        }
    }
}
