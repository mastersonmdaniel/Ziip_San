package br.com.ziipsan.platform.work;

import java.util.Set;
import java.util.UUID;

/**
 * Porta para o catálogo de permissões da P0.13 (ADR-0040, {@code recurso:ação}):
 * as permissões EFETIVAS de um usuário no tenant. A caixa pergunta; não mantém cópia
 * nem presume concessão — usuário sem linha no catálogo devolve conjunto vazio da
 * implementação, e ausência do próprio usuário é erro dela, não desta porta.
 */
public interface UserPermissions {
    Set<String> permissionsOf(UUID tenantId, UUID userId);
}
