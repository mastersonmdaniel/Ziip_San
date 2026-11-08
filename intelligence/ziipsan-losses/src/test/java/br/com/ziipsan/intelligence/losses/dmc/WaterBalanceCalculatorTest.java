package br.com.ziipsan.intelligence.losses.dmc;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

import br.com.ziipsan.core.incident.NetworkIncident;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class WaterBalanceCalculatorTest {
    static final UUID DMC = UUID.fromString("00000000-0000-0000-0000-0000000000d1".replace('d','1'));
    static final YearMonth MAR = YearMonth.of(2026, 3);

    static WaterBalanceCalculator.Inputs inputs(Double apparent, Double target,
                                                Double mainsKm, Double pressure, Double pipesKm,
                                                IncidentLossInputs annex) {
        return new WaterBalanceCalculator.Inputs(DMC, MAR, 31, 1000,
                10000.0, 500.0, 6000.0, 200.0, 300.0, apparent,
                MicroPeriodAdjustment.Method.PRO_RATA_DAILY_AVERAGE,
                0.95, target, mainsKm, pressure, pipesKm,
                annex == null ? IncidentLossInputs.none() : annex);
    }

    @Test @DisplayName("OPE-01 entradas reais: liquida 9500, autorizado 6500, perdas 3000, reais 2500, ANF 3300 (34,74%)")
    void realBalance() {
        var b = WaterBalanceCalculator.calculate(inputs(500.0, null, null, null, null, null));
        assertThat(b.netInputM3()).isEqualTo(9500.0);
        assertThat(b.authorizedConsumptionM3()).isEqualTo(6500.0);
        assertThat(b.billedTotalM3()).isEqualTo(6200.0);
        assertThat(b.totalLossesM3()).isEqualTo(3000.0);
        assertThat(b.realLossesM3OrNull()).isEqualTo(2500.0);
        assertThat(b.nonRevenueWaterM3()).isEqualTo(3300.0);
        assertThat(b.nonRevenuePercent()).isCloseTo(34.7368, within(0.001));
        assertThat(b.realLossesLPerConnDayOrNull()).isCloseTo(80.645, within(0.01));
        assertThat(b.confidence()).isEqualTo(WaterBalanceCalculator.ConfidenceGrade.HIGH);
        assertThat(b.adjustmentMethod()).isEqualTo(MicroPeriodAdjustment.Method.PRO_RATA_DAILY_AVERAGE);
    }

    @Test @DisplayName("OPE-01 sem marcador: componente null recusa o calculo (nada de zero presumido)")
    void placeholderRefuses() {
        assertThatThrownBy(() -> new WaterBalanceCalculator.Inputs(DMC, MAR, 31, 1000,
                null, 0.0, 6000.0, 0.0, 0.0, null,
                MicroPeriodAdjustment.Method.NONE, 1.0, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WaterBalanceCalculator.Inputs(DMC, MAR, 31, 1000,
                10000.0, 500.0, 6000.0, null, 0.0, null,
                MicroPeriodAdjustment.Method.NONE, 1.0, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("sem perdas aparentes declaradas: perda real AUSENTE (null), total conhecida; ILI ausente")
    void apparentUnknown() {
        var b = WaterBalanceCalculator.calculate(inputs(null, null, 12.0, 30.0, 5.0, null));
        assertThat(b.totalLossesM3()).isEqualTo(3000.0);
        assertThat(b.realLossesM3OrNull()).isNull();
        assertThat(b.realLossesLPerConnDayOrNull()).isNull();
        assertThat(b.iliOrNull()).isNull();
        assertThat(b.targetStatus()).isEqualTo(WaterBalanceCalculator.TargetStatus.NO_TARGET);
        assertThat(b.confidence()).isEqualTo(WaterBalanceCalculator.ConfidenceGrade.MEDIUM);
    }

    @Test @DisplayName("ILI com dados reais: CARL 80645 L/dia / UARL (18x12+0,8x1000+25x5)x30=34230 -> 2,356")
    void iliReal() {
        var b = WaterBalanceCalculator.calculate(inputs(500.0, null, 12.0, 30.0, 5.0, null));
        assertThat(b.iliOrNull()).isCloseTo(2.356, within(0.01));
    }

    @Test @DisplayName("meta: real 80,6 <= meta 100 cumpre; meta 50 estoura; sem meta NO_TARGET")
    void targetStatus() {
        assertThat(WaterBalanceCalculator.calculate(inputs(500.0, 100.0, null, null, null, null))
                .targetStatus()).isEqualTo(WaterBalanceCalculator.TargetStatus.AT_OR_BELOW_TARGET);
        assertThat(WaterBalanceCalculator.calculate(inputs(500.0, 50.0, null, null, null, null))
                .targetStatus()).isEqualTo(WaterBalanceCalculator.TargetStatus.ABOVE_TARGET);
    }

    @Test @DisplayName("JOR-33 anexo ED-92: consome a entidade; desconhecido nao vira zero; excesso sobre a real sinaliza")
    void incidentAnnexFromEd92() {
        UUID ten = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID wo = UUID.randomUUID();
        NetworkIncident closed = new NetworkIncident(UUID.randomUUID(), ten, "OC-1",
                NetworkIncident.Type.RUPTURE, false, NetworkIncident.Origin.CREW,
                LocalDateTime.of(2026, 3, 5, 8, 0), NetworkIncident.AffectedSnapshot.empty(), null);
        closed.linkWorkOrder(wo);
        closed.markRestored(LocalDateTime.of(2026, 3, 5, 14, 0));
        closed.close(new NetworkIncident.ClosureData("ALAGAMENTO", 3000.0, 360L, wo), List.of("ALAGAMENTO"));
        NetworkIncident unknown = new NetworkIncident(UUID.randomUUID(), ten, "OC-2",
                NetworkIncident.Type.RUPTURE, false, NetworkIncident.Origin.CREW,
                LocalDateTime.of(2026, 3, 6, 8, 0), NetworkIncident.AffectedSnapshot.empty(), null);
        UUID wo2 = UUID.randomUUID();
        unknown.linkWorkOrder(wo2);
        unknown.markRestored(LocalDateTime.of(2026, 3, 6, 10, 0));
        unknown.close(new NetworkIncident.ClosureData("ALAGAMENTO", null, null, wo2), List.of("ALAGAMENTO"));
        IncidentLossInputs annex = IncidentLossInputs.fromIncidents(List.of(closed, unknown));
        assertThat(annex.closedIncidents()).isEqualTo(2);
        assertThat(annex.volumeKnown()).isEqualTo(1);
        assertThat(annex.totalLostVolumeM3OrNull()).isEqualTo(3000.0);
        assertThat(annex.avgRepairMinutesOrNull()).isEqualTo(360.0);
        // anexo 3000 > perda real do balanco 2500 -> inconsistencia sinalizada
        var b = WaterBalanceCalculator.calculate(inputs(500.0, null, null, null, null, annex));
        assertThat(b.incidentVolumeExceedsRealLosses()).isTrue();
        // nenhum conhecido -> total ausente, nunca 0
        assertThat(IncidentLossInputs.fromIncidents(List.of(unknown)).totalLostVolumeM3OrNull()).isNull();
    }

    @Test @DisplayName("setor: agrega os DMCs; real so quando todos conhecem; fila ordena pelo excesso")
    void sectorAndQueue() {
        var b1 = WaterBalanceCalculator.calculate(inputs(500.0, 50.0, null, null, null, null));
        UUID dmc2 = UUID.randomUUID();
        var in2 = new WaterBalanceCalculator.Inputs(dmc2, MAR, 31, 500,
                4000.0, 0.0, 3000.0, 0.0, 100.0, 100.0,
                MicroPeriodAdjustment.Method.NONE, 0.5, 50.0, null, null, null, null);
        var b2 = WaterBalanceCalculator.calculate(in2);
        var sector = WaterBalanceCalculator.aggregateSector("SETOR-NORTE", List.of(b1, b2));
        assertThat(sector.netInputM3()).isEqualTo(13500.0);
        assertThat(sector.totalLossesM3()).isEqualTo(3900.0);
        assertThat(sector.realLossesM3OrNull()).isEqualTo(3300.0);
        assertThat(sector.dmcCount()).isEqualTo(2);
        // b1 real/conn/dia = 80,6 (excesso 30,6); b2: reais 800 -> 51,6 (excesso 1,6)
        var queue = DmcPriorityQueue.rank(List.of(b2, b1), Map.of(DMC, 50.0, dmc2, 50.0));
        assertThat(queue.ranked()).hasSize(2);
        assertThat(queue.ranked().get(0).balance().dmcId()).isEqualTo(DMC);
        assertThat(queue.excludedWithoutTarget()).isEqualTo(0);
        var noTargets = DmcPriorityQueue.rank(List.of(b1), Map.of());
        assertThat(noTargets.ranked()).isEmpty();
        assertThat(noTargets.excludedWithoutTarget()).isEqualTo(1);
    }

    @Test @DisplayName("ED-78 le a entrada real do balanco sem recalculo (mesmo periodo/DMC)")
    void realEntryForEd78() {
        var b = WaterBalanceCalculator.calculate(inputs(500.0, null, null, null, null, null));
        assertThat(WaterBalanceCalculator.realBalanceEntry(List.of(b), DMC, MAR)).contains(b);
        assertThat(WaterBalanceCalculator.realBalanceEntry(List.of(b), DMC, YearMonth.of(2026, 4))).isEmpty();
    }
}
