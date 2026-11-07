package br.com.ziipsan.platform.statuspage;

import br.com.ziipsan.core.json.Json;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * P3.18: derivação do status público e montagem da visão da página
 * ({@code GET /api/v1/public/status}, rota mantida pelo plano v3.0).
 *
 * <p>O coração do aceite é {@link #derive}: o status de um componente é
 * CALCULADO a cada leitura a partir de fatos — a leitura mais recente da
 * sonda, os incidentes abertos que o afetam e as janelas de manutenção
 * ativas. Não existe caminho neste serviço para "definir" um status:
 * quando a sonda cai, a próxima leitura já mostra {@code OUTAGE} sem
 * ninguém editar nada ("status muda sozinho com a sonda fora").
 *
 * <p>Regras de derivação (em ordem de prioridade, ver
 * {@link ComponentStatus}):
 * <ol>
 *   <li>sonda {@code DOWN} → {@code OUTAGE};</li>
 *   <li>incidente aberto {@code MAJOR}/{@code CRITICAL} afetando o
 *       componente → {@code OUTAGE}; {@code MINOR} → {@code DEGRADED};</li>
 *   <li>janela ativa (do componente ou global) → {@code MAINTENANCE};</li>
 *   <li>componente COM sonda e nenhuma leitura → {@code UNKNOWN}
 *       (nunca {@code OPERATIONAL} sem medição);</li>
 *   <li>sonda {@code UP} sem incidente/janela, ou componente sem sonda
 *       sem incidente/janela → {@code OPERATIONAL}.</li>
 * </ol>
 *
 * <p>A visão pública é filtrada: componentes não visíveis não aparecem,
 * incidente resolvido só aparece com o resumo quando ele foi publicado
 * ({@link PostIncidentSummary}), e nenhum campo interno (revisão
 * completa, causa interna) existe nestes records para vazar.
 *
 * <p>JSON somente pela classe {@code Json} do core ({@link #toJson}).
 */
public final class PublicStatusService {

    /** Visão pública de um componente: identidade + status derivado. */
    public record ComponentView(String componentKey, String name, String description,
                                ComponentStatus status) {
    }

    /** Visão pública de um incidente aberto. */
    public record IncidentView(String title, String impact, String status,
                               List<String> affectedComponentKeys, Instant startedAt) {
    }

    /** Visão pública de um resumo pós-incidente publicado. */
    public record PublishedSummaryView(String incidentTitle, List<String> affectedComponentKeys,
                                       Instant startedAt, Instant resolvedAt,
                                       String summary, Instant publishedAt) {
    }

    /** Visão pública de uma janela (ativa ou futura). */
    public record MaintenanceView(String title, String description, String componentKey,
                                  Instant startsAt, Instant endsAt, boolean active) {
    }

    /** A página inteira, como o endpoint a devolve. */
    public record PublicStatusView(ComponentStatus overall, Instant generatedAt,
                                   List<ComponentView> components,
                                   List<IncidentView> activeIncidents,
                                   List<MaintenanceView> maintenance,
                                   List<PublishedSummaryView> publishedSummaries) {
    }

    private PublicStatusService() {
    }

    /**
     * Deriva o status de um componente. {@code latestReading} é
     * {@code null} quando a sonda nunca mediu (ou o componente não tem
     * sonda); leituras de outro componente são erro de quem chama —
     * a leitura carrega a chave e a divergência é recusada, não ignorada.
     */
    public static ComponentStatus derive(StatusComponent component, ProbeReading latestReading,
                                         List<StatusIncident> incidents,
                                         List<MaintenanceWindow> windows, Instant now) {
        Objects.requireNonNull(component, "Componente é obrigatório para derivar o status");
        Objects.requireNonNull(incidents, "Lista de incidentes é obrigatória (vazia, não nula)");
        Objects.requireNonNull(windows, "Lista de janelas é obrigatória (vazia, não nula)");
        Objects.requireNonNull(now, "now é obrigatório para derivar o status");
        if (latestReading != null && !latestReading.componentKey().equals(component.componentKey())) {
            throw new IllegalArgumentException(
                    "Leitura da sonda de outro componente: " + latestReading.componentKey()
                            + " entregue para " + component.componentKey());
        }

        ComponentStatus derived = null;

        // 1–2. Fatos de indisponibilidade/degradação.
        if (latestReading != null && latestReading.outcome() == ProbeReading.Outcome.DOWN) {
            derived = ComponentStatus.OUTAGE;
        }
        for (StatusIncident incident : incidents) {
            if (!incident.isOpen() || !incident.affects(component.componentKey())) {
                continue;
            }
            ComponentStatus fromIncident = switch (incident.impact()) {
                case MAJOR, CRITICAL -> ComponentStatus.OUTAGE;
                case MINOR -> ComponentStatus.DEGRADED;
            };
            derived = derived == null ? fromIncident : ComponentStatus.worst(derived, fromIncident);
        }

        // 3. Manutenção ativa só aparece se nada pior estiver acontecendo.
        if (derived == null || derived.severity() < ComponentStatus.MAINTENANCE.severity()) {
            for (MaintenanceWindow window : windows) {
                if (window.affects(component.componentKey()) && window.isActiveAt(now)) {
                    derived = ComponentStatus.MAINTENANCE;
                    break;
                }
            }
        }
        if (derived != null) {
            return derived;
        }

        // 4–5. Sem fato adverso: sonda que nunca mediu não afirma saúde.
        if (component.hasProbe() && latestReading == null) {
            return ComponentStatus.UNKNOWN;
        }
        return ComponentStatus.OPERATIONAL;
    }

    /** Status geral: o pior entre os componentes visíveis; sem componentes, UNKNOWN. */
    public static ComponentStatus overallOf(List<ComponentView> components) {
        Objects.requireNonNull(components, "Lista de componentes é obrigatória (vazia, não nula)");
        ComponentStatus overall = null;
        for (ComponentView view : components) {
            overall = overall == null ? view.status() : ComponentStatus.worst(overall, view.status());
        }
        return overall == null ? ComponentStatus.UNKNOWN : overall;
    }

    /**
     * Monta a visão pública completa. {@code readingsByComponentKey} traz
     * a leitura mais recente por componente (ausência da chave = sonda
     * nunca mediu). Incidentes resolvidos sem resumo publicado NÃO
     * aparecem: a página não anuncia revisão que ainda não foi publicada.
     */
    public static PublicStatusView buildView(List<StatusComponent> components,
                                             Map<String, ProbeReading> readingsByComponentKey,
                                             List<StatusIncident> incidents,
                                             List<MaintenanceWindow> windows, Instant now) {
        Objects.requireNonNull(components, "Lista de componentes é obrigatória (vazia, não nula)");
        Objects.requireNonNull(readingsByComponentKey, "Mapa de leituras é obrigatório (vazio, não nulo)");
        Objects.requireNonNull(incidents, "Lista de incidentes é obrigatória (vazia, não nula)");
        Objects.requireNonNull(windows, "Lista de janelas é obrigatória (vazia, não nula)");
        Objects.requireNonNull(now, "now é obrigatório");

        List<StatusComponent> visible = components.stream()
                .filter(StatusComponent::visible)
                .sorted(Comparator.comparingInt(StatusComponent::sortOrder)
                        .thenComparing(StatusComponent::componentKey))
                .toList();

        List<ComponentView> componentViews = new ArrayList<>();
        for (StatusComponent component : visible) {
            ComponentStatus status = derive(component,
                    readingsByComponentKey.get(component.componentKey()), incidents, windows, now);
            componentViews.add(new ComponentView(component.componentKey(), component.name(),
                    component.description(), status));
        }

        List<IncidentView> activeIncidents = incidents.stream()
                .filter(StatusIncident::isOpen)
                .sorted(Comparator.comparing(StatusIncident::startedAt).reversed())
                .map(i -> new IncidentView(i.title(), i.impact().name(), i.status().name(),
                        i.affectedComponentKeys(), i.startedAt()))
                .toList();

        List<MaintenanceView> maintenanceViews = windows.stream()
                .filter(w -> w.isActiveAt(now) || w.isUpcomingAt(now))
                .sorted(Comparator.comparing(MaintenanceWindow::startsAt))
                .map(w -> new MaintenanceView(w.title(), w.description(), w.componentKey(),
                        w.startsAt(), w.endsAt(), w.isActiveAt(now)))
                .toList();

        List<PublishedSummaryView> summaries = incidents.stream()
                .filter(StatusIncident::hasPublishedSummary)
                .sorted(Comparator.comparing(StatusIncident::summaryPublishedAt).reversed())
                .map(i -> new PublishedSummaryView(i.title(), i.affectedComponentKeys(),
                        i.startedAt(), i.resolvedAt(), i.postIncidentSummary(), i.summaryPublishedAt()))
                .toList();

        return new PublicStatusView(overallOf(componentViews), now, List.copyOf(componentViews),
                activeIncidents, maintenanceViews, summaries);
    }

    /**
     * JSON da visão, SOMENTE pela classe {@code Json} do core. Os enums
     * viram o nome em inglês; os {@code Instant} saem em ISO-8601
     * (comportamento padrão do Jackson 3 usado pela {@code Json}).
     */
    public static String toJson(PublicStatusView view) {
        Objects.requireNonNull(view, "Visão é obrigatória para serializar");
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("overall", view.overall().name());
        root.put("generatedAt", view.generatedAt().toString());
        root.put("components", view.components().stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("componentKey", c.componentKey());
            m.put("name", c.name());
            m.put("description", c.description());
            m.put("status", c.status().name());
            return m;
        }).toList());
        root.put("activeIncidents", view.activeIncidents().stream().map(i -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("title", i.title());
            m.put("impact", i.impact());
            m.put("status", i.status());
            m.put("affectedComponentKeys", i.affectedComponentKeys());
            m.put("startedAt", i.startedAt().toString());
            return m;
        }).toList());
        root.put("maintenance", view.maintenance().stream().map(w -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("title", w.title());
            m.put("description", w.description());
            m.put("componentKey", w.componentKey());
            m.put("startsAt", w.startsAt().toString());
            m.put("endsAt", w.endsAt().toString());
            m.put("active", w.active());
            return m;
        }).toList());
        root.put("publishedSummaries", view.publishedSummaries().stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("incidentTitle", s.incidentTitle());
            m.put("affectedComponentKeys", s.affectedComponentKeys());
            m.put("startedAt", s.startedAt().toString());
            m.put("resolvedAt", s.resolvedAt().toString());
            m.put("summary", s.summary());
            m.put("publishedAt", s.publishedAt().toString());
            return m;
        }).toList());
        return Json.write(root);
    }
}
