package br.com.ziipsan.platform.notification.multichannel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Exposição de backend do uso medido (aceite da ED-41): agrega os fatos do
 * {@link UsageStore} por tenant, módulo e métrica no período — é o dado
 * que o console da P1.6 exibe e que a ED-10 (franquia/repasse) e a ED-42
 * consomem. A AGREGAÇÃO é função pura sobre a lista de fatos
 * ({@link #aggregate}), testada sem banco; a tela do console em si é da
 * P1.6 (fiação nomeada como pendência no relatório).
 */
public class UsageReportService {

    /** Uso de UM módulo no período: totais por métrica e custo total (só dos fatos com custo). */
    public record ModuleUsage(String moduleCode, Map<UsageMetric, Long> quantities, BigDecimal totalCost) {
    }

    /** Relatório de uso de UM tenant no período, por módulo. */
    public record UsageReport(UUID tenantId, Instant from, Instant to, List<ModuleUsage> modules) {
    }

    private final UsageStore store;

    public UsageReportService(UsageStore store) {
        if (store == null) {
            throw new IllegalArgumentException("o registro de uso é obrigatório");
        }
        this.store = store;
    }

    public UsageReport report(UUID tenantId, Instant from, Instant to) {
        if (tenantId == null || from == null || to == null) {
            throw new IllegalArgumentException("tenant e período são obrigatórios no relatório de uso");
        }
        return aggregate(tenantId, from, to, store.query(tenantId, from, to));
    }

    /** Agregação pura: fatos de OUTRO tenant na lista são erro do chamador, não são somados em silêncio. */
    public static UsageReport aggregate(UUID tenantId, Instant from, Instant to, List<UsageRecord> records) {
        if (tenantId == null || from == null || to == null || records == null) {
            throw new IllegalArgumentException("tenant, período e fatos são obrigatórios na agregação");
        }
        Map<String, Map<UsageMetric, Long>> quantities = new LinkedHashMap<>();
        Map<String, BigDecimal> costs = new LinkedHashMap<>();
        for (UsageRecord record : records) {
            if (!record.tenantId().equals(tenantId)) {
                throw new IllegalArgumentException("fato de uso de outro tenant na agregação: isolamento violado");
            }
            quantities.computeIfAbsent(record.moduleCode(), k -> new EnumMap<>(UsageMetric.class))
                    .merge(record.metric(), record.quantity(), Long::sum);
            if (record.cost() != null) {
                costs.merge(record.moduleCode(), record.cost(), BigDecimal::add);
            }
        }
        List<ModuleUsage> modules = quantities.entrySet().stream()
                .map(e -> new ModuleUsage(e.getKey(), Map.copyOf(e.getValue()),
                        costs.getOrDefault(e.getKey(), BigDecimal.ZERO)))
                .toList();
        return new UsageReport(tenantId, from, to, modules);
    }
}
