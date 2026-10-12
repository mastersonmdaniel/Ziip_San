package br.com.sigsan.commercial.billing.consumption;

import br.com.sigsan.commercial.billing.consumption.NonMeteredConsumption.AreaBand;
import br.com.sigsan.commercial.billing.consumption.NonMeteredConsumption.ParameterBand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static br.com.sigsan.commercial.billing.fixture.FictitiousTariffs.d;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Casos N-* do relatório 02 (seção 10.9). N-01 e N-02 (não medido "por tarifa") estão em
 * {@code TariffEngineTest}, porque são o consumo mínimo da ligação. As tabelas abaixo são FICTÍCIAS.
 */
class NonMeteredConsumptionTest {

    // consumo_minimo_area FICTÍCIA: até 40 m² → 8; até 60 → 10; até 100 → 14
    private static final List<AreaBand> AREA = List.of(
            new AreaBand(d("40"), 8), new AreaBand(d("60"), 10), new AreaBand(d("100"), 14));
    // consumo por moradores FICTÍCIA: 1 → 6; 2 → 9; 3 → 12
    private static final List<ParameterBand> RESIDENTS = List.of(
            new ParameterBand(1, 6), new ParameterBand(2, 9), new ParameterBand(3, 12));

    @Test
    @DisplayName("N-03: área 85,40 m², 2 economias virtuais: 85,40/2 = 42,70 → faixa 'até 60' → 10 × 2 economias = 20")
    void n03() {
        assertThat(NonMeteredConsumption.byArea(d("85.40"), 2, AREA, 2)).isEqualTo(20);
    }

    @Test
    @DisplayName("por área: limite da faixa é inclusivo; a menor faixa que comporta vale; área acima de tudo é erro")
    void areaBoundaries() {
        assertThat(NonMeteredConsumption.byArea(d("40.00"), 1, AREA, 1)).as("40,00 ≤ 40").isEqualTo(8);
        assertThat(NonMeteredConsumption.byArea(d("40.01"), 1, AREA, 1)).as("40,01 > 40").isEqualTo(10);
        assertThat(NonMeteredConsumption.byArea(d("100"), 1, AREA, 1)).isEqualTo(14);
        // 85,40 / 3 = 28,4666 → 28,47 (HALF_UP, 2 casas) → faixa até 40
        assertThat(NonMeteredConsumption.byArea(d("85.40"), 3, AREA, 1)).isEqualTo(8);
        assertThatThrownBy(() -> NonMeteredConsumption.byArea(d("100.01"), 1, AREA, 1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Nenhuma faixa");
        assertThatThrownBy(() -> NonMeteredConsumption.byArea(null, 1, AREA, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("N-04: moradores ausentes. O legado supunha 1 e escondia a falta do dado; aqui só com decisão explícita, e com aviso")
    void n04() {
        // com decisão explícita do tenant (usar 1): consumo 6, e o resultado traz o aviso
        NonMeteredConsumption.Result assumed = NonMeteredConsumption.byParameter(null, RESIDENTS, 1, 1);
        assertThat(assumed.consumption()).isEqualTo(6);
        assertThat(assumed.warnings()).hasSize(1);
        assertThat(assumed.warnings().get(0)).contains("ausente");

        // sem decisão: erro, não valor padrão
        assertThatThrownBy(() -> NonMeteredConsumption.byParameter(null, RESIDENTS, 1, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ausente");
    }

    @Test
    @DisplayName("por parâmetro: informado vale sem aviso; multiplica pelas economias; acima da tabela é erro")
    void parameterLookup() {
        NonMeteredConsumption.Result two = NonMeteredConsumption.byParameter(2, RESIDENTS, 3, null);
        assertThat(two.consumption()).as("9 × 3 economias").isEqualTo(27);
        assertThat(two.warnings()).isEmpty();
        assertThatThrownBy(() -> NonMeteredConsumption.byParameter(5, RESIDENTS, 1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("N-05: consumo mínimo fixado na ligação (15) sobrepõe o medido (9); medido maior, ou sem mínimo fixado, é mantido")
    void n05() {
        assertThat(NonMeteredConsumption.applyFixedMinimum(9, 15)).isEqualTo(15);
        assertThat(NonMeteredConsumption.applyFixedMinimum(20, 15)).isEqualTo(20);
        assertThat(NonMeteredConsumption.applyFixedMinimum(15, 15)).isEqualTo(15);
        assertThat(NonMeteredConsumption.applyFixedMinimum(9, null)).isEqualTo(9);
    }
}
