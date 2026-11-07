package br.com.ziipsan.platform.statuspage;

import br.com.ziipsan.core.json.Json;
import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3.18: o resumo da revisão pós-incidente (modelo da P3.11, PLT-23)
 * publicado na página — e só ele: incidente aberto não publica, resumo
 * não publicado não aparece na visão/JSON públicos.
 */
class PostIncidentSummaryTest {

    private static final Instant STARTED = Instant.parse("2026-11-07T08:00:00Z");
    private static final Instant RESOLVED = Instant.parse("2026-11-07T09:30:00Z");
    private static final Instant PUBLISHED = Instant.parse("2026-11-07T15:00:00Z");
    private static final String SUMMARY =
            "A API ficou indisponível por 1 h 30 min. O serviço foi restabelecido "
                    + "e a causa sistêmica foi corrigida; detalhes no resumo publicável da revisão.";

    private static StatusIncident resolvedIncident() {
        return new StatusIncident("API indisponível", StatusIncident.Impact.MAJOR,
                StatusIncident.Status.RESOLVED, List.of("api"), STARTED, RESOLVED, null, null);
    }

    @Test
    @DisplayName("P3.18: resumo da revisão pós-incidente da P3.11 é publicado e aparece na página (visão e JSON)")
    void publishedSummaryAppearsOnPage() {
        StatusIncident published = PostIncidentSummary.publish(resolvedIncident(), SUMMARY, PUBLISHED);
        assertThat(published.hasPublishedSummary()).isTrue();

        StatusComponent api = new StatusComponent("api", "API", null, "probe.api", 0, true);
        PublicStatusService.PublicStatusView view = PublicStatusService.buildView(
                List.of(api),
                Map.of("api", new ProbeReading("api", ProbeReading.Outcome.UP, PUBLISHED)),
                List.of(published), List.of(), PUBLISHED);
        assertThat(view.publishedSummaries()).hasSize(1);
        assertThat(view.publishedSummaries().get(0).summary()).isEqualTo(SUMMARY);
        assertThat(view.activeIncidents()).isEmpty();

        // O JSON público (via Json do core) carrega o resumo publicado.
        JsonNode json = Json.readTree(PublicStatusService.toJson(view));
        assertThat(json.get("publishedSummaries").get(0).get("summary").asString()).isEqualTo(SUMMARY);
        assertThat(json.get("overall").asString()).isEqualTo("OPERATIONAL");
    }

    @Test
    @DisplayName("P3.18: incidente resolvido SEM resumo publicado não aparece na página — nada é anunciado pela metade")
    void unpublishedSummaryStaysOffPage() {
        StatusComponent api = new StatusComponent("api", "API", null, "probe.api", 0, true);
        PublicStatusService.PublicStatusView view = PublicStatusService.buildView(
                List.of(api),
                Map.of("api", new ProbeReading("api", ProbeReading.Outcome.UP, PUBLISHED)),
                List.of(resolvedIncident()), List.of(), PUBLISHED);
        assertThat(view.publishedSummaries()).isEmpty();
        JsonNode json = Json.readTree(PublicStatusService.toJson(view));
        assertThat(json.get("publishedSummaries").size()).isZero();
    }

    @Test
    @DisplayName("P3.18: resumo de incidente ainda aberto é recusado (revisão pós-incidente é posterior à resolução)")
    void refusesSummaryForOpenIncident() {
        StatusIncident open = new StatusIncident("API indisponível", StatusIncident.Impact.MAJOR,
                StatusIncident.Status.INVESTIGATING, List.of("api"), STARTED, null, null, null);
        assertThatThrownBy(() -> PostIncidentSummary.publish(open, SUMMARY, PUBLISHED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ainda está aberto");
    }

    @Test
    @DisplayName("P3.18: resumo vazio é recusado e publicação anterior à resolução também")
    void refusesBlankOrEarlySummary() {
        assertThatThrownBy(() -> PostIncidentSummary.publish(resolvedIncident(), "  ", PUBLISHED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Resumo publicável");
        assertThatThrownBy(() -> PostIncidentSummary.publish(
                resolvedIncident(), SUMMARY, RESOLVED.minusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
