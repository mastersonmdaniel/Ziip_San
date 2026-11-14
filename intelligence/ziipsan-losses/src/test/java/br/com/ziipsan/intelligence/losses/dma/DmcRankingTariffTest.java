package br.com.ziipsan.intelligence.losses.dma;

import br.com.ziipsan.intelligence.losses.dma.DmcReturnRanking.Entry;
import br.com.ziipsan.intelligence.losses.dma.DmcReturnRanking.Mode;
import br.com.ziipsan.intelligence.losses.dma.DmcReturnRanking.Ranking;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P-II6-TARIFA (Ciclo 4 do programa 5x PDCA GIS): a costura entre o
 * ranking de DMC e a fonte de tarifa do tenant. NAO existe fonte real
 * do escalar "tarifa media por m3" no repo — a arquitectura tarifaria
 * do billing e progressiva por faixas/categorias (TariffBlock) —, por
 * isso a porta nasce sem adaptador e o ranking fica em
 * LOSS_VOLUME_ONLY declarado ate a fonte existir ([A DEFINIR],
 * pendencia P-PDCAGIS4-03). Nenhum valor e inventado nestes testes:
 * a tarifa da fixture e entrada do teste, como o motor ja aceitava.
 */
class DmcRankingTariffTest {

    private static final UUID TENANT = UUID.randomUUID();

    private static List<Entry> fixture() {
        return List.of(
                new Entry("DMC-B", DmaFixtures.bd("900"), Optional.of(DmaFixtures.bd("800"))),
                new Entry("DMC-A", DmaFixtures.bd("1200"), Optional.of(DmaFixtures.bd("800"))),
                new Entry("DMC-C", DmaFixtures.bd("2000"), Optional.empty()));
    }

    @Test
    @DisplayName("sem porta (fonte inexistente), o ranking sai em LOSS_VOLUME_ONLY declarado, sem tarifa")
    void withoutPortRankingStaysLossVolumeOnly() {
        Ranking ranking = DmcRankingTariff.rankForTenant(fixture(), TENANT, null);
        assertThat(ranking.mode()).isEqualTo(Mode.LOSS_VOLUME_ONLY);
        assertThat(ranking.tariffPerM3()).isEmpty();
        assertThat(ranking.ranked().stream().map(r -> r.dmcCode()).toList())
                .containsExactly("DMC-C", "DMC-A", "DMC-B");
    }

    @Test
    @DisplayName("porta sem valor para o tenant ([A DEFINIR]) mantem LOSS_VOLUME_ONLY — nenhum indice monetario e fabricado")
    void emptyPortRankingStaysLossVolumeOnly() {
        DmcRankingTariffPort port = tenantId -> Optional.empty();
        Ranking ranking = DmcRankingTariff.rankForTenant(fixture(), TENANT, port);
        assertThat(ranking.mode()).isEqualTo(Mode.LOSS_VOLUME_ONLY);
        assertThat(ranking.tariffPerM3()).isEmpty();
        // indice = volume recuperavel, nao um valor em moeda inventado
        assertThat(ranking.ranked().get(0).index()).isEqualByComparingTo(DmaFixtures.bd("2000"));
    }

    @Test
    @DisplayName("quando a fonte existir, a mesma costura produz TARIFF_WEIGHTED com indice exacto")
    void portWithTariffWeightsRanking() {
        DmcRankingTariffPort port = tenantId -> Optional.of(DmaFixtures.bd("7.50"));
        Ranking ranking = DmcRankingTariff.rankForTenant(fixture(), TENANT, port);
        assertThat(ranking.mode()).isEqualTo(Mode.TARIFF_WEIGHTED);
        assertThat(ranking.tariffPerM3()).contains(DmaFixtures.bd("7.50"));
        assertThat(ranking.ranked().get(0).index()).isEqualByComparingTo(DmaFixtures.bd("15000.00"));
    }

    @Test
    @DisplayName("tarifa nao positiva vinda da fonte e recusada pela validacao do motor — nao e corrigida em silencio")
    void nonPositiveTariffFromPortIsRefused() {
        DmcRankingTariffPort port = tenantId -> Optional.of(DmaFixtures.bd("0"));
        assertThatThrownBy(() -> DmcRankingTariff.rankForTenant(fixture(), TENANT, port))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("tenant e obrigatorio: a tarifa e do tenant, nunca global presumida")
    void tenantIsRequired() {
        DmcRankingTariffPort port = tenantId -> Optional.empty();
        assertThatThrownBy(() -> DmcRankingTariff.rankForTenant(fixture(), null, port))
                .isInstanceOf(NullPointerException.class);
    }
}
