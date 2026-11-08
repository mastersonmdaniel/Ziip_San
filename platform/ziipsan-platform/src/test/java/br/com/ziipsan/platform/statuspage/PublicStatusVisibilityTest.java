package br.com.ziipsan.platform.statuspage;

import br.com.ziipsan.core.json.Json;
import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P3.18 — regressão do defeito D1 da revisão cruzada (recuperação M3,
 * 08/11/2026): incidente e janela de componente {@code visible=false}
 * vazavam na visão pública (e no JSON) porque {@code buildView} filtrava
 * a visibilidade só na lista de componentes. A página é PÚBLICA: fato de
 * componente invisível não aparece, não é nomeado e não influencia o
 * {@code overall} — e o fato de componente visível continua aparecendo.
 */
class PublicStatusVisibilityTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    private static StatusComponent visibleApi() {
        return new StatusComponent("api", "API", null, "probe.api", 0, true);
    }

    private static StatusComponent hiddenInternal() {
        return new StatusComponent("internal", "Interno", null, "probe.internal", 1, false);
    }

    private static ProbeReading up(String key) {
        return new ProbeReading(key, ProbeReading.Outcome.UP, NOW.minusSeconds(60));
    }

    private static StatusIncident openIncident(String title, StatusIncident.Impact impact, String... keys) {
        return new StatusIncident(title, impact, StatusIncident.Status.INVESTIGATING,
                List.of(keys), NOW.minusSeconds(3600), null, null, null);
    }

    private static MaintenanceWindow futureWindow(String title, String componentKey) {
        return MaintenanceWindow.publish(title, null, componentKey,
                NOW.plusSeconds(80 * 3600), NOW.plusSeconds(82 * 3600), NOW);
    }

    @Test
    @DisplayName("P3.18/D1: incidente e janela de componente invisível NÃO aparecem na visão nem no JSON, e o overall não é afetado")
    void invisibleComponentFactsDoNotLeak() {
        // Mesma sonda da revisão: CRITICAL aberto e janela futura, ambos só do invisível.
        PublicStatusService.PublicStatusView view = PublicStatusService.buildView(
                List.of(visibleApi(), hiddenInternal()),
                Map.of("api", up("api"), "internal", up("internal")),
                List.of(openIncident("Falha interna secreta", StatusIncident.Impact.CRITICAL, "internal")),
                List.of(futureWindow("Janela interna", "internal")),
                NOW);

        assertThat(view.components()).hasSize(1);
        assertThat(view.components().get(0).componentKey()).isEqualTo("api");
        assertThat(view.activeIncidents()).isEmpty();
        assertThat(view.maintenance()).isEmpty();
        // O incidente oculto CRITICAL também NÃO influencia o estado geral.
        assertThat(view.overall()).isEqualTo(ComponentStatus.OPERATIONAL);

        String json = PublicStatusService.toJson(view);
        assertThat(json).doesNotContain("Falha interna secreta");
        assertThat(json).doesNotContain("Janela interna");
        assertThat(json).doesNotContain("internal");
        JsonNode tree = Json.readTree(json);
        assertThat(tree.get("activeIncidents").size()).isZero();
        assertThat(tree.get("maintenance").size()).isZero();
    }

    @Test
    @DisplayName("P3.18/D1: incidente e janela de componente VISÍVEL continuam aparecendo na visão e no JSON")
    void visibleComponentFactsStillAppear() {
        PublicStatusService.PublicStatusView view = PublicStatusService.buildView(
                List.of(visibleApi(), hiddenInternal()),
                Map.of("api", up("api"), "internal", up("internal")),
                List.of(openIncident("API degradada", StatusIncident.Impact.MAJOR, "api")),
                List.of(futureWindow("Janela da API", "api"),
                        futureWindow("Janela global", null)),
                NOW);

        assertThat(view.activeIncidents()).hasSize(1);
        assertThat(view.activeIncidents().get(0).title()).isEqualTo("API degradada");
        assertThat(view.activeIncidents().get(0).affectedComponentKeys()).containsExactly("api");
        // Janela do visível + janela global (pública por definição) aparecem; nenhuma é do invisível.
        assertThat(view.maintenance()).hasSize(2);
        assertThat(view.maintenance()).extracting(PublicStatusService.MaintenanceView::title)
                .containsExactlyInAnyOrder("Janela da API", "Janela global");
        // O incidente visível MAJOR continua dirigindo o overall.
        assertThat(view.overall()).isEqualTo(ComponentStatus.OUTAGE);

        String json = PublicStatusService.toJson(view);
        assertThat(json).contains("API degradada");
        assertThat(json).contains("Janela da API");
    }

    @Test
    @DisplayName("P3.18/D1: incidente misto aparece só com as chaves visíveis; resumo publicado de incidente só-invisível NÃO vaza")
    void mixedIncidentTrimmedAndHiddenSummaryDoesNotLeak() {
        StatusIncident mixed = openIncident("Falha parcial", StatusIncident.Impact.MINOR, "api", "internal");
        StatusIncident hiddenResolved = new StatusIncident("Falha interna antiga",
                StatusIncident.Impact.MAJOR, StatusIncident.Status.RESOLVED, List.of("internal"),
                NOW.minusSeconds(7200), NOW.minusSeconds(3600), null, null);
        StatusIncident hiddenPublished = PostIncidentSummary.publish(hiddenResolved,
                "Resumo interno que não é público", NOW.minusSeconds(1800));

        PublicStatusService.PublicStatusView view = PublicStatusService.buildView(
                List.of(visibleApi(), hiddenInternal()),
                Map.of("api", up("api"), "internal", up("internal")),
                List.of(mixed, hiddenPublished),
                List.of(),
                NOW);

        // O incidente misto é público pelo componente visível, mas a chave interna não é nomeada.
        assertThat(view.activeIncidents()).hasSize(1);
        assertThat(view.activeIncidents().get(0).affectedComponentKeys()).containsExactly("api");
        // O resumo publicado do incidente só-invisível não aparece.
        assertThat(view.publishedSummaries()).isEmpty();

        String json = PublicStatusService.toJson(view);
        assertThat(json).contains("Falha parcial");
        assertThat(json).doesNotContain("Resumo interno que não é público");
        assertThat(json).doesNotContain("Falha interna antiga");
        assertThat(json).doesNotContain("internal");
    }
}
