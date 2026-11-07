package br.com.ziipsan.reporting.registry;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Armazenamento em memoria da porta (testes; NAO e a fiacao de producao). */
public final class InMemoryExternalDefinitionStore implements ExternalDefinitionStore {

    private final Map<UUID, List<ReportDefinition>> byTenant = new ConcurrentHashMap<>();

    @Override
    public void save(UUID tenantId, ReportDefinition definition) {
        byTenant.computeIfAbsent(tenantId, t -> new CopyOnWriteArrayList<>()).add(definition);
    }

    @Override
    public List<ReportDefinition> loadByTenant(UUID tenantId) {
        return List.copyOf(byTenant.getOrDefault(tenantId, List.of()));
    }
}
