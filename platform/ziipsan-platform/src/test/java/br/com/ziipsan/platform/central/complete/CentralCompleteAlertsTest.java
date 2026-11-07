package br.com.ziipsan.platform.central.complete;

import br.com.ziipsan.platform.observability.AlertRule;
import br.com.ziipsan.platform.observability.SloDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3.15: falha provocada alerta pela regra ÚNICA existente
 * {@code central.collector.stale} (V9_1/P3.11 — correção M2); aba
 * Infraestrutura com burn calculado da P3.11 e tier derivado (M3);
 * Radar ordenado por vencimento.
 */
class CentralCompleteAlertsTest {

    private static CentralCompleteAlerts.ProbeResult probe(boolean success, long ageSeconds) {
        return new CentralCompleteAlerts.ProbeResult("coleta-central", success, ageSeconds);
    }

    @Test
    @DisplayName("M2 — fronteira da regra existente: exatamente 900 s NÃO dispara (comparador '>'); 901 s dispara; fresca não dispara")
    void staleCollectorBoundary() {
        AlertRule rule = CentralCompleteAlerts.collectorStaleRule();
        assertThat(CentralCompleteAlerts.evaluateCollectorStale(rule, probe(true, 900))).isEmpty();
        var signal = CentralCompleteAlerts.evaluateCollectorStale(rule, probe(true, 901));
        assertThat(signal).isPresent();
        assertThat(signal.get().ruleKey()).isEqualTo("central.collector.stale");
        assertThat(CentralCompleteAlerts.evaluateCollectorStale(rule, probe(true, 60))).isEmpty();
    }

    @Test
    @DisplayName("M2 — coleta fresca SEM falha não dispara; sucesso falso com coleta fresca também não (sinal e regra = mesma condição)")
    void freshCollectionNeverFires() {
        AlertRule rule = CentralCompleteAlerts.collectorStaleRule();
        assertThat(CentralCompleteAlerts.evaluateCollectorStale(rule, probe(true, 10))).isEmpty();
        // A versão anterior disparava com success=false mesmo fresco —
        // divergia da regra (idade > 900). Corrigido: sem a condição da
        // regra, não há sinal.
        assertThat(CentralCompleteAlerts.evaluateCollectorStale(rule, probe(false, 10))).isEmpty();
    }

    @Test
    @DisplayName("M2 — falha PROVOCADA dispara pela regra: medição simulada (coleta parada, sucesso falso, idade 3.600 s) alimenta a avaliação única")
    void provokedFailureFiresThroughExistingRule() {
        AlertRule rule = CentralCompleteAlerts.collectorStaleRule();
        var signal = CentralCompleteAlerts.evaluateCollectorStale(rule, probe(false, 3_600));
        assertThat(signal).isPresent();
        assertThat(signal.get().ruleKey()).isEqualTo(CentralCompleteAlerts.COLLECTOR_STALE_RULE_KEY);
        assertThat(signal.get().measuredValue()).isEqualTo(3_600.0);
        assertThat(signal.get().threshold()).isEqualTo(900.0);
        assertThat(signal.get().detail()).contains("falha provocada");
    }

