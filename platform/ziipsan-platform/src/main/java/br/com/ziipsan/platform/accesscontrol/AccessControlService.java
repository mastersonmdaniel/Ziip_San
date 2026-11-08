package br.com.ziipsan.platform.accesscontrol;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.platform.orgscope.GrantRule;
import br.com.ziipsan.platform.work.UserPermissions;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Controle de acesso de edital (ED-06, ADR-0040/0043): perfis compostos,
 * restrição negativa, delegação e aceites NIST do tenant.
 *
 * <p>A disciplina central é a MESMA da P3.30 ({@link GrantRule}): quem
 * concede só concede o que POSSUI, medido nas permissões EFETIVAS vigentes
 * do concedente (fábrica ∪ compostos ∪ concessões diretas da P0.13 +
 * delegações − negativas, via {@link EffectivePermissions}) — compor
 * perfil, delegar ou provisionar por SCIM não abre caminho de escalada.
 * As concessões diretas vêm da porta {@link UserPermissions} da P0.13
 * (correção D3): sem ela, permissões que só existem como concessão direta
 * (ex.: {@code registry:reveal-document}, ADR-0008) ficavam invisíveis às
 * efetivas. Se a porta FALHAR, a resolução falha junto (fail-closed):
 * devolver um conjunto parcial como se fosse o completo mentiria o teto.
 *
 * <p>Delegação NÃO eterniza nem estende (correção D5): permissão que o
 * concedente só possui por delegação vigente (i) não pode ser redelegada
 * com vigência além da delegação de origem que a sustenta, e (ii) não pode
 * entrar em perfil composto — perfil é permanente, delegação é temporária.
 *
 * <p>A gestão de usuários em si (criar, atribuir perfil de fábrica)
 * continua na P0.13; aqui ficam as AMPLIAÇÕES que o aceite da ED-06 nomeia
 * sobre ela e sobre o escopo da P3.30.
 */
public class AccessControlService {

    private final UUID tenantId;
    private final AccessControlPorts.AccessControlStore store;
    private final UserPermissions userPermissions;
    private final Clock clock;

    public AccessControlService(UUID tenantId, AccessControlPorts.AccessControlStore store,
                                UserPermissions userPermissions, Clock clock) {
        if (tenantId == null || store == null || userPermissions == null || clock == null) {
            throw new IllegalArgumentException("tenant, loja, permissões e relógio são obrigatórios");
        }
        this.tenantId = tenantId;
        this.store = store;
        this.userPermissions = userPermissions;
        this.clock = clock;
    }

    /**
     * Concessões diretas da P0.13 deste usuário, pela porta. Falha da porta
     * PROPAGA (fail-closed): efetiva incompleta não é devolvida como completa.
     */
    private Set<String> directGrantsOf(UUID userId) {
        Set<String> direct = userPermissions.permissionsOf(tenantId, userId);
        if (direct == null) {
            throw new IllegalStateException(
                    "a porta de permissões da P0.13 devolveu nulo para o usuário " + userId);
        }
        return direct;
    }

    /** Permissões efetivas (fábrica ∪ compostos ∪ concessões diretas + delegações − negativas). */
    public Set<String> effectivePermissionsOf(UUID userId) {
        return EffectivePermissions.resolve(
                store.factoryProfilesOfUser(userId),
                store.compositeProfilesOfUser(userId),
                store.delegationsOfDelegate(userId),
                directGrantsOf(userId),
                store.restrictionOfUser(userId).orElse(null),
                userId, clock.instant());
    }

    /**
     * Base NÃO delegada do usuário: fábrica ∪ compostos ∪ concessões diretas
     * − negativas, SEM delegações. É o que sustenta atos permanentes
     * (compor/atribuir perfil) e delegações sem teto de vigência herdado.
     */
    private Set<String> basePermissionsOf(UUID userId) {
        return EffectivePermissions.resolve(
                store.factoryProfilesOfUser(userId),
                store.compositeProfilesOfUser(userId),
                List.of(),
                directGrantsOf(userId),
                store.restrictionOfUser(userId).orElse(null),
                userId, clock.instant());
    }

