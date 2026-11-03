package br.com.ziipsan.platform.infra;

import br.com.ziipsan.platform.infra.crypto.TenantKeyStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Duplo em memoria de {@link TenantKeyStore}, SO PARA TESTE. Nao e implementacao de
 * producao: a de producao local e {@link br.com.ziipsan.platform.infra.crypto.JdbcTenantKeyStore}.
 */
public final class InMemoryTenantKeyStore implements TenantKeyStore {

    private final Map<UUID, List<KeyMaterial>> rows = new ConcurrentHashMap<>();

    @Override
    public Optional<KeyMaterial> loadActive(UUID tenantId) {
        return rows.getOrDefault(tenantId, List.of()).stream()
                .filter(m -> "ACTIVE".equals(m.status()))
                .reduce((a, b) -> b.version() > a.version() ? b : a);
    }

    @Override
    public synchronized int createVersion(UUID tenantId, byte[] wrappedEncryptionKey, byte[] wrappedBlindIndexKey) {
        List<KeyMaterial> list = rows.computeIfAbsent(tenantId, k -> new ArrayList<>());
        int next = list.stream().mapToInt(KeyMaterial::version).max().orElse(0) + 1;
        list.replaceAll(m -> "ACTIVE".equals(m.status())
                ? new KeyMaterial(m.version(), m.wrappedEncryptionKey(), m.wrappedBlindIndexKey(), "ROTATED") : m);
        list.add(new KeyMaterial(next, wrappedEncryptionKey.clone(), wrappedBlindIndexKey.clone(), "ACTIVE"));
        return next;
    }

    @Override
    public synchronized void destroyAll(UUID tenantId) {
        List<KeyMaterial> list = rows.get(tenantId);
        if (list != null) {
            list.replaceAll(m -> new KeyMaterial(m.version(), null, null, "DESTROYED"));
        }
    }

    @Override
    public boolean destroyed(UUID tenantId) {
        return rows.getOrDefault(tenantId, List.of()).stream().anyMatch(m -> "DESTROYED".equals(m.status()));
    }
}
