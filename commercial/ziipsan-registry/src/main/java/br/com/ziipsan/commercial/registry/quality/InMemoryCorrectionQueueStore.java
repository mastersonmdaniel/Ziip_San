package br.com.ziipsan.commercial.registry.quality;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Store em memoria da fila de correcao: para os testes da P3.32 e para
 * exercicios sobre massa sintetica. A fiacao de producao liga a mesma
 * interface as entidades JPA ({@code cadastral_quality_findings}).
 */
public final class InMemoryCorrectionQueueStore implements CorrectionQueueService.Store {

    private final Map<UUID, CorrectionQueueItem> items = new LinkedHashMap<>();

    @Override
    public void save(CorrectionQueueItem item) {
        items.put(item.id(), item);
    }

    @Override
    public List<CorrectionQueueItem> findByFindingKey(String findingKey) {
        List<CorrectionQueueItem> result = new ArrayList<>();
        for (CorrectionQueueItem item : items.values()) {
            if (item.findingKey().equals(findingKey)) {
                result.add(item);
            }
        }
        return result;
    }

    @Override
    public List<CorrectionQueueItem> findByIds(List<UUID> ids) {
        List<CorrectionQueueItem> result = new ArrayList<>();
        for (UUID id : ids) {
            CorrectionQueueItem item = items.get(id);
            if (item != null) {
                result.add(item);
            }
        }
        return result;
    }

    @Override
    public List<CorrectionQueueItem> findByServiceOrderId(UUID serviceOrderId) {
        List<CorrectionQueueItem> result = new ArrayList<>();
        for (CorrectionQueueItem item : items.values()) {
            if (serviceOrderId.equals(item.serviceOrderId())) {
                result.add(item);
            }
        }
        return result;
    }

    /** Todos os itens (leitura de teste). */
    public List<CorrectionQueueItem> all() {
        return List.copyOf(items.values());
    }
}
