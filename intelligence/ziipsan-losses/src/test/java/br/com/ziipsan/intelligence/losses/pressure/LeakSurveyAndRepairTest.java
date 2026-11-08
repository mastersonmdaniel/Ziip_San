package br.com.ziipsan.intelligence.losses.pressure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

import br.com.ziipsan.core.incident.LeakDetectionFinding;
import br.com.ziipsan.intelligence.losses.dmc.LossParameters;
import br.com.ziipsan.intelligence.losses.dmc.NightFlowAnalyzer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

class LeakSurveyAndRepairTest {
    static final UUID TEN = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final LocalDateTime T0 = LocalDateTime.of(2026, 3, 10, 2, 0);

    static NightFlowAnalyzer.NightFlowResult nightFlow(double minimumLPerH, double legitPerConn, int conns) {
        var window = LossParameters.nightWindow(null);
        var samples = List.of(
                new NightFlowAnalyzer.HourlyFlow(T0, minimumLPerH + 3000),
                new NightFlowAnalyzer.HourlyFlow(T0.plusHours(1), minimumLPerH));
        return NightFlowAnalyzer.analyze(samples, window, legitPerConn, conns);
    }
    static LeakSurveyCampaign campaign() {
        return new LeakSurveyCampaign(UUID.randomUUID(), TEN, "CAMP-01", UUID.randomUUID(),
                "DMC-NORTE", "URBANO_DENSO", "vazao noturna liquida alta sugere vazamento nao visivel no setor norte");
    }

    @Test @DisplayName("campanha nao inicia sem equipamento/equipe e nao conclui sem evidencia e medicoes")
    void campaignGuards() {
        LeakSurveyCampaign c = campaign();
        assertThatThrownBy(c::start).isInstanceOf(IllegalStateException.class);
        c.assignResources(List.of("geofone X1", "correlacionador Y2"), List.of("equipe-campo-1"));
        c.start();
        assertThatThrownBy(c::complete).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("evidencia");
        c.addEvidence("foto-sondagem-01");
        assertThatThrownBy(c::complete).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ANTES e DEPOIS");
    }

    @Test @DisplayName("ganho medido pela VMN da ED-85 antes/depois: liquida 7300 -> 4300 = ganho 3000 L/h (41,0959%)")
    void campaignGainFromNightFlow() {
        LeakSurveyCampaign c = campaign();
        c.assignResources(List.of("geofone X1"), List.of("equipe-campo-1"));
        c.start();
        c.addEvidence("relatorio-correlacao-01");
        // ED-85 real: minimo 9000 - legitimo 1,7 x 1000 = 7300; depois minimo 6000 - 1700 = 4300
        c.recordNightFlowBefore(nightFlow(9000, 1.7, 1000));
        c.recordNightFlowAfter(nightFlow(6000, 1.7, 1000));
        var gain = c.complete();
        assertThat(gain.beforeNetNightFlowLPerH()).isEqualTo(7300.0);
        assertThat(gain.afterNetNightFlowLPerH()).isEqualTo(4300.0);
        assertThat(gain.absoluteGainLPerH()).isEqualTo(3000.0);
        assertThat(gain.percentGain()).isPresent();
        assertThat(gain.percentGain().getAsDouble()).isCloseTo(41.0958904109589, within(1e-9));
        assertThat(c.status()).isEqualTo(LeakSurveyCampaign.Status.COMPLETED);
    }

    @Test @DisplayName("achado e o modelo da ED-92: pendente bloqueia a conclusao; disposto (NOT_A_LEAK/CONFIRMED_LINKED) libera")
    void findingFollowsEd92Model() {
        LeakSurveyCampaign c = campaign();
        c.assignResources(List.of("geofone X1"), List.of("equipe-campo-1"));
        c.start();
        c.addEvidence("audio-geofone-01");
        c.recordNightFlowBefore(nightFlow(9000, 1.7, 1000));
        c.recordNightFlowAfter(nightFlow(6000, 1.7, 1000));
        LeakDetectionFinding f = new LeakDetectionFinding(UUID.randomUUID(), TEN, "LD-1", T0, "Rua B, 200");
        c.addFinding(f);
        assertThatThrownBy(c::complete).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LD-1");
        f.confirmLinked(UUID.randomUUID(), UUID.randomUUID());
        c.linkRepairWorkOrder(f.workOrderId());
        assertThatCode(c::complete).doesNotThrowAnyException();
        // o reparo vive na OS do achado (DAD-27): a campanha guarda a OS vinculada
        assertThat(c.repairWorkOrderIds()).containsExactly(f.workOrderId());
    }

