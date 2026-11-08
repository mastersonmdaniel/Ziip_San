package br.com.ziipsan.reporting.situation;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ED-93: implementacao em memoria da tabela de estado, para os testes
 * do nucleo (precedente do InMemoryIndicatorStore da P3.41). Recusa
 * linha de tenant diverso do retrato sendo gravado — a troca e por
 * tenant, e mistura e erro nomeado, nao vazamento silencioso.
 */
public final class InMemorySituationStateStore implements SituationStateStore {

    private final Map<UUID, List<SituationStateRow>> byTenant = new ConcurrentHashMap<>();

    @Override
    public void replaceTenantSnapshot(UUID tenantId, List<SituationStateRow> rows) {
        Objects.requireNonNull(tenantId, "tenant obrigatorio");
        Objects.requireNonNull(rows, "linhas do retrato obrigatorias");
        for (SituationStateRow row : rows) {
            if (!tenantId.equals(row.tenantId())) {
                throw new IndicatorValidationException("SITUATION_ROW_TENANT_MISMATCH",
                        "linha " + row.itemRef() + " de outro tenant no retrato de " + tenantId);
            }
        }
        byTenant.put(tenantId, List.copyOf(rows));
    }

    @Override
    public List<SituationStateRow> rows(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenant obrigatorio");
        return byTenant.getOrDefault(tenantId, List.of());
    }
}
