package br.com.ziipsan.core.telemetry;

import br.com.ziipsan.core.json.Json;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * P4.8 / PLT-42: agregação da telemetria de produto — SÓ agregado por
 * tenant e por papel, como o aceite manda. Nenhuma saída deste
 * agregador tem linha por pessoa (o evento nem carrega pessoa: ver
 * {@link TelemetryEvent}); as saídas são contagens, médias e rankings
 * por {@code (tenant, papel)}.
 *
 * <p>Consumidores nomeados no aceite, todos servidos daqui:
 * <ul>
 *   <li>aba Adoção da Central Ziip — {@link #summaries} por tenant;</li>
 *   <li>painel de uso do tenant — o {@link TenantUsageSummary} do
 *       próprio tenant (a fiação aplica o contexto RLS do chamador);</li>
 *   <li>priorização do changelog — {@link #changelogPriorities}:
 *       funcionalidades mais usadas e assistentes com mais abandono,
 *       em todos os tenants, ainda agregado (sem tenant na saída desta
 *       visão de produto).</li>
 * </ul>
 * JSON somente pela classe {@link Json} do core ({@link #toJson}).
 */
public final class ProductUsageAggregator {

    /** Uso de uma funcionalidade (adoção): quantos eventos, em quantos papéis. */
    public record FeatureUsage(String featureKey, long events, long roles) {
    }

    /** Assistente (wizard): inícios, conclusões, abandonos e taxa de abandono 0–1. */
    public record WizardUsage(String wizardKey, long started, long completed, long abandoned,
                              double abandonmentRate) {
    }

    /** Tarefa repetida: chave e contagem no período. */
    public record TaskUsage(String taskKey, long executions) {
    }

    /** Resumo agregado de um (tenant, papel). */
    public record TenantUsageSummary(UUID tenantId, String role, long totalEvents,
                                     List<FeatureUsage> features,
                                     Double avgFirstCycleMillis,
                                     List<WizardUsage> wizards,
                                     List<TaskUsage> topTasks) {
    }

    /** Prioridade de changelog derivada do uso agregado global. */
    public record ChangelogPriority(String key, String type, long events, Double abandonmentRate) {
    }

    private ProductUsageAggregator() {
    }

    /** Agrega os eventos por (tenant, papel), na ordem de primeira aparição. */
    public static List<TenantUsageSummary> summaries(List<TelemetryEvent> events) {
        Objects.requireNonNull(events, "events é obrigatório (vazio, não nulo)");
        Map<String, List<TelemetryEvent>> groups = new LinkedHashMap<>();
        for (TelemetryEvent e : events) {
            groups.computeIfAbsent(e.tenantId() + "|" + e.role(), k -> new ArrayList<>()).add(e);
        }
        List<TenantUsageSummary> out = new ArrayList<>();
        for (List<TelemetryEvent> group : groups.values()) {
            out.add(summarize(group));
        }
        return List.copyOf(out);
    }

    private static TenantUsageSummary summarize(List<TelemetryEvent> group) {
        TelemetryEvent first = group.get(0);
        Map<String, Long> featureEvents = new LinkedHashMap<>();
        Map<String, java.util.Set<String>> featureRoles = new LinkedHashMap<>();
        Map<String, long[]> wizards = new LinkedHashMap<>();
        Map<String, Long> tasks = new LinkedHashMap<>();
        List<Long> firstCycles = new ArrayList<>();
        for (TelemetryEvent e : group) {
            switch (e.kind()) {
                case FEATURE_USED -> {
                    featureEvents.merge(e.featureKey(), 1L, Long::sum);
                    featureRoles.computeIfAbsent(e.featureKey(), k -> new java.util.HashSet<>()).add(e.role());
                }
                case FIRST_CYCLE_COMPLETED -> firstCycles.add(e.durationMillis());
                case WIZARD_STARTED -> wizards.computeIfAbsent(e.wizardKey(), k -> new long[3])[0]++;
                case WIZARD_COMPLETED -> wizards.computeIfAbsent(e.wizardKey(), k -> new long[3])[1]++;
                case WIZARD_ABANDONED -> wizards.computeIfAbsent(e.wizardKey(), k -> new long[3])[2]++;
                case TASK_EXECUTED -> tasks.merge(e.taskKey(), 1L, Long::sum);
            }
        }
        List<FeatureUsage> features = featureEvents.entrySet().stream()
                .map(en -> new FeatureUsage(en.getKey(), en.getValue(), featureRoles.get(en.getKey()).size()))
                .sorted(Comparator.comparingLong(FeatureUsage::events).reversed())
                .toList();
        List<WizardUsage> wizardUsages = wizards.entrySet().stream()
                .map(en -> {
                    long[] v = en.getValue();
                    double rate = v[0] == 0 ? 0.0 : (double) v[2] / (double) v[0];
                    return new WizardUsage(en.getKey(), v[0], v[1], v[2], rate);
                })
                .sorted(Comparator.comparingLong(WizardUsage::started).reversed())
                .toList();
        List<TaskUsage> topTasks = tasks.entrySet().stream()
                .map(en -> new TaskUsage(en.getKey(), en.getValue()))
                .sorted(Comparator.comparingLong(TaskUsage::executions).reversed())
                .toList();
        Double avgFirstCycle = firstCycles.isEmpty() ? null
                : firstCycles.stream().mapToLong(Long::longValue).average().orElse(0.0);
        return new TenantUsageSummary(first.tenantId(), first.role(), group.size(),
                features, avgFirstCycle, wizardUsages, topTasks);
    }

    /**
     * Priorização do changelog a partir do uso agregado de TODOS os
     * tenants: funcionalidades por volume de uso e assistentes por
     * abandono (inícios como volume, taxa como sinal). Sem tenant e sem
     * papel nesta saída — é a visão de produto.
     */
    public static List<ChangelogPriority> changelogPriorities(List<TelemetryEvent> events) {
        Objects.requireNonNull(events, "events é obrigatório (vazio, não nulo)");
        // Reagrega sem o recorte de tenant/papel somando os resumos.
        Map<String, Long> features = new LinkedHashMap<>();
        Map<String, long[]> wizards = new LinkedHashMap<>();
        for (TelemetryEvent e : events) {
            switch (e.kind()) {
                case FEATURE_USED -> features.merge(e.featureKey(), 1L, Long::sum);
                case WIZARD_STARTED -> wizards.computeIfAbsent(e.wizardKey(), k -> new long[3])[0]++;
                case WIZARD_COMPLETED -> wizards.computeIfAbsent(e.wizardKey(), k -> new long[3])[1]++;
                case WIZARD_ABANDONED -> wizards.computeIfAbsent(e.wizardKey(), k -> new long[3])[2]++;
                default -> {
                }
            }
        }
        List<ChangelogPriority> out = new ArrayList<>();
        features.forEach((k, v) -> out.add(new ChangelogPriority(k, "FEATURE", v, null)));
        wizards.forEach((k, v) -> out.add(new ChangelogPriority(k, "WIZARD", v[0],
                v[0] == 0 ? 0.0 : (double) v[2] / (double) v[0])));
        out.sort(Comparator.comparingLong(ChangelogPriority::events).reversed());
        return List.copyOf(out);
    }

    /** JSON dos resumos (Central/painel), só pela classe {@code Json} do core. */
    public static String toJson(List<TenantUsageSummary> summaries) {
        List<Map<String, Object>> rows = summaries.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tenantId", s.tenantId().toString());
            m.put("role", s.role());
            m.put("totalEvents", s.totalEvents());
            m.put("features", s.features().stream().map(f -> {
                Map<String, Object> fm = new LinkedHashMap<>();
                fm.put("featureKey", f.featureKey());
                fm.put("events", f.events());
                fm.put("roles", f.roles());
                return fm;
            }).toList());
            m.put("avgFirstCycleMillis", s.avgFirstCycleMillis());
            m.put("wizards", s.wizards().stream().map(wz -> {
                Map<String, Object> wm = new LinkedHashMap<>();
                wm.put("wizardKey", wz.wizardKey());
                wm.put("started", wz.started());
                wm.put("completed", wz.completed());
                wm.put("abandoned", wz.abandoned());
                wm.put("abandonmentRate", wz.abandonmentRate());
                return wm;
            }).toList());
            m.put("topTasks", s.topTasks().stream().map(t -> {
                Map<String, Object> tm = new LinkedHashMap<>();
                tm.put("taskKey", t.taskKey());
                tm.put("executions", t.executions());
                return tm;
            }).toList());
            return m;
        }).toList();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("summaries", rows);
        return Json.write(root);
    }
}
