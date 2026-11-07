package br.com.ziipsan.integration.sinisa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Balanco hidrico (P4.2). Caso da v1 exigido pelo aceite: agua nao faturada de
 * 3.000 m3 — entrada de 10.000 m3 e faturado de 7.000 m3.
 */
class WaterBalanceTest {

    private static BigDecimal m3(String value) {
        return new BigDecimal(value);
    }

    @Test
    @DisplayName("caso da v1: entrada 10.000 m3 e faturado 7.000 m3 dao agua nao faturada de 3.000 m3")
    void casoV1AguaNaoFaturada3000() {
        WaterBalance balance = WaterBalance.calculate(m3("10000"), m3("7000"), m3("500"), m3("300"));

        assertThat(balance.nonRevenueM3()).isEqualByComparingTo("3000");
    }

    @Test
    @DisplayName("decomposicao fecha: perdas de agua = entrada - autorizado; perdas reais = perdas - aparentes")
    void decomposicaoFecha() {
        WaterBalance balance = WaterBalance.calculate(m3("10000"), m3("7000"), m3("500"), m3("300"));

        // autorizado = 7.500; perdas de agua = 2.500; perdas reais (CARL) = 2.200
        assertThat(balance.waterLossesM3()).isEqualByComparingTo("2500");
        assertThat(balance.realLossesM3()).isEqualByComparingTo("2200");
        assertThat(balance.carlM3()).isEqualByComparingTo("2200");
        // entrada = faturado + nao faturado autorizado + aparentes + reais
        BigDecimal soma = balance.billedM3().add(balance.unbilledAuthorizedM3())
                .add(balance.apparentLossesM3()).add(balance.realLossesM3());
        assertThat(soma).isEqualByComparingTo(balance.systemInputM3());
    }

    @Test
    @DisplayName("consumo autorizado maior que a entrada e recusado")
    void autorizadoMaiorQueEntradaRecusado() {
        assertThatThrownBy(() -> WaterBalance.calculate(m3("1000"), m3("900"), m3("200"), m3("0")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("consumo autorizado");
    }

    @Test
    @DisplayName("perdas aparentes maiores que as perdas de agua sao recusadas")
    void aparentesMaioresQuePerdasRecusado() {
        assertThatThrownBy(() -> WaterBalance.calculate(m3("1000"), m3("900"), m3("0"), m3("200")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("perdas aparentes");
    }

    @Test
    @DisplayName("volume negativo e recusado")
    void volumeNegativoRecusado() {
        assertThatThrownBy(() -> WaterBalance.calculate(m3("-1"), m3("0"), m3("0"), m3("0")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