    @Test
    @DisplayName("M2 — a avaliação USA a regra recebida: limiar 60 da regra de entrada dispara em 61 e não em 60 (nada hardcoded); regra de outra métrica é recusada")
    void evaluationUsesRuleDefinition() {
        AlertRule custom = new AlertRule("central.collector.stale", "platform.collector.last_run_age_s",
                ">", 60, 1, "CRITICAL", "GLOBAL", "platform",
                "docs/operacao/runbooks/RB-02-fila-parada.md");
        assertThat(CentralCompleteAlerts.evaluateCollectorStale(custom, probe(true, 60))).isEmpty();
        assertThat(CentralCompleteAlerts.evaluateCollectorStale(custom, probe(true, 61))).isPresent();
        AlertRule otherMetric = new AlertRule("outra.regra", "platform.outbox.oldest_pending_age_s",
                ">", 60, 1, "WARNING", "GLOBAL", "platform",
                "docs/operacao/runbooks/RB-02-fila-parada.md");
        assertThatThrownBy(() -> CentralCompleteAlerts.evaluateCollectorStale(otherMetric, probe(true, 999)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("métrica da coleta");
    }

    @Test
    @DisplayName("M2 — a regra referenciada é a EXISTENTE da V9_1/P3.11: chave central.collector.stale, métrica do watchdog, > 900, runbook RB-02")
    void referencedRuleIsTheExistingOne() {
        AlertRule rule = CentralCompleteAlerts.collectorStaleRule();
        assertThat(rule.ruleKey()).isEqualTo("central.collector.stale");
        assertThat(rule.metricKey()).isEqualTo("platform.collector.last_run_age_s");
        assertThat(rule.comparator()).isEqualTo(">");
        assertThat(rule.threshold()).isEqualTo(900.0);
        assertThat(rule.owner()).isNotBlank();
        assertThat(rule.runbookUrl()).isEqualTo("docs/operacao/runbooks/RB-02-fila-parada.md");
    }

    @Test
    @DisplayName("M3 — Infraestrutura com SLO: burn 2,5 calculado pela P3.11 (250 ruins em 100.000, SLO 99,9%) e tier DERIVADO dele = TICKET_SLOW")
    void infraSloBurnAndTierDerived() {
        // À mão (P3.11): taxa observada 250/100.000 = 0,0025; permitida
        // 0,001 → burn 2,5 nas duas janelas. tierFor(2,5; 2,5): curta
        // < 6 (sem page), longa >= 1 e < 3 → TICKET_SLOW.
        var view = CentralCompleteAlerts.infraSlo(new SloDefinition("api.availability", 99.9, 30),
                100_000, 250, 100_000, 250);
        assertThat(view.burnShortWindow().burnRate()).isCloseTo(2.5, org.assertj.core.api.Assertions.within(1e-12));
        assertThat(view.burnLongWindow().burnRate()).isCloseTo(2.5, org.assertj.core.api.Assertions.within(1e-12));
        assertThat(view.tierShortLong())
                .isEqualTo(br.com.ziipsan.platform.observability.BurnRateCalculator.AlertTier.TICKET_SLOW);
        assertThatThrownBy(() -> CentralCompleteAlerts.infraSlo(
                new SloDefinition("api.availability", 99.9, 30), 0, 0, 100_000, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .as("janela sem eventos é erro da P3.11, não burn zero");
    }

    @Test
    @DisplayName("Radar: vencidos entram, itens dentro da janela entram ordenados, fora da janela ficam de fora")
    void radarWindowAndOrder() {
        UUID tenant = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        LocalDate ref = LocalDate.of(2026, 11, 7);
        var items = List.of(
                new CentralCompleteAlerts.RadarItem("NORMA_VIGENCIA_FUTURA", tenant, LocalDate.of(2026, 12, 1), "edição 2027"),
                new CentralCompleteAlerts.RadarItem("CERTIFICADO", tenant, LocalDate.of(2026, 11, 1), "vencido"),
                new CentralCompleteAlerts.RadarItem("CONTRATO", tenant, LocalDate.of(2027, 6, 1), "longe"));
        List<CentralCompleteAlerts.RadarItem> radar = CentralCompleteAlerts.radarWithin(items, ref, 30);
        assertThat(radar).extracting(CentralCompleteAlerts.RadarItem::kind)
                .containsExactly("CERTIFICADO", "NORMA_VIGENCIA_FUTURA");
        assertThat(radar.get(0).overdueAt(ref)).isTrue();
        assertThat(radar.get(0).daysRemaining(ref)).isEqualTo(-6);
    }

    @Test
    @DisplayName("b3 — RadarItem sem tenant ou sem detalhe é recusado")
    void radarItemRequiresTenantAndDetail() {
        LocalDate due = LocalDate.of(2026, 12, 1);
        assertThatThrownBy(() -> new CentralCompleteAlerts.RadarItem("CERTIFICADO", null, due, "detalhe"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("tenant");
        assertThatThrownBy(() -> new CentralCompleteAlerts.RadarItem("CERTIFICADO",
                UUID.fromString("00000000-0000-0000-0000-0000000000a1"), due, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("detalhe");
    }
}