    @Test @DisplayName("fila de suspeitas ponderada pela vazao noturna por ligacao; sem sinal positivo fica fora e contado")
    void suspectQueueWeightedByNightFlow() {
        var q = LeakSuspectQueue.rank(List.of(
                new LeakSuspectQueue.Suspect("DMC-A", 7300, 1000, 12.0, "URBANO_DENSO"),   // 7,3 L/h/lig
                new LeakSuspectQueue.Suspect("DMC-B", 4000, 200, 3.0, "RURAL"),            // 20,0 L/h/lig
                new LeakSuspectQueue.Suspect("DMC-C", -500, 800, 9.0, "RURAL"),            // sem sinal
                new LeakSuspectQueue.Suspect("DMC-D", 0, 500, 5.0, "RURAL")));             // sem sinal
        assertThat(q.excludedWithoutPositiveNet()).isEqualTo(2);
        assertThat(q.ranked()).extracting(r -> r.suspect().dmcCode()).containsExactly("DMC-B", "DMC-A");
        assertThat(q.ranked().get(0).weightLPerHPerConnection()).isEqualTo(20.0);
    }

    @Test @DisplayName("tempo de reparo por fases contra as metas: conhecimento 4h ok, localizacao 30h estoura meta 24h, reparo 10h ok")
    void repairPhasesAgainstTargets() {
        var timeline = new LeakRepairTimeline.Timeline(UUID.randomUUID(), UUID.randomUUID(),
                T0, T0.plusHours(4), T0.plusHours(34), T0.plusHours(44));
        var targets = PressureParameters.repairPhaseTargets("AWARENESS=8,LOCATION=24,REPAIR=48");
        var a = LeakRepairTimeline.assess(timeline, targets);
        assertThat(a.phases()).hasSize(3);
        assertThat(a.breachedPhases()).containsExactly(PressureParameters.RepairPhase.LOCATION);
        assertThat(a.totalOrNull()).isEqualTo(java.time.Duration.ofHours(44));
    }

    @Test @DisplayName("fase nao ocorrida e ausente (nunca zero): sem localizacao nao ha fase LOCATION/REPAIR nem total")
    void unmeasuredPhasesAbsent() {
        var timeline = new LeakRepairTimeline.Timeline(null, null, T0, T0.plusHours(2), null, null);
        var a = LeakRepairTimeline.assess(timeline,
                PressureParameters.repairPhaseTargets("AWARENESS=8,LOCATION=24,REPAIR=48"));
        assertThat(a.phases()).hasSize(1);
        assertThat(a.totalOrNull()).isNull();
        assertThatThrownBy(() -> new LeakRepairTimeline.Timeline(null, null,
                T0, T0.plusHours(2), null, T0.plusHours(9)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("ciclo de pesquisa por classe: vencido na data do ciclo, nunca pesquisado sempre vencido")
    void surveyCycleDue() {
        LocalDate last = LocalDate.of(2026, 1, 1);
        int cycle = PressureParameters.surveyCycleDaysFor("URBANO_DENSO=90", "URBANO_DENSO");
        assertThat(SurveyCyclePolicy.nextDue(last, cycle)).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(SurveyCyclePolicy.isDue(last, cycle, LocalDate.of(2026, 3, 31))).isFalse();
        assertThat(SurveyCyclePolicy.isDue(last, cycle, LocalDate.of(2026, 4, 1))).isTrue();
        assertThat(SurveyCyclePolicy.isDue(null, cycle, LocalDate.of(2026, 1, 1))).isTrue();
        assertThat(SurveyCyclePolicy.daysOverdue(last, cycle, LocalDate.of(2026, 4, 11))).isEqualTo(10);
    }
}
