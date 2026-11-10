package br.com.ziipsan.intelligence.losses.dma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.ziipsan.intelligence.losses.dma.DmaModel.DmaDefinition;
import br.com.ziipsan.intelligence.losses.dma.DmaModel.DmaPeriodBalance;
import br.com.ziipsan.intelligence.losses.dma.DmaModel.DmcPeriodBalance;

/** §4.1 da estimativa: balanco calculado a mao, igualdade EXACTA (compareTo==0 e equals com escala controlada). */
class ExactWaterBalanceTest {

    @Test
    void handCalculatedBalanceIsExact() {
        // a mao: liquida 10000.50-500.25=9500.25; faturado 6000.10+300=6300.10;
        // autorizado 6300.10+200.05=6500.15; perdas 9500.25-6500.15=3000.10;
        // reais 3000.10-500.05=2500.05; ANF 9500.25-6300.10=3200.15
        DmcPeriodBalance b = ExactWaterBalance.calculate(
                DmaFixtures.inputs("DMC-A", "10000.50", "500.25", "6000.10", "300", "200.05", "500.05"));
        assertEquals(0, b.netInputM3().compareTo(DmaFixtures.bd("9500.25")));
        assertEquals(0, b.billedTotalM3().compareTo(DmaFixtures.bd("6300.10")));
        assertEquals(0, b.authorizedConsumptionM3().compareTo(DmaFixtures.bd("6500.15")));
        assertEquals(0, b.totalLossesM3().compareTo(DmaFixtures.bd("3000.10")));
        assertEquals(0, b.realLossesM3().orElseThrow().compareTo(DmaFixtures.bd("2500.05")));
        assertEquals(0, b.nonRevenueWaterM3().compareTo(DmaFixtures.bd("3200.15")));
        assertTrue(b.absentComponents().isEmpty());
    }

    @Test
    void dmaWithoutConsumptionLossesEqualNetInput() {
        DmcPeriodBalance b = ExactWaterBalance.calculate(
                DmaFixtures.inputs("DMC-Z", "4000", "0", "0", "0", "0", "0"));
        assertEquals(0, b.totalLossesM3().compareTo(DmaFixtures.bd("4000")));
        assertEquals(0, b.realLossesM3().orElseThrow().compareTo(DmaFixtures.bd("4000")));
    }

    @Test
    void dmaWithoutInputIsExactZero() {
        DmcPeriodBalance b = ExactWaterBalance.calculate(
                DmaFixtures.inputs("DMC-E", "0", "0", "0", "0", "0", null));
        assertEquals(0, b.netInputM3().signum());
        assertEquals(0, b.totalLossesM3().signum());
        assertEquals(0, b.nonRevenueWaterM3().signum());
    }

    @Test
    void absentApparentLossesDeclaredNeverEstimated() {
        DmcPeriodBalance b = ExactWaterBalance.calculate(
                DmaFixtures.inputs("DMC-N", "1000", "0", "700", "0", "0", null));
        assertEquals(0, b.totalLossesM3().compareTo(DmaFixtures.bd("300")));
        assertTrue(b.realLossesM3().isEmpty());
        assertEquals(List.of(ExactWaterBalance.ABSENT_APPARENT_LOSSES), b.absentComponents());
    }

    @Test
    void aggregateSumsExactlyAndRequiresExactMembership() {
        DmcPeriodBalance a = ExactWaterBalance.calculate(
                DmaFixtures.inputs("DMC-A", "10000.50", "500.25", "6000.10", "300", "200.05", "500.05"));
        DmcPeriodBalance b = ExactWaterBalance.calculate(
                DmaFixtures.inputs("DMC-B", "2000", "0", "1000", "0", "100", "50"));
        DmaDefinition def = new DmaDefinition("DMA-1", "SETOR-1", List.of("DMC-A", "DMC-B"));
        DmaPeriodBalance agg = ExactWaterBalance.aggregate(def, List.of(b, a)); // ordem trocada de proposito
        assertEquals(2, agg.memberCount());
        assertEquals(0, agg.netInputM3().compareTo(DmaFixtures.bd("11500.25")));
        assertEquals(0, agg.totalLossesM3().compareTo(DmaFixtures.bd("3900.10")));
        // reais: A 2500.05 + B (2000-1100-50=850) = 3350.05
        assertEquals(0, agg.realLossesM3().orElseThrow().compareTo(DmaFixtures.bd("3350.05")));
        assertThrows(IllegalArgumentException.class, () -> ExactWaterBalance.aggregate(def, List.of(a)));
    }

    @Test
    void aggregateRealLossesAbsentWhenAnyMemberLacksApparent() {
        DmcPeriodBalance a = ExactWaterBalance.calculate(
                DmaFixtures.inputs("DMC-A", "1000", "0", "700", "0", "0", "10"));
        DmcPeriodBalance b = ExactWaterBalance.calculate(
                DmaFixtures.inputs("DMC-B", "1000", "0", "700", "0", "0", null));
        DmaDefinition def = new DmaDefinition("DMA-2", "SETOR-1", List.of("DMC-A", "DMC-B"));
        DmaPeriodBalance agg = ExactWaterBalance.aggregate(def, List.of(a, b));
        assertEquals(0, agg.totalLossesM3().compareTo(DmaFixtures.bd("600")));
        assertTrue(agg.realLossesM3().isEmpty());
        assertEquals(List.of(ExactWaterBalance.ABSENT_APPARENT_LOSSES), agg.absentComponents());
    }
}
