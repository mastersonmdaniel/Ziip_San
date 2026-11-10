package br.com.ziipsan.intelligence.losses.dma;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import br.com.ziipsan.intelligence.losses.dma.DmcReturnRanking.Entry;
import br.com.ziipsan.intelligence.losses.dma.DmcReturnRanking.LossBasis;
import br.com.ziipsan.intelligence.losses.dma.DmcReturnRanking.Mode;
import br.com.ziipsan.intelligence.losses.dma.DmcReturnRanking.Ranking;

/** §4.3 da estimativa: ordenacao exacta no fixture, desempate deterministico. */
class DmcReturnRankingTest {

    private static List<Entry> fixture() {
        return List.of(
                new Entry("DMC-B", DmaFixtures.bd("900"), Optional.of(DmaFixtures.bd("800"))),
                new Entry("DMC-A", DmaFixtures.bd("1200"), Optional.of(DmaFixtures.bd("800"))),
                new Entry("DMC-C", DmaFixtures.bd("2000"), Optional.empty()), // sem reais: base = totais
                new Entry("DMC-D", DmaFixtures.bd("100"), Optional.of(DmaFixtures.bd("50"))));
    }

    @Test
    void withoutTariffRanksByLossVolumeDeclared() {
        Ranking r = DmcReturnRanking.rank(fixture(), Optional.empty());
        assertEquals(Mode.LOSS_VOLUME_ONLY, r.mode());
        // volumes recuperaveis: C=2000, A=800, B=800, D=50; A x B desempatam pelo CODIGO (volume igual)
        assertEquals(List.of("DMC-C", "DMC-A", "DMC-B", "DMC-D"),
                r.ranked().stream().map(x -> x.dmcCode()).toList());
        assertEquals(LossBasis.TOTAL_LOSSES, r.ranked().get(0).basis());
        assertEquals(LossBasis.REAL_LOSSES, r.ranked().get(1).basis());
        assertEquals(1, r.ranked().get(0).position());
    }

    @Test
    void withTariffIndexIsExactProduct() {
        Ranking r = DmcReturnRanking.rank(fixture(), Optional.of(DmaFixtures.bd("7.50")));
        assertEquals(Mode.TARIFF_WEIGHTED, r.mode());
        // C: 2000 x 7.50 = 15000.00; A/B: 800 x 7.50 = 6000.00 (desempate por codigo); D: 375.00
        assertEquals(List.of("DMC-C", "DMC-A", "DMC-B", "DMC-D"),
                r.ranked().stream().map(x -> x.dmcCode()).toList());
        assertEquals(0, r.ranked().get(0).index().compareTo(DmaFixtures.bd("15000.00")));
        assertEquals(0, r.ranked().get(1).index().compareTo(DmaFixtures.bd("6000.00")));
        assertEquals(0, r.ranked().get(3).index().compareTo(DmaFixtures.bd("375.00")));
    }

    @Test
    void tieOnIndexBreaksByVolumeThenCode() {
        // indices iguais com volumes diferentes: X reais 100 (indice 100), Y totais 100 (indice 100) -> volume igual -> codigo
        // Z: reais 100 com totais 500 -> indice 100, desempate 1 pelo volume RECUPERAVEL (igual) e codigo
        List<Entry> tie = List.of(
                new Entry("DMC-Y", DmaFixtures.bd("100"), Optional.empty()),
                new Entry("DMC-X", DmaFixtures.bd("300"), Optional.of(DmaFixtures.bd("100"))));
        Ranking r = DmcReturnRanking.rank(tie, Optional.empty());
        assertEquals(List.of("DMC-X", "DMC-Y"), r.ranked().stream().map(x -> x.dmcCode()).toList());
    }
}
