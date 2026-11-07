package br.com.ziipsan.platform.statuspage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3.18: a derivação de status — o coração do aceite "status muda
 * sozinho com a sonda fora". Nenhum teste chama um setter de status
 * (ele não existe): todos mudam FATOS (sonda, incidente, janela) e
 * conferem o status derivado seguinte.
 */
class StatusDerivationTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    private static StatusComponent probed(String key) {
        return new StatusComponent(key, "Componente " + key, null, "probe." + key, 0, true);
    }

    private static ProbeReading reading(String key, ProbeReading.Outcome outcome) {
        return new ProbeReading(key, outcome, NOW.minusSeconds(60));
    }

    private static StatusIncident openIncident(StatusIncident.Impact impact, String... keys) {
        return new StatusIncident("Incidente", impact, StatusIncident.Status.INVESTIGATING,
                List.of(keys), NOW.minusSeconds(3600), null, null, null);
    }

    @Test
    @DisplayName("P3.18: sonda fora muda o status sozinha — UP é OPERATIONAL, a leitura seguinte DOWN é OUTAGE")
    void probeDownFlipsStatusWithoutAnyManualChange() {
        StatusComponent api = probed("api");
        assertThat(PublicStatusService.derive(api, reading("api", ProbeReading.Outcome.UP),
                List.of(), List.of(), NOW)).isEqualTo(ComponentStatus.OPERATIONAL);
        // Só o fato mudou (a sonda caiu). Nenhuma flag foi editada.
        assertThat(PublicStatusService.derive(api, reading("api", ProbeReading.Outcome.DOWN),
                List.of(), List.of(), NOW)).isEqualTo(ComponentStatus.OUTAGE);
    }

    @Test
    @DisplayName("P3.18: componente com sonda que nunca mediu é UNKNOWN, nunca OPERATIONAL inventado")
    void neverProbedIsUnknown() {
        assertThat(PublicStatusService.derive(probed("api"), null, List.of(), List.of(), NOW))
                .isEqualTo(ComponentStatus.UNKNOWN);
    }

    @Test
    @DisplayName("P3.18: incidente aberto MAJOR derruba o componente mesmo com a sonda UP; MINOR degrada")
    void openIncidentDrivesStatus() {
        StatusComponent api = probed("api");
        ProbeReading up = reading("api", ProbeReading.Outcome.UP);
        assertThat(PublicStatusService.derive(api, up,
                List.of(openIncident(StatusIncident.Impact.MAJOR, "api")), List.of(), NOW))
                .isEqualTo(ComponentStatus.OUTAGE);
        assertThat(PublicStatusService.derive(api, up,
                List.of(openIncident(StatusIncident.Impact.MINOR, "api")), List.of(), NOW))
                .isEqualTo(ComponentStatus.DEGRADED);
        // Incidente de OUTRO componente não contamina este.
        assertThat(PublicStatusService.derive(api, up,
                List.of(openIncident(StatusIncident.Impact.CRITICAL, "portal")), List.of(), NOW))
                .isEqualTo(ComponentStatus.OPERATIONAL);
    }

    @Test
    @DisplayName("P3.18: incidente resolvido não afeta mais a derivação")
    void resolvedIncidentDoesNotAffect() {
        StatusIncident resolved = new StatusIncident("Incidente", StatusIncident.Impact.CRITICAL,
                StatusIncident.Status.RESOLVED, List.of("api"), NOW.minusSeconds(7200),
                NOW.minusSeconds(3600), null, null);
        assertThat(PublicStatusService.derive(probed("api"), reading("api", ProbeReading.Outcome.UP),
                List.of(resolved), List.of(), NOW)).isEqualTo(ComponentStatus.OPERATIONAL);
    }

    @Test
    @DisplayName("P3.18: janela ativa mostra MAINTENANCE, mas interrupção real durante a janela continua OUTAGE")
    void maintenanceYieldsToRealOutage() {
        MaintenanceWindow active = MaintenanceWindow.publish("Janela", null, "api",
                NOW.minusSeconds(600), NOW.plusSeconds(3600), NOW.minusSeconds(600 + 73 * 3600));
        StatusComponent api = probed("api");
        assertThat(PublicStatusService.derive(api, reading("api", ProbeReading.Outcome.UP),
                List.of(), List.of(active), NOW)).isEqualTo(ComponentStatus.MAINTENANCE);
        assertThat(PublicStatusService.derive(api, reading("api", ProbeReading.Outcome.DOWN),
                List.of(), List.of(active), NOW)).isEqualTo(ComponentStatus.OUTAGE);
        // Janela futura não muda o status de agora.
        MaintenanceWindow future = MaintenanceWindow.publish("Futura", null, "api",
                NOW.plusSeconds(80 * 3600), NOW.plusSeconds(82 * 3600), NOW);
        assertThat(PublicStatusService.derive(api, reading("api", ProbeReading.Outcome.UP),
                List.of(), List.of(future), NOW)).isEqualTo(ComponentStatus.OPERATIONAL);
    }

    @Test
    @DisplayName("P3.18: status geral é o pior dos visíveis; componente invisível não entra; sem componentes é UNKNOWN")
    void overallIsWorstOfVisible() {
        StatusComponent api = probed("api");
        StatusComponent hidden = new StatusComponent("internal", "Interno", null, "probe.internal", 1, false);
        PublicStatusService.PublicStatusView view = PublicStatusService.buildView(
                List.of(api, hidden),
                Map.of("api", reading("api", ProbeReading.Outcome.DOWN),
                        "internal", reading("internal", ProbeReading.Outcome.UP)),
                List.of(), List.of(), NOW);
        assertThat(view.components()).hasSize(1);
        assertThat(view.overall()).isEqualTo(ComponentStatus.OUTAGE);

        PublicStatusService.PublicStatusView empty = PublicStatusService.buildView(
                List.of(), Map.of(), List.of(), List.of(), NOW);
        assertThat(empty.overall()).isEqualTo(ComponentStatus.UNKNOWN);
    }

    @Test
    @DisplayName("P3.18: leitura de outro componente é recusada, não ignorada")
    void readingOfAnotherComponentIsRefused() {
        assertThatThrownBy(() -> PublicStatusService.derive(probed("api"),
                reading("portal", ProbeReading.Outcome.DOWN), List.of(), List.of(), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outro componente");
    }
}
