package br.com.ziipsan.platform.accesscontrol;

import br.com.ziipsan.security.auth.PermissionCatalog;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Perfil COMPOSTO do tenant (ED-06): criado pelo administrador do tenant A
 * PARTIR dos perfis de fábrica da P1.37 — a composição referencia perfis de
 * fábrica e pode SOMAR permissões diretas do catálogo, mas o serviço
 * ({@link AccessControlService}) só grava o que o administrador POSSUI
 * (GrantRule da P3.30): compor não é um caminho para escalar o próprio acesso.
 *
 * <p>O perfil composto NÃO altera perfil de fábrica (esses são do produto,
 * versionados com ele): é uma linha do tenant, com nome próprio, atribuível
 * a usuários do MESMO tenant. Perfil de OUTRO tenant é invisível (404 na
 * camada de serviço/loja, como todo recurso recortado por tenant).
 */
public record CompositeProfile(UUID id, UUID tenantId, String code, String name,
                               Set<String> factoryProfileCodes, Set<String> directPermissions,
                               UUID createdBy, Instant createdAt, boolean active) {

    public CompositeProfile {
        if (id == null || tenantId == null || createdBy == null || createdAt == null) {
            throw new IllegalArgumentException("perfil composto exige id, tenant, criador e criação");
        }
        if (code == null || code.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("perfil composto exige código e nome");
        }
        factoryProfileCodes = factoryProfileCodes == null ? Set.of() : Set.copyOf(factoryProfileCodes);
        directPermissions = directPermissions == null ? Set.of() : Set.copyOf(directPermissions);
        if (factoryProfileCodes.isEmpty() && directPermissions.isEmpty()) {
            throw new IllegalArgumentException("perfil composto vazio não concede nada e não pode nascer");
        }
        for (String factory : factoryProfileCodes) {
            boolean known = PermissionCatalog.factoryProfiles().stream()
                    .anyMatch(p -> p.name().equals(factory));
            if (!known) {
                throw new IllegalArgumentException("perfil de fábrica desconhecido: " + factory);
            }
        }
        for (String permission : directPermissions) {
            if (!PermissionCatalog.isPermission(permission)) {
                throw new IllegalArgumentException("permissão fora do catálogo recurso:ação: " + permission);
            }
        }
    }

    /** Expansão: união das permissões dos perfis de fábrica (só os LIGADOS) + diretas. */
    public Set<String> expandedPermissions() {
        java.util.HashSet<String> result = new java.util.HashSet<>(directPermissions);
        for (String factory : factoryProfileCodes) {
            result.addAll(PermissionCatalog.authoritiesForRole(factory));
        }
        return Set.copyOf(result);
    }
}
