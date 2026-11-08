package br.com.ziipsan.reporting.bi;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ED-47: adaptador em memória do {@link BiRoleStore}, para testes e
 * para o ambiente local sem a tabela definitiva. NÃO é persistência de
 * produção — a pendência da reserva de migration está no relatório; a
 * integração troca este bean pela implementação JDBC sem tocar na
 * política nem na guarda.
 */
public final class InMemoryBiRoleStore implements BiRoleStore {

    private final Map<UUID, TenantBiRole> byTenant = new ConcurrentHashMap<>();

    @Override
    public void save(TenantBiRole mapping) {
        byTenant.put(mapping.tenantId(), mapping);
    }

    @Override
    public Optional<TenantBiRole> findByTenant(UUID tenantId) {
        return Optional.ofNullable(byTenant.get(tenantId));
    }

    @Override
    public Optional<TenantBiRole> findByRoleName(String roleName) {
        return byTenant.values().stream().filter(m -> m.roleName().equals(roleName)).findFirst();
    }
}
