package br.com.ziipsan.platform.accesscontrol;

import br.com.ziipsan.security.auth.PermissionCatalog;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Resolução das permissões EFETIVAS de um usuário (ED-06), na ordem que o
 * aceite manda:
 * <pre>
 *   efetivas = (fábrica atribuídos ∪ compostos atribuídos ∪ delegações vigentes
 *               ∪ concessões DIRETAS da P0.13)
 *              − restrições negativas do usuário
 * </pre>
 * <ul>
 *   <li><b>Restrição negativa por usuário</b> SUBTRAI sempre, inclusive de
 *       permissão vinda de perfil de fábrica: é o instrumento para tirar UMA
 *       permissão de UMA pessoa sem clonar o perfil;</li>
 *   <li><b>Delegação</b> só soma o que o delegante possuía AO DELEGAR (o
 *       serviço confere na criação) e só enquanto vigente — delegação
 *       expirada ou revogada não soma nada;</li>
 *   <li>perfil de fábrica DESLIGADO (auditor externo/regulador, até a ED-75)
 *       expande para vazio, como no {@link PermissionCatalog};</li>
 *   <li>perfil composto INATIVO não soma nada;</li>
 *   <li>negação por padrão: usuário sem atribuição tem conjunto vazio.</li>
 * </ul>
 * Função pura: as listas chegam de quem lê a loja; o MESMO cálculo serve ao
 * backoffice, ao portal e ao teste de dois tenants.
 */
public final class EffectivePermissions {

    /** Restrição negativa: permissões SUBTRAÍDAS de um usuário específico. */
    public record NegativeRestriction(UUID userId, Set<String> deniedPermissions, UUID createdBy) {
        public NegativeRestriction {
            deniedPermissions = deniedPermissions == null ? Set.of() : Set.copyOf(deniedPermissions);
            for (String permission : deniedPermissions) {
                if (!PermissionCatalog.isPermission(permission)) {
                    throw new IllegalArgumentException("restrição fora do catálogo: " + permission);
                }
            }
        }
    }

    /** Delegação temporária de permissões, limitada ao que o delegante possui. */
    public record Delegation(UUID id, UUID tenantId, UUID delegatorId, UUID delegateId,
                             Set<String> permissions, Instant validFrom, Instant validTo,
                             Instant revokedAt) {
        public Delegation {
            if (id == null || tenantId == null || delegatorId == null || delegateId == null
                    || validFrom == null || validTo == null) {
                throw new IllegalArgumentException("delegação exige ids, delegante, delegado e vigência");
            }
            if (!validTo.isAfter(validFrom)) {
                throw new IllegalArgumentException("delegação exige fim depois do início");
            }
            permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
            if (permissions.isEmpty()) {
                throw new IllegalArgumentException("delegação vazia não delega nada e não pode nascer");
            }
            for (String permission : permissions) {
                if (!PermissionCatalog.isPermission(permission)) {
                    throw new IllegalArgumentException("delegação fora do catálogo: " + permission);
                }
            }
        }

        public boolean activeAt(Instant now) {
            return !validFrom.isAfter(now) && validTo.isAfter(now)
                    && (revokedAt == null || revokedAt.isAfter(now));
        }
    }

    private EffectivePermissions() {
    }

    /** Sem concessões diretas — mantido para os chamadores que não as têm. */
    public static Set<String> resolve(Set<String> factoryProfileCodes,
                                      List<CompositeProfile> compositeProfiles,
                                      List<Delegation> delegations,
                                      NegativeRestriction restriction,
                                      UUID userId, Instant now) {
        return resolve(factoryProfileCodes, compositeProfiles, delegations, Set.of(),
                restriction, userId, now);
    }

    /**
     * Resolução completa (correção D3): as concessões DIRETAS da P0.13
     * (ex.: {@code registry:reveal-document}, que nenhum perfil de fábrica
     * inclui de propósito — ADR-0008) entram na união ANTES da subtração:
     * a restrição negativa continua valendo inclusive sobre concessão direta.
     */
    public static Set<String> resolve(Set<String> factoryProfileCodes,
                                      List<CompositeProfile> compositeProfiles,
                                      List<Delegation> delegations,
                                      Set<String> directGrants,
                                      NegativeRestriction restriction,
                                      UUID userId, Instant now) {
        Set<String> result = new HashSet<>();
        if (factoryProfileCodes != null) {
            for (String code : factoryProfileCodes) {
                result.addAll(PermissionCatalog.authoritiesForRole(code));
            }
        }
        if (directGrants != null) {
            result.addAll(directGrants);
        }
        if (compositeProfiles != null) {
            for (CompositeProfile profile : compositeProfiles) {
                if (profile.active()) {
                    result.addAll(profile.expandedPermissions());
                }
            }
        }
        if (delegations != null && userId != null) {
            for (Delegation delegation : delegations) {
                if (delegation.delegateId().equals(userId) && delegation.activeAt(now)) {
                    result.addAll(delegation.permissions());
                }
            }
        }
        if (restriction != null && restriction.userId().equals(userId)) {
            result.removeAll(restriction.deniedPermissions());
        }
        return Set.copyOf(result);
    }

    /** Perfil SÓ DE CONSULTA (roteiro VISAN): nenhuma permissão de escrita. */
    public static boolean isReadOnly(Set<String> effectivePermissions) {
        if (effectivePermissions == null || effectivePermissions.isEmpty()) {
            return true;
        }
        for (String permission : effectivePermissions) {
            String action = permission.substring(permission.indexOf(':') + 1);
            if (!action.equals("read") && !action.endsWith("-read")
                    && !action.equals("catalog-read") && !action.equals("read-own")) {
                return false;
            }
        }
        return true;
    }
}