    /**
     * Correção D5 (composto): permissão que o ator só possui por delegação
     * vigente NÃO pode virar permanente via perfil composto — nem criando,
     * nem atribuindo um perfil que a contenha.
     */
    private void assertNotSolelyDelegated(UUID actorId, Set<String> permissions) {
        Set<String> base = basePermissionsOf(actorId);
        Set<String> solelyDelegated = new HashSet<>(permissions);
        solelyDelegated.removeAll(base);
        if (!solelyDelegated.isEmpty()) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "permissions", "delegated-permission-not-permanent",
                    "Permissão recebida por delegação temporária não vira permanente: "
                            + solelyDelegated.stream().sorted().toList()));
        }
    }

    /**
     * Correção D5 (cadeia): redelegar permissão sustentada por delegação
     * vigente exige {@code validTo} até o MENOR fim das delegações de origem
     * que a sustentam — a cadeia nunca estende a vigência da origem.
     */
    private void assertChainWithinOrigin(UUID delegatorId, Set<String> permissions, Instant validTo) {
        Set<String> base = basePermissionsOf(delegatorId);
        Instant now = clock.instant();
        for (String permission : permissions) {
            if (base.contains(permission)) {
                continue;
            }
            Instant cap = null;
            for (EffectivePermissions.Delegation origin : store.delegationsOfDelegate(delegatorId)) {
                if (origin.activeAt(now) && origin.permissions().contains(permission)) {
                    cap = cap == null || origin.validTo().isBefore(cap) ? origin.validTo() : cap;
                }
            }
            if (cap == null || validTo.isAfter(cap)) {
                throw ApiException.Validation.of(new ApiException.FieldError(
                        "validTo", "delegation-beyond-origin",
                        "Delegação em cadeia não estende a vigência da delegação de origem ("
                                + permission + " vale até " + cap + ")."));
            }
        }
    }

    // ------------------------------------------------------------------
    // Perfis compostos
    // ------------------------------------------------------------------

    /**
     * Cria perfil composto a partir de perfis de fábrica + permissões diretas.
     * A EXPANSÃO inteira precisa caber nas permissões efetivas do criador, e
     * nenhuma permissão da expansão pode ser sustentada só por delegação
     * temporária do criador (perfil composto é permanente — correção D5).
     */
    public CompositeProfile createCompositeProfile(UUID actorId, String code, String name,
                                                   Set<String> factoryProfileCodes,
                                                   Set<String> directPermissions) {
        CompositeProfile profile = new CompositeProfile(UUID.randomUUID(), tenantId, code, name,
                factoryProfileCodes, directPermissions, actorId, clock.instant(), true);
        GrantRule.assertCanGrantPermissions(effectivePermissionsOf(actorId), profile.expandedPermissions());
        assertNotSolelyDelegated(actorId, profile.expandedPermissions());
        for (CompositeProfile existing : store.listCompositeProfiles()) {
            if (existing.code().equals(code)) {
                throw new ApiException.Conflict("composite-profile-code-taken",
                        "Já existe perfil composto com este código neste tenant.");
            }
        }
        store.insertCompositeProfile(profile);
        return profile;
    }

    public void assignCompositeProfile(UUID actorId, UUID userId, UUID profileId) {
        CompositeProfile profile = store.findCompositeProfile(profileId)
                .orElseThrow(() -> new ApiException.NotFound("Perfil composto não encontrado."));
        if (!profile.active()) {
            throw new ApiException.Conflict("composite-profile-inactive",
                    "Perfil composto inativo não pode ser atribuído.");
        }
        GrantRule.assertCanGrantPermissions(effectivePermissionsOf(actorId), profile.expandedPermissions());
        assertNotSolelyDelegated(actorId, profile.expandedPermissions());
        store.assignCompositeProfile(userId, profileId, actorId, clock.instant());
    }

    public List<CompositeProfile> listCompositeProfiles() {
        return store.listCompositeProfiles();
    }

    // ------------------------------------------------------------------
    // Restrição negativa e delegação
    // ------------------------------------------------------------------

    /** Restrição negativa: subtrai permissões de UM usuário (não exige possuí-las: tirar não é conceder). */
    public void restrictUser(UUID actorId, UUID userId, Set<String> deniedPermissions) {
        EffectivePermissions.NegativeRestriction restriction =
                new EffectivePermissions.NegativeRestriction(userId, deniedPermissions, actorId);
        store.upsertRestriction(restriction);
    }

    /**
     * Delegação temporária: o delegado recebe ⊆ efetivas do delegante, só na
     * vigência — e, em cadeia, nunca além da vigência da delegação de origem
     * que sustenta cada permissão (correção D5).
     */
    public EffectivePermissions.Delegation delegate(UUID delegatorId, UUID delegateId,
                                                    Set<String> permissions, Instant validFrom, Instant validTo) {
        EffectivePermissions.Delegation delegation = new EffectivePermissions.Delegation(
                UUID.randomUUID(), tenantId, delegatorId, delegateId, permissions, validFrom, validTo, null);
        GrantRule.assertCanGrantPermissions(effectivePermissionsOf(delegatorId), permissions);
        assertChainWithinOrigin(delegatorId, permissions, validTo);
        store.insertDelegation(delegation);
        return delegation;
    }

    public void revokeDelegation(UUID delegationId) {
        store.revokeDelegation(delegationId, clock.instant());
    }

    // ------------------------------------------------------------------
    // Política, aceites NIST e SSO/SCIM
    // ------------------------------------------------------------------

    /** Política EFETIVA do tenant (opções anti-NIST sem aceite DO TENANT ficam inertes). */
    public TenantSecurityPolicy effectivePolicy() {
        return PasswordPolicyRule.effectivePolicy(store.securityPolicy(), tenantId,
                store.nistAcceptances(), clock.instant());
    }

    /** Registra o aceite do ADMIN para uma opção contrária ao NIST (trilha da decisão). */
    public NistAcceptance acceptNistContraryOption(UUID adminId, String optionKey) {
        NistAcceptance acceptance = new NistAcceptance(UUID.randomUUID(), tenantId, adminId,
                optionKey, clock.instant(), null);
        store.insertNistAcceptance(acceptance);
        return acceptance;
    }

    public TenantSsoConfig ssoConfig() {
        return store.ssoConfig();
    }

    /** Login local recusado quando o SSO do tenant é obrigatório (sec.sso_enforced). */
    public void assertLocalLoginAllowed() {
        if (!store.ssoConfig().localLoginAllowed()) {
            throw new ApiException.Forbidden(
                    "Este tenant exige o SSO corporativo; o login local está desligado.");
        }
    }
}
